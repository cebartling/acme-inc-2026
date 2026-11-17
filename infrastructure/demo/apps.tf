# The seven apps that run on ECS: five backends and two frontends. The load balancer
# (alb.tf), the ECS services (ecs-services.tf) and the metrics scrape (adot.tf) all read this
# one map. The ports and health paths come from each app's application.yml and Containerfile.
locals {
  apps = {
    identity = {
      image       = "acme-identity-service"
      port        = 10300
      health_path = "/actuator/health"
      desired     = 2 # Two tasks prove a token from one is accepted by the other.
      is_frontend = false
    }
    customer = {
      image       = "acme-customer-service"
      port        = 10301
      health_path = "/actuator/health"
      desired     = 1
      is_frontend = false
    }
    notification = {
      image       = "acme-notification-service"
      port        = 10302
      health_path = "/actuator/health"
      desired     = 1
      is_frontend = false
    }
    product = {
      image       = "acme-product-service"
      port        = 10303
      health_path = "/actuator/health"
      desired     = 1
      is_frontend = false
    }
    shopping-cart = {
      image       = "acme-shopping-cart-service"
      port        = 10304
      health_path = "/actuator/health"
      desired     = 1
      is_frontend = false
    }
    customer-frontend = {
      image       = "acme-customer-frontend"
      port        = 3000
      health_path = "/api/health"
      desired     = 1
      is_frontend = true
    }
    admin-frontend = {
      image       = "acme-admin-frontend"
      port        = 3000
      health_path = "/api/health"
      desired     = 1
      is_frontend = true
    }
  }

  backends  = { for name, app in local.apps : name => app if !app.is_frontend }
  frontends = { for name, app in local.apps : name => app if app.is_frontend }
}
