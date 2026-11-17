# Database credentials, generated per demo. The JSON keys match the environment variables
# the services read, so ECS can inject each key by name. The values are in Terraform state,
# which lives in the private state bucket.
#
# recovery_window_in_days = 0 deletes a secret at once on destroy. Without it the name stays
# reserved for 30 days and the next apply fails.

# Letters and digits only: the DocumentDB password goes into a URI, and a special
# character would need escaping.
resource "random_password" "postgres" {
  length  = 32
  special = false
}

resource "random_password" "documentdb" {
  length  = 32
  special = false
}

locals {
  postgres_user   = "postgres"
  documentdb_user = "acmeadmin"
}

# Read by every backend service: POSTGRES_USER and POSTGRES_PASSWORD in docker-compose.apps.yml.
resource "aws_secretsmanager_secret" "postgres" {
  name                    = "acme-demo/postgres"
  description             = "RDS for PostgreSQL credentials. JSON keys: POSTGRES_USER, POSTGRES_PASSWORD."
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "postgres" {
  secret_id = aws_secretsmanager_secret.postgres.id
  secret_string = jsonencode({
    POSTGRES_USER     = local.postgres_user
    POSTGRES_PASSWORD = random_password.postgres.result
  })
}

# MONGODB_URI follows ADR-0064 decision 4: TLS on, replica set rs0.
resource "aws_secretsmanager_secret" "documentdb" {
  name                    = "acme-demo/documentdb"
  description             = "DocumentDB credentials. JSON keys: MONGODB_URI, MONGODB_USER, MONGODB_PASSWORD."
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "documentdb" {
  secret_id = aws_secretsmanager_secret.documentdb.id
  secret_string = jsonencode({
    MONGODB_URI      = "mongodb://${local.documentdb_user}:${random_password.documentdb.result}@${aws_docdb_cluster.demo.endpoint}:${aws_docdb_cluster.demo.port}/?tls=true&replicaSet=rs0"
    MONGODB_USER     = local.documentdb_user
    MONGODB_PASSWORD = random_password.documentdb.result
  })
}
