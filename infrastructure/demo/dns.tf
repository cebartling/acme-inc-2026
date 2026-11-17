# The three hostnames all point at the one load balancer. The hosted zone and the certificate
# for these names belong to the foundation stack (foundation.tf).
resource "aws_route53_record" "hostnames" {
  for_each = local.hostnames

  zone_id = data.aws_route53_zone.demo.zone_id
  name    = each.value
  type    = "A"

  alias {
    name                   = aws_lb.demo.dns_name
    zone_id                = aws_lb.demo.zone_id
    evaluate_target_health = true
  }
}
