/**
 * KMS custom key store integration tests.
 */

import { describe, it, expect, beforeAll } from 'vitest';
import {
  KMSClient,
  ConnectCustomKeyStoreCommand,
  CreateCustomKeyStoreCommand,
  DeleteCustomKeyStoreCommand,
  DescribeCustomKeyStoresCommand,
  DisconnectCustomKeyStoreCommand,
  UpdateCustomKeyStoreCommand,
} from '@aws-sdk/client-kms';
import selfsigned from 'selfsigned';
import { makeClient, uniqueName } from './setup';

describe('KMS Custom Key Store', () => {
  let kms: KMSClient;
  let cloudHsmClusterId: string;
  let trustAnchorCertificate: string;
  let keyStorePassword: string;

  beforeAll(async () => {
    kms = makeClient(KMSClient);
    cloudHsmClusterId = `cluster-${uniqueName('hsm')}`;
    keyStorePassword = `password-${uniqueName('kms')}`;

    const pems = await selfsigned.generate([
      { name: 'commonName', value: `${uniqueName('trust-anchor')}.example.com` },
    ]);
    trustAnchorCertificate = pems.cert;
  });

  async function createCustomKeyStore(name: string): Promise<string> {
    const response = await kms.send(
      new CreateCustomKeyStoreCommand({
        CustomKeyStoreName: name,
        CloudHsmClusterId: cloudHsmClusterId,
        TrustAnchorCertificate: trustAnchorCertificate,
        KeyStorePassword: keyStorePassword,
      })
    );

    expect(response.CustomKeyStoreId).toBeTruthy();
    return response.CustomKeyStoreId!;
  }

  async function describeCustomKeyStore(customKeyStoreId: string) {
    const response = await kms.send(
      new DescribeCustomKeyStoresCommand({
        CustomKeyStoreId: customKeyStoreId,
      })
    );

    return response.CustomKeyStores?.find((store) => store.CustomKeyStoreId === customKeyStoreId);
  }

  async function disconnectCustomKeyStore(customKeyStoreId: string): Promise<void> {
    await kms.send(
      new DisconnectCustomKeyStoreCommand({
        CustomKeyStoreId: customKeyStoreId,
      })
    );
  }

  async function connectCustomKeyStore(customKeyStoreId: string): Promise<void> {
    await kms.send(
      new ConnectCustomKeyStoreCommand({
        CustomKeyStoreId: customKeyStoreId,
      })
    );
  }

  async function deleteCustomKeyStore(customKeyStoreId: string): Promise<void> {
    await kms.send(
      new DeleteCustomKeyStoreCommand({
        CustomKeyStoreId: customKeyStoreId,
      })
    );
  }

  async function cleanupCustomKeyStore(customKeyStoreId: string | undefined): Promise<void> {
    if (!customKeyStoreId) {
      return;
    }

    try {
      await disconnectCustomKeyStore(customKeyStoreId);
    } catch {
      // The store may already be disconnected.
    }

    try {
      await deleteCustomKeyStore(customKeyStoreId);
    } catch {
      // Ignore cleanup failures so the test can report the original assertion.
    }
  }

  async function withCustomKeyStore<T>(
    name: string,
    fn: (customKeyStoreId: string) => Promise<T>
  ): Promise<T> {
    const customKeyStoreId = await createCustomKeyStore(name);

    try {
      return await fn(customKeyStoreId);
    } finally {
      await cleanupCustomKeyStore(customKeyStoreId);
    }
  }

  it('should create custom key store', async () => {
    await withCustomKeyStore(`custom-keystore-${uniqueName()}`, async (customKeyStoreId) => {
      expect(customKeyStoreId).toBeTruthy();
    });
  });

  it('should describe custom key stores', async () => {
    await withCustomKeyStore(`custom-keystore-${uniqueName()}`, async (customKeyStoreId) => {
      const response = await kms.send(new DescribeCustomKeyStoresCommand({}));
      const store = response.CustomKeyStores?.find(
        (entry) => entry.CustomKeyStoreId === customKeyStoreId
      );

      expect(store).toBeTruthy();
      expect(store?.CustomKeyStoreName).toBeTruthy();
    });
  });

  it('should describe custom key stores by id', async () => {
    await withCustomKeyStore(`custom-keystore-${uniqueName()}`, async (customKeyStoreId) => {
      const store = await describeCustomKeyStore(customKeyStoreId);

      expect(store).toBeTruthy();
      expect(store?.CustomKeyStoreId).toBe(customKeyStoreId);
    });
  });

  it('should update custom key store name', async () => {
    await withCustomKeyStore(`custom-keystore-${uniqueName()}`, async (customKeyStoreId) => {
      const updatedName = `custom-keystore-updated-${uniqueName()}`;

      await disconnectCustomKeyStore(customKeyStoreId);

      await kms.send(
        new UpdateCustomKeyStoreCommand({
          CustomKeyStoreId: customKeyStoreId,
          NewCustomKeyStoreName: updatedName,
        })
      );

      const store = await describeCustomKeyStore(customKeyStoreId);

      expect(store?.CustomKeyStoreName).toBe(updatedName);
    });
  });

  it('should connect custom key store', async () => {
    await withCustomKeyStore(`custom-keystore-${uniqueName()}`, async (customKeyStoreId) => {
      await disconnectCustomKeyStore(customKeyStoreId);
      await connectCustomKeyStore(customKeyStoreId);

      const store = await describeCustomKeyStore(customKeyStoreId);
      expect(store?.ConnectionState).toBe('CONNECTED');
    });
  });

  it('should disconnect custom key store', async () => {
    await withCustomKeyStore(`custom-keystore-${uniqueName()}`, async (customKeyStoreId) => {
      await disconnectCustomKeyStore(customKeyStoreId);

      const store = await describeCustomKeyStore(customKeyStoreId);
      expect(store?.ConnectionState).toBe('DISCONNECTED');
    });
  });

  it('should delete custom key store', async () => {
    await withCustomKeyStore(`custom-keystore-${uniqueName()}`, async (customKeyStoreId) => {
      await disconnectCustomKeyStore(customKeyStoreId);
      await deleteCustomKeyStore(customKeyStoreId);

      const store = await describeCustomKeyStore(customKeyStoreId);
      expect(store).toBeUndefined();
    });
  });

  it('should fail to delete connected custom key store', async () => {
    await withCustomKeyStore(`custom-keystore-${uniqueName()}`, async (customKeyStoreId) => {
      await expect(
        kms.send(
          new DeleteCustomKeyStoreCommand({
            CustomKeyStoreId: customKeyStoreId,
          })
        )
      ).rejects.toThrow();

      const store = await describeCustomKeyStore(customKeyStoreId);
      expect(store).toBeTruthy();
    });
  });
});
