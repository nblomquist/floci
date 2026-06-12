package io.github.hectorvent.floci.services.iot.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class Thing {

    private String thingName;
    private String thingArn;
    private String thingId;
    private Map<String, String> attributes = new HashMap<>();
    private long version;
    private Instant creationDate;
    private Instant lastModifiedDate;

    public String getThingName() {
        return thingName;
    }

    public void setThingName(String thingName) {
        this.thingName = thingName;
    }

    public String getThingArn() {
        return thingArn;
    }

    public void setThingArn(String thingArn) {
        this.thingArn = thingArn;
    }

    public String getThingId() {
        return thingId;
    }

    public void setThingId(String thingId) {
        this.thingId = thingId;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes != null ? new HashMap<>(attributes) : new HashMap<>();
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public Instant getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Instant creationDate) {
        this.creationDate = creationDate;
    }

    public Instant getLastModifiedDate() {
        return lastModifiedDate;
    }

    public void setLastModifiedDate(Instant lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate;
    }
}
