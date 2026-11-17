# Runbook: GitHub deploy role

GitHub Actions workflows in this repository reach AWS by signing in as one IAM role,
`acme-demo-github-deploy`, through GitHub's OpenID Connect (OIDC) provider. GitHub holds no
long-lived AWS keys ([ADR-0064](../documentation/adrs/0064-aws-demo-deployment.md), decision 8).

You create this role by hand with this runbook. Terraform doesn't manage it, so no workflow can
change or break the role it signs in with.

- **Permissions:** the AWS managed policy `AdministratorAccess`. The demo stack creates IAM roles,
  networking and data stores, so a narrower policy would need updating with every change to it.
- **Who can use it:** only workflows in `cebartling/acme-inc-2026`, matched by its owner and
  repository IDs, from any branch, pull request or environment. Workflows in other repositories
  are refused, including a repository that later takes over this name.
- **Session length:** up to 2 hours. A deploy takes half an hour or more, plus the smoke suite, and
  the 1-hour default could run out partway through.

## Prerequisites

- AWS CLI v2, signed in to the demo account with administrator rights. Check with
  `aws sts get-caller-identity`.
- GitHub CLI (`gh`), signed in with admin access to this repository, so it can set secrets.
- A shell in the repository root.

## 1. Look up the account ID

```zsh
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
echo "$ACCOUNT_ID"
```

## 2. Create GitHub's OIDC provider

An account needs this provider only once. Check whether it exists:

```zsh
aws iam list-open-id-connect-providers --output text | grep token.actions.githubusercontent.com
```

If that prints nothing, create it:

```zsh
aws iam create-open-id-connect-provider \
  --url https://token.actions.githubusercontent.com \
  --client-id-list sts.amazonaws.com
```

There's no `--thumbprint-list`: it's optional, and AWS checks GitHub's certificate itself.

## 3. Write the trust policy

First, switch this repository's OIDC tokens to GitHub's immutable subject format, which adds the
owner and repository IDs to the `sub` claim. Then no other repository that later takes this name
can match the trust policy.

```zsh
gh api -X PUT repos/cebartling/acme-inc-2026/actions/oidc/customization/sub \
  -F use_default=true -F use_immutable_subject=true
gh api repos/cebartling/acme-inc-2026/actions/oidc/customization/sub --jq .sub_claim_prefix
```

The second command should print `repo:cebartling@33961/acme-inc-2026@1126364509`, the prefix the
policy below matches. If it still prints `repo:cebartling/acme-inc-2026`, the switch didn't take
effect.

```zsh
TRUST_POLICY=$(mktemp)
cat > "$TRUST_POLICY" <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::${ACCOUNT_ID}:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com"
        },
        "StringLike": {
          "token.actions.githubusercontent.com:sub": "repo:cebartling@33961/acme-inc-2026@1126364509:*"
        }
      }
    }
  ]
}
EOF
```

- The `aud` condition accepts only tokens that GitHub issued for AWS STS.
- The `sub` condition accepts only tokens from this repository, matched by its owner ID (`33961`)
  and repository ID (`1126364509`).

## 4. Create the role

```zsh
aws iam create-role \
  --role-name acme-demo-github-deploy \
  --description "GitHub Actions deploy role for cebartling/acme-inc-2026" \
  --assume-role-policy-document "file://$TRUST_POLICY" \
  --max-session-duration 7200
```

## 5. Attach AdministratorAccess

```zsh
aws iam attach-role-policy \
  --role-name acme-demo-github-deploy \
  --policy-arn arn:aws:iam::aws:policy/AdministratorAccess
rm "$TRUST_POLICY"
```

## 6. Give the workflows the role ARN

```zsh
gh secret set AWS_DEPLOY_ROLE_ARN --body "arn:aws:iam::${ACCOUNT_ID}:role/acme-demo-github-deploy"
```

It's a secret rather than a variable because this repository is public. GitHub masks secrets in
workflow logs, so the account ID in the ARN stays out of public logs.

## 7. Check it

1. The trust policy and the attached policy:

   ```zsh
   aws iam get-role --role-name acme-demo-github-deploy --query Role.AssumeRolePolicyDocument
   aws iam list-attached-role-policies --role-name acme-demo-github-deploy
   ```

2. Sign in from GitHub Actions. The `AWS demo OIDC check` workflow
   (`.github/workflows/aws-demo-oidc-check.yml`) assumes the role and runs
   `aws sts get-caller-identity`. GitHub can only start it by hand once it's on `main`.

   ```zsh
   gh workflow run aws-demo-oidc-check.yml
   gh run watch
   ```

   The run succeeds, and its last step prints an `assumed-role/acme-demo-github-deploy/...` ARN,
   with the account ID masked.

3. Other repositories can't sign in: the same workflow, run from any other repository with this
   role's ARN, fails with `Not authorized to perform sts:AssumeRoleWithWebIdentity`.

## Changing who can use the role

To narrow the role, change the `sub` condition in step 3, for example:

- `repo:cebartling@33961/acme-inc-2026@1126364509:ref:refs/heads/main`: only workflows running on
  `main`.
- `repo:cebartling@33961/acme-inc-2026@1126364509:environment:<name>`: only jobs that use that
  GitHub environment.

The subject also contains the owner and repository names, so renaming or transferring the
repository changes it. Afterwards, update the trust policy with the prefix that step 3's
`gh api ... --jq .sub_claim_prefix` command prints.

Write the new policy to `$TRUST_POLICY` as in step 3, then:

```zsh
aws iam update-assume-role-policy \
  --role-name acme-demo-github-deploy \
  --policy-document "file://$TRUST_POLICY"
```

## Removing the role

```zsh
aws iam detach-role-policy \
  --role-name acme-demo-github-deploy \
  --policy-arn arn:aws:iam::aws:policy/AdministratorAccess
aws iam delete-role --role-name acme-demo-github-deploy
gh secret delete AWS_DEPLOY_ROLE_ARN
```

Delete the OIDC provider too, unless something else in the account signs in from GitHub:

```zsh
aws iam delete-open-id-connect-provider \
  --open-id-connect-provider-arn "arn:aws:iam::${ACCOUNT_ID}:oidc-provider/token.actions.githubusercontent.com"
```
