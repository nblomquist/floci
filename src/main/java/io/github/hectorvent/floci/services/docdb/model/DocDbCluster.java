package io.github.hectorvent.floci.services.docdb.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RegisterForReflection
public class DocDbCluster {

    private String dbClusterIdentifier;
    private String status;
    private String engineVersion;
    private String endpoint;
    private int port;
    private String readerEndpoint;
    private String masterUsername;
    private String dbClusterArn;
    private String dbClusterResourceId;
    private String dbSubnetGroup;
    private boolean multiAz;
    private boolean storageEncrypted;
    private String availabilityZone;
    private List<String> dbClusterMembers = new ArrayList<>();
    private Map<String, String> tags = new HashMap<>();
    private Instant createdAt;

    public DocDbCluster() {}

    public String getDbClusterIdentifier() { return dbClusterIdentifier; }
    public void setDbClusterIdentifier(String dbClusterIdentifier) { this.dbClusterIdentifier = dbClusterIdentifier; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getEngineVersion() { return engineVersion; }
    public void setEngineVersion(String engineVersion) { this.engineVersion = engineVersion; }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getReaderEndpoint() { return readerEndpoint; }
    public void setReaderEndpoint(String readerEndpoint) { this.readerEndpoint = readerEndpoint; }

    public String getMasterUsername() { return masterUsername; }
    public void setMasterUsername(String masterUsername) { this.masterUsername = masterUsername; }

    public String getDbClusterArn() { return dbClusterArn; }
    public void setDbClusterArn(String dbClusterArn) { this.dbClusterArn = dbClusterArn; }

    public String getDbClusterResourceId() { return dbClusterResourceId; }
    public void setDbClusterResourceId(String dbClusterResourceId) { this.dbClusterResourceId = dbClusterResourceId; }

    public String getDbSubnetGroup() { return dbSubnetGroup; }
    public void setDbSubnetGroup(String dbSubnetGroup) { this.dbSubnetGroup = dbSubnetGroup; }

    public boolean isMultiAz() { return multiAz; }
    public void setMultiAz(boolean multiAz) { this.multiAz = multiAz; }

    public boolean isStorageEncrypted() { return storageEncrypted; }
    public void setStorageEncrypted(boolean storageEncrypted) { this.storageEncrypted = storageEncrypted; }

    public String getAvailabilityZone() { return availabilityZone; }
    public void setAvailabilityZone(String availabilityZone) { this.availabilityZone = availabilityZone; }

    public List<String> getDbClusterMembers() { return dbClusterMembers; }
    public void setDbClusterMembers(List<String> dbClusterMembers) {
        this.dbClusterMembers = dbClusterMembers != null ? new ArrayList<>(dbClusterMembers) : new ArrayList<>();
    }

    public Map<String, String> getTags() { return tags; }
    public void setTags(Map<String, String> tags) {
        this.tags = tags != null ? new HashMap<>(tags) : new HashMap<>();
    }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
