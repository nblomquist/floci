"""DocumentDB compatibility tests."""

from botocore.exceptions import ClientError


class TestDocDbControlPlane:
    """Test DocumentDB subnet group, cluster, instance, and tag operations."""

    def test_create_describe_tag_and_delete_docdb_resources(
        self, docdb_client, unique_name
    ):
        """Test the initial DocumentDB MVP control-plane lifecycle."""
        subnet_group = f"pytest-docdb-subnet-{unique_name}"
        cluster_id = f"pytest-docdb-cluster-{unique_name}"
        instance_id = f"pytest-docdb-instance-{unique_name}"
        cluster_arn = None

        try:
            subnet_response = docdb_client.create_db_subnet_group(
                DBSubnetGroupName=subnet_group,
                DBSubnetGroupDescription="pytest docdb subnet group",
                SubnetIds=["subnet-11111111", "subnet-22222222"],
                Tags=[{"Key": "purpose", "Value": "compat"}],
            )
            assert subnet_response["DBSubnetGroup"]["DBSubnetGroupName"] == subnet_group

            subnet_groups = docdb_client.describe_db_subnet_groups(
                DBSubnetGroupName=subnet_group
            )["DBSubnetGroups"]
            assert len(subnet_groups) == 1
            assert subnet_groups[0]["DBSubnetGroupName"] == subnet_group

            cluster_response = docdb_client.create_db_cluster(
                DBClusterIdentifier=cluster_id,
                Engine="docdb",
                MasterUsername="masteruser",
                MasterUserPassword="secret12345",
                DBSubnetGroupName=subnet_group,
            )
            cluster = cluster_response["DBCluster"]
            assert cluster["DBClusterIdentifier"] == cluster_id
            assert cluster["Engine"] == "docdb"
            cluster_arn = cluster["DBClusterArn"]

            clusters = docdb_client.describe_db_clusters(
                DBClusterIdentifier=cluster_id
            )["DBClusters"]
            assert len(clusters) == 1
            assert clusters[0]["DBClusterIdentifier"] == cluster_id

            instance_response = docdb_client.create_db_instance(
                DBInstanceIdentifier=instance_id,
                DBClusterIdentifier=cluster_id,
                DBInstanceClass="db.r5.large",
                Engine="docdb",
            )
            instance = instance_response["DBInstance"]
            assert instance["DBInstanceIdentifier"] == instance_id
            assert instance["DBClusterIdentifier"] == cluster_id

            instances = docdb_client.describe_db_instances(
                DBInstanceIdentifier=instance_id
            )["DBInstances"]
            assert len(instances) == 1
            assert instances[0]["DBInstanceIdentifier"] == instance_id

            docdb_client.add_tags_to_resource(
                ResourceName=cluster_arn,
                Tags=[{"Key": "phase", "Value": "zero"}],
            )
            tags = docdb_client.list_tags_for_resource(ResourceName=cluster_arn)[
                "TagList"
            ]
            assert {"Key": "phase", "Value": "zero"} in tags
        finally:
            try:
                docdb_client.delete_db_instance(DBInstanceIdentifier=instance_id)
            except ClientError:
                pass
            try:
                docdb_client.delete_db_cluster(
                    DBClusterIdentifier=cluster_id,
                    SkipFinalSnapshot=True,
                )
            except ClientError:
                pass
            try:
                docdb_client.delete_db_subnet_group(DBSubnetGroupName=subnet_group)
            except ClientError:
                pass
