terraform {
  required_version = ">= 1.9.0"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 6.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.0"
    }
  }

  # Bucket is a fixed fact about this one deployment (created by bootstrap/), not per-operator
  # like deploy-role/'s — hardcoded here rather than -backend-config, since only CI ever applies
  # this stack and there's no interactive step to fill in a local backend.hcl. GCS locks natively.
  backend "gcs" {
    bucket = "spring-auction-tf-state-spring-auction"
    prefix = "application"
  }
}

provider "google" {
  project = var.project_id
  region  = var.region
}
