# Things the foundation stack owns, looked up by name or domain rather than read from its
# state, so this stack needs no access to the foundation's state file. The names and the
# hostnames must match infrastructure/foundation/.

locals {
  hostnames = {
    api      = "api.${var.domain_name}"
    customer = "www.${var.domain_name}"
    admin    = "admin.${var.domain_name}"
  }

  # Foundation's ecr.tf. The order service isn't deployed (ADR-0064, Open Decisions).
  ecr_repositories = toset([
    "acme-identity-service",
    "acme-customer-service",
    "acme-notification-service",
    "acme-product-service",
    "acme-shopping-cart-service",
    "acme-customer-frontend",
    "acme-admin-frontend",
  ])
}

data "aws_route53_zone" "demo" {
  name         = var.domain_name
  private_zone = false
}

# One certificate covers all three hostnames (foundation's dns.tf).
data "aws_acm_certificate" "demo" {
  domain      = local.hostnames.api
  statuses    = ["ISSUED"]
  most_recent = true
}

data "aws_ecr_repository" "images" {
  for_each = local.ecr_repositories

  name = each.value
}

# Foundation's observability.tf creates one workspace with the alias "acme-demo".
data "aws_prometheus_workspaces" "demo" {
  alias_prefix = "acme-demo"
}

data "aws_prometheus_workspace" "demo" {
  workspace_id = one(data.aws_prometheus_workspaces.demo.workspace_ids)
}

data "aws_secretsmanager_secret" "sendgrid" {
  name = "acme-demo/sendgrid"
}

data "aws_secretsmanager_secret" "twilio" {
  name = "acme-demo/twilio"
}
