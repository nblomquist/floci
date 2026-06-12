package com.floci.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.iot.IotClient;
import software.amazon.awssdk.services.iot.model.AttributePayload;
import software.amazon.awssdk.services.iot.model.CreateThingRequest;
import software.amazon.awssdk.services.iot.model.DeleteThingRequest;
import software.amazon.awssdk.services.iot.model.DescribeEndpointRequest;
import software.amazon.awssdk.services.iot.model.DescribeThingRequest;
import software.amazon.awssdk.services.iot.model.ListThingsRequest;
import software.amazon.awssdk.services.iot.model.ResourceAlreadyExistsException;
import software.amazon.awssdk.services.iot.model.ResourceNotFoundException;
import software.amazon.awssdk.services.iot.model.UpdateThingRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AWS IoT")
class IotTest {

    private final IotClient iot = TestFixtures.iotClient();

    @Test
    void describeEndpoint() {
        var response = iot.describeEndpoint(DescribeEndpointRequest.builder()
                .endpointType("iot:Data-ATS")
                .build());

        assertThat(response.endpointAddress()).isNotBlank();
    }

    @Test
    void thingRegistryCrud() {
        String thingName = "java-iot-thing";
        try {
            iot.deleteThing(DeleteThingRequest.builder().thingName(thingName).build());
        } catch (Exception ignored) {
        }

        assertThatThrownBy(() -> iot.describeThing(DescribeThingRequest.builder().thingName(thingName).build()))
                .isInstanceOf(ResourceNotFoundException.class);

        var created = iot.createThing(CreateThingRequest.builder()
                .thingName(thingName)
                .attributePayload(AttributePayload.builder().attributes(Map.of("env", "java")).build())
                .build());
        assertThat(created.thingName()).isEqualTo(thingName);
        assertThat(created.thingArn()).endsWith(":thing/" + thingName);

        assertThatThrownBy(() -> iot.createThing(CreateThingRequest.builder().thingName(thingName).build()))
                .isInstanceOf(ResourceAlreadyExistsException.class);

        var described = iot.describeThing(DescribeThingRequest.builder().thingName(thingName).build());
        assertThat(described.attributes()).containsEntry("env", "java");

        var listed = iot.listThings(ListThingsRequest.builder().build());
        assertThat(listed.things()).anyMatch(thing -> thingName.equals(thing.thingName()));

        iot.updateThing(UpdateThingRequest.builder()
                .thingName(thingName)
                .attributePayload(AttributePayload.builder()
                        .attributes(Map.of("env", "updated", "owner", "iot"))
                        .build())
                .build());

        var updated = iot.describeThing(DescribeThingRequest.builder().thingName(thingName).build());
        assertThat(updated.attributes()).containsEntry("env", "updated").containsEntry("owner", "iot");

        iot.deleteThing(DeleteThingRequest.builder().thingName(thingName).build());
        assertThatThrownBy(() -> iot.describeThing(DescribeThingRequest.builder().thingName(thingName).build()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
