package io.github.hectorvent.floci.services.iot;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

@QuarkusTest
class IotDataIntegrationTest {

    @Test
    void classicShadowLifecycle() {
        given()
        .when()
            .get("/things/phase-five-thing/shadow")
        .then()
            .statusCode(404)
            .body("__type", equalTo("ResourceNotFoundException"));

        given()
            .contentType("application/json")
            .body("{\"state\":{\"desired\":{\"color\":\"blue\"}}}")
        .when()
            .post("/things/phase-five-thing/shadow")
        .then()
            .statusCode(200)
            .body("state.desired.color", equalTo("blue"))
            .body("version", equalTo(1));

        given()
            .contentType("application/json")
            .body("{\"state\":{\"reported\":{\"color\":\"green\"}}}")
        .when()
            .post("/things/phase-five-thing/shadow")
        .then()
            .statusCode(200)
            .body("state.desired.color", equalTo("blue"))
            .body("state.reported.color", equalTo("green"))
            .body("version", equalTo(2));

        given()
        .when()
            .delete("/things/phase-five-thing/shadow")
        .then()
            .statusCode(200)
            .body("version", equalTo(2));

        given()
        .when()
            .get("/things/phase-five-thing/shadow")
        .then()
            .statusCode(404);
    }

    @Test
    void namedShadowLifecycleAndPublish() {
        given()
            .contentType("application/json")
            .queryParam("name", "settings")
            .body("{\"state\":{\"desired\":{\"mode\":\"auto\"}}}")
        .when()
            .post("/things/phase-five-named/shadow")
        .then()
            .statusCode(200)
            .body("state.desired.mode", equalTo("auto"));

        given()
        .when()
            .get("/api/things/shadow/ListNamedShadowsForThing/phase-five-named")
        .then()
            .statusCode(200)
            .body("results", hasItem("settings"));

        given()
            .queryParam("name", "settings")
        .when()
            .get("/things/phase-five-named/shadow")
        .then()
            .statusCode(200)
            .body("state.desired.mode", equalTo("auto"));

        given()
            .contentType("text/plain")
            .body("payload")
        .when()
            .post("/topics/devices/phase-five-named/events")
        .then()
            .statusCode(200);

        given()
            .queryParam("name", "settings")
        .when()
            .delete("/things/phase-five-named/shadow")
        .then()
            .statusCode(200);
    }
}
