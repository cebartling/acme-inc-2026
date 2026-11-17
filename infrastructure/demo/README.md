# Runbook: AWS demo stack, network and data tier

The per-demo part of the AWS demo environment
([ADR-0064](../../documentation/adrs/0064-aws-demo-deployment.md)). You create it before a demo
and destroy it afterwards. It contains:

- a VPC in two Availability Zones, with public and private subnets and one NAT gateway;
- Amazon RDS for PostgreSQL, with logical replication on for Debezium, holding the five service
  databases: `acme_identity`, `acme_customers`, `acme_notifications`, `acme_products`, `acme_carts`;
- Amazon DocumentDB 8.0, with TLS on;
- Amazon ElastiCache for Redis, one node, with no TLS;
- Amazon MSK Provisioned: two `kafka.t3.small` brokers, topic auto-creation on, plaintext allowed;
- Secrets Manager secrets for the PostgreSQL and DocumentDB credentials;
- security groups: the data stores accept traffic only from one `services` group;
- a small ECS cluster for the one-off task that sets up the databases.

The ECS services, load balancer and DNS come in the next stack. This stack's outputs are what that
stack needs (`terraform output`).

**Cost:** while it runs, roughly $350–500 a month, mostly MSK (ADR-0064, decision 7). Destroy it
after the demo.

Everything is destroyable: no deletion protection, no final snapshots, and secrets are deleted
with no recovery window.

## Prerequisites

1. **Foundation stack applied** ([`../foundation/README.md`](../foundation/README.md)). It owns the
   state bucket this stack uses.
2. **Tools:**
   - Terraform 1.17 (`terraform version`).
   - AWS CLI v2, signed in to the demo account with administrator rights
     ([`../aws-cli-sign-in.md`](../aws-cli-sign-in.md)). Check with `aws sts get-caller-identity`.
   - The CLI pointed at the stack's Region: `export AWS_REGION=us-east-2`.
   - `bash` on the machine that runs `apply`: a `local-exec` step starts the database setup task.

Run the commands below from `infrastructure/demo`. From anywhere in the repository,
`just aws-demo <args>` runs `terraform <args>` in this directory.

## Create

```zsh
terraform init -backend-config="bucket=acme-inc-2026-tfstate-$(aws sts get-caller-identity --query Account --output text)"
terraform apply
```

`apply` takes 20–30 minutes; MSK is the slow part. At the end it runs the database setup task
(`db-init.sh`) in the private subnets, which:

1. checks that RDS, DocumentDB, ElastiCache and MSK accept connections;
2. creates the five service databases in RDS if they don't exist.

If either step fails, `apply` fails. The task's output is in the CloudWatch log group
`/acme-demo/db-init`. Fix the cause and run `terraform apply` again.

If `apply` rejects an engine version, list what the Region accepts and set the matching variable
(`postgres_engine_version`, `documentdb_engine_version`, `kafka_version`) in `terraform.tfvars`:

```zsh
aws rds describe-db-engine-versions --engine postgres --query 'DBEngineVersions[].EngineVersion'
aws docdb describe-db-engine-versions --engine docdb --query 'DBEngineVersions[].EngineVersion'
aws kafka list-kafka-versions --query 'KafkaVersions[].Version'
```

The services need DocumentDB 8.0.2 or later (retryable writes). After `apply`, check which
version the cluster runs:

```zsh
aws docdb describe-db-clusters --db-cluster-identifier acme-demo --query 'DBClusters[0].EngineVersion'
```

## Check

Run the database setup task by hand. It is safe to repeat: it only creates what is missing.

```zsh
aws ecs run-task \
  --cluster "$(terraform output -raw ecs_cluster_name)" \
  --task-definition acme-demo-db-init \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[$(terraform output -json private_subnet_ids | jq -r 'join(",")')],securityGroups=[$(terraform output -raw services_security_group_id)],assignPublicIp=DISABLED}" \
  --query 'tasks[0].taskArn' --output text
```

Read the result in the log group once the task has stopped:

```zsh
aws logs tail /acme-demo/db-init --since 15m
```

You should see `reachable:` for the four data stores, then `database exists:` for each of the five
databases.

## Destroy

```zsh
terraform destroy
```

Check that nothing is left. Both commands should return nothing for this stack:

```zsh
aws resourcegroupstaggingapi get-resources --tag-filters Key=Stack,Values=demo \
  --query 'ResourceTagMappingList[].ResourceARN'
aws secretsmanager list-secrets --filters Key=name,Values=acme-demo/postgres Key=name,Values=acme-demo/documentdb \
  --query 'SecretList[].Name'
```

The secrets are deleted at once, so a new `terraform apply` right after a `destroy` reuses the
same names. ECS keeps the database setup task definition as an inactive revision; it costs
nothing and the next `apply` registers a new one.
