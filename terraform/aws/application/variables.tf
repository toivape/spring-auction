variable "aws_region" {
  description = "AWS region (must match bootstrap/ and deploy-role/)"
  type        = string
  default     = "eu-north-1"
}

variable "ecr_repository_name" {
  description = "Must match deploy-role/'s ecr_repository_name — its policy scopes ECR push to this name"
  type        = string
  default     = "spring-auction"
}

variable "service_name" {
  description = "Express Mode service name"
  type        = string
  default     = "spring-auction"
}

variable "container_image" {
  description = "Full ECR image URI:tag to deploy. Always supplied by CI on every apply — no default (there's no meaningful placeholder once the walking-skeleton phase is over)."
  type        = string
}

variable "container_port" {
  description = "Port the primary container listens on"
  type        = number
  default     = 8080
}

variable "health_check_path" {
  description = "ALB health check path — permitAll() in SecurityConfig.adminChain"
  type        = string
  default     = "/actuator/health"
}

variable "rds_identifier" {
  description = "Must match deploy-role/'s rds_identifier — its policy scopes RDS actions to this name"
  type        = string
  default     = "spring-auction"
}

variable "google_client_id" {
  description = "Not sensitive — plain env var per the app's own config convention"
  type        = string
}

variable "app_base_url" {
  description = "Public URL of the deployed Express Mode service, used for OAuth redirect and win-email payment links. Not knowable before first apply — see ticket #21."
  type        = string
}

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
