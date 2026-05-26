package io.github.hectorvent.floci.services.docdb;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.hectorvent.floci.config.EmulatorConfig;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.common.RegionResolver;
import io.github.hectorvent.floci.core.storage.StorageBackend;
import io.github.hectorvent.floci.core.storage.StorageFactory;
import io.github.hectorvent.floci.services.docdb.model.DocDbCluster;
import io.github.hectorvent.floci.services.docdb.model.DocDbInstance;
import io.github.hectorvent.floci.services.docdb.model.DocDbSubnetGroup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class DocDbService {

    private static final Logger LOG = Logger.getLogger(DocDbService.class);
    private static final String ENGINE_VERSION_DEFAULT = "4.0.0";
    private static final String DEFAULT_INSTANCE_CLASS = "db.r5.large";
    private static final String DEFAULT_VPC_ID = "vpc-00000000";
    private static final String DEFAULT_SUBNET_ID = "subnet-00000000";
    private static final String DEFAULT_AZ = "us-east-1a";

    private final StorageBackend<String, DocDbCluster> clusters;
    private final StorageBackend<String, DocDbInstance> instances;
    private final StorageBackend<String, DocDbSubnetGroup> subnetGroups;
    private final EmulatorConfig config;
    private final RegionResolver regionResolver;

    @Inject
    public DocDbService(EmulatorConfig config,
                        RegionResolver regionResolver,
                        StorageFactory storageFactory) {
        this.config = config;
        this.regionResolver = regionResolver;
        this.clusters = storageFactory.create("docdb", "docdb-clusters.json",
                new TypeReference<Map<String, DocDbCluster>>() {});
        this.instances = storageFactory.create("docdb", "docdb-instances.json",
                new TypeReference<Map<String, DocDbInstance>>() {});
        this.subnetGroups = storageFactory.create("docdb", "docdb-subnet-groups.json",
                new TypeReference<Map<String, DocDbSubnetGroup>>() {});
    }

    // Package-private constructor for test injection
    DocDbService(EmulatorConfig config,
                 RegionResolver regionResolver,
                 StorageBackend<String, DocDbCluster> clusters,
                 StorageBackend<String, DocDbInstance> instances,
                 StorageBackend<String, DocDbSubnetGroup> subnetGroups) {
        this.config = config;
        this.regionResolver = regionResolver;
        this.clusters = clusters;
        this.instances = instances;
        this.subnetGroups = subnetGroups;
    }

    // ── Subnet Groups ────────────────────────────────────────────────────────

    public DocDbSubnetGroup createSubnetGroup(String name, String description) {
        if (subnetGroups.get(name).isPresent()) {
            throw new AwsException("DBSubnetGroupAlreadyExistsFault",
                    "DB subnet group " + name + " already exists.", 400);
        }
        DocDbSubnetGroup group = new DocDbSubnetGroup();
        group.setDbSubnetGroupName(name);
        group.setDbSubnetGroupDescription(description != null ? description : "");
        group.setVpcId(DEFAULT_VPC_ID);
        group.setSubnetGroupStatus("Complete");
        group.getSubnets().add(new DocDbSubnetGroup.Subnet(DEFAULT_SUBNET_ID, DEFAULT_AZ, "Active"));
        subnetGroups.put(name, group);
        LOG.infov("DB subnet group {0} created", name);
        return group;
    }

    public DocDbSubnetGroup getSubnetGroup(String name) {
        return subnetGroups.get(name).orElseThrow(() ->
                new AwsException("DBSubnetGroupNotFoundFault",
                        "DB subnet group " + name + " not found.", 404));
    }

    public boolean hasSubnetGroup(String name) {
        return name != null && !name.isBlank() && subnetGroups.get(name).isPresent();
    }

    public Collection<DocDbSubnetGroup> listSubnetGroups(String filterName) {
        if (filterName != null && !filterName.isBlank()) {
            return subnetGroups.scan(k -> k.equalsIgnoreCase(filterName));
        }
        return subnetGroups.scan(k -> true);
    }

    public void deleteSubnetGroup(String name) {
        subnetGroups.get(name).orElseThrow(() ->
                new AwsException("DBSubnetGroupNotFoundFault",
                        "DB subnet group " + name + " not found.", 404));
        subnetGroups.delete(name);
        LOG.infov("DB subnet group {0} deleted", name);
    }

    // ── Clusters ─────────────────────────────────────────────────────────────

    public DocDbCluster createDbCluster(String id, String engineVersion, String masterUsername,
                                        String masterPassword, String subnetGroupName) {
        if (clusters.get(id).isPresent()) {
            throw new AwsException("DBClusterAlreadyExistsFault",
                    "DB cluster " + id + " already exists.", 400);
        }

        // Validate subnet group exists
        if (subnetGroupName != null && !subnetGroupName.isBlank()) {
            getSubnetGroup(subnetGroupName); // throws if not found
        }

        String region = regionResolver.getDefaultRegion();
        String endpointHost = config.hostname().orElse("localhost");

        DocDbCluster cluster = new DocDbCluster();
        cluster.setDbClusterIdentifier(id);
        cluster.setStatus("available");
        cluster.setEngineVersion(engineVersion != null ? engineVersion : ENGINE_VERSION_DEFAULT);
        cluster.setEndpoint(endpointHost);
        cluster.setReaderEndpoint(endpointHost);
        cluster.setPort(27017);
        cluster.setMasterUsername(masterUsername != null ? masterUsername : "admin");
        cluster.setMultiAz(false);
        cluster.setStorageEncrypted(true);
        cluster.setAvailabilityZone(config.defaultAvailabilityZone());
        cluster.setDbClusterArn(regionResolver.buildArn("rds", region, "cluster:" + id));
        cluster.setDbClusterResourceId("cluster-" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 24).toUpperCase());
        cluster.setDbSubnetGroup(subnetGroupName);
        cluster.setCreatedAt(Instant.now());

        clusters.put(id, cluster);
        LOG.infov("DocumentDB cluster {0} created, endpoint={1}:{2}", id, endpointHost, 27017);
        return cluster;
    }

    public DocDbCluster getDbCluster(String id) {
        return clusters.get(id).orElseThrow(() ->
                new AwsException("DBClusterNotFoundFault",
                        "DB cluster " + id + " not found.", 404));
    }

    public boolean hasCluster(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        return clusters.get(id).isPresent();
    }

    public boolean hasInstance(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        return instances.get(id).isPresent();
    }

    public Collection<DocDbCluster> listDbClusters(String filterId) {
        if (filterId != null && !filterId.isBlank()) {
            return clusters.scan(k -> k.equalsIgnoreCase(filterId));
        }
        return clusters.scan(k -> true);
    }

    public DocDbCluster modifyDbCluster(String id, String masterPassword) {
        DocDbCluster cluster = getDbCluster(id);
        if (masterPassword != null && !masterPassword.isBlank()) {
            cluster.setMasterUsername(masterPassword); // AWS allows MasterUserPassword change via ModifyDBCluster
        }
        clusters.put(id, cluster);
        LOG.infov("DocumentDB cluster {0} modified", id);
        return cluster;
    }

    public void deleteDbCluster(String id) {
        DocDbCluster cluster = clusters.get(id).orElseThrow(() ->
                new AwsException("DBClusterNotFoundFault",
                        "DB cluster " + id + " not found.", 404));

        if (cluster.getDbClusterMembers() != null && !cluster.getDbClusterMembers().isEmpty()) {
            throw new AwsException("InvalidDBClusterStateFault",
                    "Cannot delete DB cluster " + id + " — it still has DB instances.", 400);
        }

        cluster.setStatus("deleting");
        clusters.put(id, cluster);
        clusters.delete(id);
        LOG.infov("DocumentDB cluster {0} deleted", id);
    }

    // ── Instances ────────────────────────────────────────────────────────────

    public DocDbInstance createDbInstance(String id, String dbClusterIdentifier,
                                          String dbInstanceClass, String engineVersion) {
        if (instances.get(id).isPresent()) {
            throw new AwsException("DBInstanceAlreadyExists",
                    "DB instance " + id + " already exists.", 400);
        }

        DocDbCluster cluster = getDbCluster(dbClusterIdentifier);
        String region = regionResolver.getDefaultRegion();

        DocDbInstance instance = new DocDbInstance();
        instance.setDbInstanceIdentifier(id);
        instance.setDbClusterIdentifier(dbClusterIdentifier);
        instance.setDbInstanceClass(dbInstanceClass != null ? dbInstanceClass : DEFAULT_INSTANCE_CLASS);
        instance.setEngineVersion(engineVersion != null ? engineVersion : cluster.getEngineVersion());
        instance.setStatus("available");
        instance.setEndpoint(cluster.getEndpoint());
        instance.setPort(cluster.getPort());
        instance.setMasterUsername(cluster.getMasterUsername());
        instance.setDbInstanceArn(regionResolver.buildArn("rds", region, "db:" + id));
        instance.setDbiResourceId("db-" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 24).toUpperCase());
        instance.setCreatedAt(Instant.now());

        cluster.getDbClusterMembers().add(id);
        clusters.put(dbClusterIdentifier, cluster);

        instances.put(id, instance);
        LOG.infov("DocumentDB instance {0} created in cluster {1}", id, dbClusterIdentifier);
        return instance;
    }

    public DocDbInstance getDbInstance(String id) {
        return instances.get(id).orElseThrow(() ->
                new AwsException("DBInstanceNotFound",
                        "DB instance " + id + " not found.", 404));
    }

    public Collection<DocDbInstance> listDbInstances(String filterId) {
        if (filterId != null && !filterId.isBlank()) {
            return instances.scan(k -> k.equalsIgnoreCase(filterId));
        }
        return instances.scan(k -> true);
    }

    public DocDbInstance modifyDbInstance(String id, String dbInstanceClass) {
        DocDbInstance instance = getDbInstance(id);
        if (dbInstanceClass != null && !dbInstanceClass.isBlank()) {
            instance.setDbInstanceClass(dbInstanceClass);
        }
        instances.put(id, instance);
        LOG.infov("DocumentDB instance {0} modified", id);
        return instance;
    }

    public void deleteDbInstance(String id) {
        DocDbInstance instance = instances.get(id).orElseThrow(() ->
                new AwsException("DBInstanceNotFound",
                        "DB instance " + id + " not found.", 404));

        String clusterId = instance.getDbClusterIdentifier();
        DocDbCluster cluster = clusters.get(clusterId).orElse(null);
        if (cluster != null) {
            cluster.getDbClusterMembers().remove(id);
            clusters.put(clusterId, cluster);
        }

        instances.delete(id);
        LOG.infov("DocumentDB instance {0} deleted", id);
    }
}
