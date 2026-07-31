# The app's runtime secrets (analog of AWS Secrets Manager). Six total: the five sensitive
# app-config values supplied by CI as TF_VAR_*, plus db-password — the generated Cloud SQL user
# password (GCP has no RDS-style managed secret, so we store it ourselves). Secret Manager has
# no 30-day recovery window to work around (deletes are immediate), so no recovery-window knob.
locals {
  app_secrets = {
    google-client-secret = var.google_client_secret
    mailjet-api-key      = var.mailjet_api_key
    mailjet-secret-key   = var.mailjet_secret_key
    admin-password       = var.admin_password
    ingestion-api-key    = var.ingestion_api_key
    db-password          = random_password.db.result
  }
}

resource "google_secret_manager_secret" "app" {
  for_each  = local.app_secrets
  secret_id = each.key

  replication {
    auto {}
  }
}

resource "google_secret_manager_secret_version" "app" {
  for_each    = local.app_secrets
  secret      = google_secret_manager_secret.app[each.key].id
  secret_data = each.value
}
