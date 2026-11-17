output "state_bucket" {
  description = "S3 bucket for Terraform state. Pass it to terraform init -backend-config=bucket=<name>."
  value       = aws_s3_bucket.terraform_state.id
}

output "ecr_repository_urls" {
  description = "ECR repository URL for each image, by image name."
  value       = { for name, repository in aws_ecr_repository.images : name => repository.repository_url }
}

output "hosted_zone_id" {
  description = "Route 53 hosted zone for the demo domain."
  value       = data.aws_route53_zone.demo.zone_id
}

output "hostnames" {
  description = "Hostnames for the API and the two frontends."
  value       = local.hostnames
}

output "certificate_arn" {
  description = "Issued ACM certificate covering every hostname."
  value       = aws_acm_certificate_validation.demo.certificate_arn
}

output "prometheus_workspace_id" {
  description = "Amazon Managed Service for Prometheus workspace ID."
  value       = aws_prometheus_workspace.demo.id
}

output "prometheus_endpoint" {
  description = "Prometheus workspace endpoint, as AWS returns it. Remote write and queries go to paths under it."
  value       = aws_prometheus_workspace.demo.prometheus_endpoint
}

output "grafana_workspace_endpoint" {
  description = "Amazon Managed Grafana workspace endpoint, as AWS returns it."
  value       = aws_grafana_workspace.demo.endpoint
}

output "sendgrid_secret_arn" {
  description = "Secrets Manager secret holding SENDGRID_API_KEY."
  value       = aws_secretsmanager_secret.sendgrid.arn
}

output "twilio_secret_arn" {
  description = "Secrets Manager secret holding TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN and TWILIO_FROM_NUMBER."
  value       = aws_secretsmanager_secret.twilio.arn
}
