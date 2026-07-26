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
  description = "Port the primary container listens on. 80 for the walking-skeleton nginx placeholder; swaps to 8080 once the real app image is wired in."
  type        = number
  default     = 80
}

variable "health_check_path" {
  description = "ALB health check path. '/' for the nginx placeholder; swaps to /actuator/health with the real app."
  type        = string
  default     = "/"
}
