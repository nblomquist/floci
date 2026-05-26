package com.floci.test;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import software.amazon.awssdk.services.docdb.DocDbClient;
import software.amazon.awssdk.services.docdb.model.AddTagsToResourceRequest;
import software.amazon.awssdk.services.docdb.model.CreateDbClusterRequest;
import software.amazon.awssdk.services.docdb.model.CreateDbInstanceRequest;
import software.amazon.awssdk.services.docdb.model.CreateDbSubnetGroupRequest;
import software.amazon.awssdk.services.docdb.model.DeleteDbClusterRequest;
import software.amazon.awssdk.services.docdb.model.DeleteDbInstanceRequest;
import software.amazon.awssdk.services.docdb.model.DeleteDbSubnetGroupRequest;
import software.amazon.awssdk.services.docdb.model.DescribeDbClustersRequest;
import software.amazon.awssdk.services.docdb.model.DescribeDbInstancesRequest;
import software.amazon.awssdk.services.docdb.model.DescribeDbSubnetGroupsRequest;
import software.amazon.awssdk.services.docdb.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.docdb.model.Tag;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DocumentDB Control Plane Compatibility")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DocDbTest {

    private static DocDbClient docdb;
    private static final String SUBNET_GROUP = TestFixtures.uniqueName("docdb-subnet");
    private static final String CLUSTER_ID = TestFixtures.uniqueName("docdb-cluster");
    private static final String INSTANCE_ID = TestFixtures.uniqueName("docdb-instance");
    private static String clusterArn;

    @BeforeAll
    static void setup() {
        docdb = TestFixtures.docDbClient();
    }

    @AfterAll
    static void cleanup() {
        if (docdb == null) return;
        try {
            docdb.deleteDBInstance(DeleteDbInstanceRequest.builder()
                    .dbInstanceIdentifier(INSTANCE_ID)
                    .build());
        } catch (Exception ignored) {}
        try {
            docdb.deleteDBCluster(DeleteDbClusterRequest.builder()
                    .dbClusterIdentifier(CLUSTER_ID)
                    .skipFinalSnapshot(true)
                    .build());
        } catch (Exception ignored) {}
        try {
            docdb.deleteDBSubnetGroup(DeleteDbSubnetGroupRequest.builder()
                    .dbSubnetGroupName(SUBNET_GROUP)
                    .build());
        } catch (Exception ignored) {}
        docdb.close();
    }

    @Test
    @Order(1)
    @DisplayName("CreateDBSubnetGroup returns valid subnet group descriptor")
    void createSubnetGroup() {
        var response = docdb.createDBSubnetGroup(CreateDbSubnetGroupRequest.builder()
                .dbSubnetGroupName(SUBNET_GROUP)
                .dbSubnetGroupDescription("junit docdb subnet group")
                .subnetIds("subnet-11111111", "subnet-22222222")
                .tags(Tag.builder().key("purpose").value("compat").build())
                .build());

        assertThat(response.dbSubnetGroup().dbSubnetGroupName()).isEqualTo(SUBNET_GROUP);
    }

    @Test
    @Order(2)
    @DisplayName("DescribeDBSubnetGroups returns created subnet group")
    void describeSubnetGroups() {
        var response = docdb.describeDBSubnetGroups(DescribeDbSubnetGroupsRequest.builder()
                .dbSubnetGroupName(SUBNET_GROUP)
                .build());

        assertThat(response.dbSubnetGroups()).hasSize(1);
        assertThat(response.dbSubnetGroups().get(0).dbSubnetGroupName()).isEqualTo(SUBNET_GROUP);
    }

    @Test
    @Order(3)
    @DisplayName("CreateDBCluster returns valid DocumentDB cluster descriptor")
    void createCluster() {
        var response = docdb.createDBCluster(CreateDbClusterRequest.builder()
                .dbClusterIdentifier(CLUSTER_ID)
                .engine("docdb")
                .masterUsername("masteruser")
                .masterUserPassword("secret12345")
                .dbSubnetGroupName(SUBNET_GROUP)
                .build());

        var cluster = response.dbCluster();
        assertThat(cluster.dbClusterIdentifier()).isEqualTo(CLUSTER_ID);
        assertThat(cluster.engine()).isEqualTo("docdb");
        clusterArn = cluster.dbClusterArn();
        assertThat(clusterArn).startsWith("arn:aws:rds:");
    }

    @Test
    @Order(4)
    @DisplayName("DescribeDBClusters returns created cluster")
    void describeClusters() {
        var response = docdb.describeDBClusters(DescribeDbClustersRequest.builder()
                .dbClusterIdentifier(CLUSTER_ID)
                .build());

        assertThat(response.dbClusters()).hasSize(1);
        assertThat(response.dbClusters().get(0).dbClusterIdentifier()).isEqualTo(CLUSTER_ID);
    }

    @Test
    @Order(5)
    @DisplayName("CreateDBInstance returns valid DocumentDB instance descriptor")
    void createInstance() {
        var response = docdb.createDBInstance(CreateDbInstanceRequest.builder()
                .dbInstanceIdentifier(INSTANCE_ID)
                .dbClusterIdentifier(CLUSTER_ID)
                .dbInstanceClass("db.r5.large")
                .engine("docdb")
                .build());

        var instance = response.dbInstance();
        assertThat(instance.dbInstanceIdentifier()).isEqualTo(INSTANCE_ID);
        assertThat(instance.dbClusterIdentifier()).isEqualTo(CLUSTER_ID);
    }

    @Test
    @Order(6)
    @DisplayName("DescribeDBInstances returns created instance")
    void describeInstances() {
        var response = docdb.describeDBInstances(DescribeDbInstancesRequest.builder()
                .dbInstanceIdentifier(INSTANCE_ID)
                .build());

        assertThat(response.dbInstances()).hasSize(1);
        assertThat(response.dbInstances().get(0).dbInstanceIdentifier()).isEqualTo(INSTANCE_ID);
    }

    @Test
    @Order(7)
    @DisplayName("AddTagsToResource and ListTagsForResource round-trip cluster tags")
    void tagsCluster() {
        docdb.addTagsToResource(AddTagsToResourceRequest.builder()
                .resourceName(clusterArn)
                .tags(Tag.builder().key("phase").value("zero").build())
                .build());

        var response = docdb.listTagsForResource(ListTagsForResourceRequest.builder()
                .resourceName(clusterArn)
                .build());

        assertThat(response.tagList()).anySatisfy(tag -> {
            assertThat(tag.key()).isEqualTo("phase");
            assertThat(tag.value()).isEqualTo("zero");
        });
    }
}
