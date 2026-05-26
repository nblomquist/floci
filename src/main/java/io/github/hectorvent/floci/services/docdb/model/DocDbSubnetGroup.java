package io.github.hectorvent.floci.services.docdb.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.ArrayList;
import java.util.List;

@RegisterForReflection
public class DocDbSubnetGroup {

    private String dbSubnetGroupName;
    private String dbSubnetGroupDescription;
    private String vpcId;
    private String subnetGroupStatus;
    private List<Subnet> subnets = new ArrayList<>();

    public DocDbSubnetGroup() {}

    public String getDbSubnetGroupName() { return dbSubnetGroupName; }
    public void setDbSubnetGroupName(String dbSubnetGroupName) { this.dbSubnetGroupName = dbSubnetGroupName; }

    public String getDbSubnetGroupDescription() { return dbSubnetGroupDescription; }
    public void setDbSubnetGroupDescription(String dbSubnetGroupDescription) { this.dbSubnetGroupDescription = dbSubnetGroupDescription; }

    public String getVpcId() { return vpcId; }
    public void setVpcId(String vpcId) { this.vpcId = vpcId; }

    public String getSubnetGroupStatus() { return subnetGroupStatus; }
    public void setSubnetGroupStatus(String subnetGroupStatus) { this.subnetGroupStatus = subnetGroupStatus; }

    public List<Subnet> getSubnets() { return subnets; }
    public void setSubnets(List<Subnet> subnets) {
        this.subnets = subnets != null ? new ArrayList<>(subnets) : new ArrayList<>();
    }

    @RegisterForReflection
    public static class Subnet {
        private String subnetIdentifier;
        private String subnetAvailabilityZoneName;
        private String subnetStatus;

        public Subnet() {}

        public Subnet(String subnetIdentifier, String subnetAvailabilityZoneName, String subnetStatus) {
            this.subnetIdentifier = subnetIdentifier;
            this.subnetAvailabilityZoneName = subnetAvailabilityZoneName;
            this.subnetStatus = subnetStatus;
        }

        public String getSubnetIdentifier() { return subnetIdentifier; }
        public void setSubnetIdentifier(String subnetIdentifier) { this.subnetIdentifier = subnetIdentifier; }

        public String getSubnetAvailabilityZoneName() { return subnetAvailabilityZoneName; }
        public void setSubnetAvailabilityZoneName(String subnetAvailabilityZoneName) { this.subnetAvailabilityZoneName = subnetAvailabilityZoneName; }

        public String getSubnetStatus() { return subnetStatus; }
        public void setSubnetStatus(String subnetStatus) { this.subnetStatus = subnetStatus; }
    }
}
