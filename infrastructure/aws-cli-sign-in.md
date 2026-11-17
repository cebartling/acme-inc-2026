# Runbook: sign in to the AWS CLI with IAM Identity Center

How to get the AWS CLI signed in to the demo account as an administrator, without access keys. Do
this once per machine, before [`github-deploy-role.md`](github-deploy-role.md) and
[`foundation/README.md`](foundation/README.md), which both need `aws sts get-caller-identity` to
work.

The CLI signs in as an IAM Identity Center user. The account's **root user can't do this**: the
sign-in page for the access portal only accepts users that exist in Identity Center. Use root only
for the one-time setup in part 1.

Part 1 is console work, and part 2 needs a browser to approve a sign-in. If a coding agent is
doing the CLI work, the person at the keyboard does those two things. The agent doesn't type
passwords or MFA codes for them.

## Prerequisites

- IAM Identity Center turned on in `us-east-2`, as an organization instance
  ([`foundation/README.md`](foundation/README.md), prerequisite 3).
- AWS CLI v2 (`aws --version`). `aws configure sso` needs an interactive terminal, so an agent's
  shell can't run it. Part 2 writes the same configuration by hand.
- Two values from the console, which aren't committed because this repository is public:
  - the **access portal URL**, from the Identity Center dashboard (`https://d-xxxxxxxxxx.awsapps.com/start`);
  - the **demo account's 12-digit ID**.

## 1. Create the user and give it administrator access

Once per account. Sign in to the AWS console as root or as any administrator, and switch to
US East (Ohio). The menu names below may differ slightly from the console.

1. **Create a user.** In IAM Identity Center, open Users, then Add user. Use the username
   `acme-inc-2026-admin`, and enter a name and email. AWS emails an invitation, or you can set a
   one-time password.
2. **Create the permission set**, unless one with `AdministratorAccess` exists. Under
   Multi-account permissions, open Permission sets and choose Create permission set. Pick the
   predefined `AdministratorAccess` permission set and keep the defaults.
3. **Assign the user to the account.** Assignments are made from the account, not from the
   user's page. Under Multi-account permissions, open AWS accounts and tick the demo account. For an
   organization instance that's the management account. Choose Assign users or groups, pick
   `acme-inc-2026-admin`, then pick `AdministratorAccess` and submit.
4. **Accept the invitation.** Open the email, set a password, and set up MFA if asked.

Check: the AWS accounts page lists `acme-inc-2026-admin` under the account with
`AdministratorAccess`.

## 2. Configure and sign in the CLI

1. Add an `sso-session` to `~/.aws/config`. Replace the URL with your access portal URL.

   ```ini
   [sso-session acme]
   sso_start_url = https://d-xxxxxxxxxx.awsapps.com/start
   sso_region = us-east-2
   sso_registration_scopes = sso:account:access
   ```

2. Sign in with the device-code flow:

   ```zsh
   aws sso login --sso-session acme --use-device-code --no-browser
   ```

   It prints a URL and a code. Open the URL in a browser on any machine, sign in **as
   `acme-inc-2026-admin` (not root)**, and approve the request. The code expires after a few
   minutes, so have the browser ready before you run the command.

   The default flow redirects to `127.0.0.1` on the machine running the CLI, so it only works when
   the browser is on that machine. The device-code flow works from anywhere.

3. Add a profile for the account. Replace the account ID.

   ```ini
   [profile acme-demo]
   sso_session = acme
   sso_account_id = 123456789012
   sso_role_name = AdministratorAccess
   region = us-east-2
   output = json
   ```

4. Check it, and confirm the account ID and the role in the ARN:

   ```zsh
   export AWS_PROFILE=acme-demo
   aws sts get-caller-identity
   ```

   The ARN should contain `assumed-role/AWSReservedSSO_AdministratorAccess_`.

The runbooks assume this profile is set: run `export AWS_PROFILE=acme-demo` in each new shell.
Credentials last for the permission set's session duration (1 hour by default). When they expire,
repeat step 2.

## Troubleshooting

| Symptom | Cause |
| --- | --- |
| `aws configure sso` fails with `Input is not a terminal` | It needs an interactive terminal. Use part 2 instead. |
| `invalid_grant` / `Invalid device code provided` | The code expired, or the sign-in used the wrong identity. Run the login again. |
| Signing in at the portal fails for root | Root can't sign in there. Use `acme-inc-2026-admin`. |
| The portal lists no accounts | The assignment in part 1, step 3 is missing or still provisioning. |
