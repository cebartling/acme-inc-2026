variable "domain_name" {
  description = "Domain registered in Route 53 in this account, such as example.com. The frontends and the API get subdomains of it."
  type        = string
}

variable "budget_alert_email" {
  description = "Email address that gets the AWS Budgets alert."
  type        = string
}

variable "budget_limit_usd" {
  description = "Monthly cost, in US dollars, above which the budget alert is sent."
  type        = number
  default     = 50
}
