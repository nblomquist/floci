# DocumentDB

**Protocol:** Query (XML) for management API + MongoDB wire protocol for data plane
**Management Endpoint:** `POST http://localhost:4566/`
**Data Endpoint:** `localhost:<proxy-port>` (TCP)

Floci manages a real MongoDB Docker container per DocumentDB cluster and proxies TCP connections to it. The MongoDB engine is a stock `mongodb/mongodb-community-server` image — not the Amazon DocumentDB engine, but MongoDB 8.0 API-compatible for local development and testing.

## Supported Management Actions

| Action | Description |
|---|---|
| `CreateDBSubnetGroup` | Create a subnet group (metadata-only) |
| `DescribeDBSubnetGroups` | List subnet groups |
| `DeleteDBSubnetGroup` | Delete a subnet group |
| `CreateDBCluster` | Start a new MongoDB container with TCP proxy |
| `DescribeDBClusters` | List clusters and their connection info |
| `DeleteDBCluster` | Stop the MongoDB container, remove proxy, delete cluster |
| `ModifyDBCluster` | Update cluster settings |
| `CreateDBInstance` | Register an instance (metadata-only; shares the cluster's backend) |
| `DescribeDBInstances` | List instances and their connection info |
| `DeleteDBInstance` | Remove an instance (metadata-only) |
| `ModifyDBInstance` | Update instance settings |
| `AddTagsToResource` | Tag a cluster, instance, or subnet group by ARN |
| `RemoveTagsFromResource` | Remove tags from a resource |
| `ListTagsForResource` | List tags on a resource by ARN |

## Configuration

| Variable | Default | Description |
|---|---|---|
| `FLOCI_SERVICES_DOCDB_ENABLED` | `true` | Enable or disable the service |
| `FLOCI_SERVICES_DOCDB_PROXY_BASE_PORT` | `8300` | First host port in the DocumentDB proxy range |
| `FLOCI_SERVICES_DOCDB_PROXY_MAX_PORT` | `8399` | Last host port in the DocumentDB proxy range |
| `FLOCI_SERVICES_DOCDB_DEFAULT_IMAGE` | `mongodb/mongodb-community-server:8.0-ubi9-slim` | Docker image for MongoDB containers |

### Docker Compose

DocumentDB requires the Docker socket and port range exposure. For private registry authentication and other Docker settings see [Docker Configuration](../configuration/docker.md).

```yaml
services:
  floci:
    image: floci/floci:latest
    ports:
      - "4566:4566"
      - "8300-8399:8300-8399"   # DocumentDB proxy ports
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
    environment:
      FLOCI_SERVICES_DOCKER_NETWORK: my-project_default
```

## Examples

```bash
export AWS_ENDPOINT_URL=http://localhost:4566

# Create a subnet group
aws docdb create-db-subnet-group \
  --db-subnet-group-name my-subnets \
  --db-subnet-group-description "Dev subnets" \
  --subnet-ids subnet-00000000 \
  --endpoint-url $AWS_ENDPOINT_URL

# Create a cluster (starts a MongoDB container)
aws docdb create-db-cluster \
  --db-cluster-identifier my-docdb \
  --engine docdb \
  --master-username admin \
  --master-user-password secret123 \
  --db-subnet-group-name my-subnets \
  --endpoint-url $AWS_ENDPOINT_URL

# Get the connection port
PORT=$(aws docdb describe-db-clusters \
  --db-cluster-identifier my-docdb \
  --query 'DBClusters[0].Port' \
  --output text \
  --endpoint-url $AWS_ENDPOINT_URL)

# Connect with mongosh
mongosh --host localhost --port $PORT

# Or connect from Java with the MongoDB driver
# MongoClient client = MongoClients.create("mongodb://localhost:" + port);
# MongoDatabase db = client.getDatabase("test");
# db.getCollection("docs").insertOne(new Document("hello", "world"));

# Add tags
aws docdb add-tags-to-resource \
  --resource-name arn:aws:rds:us-east-1:000000000000:cluster:my-docdb \
  --tags Key=env,Value=dev \
  --endpoint-url $AWS_ENDPOINT_URL

# Delete the cluster
aws docdb delete-db-cluster \
  --db-cluster-identifier my-docdb \
  --endpoint-url $AWS_ENDPOINT_URL
```

## Supported Engines

| Engine | Default image |
|---|---|
| `docdb` | `mongodb/mongodb-community-server:8.0-ubi9-slim` |

Override the image globally via `FLOCI_SERVICES_DOCDB_DEFAULT_IMAGE`.

## Persistence

DocumentDB does **not** create persistent Docker volumes for the MongoDB container. Container document data is ephemeral — it is lost when the cluster is deleted or Floci restarts. Metadata (cluster, instance, and subnet group definitions) persists according to `FLOCI_STORAGE_MODE`.

| Scenario | Metadata | Document data |
|---|---|---|
| `memory` mode (default) | Lost on restart | Lost on restart/cluster delete |
| `persistent` / `hybrid` / `wal` | Retained | Ephemeral — lost on cluster delete |

## Compatibility Boundary

- **Data plane:** Stock MongoDB 8.0, not the Amazon DocumentDB engine. MongoDB wire protocol is identical; engine-specific features (e.g. DocumentDB triggers, certain aggregation operators) may differ.
- **Authentication:** Master username and password are set at cluster creation. No IAM database authentication support.
- **TLS/SSL:** Not supported on the proxy — use plain `mongodb://` connections.
- **Replica sets:** Single-node containers only. No replica set configuration.
- **Instances:** Metadata-only. All instances in a cluster share the same single-node MongoDB backend.
