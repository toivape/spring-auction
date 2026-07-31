variable "project_id" {
  description = "GCP project id (must match bootstrap/ and application/)"
  type        = string
}

variable "region" {
  description = "GCP region (must match bootstrap/ and application/)"
  type        = string
  default     = "europe-north1"
}

variable "github_repo" {
  description = "GitHub repo (org/repo) allowed to impersonate the deploy SA — federation is scoped to its main branch only"
  type        = string
  default     = "toivape/spring-auction"
}

# From bootstrap/, needed here only to scope the deploy SA's storage access to the specific
# state bucket (not all of GCS in the project).
variable "state_bucket_name" {
  description = "State bucket bootstrap/ created (same value used in this stack's backend.hcl)"
  type        = string
}
