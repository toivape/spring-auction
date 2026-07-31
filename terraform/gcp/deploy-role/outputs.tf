output "wif_provider" {
  value       = google_iam_workload_identity_pool_provider.github.name
  description = "Full provider resource name — feed into the deploy workflow's workload_identity_provider input (GCP_WIF_PROVIDER)"
}

output "deploy_sa_email" {
  value       = google_service_account.deploy.email
  description = "Deploy SA email — feed into the deploy workflow's service_account input (GCP_DEPLOY_SA)"
}
