output "state_bucket_name" {
  value       = google_storage_bucket.state.name
  description = "Feed into deploy-role/ and application/'s backend config (-backend-config or backend.hcl)"
}

output "region" {
  value = var.region
}

output "project_id" {
  value = var.project_id
}
