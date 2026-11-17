# MongoDB read models (ADR-0064 decision 4). TLS stays on, the DocumentDB default: the
# service images carry the RDS CA bundle, so no cluster parameter group is needed.

resource "aws_docdb_subnet_group" "demo" {
  name       = "acme-demo"
  subnet_ids = aws_subnet.private[*].id
}

resource "aws_docdb_cluster" "demo" {
  cluster_identifier = "acme-demo"
  engine             = "docdb"
  engine_version     = var.documentdb_engine_version

  master_username = local.documentdb_user
  master_password = random_password.documentdb.result

  db_subnet_group_name   = aws_docdb_subnet_group.demo.name
  vpc_security_group_ids = [aws_security_group.data_store["documentdb"].id]

  # Destroyable: no final snapshot, no deletion protection. DocumentDB requires at
  # least one day of backup retention.
  backup_retention_period = 1
  skip_final_snapshot     = true
  deletion_protection     = false
  apply_immediately       = true
}

resource "aws_docdb_cluster_instance" "demo" {
  identifier         = "acme-demo-1"
  cluster_identifier = aws_docdb_cluster.demo.id
  instance_class     = "db.t4g.medium"
}
