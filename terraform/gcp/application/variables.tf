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

variable "service_name" {
  description = "Cloud Run service name"
  type        = string
  default     = "spring-auction"
}

variable "container_image" {
  description = "Full Artifact Registry image URI:tag to deploy. Always supplied by CI on every apply — no default."
  type        = string
}

variable "container_port" {
  description = "Port the container listens on"
  type        = number
  default     = 8080
}

variable "health_check_path" {
  description = "Startup/liveness probe path — permitAll() in SecurityConfig.adminChain"
  type        = string
  default     = "/actuator/health"
}

variable "google_client_id" {
  description = "Not sensitive — plain env var per the app's own config convention"
  type        = string
}

variable "app_base_url" {
  description = "Public URL of the deployed Cloud Run service (OAuth redirect + win-email links). Cloud Run's URL is deterministic (https://<service>-<projectnumber>.<region>.run.app), so this can be set before first deploy."
  type        = string
}

variable "mail_from" {
  description = "Verified Mailjet sender address (app.notification.from-address). Not sensitive. Unset on AWS — set here so notifications actually send."
  type        = string
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
