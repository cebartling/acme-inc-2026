# One security group for everything that talks to the data stores: the services (next
# sub-issue) and the database setup task (db-init.tf). Each data store accepts its own
# port from that group and nothing else.

resource "aws_security_group" "services" {
  name        = "acme-demo-services"
  description = "Clients of the demo data stores. Attach it to every task that needs them."
  vpc_id      = aws_vpc.demo.id
}

resource "aws_vpc_security_group_egress_rule" "services_all" {
  security_group_id = aws_security_group.services.id
  description       = "Everything outbound: the data stores, and the internet through the NAT gateway"
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

locals {
  data_store_ports = {
    postgres   = 5432
    documentdb = 27017
    redis      = 6379
    kafka      = 9092
  }
}

resource "aws_security_group" "data_store" {
  for_each = local.data_store_ports

  name        = "acme-demo-${each.key}"
  description = "Accepts ${each.key} traffic from the services security group."
  vpc_id      = aws_vpc.demo.id
}

resource "aws_vpc_security_group_ingress_rule" "from_services" {
  for_each = local.data_store_ports

  security_group_id            = aws_security_group.data_store[each.key].id
  description                  = "${each.key} from the services security group"
  referenced_security_group_id = aws_security_group.services.id
  ip_protocol                  = "tcp"
  from_port                    = each.value
  to_port                      = each.value
}
