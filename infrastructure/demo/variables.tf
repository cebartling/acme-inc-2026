variable "domain_name" {
  description = "Domain registered in Route 53 in this account. The same value the foundation stack was applied with."
  type        = string
}

variable "vpc_cidr" {
  description = "CIDR block of the demo VPC. Two public and two private /24 subnets are carved out of it."
  type        = string
  default     = "10.0.0.0/16"
}

# The three engine versions below are the values to check first if a plan or apply
# rejects one: README.md shows the CLI calls that list what the Region accepts.

variable "postgres_engine_version" {
  description = "RDS for PostgreSQL engine version. A major version alone lets RDS pick the current minor release. Keep major version 16: the parameter group family (rds.tf) and the setup task image (db-init.tf) are 16."
  type        = string
  default     = "16"
}

variable "documentdb_engine_version" {
  description = "DocumentDB engine version. Must be 8.0.2 or later: retryable writes (ADR-0064, decision 4). A minor version is only applied when you choose it here."
  type        = string
  default     = "8.0.2"
}

variable "kafka_version" {
  description = "MSK Apache Kafka version. Use a ZooKeeper-mode 3.x version: kafka.t3.small can't run KRaft, so 4.x and '.kraft' versions are rejected."
  type        = string
  default     = "3.9.x"
}
