terraform {
  required_version = ">= 1.9.0"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 6.0"
    }
  }

  # One-time, throwaway: the state bucket can't be created by a stack that depends on it
  # existing. Not counted as one of the two GCS-backed stacks (deploy-role, application) —
  # those are applied against the backend this config creates. Unlike AWS (S3 + DynamoDB),
  # the GCS backend does its own locking, so there's no separate lock table.
  backend "local" {}
}

provider "google" {
  project = var.project_id
  region  = var.region
}

resource "google_storage_bucket" "state" {
  name     = var.state_bucket_name
  location = var.region

  versioning {
    enabled = true
  }

  uniform_bucket_level_access = true
  force_destroy               = false
}

# One-time API enablement, codified so it's repeatable. disable_on_destroy=false leaves the
# APIs on if this stack is ever destroyed — the project keeps working for the other stacks.
resource "google_project_service" "apis" {
  for_each = toset([
    "run.googleapis.com",
    "sqladmin.googleapis.com",
    "artifactregistry.googleapis.com",
    "secretmanager.googleapis.com",
    "compute.googleapis.com",
    "servicenetworking.googleapis.com",
    "iam.googleapis.com",
    "iamcredentials.googleapis.com",
    "sts.googleapis.com",
    "cloudresourcemanager.googleapis.com",
    "serviceusage.googleapis.com",
  ])

  service            = each.value
  disable_on_destroy = false
}
