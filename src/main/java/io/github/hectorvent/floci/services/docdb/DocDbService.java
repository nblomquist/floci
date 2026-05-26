package io.github.hectorvent.floci.services.docdb;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.hectorvent.floci.config.EmulatorConfig;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.common.RegionResolver;
import io.github.hectorvent.floci.core.storage.StorageBackend;
import io.github.hectorvent.floci.core.storage.StorageFactory;
import io.github.hectorvent.floci.services.docdb.container.DocDbContainerHandle;
import io.github.hectorvent.floci.services.docdb.container.DocDbContainerManager;
import io.github.hectorvent.floci.services.docdb.model.DocDbCluster;
import io.github.hectorvent.floci.services.docdb.model.DocDbInstance;
import io.github.hectorvent.floci.services.docdb.model.DocDbSubnetGroup;
import io.github.hectorvent.floci.services.docdb.proxy.DocDbProxyManager;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
    private final DocDbContainerManager containerManager;
    private final DocDbProxyManager proxyManager;
    private final Set<Integer> usedPorts = ConcurrentHashMap.newKeySet();

    @Inject
    public DocDbService(EmulatorConfig config,
                        RegionResolver regionResolver,
                        DocDbContainerManager containerManager,
                        DocDbProxyManager proxyManager,
                        StorageFactory storageFactory) {
        this.config = config;
        this.regionResolver = regionResolver;
        this.containerManager = containerManager;
        this.proxyManager = proxyManager;
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
                 DocDbContainerManager containerManager,
                 DocDbProxyManager proxyManager,
                 StorageBackend<String, DocDbCluster> clusters,
                 StorageBackend<String, DocDbInstance> instances,
                 StorageBackend<String, DocDbSubnetGroup> subnetGroups) {
        this.config = config;
        this.regionResolver = regionResolver;
        this.containerManager = containerManager;
        this.proxyManager = proxyManager;
        this.clusters = clusters;
        this.instances = instances;
        this.subnetGroups = subnetGroups;
    }

    // ── Subnet Groups ────────────────────────────────────────────────────────

    public DocDbSubnetGroup createSubnetGroup(String name, String description,
                                               Map<String, String> initialTags) {
        if (subnetGroups.get(name).isPresent()) {
            throw new AwsException("DBSubnetGroupAlreadyExistsFault",
                    "DB subnet group " + name + " already exists.", 400);
        }
        String region = regionResolver.getDefaultRegion();
        DocDbSubnetGroup group = new DocDbSubnetGroup();
        group.setDbSubnetGroupName(name);
        group.setDbSubnetGroupDescription(description != null ? description : "");
        group.setVpcId(DEFAULT_VPC_ID);
        group.setSubnetGroupStatus("Complete");
        group.setDbSubnetGroupArn(regionResolver.buildArn("rds", region, "subgrp:" + name));
        group.getSubnets().add(new DocDbSubnetGroup.Subnet(DEFAULT_SUBNET_ID, DEFAULT_AZ, "Active"));
        if (initialTags != null) {
            group.getTags().putAll(initialTags);
        }
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
                                        String masterPassword, String subnetGroupName,
                                        Map<String, String> initialTags) {
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
        int proxyPort = allocateProxyPort();
        DocDbContainerHandle handle;
        try {
            handle = containerManager.start(id);
        } catch (RuntimeException e) {
            releaseProxyPort(proxyPort);
            throw e;
        }

        try {
            proxyManager.startProxy(id, proxyPort, handle.getHost(), handle.getPort());
        } catch (RuntimeException e) {
            try {
                containerManager.stop(handle);
            } catch (RuntimeException cleanupException) {
                LOG.warnv(cleanupException, "Failed to stop DocDB container during rollback for cluster {0}", id);
            }
            releaseProxyPort(proxyPort);
            throw e;
        }

        DocDbCluster cluster = new DocDbCluster();
        cluster.setDbClusterIdentifier(id);
        cluster.setStatus("available");
        cluster.setEngineVersion(engineVersion != null ? engineVersion : ENGINE_VERSION_DEFAULT);
        cluster.setEndpoint(endpointHost);
        cluster.setReaderEndpoint(endpointHost);
        cluster.setPort(proxyPort);
        cluster.setMasterUsername(masterUsername != null ? masterUsername : "admin");
        cluster.setMultiAz(false);
        cluster.setStorageEncrypted(true);
        cluster.setAvailabilityZone(config.defaultAvailabilityZone());
        cluster.setDbClusterArn(regionResolver.buildArn("rds", region, "cluster:" + id));
        cluster.setDbClusterResourceId("cluster-" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 24).toUpperCase());
        cluster.setDbSubnetGroup(subnetGroupName);
        cluster.setCreatedAt(Instant.now());
        cluster.setContainerId(handle.getContainerId());
        cluster.setContainerHost(handle.getHost());
        cluster.setContainerPort(handle.getPort());
        cluster.setProxyPort(proxyPort);
        if (initialTags != null) {
            cluster.getTags().putAll(initialTags);
        }

        clusters.put(id, cluster);
        LOG.infov("DocumentDB cluster {0} created, endpoint={1}:{2}", id, endpointHost, proxyPort);
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

        proxyManager.stopProxy(id);

        if (cluster.getContainerId() != null) {
            containerManager.stop(new DocDbContainerHandle(
                    cluster.getContainerId(), id,
                    cluster.getContainerHost(), cluster.getContainerPort()));
        }

        releaseProxyPort(cluster.getProxyPort());
        clusters.delete(id);
        LOG.infov("DocumentDB cluster {0} deleted", id);
    }

    // ── Instances ────────────────────────────────────────────────────────────

    public DocDbInstance createDbInstance(String id, String dbClusterIdentifier,
                                          String dbInstanceClass, String engineVersion,
                                          Map<String, String> initialTags) {
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
        if (initialTags != null) {
            instance.getTags().putAll(initialTags);
        }

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

    // ── Tags ──────────────────────────────────────────────────────────────────

    /**
     * Resolves a resource by its ARN across all three stores.
     * Returns a record holding the found resource and a mutator that persists
     * changes back to the correct store.
     */
    private TaggedResource resolveByArn(String arn) {
        // Check clusters
        for (DocDbCluster c : clusters.scan(k -> true)) {
            if (arn.equals(c.getDbClusterArn())) {
                return new TaggedResource(arn, c.getTags(),
                        () -> clusters.put(c.getDbClusterIdentifier(), c));
            }
        }
        // Check instances
        for (DocDbInstance i : instances.scan(k -> true)) {
            if (arn.equals(i.getDbInstanceArn())) {
                return new TaggedResource(arn, i.getTags(),
                        () -> instances.put(i.getDbInstanceIdentifier(), i));
            }
        }
        // Check subnet groups
        for (DocDbSubnetGroup g : subnetGroups.scan(k -> true)) {
            if (arn.equals(g.getDbSubnetGroupArn())) {
                return new TaggedResource(arn, g.getTags(),
                        () -> subnetGroups.put(g.getDbSubnetGroupName(), g));
            }
        }
        throw new AwsException("InvalidParameterValue",
                "Resource " + arn + " not found.", 400);
    }

    public Map<String, String> listTags(String arn) {
        TaggedResource resource = resolveByArn(arn);
        return new HashMap<>(resource.tags());
    }

    public void tagResource(String arn, Map<String, String> tags) {
        TaggedResource resource = resolveByArn(arn);
        resource.tags().putAll(tags);
        resource.persist().run();
    }

    public void untagResource(String arn, List<String> tagKeys) {
        TaggedResource resource = resolveByArn(arn);
        tagKeys.forEach(resource.tags()::remove);
        resource.persist().run();
    }

    private record TaggedResource(
            String arn,
            Map<String, String> tags,
            Runnable persist
    ) {}

    private int allocateProxyPort() {
        int base = config.services().docdb().proxyBasePort();
        int max = config.services().docdb().proxyMaxPort();
        for (int port = base; port <= max; port++) {
            if (usedPorts.add(port)) {
                return port;
            }
        }
        throw new AwsException("InsufficientDBClusterCapacityFault",
                "No available proxy ports in range " + base + "-" + max, 503);
    }

    private void releaseProxyPort(int port) {
        usedPorts.remove(port);
    }
}
