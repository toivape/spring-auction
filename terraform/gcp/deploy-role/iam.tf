# CI applies the whole application/ stack on every deploy (see ADR 0004), so the deploy SA
# needs create/update rights on Cloud Run, Cloud SQL, Artifact Registry, Secret Manager, the
# VPC, and service accounts. GCP's predefined roles are coarser than AWS's inline per-ARN
# policy — there's no clean way to scope these to individual resources ahead of their existing,
# so we grant the predefined *admin roles at project level (the accepted GCP equivalent of the
# AWS VPC/RDS "service-broad" exceptions).
locals {
  deploy_project_roles = [
    "roles/run.admin",                         # Cloud Run service
    "roles/cloudsql.admin",                    # Cloud SQL instance/db/user
    "roles/artifactregistry.admin",            # AR repo + image push
    "roles/secretmanager.admin",               # secrets + their IAM bindings
    "roles/compute.networkAdmin",              # VPC, subnet, global address
    "roles/servicenetworking.networksAdmin",   # private-services-access peering
    "roles/iam.serviceAccountAdmin",           # create/delete the runtime SA
    "roles/iam.serviceAccountUser",            # actAs the runtime SA (the PassRole analog)
    "roles/serviceusage.serviceUsageConsumer", # consume the enabled APIs
  ]
}

resource "google_project_iam_member" "deploy" {
  for_each = toset(local.deploy_project_roles)

  project = var.project_id
  role    = each.value
  member  = "serviceAccount:${google_service_account.deploy.email}"
}

# Terraform state access — scoped to the one state bucket bootstrap/ created, not all of GCS
# (the analog of the AWS role's per-bucket S3 scoping).
resource "google_storage_bucket_iam_member" "deploy_state" {
  bucket = var.state_bucket_name
  role   = "roles/storage.admin"
  member = "serviceAccount:${google_service_account.deploy.email}"
}
