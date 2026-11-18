# The role ECS uses to start every service task: pull the image, write the logs, and read the
# secrets the task definitions inject as environment variables. The services themselves
# call no AWS APIs, so they have no task role.
resource "aws_iam_role" "app_execution" {
  name               = "acme-demo-app-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume_role.json
}

resource "aws_iam_role_policy_attachment" "app_execution" {
  role       = aws_iam_role.app_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "app_read_secrets" {
  statement {
    actions = ["secretsmanager:GetSecretValue"]
    resources = concat(
      [
        aws_secretsmanager_secret.postgres.arn,
        aws_secretsmanager_secret.documentdb.arn,
        aws_secretsmanager_secret.jwt_signing.arn,
        aws_secretsmanager_secret.test_api_key.arn,
        aws_secretsmanager_secret.service_tokens.arn,
        data.aws_secretsmanager_secret.sendgrid.arn,
      ],
      var.sms_provider == "twilio" ? [data.aws_secretsmanager_secret.twilio.arn] : [],
    )
  }
}

resource "aws_iam_role_policy" "app_read_secrets" {
  name   = "read-secrets"
  role   = aws_iam_role.app_execution.id
  policy = data.aws_iam_policy_document.app_read_secrets.json
}
