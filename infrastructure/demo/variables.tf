variable "vpc_cidr" {
  description = "CIDR block of the demo VPC. Two public and two private /24 subnets are carved out of it."
  type        = string
  default     = "10.0.0.0/16"
}

# The three engine versions below are the values to check first if a plan or apply
# rejects one: README.md shows the CLI calls that list what the Region accepts.

variable "postgres_engine_version" {
  description = "RDS for PostgreSQL engine version. A major version alone lets RDS pick the current minor release."
  type        = string
  default     = "16"
}

variable "documentdb_engine_version" {
  description = "DocumentDB engine version. Must be 8.0.2 or later: retryable writes (ADR-0064, decision 4). A minor version is only applied when you choose it here."
  type        = string
  default     = "8.0.2"
}

variable "kafka_version" {
  description = "MSK Apache Kafka version."
  type        = string
  default     = "3.9.x"
}
