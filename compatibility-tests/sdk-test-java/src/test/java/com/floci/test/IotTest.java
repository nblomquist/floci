package com.floci.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.iot.IotClient;
import software.amazon.awssdk.services.iot.model.AttributePayload;
import software.amazon.awssdk.services.iot.model.AttachPolicyRequest;
import software.amazon.awssdk.services.iot.model.AttachThingPrincipalRequest;
import software.amazon.awssdk.services.iot.model.CertificateStatus;
import software.amazon.awssdk.services.iot.model.CreateKeysAndCertificateRequest;
import software.amazon.awssdk.services.iot.model.CreatePolicyRequest;
import software.amazon.awssdk.services.iot.model.CreateThingRequest;
import software.amazon.awssdk.services.iot.model.DeleteThingRequest;
import software.amazon.awssdk.services.iot.model.DescribeCertificateRequest;
import software.amazon.awssdk.services.iot.model.DescribeEndpointRequest;
import software.amazon.awssdk.services.iot.model.DescribeThingRequest;
import software.amazon.awssdk.services.iot.model.DetachPolicyRequest;
import software.amazon.awssdk.services.iot.model.DetachThingPrincipalRequest;
import software.amazon.awssdk.services.iot.model.GetPolicyRequest;
import software.amazon.awssdk.services.iot.model.ListCertificatesRequest;
import software.amazon.awssdk.services.iot.model.ListPoliciesRequest;
import software.amazon.awssdk.services.iot.model.ListThingsRequest;
import software.amazon.awssdk.services.iot.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.iot.model.ListThingPrincipalsRequest;
import software.amazon.awssdk.services.iot.model.ResourceAlreadyExistsException;
import software.amazon.awssdk.services.iot.model.ResourceNotFoundException;
import software.amazon.awssdk.services.iot.model.Tag;
import software.amazon.awssdk.services.iot.model.TagResourceRequest;
import software.amazon.awssdk.services.iot.model.UntagResourceRequest;
import software.amazon.awssdk.services.iot.model.UpdateCertificateRequest;
import software.amazon.awssdk.services.iot.model.UpdateThingRequest;
import software.amazon.awssdk.services.iotdataplane.IotDataPlaneClient;
import software.amazon.awssdk.services.iotdataplane.model.DeleteThingShadowRequest;
import software.amazon.awssdk.services.iotdataplane.model.GetThingShadowRequest;
import software.amazon.awssdk.services.iotdataplane.model.ListNamedShadowsForThingRequest;
import software.amazon.awssdk.services.iotdataplane.model.PublishRequest;
import software.amazon.awssdk.services.iotdataplane.model.UpdateThingShadowRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AWS IoT")
class IotTest {

    private final IotClient iot = TestFixtures.iotClient();
    private final IotDataPlaneClient iotData = TestFixtures.iotDataClient();

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

    @Test
    void thingTags() {
        String thingName = "java-iot-tagged-thing";
        try {
            iot.deleteThing(DeleteThingRequest.builder().thingName(thingName).build());
        } catch (Exception ignored) {
        }

        var created = iot.createThing(CreateThingRequest.builder().thingName(thingName).build());
        String thingArn = created.thingArn();

        var emptyTags = iot.listTagsForResource(ListTagsForResourceRequest.builder()
                .resourceArn(thingArn)
                .build());
        assertThat(emptyTags.tags()).isEmpty();

        iot.tagResource(TagResourceRequest.builder()
                .resourceArn(thingArn)
                .tags(Tag.builder().key("env").value("java").build(),
                        Tag.builder().key("owner").value("iot").build())
                .build());

        var tags = iot.listTagsForResource(ListTagsForResourceRequest.builder()
                .resourceArn(thingArn)
                .build());
        assertThat(tags.tags()).extracting(Tag::key).containsExactlyInAnyOrder("env", "owner");
        assertThat(tags.tags()).extracting(Tag::value).contains("java", "iot");

        iot.untagResource(UntagResourceRequest.builder()
                .resourceArn(thingArn)
                .tagKeys("env")
                .build());

        var remainingTags = iot.listTagsForResource(ListTagsForResourceRequest.builder()
                .resourceArn(thingArn)
                .build());
        assertThat(remainingTags.tags()).extracting(Tag::key).containsExactly("owner");

        assertThatThrownBy(() -> iot.listTagsForResource(ListTagsForResourceRequest.builder()
                .resourceArn("arn:aws:iot:us-east-1:000000000000:thing/missing-tagged-thing")
                .build()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void certificatesPoliciesAndAttachments() {
        var cert = iot.createKeysAndCertificate(CreateKeysAndCertificateRequest.builder()
                .setAsActive(true)
                .build());
        assertThat(cert.certificatePem()).contains("BEGIN CERTIFICATE");
        assertThat(cert.keyPair().publicKey()).contains("BEGIN PUBLIC KEY");

        var described = iot.describeCertificate(DescribeCertificateRequest.builder()
                .certificateId(cert.certificateId())
                .build());
        assertThat(described.certificateDescription().status()).isEqualTo(CertificateStatus.ACTIVE);

        var certs = iot.listCertificates(ListCertificatesRequest.builder().build());
        assertThat(certs.certificates()).anyMatch(item -> cert.certificateArn().equals(item.certificateArn()));

        iot.updateCertificate(UpdateCertificateRequest.builder()
                .certificateId(cert.certificateId())
                .newStatus(CertificateStatus.INACTIVE)
                .build());
        described = iot.describeCertificate(DescribeCertificateRequest.builder()
                .certificateId(cert.certificateId())
                .build());
        assertThat(described.certificateDescription().status()).isEqualTo(CertificateStatus.INACTIVE);

        String policyName = "java-iot-policy";
        String policyDocument = "{\"Version\":\"2012-10-17\",\"Statement\":[]}";
        var policy = iot.createPolicy(CreatePolicyRequest.builder()
                .policyName(policyName)
                .policyDocument(policyDocument)
                .build());
        assertThat(policy.policyName()).isEqualTo(policyName);

        var gotPolicy = iot.getPolicy(GetPolicyRequest.builder().policyName(policyName).build());
        assertThat(gotPolicy.policyDocument()).contains("2012-10-17");

        var policies = iot.listPolicies(ListPoliciesRequest.builder().build());
        assertThat(policies.policies()).anyMatch(item -> policyName.equals(item.policyName()));

        iot.attachPolicy(AttachPolicyRequest.builder().policyName(policyName).target(cert.certificateArn()).build());
        iot.detachPolicy(DetachPolicyRequest.builder().policyName(policyName).target(cert.certificateArn()).build());

        String thingName = "java-iot-principal-thing";
        try {
            iot.deleteThing(DeleteThingRequest.builder().thingName(thingName).build());
        } catch (Exception ignored) {
        }
        iot.createThing(CreateThingRequest.builder().thingName(thingName).build());
        iot.attachThingPrincipal(AttachThingPrincipalRequest.builder()
                .thingName(thingName)
                .principal(cert.certificateArn())
                .build());
        var principals = iot.listThingPrincipals(ListThingPrincipalsRequest.builder().thingName(thingName).build());
        assertThat(principals.principals()).contains(cert.certificateArn());
        iot.detachThingPrincipal(DetachThingPrincipalRequest.builder()
                .thingName(thingName)
                .principal(cert.certificateArn())
                .build());
    }

    @Test
    void iotDataShadowsAndPublish() {
        String thingName = "java-iot-shadow-thing";
        assertThatThrownBy(() -> iotData.getThingShadow(GetThingShadowRequest.builder()
                .thingName(thingName)
                .build()))
                .isInstanceOf(software.amazon.awssdk.services.iotdataplane.model.ResourceNotFoundException.class);

        var updated = iotData.updateThingShadow(UpdateThingShadowRequest.builder()
                .thingName(thingName)
                .payload(SdkBytes.fromUtf8String("{\"state\":{\"desired\":{\"color\":\"blue\"}}}"))
                .build());
        assertThat(updated.payload().asUtf8String()).contains("\"version\":1");

        iotData.updateThingShadow(UpdateThingShadowRequest.builder()
                .thingName(thingName)
                .payload(SdkBytes.fromUtf8String("{\"state\":{\"reported\":{\"color\":\"green\"}}}"))
                .build());
        var got = iotData.getThingShadow(GetThingShadowRequest.builder().thingName(thingName).build());
        assertThat(got.payload().asUtf8String()).contains("blue").contains("green");

        iotData.updateThingShadow(UpdateThingShadowRequest.builder()
                .thingName(thingName)
                .shadowName("settings")
                .payload(SdkBytes.fromUtf8String("{\"state\":{\"desired\":{\"mode\":\"auto\"}}}"))
                .build());
        var named = iotData.listNamedShadowsForThing(ListNamedShadowsForThingRequest.builder()
                .thingName(thingName)
                .build());
        assertThat(named.results()).contains("settings");

        iotData.publish(PublishRequest.builder()
                .topic("devices/" + thingName + "/events")
                .payload(SdkBytes.fromUtf8String("payload"))
                .build());
        iotData.deleteThingShadow(DeleteThingShadowRequest.builder().thingName(thingName).shadowName("settings").build());
        iotData.deleteThingShadow(DeleteThingShadowRequest.builder().thingName(thingName).build());
    }
}
