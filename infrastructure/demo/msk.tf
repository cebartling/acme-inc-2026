# Kafka. Two of the smallest brokers, one per Availability Zone.

# MSK's default configuration has topic auto-creation off, and no service creates its own
# topics. The replication settings must not exceed the broker count: topics created with
# MSK's default replication factor of 3 would fail on two brokers.
resource "aws_msk_configuration" "demo" {
  name           = "acme-demo"
  kafka_versions = [var.kafka_version]

  server_properties = <<-EOT
    auto.create.topics.enable=true
    default.replication.factor=2
    min.insync.replicas=1
    offsets.topic.replication.factor=2
    transaction.state.log.replication.factor=2
    transaction.state.log.min.isr=1
  EOT
}

resource "aws_msk_cluster" "demo" {
  cluster_name           = "acme-demo"
  kafka_version          = var.kafka_version
  number_of_broker_nodes = length(local.azs)

  broker_node_group_info {
    instance_type   = "kafka.t3.small"
    client_subnets  = aws_subnet.private[*].id
    security_groups = [aws_security_group.data_store["kafka"].id]

    storage_info {
      ebs_storage_info {
        volume_size = 10
      }
    }
  }

  configuration_info {
    arn      = aws_msk_configuration.demo.arn
    revision = aws_msk_configuration.demo.latest_revision
  }

  # No service sets spring.kafka.security.protocol, so clients connect in plaintext.
  # The Terraform default is TLS only.
  encryption_info {
    encryption_in_transit {
      client_broker = "PLAINTEXT"
      in_cluster    = true
    }
  }
}
