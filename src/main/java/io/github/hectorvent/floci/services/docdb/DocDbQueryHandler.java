package io.github.hectorvent.floci.services.docdb;

import io.github.hectorvent.floci.config.EmulatorConfig;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.common.AwsNamespaces;
import io.github.hectorvent.floci.core.common.AwsQueryResponse;
import io.github.hectorvent.floci.core.common.XmlBuilder;
import io.github.hectorvent.floci.services.docdb.model.DocDbCluster;
import io.github.hectorvent.floci.services.docdb.model.DocDbInstance;
import io.github.hectorvent.floci.services.docdb.model.DocDbSubnetGroup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.Collection;

@ApplicationScoped
public class DocDbQueryHandler {

    private static final Logger LOG = Logger.getLogger(DocDbQueryHandler.class);

    private final DocDbService service;
    private final EmulatorConfig config;

    @Inject
    public DocDbQueryHandler(DocDbService service, EmulatorConfig config) {
        this.service = service;
        this.config = config;
    }

    public Response handle(String action, MultivaluedMap<String, String> params) {
        LOG.infov("DocumentDB action: {0}", action);
        try {
            return switch (action) {
                // Subnet groups
                case "CreateDBSubnetGroup"    -> handleCreateSubnetGroup(params);
                case "DescribeDBSubnetGroups" -> handleDescribeSubnetGroups(params);
                case "DeleteDBSubnetGroup"    -> handleDeleteSubnetGroup(params);
                // Clusters
                case "CreateDBCluster"        -> handleCreateDbCluster(params);
                case "DescribeDBClusters"     -> handleDescribeDbClusters(params);
                case "DeleteDBCluster"        -> handleDeleteDbCluster(params);
                case "ModifyDBCluster"        -> handleModifyDbCluster(params);
                // Instances
                case "CreateDBInstance"       -> handleCreateDbInstance(params);
                case "DescribeDBInstances"    -> handleDescribeDbInstances(params);
                case "DeleteDBInstance"       -> handleDeleteDbInstance(params);
                case "ModifyDBInstance"       -> handleModifyDbInstance(params);
                default -> AwsQueryResponse.error("UnsupportedOperation",
                        "Operation " + action + " is not supported by DocumentDB.", AwsNamespaces.RDS, 400);
            };
        } catch (AwsException e) {
            return AwsQueryResponse.error(e.getErrorCode(), e.getMessage(), AwsNamespaces.RDS, e.getHttpStatus());
        } catch (Exception e) {
            LOG.errorv(e, "Unexpected error in DocumentDB {0}", action);
            return Response.serverError().entity("Unexpected error: " + e.getMessage()).build();
        }
    }

    // ── Subnet Groups ─────────────────────────────────────────────────────────

    private Response handleCreateSubnetGroup(MultivaluedMap<String, String> params) {
        String name = params.getFirst("DBSubnetGroupName");
        if (name == null || name.isBlank()) {
            return AwsQueryResponse.error("InvalidParameterValue",
                    "DBSubnetGroupName is required.", AwsNamespaces.RDS, 400);
        }
        String description = params.getFirst("DBSubnetGroupDescription");
        DocDbSubnetGroup group = service.createSubnetGroup(name, description);
        return Response.ok(AwsQueryResponse.envelope("CreateDBSubnetGroup", AwsNamespaces.RDS,
                subnetGroupXml(group))).build();
    }

    private Response handleDescribeSubnetGroups(MultivaluedMap<String, String> params) {
        String filterName = params.getFirst("DBSubnetGroupName");
        if (filterName != null && !filterName.isBlank()) {
            service.getSubnetGroup(filterName); // throws if not found
        }

        Collection<DocDbSubnetGroup> result = service.listSubnetGroups(filterName);

        XmlBuilder xml = new XmlBuilder().start("DBSubnetGroups");
        for (DocDbSubnetGroup g : result) {
            xml.start("DBSubnetGroup").raw(subnetGroupInnerXml(g)).end("DBSubnetGroup");
        }
        xml.end("DBSubnetGroups").start("Marker").end("Marker");
        return Response.ok(AwsQueryResponse.envelope("DescribeDBSubnetGroups", AwsNamespaces.RDS, xml.build())).build();
    }

    private Response handleDeleteSubnetGroup(MultivaluedMap<String, String> params) {
        String name = params.getFirst("DBSubnetGroupName");
        if (name == null || name.isBlank()) {
            return AwsQueryResponse.error("InvalidParameterValue",
                    "DBSubnetGroupName is required.", AwsNamespaces.RDS, 400);
        }
        service.deleteSubnetGroup(name);
        return Response.ok(AwsQueryResponse.envelopeNoResult("DeleteDBSubnetGroup", AwsNamespaces.RDS)).build();
    }

    // ── Clusters ──────────────────────────────────────────────────────────────

    private Response handleCreateDbCluster(MultivaluedMap<String, String> params) {
        String id = params.getFirst("DBClusterIdentifier");
        if (id == null || id.isBlank()) {
            return AwsQueryResponse.error("InvalidParameterValue",
                    "DBClusterIdentifier is required.", AwsNamespaces.RDS, 400);
        }
        String engineVersion = params.getFirst("EngineVersion");
        String masterUsername = params.getFirst("MasterUsername");
        String masterPassword = params.getFirst("MasterUserPassword");
        String subnetGroupName = params.getFirst("DBSubnetGroupName");

        DocDbCluster cluster = service.createDbCluster(id, engineVersion, masterUsername, masterPassword, subnetGroupName);
        return Response.ok(AwsQueryResponse.envelope("CreateDBCluster", AwsNamespaces.RDS,
                clusterXml(cluster))).build();
    }

    private Response handleDescribeDbClusters(MultivaluedMap<String, String> params) {
        String filterId = params.getFirst("DBClusterIdentifier");
        if (filterId == null || filterId.isBlank()) {
            filterId = extractFilterValue(params, "db-cluster-id");
        }

        if (filterId != null && !filterId.isBlank()) {
            service.getDbCluster(filterId); // throws DBClusterNotFoundFault if absent
        }

        Collection<DocDbCluster> result = service.listDbClusters(filterId);

        XmlBuilder xml = new XmlBuilder().start("DBClusters");
        for (DocDbCluster c : result) {
            xml.start("DBCluster").raw(clusterInnerXml(c)).end("DBCluster");
        }
        xml.end("DBClusters").start("Marker").end("Marker");
        return Response.ok(AwsQueryResponse.envelope("DescribeDBClusters", AwsNamespaces.RDS, xml.build())).build();
    }

    private Response handleDeleteDbCluster(MultivaluedMap<String, String> params) {
        String id = params.getFirst("DBClusterIdentifier");
        if (id == null || id.isBlank()) {
            return AwsQueryResponse.error("InvalidParameterValue",
                    "DBClusterIdentifier is required.", AwsNamespaces.RDS, 400);
        }
        DocDbCluster cluster = service.getDbCluster(id);
        service.deleteDbCluster(id);
        return Response.ok(AwsQueryResponse.envelope("DeleteDBCluster", AwsNamespaces.RDS,
                clusterXml(cluster))).build();
    }

    private Response handleModifyDbCluster(MultivaluedMap<String, String> params) {
        String id = params.getFirst("DBClusterIdentifier");
        if (id == null || id.isBlank()) {
            return AwsQueryResponse.error("InvalidParameterValue",
                    "DBClusterIdentifier is required.", AwsNamespaces.RDS, 400);
        }
        // DocumentDB ModifyDBCluster only supports MasterUserPassword in the spec
        String masterPassword = params.getFirst("MasterUserPassword");
        DocDbCluster cluster = service.modifyDbCluster(id, masterPassword);
        return Response.ok(AwsQueryResponse.envelope("ModifyDBCluster", AwsNamespaces.RDS,
                clusterXml(cluster))).build();
    }

    // ── Instances ─────────────────────────────────────────────────────────────

    private Response handleCreateDbInstance(MultivaluedMap<String, String> params) {
        String id = params.getFirst("DBInstanceIdentifier");
        if (id == null || id.isBlank()) {
            return AwsQueryResponse.error("InvalidParameterValue",
                    "DBInstanceIdentifier is required.", AwsNamespaces.RDS, 400);
        }
        String dbClusterIdentifier = params.getFirst("DBClusterIdentifier");
        if (dbClusterIdentifier == null || dbClusterIdentifier.isBlank()) {
            return AwsQueryResponse.error("InvalidParameterValue",
                    "DBClusterIdentifier is required for DocumentDB instances.", AwsNamespaces.RDS, 400);
        }
        String dbInstanceClass = params.getFirst("DBInstanceClass");
        String engineVersion = params.getFirst("EngineVersion");

        DocDbInstance instance = service.createDbInstance(id, dbClusterIdentifier,
                dbInstanceClass, engineVersion);
        return Response.ok(AwsQueryResponse.envelope("CreateDBInstance", AwsNamespaces.RDS,
                instanceXml(instance))).build();
    }

    private Response handleDescribeDbInstances(MultivaluedMap<String, String> params) {
        String filterId = params.getFirst("DBInstanceIdentifier");
        if (filterId == null || filterId.isBlank()) {
            filterId = extractFilterValue(params, "db-instance-id");
        }

        if (filterId != null && !filterId.isBlank()) {
            service.getDbInstance(filterId); // throws DBInstanceNotFound if absent
        }

        Collection<DocDbInstance> result = service.listDbInstances(filterId);

        XmlBuilder xml = new XmlBuilder().start("DBInstances");
        for (DocDbInstance i : result) {
            xml.start("DBInstance").raw(instanceInnerXml(i)).end("DBInstance");
        }
        xml.end("DBInstances").start("Marker").end("Marker");
        return Response.ok(AwsQueryResponse.envelope("DescribeDBInstances", AwsNamespaces.RDS, xml.build())).build();
    }

    private Response handleDeleteDbInstance(MultivaluedMap<String, String> params) {
        String id = params.getFirst("DBInstanceIdentifier");
        if (id == null || id.isBlank()) {
            return AwsQueryResponse.error("InvalidParameterValue",
                    "DBInstanceIdentifier is required.", AwsNamespaces.RDS, 400);
        }
        DocDbInstance instance = service.getDbInstance(id);
        service.deleteDbInstance(id);
        return Response.ok(AwsQueryResponse.envelope("DeleteDBInstance", AwsNamespaces.RDS,
                instanceXml(instance))).build();
    }

    private Response handleModifyDbInstance(MultivaluedMap<String, String> params) {
        String id = params.getFirst("DBInstanceIdentifier");
        if (id == null || id.isBlank()) {
            return AwsQueryResponse.error("InvalidParameterValue",
                    "DBInstanceIdentifier is required.", AwsNamespaces.RDS, 400);
        }
        String dbInstanceClass = params.getFirst("DBInstanceClass");
        DocDbInstance instance = service.modifyDbInstance(id, dbInstanceClass);
        return Response.ok(AwsQueryResponse.envelope("ModifyDBInstance", AwsNamespaces.RDS,
                instanceXml(instance))).build();
    }

    // ── XML builders ──────────────────────────────────────────────────────────

    private String subnetGroupXml(DocDbSubnetGroup g) {
        return new XmlBuilder().start("DBSubnetGroup").raw(subnetGroupInnerXml(g)).end("DBSubnetGroup").build();
    }

    private String subnetGroupInnerXml(DocDbSubnetGroup g) {
        XmlBuilder xml = new XmlBuilder()
                .elem("DBSubnetGroupName", g.getDbSubnetGroupName())
                .elem("DBSubnetGroupDescription", g.getDbSubnetGroupDescription())
                .elem("VpcId", g.getVpcId())
                .elem("SubnetGroupStatus", g.getSubnetGroupStatus())
                .start("Subnets");
        if (g.getSubnets() != null) {
            for (DocDbSubnetGroup.Subnet s : g.getSubnets()) {
                xml.start("Subnet")
                   .elem("SubnetIdentifier", s.getSubnetIdentifier())
                   .start("SubnetAvailabilityZone")
                     .elem("Name", s.getSubnetAvailabilityZoneName())
                   .end("SubnetAvailabilityZone")
                   .elem("SubnetStatus", s.getSubnetStatus())
                   .end("Subnet");
            }
        }
        xml.end("Subnets");
        return xml.build();
    }

    private String clusterXml(DocDbCluster c) {
        return new XmlBuilder().start("DBCluster").raw(clusterInnerXml(c)).end("DBCluster").build();
    }

    private String clusterInnerXml(DocDbCluster c) {
        XmlBuilder xml = new XmlBuilder()
                .elem("DBClusterIdentifier", c.getDbClusterIdentifier())
                .elem("Status", c.getStatus())
                .elem("Engine", "docdb")
                .elem("EngineVersion", c.getEngineVersion())
                .elem("Endpoint", c.getEndpoint())
                .elem("ReaderEndpoint", c.getReaderEndpoint())
                .elem("Port", c.getPort())
                .elem("MasterUsername", c.getMasterUsername())
                .elem("MultiAZ", c.isMultiAz())
                .elem("StorageEncrypted", c.isStorageEncrypted())
                .elem("AvailabilityZone", config.defaultAvailabilityZone())
                .elem("DbClusterResourceId", c.getDbClusterResourceId())
                .elem("DBClusterArn", c.getDbClusterArn())
                .start("DBClusterMembers");
        if (c.getDbClusterMembers() != null) {
            for (String memberId : c.getDbClusterMembers()) {
                xml.start("member")
                   .elem("DBInstanceIdentifier", memberId)
                   .elem("IsClusterWriter", true)
                   .end("member");
            }
        }
        xml.end("DBClusterMembers");

        if (c.getDbSubnetGroup() != null) {
            xml.start("DBSubnetGroup")
               .elem("DBSubnetGroupName", c.getDbSubnetGroup())
               .end("DBSubnetGroup");
            xml.start("VpcSecurityGroups").end("VpcSecurityGroups");
        }

        return xml.build();
    }

    private String instanceXml(DocDbInstance i) {
        return new XmlBuilder().start("DBInstance").raw(instanceInnerXml(i)).end("DBInstance").build();
    }

    private String instanceInnerXml(DocDbInstance i) {
        return new XmlBuilder()
                .elem("DBInstanceIdentifier", i.getDbInstanceIdentifier())
                .elem("DBClusterIdentifier", i.getDbClusterIdentifier())
                .elem("DBInstanceClass", i.getDbInstanceClass())
                .elem("DBInstanceStatus", i.getStatus())
                .elem("Engine", "docdb")
                .elem("EngineVersion", i.getEngineVersion())
                .start("Endpoint")
                  .elem("Address", i.getEndpoint())
                  .elem("Port", i.getPort())
                .end("Endpoint")
                .elem("MultiAZ", false)
                .elem("StorageEncrypted", true)
                .elem("AvailabilityZone", config.defaultAvailabilityZone())
                .elem("DbiResourceId", i.getDbiResourceId())
                .elem("DBInstanceArn", i.getDbInstanceArn())
                .build();
    }

    private static String extractFilterValue(MultivaluedMap<String, String> params, String filterName) {
        for (int i = 1; ; i++) {
            String name = params.getFirst("Filters.Filter." + i + ".Name");
            if (name == null) {
                break;
            }
            if (filterName.equals(name)) {
                return params.getFirst("Filters.Filter." + i + ".Values.Value.1");
            }
        }
        return null;
    }
}
