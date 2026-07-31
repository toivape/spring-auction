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
