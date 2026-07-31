# Docker image registry (analog of the AWS ECR repo). The deploy workflow pushes the app image
# here, then Cloud Run pulls it. CI applies this repo first (targeted apply) so the image can be
# pushed before the full apply — same chicken-and-egg two-phase flow as AWS.
resource "google_artifact_registry_repository" "app" {
  location      = var.region
  repository_id = var.artifact_repository_id
  format        = "DOCKER"

  # Keep only the single most-recently-pushed image (analog of the ECR lifecycle policy). KEEP
  # wins over DELETE when both match, so "keep newest 1" + "delete everything" = keep only newest.
  cleanup_policies {
    id     = "keep-most-recent"
    action = "KEEP"
    most_recent_versions {
      keep_count = 1
    }
  }

  cleanup_policies {
    id     = "delete-all-others"
    action = "DELETE"
    condition {
      older_than = "0s"
    }
  }
}
