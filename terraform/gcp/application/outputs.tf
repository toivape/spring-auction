output "db_private_ip" {
  value       = google_sql_database_instance.app.private_ip_address
  description = "Cloud SQL private IP — the DB_HOST the Cloud Run service connects to (ticket #32)"
}

output "db_instance_name" {
  value = google_sql_database_instance.app.name
}

output "vpc_network" {
  value       = google_compute_network.main.id
  description = "Feeds Cloud Run's Direct VPC egress config (ticket #32)"
}

output "vpc_subnetwork" {
  value       = google_compute_subnetwork.main.id
  description = "Feeds Cloud Run's Direct VPC egress config (ticket #32)"
}

output "service_url" {
  value       = google_cloud_run_v2_service.app.uri
  description = "Public URL of the Cloud Run service"
}

output "artifact_registry_repo" {
  value       = "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.app.repository_id}"
  description = "Artifact Registry repo path the deploy workflow pushes images to"
}
