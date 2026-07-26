output "state_bucket_name" {
  value       = aws_s3_bucket.state.id
  description = "Feed into deploy-role/ and application/'s backend config (-backend-config or backend.hcl)"
}

output "lock_table_name" {
  value       = aws_dynamodb_table.locks.name
  description = "Feed into deploy-role/ and application/'s backend config"
}

output "aws_region" {
  value = var.aws_region
}
