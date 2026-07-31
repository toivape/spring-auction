# Terraform (GCP)

The GCP mirror of `../aws/` — same three-config, two-operator-model split. See
`../../docs/adr/0004-ci-applies-application-stack.md` for the CI-applies-application reasoning
(identical here).

- **`bootstrap/`** — one-time, applied locally with ADC admin creds, local state (can't depend on
  the GCS backend it creates). Creates the shared state bucket and enables the project APIs. No lock
  table — the GCS backend locks natively (unlike AWS's S3 + DynamoDB).
- **`deploy-role/`** — applied locally with admin creds. Creates the Workload Identity Federation
  pool/provider + `github-actions-deploy` service account that CI impersonates — can't apply itself,
  since CI has no identity until this exists. **Applied manually, never by CI**: CI impersonates this
  identity, so a role/permission change here has no effect until someone re-runs `terraform apply` on
  this stack (the same "merge isn't enough" trap as AWS `deploy-role/`).
- **`application/`** — applied exclusively by CI (the `gcp-deploy` workflow), on every deploy.
  VPC, Cloud SQL, Artifact Registry, Secret Manager, IAM, and the Cloud Run service.

## One-time prerequisites (manual)

Billing must be linked to the project and you must be logged in **twice**:

```bash
gcloud auth login                        # gcloud CLI commands
gcloud auth application-default login    # ADC — what Terraform reads
gcloud config set project <PROJECT_ID>   # e.g. spring-auction
```

## Applying `bootstrap/` by hand

```bash
cd terraform/gcp/bootstrap
terraform init
terraform apply \
  -var="project_id=<PROJECT_ID>" \
  -var="state_bucket_name=spring-auction-tf-state-<PROJECT_ID>"
terraform output   # note state_bucket_name for the next stack's backend config
```

If the first apply fails with an API-not-enabled error (a fresh project may not have
`serviceusage`/`cloudresourcemanager` on yet), enable those two by hand once, then re-apply:

```bash
gcloud services enable serviceusage.googleapis.com cloudresourcemanager.googleapis.com
```

## Applying `deploy-role/` by hand

```bash
cd terraform/gcp/deploy-role
cp backend.hcl.example backend.hcl   # fill in the state bucket from bootstrap/'s output
terraform init -backend-config=backend.hcl
terraform apply \
  -var="project_id=<PROJECT_ID>" \
  -var="state_bucket_name=<state-bucket-name>"
terraform output   # wif_provider + deploy_sa_email — set as repo Actions vars
```

Feed the outputs into the repo's Actions **variables** (not secrets):

```bash
gh variable set GCP_WIF_PROVIDER --body "<wif_provider>"
gh variable set GCP_DEPLOY_SA     --body "<deploy_sa_email>"
```
