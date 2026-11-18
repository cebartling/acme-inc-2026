# The seven apps (apps.tf) as ECS Fargate services in the cluster db-init.tf creates, in the
# private subnets, behind the load balancer (alb.tf).
#
# The backends find each other through ECS Service Connect: each one is reachable from the
# other tasks as http://<name>:<port>, so those calls never go through the load balancer.
# The images are pushed by hand until the deploy workflow exists (README.md).

resource "aws_service_discovery_http_namespace" "demo" {
  name = "acme-demo"
}

resource "aws_cloudwatch_log_group" "apps" {
  for_each = local.apps

  name              = "/acme-demo/${each.key}"
  retention_in_days = 7
}

locals {
  # A Spring Boot task needs room for the JVM and the Service Connect proxy. The frontends
  # are small Node servers. The CPU and memory pairs are ones Fargate accepts.
  task_size = {
    backend  = { cpu = 512, memory = 2048 }
    frontend = { cpu = 256, memory = 512 }
  }

  # Each service's own database in RDS. db-init.tf creates them (service_databases).
  databases = {
    identity      = "acme_identity"
    customer      = "acme_customers"
    notification  = "acme_notifications"
    product       = "acme_products"
    shopping-cart = "acme_carts"
  }

  cors_origins  = "https://${local.hostnames.customer},https://${local.hostnames.admin}"
  jwks_uri      = "http://identity:${local.apps.identity.port}/.well-known/jwks.json"
  otlp_endpoint = "http://adot-collector:4318"

  # Settings every backend takes. The Spring defaults suit everything not listed here.
  # Kafka is plaintext and Redis has no TLS (ADR-0064), which are the services' defaults.
  backend_environment = {
    SPRING_PROFILES_ACTIVE                  = "docker"
    POSTGRES_HOST                           = aws_db_instance.postgres.address
    POSTGRES_PORT                           = tostring(aws_db_instance.postgres.port)
    KAFKA_BOOTSTRAP_SERVERS                 = aws_msk_cluster.demo.bootstrap_brokers
    OTEL_EXPORTER_OTLP_ENDPOINT             = local.otlp_endpoint
    MANAGEMENT_TRACING_SAMPLING_PROBABILITY = "1.0"
    # The collector scrapes /actuator/prometheus (adot.tf), so the services don't push metrics.
    MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED = "false"
  }

  # DocumentDB's certificate is signed by Amazon's RDS CA, which the JVM doesn't trust. The
  # image carries the CA bundle, and these two settings point the Mongo client at it
  # (ADR-0064, decision 4). Every service that takes MONGODB_URI needs them.
  docdb_tls = {
    SPRING_SSL_BUNDLE_PEM_DOCDB_TRUSTSTORE_CERTIFICATE = "file:/app/certs/rds-global-bundle.pem"
    SPRING_MONGODB_SSL_BUNDLE                          = "docdb"
  }

  # What only some backends take.
  backend_environment_extra = {
    identity = merge(local.docdb_tls, {
      ACME_CORS_EXTRA_ORIGINS = local.cors_origins
      FRONTEND_BASE_URL       = "https://${local.hostnames.customer}"
      MONGODB_DB              = "acme_query"
      REDIS_HOST              = aws_elasticache_cluster.demo.cache_nodes[0].address
      REDIS_PORT              = tostring(aws_elasticache_cluster.demo.cache_nodes[0].port)
      SMS_PROVIDER            = var.sms_provider
      TEST_API_ENABLED        = "true"
    })
    customer = merge(local.docdb_tls, {
      ACME_CORS_EXTRA_ORIGINS = local.cors_origins
      ACME_IDENTITY_JWKS_URI  = local.jwks_uri
      MONGODB_DB              = "acme_query"
      # The test profile turns on customer's test API, as in docker-compose.apps.yml.
      SPRING_PROFILES_ACTIVE = "docker,test"
    })
    notification = {
      CUSTOMER_SERVICE_URL = "http://customer:${local.apps.customer.port}"
      EMAIL_FROM_ADDRESS   = var.email_from_address
      # Identity's verification endpoint, which redirects to the customer app.
      VERIFICATION_BASE_URL   = "https://${local.hostnames.api}/api/v1/users/verify"
      PASSWORD_RESET_BASE_URL = "https://${local.hostnames.customer}/reset-password"
      SHOP_URL                = "https://${local.hostnames.customer}/"
      PROFILE_URL             = "https://${local.hostnames.customer}/profile"
      # The customer frontend has no unsubscribe page; its preferences page is the closest.
      UNSUBSCRIBE_URL        = "https://${local.hostnames.customer}/preferences"
      MANAGE_PREFERENCES_URL = "https://${local.hostnames.customer}/preferences"
    }
    product = {
      ACME_CORS_EXTRA_ORIGINS = local.cors_origins
    }
    shopping-cart = merge(local.docdb_tls, {
      ACME_CORS_EXTRA_ORIGINS = local.cors_origins
      ACME_IDENTITY_JWKS_URI  = local.jwks_uri
      MONGODB_DB              = "acme_query"
      PRODUCT_SERVICE_URL     = "http://product:${local.apps.product.port}"
    })
  }

  # Environment variables ECS fills from Secrets Manager, as "<secret ARN>:<JSON key>::".
  backend_secrets = {
    POSTGRES_USER     = "${aws_secretsmanager_secret.postgres.arn}:POSTGRES_USER::"
    POSTGRES_PASSWORD = "${aws_secretsmanager_secret.postgres.arn}:POSTGRES_PASSWORD::"
  }

  backend_secrets_extra = {
    identity = merge(
      {
        IDENTITY_TEST_API_KEY = "${aws_secretsmanager_secret.test_api_key.arn}:IDENTITY_TEST_API_KEY::"
        JWT_SIGNING_KEY       = "${aws_secretsmanager_secret.jwt_signing.arn}:JWT_SIGNING_KEY::"
        MONGODB_URI           = "${aws_secretsmanager_secret.documentdb.arn}:MONGODB_URI::"
      },
      var.sms_provider == "twilio" ? {
        TWILIO_ACCOUNT_SID = "${data.aws_secretsmanager_secret.twilio.arn}:TWILIO_ACCOUNT_SID::"
        TWILIO_AUTH_TOKEN  = "${data.aws_secretsmanager_secret.twilio.arn}:TWILIO_AUTH_TOKEN::"
        TWILIO_FROM_NUMBER = "${data.aws_secretsmanager_secret.twilio.arn}:TWILIO_FROM_NUMBER::"
      } : {},
    )
    customer = {
      ACME_CUSTOMER_SERVICE_TOKEN = "${aws_secretsmanager_secret.service_tokens.arn}:ACME_CUSTOMER_SERVICE_TOKEN::"
      MONGODB_URI                 = "${aws_secretsmanager_secret.documentdb.arn}:MONGODB_URI::"
    }
    notification = {
      ACME_CUSTOMER_SERVICE_TOKEN = "${aws_secretsmanager_secret.service_tokens.arn}:ACME_CUSTOMER_SERVICE_TOKEN::"
      SENDGRID_API_KEY            = "${data.aws_secretsmanager_secret.sendgrid.arn}:SENDGRID_API_KEY::"
    }
    shopping-cart = {
      ACME_ORDER_SERVICE_TOKEN = "${aws_secretsmanager_secret.service_tokens.arn}:ACME_ORDER_SERVICE_TOKEN::"
      MONGODB_URI              = "${aws_secretsmanager_secret.documentdb.arn}:MONGODB_URI::"
    }
  }

  # The frontends take no settings at run time: the customer frontend's URLs are baked in
  # when the image is built (README.md), and the admin frontend has none.
  app_environment = {
    for name, app in local.apps : name => app.is_frontend ? {} : merge(
      local.backend_environment,
      { POSTGRES_DB = local.databases[name], OTEL_SERVICE_NAME = "${name}-service" },
      lookup(local.backend_environment_extra, name, {}),
    )
  }

  app_secrets = {
    for name, app in local.apps : name => app.is_frontend ? {} : merge(
      local.backend_secrets,
      lookup(local.backend_secrets_extra, name, {}),
    )
  }
}

resource "aws_ecs_task_definition" "apps" {
  for_each = local.apps

  family                   = "acme-demo-${each.key}"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = local.task_size[each.value.is_frontend ? "frontend" : "backend"].cpu
  memory                   = local.task_size[each.value.is_frontend ? "frontend" : "backend"].memory
  execution_role_arn       = aws_iam_role.app_execution.arn

  container_definitions = jsonencode([{
    name      = each.key
    image     = "${data.aws_ecr_repository.images[each.value.image].repository_url}:${var.image_tag}"
    essential = true

    # The port's name is what Service Connect advertises (the service below).
    portMappings = [{
      name          = each.key
      containerPort = each.value.port
      protocol      = "tcp"
      appProtocol   = "http"
    }]

    environment = [for name, value in local.app_environment[each.key] : { name = name, value = value }]
    secrets     = [for name, value_from in local.app_secrets[each.key] : { name = name, valueFrom = value_from }]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.apps[each.key].name
        "awslogs-region"        = data.aws_region.current.region
        "awslogs-stream-prefix" = each.key
      }
    }
  }])
}

resource "aws_ecs_service" "apps" {
  for_each = local.apps

  name            = each.key
  cluster         = aws_ecs_cluster.demo.id
  task_definition = aws_ecs_task_definition.apps[each.key].arn
  desired_count   = each.value.desired
  launch_type     = "FARGATE"

  # A Spring Boot service takes a minute or two to pass its health check. Without a grace
  # period the load balancer would fail it and ECS would stop the task first.
  health_check_grace_period_seconds = 120

  network_configuration {
    subnets          = aws_subnet.private[*].id
    security_groups  = [each.value.is_frontend ? aws_security_group.frontends.id : aws_security_group.services.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.apps[each.key].arn
    container_name   = each.key
    container_port   = each.value.port
  }

  # Only the backends are called by name from other tasks.
  dynamic "service_connect_configuration" {
    for_each = each.value.is_frontend ? [] : [1]

    content {
      enabled   = true
      namespace = aws_service_discovery_http_namespace.demo.arn

      service {
        port_name      = each.key
        discovery_name = each.key

        client_alias {
          port     = each.value.port
          dns_name = each.key
        }
      }
    }
  }

  # The databases must exist before a service starts, and a target group must be attached to
  # the load balancer (through its rule) before a service can register with it.
  depends_on = [
    terraform_data.db_init,
    aws_lb_listener_rule.frontends,
    aws_lb_listener_rule.api,
    aws_iam_role_policy_attachment.app_execution,
    aws_iam_role_policy.app_read_secrets,
  ]
}
