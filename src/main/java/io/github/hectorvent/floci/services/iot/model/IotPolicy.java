package io.github.hectorvent.floci.services.iot.model;

import java.time.Instant;

public class IotPolicy {
    private String policyName;
    private String policyArn;
    private String policyDocument;
    private String defaultVersionId;
    private Instant creationDate;

    public String getPolicyName() { return policyName; }
    public void setPolicyName(String policyName) { this.policyName = policyName; }
    public String getPolicyArn() { return policyArn; }
    public void setPolicyArn(String policyArn) { this.policyArn = policyArn; }
    public String getPolicyDocument() { return policyDocument; }
    public void setPolicyDocument(String policyDocument) { this.policyDocument = policyDocument; }
    public String getDefaultVersionId() { return defaultVersionId; }
    public void setDefaultVersionId(String defaultVersionId) { this.defaultVersionId = defaultVersionId; }
    public Instant getCreationDate() { return creationDate; }
    public void setCreationDate(Instant creationDate) { this.creationDate = creationDate; }
}
