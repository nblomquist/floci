package io.github.hectorvent.floci.services.kms.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;

@RegisterForReflection
@JsonIgnoreProperties(ignoreUnknown = true)
public class KmsCustomKeyStore {
    private String customKeyStoreId;
    private String customKeyStoreName;
    private String customKeyStoreType = "AWS_CLOUDHSM";
    private String cloudHsmClusterId;
    private String trustAnchorCertificate;
    private String keyStorePassword;
    private String connectionState = KmsCustomKeyStoreConnectionState.CREATING.name();
    private String connectionErrorCode;
    private long creationDate;

    public KmsCustomKeyStore() {
        this.creationDate = Instant.now().getEpochSecond();
    }

    public String getCustomKeyStoreId() { return customKeyStoreId; }
    public void setCustomKeyStoreId(String customKeyStoreId) { this.customKeyStoreId = customKeyStoreId; }

    public String getCustomKeyStoreName() { return customKeyStoreName; }
    public void setCustomKeyStoreName(String customKeyStoreName) { this.customKeyStoreName = customKeyStoreName; }

    public String getCustomKeyStoreType() { return customKeyStoreType; }
    public void setCustomKeyStoreType(String customKeyStoreType) { this.customKeyStoreType = customKeyStoreType; }

    public String getCloudHsmClusterId() { return cloudHsmClusterId; }
    public void setCloudHsmClusterId(String cloudHsmClusterId) { this.cloudHsmClusterId = cloudHsmClusterId; }

    public String getTrustAnchorCertificate() { return trustAnchorCertificate; }
    public void setTrustAnchorCertificate(String trustAnchorCertificate) { this.trustAnchorCertificate = trustAnchorCertificate; }

    public String getKeyStorePassword() { return keyStorePassword; }
    public void setKeyStorePassword(String keyStorePassword) { this.keyStorePassword = keyStorePassword; }

    public String getConnectionState() { return connectionState; }
    public void setConnectionState(String connectionState) { this.connectionState = connectionState; }

    public String getConnectionErrorCode() { return connectionErrorCode; }
    public void setConnectionErrorCode(String connectionErrorCode) { this.connectionErrorCode = connectionErrorCode; }

    public long getCreationDate() { return creationDate; }
    public void setCreationDate(long creationDate) { this.creationDate = creationDate; }
}
