package io.github.hectorvent.floci.services.docdb;

import io.github.hectorvent.floci.config.EmulatorConfig;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.common.RegionResolver;
import io.github.hectorvent.floci.core.storage.InMemoryStorage;
import io.github.hectorvent.floci.services.docdb.model.DocDbCluster;
import io.github.hectorvent.floci.services.docdb.model.DocDbInstance;
import io.github.hectorvent.floci.services.docdb.model.DocDbSubnetGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocDbServiceTest {

    private DocDbService service;
    private RegionResolver regionResolver;
    private EmulatorConfig config;

    @BeforeEach
    void setUp() {
        regionResolver = new RegionResolver("us-east-1", "123456789012");
        config = mock(EmulatorConfig.class);
        when(config.hostname()).thenReturn(java.util.Optional.of("localhost"));
        when(config.defaultAvailabilityZone()).thenReturn("us-east-1a");

        service = new DocDbService(config, regionResolver,
                new InMemoryStorage<>(), new InMemoryStorage<>(), new InMemoryStorage<>());
    }

    // ── Subnet Groups ─────────────────────────────────────────────────────────

    @Test
    void createSubnetGroupGeneratesVirtualFields() {
        DocDbSubnetGroup group = service.createSubnetGroup("my-subnet-group", "My subnet group", null);

        assertEquals("my-subnet-group", group.getDbSubnetGroupName());
        assertEquals("My subnet group", group.getDbSubnetGroupDescription());
        assertEquals("vpc-00000000", group.getVpcId());
        assertEquals("Complete", group.getSubnetGroupStatus());
        assertFalse(group.getSubnets().isEmpty());
        assertEquals("subnet-00000000", group.getSubnets().get(0).getSubnetIdentifier());
    }

    @Test
    void createSubnetGroupRejectsDuplicate() {
        service.createSubnetGroup("sg1", "desc", null);
        AwsException exception = assertThrows(AwsException.class,
                () -> service.createSubnetGroup("sg1", "desc", null));
        assertEquals("DBSubnetGroupAlreadyExistsFault", exception.getErrorCode());
    }

    @Test
    void getSubnetGroupNotFoundThrows() {
        AwsException exception = assertThrows(AwsException.class,
                () -> service.getSubnetGroup("nonexistent"));
        assertEquals("DBSubnetGroupNotFoundFault", exception.getErrorCode());
    }

    @Test
    void listSubnetGroupsIsCaseInsensitive() {
        service.createSubnetGroup("mysg", "desc", null);
        Collection<DocDbSubnetGroup> result = service.listSubnetGroups("MYSG");
        assertEquals(1, result.size());
        assertEquals("mysg", result.iterator().next().getDbSubnetGroupName());
    }

    @Test
    void listSubnetGroupsReturnsAllWhenNoFilter() {
        service.createSubnetGroup("sg1", "desc1", null);
        service.createSubnetGroup("sg2", "desc2", null);
        assertEquals(2, service.listSubnetGroups(null).size());
    }

    @Test
    void deleteSubnetGroupSucceeds() {
        service.createSubnetGroup("sg1", "desc", null);
        service.deleteSubnetGroup("sg1");
        assertTrue(service.listSubnetGroups(null).isEmpty());
    }

    @Test
    void deleteSubnetGroupNotFoundThrows() {
        AwsException exception = assertThrows(AwsException.class,
                () -> service.deleteSubnetGroup("nonexistent"));
        assertEquals("DBSubnetGroupNotFoundFault", exception.getErrorCode());
    }

    // ── Clusters ──────────────────────────────────────────────────────────────

    @Test
    void createDbClusterGeneratesMissingFields() {
        DocDbCluster cluster = service.createDbCluster("my-cluster", null, null, null, null, null);

        assertEquals("my-cluster", cluster.getDbClusterIdentifier());
        assertEquals("available", cluster.getStatus());
        assertEquals("4.0.0", cluster.getEngineVersion());
        assertEquals("localhost", cluster.getEndpoint());
        assertEquals(27017, cluster.getPort());
        assertEquals("admin", cluster.getMasterUsername());
        assertNotNull(cluster.getDbClusterResourceId());
        assertTrue(cluster.getDbClusterResourceId().startsWith("cluster-"));
        assertEquals("arn:aws:rds:us-east-1:123456789012:cluster:my-cluster", cluster.getDbClusterArn());
    }

    @Test
    void createDbClusterAcceptsExplicitParameters() {
        service.createSubnetGroup("sg1", "desc", null);
        DocDbCluster cluster = service.createDbCluster("my-cluster", "5.0.0", "dbadmin", "secret", "sg1", null);

        assertEquals("5.0.0", cluster.getEngineVersion());
        assertEquals("dbadmin", cluster.getMasterUsername());
        assertEquals("sg1", cluster.getDbSubnetGroup());
    }

    @Test
    void createDbClusterRejectsDuplicate() {
        service.createDbCluster("c1", null, null, null, null, null);
        AwsException exception = assertThrows(AwsException.class,
                () -> service.createDbCluster("c1", null, null, null, null, null));
        assertEquals("DBClusterAlreadyExistsFault", exception.getErrorCode());
    }

    @Test
    void createDbClusterRejectsMissingSubnetGroup() {
        AwsException exception = assertThrows(AwsException.class,
                () -> service.createDbCluster("c1", null, null, null, "nonexistent-sg", null));
        assertEquals("DBSubnetGroupNotFoundFault", exception.getErrorCode());
    }

    @Test
    void getDbClusterNotFoundThrows() {
        AwsException exception = assertThrows(AwsException.class,
                () -> service.getDbCluster("nonexistent"));
        assertEquals("DBClusterNotFoundFault", exception.getErrorCode());
    }

    @Test
    void listDbClustersIsCaseInsensitive() {
        service.createDbCluster("mycluster", null, null, null, null, null);
        Collection<DocDbCluster> result = service.listDbClusters("MYCLUSTER");
        assertEquals(1, result.size());
    }

    @Test
    void modifyDbClusterDoesNotThrow() {
        service.createDbCluster("c1", null, null, null, null, null);
        DocDbCluster modified = service.modifyDbCluster("c1", "new-password");
        assertNotNull(modified);
        assertEquals("c1", modified.getDbClusterIdentifier());
    }

    @Test
    void deleteDbClusterSucceeds() {
        service.createDbCluster("c1", null, null, null, null, null);
        service.deleteDbCluster("c1");
        assertTrue(service.listDbClusters(null).isEmpty());
    }

    @Test
    void deleteDbClusterFailsWhenMembersRemain() {
        DocDbCluster cluster = service.createDbCluster("c1", null, null, null, null, null);
        cluster.getDbClusterMembers().add("instance-1");

        AwsException exception = assertThrows(AwsException.class,
                () -> service.deleteDbCluster("c1"));
        assertEquals("InvalidDBClusterStateFault", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("still has DB instances"));
    }

    @Test
    void deleteDbClusterNotFoundThrows() {
        AwsException exception = assertThrows(AwsException.class,
                () -> service.deleteDbCluster("nonexistent"));
        assertEquals("DBClusterNotFoundFault", exception.getErrorCode());
    }

    // ── Instances ─────────────────────────────────────────────────────────────

    @Test
    void createDbInstanceRequiresCluster() {
        AwsException exception = assertThrows(AwsException.class,
                () -> service.createDbInstance("i1", "c1", null, null, null));
        assertEquals("DBClusterNotFoundFault", exception.getErrorCode());
    }

    @Test
    void createDbInstanceGeneratesMissingFields() {
        service.createDbCluster("c1", null, null, null, null, null);
        DocDbInstance instance = service.createDbInstance("i1", "c1", null, null, null);

        assertEquals("i1", instance.getDbInstanceIdentifier());
        assertEquals("c1", instance.getDbClusterIdentifier());
        assertEquals("db.r5.large", instance.getDbInstanceClass());
        assertEquals("available", instance.getStatus());
        assertEquals("localhost", instance.getEndpoint());
        assertEquals(27017, instance.getPort());
        assertNotNull(instance.getDbiResourceId());
        assertTrue(instance.getDbiResourceId().startsWith("db-"));
        assertEquals("arn:aws:rds:us-east-1:123456789012:db:i1", instance.getDbInstanceArn());
    }

    @Test
    void createDbInstanceAddsToClusterMembers() {
        service.createDbCluster("c1", null, null, null, null, null);
        DocDbInstance instance = service.createDbInstance("i1", "c1", "db.r5.xlarge", "5.0.0", null);

        assertEquals("db.r5.xlarge", instance.getDbInstanceClass());
        assertEquals("5.0.0", instance.getEngineVersion());

        DocDbCluster cluster = service.getDbCluster("c1");
        assertTrue(cluster.getDbClusterMembers().contains("i1"));
    }

    @Test
    void createDbInstanceRejectsDuplicate() {
        service.createDbCluster("c1", null, null, null, null, null);
        service.createDbInstance("i1", "c1", null, null, null);
        AwsException exception = assertThrows(AwsException.class,
                () -> service.createDbInstance("i1", "c1", null, null, null));
        assertEquals("DBInstanceAlreadyExists", exception.getErrorCode());
    }

    @Test
    void getDbInstanceNotFoundThrows() {
        AwsException exception = assertThrows(AwsException.class,
                () -> service.getDbInstance("nonexistent"));
        assertEquals("DBInstanceNotFound", exception.getErrorCode());
    }

    @Test
    void listDbInstancesIsCaseInsensitive() {
        service.createDbCluster("c1", null, null, null, null, null);
        service.createDbInstance("myinstance", "c1", null, null, null);
        Collection<DocDbInstance> result = service.listDbInstances("MYINSTANCE");
        assertEquals(1, result.size());
    }

    @Test
    void listDbInstancesReturnsAllWhenNoFilter() {
        service.createDbCluster("c1", null, null, null, null, null);
        service.createDbInstance("i1", "c1", null, null, null);
        service.createDbInstance("i2", "c1", null, null, null);
        assertEquals(2, service.listDbInstances(null).size());
    }

    @Test
    void modifyDbInstanceUpdatesClass() {
        service.createDbCluster("c1", null, null, null, null, null);
        service.createDbInstance("i1", "c1", "db.r5.large", null, null);
        DocDbInstance modified = service.modifyDbInstance("i1", "db.r5.xlarge");
        assertEquals("db.r5.xlarge", modified.getDbInstanceClass());
    }

    @Test
    void deleteDbInstanceRemovesFromClusterMembers() {
        service.createDbCluster("c1", null, null, null, null, null);
        service.createDbInstance("i1", "c1", null, null, null);
        service.deleteDbInstance("i1");

        assertTrue(service.listDbInstances(null).isEmpty());
        assertTrue(service.getDbCluster("c1").getDbClusterMembers().isEmpty());
    }

    @Test
    void deleteDbInstanceNotFoundThrows() {
        AwsException exception = assertThrows(AwsException.class,
                () -> service.deleteDbInstance("nonexistent"));
        assertEquals("DBInstanceNotFound", exception.getErrorCode());
    }

    @Test
    void fullLifecycleCreateDescribeDelete() {
        // Subnet group
        service.createSubnetGroup("sg1", "Test subnet group", null);

        // Cluster
        DocDbCluster cluster = service.createDbCluster("cluster1", "4.0.0", "admin", "password", "sg1", null);
        assertEquals("cluster1", cluster.getDbClusterIdentifier());

        // Instances
        DocDbInstance i1 = service.createDbInstance("instance1", "cluster1", "db.r5.large", "4.0.0", null);
        assertEquals("instance1", i1.getDbInstanceIdentifier());
        DocDbInstance i2 = service.createDbInstance("instance2", "cluster1", "db.r5.xlarge", "4.0.0", null);
        assertEquals("instance2", i2.getDbInstanceIdentifier());

        // Describe cluster - should have 2 members
        DocDbCluster describedCluster = service.getDbCluster("cluster1");
        assertEquals(2, describedCluster.getDbClusterMembers().size());
        assertTrue(describedCluster.getDbClusterMembers().contains("instance1"));
        assertTrue(describedCluster.getDbClusterMembers().contains("instance2"));

        // Delete instances
        service.deleteDbInstance("instance1");
        service.deleteDbInstance("instance2");
        assertTrue(service.listDbInstances(null).isEmpty());

        // Delete cluster
        service.deleteDbCluster("cluster1");
        assertTrue(service.listDbClusters(null).isEmpty());

        // Delete subnet group
        service.deleteSubnetGroup("sg1");
        assertTrue(service.listSubnetGroups(null).isEmpty());
    }

    // ── Tags ──────────────────────────────────────────────────────────────────

    @Test
    void listTagsForClusterReturnsEmptyByDefault() {
        service.createDbCluster("c1", null, null, null, null, null);
        DocDbCluster cluster = service.getDbCluster("c1");
        Map<String, String> tags = service.listTags(cluster.getDbClusterArn());
        assertTrue(tags.isEmpty());
    }

    @Test
    void tagResourceAddsTagsToCluster() {
        service.createDbCluster("c1", null, null, null, null, null);
        DocDbCluster cluster = service.getDbCluster("c1");
        String arn = cluster.getDbClusterArn();

        Map<String, String> newTags = new HashMap<>();
        newTags.put("environment", "test");
        newTags.put("phase", "three");
        service.tagResource(arn, newTags);

        Map<String, String> tags = service.listTags(arn);
        assertEquals(2, tags.size());
        assertEquals("test", tags.get("environment"));
        assertEquals("three", tags.get("phase"));
    }

    @Test
    void untagResourceRemovesSpecifiedKeys() {
        service.createDbCluster("c1", null, null, null, null, null);
        DocDbCluster cluster = service.getDbCluster("c1");
        String arn = cluster.getDbClusterArn();

        Map<String, String> newTags = new HashMap<>();
        newTags.put("keep", "this");
        newTags.put("remove", "me");
        service.tagResource(arn, newTags);

        service.untagResource(arn, java.util.List.of("remove"));

        Map<String, String> tags = service.listTags(arn);
        assertEquals(1, tags.size());
        assertEquals("this", tags.get("keep"));
        assertNull(tags.get("remove"));
    }

    @Test
    void tagResourceWorksOnInstance() {
        service.createDbCluster("c1", null, null, null, null, null);
        DocDbInstance instance = service.createDbInstance("i1", "c1", null, null, null);

        Map<String, String> newTags = new HashMap<>();
        newTags.put("role", "reader");
        service.tagResource(instance.getDbInstanceArn(), newTags);

        Map<String, String> tags = service.listTags(instance.getDbInstanceArn());
        assertEquals(1, tags.size());
        assertEquals("reader", tags.get("role"));
    }

    @Test
    void tagResourceWorksOnSubnetGroup() {
        DocDbSubnetGroup group = service.createSubnetGroup("sg1", "desc", null);

        Map<String, String> newTags = new HashMap<>();
        newTags.put("purpose", "compat");
        service.tagResource(group.getDbSubnetGroupArn(), newTags);

        Map<String, String> tags = service.listTags(group.getDbSubnetGroupArn());
        assertEquals(1, tags.size());
        assertEquals("compat", tags.get("purpose"));
    }

    @Test
    void tagResourceThrowsForUnknownArn() {
        AwsException exception = assertThrows(AwsException.class,
                () -> service.tagResource("arn:aws:rds:us-east-1:123456789012:cluster:nonexistent",
                        Map.of("k", "v")));
        assertEquals("InvalidParameterValue", exception.getErrorCode());
    }

    @Test
    void createSubnetGroupWithInitialTagsStoresTags() {
        Map<String, String> initialTags = new HashMap<>();
        initialTags.put("purpose", "compat");
        DocDbSubnetGroup group = service.createSubnetGroup("sg-tagged", "desc", initialTags);

        assertEquals("compat", group.getTags().get("purpose"));
        Map<String, String> tags = service.listTags(group.getDbSubnetGroupArn());
        assertEquals("compat", tags.get("purpose"));
    }

    @Test
    void createClusterWithInitialTagsStoresTags() {
        Map<String, String> initialTags = new HashMap<>();
        initialTags.put("env", "prod");
        DocDbCluster cluster = service.createDbCluster("c-tagged", null, null, null, null, initialTags);

        assertEquals("prod", cluster.getTags().get("env"));
        Map<String, String> tags = service.listTags(cluster.getDbClusterArn());
        assertEquals("prod", tags.get("env"));
    }
}
