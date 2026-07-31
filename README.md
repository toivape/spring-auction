# spring-auction

[![CI](https://github.com/toivape/spring-auction/actions/workflows/ci.yml/badge.svg)](https://github.com/toivape/spring-auction/actions/workflows/ci.yml)
![Coverage](.github/badges/jacoco.svg)

A Spring Boot web app for running internal asset auctions: staff list assets for auction, bidders place and can withdraw bids before the auction closes.

## Prerequisites

- Java 26 (e.g. via [sdkman](https://sdkman.io/): `sdk install java 26-tem`)
- Maven (a wrapper is included — use `./mvnw`, no local Maven install required)
- Docker Desktop, for the local Postgres database (`compose.yaml`)

## Running the project

1. Start Docker Desktop if it isn't already running.
2. Run the app from the repo root:
   ```
   ./mvnw spring-boot:run
   ```
   Spring Boot's Docker Compose support automatically starts the `postgres` and `mailpit` containers defined in `compose.yaml` and runs Flyway migrations on startup.
3. The app is available at http://localhost:8080.
4. Outgoing email (win/lose notifications sent when an auction is finalized) is captured by [mailpit](https://mailpit.axllent.org/) rather than actually delivered — open the web inbox at http://localhost:8025 to view it.
5. A public health check is available at http://localhost:8080/actuator/health — it returns `{"status":"UP"}` and reports `DOWN` if the database is unreachable.

### Default accounts

- **Admin console** (`/admin/**`): form login at `/admin/login`, seeded on every startup from `ADMIN_EMAIL`/`ADMIN_PASSWORD` (defaults: `admin@example.com` / `dev-admin-password`).
- **Bidder-facing pages**: any Google account, once OAuth is configured (see below). Without it configured, these pages are unauthenticated in dev.

### Useful environment variables

| Variable | Default | Purpose |
|---|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | `localhost`, `5432`, `auction`, `myuser`, `secret` | Postgres connection |
| `INGESTION_API_KEY` | `dev-ingestion-key` | `X-API-Key` required by the `/api/ingest/**` endpoints |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | `admin@example.com`, `dev-admin-password` | Admin console login |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | *(unset)* | Google OAuth login for bidders — see below |
| `MAIL_HOST`, `MAIL_PORT` | `localhost`, `1025` | SMTP endpoint for outgoing email (mailpit in dev); used by the `smtp` transport |
| `MAIL_FROM` | `auctions@spring-auction.local` | From address on notification emails |
| `APP_BASE_URL` | `http://localhost:8080` | Base URL used to build links in notification emails |
| `NOTIFICATION_TRANSPORT` | `smtp` | Email transport: `smtp` (Mailpit / SMTP relay) or `mailjet` (Mailjet HTTP API) |
| `MAILJET_API_KEY`, `MAILJET_SECRET_KEY` | *(unset)* | Mailjet credentials, required when `NOTIFICATION_TRANSPORT=mailjet` |

Set these in a `.env` file at the repo root (already gitignored) or export them in your shell before running.

## Sending email

Notification emails go through a pluggable transport, selected by `NOTIFICATION_TRANSPORT`:

- **`smtp` (default)** — Spring `JavaMailSender` against `MAIL_HOST`/`MAIL_PORT`. Locally that's Mailpit; it also covers any SMTP relay.
- **`mailjet`** — the [Mailjet](https://www.mailjet.com/) HTTP API, intended for the AWS deployment (AWS blocks outbound SMTP port 25 by default). Set `NOTIFICATION_TRANSPORT=mailjet` and provide `MAILJET_API_KEY`/`MAILJET_SECRET_KEY` (from AWS Secrets Manager in production). The `MAIL_FROM` address must be a verified Mailjet sender.

The transport is chosen at startup (a config change, no rebuild). To switch:

```bash
# SMTP / Mailpit — the default; nothing to set
./mvnw spring-boot:run

# Mailjet — set the transport + credentials (e.g. in .env)
NOTIFICATION_TRANSPORT=mailjet
MAILJET_API_KEY=your-mailjet-api-key
MAILJET_SECRET_KEY=your-mailjet-secret-key
MAIL_FROM=auctions@yourdomain.com   # must be a verified Mailjet sender
```

To switch back, unset `NOTIFICATION_TRANSPORT` (or set it to `smtp`) and restart.

## Setting up Google OAuth

Bidder login uses Sign in with Google. Without `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET` set, that login is disabled and the bidder-facing pages are left unsecured for local development.

1. In the [Google Cloud Console](https://console.cloud.google.com/), create (or reuse) a project.
2. Go to **APIs & Services → Credentials → Create Credentials → OAuth client ID**.
   - Application type: **Web application**.
   - Authorized redirect URI: `http://localhost:8080/login/oauth2/code/google` (adjust host/port for other environments).
3. Copy the generated **Client ID** and **Client secret**.
4. Add them to your `.env` file:
   ```
   export GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com
   export GOOGLE_CLIENT_SECRET=your-client-secret
   ```
5. Restart the app. Any Google account can now sign in; a `User` row is auto-provisioned on first login with the `USER` role.

## Tests

```
./mvnw test
```

Tests run against an ephemeral Testcontainers Postgres instance — no local Docker Compose setup or database state is required beyond having Docker available.

Code coverage is optional and off by default (so the plain build stays fast). To measure it:

```
./mvnw test -Pcoverage
```

This writes a JaCoCo report to `target/site/jacoco/index.html`. CI runs this on every push/PR and posts a coverage summary as a PR comment.

## Deployment (AWS)

The app deploys to AWS ECS Express Mode via GitHub Actions. Terraform lives under `terraform/aws/` in three stacks:

| Stack | Manages | Applied by |
|---|---|---|
| `bootstrap/` | S3 state bucket + DynamoDB lock table (Terraform's own backend) | Once, manually (local backend) |
| `deploy-role/` | The `github-actions-ecs-role` OIDC role and the IAM policy CI assumes | **Manually**, with account-admin credentials (see below) |
| `application/` | VPC, RDS, ECR, Secrets Manager, the ECS Express service | CI, on every **aws-deploy** run |

`aws-deploy` (build image + `terraform apply`) and `aws-destroy` (`terraform destroy`, to stop the bill) are manual `workflow_dispatch` workflows, both guarded to the `main` branch; `aws-destroy` additionally requires typing `destroy` to confirm.

### Applying the `deploy-role` stack (manual step)

CI assumes `github-actions-ecs-role` but cannot modify it, so the `deploy-role/` stack is **not** part of the CI flow — it must be applied by hand whenever its IAM policy changes (e.g. granting the CI role a new permission). Use admin credentials for the deployment account (`039314425267`):

```bash
# Log in with an admin profile for the account (e.g. via granted/assume) first, then:
cd terraform/aws/deploy-role
terraform init -backend-config=backend.hcl
terraform apply -var state_bucket_name=spring-auction-tf-state-039314425267
```

> **Common trap:** a permission added to `deploy-role/iam.tf` and merged to `main` has **no effect** until this apply runs. Until then the next `aws-deploy` / `aws-destroy` still fails with `AccessDenied` on the missing action — merging the change is not enough.

## Deployment (GCP)

A parallel deployment runs the same image on Google Cloud Run — both clouds deployable independently.
Terraform lives under `terraform/gcp/` in the same three-stack shape as AWS (see `terraform/gcp/README.md`
for the full commands):

| Stack | Manages | Applied by |
|---|---|---|
| `bootstrap/` | GCS state bucket + project API enablement (Terraform's own backend) | Once, manually (local backend). No lock table — GCS locks natively. |
| `deploy-role/` | Workload Identity Federation pool/provider + the `github-actions-deploy` service account CI impersonates | **Manually**, with account-admin credentials (see below) |
| `application/` | VPC, Cloud SQL, Artifact Registry, Secret Manager, IAM, the Cloud Run service | CI, on every **gcp-deploy** run |

`gcp-deploy` (build image + two-phase `terraform apply`) and `gcp-destroy` (`terraform destroy`, to stop
the bill) are manual `workflow_dispatch` workflows, both guarded to `main`; `gcp-destroy` additionally
requires typing `destroy` to confirm. Cloud Run reaches Cloud SQL over a private IP via Direct VPC egress
(see `docs/adr/0005-cloud-run-reaches-cloud-sql-via-private-ip.md`) — the app's JDBC config is unchanged.

### Applying the `deploy-role` stack (manual step)

Exactly like AWS: CI *impersonates* `github-actions-deploy` but cannot modify it, so `deploy-role/` is
**not** part of the CI flow — apply it by hand whenever its IAM changes (e.g. granting the deploy SA a
new role). Use admin credentials (ADC) for the project (`spring-auction`):

```bash
cd terraform/gcp/deploy-role
terraform init -backend-config=backend.hcl
terraform apply -var project_id=spring-auction -var state_bucket_name=spring-auction-tf-state-spring-auction
```

> **Common trap (same as AWS):** a role added to `deploy-role/iam.tf` and merged to `main` has **no
> effect** until this apply runs. Until then the next `gcp-deploy` / `gcp-destroy` still fails with a
> `403 PERMISSION_DENIED` on the missing action — merging the change is not enough.

### First deploy — one manual OAuth step

Cloud Run's URL is deterministic (`https://spring-auction-<projectnumber>.<region>.run.app`), so
`GCP_APP_BASE_URL` is set upfront and no redeploy is needed. After the first `gcp-deploy`, add the
service URL's callback to the existing Google OAuth client's **Authorized redirect URIs** (Console →
APIs & Services → Credentials), or Google/admin login will fail:

```
https://spring-auction-<projectnumber>.<region>.run.app/login/oauth2/code/google
```

Verify with `curl <service_url>/actuator/health` → `{"status":"UP"}` (proves Cloud Run is up **and**
reached Cloud SQL over the private IP — health reports DOWN if the DB is unreachable).
