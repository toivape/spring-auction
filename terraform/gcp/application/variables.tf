variable "project_id" {
  description = "GCP project id (must match bootstrap/ and deploy-role/). Single-deployment fact; CI overrides via TF_VAR_project_id."
  type        = string
  default     = "spring-auction"
}

variable "region" {
  description = "GCP region (must match bootstrap/ and deploy-role/)"
  type        = string
  default     = "europe-north1"
}

variable "db_instance_name" {
  description = "Cloud SQL instance name"
  type        = string
  default     = "spring-auction"
}

variable "artifact_repository_id" {
  description = "Artifact Registry repository id (Docker). Must match the image URI the deploy workflow pushes."
  type        = string
  default     = "spring-auction"
}

# Sensitive app-config values — always supplied by CI as TF_VAR_*, never committed. Stored in
# Secret Manager (secrets.tf) and injected into Cloud Run as secret env vars (ticket #32).

variable "google_client_secret" {
  type      = string
  sensitive = true
}

variable "mailjet_api_key" {
  type      = string
  sensitive = true
}

variable "mailjet_secret_key" {
  type      = string
  sensitive = true
}

variable "admin_password" {
  type      = string
  sensitive = true
}

variable "ingestion_api_key" {
  type      = string
  sensitive = true
}
