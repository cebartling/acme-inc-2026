# One security group for everything that talks to the data stores: the five backends, the
# ADOT collector (adot.tf) and the database setup task (db-init.tf). Each data store accepts
# its own port from that group and nothing else. The frontends call no data store, so they
# have their own group (below).

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

# The load balancer takes HTTPS from the internet. Port 80 only redirects to it.
resource "aws_security_group" "alb" {
  name        = "acme-demo-alb"
  description = "The demo load balancer: HTTP and HTTPS from the internet."
  vpc_id      = aws_vpc.demo.id
}

resource "aws_vpc_security_group_ingress_rule" "alb_from_internet" {
  for_each = toset(["80", "443"])

  security_group_id = aws_security_group.alb.id
  description       = "Port ${each.value} from the internet"
  ip_protocol       = "tcp"
  from_port         = tonumber(each.value)
  to_port           = tonumber(each.value)
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_vpc_security_group_egress_rule" "alb_to_services" {
  for_each = toset([for app in local.backends : tostring(app.port)])

  security_group_id            = aws_security_group.alb.id
  description                  = "Backend port ${each.value} on the service tasks"
  referenced_security_group_id = aws_security_group.services.id
  ip_protocol                  = "tcp"
  from_port                    = tonumber(each.value)
  to_port                      = tonumber(each.value)
}

# The frontends call no data store, so they get their own group rather than `services`: the
# load balancer in, anything out (the image pull, and the API host if they call it).
resource "aws_security_group" "frontends" {
  name        = "acme-demo-frontends"
  description = "The frontend tasks: traffic from the load balancer only."
  vpc_id      = aws_vpc.demo.id
}

resource "aws_vpc_security_group_egress_rule" "frontends_all" {
  security_group_id = aws_security_group.frontends.id
  description       = "Everything outbound, through the NAT gateway"
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_vpc_security_group_ingress_rule" "frontends_from_alb" {
  for_each = toset([for app in local.frontends : tostring(app.port)])

  security_group_id            = aws_security_group.frontends.id
  description                  = "Frontend port ${each.value} from the load balancer"
  referenced_security_group_id = aws_security_group.alb.id
  ip_protocol                  = "tcp"
  from_port                    = tonumber(each.value)
  to_port                      = tonumber(each.value)
}

resource "aws_vpc_security_group_egress_rule" "alb_to_frontends" {
  for_each = toset([for app in local.frontends : tostring(app.port)])

  security_group_id            = aws_security_group.alb.id
  description                  = "Frontend port ${each.value} on the frontend tasks"
  referenced_security_group_id = aws_security_group.frontends.id
  ip_protocol                  = "tcp"
  from_port                    = tonumber(each.value)
  to_port                      = tonumber(each.value)
}

# The backend tasks accept traffic on their ports from the load balancer, and from each other:
# service-to-service calls (Service Connect) and the metrics scrape (adot.tf).
resource "aws_vpc_security_group_ingress_rule" "services_from_alb" {
  for_each = toset([for app in local.backends : tostring(app.port)])

  security_group_id            = aws_security_group.services.id
  description                  = "Backend port ${each.value} from the load balancer"
  referenced_security_group_id = aws_security_group.alb.id
  ip_protocol                  = "tcp"
  from_port                    = tonumber(each.value)
  to_port                      = tonumber(each.value)
}

resource "aws_vpc_security_group_ingress_rule" "services_from_services" {
  for_each = toset([for app in local.backends : tostring(app.port)])

  security_group_id            = aws_security_group.services.id
  description                  = "Backend port ${each.value} from other service tasks"
  referenced_security_group_id = aws_security_group.services.id
  ip_protocol                  = "tcp"
  from_port                    = tonumber(each.value)
  to_port                      = tonumber(each.value)
}

# The ADOT collector takes OTLP traces over HTTP from the service tasks (adot.tf).
resource "aws_vpc_security_group_ingress_rule" "services_to_collector" {
  security_group_id            = aws_security_group.services.id
  description                  = "OTLP over HTTP from the service tasks"
  referenced_security_group_id = aws_security_group.services.id
  ip_protocol                  = "tcp"
  from_port                    = 4318
  to_port                      = 4318
}
