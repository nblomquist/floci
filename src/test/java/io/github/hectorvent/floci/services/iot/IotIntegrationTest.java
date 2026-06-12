package io.github.hectorvent.floci.services.iot;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IotIntegrationTest {

    @Test
    @Order(1)
    void unsupportedEndpointTypeReturnsAwsError() {
        given()
            .queryParam("endpointType", "unsupported")
        .when()
            .get("/endpoint")
        .then()
            .statusCode(400)
            .body("__type", equalTo("InvalidRequestException"));
    }

    @Test
    @Order(2)
    void defaultDescribeEndpointReturnsBaseUrlAuthority() {
        given()
        .when()
            .get("/endpoint")
        .then()
            .statusCode(200)
            .body("endpointAddress", equalTo("localhost:4566"));
    }

    @Test
    @Order(3)
    void describeEndpointAcceptsAwsDefaultEndpointType() {
        given()
            .queryParam("endpointType", "iot:Data-ATS")
        .when()
            .get("/endpoint")
        .then()
            .statusCode(200)
            .body("endpointAddress", equalTo("localhost:4566"));
    }

    @Test
    @Order(4)
    void describeMissingThingReturnsAwsError() {
        given()
        .when()
            .get("/things/missing-thing")
        .then()
            .statusCode(404)
            .body("__type", equalTo("ResourceNotFoundException"));
    }

    @Test
    @Order(5)
    void createThingReturnsAwsShape() {
        given()
            .contentType("application/json")
            .body("""
                {
                  "attributePayload": {
                    "attributes": {
                      "env": "test"
                    }
                  }
                }
                """)
        .when()
            .post("/things/phase-two-thing")
        .then()
            .statusCode(200)
            .body("thingName", equalTo("phase-two-thing"))
            .body("thingArn", containsString(":iot:us-east-1:000000000000:thing/phase-two-thing"))
            .body("thingId", notNullValue())
            .body("version", equalTo(1))
            .body("attributes.env", equalTo("test"));
    }

    @Test
    @Order(6)
    void duplicateCreateThingReturnsConflict() {
        given()
            .contentType("application/json")
            .body("{}")
        .when()
            .post("/things/phase-two-thing")
        .then()
            .statusCode(409)
            .body("__type", equalTo("ResourceAlreadyExistsException"));
    }

    @Test
    @Order(7)
    void describeThingReturnsStoredThing() {
        given()
        .when()
            .get("/things/phase-two-thing")
        .then()
            .statusCode(200)
            .body("thingName", equalTo("phase-two-thing"))
            .body("attributes.env", equalTo("test"))
            .body("creationDate", notNullValue())
            .body("lastModifiedDate", notNullValue());
    }

    @Test
    @Order(8)
    void listThingsIncludesCreatedThing() {
        given()
            .contentType("application/json")
            .body("{}")
        .when()
            .post("/things/phase-two-other")
        .then()
            .statusCode(200);

        given()
        .when()
            .get("/things")
        .then()
            .statusCode(200)
            .body("things.thingName", hasItem("phase-two-thing"))
            .body("things.thingName", hasItem("phase-two-other"));
    }

    @Test
    @Order(9)
    void updateThingChangesAttributes() {
        given()
            .contentType("application/json")
            .body("""
                {
                  "attributePayload": {
                    "attributes": {
                      "env": "updated",
                      "owner": "iot"
                    }
                  }
                }
                """)
        .when()
            .patch("/things/phase-two-thing")
        .then()
            .statusCode(200)
            .body("version", equalTo(2));

        given()
        .when()
            .get("/things/phase-two-thing")
        .then()
            .statusCode(200)
            .body("attributes.env", equalTo("updated"))
            .body("attributes.owner", equalTo("iot"));
    }

    @Test
    @Order(10)
    void deleteThingRemovesThing() {
        given()
        .when()
            .delete("/things/phase-two-thing")
        .then()
            .statusCode(200);

        given()
        .when()
            .get("/things/phase-two-thing")
        .then()
            .statusCode(404);

        given()
        .when()
            .get("/things")
        .then()
            .statusCode(200)
            .body("things.thingName", not(hasItem("phase-two-thing")))
            .body("things.thingName", hasItem("phase-two-other"));
    }

}
