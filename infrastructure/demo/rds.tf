# One PostgreSQL instance holds the five service databases (db-init.tf creates them).

resource "aws_db_subnet_group" "postgres" {
  name       = "acme-demo-postgres"
  subnet_ids = aws_subnet.private[*].id
}

# Debezium reads the write-ahead log through logical replication (PIN-534). The setting is
# static, so it only takes effect at instance start; the instance is created with the group.
resource "aws_db_parameter_group" "postgres" {
  name   = "acme-demo-postgres"
  family = "postgres16"

  parameter {
    name         = "rds.logical_replication"
    value        = "1"
    apply_method = "pending-reboot"
  }
}

resource "aws_db_instance" "postgres" {
  identifier     = "acme-demo"
  engine         = "postgres"
  engine_version = var.postgres_engine_version
  instance_class = "db.t4g.micro"

  allocated_storage = 20
  storage_type      = "gp3"

  username = local.postgres_user
  password = random_password.postgres.result

  db_subnet_group_name   = aws_db_subnet_group.postgres.name
  vpc_security_group_ids = [aws_security_group.data_store["postgres"].id]
  parameter_group_name   = aws_db_parameter_group.postgres.name
  publicly_accessible    = false

  # Destroyable: no automated backups, no final snapshot, no deletion protection.
  backup_retention_period = 0
  skip_final_snapshot     = true
  deletion_protection     = false
  apply_immediately       = true
}
