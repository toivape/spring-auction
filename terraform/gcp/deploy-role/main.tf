terraform {
  required_version = ">= 1.9.0"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 6.0"
    }
  }

  # Partial config — the state bucket is supplied at `terraform init` time via
  # -backend-config (see backend.hcl.example), pointing at what bootstrap/ created.
  # No lock table: the GCS backend locks natively.
  backend "gcs" {
    prefix = "deploy-role"
  }
}

provider "google" {
  project = var.project_id
  region  = var.region
}

# --- Workload Identity Federation: keyless GitHub Actions -> GCP auth (the OIDC analog) ---

resource "google_iam_workload_identity_pool" "github" {
  workload_identity_pool_id = "github-actions"
  display_name              = "GitHub Actions"
  description               = "Federated identity pool for GitHub Actions CI (spring-auction)"
}

resource "google_iam_workload_identity_pool_provider" "github" {
  workload_identity_pool_id          = google_iam_workload_identity_pool.github.workload_identity_pool_id
  workload_identity_pool_provider_id = "github"
  display_name                       = "GitHub Actions OIDC"

  oidc {
    issuer_uri = "https://token.actions.githubusercontent.com"
  }

  # Only the claims we actually key on. attribute.repository backs the principalSet
  # impersonation binding below; google.subject is required.
  attribute_mapping = {
    "google.subject"       = "assertion.sub"
    "attribute.repository" = "assertion.repository"
  }

  # The WIF analog of the AWS trust-policy `sub` scoping: only tokens minted for this
  # repo's main branch can exchange for a GCP token at all. Deploys are main-only
  # (workflow_dispatch, main-guarded), so this matches the AWS role exactly.
  attribute_condition = "assertion.repository == '${var.github_repo}' && assertion.ref == 'refs/heads/main'"
}

# --- The deploy service account CI impersonates (the identity behind the pool) ---

resource "google_service_account" "deploy" {
  account_id   = "github-actions-deploy"
  display_name = "GitHub Actions deploy (spring-auction)"
  description  = "Impersonated by CI via WIF to apply the application/ stack. Applied manually — CI cannot edit its own identity."
}

# Let any workflow run in this repo (that passed the provider's attribute condition)
# impersonate the deploy SA. principalSet scopes to attribute.repository.
resource "google_service_account_iam_member" "deploy_wif" {
  service_account_id = google_service_account.deploy.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/${google_iam_workload_identity_pool.github.name}/attribute.repository/${var.github_repo}"
}
