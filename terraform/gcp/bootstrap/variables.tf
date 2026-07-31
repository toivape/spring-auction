variable "project_id" {
  description = "GCP project id that owns the state bucket and enabled APIs. No default — must be supplied."
  type        = string
}

variable "region" {
  description = "GCP region for the Terraform state bucket (and default for later stacks)"
  type        = string
  default     = "europe-north1"
}

variable "state_bucket_name" {
  description = "Globally-unique GCS bucket name for Terraform remote state. Operator-supplied — convention is spring-auction-tf-state-<PROJECT_ID>."
  type        = string
}
