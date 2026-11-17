# RDS and the other data stores sit in private subnets, so Terraform on a laptop can't connect
# to them. A one-off Fargate task inside the VPC does it instead (db-init.sh): it checks that
# all four data stores are reachable, then creates the five service databases. If either step
# fails, the apply fails.
#
# This cluster is only for that task. The services stack (next sub-issue) can reuse it or
# create its own.

locals {
  service_databases = ["acme_identity", "acme_customers", "acme_notifications", "acme_products", "acme_carts"]
}

resource "aws_ecs_cluster" "demo" {
  name = "acme-demo"
}

resource "aws_cloudwatch_log_group" "db_init" {
  name              = "/acme-demo/db-init"
  retention_in_days = 7
}

data "aws_iam_policy_document" "ecs_tasks_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

# The execution role pulls the image, writes logs, and reads the secret that becomes PGPASSWORD.
resource "aws_iam_role" "db_init_execution" {
  name               = "acme-demo-db-init-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume_role.json
}

resource "aws_iam_role_policy_attachment" "db_init_execution" {
  role       = aws_iam_role.db_init_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "db_init_read_postgres_secret" {
  statement {
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [aws_secretsmanager_secret.postgres.arn]
  }
}

resource "aws_iam_role_policy" "db_init_read_postgres_secret" {
  name   = "read-postgres-secret"
  role   = aws_iam_role.db_init_execution.id
  policy = data.aws_iam_policy_document.db_init_read_postgres_secret.json
}

resource "aws_ecs_task_definition" "db_init" {
  family                   = "acme-demo-db-init"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 256
  memory                   = 512
  execution_role_arn       = aws_iam_role.db_init_execution.arn

  container_definitions = jsonencode([{
    name  = "db-init"
    image = "public.ecr.aws/docker/library/postgres:16"

    # The script is passed as an argument, so the task needs nothing but this image.
    entryPoint = ["bash", "-c", file("${path.module}/db-init.sh")]

    environment = [
      { name = "POSTGRES_HOST", value = aws_db_instance.postgres.address },
      { name = "POSTGRES_PORT", value = tostring(aws_db_instance.postgres.port) },
      { name = "POSTGRES_USER", value = local.postgres_user },
      { name = "SERVICE_DATABASES", value = join(" ", local.service_databases) },
      { name = "DOCDB_HOST", value = aws_docdb_cluster.demo.endpoint },
      { name = "DOCDB_PORT", value = tostring(aws_docdb_cluster.demo.port) },
      { name = "REDIS_HOST", value = aws_elasticache_cluster.demo.cache_nodes[0].address },
      { name = "REDIS_PORT", value = tostring(aws_elasticache_cluster.demo.cache_nodes[0].port) },
      { name = "KAFKA_BROKERS", value = aws_msk_cluster.demo.bootstrap_brokers },
    ]

    secrets = [
      { name = "PGPASSWORD", valueFrom = "${aws_secretsmanager_secret.postgres.arn}:POSTGRES_PASSWORD::" },
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.db_init.name
        "awslogs-region"        = data.aws_region.current.region
        "awslogs-stream-prefix" = "db-init"
      }
    }
  }])

  depends_on = [aws_secretsmanager_secret_version.postgres]
}

data "aws_region" "current" {}

# Runs the task and fails the apply unless it exits 0. A new task definition revision or a
# new RDS instance runs it again; README.md has the command to run it by hand.
resource "terraform_data" "db_init" {
  triggers_replace = [aws_db_instance.postgres.id, aws_ecs_task_definition.db_init.arn]

  provisioner "local-exec" {
    interpreter = ["/bin/bash", "-c"]
    command     = <<-EOT
      set -euo pipefail
      run_result=$(aws ecs run-task \
        --cluster "$CLUSTER" \
        --task-definition "$TASK_DEFINITION" \
        --launch-type FARGATE \
        --network-configuration "awsvpcConfiguration={subnets=[$SUBNETS],securityGroups=[$SECURITY_GROUP],assignPublicIp=DISABLED}" \
        --query '[tasks[0].taskArn, failures[0].reason]' --output text)
      read -r task_arn failure_reason <<< "$run_result"
      if [ "$task_arn" = "None" ]; then
        echo "ecs run-task did not start a task: $failure_reason" >&2
        exit 1
      fi
      echo "waiting for $task_arn"
      # A task that never stops would keep its network interface and block the destroy.
      if ! aws ecs wait tasks-stopped --cluster "$CLUSTER" --tasks "$task_arn"; then
        aws ecs stop-task --cluster "$CLUSTER" --task "$task_arn" --reason "db-init did not stop in time" > /dev/null
        echo "db-init did not stop in time; see log group $LOG_GROUP" >&2
        exit 1
      fi
      exit_code=$(aws ecs describe-tasks --cluster "$CLUSTER" --tasks "$task_arn" \
        --query 'tasks[0].containers[0].exitCode' --output text)
      if [ "$exit_code" != "0" ]; then
        echo "db-init failed (exit code $exit_code); see log group $LOG_GROUP" >&2
        # With no exit code the container never started, and the log group is empty.
        aws ecs describe-tasks --cluster "$CLUSTER" --tasks "$task_arn" \
          --query 'tasks[0].[stoppedReason, containers[0].reason]' --output text >&2
        exit 1
      fi
    EOT

    environment = {
      AWS_REGION      = data.aws_region.current.region
      CLUSTER         = aws_ecs_cluster.demo.name
      TASK_DEFINITION = aws_ecs_task_definition.db_init.arn
      SUBNETS         = join(",", aws_subnet.private[*].id)
      SECURITY_GROUP  = aws_security_group.services.id
      LOG_GROUP       = aws_cloudwatch_log_group.db_init.name
    }
  }

  # The task reaches every data store, pulls its image through the NAT gateway, and needs the
  # security group rules in place.
  depends_on = [
    aws_docdb_cluster_instance.demo,
    aws_route_table_association.private,
    aws_vpc_security_group_ingress_rule.from_services,
    aws_vpc_security_group_egress_rule.services_all,
  ]
}
