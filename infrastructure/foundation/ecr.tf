locals {
  # Named like the Docker Compose images (docker-compose.apps.yml). The order
  # service isn't deployed yet (ADR-0064, Open Decisions).
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

resource "aws_ecr_repository" "images" {
  for_each = local.ecr_repositories

  name = each.value
}
