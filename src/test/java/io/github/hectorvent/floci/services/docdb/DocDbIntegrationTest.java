package io.github.hectorvent.floci.services.docdb;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DocDbIntegrationTest {

    private static final String FORM = "application/x-www-form-urlencoded";
    private static final String SUBNET_GROUP = "my-docdb-subnet-group";
    private static final String CLUSTER_ID = "my-docdb-cluster";
    private static final String INSTANCE_ID = "my-docdb-instance";

    // Fake SigV4 header — service name "docdb" should route to the DocumentDB Query handler.
    private static final String AUTH =
            "AWS4-HMAC-SHA256 Credential=test/20260516/us-east-1/docdb/aws4_request, " +
            "SignedHeaders=content-type;host, Signature=test";

    @Test
    @Order(1)
    void createSubnetGroup() {
        given()
            .header("Authorization", AUTH)
            .contentType(FORM)
            .formParam("Action", "CreateDBSubnetGroup")
            .formParam("DBSubnetGroupName", SUBNET_GROUP)
            .formParam("DBSubnetGroupDescription", "integration test subnet group")
            .formParam("SubnetIds.member.1", "subnet-11111111")
            .formParam("SubnetIds.member.2", "subnet-22222222")
        .when().post("/")
        .then()
            .statusCode(200)
            .body(containsString(SUBNET_GROUP));
    }

    @Test
    @Order(2)
    void createCluster() {
        given()
            .header("Authorization", AUTH)
            .contentType(FORM)
            .formParam("Action", "CreateDBCluster")
            .formParam("DBClusterIdentifier", CLUSTER_ID)
            .formParam("Engine", "docdb")
            .formParam("MasterUsername", "masteruser")
            .formParam("MasterUserPassword", "secret12345")
            .formParam("DBSubnetGroupName", SUBNET_GROUP)
        .when().post("/")
        .then()
            .statusCode(200)
            .body(containsString(CLUSTER_ID))
            .body(containsString("docdb"));
    }

    @Test
    @Order(3)
    void createInstance() {
        given()
            .header("Authorization", AUTH)
            .contentType(FORM)
            .formParam("Action", "CreateDBInstance")
            .formParam("DBInstanceIdentifier", INSTANCE_ID)
            .formParam("DBClusterIdentifier", CLUSTER_ID)
            .formParam("DBInstanceClass", "db.r5.large")
            .formParam("Engine", "docdb")
        .when().post("/")
        .then()
            .statusCode(200)
            .body(containsString(INSTANCE_ID))
            .body(containsString(CLUSTER_ID));
    }

    @Test
    @Order(4)
    void describeResources() {
        given()
            .header("Authorization", AUTH)
            .contentType(FORM)
            .formParam("Action", "DescribeDBClusters")
            .formParam("DBClusterIdentifier", CLUSTER_ID)
        .when().post("/")
        .then()
            .statusCode(200)
            .body(containsString(CLUSTER_ID));

        given()
            .header("Authorization", AUTH)
            .contentType(FORM)
            .formParam("Action", "DescribeDBInstances")
            .formParam("DBInstanceIdentifier", INSTANCE_ID)
        .when().post("/")
        .then()
            .statusCode(200)
            .body(containsString(INSTANCE_ID));

        given()
            .header("Authorization", AUTH)
            .contentType(FORM)
            .formParam("Action", "DescribeDBSubnetGroups")
            .formParam("DBSubnetGroupName", SUBNET_GROUP)
        .when().post("/")
        .then()
            .statusCode(200)
            .body(containsString(SUBNET_GROUP));
    }

    @Test
    @Order(5)
    void tagCluster() {
        String clusterArn = "arn:aws:rds:us-east-1:000000000000:cluster:" + CLUSTER_ID;

        given()
            .header("Authorization", AUTH)
            .contentType(FORM)
            .formParam("Action", "AddTagsToResource")
            .formParam("ResourceName", clusterArn)
            .formParam("Tags.member.1.Key", "phase")
            .formParam("Tags.member.1.Value", "zero")
        .when().post("/")
        .then()
            .statusCode(200);

        given()
            .header("Authorization", AUTH)
            .contentType(FORM)
            .formParam("Action", "ListTagsForResource")
            .formParam("ResourceName", clusterArn)
        .when().post("/")
        .then()
            .statusCode(200)
            .body(containsString("phase"))
            .body(containsString("zero"));
    }
}
