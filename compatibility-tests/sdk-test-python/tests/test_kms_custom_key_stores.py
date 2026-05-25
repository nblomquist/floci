"""KMS custom key store integration tests."""

# pyright: reportMissingImports=false

import pytest
from botocore.exceptions import ClientError


TRUST_ANCHOR_CERTIFICATE = """-----BEGIN CERTIFICATE-----
MIIBszCCAVmgAwIBAgIUQmN1c3RvbUtleVN0b3JlQ2VydDAKBggqhkjOPQQDAjASMRAw
DgYDVQQDDAdGbG9jaSBLTVMwHhcNMjUwMTAxMDAwMDAwWhcNMzUwMTAxMDAwMDAwWjAS
MRAwDgYDVQQDDAdGbG9jaSBLTVMwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAS4xQm4
Q3J5cHRvVGVzdENlcnRpZmljYXRlRm9yRmxvY2lLbXNDdXN0b21LZXlTdG9yZQIDAQAB
o1MwUTAdBgNVHQ4EFgQU4qP3l9kZ1c3RvbUtleVN0b3JlMB8GA1UdIwQYMBaAFOKj95fZ
GdXN0b21LZXlTdG9yZTAfBgNVHSMEGDAWgBTio/eX2RnVzdtvbUtleVN0b3JlMAoGCCqG
SM49BAMCA0kAMEYCIQDQ1mVzdENlcnQwMAoGCCqGSM49BAMCA0cAMEQCIQDJVGVzdENlcn
RpZmljYXRlQ29udGVudA==
-----END CERTIFICATE-----"""
KEY_STORE_PASSWORD = "pytest-key-store-password"
HSM_CLUSTER_ID = "cluster-1234567890abcdef0"


class TestKMSCustomKeyStore:
    """Test KMS custom key store operations."""

    def test_create_custom_key_store(self, kms_client, unique_name):
        """Test CreateCustomKeyStore creates a custom key store."""
        custom_key_store_name = f"pytest-custom-key-store-{unique_name}"
        response = kms_client.create_custom_key_store(
            CustomKeyStoreName=custom_key_store_name,
            CloudHsmClusterId=HSM_CLUSTER_ID,
            TrustAnchorCertificate=TRUST_ANCHOR_CERTIFICATE,
            KeyStorePassword=KEY_STORE_PASSWORD,
        )
        custom_key_store_id = response["CustomKeyStoreId"]

        try:
            assert custom_key_store_id
        finally:
            kms_client.disconnect_custom_key_store(CustomKeyStoreId=custom_key_store_id)
            kms_client.delete_custom_key_store(CustomKeyStoreId=custom_key_store_id)

    def test_describe_custom_key_stores(self, kms_client, unique_name):
        """Test DescribeCustomKeyStores returns created custom key stores."""
        custom_key_store_name = f"pytest-custom-key-store-{unique_name}"
        response = kms_client.create_custom_key_store(
            CustomKeyStoreName=custom_key_store_name,
            CloudHsmClusterId=HSM_CLUSTER_ID,
            TrustAnchorCertificate=TRUST_ANCHOR_CERTIFICATE,
            KeyStorePassword=KEY_STORE_PASSWORD,
        )
        custom_key_store_id = response["CustomKeyStoreId"]

        try:
            response = kms_client.describe_custom_key_stores()
            assert any(
                store["CustomKeyStoreId"] == custom_key_store_id
                for store in response.get("CustomKeyStores", [])
            )
        finally:
            kms_client.disconnect_custom_key_store(CustomKeyStoreId=custom_key_store_id)
            kms_client.delete_custom_key_store(CustomKeyStoreId=custom_key_store_id)

    def test_describe_custom_key_stores_by_id(self, kms_client, unique_name):
        """Test DescribeCustomKeyStores filters by custom key store ID."""
        custom_key_store_name = f"pytest-custom-key-store-{unique_name}"
        response = kms_client.create_custom_key_store(
            CustomKeyStoreName=custom_key_store_name,
            CloudHsmClusterId=HSM_CLUSTER_ID,
            TrustAnchorCertificate=TRUST_ANCHOR_CERTIFICATE,
            KeyStorePassword=KEY_STORE_PASSWORD,
        )
        custom_key_store_id = response["CustomKeyStoreId"]

        try:
            response = kms_client.describe_custom_key_stores(
                CustomKeyStoreId=custom_key_store_id
            )
            stores = response.get("CustomKeyStores", [])
            assert len(stores) == 1
            assert stores[0]["CustomKeyStoreId"] == custom_key_store_id
        finally:
            kms_client.disconnect_custom_key_store(CustomKeyStoreId=custom_key_store_id)
            kms_client.delete_custom_key_store(CustomKeyStoreId=custom_key_store_id)

    def test_describe_custom_key_stores_by_name(self, kms_client, unique_name):
        """Test DescribeCustomKeyStores filters by custom key store name."""
        custom_key_store_name = f"pytest-custom-key-store-{unique_name}"
        response = kms_client.create_custom_key_store(
            CustomKeyStoreName=custom_key_store_name,
            CloudHsmClusterId=HSM_CLUSTER_ID,
            TrustAnchorCertificate=TRUST_ANCHOR_CERTIFICATE,
            KeyStorePassword=KEY_STORE_PASSWORD,
        )
        custom_key_store_id = response["CustomKeyStoreId"]

        try:
            response = kms_client.describe_custom_key_stores(
                CustomKeyStoreName=custom_key_store_name
            )
            stores = response.get("CustomKeyStores", [])
            assert len(stores) == 1
            assert stores[0]["CustomKeyStoreId"] == custom_key_store_id
            assert stores[0]["CustomKeyStoreName"] == custom_key_store_name
        finally:
            kms_client.disconnect_custom_key_store(CustomKeyStoreId=custom_key_store_id)
            kms_client.delete_custom_key_store(CustomKeyStoreId=custom_key_store_id)

    def test_update_custom_key_store_name(self, kms_client, unique_name):
        """Test UpdateCustomKeyStore updates the custom key store name."""
        custom_key_store_name = f"pytest-custom-key-store-{unique_name}"
        updated_custom_key_store_name = f"pytest-custom-key-store-updated-{unique_name}"
        response = kms_client.create_custom_key_store(
            CustomKeyStoreName=custom_key_store_name,
            CloudHsmClusterId=HSM_CLUSTER_ID,
            TrustAnchorCertificate=TRUST_ANCHOR_CERTIFICATE,
            KeyStorePassword=KEY_STORE_PASSWORD,
        )
        custom_key_store_id = response["CustomKeyStoreId"]

        try:
            kms_client.disconnect_custom_key_store(CustomKeyStoreId=custom_key_store_id)
            kms_client.update_custom_key_store(
                CustomKeyStoreId=custom_key_store_id,
                NewCustomKeyStoreName=updated_custom_key_store_name,
            )

            response = kms_client.describe_custom_key_stores(
                CustomKeyStoreId=custom_key_store_id
            )
            stores = response.get("CustomKeyStores", [])
            assert len(stores) == 1
            assert stores[0]["CustomKeyStoreName"] == updated_custom_key_store_name
        finally:
            kms_client.disconnect_custom_key_store(CustomKeyStoreId=custom_key_store_id)
            kms_client.delete_custom_key_store(CustomKeyStoreId=custom_key_store_id)

    def test_connect_custom_key_store(self, kms_client, unique_name):
        """Test ConnectCustomKeyStore reconnects a disconnected custom key store."""
        custom_key_store_name = f"pytest-custom-key-store-{unique_name}"
        response = kms_client.create_custom_key_store(
            CustomKeyStoreName=custom_key_store_name,
            CloudHsmClusterId=HSM_CLUSTER_ID,
            TrustAnchorCertificate=TRUST_ANCHOR_CERTIFICATE,
            KeyStorePassword=KEY_STORE_PASSWORD,
        )
        custom_key_store_id = response["CustomKeyStoreId"]

        try:
            kms_client.disconnect_custom_key_store(CustomKeyStoreId=custom_key_store_id)
            response = kms_client.describe_custom_key_stores(
                CustomKeyStoreId=custom_key_store_id
            )
            assert response["CustomKeyStores"][0]["ConnectionState"] == "DISCONNECTED"

            kms_client.connect_custom_key_store(CustomKeyStoreId=custom_key_store_id)
            response = kms_client.describe_custom_key_stores(
                CustomKeyStoreId=custom_key_store_id
            )
            assert response["CustomKeyStores"][0]["ConnectionState"] == "CONNECTED"
        finally:
            kms_client.disconnect_custom_key_store(CustomKeyStoreId=custom_key_store_id)
            kms_client.delete_custom_key_store(CustomKeyStoreId=custom_key_store_id)

    def test_disconnect_custom_key_store(self, kms_client, unique_name):
        """Test DisconnectCustomKeyStore disconnects a custom key store."""
        custom_key_store_name = f"pytest-custom-key-store-{unique_name}"
        response = kms_client.create_custom_key_store(
            CustomKeyStoreName=custom_key_store_name,
            CloudHsmClusterId=HSM_CLUSTER_ID,
            TrustAnchorCertificate=TRUST_ANCHOR_CERTIFICATE,
            KeyStorePassword=KEY_STORE_PASSWORD,
        )
        custom_key_store_id = response["CustomKeyStoreId"]

        try:
            kms_client.disconnect_custom_key_store(CustomKeyStoreId=custom_key_store_id)
            response = kms_client.describe_custom_key_stores(
                CustomKeyStoreId=custom_key_store_id
            )
            assert response["CustomKeyStores"][0]["ConnectionState"] == "DISCONNECTED"
        finally:
            kms_client.delete_custom_key_store(CustomKeyStoreId=custom_key_store_id)

    def test_delete_custom_key_store(self, kms_client, unique_name):
        """Test DeleteCustomKeyStore removes a disconnected custom key store."""
        custom_key_store_name = f"pytest-custom-key-store-{unique_name}"
        response = kms_client.create_custom_key_store(
            CustomKeyStoreName=custom_key_store_name,
            CloudHsmClusterId=HSM_CLUSTER_ID,
            TrustAnchorCertificate=TRUST_ANCHOR_CERTIFICATE,
            KeyStorePassword=KEY_STORE_PASSWORD,
        )
        custom_key_store_id = response["CustomKeyStoreId"]

        try:
            kms_client.disconnect_custom_key_store(CustomKeyStoreId=custom_key_store_id)
            kms_client.delete_custom_key_store(CustomKeyStoreId=custom_key_store_id)

            response = kms_client.describe_custom_key_stores(
                CustomKeyStoreId=custom_key_store_id
            )
            assert not response.get("CustomKeyStores", [])
        finally:
            try:
                kms_client.delete_custom_key_store(CustomKeyStoreId=custom_key_store_id)
            except ClientError:
                pass

    def test_delete_custom_key_store_connected_fails(self, kms_client, unique_name):
        """Test DeleteCustomKeyStore fails for a connected custom key store."""
        custom_key_store_name = f"pytest-custom-key-store-{unique_name}"
        response = kms_client.create_custom_key_store(
            CustomKeyStoreName=custom_key_store_name,
            CloudHsmClusterId=HSM_CLUSTER_ID,
            TrustAnchorCertificate=TRUST_ANCHOR_CERTIFICATE,
            KeyStorePassword=KEY_STORE_PASSWORD,
        )
        custom_key_store_id = response["CustomKeyStoreId"]

        try:
            response = kms_client.describe_custom_key_stores(
                CustomKeyStoreId=custom_key_store_id
            )
            assert response["CustomKeyStores"][0]["ConnectionState"] == "CONNECTED"

            with pytest.raises(ClientError):
                kms_client.delete_custom_key_store(CustomKeyStoreId=custom_key_store_id)
        finally:
            try:
                kms_client.disconnect_custom_key_store(
                    CustomKeyStoreId=custom_key_store_id
                )
            except ClientError:
                pass
            try:
                kms_client.delete_custom_key_store(CustomKeyStoreId=custom_key_store_id)
            except ClientError:
                pass

    def test_create_custom_key_store_duplicate_name(self, kms_client, unique_name):
        """Test CreateCustomKeyStore rejects duplicate custom key store names."""
        custom_key_store_name = f"pytest-custom-key-store-{unique_name}"
        response = kms_client.create_custom_key_store(
            CustomKeyStoreName=custom_key_store_name,
            CloudHsmClusterId=HSM_CLUSTER_ID,
            TrustAnchorCertificate=TRUST_ANCHOR_CERTIFICATE,
            KeyStorePassword=KEY_STORE_PASSWORD,
        )
        custom_key_store_id = response["CustomKeyStoreId"]

        try:
            with pytest.raises(ClientError):
                kms_client.create_custom_key_store(
                    CustomKeyStoreName=custom_key_store_name,
                    CloudHsmClusterId=HSM_CLUSTER_ID,
                    TrustAnchorCertificate=TRUST_ANCHOR_CERTIFICATE,
                    KeyStorePassword=KEY_STORE_PASSWORD,
                )
        finally:
            kms_client.disconnect_custom_key_store(CustomKeyStoreId=custom_key_store_id)
            kms_client.delete_custom_key_store(CustomKeyStoreId=custom_key_store_id)
