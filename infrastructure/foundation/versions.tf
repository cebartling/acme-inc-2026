terraform {
  required_version = "~> 1.17"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.73"
    }
  }

  # This stack creates the state bucket, so its name is passed at init:
  #   terraform init -backend-config=bucket=<state bucket>
  # README.md covers the first apply, before the bucket exists.
  backend "s3" {
    key          = "foundation/terraform.tfstate"
    region       = "us-east-2"
    use_lockfile = true
  }
}

provider "aws" {
  region = "us-east-2"

  default_tags {
    tags = {
      Project   = "acme-inc-2026"
      Stack     = "foundation"
      ManagedBy = "terraform"
    }
  }
}
