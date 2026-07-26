variable "aws_region" {
  description = "AWS region for the Terraform state backend"
  type        = string
  default     = "eu-north-1"
}

variable "state_bucket_name" {
  description = "Globally-unique S3 bucket name for Terraform remote state. Operator-supplied — must be unique across all of AWS, so no default."
  type        = string
}

variable "lock_table_name" {
  description = "DynamoDB table name for Terraform state locking"
  type        = string
  default     = "spring-auction-tf-locks"
}
