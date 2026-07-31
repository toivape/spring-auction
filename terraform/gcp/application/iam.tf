# The identity the Cloud Run service runs as (analog of ecsTaskExecutionRole). Ticket #32 wires
# it into the Cloud Run service; here it just gets read access to the runtime secrets.
resource "google_service_account" "cloud_run_runtime" {
  account_id   = "spring-auction-run"
  display_name = "spring-auction Cloud Run runtime"
  description  = "Identity the Cloud Run service runs as; reads the app secrets from Secret Manager."
}

# Per-secret read access (analog of ecsTaskExecutionRole's scoped GetSecretValue) — the runtime
# SA can read exactly these six secrets, nothing else in the project.
resource "google_secret_manager_secret_iam_member" "runtime_access" {
  for_each = google_secret_manager_secret.app

  secret_id = each.value.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.cloud_run_runtime.email}"
}
