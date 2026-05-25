package com.floci.test;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.*;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KmsCustomKeyStoreTest {

    private static final KmsClient kms = TestFixtures.kmsClient();
    private static String customKeyStoreId;

    private static final String UNIQUE_SUFFIX = UUID.randomUUID().toString();
    private static final String INITIAL_CUSTOM_KEY_STORE_NAME = "java-custom-key-store-" + UNIQUE_SUFFIX;
    private static final String UPDATED_CUSTOM_KEY_STORE_NAME = "java-custom-key-store-renamed-" + UNIQUE_SUFFIX;
    private static final String CLOUD_HSM_CLUSTER_ID = "cluster-1234567890";
    private static final String TRUST_ANCHOR_CERTIFICATE = "-----BEGIN CERTIFICATE-----\n"
        + "MIICsjCCAZqgAwIBAgIUQmFzZTY0UmVwbGFjZW1lbnQwDQYJKoZIhvcNAQELBQAw\n"
        + "EjEQMA4GA1UEAwwHRmxvY2kgQ0EwHhcNMjYwMTAxMDAwMDAwWhcNMzYwMTAxMDAw\n"
        + "MDAwWjASMRAwDgYDVQQDDAdGbG9jaSBDQTCBnzANBgkqhkiG9w0BAQEFAAOBjQAw\n"
        + "gYkCgYEAu0h2F8fW0m7r0vY7m0X7m0X7m0X7m0X7m0X7m0X7m0X7m0X7m0X7m0X7\n"
        + "m0X7m0X7m0X7m0X7m0X7m0X7m0X7m0X7m0X7m0X7m0X7m0X7m0X7m0X7m0X7m0X7\n"
        + "AQIDAQABo1MwUTAdBgNVHQ4EFgQUVGVzdFRydXN0QW5jaG9yQ2VydDAfBgNVHSME\n"
        + "GDAWgBRUZXN0VHJ1c3RBbmNob3JDZXJ0MA8GA1UdEwEB/wQFMAMBAf8wDQYJKoZI\n"
        + "hvcNAQELBQADgYEAFakeZWNlcnQ=\n"
        + "-----END CERTIFICATE-----";
    private static final String KEY_STORE_PASSWORD = "Password123!";

    @AfterAll
    static void closeClient() {
        if (customKeyStoreId != null) {
            try {
                kms.disconnectCustomKeyStore(b -> b.customKeyStoreId(customKeyStoreId));
            } catch (SdkServiceException ignored) {
                // Best effort cleanup.
            }

            try {
                kms.deleteCustomKeyStore(b -> b.customKeyStoreId(customKeyStoreId));
            } catch (SdkServiceException ignored) {
                // Best effort cleanup.
            }
        }

        kms.close();
    }

    @Test
    @Order(1)
    void createCustomKeyStore() {
        CreateCustomKeyStoreResponse response = kms.createCustomKeyStore(b -> b
            .customKeyStoreName(INITIAL_CUSTOM_KEY_STORE_NAME)
            .cloudHsmClusterId(CLOUD_HSM_CLUSTER_ID)
            .trustAnchorCertificate(TRUST_ANCHOR_CERTIFICATE)
            .keyStorePassword(KEY_STORE_PASSWORD));

        customKeyStoreId = response.customKeyStoreId();

        assertThat(customKeyStoreId).isNotNull();
    }

    @Test
    @Order(2)
    void describeCustomKeyStores() {
        DescribeCustomKeyStoresResponse response = kms.describeCustomKeyStores();

        assertThat(response.customKeyStores())
            .anySatisfy(store -> assertThat(store.customKeyStoreId()).isEqualTo(customKeyStoreId));
    }

    @Test
    @Order(3)
    void describeCustomKeyStoresById() {
        DescribeCustomKeyStoresResponse response = kms.describeCustomKeyStores(b -> b.customKeyStoreId(customKeyStoreId));

        assertThat(response.customKeyStores()).hasSize(1);
        assertThat(response.customKeyStores().get(0).customKeyStoreId()).isEqualTo(customKeyStoreId);
        assertThat(response.customKeyStores().get(0).customKeyStoreName()).isEqualTo(INITIAL_CUSTOM_KEY_STORE_NAME);
    }

    @Test
    @Order(4)
    void updateCustomKeyStoreName() {
        UpdateCustomKeyStoreResponse response = kms.updateCustomKeyStore(b -> b
            .customKeyStoreId(customKeyStoreId)
            .newCustomKeyStoreName(UPDATED_CUSTOM_KEY_STORE_NAME));

        assertThat(response).isNotNull();

        DescribeCustomKeyStoresResponse described = kms.describeCustomKeyStores(b -> b.customKeyStoreId(customKeyStoreId));

        assertThat(described.customKeyStores()).hasSize(1);
        assertThat(described.customKeyStores().get(0).customKeyStoreName()).isEqualTo(UPDATED_CUSTOM_KEY_STORE_NAME);
    }

    @Test
    @Order(5)
    void disconnectCustomKeyStore() {
        DisconnectCustomKeyStoreResponse response = kms.disconnectCustomKeyStore(b -> b.customKeyStoreId(customKeyStoreId));

        assertThat(response).isNotNull();

        DescribeCustomKeyStoresResponse described = kms.describeCustomKeyStores(b -> b.customKeyStoreId(customKeyStoreId));

        assertThat(described.customKeyStores()).hasSize(1);
        assertThat(described.customKeyStores().get(0).connectionStateAsString()).isEqualTo("DISCONNECTED");
    }

    @Test
    @Order(6)
    void connectCustomKeyStore() {
        ConnectCustomKeyStoreResponse response = kms.connectCustomKeyStore(b -> b.customKeyStoreId(customKeyStoreId));

        assertThat(response).isNotNull();

        DescribeCustomKeyStoresResponse described = kms.describeCustomKeyStores(b -> b.customKeyStoreId(customKeyStoreId));

        assertThat(described.customKeyStores()).hasSize(1);
        assertThat(described.customKeyStores().get(0).connectionStateAsString()).isEqualTo("CONNECTED");
    }

    @Test
    @Order(7)
    void deleteCustomKeyStore() {
        kms.disconnectCustomKeyStore(b -> b.customKeyStoreId(customKeyStoreId));

        DescribeCustomKeyStoresResponse described = kms.describeCustomKeyStores(b -> b.customKeyStoreId(customKeyStoreId));
        assertThat(described.customKeyStores()).hasSize(1);
        assertThat(described.customKeyStores().get(0).connectionStateAsString()).isEqualTo("DISCONNECTED");

        DeleteCustomKeyStoreResponse response = kms.deleteCustomKeyStore(b -> b.customKeyStoreId(customKeyStoreId));

        assertThat(response).isNotNull();
    }

    @Test
    void testDeleteCustomKeyStoreConnectedFails() {
        String tempStoreName = "java-custom-key-store-temp-" + UUID.randomUUID();
        final String[] tempCustomKeyStoreId = new String[1];

        try {
            CreateCustomKeyStoreResponse created = kms.createCustomKeyStore(b -> b
                .customKeyStoreName(tempStoreName)
                .cloudHsmClusterId(CLOUD_HSM_CLUSTER_ID)
                .trustAnchorCertificate(TRUST_ANCHOR_CERTIFICATE)
                .keyStorePassword(KEY_STORE_PASSWORD));

            tempCustomKeyStoreId[0] = created.customKeyStoreId();

            DescribeCustomKeyStoresResponse described = kms.describeCustomKeyStores(b -> b.customKeyStoreId(tempCustomKeyStoreId[0]));
            assertThat(described.customKeyStores()).hasSize(1);

            if (!"CONNECTED".equals(described.customKeyStores().get(0).connectionStateAsString())) {
                ConnectCustomKeyStoreResponse connected = kms.connectCustomKeyStore(b -> b.customKeyStoreId(tempCustomKeyStoreId[0]));
                assertThat(connected).isNotNull();
            }

            DescribeCustomKeyStoresResponse connectedState = kms.describeCustomKeyStores(b -> b.customKeyStoreId(tempCustomKeyStoreId[0]));
            assertThat(connectedState.customKeyStores()).hasSize(1);
            assertThat(connectedState.customKeyStores().get(0).connectionStateAsString()).isEqualTo("CONNECTED");

            assertThatThrownBy(() -> kms.deleteCustomKeyStore(b -> b.customKeyStoreId(tempCustomKeyStoreId[0])))
                .isInstanceOf(KmsException.class);
        } finally {
            if (tempCustomKeyStoreId[0] != null) {
                try {
                    kms.disconnectCustomKeyStore(b -> b.customKeyStoreId(tempCustomKeyStoreId[0]));
                } catch (SdkServiceException ignored) {
                    // Best effort cleanup.
                }

                try {
                    kms.deleteCustomKeyStore(b -> b.customKeyStoreId(tempCustomKeyStoreId[0]));
                } catch (SdkServiceException ignored) {
                    // Best effort cleanup.
                }
            }
        }
    }
}
