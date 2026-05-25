package tests

import (
	"context"
	"fmt"
	"testing"
	"time"

	"floci-sdk-test-go/internal/testutil"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/service/kms"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestKMSCustomKeyStore(t *testing.T) {
	ctx := context.Background()
	svc := testutil.KMSClient()

	storePassword := "TestPassword123!"
	trustAnchorCertificate := "-----BEGIN CERTIFICATE-----\ntest-trust-anchor\n-----END CERTIFICATE-----"

	var customKeyStoreID string
	var customKeyStoreName string
	var updatedCustomKeyStoreName string

	cleanupCustomKeyStore := func(customKeyStoreID string) {
		if customKeyStoreID == "" {
			return
		}

		_, _ = svc.DisconnectCustomKeyStore(ctx, &kms.DisconnectCustomKeyStoreInput{
			CustomKeyStoreId: aws.String(customKeyStoreID),
		})
		_, _ = svc.DeleteCustomKeyStore(ctx, &kms.DeleteCustomKeyStoreInput{
			CustomKeyStoreId: aws.String(customKeyStoreID),
		})
	}

	defer func() {
		cleanupCustomKeyStore(customKeyStoreID)
	}()

	t.Run("CreateCustomKeyStore", func(t *testing.T) {
		customKeyStoreName = fmt.Sprintf("go-custom-key-store-%d", time.Now().UnixNano())
		r, err := svc.CreateCustomKeyStore(ctx, &kms.CreateCustomKeyStoreInput{
			CustomKeyStoreName:     aws.String(customKeyStoreName),
			CloudHsmClusterId:      aws.String(fmt.Sprintf("cluster-%d", time.Now().UnixNano())),
			TrustAnchorCertificate: aws.String(trustAnchorCertificate),
			KeyStorePassword:       aws.String(storePassword),
		})
		require.NoError(t, err)
		require.NotNil(t, r)
		customKeyStoreID = aws.ToString(r.CustomKeyStoreId)
		assert.NotEmpty(t, customKeyStoreID)
	})

	t.Run("DescribeCustomKeyStores", func(t *testing.T) {
		r, err := svc.DescribeCustomKeyStores(ctx, &kms.DescribeCustomKeyStoresInput{})
		require.NoError(t, err)
		require.NotNil(t, r)
		assert.NotEmpty(t, r.CustomKeyStores)

		found := false
		for _, store := range r.CustomKeyStores {
			if aws.ToString(store.CustomKeyStoreId) == customKeyStoreID {
				found = true
				assert.Equal(t, customKeyStoreName, aws.ToString(store.CustomKeyStoreName))
				break
			}
		}
		assert.True(t, found)
	})

	t.Run("DescribeCustomKeyStoresById", func(t *testing.T) {
		r, err := svc.DescribeCustomKeyStores(ctx, &kms.DescribeCustomKeyStoresInput{
			CustomKeyStoreId: aws.String(customKeyStoreID),
		})
		require.NoError(t, err)
		require.NotNil(t, r)
		assert.NotEmpty(t, r.CustomKeyStores)
		require.NotEmpty(t, r.CustomKeyStores)
		assert.Equal(t, customKeyStoreID, aws.ToString(r.CustomKeyStores[0].CustomKeyStoreId))
	})

	t.Run("UpdateCustomKeyStoreName", func(t *testing.T) {
		updatedCustomKeyStoreName = fmt.Sprintf("go-custom-key-store-updated-%d", time.Now().UnixNano())
		r, err := svc.UpdateCustomKeyStore(ctx, &kms.UpdateCustomKeyStoreInput{
			CustomKeyStoreId:      aws.String(customKeyStoreID),
			NewCustomKeyStoreName: aws.String(updatedCustomKeyStoreName),
		})
		require.NoError(t, err)
		require.NotNil(t, r)

		describe, err := svc.DescribeCustomKeyStores(ctx, &kms.DescribeCustomKeyStoresInput{
			CustomKeyStoreId: aws.String(customKeyStoreID),
		})
		require.NoError(t, err)
		require.NotNil(t, describe)
		require.NotEmpty(t, describe.CustomKeyStores)
		assert.Equal(t, updatedCustomKeyStoreName, aws.ToString(describe.CustomKeyStores[0].CustomKeyStoreName))
	})

	t.Run("DisconnectCustomKeyStore", func(t *testing.T) {
		r, err := svc.DisconnectCustomKeyStore(ctx, &kms.DisconnectCustomKeyStoreInput{
			CustomKeyStoreId: aws.String(customKeyStoreID),
		})
		require.NoError(t, err)
		assert.NotNil(t, r)
	})

	t.Run("ConnectCustomKeyStore", func(t *testing.T) {
		r, err := svc.ConnectCustomKeyStore(ctx, &kms.ConnectCustomKeyStoreInput{
			CustomKeyStoreId: aws.String(customKeyStoreID),
		})
		require.NoError(t, err)
		assert.NotNil(t, r)
	})

	t.Run("DeleteCustomKeyStore", func(t *testing.T) {
		r, err := svc.DisconnectCustomKeyStore(ctx, &kms.DisconnectCustomKeyStoreInput{
			CustomKeyStoreId: aws.String(customKeyStoreID),
		})
		require.NoError(t, err)
		assert.NotNil(t, r)

		rm, err := svc.DeleteCustomKeyStore(ctx, &kms.DeleteCustomKeyStoreInput{
			CustomKeyStoreId: aws.String(customKeyStoreID),
		})
		require.NoError(t, err)
		assert.NotNil(t, rm)
		customKeyStoreID = ""
	})

	t.Run("DeleteCustomKeyStoreWhileConnectedFails", func(t *testing.T) {
		tempName := fmt.Sprintf("go-custom-key-store-temp-%d", time.Now().UnixNano())
		tempClusterID := fmt.Sprintf("cluster-temp-%d", time.Now().UnixNano())

		create, err := svc.CreateCustomKeyStore(ctx, &kms.CreateCustomKeyStoreInput{
			CustomKeyStoreName:     aws.String(tempName),
			CloudHsmClusterId:      aws.String(tempClusterID),
			TrustAnchorCertificate: aws.String(trustAnchorCertificate),
			KeyStorePassword:       aws.String(storePassword),
		})
		require.NoError(t, err)
		require.NotNil(t, create)
		tempCustomKeyStoreID := aws.ToString(create.CustomKeyStoreId)
		assert.NotEmpty(t, tempCustomKeyStoreID)
		defer cleanupCustomKeyStore(tempCustomKeyStoreID)

		describe, err := svc.DescribeCustomKeyStores(ctx, &kms.DescribeCustomKeyStoresInput{
			CustomKeyStoreId: aws.String(tempCustomKeyStoreID),
		})
		require.NoError(t, err)
		require.NotEmpty(t, describe.CustomKeyStores)
		if fmt.Sprint(describe.CustomKeyStores[0].ConnectionState) != "CONNECTED" {
			connect, err := svc.ConnectCustomKeyStore(ctx, &kms.ConnectCustomKeyStoreInput{
				CustomKeyStoreId: aws.String(tempCustomKeyStoreID),
			})
			require.NoError(t, err)
			assert.NotNil(t, connect)
		}

		_, err = svc.DeleteCustomKeyStore(ctx, &kms.DeleteCustomKeyStoreInput{
			CustomKeyStoreId: aws.String(tempCustomKeyStoreID),
		})
		require.Error(t, err)
	})
}
