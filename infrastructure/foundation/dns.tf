locals {
  # All five backends share the API host, so identity's host-only sign-in
  # cookie reaches every service (ADR-0064, decision 6).
  hostnames = {
    api      = "api.${var.domain_name}"
    customer = "shop.${var.domain_name}"
    admin    = "admin.${var.domain_name}"
  }
}

# Registering the domain in Route 53 creates this zone, so it's looked up
# rather than created a second time.
data "aws_route53_zone" "demo" {
  name         = var.domain_name
  private_zone = false
}

# One certificate covers all three hostnames, because one load balancer
# serves them.
resource "aws_acm_certificate" "demo" {
  domain_name               = local.hostnames.api
  subject_alternative_names = [local.hostnames.customer, local.hostnames.admin]
  validation_method         = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_route53_record" "certificate_validation" {
  for_each = {
    for option in aws_acm_certificate.demo.domain_validation_options : option.domain_name => {
      name   = option.resource_record_name
      type   = option.resource_record_type
      record = option.resource_record_value
    }
  }

  zone_id         = data.aws_route53_zone.demo.zone_id
  name            = each.value.name
  type            = each.value.type
  records         = [each.value.record]
  ttl             = 300
  allow_overwrite = true
}

# Apply waits here until the certificate is issued.
resource "aws_acm_certificate_validation" "demo" {
  certificate_arn         = aws_acm_certificate.demo.arn
  validation_record_fqdns = [for record in aws_route53_record.certificate_validation : record.fqdn]
}
