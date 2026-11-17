# Runbook: AWS demo foundation stack

The long-lived part of the AWS demo environment
([ADR-0064](../../documentation/adrs/0064-aws-demo-deployment.md)). Destroying the demo
environment leaves it alone. It contains:

- the S3 bucket for Terraform state, for this stack and the demo stack;
- ECR repositories for the 5 backend and 2 frontend images;
- one ACM certificate for `api.`, `shop.` and `admin.<domain>`;
- Secrets Manager secrets for SendGrid and Twilio, whose values you set by hand;
- the Managed Prometheus and Managed Grafana workspaces;
- a monthly cost budget with an email alert.

The GitHub deploy role isn't in this stack. You create it with its own runbook,
[`../github-deploy-role.md`](../github-deploy-role.md), before or after this one.

Changes to this stack run from a laptop. PIN-544 plans a GitHub Actions workflow for it.

## Prerequisites

1. **Tools:**
   - Terraform 1.17 (`terraform version`).
   - AWS CLI v2, signed in to the demo account with administrator rights. Check with
     `aws sts get-caller-identity`.
2. **Domain:** registered through Route 53 in this account. Registering it creates a hosted zone
   with the same name, which this stack uses. Check that the zone exists:

   ```zsh
   aws route53 list-hosted-zones-by-name --dns-name <domain> --query 'HostedZones[0].Name'
   ```

3. **IAM Identity Center:** turned on in `us-east-1`, with a user for you. Grafana signs people
   in through it. AWS's Grafana documentation lists AWS Organizations permissions for a
   standalone account, so turning Identity Center on may put the account into AWS Organizations.

Run the commands below from `infrastructure/foundation`. From anywhere in the repository,
`just foundation <args>` runs `terraform <args>` in this directory.

## First apply

The state bucket doesn't exist yet. The first apply keeps state on your laptop, then moves it
into the bucket it created.

1. Fill in the values. `terraform.tfvars` is gitignored, so your email stays out of the
   repository.

   ```zsh
   cp terraform.tfvars.example terraform.tfvars
   # Edit terraform.tfvars: domain_name and budget_alert_email.
   ```

2. Use a local backend for now. `backend_override.tf` is gitignored.

   ```zsh
   cat > backend_override.tf <<'EOF'
   terraform {
     backend "local" {}
   }
   EOF
   terraform init
   ```

3. Apply. It waits until the ACM certificate is issued, which can take several minutes.

   ```zsh
   terraform apply
   ```

4. Move the state into the bucket. Answer `yes` when Terraform asks to copy the state.

   ```zsh
   BUCKET=$(terraform output -raw state_bucket)
   rm backend_override.tf
   terraform init -migrate-state -backend-config="bucket=$BUCKET"
   ```

5. Check, then delete the local state files:

   ```zsh
   terraform plan                         # No changes.
   aws s3 ls "s3://$BUCKET/foundation/"   # terraform.tfstate
   rm -f terraform.tfstate terraform.tfstate.backup
   ```

6. Give the workflows the bucket name. It's a secret rather than a variable because the name
   contains the account ID and this repository is public.

   ```zsh
   gh secret set TF_STATE_BUCKET --body "$BUCKET"
   ```

## Later changes

The bucket is `acme-inc-2026-tfstate-<account id>`. Run `init` once per checkout:

```zsh
terraform init -backend-config="bucket=acme-inc-2026-tfstate-$(aws sts get-caller-identity --query Account --output text)"
terraform plan
terraform apply
```

## Set the SendGrid and Twilio values

Terraform creates the secrets with no value, so the values never reach Terraform state. Set each
value from a file, so it stays out of your shell history. The JSON keys must match exactly: they
are the environment variable names the services read.

```zsh
SECRET_FILE=$(mktemp)
${EDITOR:-vi} "$SECRET_FILE"   # {"SENDGRID_API_KEY": "..."}
aws secretsmanager put-secret-value --secret-id acme-demo/sendgrid --secret-string "file://$SECRET_FILE"
rm "$SECRET_FILE"
```

```zsh
SECRET_FILE=$(mktemp)
${EDITOR:-vi} "$SECRET_FILE"   # {"TWILIO_ACCOUNT_SID": "...", "TWILIO_AUTH_TOKEN": "...", "TWILIO_FROM_NUMBER": "..."}
aws secretsmanager put-secret-value --secret-id acme-demo/twilio --secret-string "file://$SECRET_FILE"
rm "$SECRET_FILE"
```

Check that each secret now has a current version, without printing the value:

```zsh
aws secretsmanager describe-secret --secret-id acme-demo/sendgrid --query VersionIdsToStages
```

To change a value later, run the same commands again. Each run adds a new version. Whether the
demo uses real Twilio or the mock SMS provider is still open on PIN-533. With the mock, the
Twilio secret can stay empty.

## Set up Grafana

Do this once, after the first apply.

1. In the AWS console, open Amazon Managed Grafana, then the `acme-demo` workspace. Under
   Authentication, assign your IAM Identity Center user and make them an Admin.
2. Open the workspace and sign in. Its address is:

   ```zsh
   echo "https://$(terraform output -raw grafana_workspace_endpoint)"
   ```

3. In Grafana, open the AWS data sources page, choose Amazon Managed Service for Prometheus in
   `us-east-1`, and add the `acme-demo` workspace. Grafana queries it through the workspace's
   `acme-demo-grafana` role, which this stack creates.

## Removing the stack

This stack is meant to stay. `terraform destroy` stops at the state bucket, which has
`prevent_destroy` set. To really remove everything, move the state out of the bucket first, then
delete the `lifecycle` block in `state.tf`.
