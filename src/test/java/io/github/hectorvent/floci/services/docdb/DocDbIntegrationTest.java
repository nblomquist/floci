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
            .body(containsString("<Code>UnsupportedOperation</Code>"))
            .body(containsString("DocumentDB"))
            .body(containsString("CreateDBSubnetGroup"));
    }

    @Test
    void docDbEngineDoesNotRouteToRdsImplementation() {
        given()
            .header("Authorization", rdsAuthorization())
            .contentType(FORM)
            .formParam("Action", "CreateDBCluster")
            .formParam("DBClusterIdentifier", "phase-one-docdb-cluster")
            .formParam("Engine", "docdb")
        .when().post("/")
        .then()
            .statusCode(400)
            .body(containsString("<Code>UnsupportedOperation</Code>"))
            .body(containsString("DocumentDB"))
            .body(containsString("CreateDBCluster"));
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
