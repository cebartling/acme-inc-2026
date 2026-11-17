# Redis, used by identity and customer. A single node. In-transit encryption is off: the
# services have no Redis TLS config.

resource "aws_elasticache_subnet_group" "demo" {
  name       = "acme-demo"
  subnet_ids = aws_subnet.private[*].id
}

resource "aws_elasticache_cluster" "demo" {
  cluster_id           = "acme-demo"
  engine               = "redis"
  engine_version       = "7.1"
  node_type            = "cache.t4g.micro"
  num_cache_nodes      = 1
  parameter_group_name = "default.redis7"
  port                 = 6379

  transit_encryption_enabled = false

  subnet_group_name  = aws_elasticache_subnet_group.demo.name
  security_group_ids = [aws_security_group.data_store["redis"].id]
  apply_immediately  = true
}
