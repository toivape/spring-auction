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
