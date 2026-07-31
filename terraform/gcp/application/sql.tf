# Cloud SQL user password. GCP has no RDS-style managed-secret equivalent, so we generate it
# and (ticket #31) store it in Secret Manager for the Cloud Run service to read.
resource "random_password" "db" {
  length  = 32
  special = false # keep it URL/JDBC-safe — no escaping needed in the connection string
}

# POC cost/iteration posture (mirrors the AWS RDS instance): db-f1-micro, zonal, no backups,
# deletable. Do not point this at anything that matters — data is unrecoverable on destroy.
resource "google_sql_database_instance" "app" {
  name             = var.db_instance_name
  database_version = "POSTGRES_18"
  region           = var.region

  # The private-services-access peering must be established before the instance can take a
  # private IP from it — without this, creation fails with "network not ready".
  depends_on = [google_service_networking_connection.private_services]

  settings {
    # Shared-core tiers (db-f1-micro) exist only in the ENTERPRISE edition — Postgres 18
    # otherwise defaults new instances to ENTERPRISE_PLUS, which rejects db-f1-micro.
    edition           = "ENTERPRISE"
    tier              = "db-f1-micro"
    availability_type = "ZONAL"
    disk_size         = 10

    backup_configuration {
      enabled = false
    }

    ip_configuration {
      ipv4_enabled    = false
      private_network = google_compute_network.main.id
    }
  }

  deletion_protection = false
}

resource "google_sql_database" "auction" {
  name     = "auction"
  instance = google_sql_database_instance.app.name
}

resource "google_sql_user" "app" {
  name     = "myuser"
  instance = google_sql_database_instance.app.name
  password = random_password.db.result
}
