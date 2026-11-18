terraform {
  required_version = "~> 1.17"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.73"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.7"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }

  # The state bucket belongs to the foundation stack. Pass its name at init:
  #   terraform init -backend-config=bucket=<state bucket>
  # README.md has the full command.
  backend "s3" {
    key          = "demo/terraform.tfstate"
    region       = "us-east-2"
    use_lockfile = true
  }
}

provider "aws" {
  region = "us-east-2"

  default_tags {
    tags = {
      Project   = "acme-inc-2026"
      Stack     = "demo"
      ManagedBy = "terraform"
    }
  }
}
