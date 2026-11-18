# The AWS Distro for OpenTelemetry collector (ADR-0064, decision 9). It does two jobs:
#   - metrics: scrapes each backend's /actuator/prometheus and writes it to the foundation
#     stack's Managed Prometheus workspace, where Managed Grafana reads it;
#   - traces: takes OTLP from the services (OTEL_EXPORTER_OTLP_ENDPOINT) and sends it to X-Ray.
# Service Connect gives it the same names the services call each other by, so it scrapes
# http://identity:10300 and the services send traces to http://adot-collector:4318.
#
# A scrape goes through Service Connect, which picks one task. Identity runs two, so its
# metrics come from either, and counters from the two tasks are interleaved. That's fine for
# a demo dashboard.

locals {
  # The job names are the ones docker/prometheus/config/prometheus.yml uses, so the PromQL
  # and the Grafana dashboards work unchanged (ADR-0012).
  collector_config = {
    extensions = {
      sigv4auth = { region = data.aws_region.current.region, service = "aps" }
    }

    receivers = {
      otlp = { protocols = { http = { endpoint = "0.0.0.0:4318" } } }
      prometheus = {
        config = {
          scrape_configs = [for name, app in local.backends : {
            job_name        = "${name}-service"
            metrics_path    = "/actuator/prometheus"
            scrape_interval = "30s"
            static_configs  = [{ targets = ["${name}:${app.port}"] }]
          }]
        }
      }
    }

    processors = { batch = {} }

    exporters = {
      prometheusremotewrite = {
        endpoint = "${trimsuffix(data.aws_prometheus_workspace.demo.prometheus_endpoint, "/")}/api/v1/remote_write"
        auth     = { authenticator = "sigv4auth" }
      }
      awsxray = { region = data.aws_region.current.region }
    }

    service = {
      extensions = ["sigv4auth"]
      pipelines = {
        metrics = { receivers = ["prometheus"], processors = ["batch"], exporters = ["prometheusremotewrite"] }
        traces  = { receivers = ["otlp"], processors = ["batch"], exporters = ["awsxray"] }
      }
    }
  }
}

resource "aws_cloudwatch_log_group" "adot" {
  name              = "/acme-demo/adot-collector"
  retention_in_days = 7
}

# The collector's own AWS access: write to the Prometheus workspace and X-Ray.
resource "aws_iam_role" "adot_task" {
  name               = "acme-demo-adot-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume_role.json
}

data "aws_iam_policy_document" "adot_task" {
  statement {
    actions   = ["aps:RemoteWrite"]
    resources = [data.aws_prometheus_workspace.demo.arn]
  }

  # X-Ray's write and sampling calls don't take a resource.
  statement {
    actions = [
      "xray:PutTraceSegments",
      "xray:PutTelemetryRecords",
      "xray:GetSamplingRules",
      "xray:GetSamplingTargets",
      "xray:GetSamplingStatisticSummaries",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "adot_task" {
  name   = "write-metrics-and-traces"
  role   = aws_iam_role.adot_task.id
  policy = data.aws_iam_policy_document.adot_task.json
}

resource "aws_ecs_task_definition" "adot" {
  family                   = "acme-demo-adot-collector"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 256
  memory                   = 512
  execution_role_arn       = aws_iam_role.app_execution.arn
  task_role_arn            = aws_iam_role.adot_task.arn

  container_definitions = jsonencode([{
    name      = "adot-collector"
    image     = "public.ecr.aws/aws-observability/aws-otel-collector:v0.50.0"
    essential = true

    portMappings = [{
      name          = "otlp-http"
      containerPort = 4318
      protocol      = "tcp"
      appProtocol   = "http"
    }]

    # The collector reads its configuration from this variable.
    environment = [{ name = "AOT_CONFIG_CONTENT", value = yamlencode(local.collector_config) }]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.adot.name
        "awslogs-region"        = data.aws_region.current.region
        "awslogs-stream-prefix" = "adot-collector"
      }
    }
  }])
}

resource "aws_ecs_service" "adot" {
  name            = "adot-collector"
  cluster         = aws_ecs_cluster.demo.id
  task_definition = aws_ecs_task_definition.adot.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.private[*].id
    security_groups  = [aws_security_group.services.id]
    assign_public_ip = false
  }

  # It advertises the OTLP port for the services. To resolve the backends it scrapes, it has to
  # start after they exist (adot_redeploy below).
  service_connect_configuration {
    enabled   = true
    namespace = aws_service_discovery_http_namespace.demo.arn

    service {
      port_name      = "otlp-http"
      discovery_name = "adot-collector"

      client_alias {
        port     = 4318
        dns_name = "adot-collector"
      }
    }
  }

  depends_on = [
    aws_iam_role_policy_attachment.app_execution,
    aws_iam_role_policy.adot_task,
  ]
}

# A Service Connect task resolves only the endpoints that existed when it started. The collector
# starts long before the backends, which wait for MSK and the database setup, so its first
# tasks can't resolve them and every scrape fails. Once the backend services exist, start the
# collector again so its new tasks can. This runs on the first apply and when the services are
# replaced, not on every apply.
resource "terraform_data" "adot_redeploy" {
  triggers_replace = [for service in aws_ecs_service.apps : service.id]

  provisioner "local-exec" {
    interpreter = ["/bin/bash", "-c"]
    command     = "aws ecs update-service --cluster \"$CLUSTER\" --service \"$SERVICE\" --force-new-deployment > /dev/null"

    environment = {
      AWS_REGION = data.aws_region.current.region
      CLUSTER    = aws_ecs_cluster.demo.name
      SERVICE    = aws_ecs_service.adot.name
    }
  }
}
