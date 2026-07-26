variable "aws_region" {
  description = "AWS region (must match bootstrap/ and application/)"
  type        = string
  default     = "eu-north-1"
}

variable "github_repo" {
  description = "GitHub repo (org/repo) allowed to assume this role — trust is scoped to its main branch only"
  type        = string
  default     = "toivape/spring-auction"
}

# Names below must match what the application/ stack (ticket #19/#20) actually creates —
# this role's policy is scoped to these specific resources ahead of their existing so CI
# never needs a *-on-everything grant for RDS/Secrets Manager/IAM.

variable "ecr_repository_name" {
  description = "ECR repo name application/ecr.tf creates"
  type        = string
  default     = "spring-auction"
}

variable "secrets_name_prefix" {
  description = "Secrets Manager name prefix application/secrets.tf creates entries under"
  type        = string
  default     = "spring-auction/"
}

variable "ecs_task_execution_role_name" {
  description = "IAM role name application/iam.tf creates for ECS task execution"
  type        = string
  default     = "ecsTaskExecutionRole"
}

variable "ecs_infrastructure_role_name" {
  description = "IAM role name application/iam.tf creates for the Express Mode infrastructure role"
  type        = string
  default     = "ecsInfrastructureRoleForExpressServices"
}

# Backend resources from bootstrap/, needed here only to scope this role's S3/DynamoDB
# permissions to the specific backend (not all of S3/DynamoDB in the account).

variable "state_bucket_name" {
  description = "State bucket bootstrap/ created (same value used in its backend.hcl)"
  type        = string
}

variable "lock_table_name" {
  description = "Lock table bootstrap/ created (same value used in its backend.hcl)"
  type        = string
  default     = "spring-auction-tf-locks"
}
