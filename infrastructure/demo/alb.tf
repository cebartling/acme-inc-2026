# One load balancer serves all three hostnames (ADR-0064, decision 6): the two frontends by
# host name, and the five backends by path on the API host. /actuator has no rule, so it
# falls through to the 404 default and is never reachable from the internet.

resource "aws_lb" "demo" {
  name               = "acme-demo"
  load_balancer_type = "application"
  subnets            = aws_subnet.public[*].id
  security_groups    = [aws_security_group.alb.id]
}

resource "aws_lb_target_group" "apps" {
  for_each = local.apps

  name                 = "acme-demo-${each.key}"
  vpc_id               = aws_vpc.demo.id
  target_type          = "ip" # Fargate tasks in awsvpc mode register by IP.
  protocol             = "HTTP"
  port                 = each.value.port
  deregistration_delay = 30 # The default 300 seconds slows every deploy and destroy.

  health_check {
    path    = each.value.health_path
    matcher = "200"
  }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.demo.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "redirect"
    redirect {
      port        = "443"
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }
}

resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.demo.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = data.aws_acm_certificate.demo.arn

  default_action {
    type = "fixed-response"
    fixed_response {
      content_type = "text/plain"
      message_body = "Not found"
      status_code  = "404"
    }
  }
}

resource "aws_lb_listener_rule" "frontends" {
  for_each = {
    "customer-frontend" = { priority = 1, host = local.hostnames.customer }
    "admin-frontend"    = { priority = 2, host = local.hostnames.admin }
  }

  listener_arn = aws_lb_listener.https.arn
  priority     = each.value.priority

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.apps[each.key].arn
  }

  condition {
    host_header {
      values = [each.value.host]
    }
  }
}

# The path prefixes are the routing table in ADR-0064 and PIN-542.
#
# The customer test API (customer_test below, priority 10) must be matched before the identity
# one: identity's rule, /api/v1/test/*, would otherwise take /api/v1/test/customers too. Lower
# priority numbers are evaluated first.
#
# A rule allows 5 match evaluations, and the host condition uses one, so a rule gets at most 4
# paths. Product has five prefixes, so it takes two rules.
locals {
  api_routes = {
    test-identity   = { priority = 20, service = "identity", paths = ["/api/v1/test/*"] }
    identity        = { priority = 30, service = "identity", paths = ["/api/v1/auth*", "/api/v1/users*"] }
    customer        = { priority = 40, service = "customer", paths = ["/api/v1/customers*"] }
    product-catalog = { priority = 50, service = "product", paths = ["/api/v1/products*", "/api/v1/categories*", "/api/v1/inventory*"] }
    product-prices  = { priority = 60, service = "product", paths = ["/api/v1/prices*", "/api/v1/search*"] }
    shopping-cart   = { priority = 70, service = "shopping-cart", paths = ["/api/v1/carts*"] }
    notification    = { priority = 80, service = "notification", paths = ["/api/v1/notifications*"] }
  }
}

resource "aws_lb_listener_rule" "api" {
  for_each = local.api_routes

  listener_arn = aws_lb_listener.https.arn
  priority     = each.value.priority

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.apps[each.value.service].arn
  }

  condition {
    host_header {
      values = [local.hostnames.api]
    }
  }

  condition {
    path_pattern {
      values = each.value.paths
    }
  }
}

# Customer's test API has no authentication of its own, and the demo is on a public URL. So this
# rule matches only a request that carries customer's service token, the one the acceptance
# tests already send to customer. Any other request falls through to identity's test rule,
# which asks for its own key.
resource "aws_lb_listener_rule" "customer_test" {
  listener_arn = aws_lb_listener.https.arn
  priority     = 10

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.apps["customer"].arn
  }

  condition {
    host_header {
      values = [local.hostnames.api]
    }
  }

  condition {
    path_pattern {
      values = ["/api/v1/test/customers*"]
    }
  }

  condition {
    http_header {
      http_header_name = "X-Service-Token"
      values           = [random_password.customer_service_token.result]
    }
  }
}
