# Third-party credentials. The values are set by hand (README.md), so they never
# reach Terraform state. The JSON keys match the environment variables the
# services read, so ECS can inject each key by name.

# Read by notification: backend-services/notification/src/main/resources/application.yml
resource "aws_secretsmanager_secret" "sendgrid" {
  name        = "acme-demo/sendgrid"
  description = "SendGrid credentials for notification. JSON key: SENDGRID_API_KEY."
}

# Read by identity for SMS: backend-services/identity/src/main/resources/application.yml
resource "aws_secretsmanager_secret" "twilio" {
  name        = "acme-demo/twilio"
  description = "Twilio credentials for identity's SMS. JSON keys: TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, TWILIO_FROM_NUMBER."
}
