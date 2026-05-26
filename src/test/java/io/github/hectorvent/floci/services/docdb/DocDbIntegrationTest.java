package io.github.hectorvent.floci.services.docdb;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

@QuarkusTest
class DocDbIntegrationTest {

    private static final String FORM = "application/x-www-form-urlencoded";

    // Fake SigV4 header — service name "docdb" should route to the DocumentDB Query handler.
    private static final String AUTH =
            "AWS4-HMAC-SHA256 Credential=test/20260516/us-east-1/docdb/aws4_request, " +
            "SignedHeaders=content-type;host, Signature=test";

    // ── Routing ───────────────────────────────────────────────────────────────────

    @Test
    void signedDocDbQueryRequestsUseDocumentDbEntrypoint() {
        given()
            .header("Authorization", AUTH)
            .contentType(FORM)
            .formParam("Action", "CreateDBSubnetGroup")
        .when().post("/")
        .then()
            .statusCode(400)
            .body(containsString("<Code>InvalidParameterValue</Code>"))
            .body(containsString("DBSubnetGroupName is required."));
    }

    @Test
    void docDbEngineRoutedToDocDbHandlerNotRds() {
        given()
            .header("Authorization", rdsAuthorization())
            .contentType(FORM)
            .formParam("Action", "CreateDBCluster")
            .formParam("DBClusterIdentifier", "phase-1-docdb-cluster-int")
            .formParam("Engine", "docdb")
        .when().post("/")
        .then()
            .statusCode(200)
            .body(containsString("<Engine>docdb</Engine>"))
            .body(containsString("<DBClusterIdentifier>phase-1-docdb-cluster-int</DBClusterIdentifier>"));
    }

    // ── Subnet Group Lifecycle ────────────────────────────────────────────────────

    @Test
    void subnetGroupLifecycleThroughHttp() {
        String sgName = "harden-sg-lifecycle";

        // Create
        given()
            .header("Authorization", AUTH)
            .contentType(FORM)
            .formParam("Action", "CreateDBSubnetGroup")
            .formParam("DBSubnetGroupName", sgName)
            .formParam("DBSubnetGroupDescription", "harden test sg")
        .when().post("/")
        .then()
            .statusCode(200)
            .body(containsString("<DBSubnetGroupName>" + sgName + "</DBSubnetGroupName>"));

        // Describe by name
        given()
            .header("Authorization", AUTH)
            .contentType(FORM)
            .formParam("Action", "DescribeDBSubnetGroups")
            .formParam("DBSubnetGroupName", sgName)
        .when().post("/")
        .then()
            .statusCode(200)
            .body(containsString("<DBSubnetGroupName>" + sgName + "</DBSubnetGroupName>"));

        // Describe all (should include our sg)
        given()
            .header("Authorization", AUTH)
            .contentType(FORM)
            .formParam("Action", "DescribeDBSubnetGroups")
        .when().post("/")
        .then()
            .statusCode(200)
            .body(containsString("<DBSubnetGroupName>" + sgName + "</DBSubnetGroupName>"));

        // Delete
        given()
            .header("Authorization", AUTH)
            .contentType(FORM)
            .formParam("Action", "DeleteDBSubnetGroup")
            .formParam("DBSubnetGroupName", sgName)
        .when().post("/")
        .then()
            .statusCode(200)
            .body(containsString("DeleteDBSubnetGroupResponse"));
    }

    @Test
    void subnetGroupNotFoundReturnsError() {
        given()
            .header("Authorization", AUTH)
            .contentType(FORM)
            .formParam("Action", "DescribeDBSubnetGroups")
            .formParam("DBSubnetGroupName", "nonexistent-sg")
        .when().post("/")
        .then()
            .statusCode(404)
            .body(containsString("DBSubnetGroupNotFoundFault"));
    }

    @Test
    void duplicateSubnetGroupReturnsError() {
        String sgName = "harden-sg-duplicate";

        // Create first time
        given()
            .header("Authorization", AUTH)
            .contentType(FORM)
            .formParam("Action", "CreateDBSubnetGroup")
            .formParam("DBSubnetGroupName", sgName)
            .formParam("DBSubnetGroupDescription", "first")
        .when().post("/")
        .then()
            .statusCode(200);

        // Create duplicate
        given()
            .header("Authorization", AUTH)
            .contentType(FORM)
            .formParam("Action", "CreateDBSubnetGroup")
            .formParam("DBSubnetGroupName", sgName)
            .formParam("DBSubnetGroupDescription", "duplicate")
        .when().post("/")
        .then()
            .statusCode(400)
            .body(containsString("DBSubnetGroupAlreadyExistsFault"));
    }

    @Test
    void deleteNonexistentSubnetGroupReturnsError() {
        given()
            .header("Authorization", AUTH)
            .contentType(FORM)
            .formParam("Action", "DeleteDBSubnetGroup")
            .formParam("DBSubnetGroupName", "nonexistent-sg")
        .when().post("/")
        .then()
            .statusCode(404)
            .body(containsString("DBSubnetGroupNotFoundFault"));
    }

    // ── Cluster Lifecycle ────────────────────────────────────────────────────────

    @Test
    void clusterLifecycleThroughHttp() {
        String sgName = "harden-cluster-sg";
        String clusterId = "harden-cluster-lifecycle";

        // Create subnet group first
        given()
            .header("Authorization", AUTH)
            .contentType(FORM)
            .formParam("Action", "CreateDBSubnetGroup")
            .formParam("DBSubnetGroupName", sgName)
            .formParam("DBSubnetGroupDescription", "for cluster lifecycle")
        .when().post("/")
        .then()
            .statusCode(200);

        // Create cluster via rds-scoped auth with Engine=docdb
        given()
            .header("Authorization", rdsAuthorization())
            .contentType(FORM)
            .formParam("Action", "CreateDBCluster")
            .formParam("DBClusterIdentifier", clusterId)
            .formParam("Engine", "docdb")
            .formParam("MasterUsername", "admin")
            .formParam("MasterUserPassword", "secret")
            .formParam("DBSubnetGroupName", sgName)
        .when().post("/")
        .then()
            .statusCode(200)
            .body(containsString("<DBClusterIdentifier>" + clusterId + "</DBClusterIdentifier>"))
            .body(containsString("<Engine>docdb</Engine>"))
            .body(containsString("<Endpoint>"));

        // Describe by identifier (via rds auth + Engine=docdb)
        given()
            .header("Authorization", rdsAuthorization())
            .contentType(FORM)
            .formParam("Action", "DescribeDBClusters")
            .formParam("DBClusterIdentifier", clusterId)
            .formParam("Engine", "docdb")
        .when().post("/")
        .then()
            .statusCode(200)
            .body(containsString("<DBClusterIdentifier>" + clusterId + "</DBClusterIdentifier>"));

        // Describe all
        given()
            .header("Authorization", AUTH)
            .contentType(FORM)
            .formParam("Action", "DescribeDBClusters")
        .when().post("/")
        .then()
            .statusCode(200)
            .body(containsString("<DBClusterIdentifier>" + clusterId + "</DBClusterIdentifier>"));

        // Modify cluster
        given()
            .header("Authorization", rdsAuthorization())
            .contentType(FORM)
            .formParam("Action", "ModifyDBCluster")
            .formParam("DBClusterIdentifier", clusterId)
            .formParam("Engine", "docdb")
            .formParam("MasterUserPassword", "new-secret")
        .when().post("/")
        .then()
            .statusCode(200)
            .body(containsString("<DBClusterIdentifier>" + clusterId + "</DBClusterIdentifier>"));

        // Delete cluster (via rds scoped so the routing uses Engine=docdb)
        given()
            .header("Authorization", rdsAuthorization())
            .contentType(FORM)
            .formParam("Action", "DeleteDBCluster")
            .formParam("DBClusterIdentifier", clusterId)
            .formParam("Engine", "docdb")
        .when().post("/")
        .then()
            .statusCode(200)
            .body(containsString("DeleteDBClusterResponse"))
            .body(containsString("<DBClusterIdentifier>" + clusterId + "</DBClusterIdentifier>"));

        // Cleanup subnet group
        given()
            .header("Authorization", AUTH)
            .contentType(FORM)
            .formParam("Action", "DeleteDBSubnetGroup")
            .formParam("DBSubnetGroupName", sgName)
        .when().post("/")
        .then()
            .statusCode(200);
    }

    @Test
    void clusterNotFoundReturnsError() {
        given()
            .header("Authorization", AUTH)
            .contentType(FORM)
            .formParam("Action", "DescribeDBClusters")
            .formParam("DBClusterIdentifier", "nonexistent-cluster")
        .when().post("/")
        .then()
            .statusCode(404)
            .body(containsString("DBClusterNotFoundFault"));
    }

    @Test
    void deleteClusterWithInstancesFails() {
        String sgName = "harden-del-inst-sg";
        String clusterId = "harden-del-inst-cluster";

        // Create subnet group
        given()
            .header("Authorization", AUTH)
            .contentType(FORM)
            .formParam("Action", "CreateDBSubnetGroup")
            .formParam("DBSubnetGroupName", sgName)
            .formParam("DBSubnetGroupDescription", "for delete-with-instances")
        .when().post("/")
        .then()
            .statusCode(200);

        // Create cluster
        given()
            .header("Authorization", rdsAuthorization())
            .contentType(FORM)
            .formParam("Action", "CreateDBCluster")
            .formParam("DBClusterIdentifier", clusterId)
            .formParam("Engine", "docdb")
            .formParam("DBSubnetGroupName", sgName)
        .when().post("/")
        .then()
            .statusCode(200);

        // Create instance in cluster
        given()
            .header("Authorization", AUTH)
            .contentType(FORM)
            .formParam("Action", "CreateDBInstance")
            .formParam("DBInstanceIdentifier", clusterId + "-instance")
            .formParam("DBClusterIdentifier", clusterId)
        .when().post("/")
        .then()
            .statusCode(200);

        // Delete cluster should fail
        given()
            .header("Authorization", rdsAuthorization())
            .contentType(FORM)
            .formParam("Action", "DeleteDBCluster")
            .formParam("DBClusterIdentifier", clusterId)
            .formParam("Engine", "docdb")
        .when().post("/")
        .then()
            .statusCode(400)
            .body(containsString("InvalidDBClusterStateFault"));
    }

    @Test
    void missingSubnetGroupOnClusterCreationReturnsError() {
        given()
            .header("Authorization", rdsAuthorization())
            .contentType(FORM)
            .formParam("Action", "CreateDBCluster")
            .formParam("DBClusterIdentifier", "harden-no-sg-cluster")
            .formParam("Engine", "docdb")
            .formParam("DBSubnetGroupName", "nonexistent-sg")
        .when().post("/")
        .then()
            .statusCode(404)
            .body(containsString("DBSubnetGroupNotFoundFault"));
    }

    // ── Instance Lifecycle ───────────────────────────────────────────────────────

    @Test
    void instanceLifecycleThroughHttp() {
        String sgName = "harden-inst-sg";
        String clusterId = "harden-inst-cluster";
        String instanceId = "harden-inst-lifecycle";

        // Create subnet group
        given()
            .header("Authorization", AUTH)
            .contentType(FORM)
            .formParam("Action", "CreateDBSubnetGroup")
            .formParam("DBSubnetGroupName", sgName)
            .formParam("DBSubnetGroupDescription", "for instance lifecycle")
        .when().post("/")
        .then()
            .statusCode(200);

        // Create cluster
        given()
            .header("Authorization", rdsAuthorization())
            .contentType(FORM)
            .formParam("Action", "CreateDBCluster")
            .formParam("DBClusterIdentifier", clusterId)
            .formParam("Engine", "docdb")
            .formParam("DBSubnetGroupName", sgName)
        .when().post("/")
        .then()
            .statusCode(200);

        // Create instance
        given()
            .header("Authorization", AUTH)
            .contentType(FORM)
            .formParam("Action", "CreateDBInstance")
            .formParam("DBInstanceIdentifier", instanceId)
            .formParam("DBClusterIdentifier", clusterId)
            .formParam("DBInstanceClass", "db.r5.xlarge")
            .formParam("EngineVersion", "5.0.0")
        .when().post("/")
        .then()
            .statusCode(200)
            .body(containsString("<DBInstanceIdentifier>" + instanceId + "</DBInstanceIdentifier>"))
            .body(containsString("<Endpoint>"))
            .body(containsString("<Address>"));

        // Describe by identifier
        given()
            .header("Authorization", AUTH)
            .contentType(FORM)
            .formParam("Action", "DescribeDBInstances")
            .formParam("DBInstanceIdentifier", instanceId)
        .when().post("/")
        .then()
            .statusCode(200)
            .body(containsString("<DBInstanceIdentifier>" + instanceId + "</DBInstanceIdentifier>"));

        // Describe all
        given()
            .header("Authorization", AUTH)
            .contentType(FORM)
            .formParam("Action", "DescribeDBInstances")
        .when().post("/")
        .then()
            .statusCode(200)
            .body(containsString("<DBInstanceIdentifier>" + instanceId + "</DBInstanceIdentifier>"));

        // Describe by filter parameter
        given()
            .header("Authorization", AUTH)
            .contentType(FORM)
            .formParam("Action", "DescribeDBInstances")
            .formParam("Filters.Filter.1.Name", "db-instance-id")
            .formParam("Filters.Filter.1.Values.Value.1", instanceId)
        .when().post("/")
        .then()
            .statusCode(200)
            .body(containsString("<DBInstanceIdentifier>" + instanceId + "</DBInstanceIdentifier>"));

        // Modify instance
        given()
            .header("Authorization", AUTH)
            .contentType(FORM)
            .formParam("Action", "ModifyDBInstance")
            .formParam("DBInstanceIdentifier", instanceId)
            .formParam("DBInstanceClass", "db.r5.2xlarge")
        .when().post("/")
        .then()
            .statusCode(200)
            .body(containsString("<DBInstanceIdentifier>" + instanceId + "</DBInstanceIdentifier>"));

        // Describe to verify modify took effect
        given()
            .header("Authorization", AUTH)
            .contentType(FORM)
            .formParam("Action", "DescribeDBInstances")
            .formParam("DBInstanceIdentifier", instanceId)
        .when().post("/")
        .then()
            .statusCode(200)
            .body(containsString("db.r5.2xlarge"));

        // Delete instance
        given()
            .header("Authorization", AUTH)
            .contentType(FORM)
            .formParam("Action", "DeleteDBInstance")
            .formParam("DBInstanceIdentifier", instanceId)
        .when().post("/")
        .then()
            .statusCode(200)
            .body(containsString("DeleteDBInstanceResponse"));

        // Cleanup cluster + subnet group
        given()
            .header("Authorization", rdsAuthorization())
            .contentType(FORM)
            .formParam("Action", "DeleteDBCluster")
            .formParam("DBClusterIdentifier", clusterId)
            .formParam("Engine", "docdb")
        .when().post("/")
        .then()
            .statusCode(200);

        given()
            .header("Authorization", AUTH)
            .contentType(FORM)
            .formParam("Action", "DeleteDBSubnetGroup")
            .formParam("DBSubnetGroupName", sgName)
        .when().post("/")
        .then()
            .statusCode(200);
    }

    @Test
    void instanceNotFoundReturnsError() {
        given()
            .header("Authorization", AUTH)
            .contentType(FORM)
            .formParam("Action", "DescribeDBInstances")
            .formParam("DBInstanceIdentifier", "nonexistent-instance")
        .when().post("/")
        .then()
            .statusCode(404)
            .body(containsString("DBInstanceNotFound"));
    }

    @Test
    void createInstanceMissingClusterReturnsError() {
        given()
            .header("Authorization", AUTH)
            .contentType(FORM)
            .formParam("Action", "CreateDBInstance")
            .formParam("DBInstanceIdentifier", "harden-no-cluster-inst")
            .formParam("DBClusterIdentifier", "nonexistent-cluster")
        .when().post("/")
        .then()
            .statusCode(404)
            .body(containsString("DBClusterNotFoundFault"));
    }

    @Test
    void duplicateInstanceReturnsError() {
        String sgName = "harden-dup-inst-sg";
        String clusterId = "harden-dup-inst-cluster";
        String instanceId = "harden-dup-inst";

        // Setup
        given().header("Authorization", AUTH).contentType(FORM)
            .formParam("Action", "CreateDBSubnetGroup")
            .formParam("DBSubnetGroupName", sgName)
            .formParam("DBSubnetGroupDescription", "dup test").when().post("/").then().statusCode(200);
        given().header("Authorization", rdsAuthorization()).contentType(FORM)
            .formParam("Action", "CreateDBCluster")
            .formParam("DBClusterIdentifier", clusterId)
            .formParam("Engine", "docdb")
            .formParam("DBSubnetGroupName", sgName).when().post("/").then().statusCode(200);

        // Create instance first time
        given().header("Authorization", AUTH).contentType(FORM)
            .formParam("Action", "CreateDBInstance")
            .formParam("DBInstanceIdentifier", instanceId)
            .formParam("DBClusterIdentifier", clusterId)
        .when().post("/").then().statusCode(200);

        // Duplicate should fail
        given().header("Authorization", AUTH).contentType(FORM)
            .formParam("Action", "CreateDBInstance")
            .formParam("DBInstanceIdentifier", instanceId)
            .formParam("DBClusterIdentifier", clusterId)
        .when().post("/").then()
            .statusCode(400)
            .body(containsString("DBInstanceAlreadyExists"));
    }

    // ── Tags ─────────────────────────────────────────────────────────────────────

    @Test
    void addAndListTagsRoundTripsThroughHttp() {
        String clusterId = "docdb-int-tag-cluster";

        // Create subnet group (needed for cluster creation)
        // Use docdb-scoped auth since CreateDBSubnetGroup doesn't have Engine=docdb param
        given()
            .header("Authorization", AUTH)
            .contentType(FORM)
            .formParam("Action", "CreateDBSubnetGroup")
            .formParam("DBSubnetGroupName", "int-tag-sg")
            .formParam("DBSubnetGroupDescription", "integration test sg")
        .when().post("/")
        .then()
            .statusCode(200);

        // Create cluster — use rds-scoped auth with Engine=docdb for routing
        given()
            .header("Authorization", rdsAuthorization())
            .contentType(FORM)
            .formParam("Action", "CreateDBCluster")
            .formParam("DBClusterIdentifier", clusterId)
            .formParam("Engine", "docdb")
            .formParam("DBSubnetGroupName", "int-tag-sg")
        .when().post("/")
        .then()
            .statusCode(200)
            .body(containsString("<DBClusterIdentifier>" + clusterId + "</DBClusterIdentifier>"));

        // AddTagsToResource — use docdb-scoped auth
        given()
            .header("Authorization", AUTH)
            .contentType(FORM)
            .formParam("Action", "AddTagsToResource")
            .formParam("ResourceName", "arn:aws:rds:us-east-1:000000000000:cluster:" + clusterId)
            .formParam("Tags.member.1.Key", "environment")
            .formParam("Tags.member.1.Value", "integration")
        .when().post("/")
        .then()
            .statusCode(200)
            .body(containsString("AddTagsToResourceResponse"));

        // ListTagsForResource — use docdb-scoped auth
        given()
            .header("Authorization", AUTH)
            .contentType(FORM)
            .formParam("Action", "ListTagsForResource")
            .formParam("ResourceName", "arn:aws:rds:us-east-1:000000000000:cluster:" + clusterId)
        .when().post("/")
        .then()
            .statusCode(200)
            .body(containsString("<TagList>"))
            .body(containsString("<Key>environment</Key>"))
            .body(containsString("<Value>integration</Value>"));
    }

    @Test
    void removeTagsThroughHttp() {
        String sgName = "harden-rm-tag-sg";
        String clusterId = "harden-rm-tag-cluster";

        // Setup
        given().header("Authorization", AUTH).contentType(FORM)
            .formParam("Action", "CreateDBSubnetGroup")
            .formParam("DBSubnetGroupName", sgName)
            .formParam("DBSubnetGroupDescription", "rm tag test").when().post("/").then().statusCode(200);
        given().header("Authorization", rdsAuthorization()).contentType(FORM)
            .formParam("Action", "CreateDBCluster")
            .formParam("DBClusterIdentifier", clusterId)
            .formParam("Engine", "docdb")
            .formParam("DBSubnetGroupName", sgName).when().post("/").then().statusCode(200);

        String arn = "arn:aws:rds:us-east-1:000000000000:cluster:" + clusterId;

        // Add two tags
        given().header("Authorization", AUTH).contentType(FORM)
            .formParam("Action", "AddTagsToResource")
            .formParam("ResourceName", arn)
            .formParam("Tags.member.1.Key", "keep")
            .formParam("Tags.member.1.Value", "this")
            .formParam("Tags.member.2.Key", "remove")
            .formParam("Tags.member.2.Value", "me")
        .when().post("/").then().statusCode(200);

        // Remove one tag
        given().header("Authorization", AUTH).contentType(FORM)
            .formParam("Action", "RemoveTagsFromResource")
            .formParam("ResourceName", arn)
            .formParam("TagKeys.member.1", "remove")
        .when().post("/").then()
            .statusCode(200)
            .body(containsString("RemoveTagsFromResourceResponse"));

        // List should show only the kept tag
        given().header("Authorization", AUTH).contentType(FORM)
            .formParam("Action", "ListTagsForResource")
            .formParam("ResourceName", arn)
        .when().post("/").then()
            .statusCode(200)
            .body(containsString("<Key>keep</Key>"))
            .body(containsString("<Value>this</Value>"))
            .body(not(containsString("<Key>remove</Key>")));
    }

    // ── Health ───────────────────────────────────────────────────────────────────

    @Test
    void healthIncludesDocDbService() {
        given()
        .when().get("/_floci/health")
        .then()
            .statusCode(200)
            .body("services.docdb", equalTo("running"));
    }

    private static String rdsAuthorization() {
        return "AWS4-HMAC-SHA256 Credential=test/20260516/us-east-1/rds/aws4_request, " +
                "SignedHeaders=content-type;host, Signature=test";
    }
}
