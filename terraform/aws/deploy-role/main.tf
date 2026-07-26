terraform {
  required_version = ">= 1.9.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }

  # Partial config — bucket/region/dynamodb_table supplied at `terraform init` time via
  # -backend-config (see backend.hcl.example), pointing at what bootstrap/ created.
  backend "s3" {
    key     = "deploy-role/terraform.tfstate"
    encrypt = true
  }
}

provider "aws" {
  region = var.aws_region
}

data "aws_caller_identity" "current" {}

# RDS's manage_master_user_password uses this key by default; needed to scope the KMS
# grant permissions the CI role needs to create that RDS-managed secret.
data "aws_kms_alias" "secretsmanager" {
  name = "alias/aws/secretsmanager"
}
