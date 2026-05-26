package io.github.hectorvent.floci.services.docdb;

import io.github.hectorvent.floci.config.EmulatorConfig;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.services.docdb.model.DocDbCluster;
import io.github.hectorvent.floci.services.docdb.model.DocDbInstance;
import io.github.hectorvent.floci.services.docdb.model.DocDbSubnetGroup;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DocDbQueryHandlerTest {

    private DocDbService service;
    private DocDbQueryHandler handler;

    @BeforeEach
    void setUp() {
        service = mock(DocDbService.class);
        EmulatorConfig config = mock(EmulatorConfig.class);
        EmulatorConfig.ServicesConfig servicesConfig = mock(EmulatorConfig.ServicesConfig.class);
        EmulatorConfig.DocDbServiceConfig docdbConfig = mock(EmulatorConfig.DocDbServiceConfig.class);
        when(config.services()).thenReturn(servicesConfig);
        when(config.defaultAvailabilityZone()).thenReturn("us-east-1a");
        handler = new DocDbQueryHandler(service, config);
    }

    // ── DBSubnetGroups XML tag ────────────────────────────────────────────────

    @Test
    void describeDbSubnetGroups_usesDBSubnetGroupTag() {
        DocDbSubnetGroup group = makeSubnetGroup("mysg");
        when(service.listSubnetGroups(null)).thenReturn(List.of(group));

        Response response = handler.handle("DescribeDBSubnetGroups", params());

        String body = (String) response.getEntity();
        assertTrue(body.contains("<DBSubnetGroup>"), "Expected <DBSubnetGroup> element in response");
        assertFalse(body.contains("<member><DBSubnetGroupName>"), "Did not expect <member> wrapping DBSubnetGroup");
    }

    @Test
    void createDBSubnetGroup_requiresName() {
        Response response = handler.handle("CreateDBSubnetGroup", params());

        assertEquals(400, response.getStatus());
        assertTrue(((String) response.getEntity()).contains("DBSubnetGroupName is required"));
    }

    @Test
    void createDBSubnetGroup_passesArgumentsToService() {
        DocDbSubnetGroup group = makeSubnetGroup("sg1");
        when(service.createSubnetGroup("sg1", "test desc")).thenReturn(group);

        MultivaluedMap<String, String> p = params();
        p.add("DBSubnetGroupName", "sg1");
        p.add("DBSubnetGroupDescription", "test desc");
        Response response = handler.handle("CreateDBSubnetGroup", p);

        verify(service).createSubnetGroup("sg1", "test desc");
        String body = (String) response.getEntity();
        assertTrue(body.contains("<DBSubnetGroupName>sg1</DBSubnetGroupName>"));
    }

    @Test
    void deleteDBSubnetGroup_requiresName() {
        Response response = handler.handle("DeleteDBSubnetGroup", params());

        assertEquals(400, response.getStatus());
        assertTrue(((String) response.getEntity()).contains("DBSubnetGroupName is required"));
    }

    @Test
    void deleteDBSubnetGroup_returnsNoResult() {
        MultivaluedMap<String, String> p = params();
        p.add("DBSubnetGroupName", "sg1");
        Response response = handler.handle("DeleteDBSubnetGroup", p);

        String body = (String) response.getEntity();
        assertTrue(body.contains("DeleteDBSubnetGroupResponse"));
        assertTrue(body.contains("ResponseMetadata"));
    }

    // ── DBClusters XML tag ────────────────────────────────────────────────────

    @Test
    void describeDbClusters_usesDBClusterTag() {
        DocDbCluster cluster = makeCluster("mycluster");
        when(service.listDbClusters(null)).thenReturn(List.of(cluster));

        Response response = handler.handle("DescribeDBClusters", params());

        String body = (String) response.getEntity();
        assertTrue(body.contains("<DBCluster>"), "Expected <DBCluster> element in response");
        assertFalse(body.contains("<member><DBClusterIdentifier>"), "Did not expect <member> wrapping DBCluster");
    }

    @Test
    void describeDbClusters_filterByFiltersParam() {
        when(service.listDbClusters("mycluster")).thenReturn(List.of());

        MultivaluedMap<String, String> p = params();
        p.add("Filters.Filter.1.Name", "db-cluster-id");
        p.add("Filters.Filter.1.Values.Value.1", "mycluster");
        handler.handle("DescribeDBClusters", p);

        verify(service).listDbClusters("mycluster");
    }

    @Test
    void createDBCluster_requiresIdentifier() {
        Response response = handler.handle("CreateDBCluster", params());

        assertEquals(400, response.getStatus());
        assertTrue(((String) response.getEntity()).contains("DBClusterIdentifier is required"));
    }

    @Test
    void createDBCluster_passesArgumentsToService() {
        DocDbCluster cluster = makeCluster("c1");
        when(service.createDbCluster("c1", "4.0.0", "admin", "secret", "sg1")).thenReturn(cluster);

        MultivaluedMap<String, String> p = params();
        p.add("DBClusterIdentifier", "c1");
        p.add("EngineVersion", "4.0.0");
        p.add("MasterUsername", "admin");
        p.add("MasterUserPassword", "secret");
        p.add("DBSubnetGroupName", "sg1");
        Response response = handler.handle("CreateDBCluster", p);

        verify(service).createDbCluster("c1", "4.0.0", "admin", "secret", "sg1");
        assertEquals(200, response.getStatus());
    }

    @Test
    void deleteDBCluster_requiresIdentifier() {
        Response response = handler.handle("DeleteDBCluster", params());

        assertEquals(400, response.getStatus());
        assertTrue(((String) response.getEntity()).contains("DBClusterIdentifier is required"));
    }

    @Test
    void modifyDBCluster_requiresIdentifier() {
        Response response = handler.handle("ModifyDBCluster", params());

        assertEquals(400, response.getStatus());
        assertTrue(((String) response.getEntity()).contains("DBClusterIdentifier is required"));
    }

    // ── DBInstances XML tag ───────────────────────────────────────────────────

    @Test
    void describeDbInstances_usesDBInstanceTag() {
        DocDbInstance instance = makeInstance("myinstance");
        when(service.listDbInstances(null)).thenReturn(List.of(instance));

        Response response = handler.handle("DescribeDBInstances", params());

        String body = (String) response.getEntity();
        assertTrue(body.contains("<DBInstance>"), "Expected <DBInstance> element in response");
        assertFalse(body.contains("<member><DBInstanceIdentifier>"), "Did not expect <member> wrapping DBInstance");
    }

    @Test
    void describeDbInstances_filterByDirectIdentifier() {
        DocDbInstance instance = makeInstance("myinstance");
        when(service.listDbInstances("myinstance")).thenReturn(List.of(instance));

        MultivaluedMap<String, String> p = params();
        p.add("DBInstanceIdentifier", "myinstance");
        handler.handle("DescribeDBInstances", p);

        verify(service).listDbInstances("myinstance");
    }

    @Test
    void describeDbInstances_filterByFiltersParam() {
        when(service.listDbInstances("myinstance")).thenReturn(List.of());

        MultivaluedMap<String, String> p = params();
        p.add("Filters.Filter.1.Name", "db-instance-id");
        p.add("Filters.Filter.1.Values.Value.1", "myinstance");
        handler.handle("DescribeDBInstances", p);

        verify(service).listDbInstances("myinstance");
    }

    @Test
    void createDBInstance_requiresIdentifier() {
        Response response = handler.handle("CreateDBInstance", params());

        assertEquals(400, response.getStatus());
        assertTrue(((String) response.getEntity()).contains("DBInstanceIdentifier is required"));
    }

    @Test
    void createDBInstance_requiresClusterIdentifier() {
        MultivaluedMap<String, String> p = params();
        p.add("DBInstanceIdentifier", "i1");
        Response response = handler.handle("CreateDBInstance", p);

        assertEquals(400, response.getStatus());
        assertTrue(((String) response.getEntity()).contains("DBClusterIdentifier is required"));
    }

    @Test
    void createDBInstance_passesArgumentsToService() {
        DocDbInstance instance = makeInstance("i1");
        when(service.createDbInstance("i1", "c1", "db.r5.large", "4.0.0")).thenReturn(instance);

        MultivaluedMap<String, String> p = params();
        p.add("DBInstanceIdentifier", "i1");
        p.add("DBClusterIdentifier", "c1");
        p.add("DBInstanceClass", "db.r5.large");
        p.add("EngineVersion", "4.0.0");
        Response response = handler.handle("CreateDBInstance", p);

        verify(service).createDbInstance("i1", "c1", "db.r5.large", "4.0.0");
        assertEquals(200, response.getStatus());
    }

    @Test
    void deleteDBInstance_requiresIdentifier() {
        Response response = handler.handle("DeleteDBInstance", params());

        assertEquals(400, response.getStatus());
        assertTrue(((String) response.getEntity()).contains("DBInstanceIdentifier is required"));
    }

    @Test
    void modifyDBInstance_requiresIdentifier() {
        Response response = handler.handle("ModifyDBInstance", params());

        assertEquals(400, response.getStatus());
        assertTrue(((String) response.getEntity()).contains("DBInstanceIdentifier is required"));
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Test
    void unsupportedOperationReturnsQueryError() {
        Response response = handler.handle("NoSuchAction", params());

        assertEquals(400, response.getStatus());
        assertTrue(((String) response.getEntity()).contains("UnsupportedOperation"));
    }

    @Test
    void serviceAwsExceptionWrapsToXmlError() {
        when(service.createDbCluster(eq("fail"), any(), any(), any(), any()))
                .thenThrow(new AwsException("DBClusterNotFoundFault", "cluster not found", 404));

        MultivaluedMap<String, String> p = params();
        p.add("DBClusterIdentifier", "fail");
        Response response = handler.handle("CreateDBCluster", p);

        assertEquals(404, response.getStatus());
        String body = (String) response.getEntity();
        assertTrue(body.contains("DBClusterNotFoundFault"));
        assertTrue(body.contains("cluster not found"));
    }

    // ── XML shape validation ──────────────────────────────────────────────────

    @Test
    void createDBCluster_responseContainsDocdbEngine() {
        DocDbCluster cluster = makeCluster("c1");
        when(service.createDbCluster(any(), any(), any(), any(), any())).thenReturn(cluster);

        MultivaluedMap<String, String> p = params();
        p.add("DBClusterIdentifier", "c1");
        Response response = handler.handle("CreateDBCluster", p);

        String body = (String) response.getEntity();
        assertTrue(body.contains("<Engine>docdb</Engine>"));
    }

    @Test
    void createDBInstance_responseContainsEndpointBlock() {
        DocDbInstance instance = makeInstance("i1");
        when(service.createDbInstance(any(), any(), any(), any())).thenReturn(instance);

        MultivaluedMap<String, String> p = params();
        p.add("DBInstanceIdentifier", "i1");
        p.add("DBClusterIdentifier", "c1");
        Response response = handler.handle("CreateDBInstance", p);

        String body = (String) response.getEntity();
        assertTrue(body.contains("<Endpoint>"));
        assertTrue(body.contains("<Address>"));
        assertTrue(body.contains("<Port>"));
    }

    @Test
    void createDBSubnetGroup_responseContainsSubnetXml() {
        DocDbSubnetGroup group = makeSubnetGroup("sg1");
        when(service.createSubnetGroup(any(), any())).thenReturn(group);

        MultivaluedMap<String, String> p = params();
        p.add("DBSubnetGroupName", "sg1");
        p.add("DBSubnetGroupDescription", "desc");
        Response response = handler.handle("CreateDBSubnetGroup", p);

        String body = (String) response.getEntity();
        assertTrue(body.contains("<Subnets>"));
        assertTrue(body.contains("<SubnetAvailabilityZone>"));
        assertTrue(body.contains("<SubnetIdentifier>"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static MultivaluedMap<String, String> params() {
        return new MultivaluedHashMap<>();
    }

    private static DocDbCluster makeCluster(String id) {
        DocDbCluster c = new DocDbCluster();
        c.setDbClusterIdentifier(id);
        c.setStatus("available");
        c.setEngineVersion("4.0.0");
        c.setEndpoint("localhost");
        c.setPort(27017);
        c.setMasterUsername("admin");
        c.setDbClusterArn("arn:aws:rds:us-east-1:123456789012:cluster:" + id);
        c.setDbClusterResourceId("cluster-" + id);
        c.setCreatedAt(Instant.now());
        return c;
    }

    private static DocDbInstance makeInstance(String id) {
        DocDbInstance i = new DocDbInstance();
        i.setDbInstanceIdentifier(id);
        i.setDbClusterIdentifier("c1");
        i.setDbInstanceClass("db.r5.large");
        i.setStatus("available");
        i.setEngineVersion("4.0.0");
        i.setEndpoint("localhost");
        i.setPort(27017);
        i.setMasterUsername("admin");
        i.setDbInstanceArn("arn:aws:rds:us-east-1:123456789012:db:" + id);
        i.setDbiResourceId("db-" + id);
        i.setCreatedAt(Instant.now());
        return i;
    }

    private static DocDbSubnetGroup makeSubnetGroup(String name) {
        DocDbSubnetGroup g = new DocDbSubnetGroup();
        g.setDbSubnetGroupName(name);
        g.setDbSubnetGroupDescription("test description");
        g.setVpcId("vpc-00000000");
        g.setSubnetGroupStatus("Complete");
        g.getSubnets().add(new DocDbSubnetGroup.Subnet("subnet-00000000", "us-east-1a", "Active"));
        return g;
    }
}
