# The Cloud Run service — the point-at-image analog of ECS Express Gateway. Reaches the private
# Cloud SQL over Direct VPC egress (ADR 0005), reads secrets from Secret Manager, public ingress.
resource "google_cloud_run_v2_service" "app" {
  name     = var.service_name
  location = var.region

  deletion_protection = false
  ingress             = "INGRESS_TRAFFIC_ALL" # public, like the internet-facing ALB

  template {
    service_account = google_service_account.cloud_run_runtime.email

    # ADR 0003: exactly one instance, in-memory sessions. Do not raise max without first adding a
    # shared session store. min=1 mirrors AWS always-on (no cold starts).
    scaling {
      min_instance_count = 1
      max_instance_count = 1
    }

    # Direct VPC egress (GA) onto our VPC, so the app reaches the Cloud SQL private IP over plain
    # TCP — no Auth Proxy, no pom.xml/JDBC change. PRIVATE_RANGES_ONLY keeps public egress direct.
    vpc_access {
      network_interfaces {
        network    = google_compute_network.main.id
        subnetwork = google_compute_subnetwork.main.id
      }
      egress = "PRIVATE_RANGES_ONLY"
    }

    containers {
      image = var.container_image

      ports {
        container_port = var.container_port
      }

      resources {
        # CPU always allocated so the single warm instance stays fully alive. cpu_idle=false
        # requires CPU >= 1 vCPU — can't use AWS's fractional 0.25.
        cpu_idle = false
        limits = {
          cpu    = "1"
          memory = "512Mi"
        }
      }

      # Plain (non-secret) env.
      env {
        name  = "DB_HOST"
        value = google_sql_database_instance.app.private_ip_address
      }
      env {
        name  = "DB_PORT"
        value = "5432"
      }
      env {
        name  = "DB_NAME"
        value = google_sql_database.auction.name
      }
      env {
        name  = "DB_USERNAME"
        value = google_sql_user.app.name
      }
      env {
        name  = "NOTIFICATION_TRANSPORT"
        value = "mailjet"
      }
      # MAIL_FROM: a verified Mailjet sender. Unset on AWS (falls back to an unverified address,
      # so email silently fails) — set here so notifications actually send. See GCP-DEPLOYMENT-PLAN.
      env {
        name  = "MAIL_FROM"
        value = var.mail_from
      }
      env {
        name  = "GOOGLE_CLIENT_ID"
        value = var.google_client_id
      }
      env {
        name  = "APP_BASE_URL"
        value = var.app_base_url
      }

      # Secret env, pulled from Secret Manager at instance start (runtime SA has accessor — iam.tf).
      env {
        name = "DB_PASSWORD"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.app["db-password"].secret_id
            version = "latest"
          }
        }
      }
      env {
        name = "GOOGLE_CLIENT_SECRET"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.app["google-client-secret"].secret_id
            version = "latest"
          }
        }
      }
      env {
        name = "MAILJET_API_KEY"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.app["mailjet-api-key"].secret_id
            version = "latest"
          }
        }
      }
      env {
        name = "MAILJET_SECRET_KEY"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.app["mailjet-secret-key"].secret_id
            version = "latest"
          }
        }
      }
      env {
        name = "ADMIN_PASSWORD"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.app["admin-password"].secret_id
            version = "latest"
          }
        }
      }
      env {
        name = "INGESTION_API_KEY"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.app["ingestion-api-key"].secret_id
            version = "latest"
          }
        }
      }

      # Generous startup budget (~240s): slow JVM + Flyway boot on db-f1-micro mustn't be killed
      # before it's listening. failure_threshold * period_seconds = the total grace window.
      startup_probe {
        http_get {
          path = var.health_check_path
          port = var.container_port
        }
        period_seconds    = 10
        timeout_seconds   = 5
        failure_threshold = 24
      }

      liveness_probe {
        http_get {
          path = var.health_check_path
          port = var.container_port
        }
        period_seconds    = 30
        timeout_seconds   = 5
        failure_threshold = 3
      }
    }
  }

  # Secret access and the private-services peering must exist before the service starts, or the
  # first revision fails to pull secrets / reach the DB.
  depends_on = [
    google_secret_manager_secret_iam_member.runtime_access,
    google_service_networking_connection.private_services,
  ]
}

# Public, unauthenticated access (matches the internet-facing ALB) — allUsers may invoke.
resource "google_cloud_run_v2_service_iam_member" "public" {
  name     = google_cloud_run_v2_service.app.name
  location = google_cloud_run_v2_service.app.location
  role     = "roles/run.invoker"
  member   = "allUsers"
}
