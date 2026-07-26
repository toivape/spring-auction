terraform {
  required_version = ">= 1.9.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }

  # Bucket/table are fixed facts about this one deployment (created once by bootstrap/), not
  # environment-specific per-operator like deploy-role/'s — hardcoded here rather than
  # -backend-config, since only CI ever applies this stack and there's no interactive step
  # to fill in a local backend.hcl.
  backend "s3" {
    bucket         = "spring-auction-tf-state-039314425267"
    key            = "application/terraform.tfstate"
    region         = "eu-north-1"
    dynamodb_table = "spring-auction-tf-locks"
    encrypt        = true
  }
}

provider "aws" {
  region = var.aws_region
}

data "aws_caller_identity" "current" {}

data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  azs = slice(data.aws_availability_zones.available.names, 0, 2)
}
