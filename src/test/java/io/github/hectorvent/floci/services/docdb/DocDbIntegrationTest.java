package io.github.hectorvent.floci.services.docdb;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class DocDbIntegrationTest {

    private static final String FORM = "application/x-www-form-urlencoded";

    // Fake SigV4 header — service name "docdb" should route to the DocumentDB Query handler.
    private static final String AUTH =
            "AWS4-HMAC-SHA256 Credential=test/20260516/us-east-1/docdb/aws4_request, " +
            "SignedHeaders=content-type;host, Signature=test";

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
