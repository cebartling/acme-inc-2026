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

3. **IAM Identity Center:** turned on in `us-east-2`, with a user for you. Grafana signs people
   in through it, and two AWS requirements apply. The Grafana workspace must be in Identity
   Center's primary Region (`us-east-2`). And Identity Center must be an *organization* instance:
   Grafana doesn't work with account instances. The IAM Identity Center console's Settings page
   shows which kind you have. AWS's Grafana documentation also lists AWS Organizations
   permissions for a standalone account, so turning Identity Center on may put the account into
   AWS Organizations.

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
   `us-east-2`, and add the `acme-demo` workspace. Grafana queries it through the workspace's
   `acme-demo-grafana` role, which this stack creates.

## Removing the stack

This stack is meant to stay: its bucket also holds the demo stack's state. `terraform destroy`
refuses to run while `state.tf` has `prevent_destroy`. Even without it, destroy would fail on the
bucket's old state versions and on ECR repositories that still hold images. Deleted secrets also
stay reserved for 30 days, so applying again soon afterwards would fail on the `acme-demo/*`
names. To remove everything:

1. Destroy the demo environment first. Its state lives in this bucket.
2. Move this stack's state to your laptop, because destroy deletes the bucket that holds it. Run
   this in a directory already initialized against the bucket (see "Later changes"), and answer
   `yes` when Terraform asks to copy the state:

   ```zsh
   cat > backend_override.tf <<'EOF'
   terraform {
     backend "local" {}
   }
   EOF
   terraform init -migrate-state
   ```

3. Edit the code so Terraform can delete everything. These edits are only for the teardown; don't
   commit them.
   - `state.tf`: delete the `lifecycle` block, and add `force_destroy = true` to
     `aws_s3_bucket.terraform_state`. Terraform then deletes every object version before the
     bucket.
   - `ecr.tf`: add `force_delete = true` to `aws_ecr_repository.images`. Terraform then deletes
     the images before the repositories.
   - `secrets.tf`: add `recovery_window_in_days = 0` to both secrets. Terraform then deletes them
     at once, rather than keeping them reserved for 30 days.

4. Record those settings, then destroy. The `apply` only changes Terraform's state, not AWS.

   ```zsh
   terraform apply
   terraform destroy
   ```

5. Clean up locally:

   ```zsh
   rm -f backend_override.tf terraform.tfstate terraform.tfstate.backup
   git checkout -- state.tf ecr.tf secrets.tf
   ```

Destroy leaves alone what this stack doesn't manage: the domain and its hosted zone, and the GitHub
deploy role, which has its own removal steps in [`../github-deploy-role.md`](../github-deploy-role.md).
