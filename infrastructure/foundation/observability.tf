resource "aws_prometheus_workspace" "demo" {
  alias = "acme-demo"
}

# The role Grafana uses to query Prometheus. It's created here rather than left
# to the service, so Terraform tracks it and destroy removes it.
resource "aws_iam_role" "grafana" {
  name = "acme-demo-grafana"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "grafana.amazonaws.com" }
      Action    = "sts:AssumeRole"
      Condition = {
        StringEquals = { "aws:SourceAccount" = data.aws_caller_identity.current.account_id }
      }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "grafana_prometheus" {
  role       = aws_iam_role.grafana.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonPrometheusQueryAccess"
}

# Grafana's AWS data sources page lists and describes workspaces to find
# them. The query policy above doesn't allow that.
resource "aws_iam_role_policy" "grafana_prometheus_discovery" {
  name = "prometheus-discovery"
  role = aws_iam_role.grafana.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["aps:ListWorkspaces", "aps:DescribeWorkspace"]
      Resource = "*"
    }]
  })
}

# People sign in through IAM Identity Center, which must be set up before the
# first apply. The Prometheus data source and user roles are set by hand
# (README.md), so no Grafana access token lands in Terraform state.
resource "aws_grafana_workspace" "demo" {
  name                     = "acme-demo"
  account_access_type      = "CURRENT_ACCOUNT"
  authentication_providers = ["AWS_SSO"]
  permission_type          = "CUSTOMER_MANAGED"
  role_arn                 = aws_iam_role.grafana.arn
  data_sources             = ["PROMETHEUS"]
}
