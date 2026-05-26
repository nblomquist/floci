package io.github.hectorvent.floci.services.docdb.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RegisterForReflection
public class DocDbInstance {

    private String dbInstanceIdentifier;
    private String dbClusterIdentifier;
    private String dbInstanceClass;
    private String engineVersion;
    private String status;
    private String endpoint;
    private int port;
    private String masterUsername;
    private String dbInstanceArn;
    private String dbiResourceId;
    private Map<String, String> tags = new HashMap<>();
    private Instant createdAt;

    public DocDbInstance() {}

    public String getDbInstanceIdentifier() { return dbInstanceIdentifier; }
    public void setDbInstanceIdentifier(String dbInstanceIdentifier) { this.dbInstanceIdentifier = dbInstanceIdentifier; }

    public String getDbClusterIdentifier() { return dbClusterIdentifier; }
    public void setDbClusterIdentifier(String dbClusterIdentifier) { this.dbClusterIdentifier = dbClusterIdentifier; }

    public String getDbInstanceClass() { return dbInstanceClass; }
    public void setDbInstanceClass(String dbInstanceClass) { this.dbInstanceClass = dbInstanceClass; }

    public String getEngineVersion() { return engineVersion; }
    public void setEngineVersion(String engineVersion) { this.engineVersion = engineVersion; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getMasterUsername() { return masterUsername; }
    public void setMasterUsername(String masterUsername) { this.masterUsername = masterUsername; }

    public String getDbInstanceArn() { return dbInstanceArn; }
    public void setDbInstanceArn(String dbInstanceArn) { this.dbInstanceArn = dbInstanceArn; }

    public String getDbiResourceId() { return dbiResourceId; }
    public void setDbiResourceId(String dbiResourceId) { this.dbiResourceId = dbiResourceId; }

    public Map<String, String> getTags() { return tags; }
    public void setTags(Map<String, String> tags) {
        this.tags = tags != null ? new HashMap<>(tags) : new HashMap<>();
    }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
