output "vpc_id" {
  description = "Demo VPC."
  value       = aws_vpc.demo.id
}

output "public_subnet_ids" {
  description = "Public subnets, one per Availability Zone. The load balancer goes here."
  value       = aws_subnet.public[*].id
}

output "private_subnet_ids" {
  description = "Private subnets, one per Availability Zone. The service tasks go here."
  value       = aws_subnet.private[*].id
}

output "services_security_group_id" {
  description = "Security group the data stores accept traffic from. Attach it to every service task."
  value       = aws_security_group.services.id
}

output "ecs_cluster_name" {
  description = "ECS cluster that runs the services and the database setup task."
  value       = aws_ecs_cluster.demo.name
}

output "postgres_host" {
  description = "RDS for PostgreSQL endpoint address. The services' POSTGRES_HOST."
  value       = aws_db_instance.postgres.address
}

output "postgres_port" {
  description = "RDS for PostgreSQL port. The services' POSTGRES_PORT."
  value       = aws_db_instance.postgres.port
}

output "postgres_databases" {
  description = "Service databases created in RDS."
  value       = local.service_databases
}

output "postgres_secret_arn" {
  description = "Secrets Manager secret holding POSTGRES_USER and POSTGRES_PASSWORD."
  value       = aws_secretsmanager_secret.postgres.arn
}

output "documentdb_endpoint" {
  description = "DocumentDB cluster endpoint."
  value       = aws_docdb_cluster.demo.endpoint
}

output "documentdb_secret_arn" {
  description = "Secrets Manager secret holding MONGODB_URI, MONGODB_USER and MONGODB_PASSWORD."
  value       = aws_secretsmanager_secret.documentdb.arn
}

output "redis_host" {
  description = "ElastiCache Redis node address. The services' REDIS_HOST."
  value       = aws_elasticache_cluster.demo.cache_nodes[0].address
}

output "redis_port" {
  description = "ElastiCache Redis port. The services' REDIS_PORT."
  value       = aws_elasticache_cluster.demo.cache_nodes[0].port
}

output "kafka_bootstrap_brokers" {
  description = "MSK plaintext bootstrap brokers, comma-separated. The services' KAFKA_BOOTSTRAP_SERVERS."
  value       = aws_msk_cluster.demo.bootstrap_brokers
}

output "urls" {
  description = "Public URLs: the customer frontend, the admin frontend and the API host."
  value       = { for name, hostname in local.hostnames : name => "https://${hostname}" }
}

output "load_balancer_dns_name" {
  description = "Load balancer DNS name. The Route 53 records for the three hostnames alias it."
  value       = aws_lb.demo.dns_name
}

output "test_api_key_secret_arn" {
  description = "Secrets Manager secret holding IDENTITY_TEST_API_KEY, the key for identity's test API (X-Test-Api-Key)."
  value       = aws_secretsmanager_secret.test_api_key.arn
}
