# Names must start with "spring-auction/" — deploy-role/'s secrets_name_prefix variable
# (default "spring-auction/") scopes that stack's CI policy to this exact prefix.

locals {
  secrets = {
    google-client-secret = var.google_client_secret
    mailjet-api-key      = var.mailjet_api_key
    mailjet-secret-key   = var.mailjet_secret_key
    admin-password       = var.admin_password
    ingestion-api-key    = var.ingestion_api_key
  }
}

resource "aws_secretsmanager_secret" "app" {
  for_each = local.secrets

  name = "spring-auction/${each.key}"
}

resource "aws_secretsmanager_secret_version" "app" {
  for_each = local.secrets

  secret_id     = aws_secretsmanager_secret.app[each.key].id
  secret_string = each.value
}
