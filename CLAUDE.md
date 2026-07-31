# CLAUDE.md

Project-specific notes for spring-auction. See PLAN.md (parent directory) for the full design/feature plan.

@.claude/CLAUDE.local.md

## Repo layout
- This directory is both the git repo root and the Maven project root. Always run `./mvnw` etc. from here.
- `PLAN.md` lives one level up, in the parent directory — it is NOT inside this git repo and is not version-controlled here. It's the living design doc: update it in the same turn a decision or implementation diverges from what it says.

## Stack (verify against pom.xml, don't assume)
- Maven (`pom.xml`, `./mvnw`), not Gradle.
- Spring Boot 4.0.7, Spring Framework 7.0.8, Java 26 (via sdkman).
- Spring Data JDBC, not JPA — no Hibernate. Entities are plain records/classes (`@Table`, `@Id`), repositories extend `ListCrudRepository`/`CrudRepository`.
- Package root `fi.petri.springauction`, package-by-feature (`auction/`, `ingest/`, `security/`, ...).

## Spring Boot 4 / Spring Security 7 test-annotation relocations
- `@DataJdbcTest` → `org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest`
- `@WebMvcTest` / `@AutoConfigureMockMvc` → `org.springframework.boot.webmvc.test.autoconfigure`
- `SecurityMockMvcResultMatchers` (e.g. `authenticated()`, `unauthenticated()`) → `org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers` (package is `response`, not `result`, in spring-security-test 7.0.6). `SecurityMockMvcRequestBuilders.formLogin(...)` and `SecurityMockMvcRequestPostProcessors.csrf()` are still under `...servlet.request`.
- If a test-annotation/matcher import doesn't resolve, it likely moved packages on this version — check the jar (`jar tf ~/.m2/repository/.../<artifact>-<version>.jar | grep -i <ClassName>`) rather than guessing.
- `webjars-locator-core`'s versionless `/webjars/<name>/<file>` resolution is **not** wired up in Boot 4.0.7 (no `webjar` string appears anywhere in any `spring-boot*` jar) — don't add that dependency expecting it to do anything. Use the versioned path instead, e.g. `/webjars/bootstrap/5.3.7/css/bootstrap.min.css`; the base `/webjars/**` → classpath `META-INF/resources/webjars/` static mapping still works fine on its own.

## Local Postgres / Docker
- **Standing permission**: you may run `docker compose down -v` (dev-volume reset) without asking first. The dev volume holds only seed/throwaway data, so wiping and re-migrating it is a safe, expected operation here — just do it when a reset is the fix, and mention that you did.
- `compose.yaml`'s `POSTGRES_DB`/`USER`/`PASSWORD` only take effect on a fresh volume. Changing them and hitting "database X does not exist" means the fix is `docker compose down -v` — that deletes the local dev volume (fine to run unprompted, per the standing permission above).
- Editing `V1__create_database.sql` in place (see Migrations below) leaves the dev volume's `flyway_schema_history` checksum stale the next time the app actually starts against it — Flyway refuses to run with "Migration checksum mismatch". A `docker compose down -v` (fresh volume, re-migrates cleanly) is the reliable fix; run it unprompted per the standing permission above. Don't just patch the checksum in `flyway_schema_history` — the dev volume's actual table/column names can *also* be stale from before an in-place edit (e.g. still `auctions`/`users` from before a rename), so the schema itself, not just the recorded checksum, needs to be current.
- Tests use a separate, ephemeral Testcontainers Postgres (`TestcontainersConfiguration`, `public`) — unrelated to the `compose.yaml` dev container, no reset needed there, and unaffected by the checksum issue above since it always migrates from empty.

## Security
- With no `SecurityFilterChain` bean at all, Spring Boot's default auto-config secures every path and logs a random dev password on startup.
- Once any `SecurityFilterChain` bean exists, it's the *only* chain unless a catch-all is added — paths outside every `securityMatcher` become fully unsecured, not auto-protected by anything else. `ingestionChain` covers `/api/ingest/**`, `adminChain` covers `/admin/**`; everything else is still unsecured until a Google-OAuth `appChain` catch-all is added.
- `ingestionChain`: stateless, CSRF disabled, `X-API-Key` header matching `app.ingestion.api-key` (env `INGESTION_API_KEY`, dev default `dev-ingestion-key`). `anyRequest().authenticated()` with no custom `AuthenticationEntryPoint` returns **403** for a missing/invalid credential, not 401.
- `adminChain`: session-based `formLogin()` at `/admin/login`, `hasRole("ADMIN")`. CSRF stays **enabled** (session cookies, unlike ingestion) — two distinct failure modes to keep straight: a POST with **no** `_csrf` at all is rejected by `CsrfFilter` itself with 403 before auth ever runs; a POST **with** a valid CSRF token but no authenticated session gets a **302 redirect to the login page** instead (anonymous + access-denied is translated to an auth challenge by `ExceptionTranslationFilter`, not a 403).
- Admin login is seeded/re-hashed on every startup by `AdminBootstrapRunner` from `app.admin.email`/`app.admin.password` (env `ADMIN_EMAIL`/`ADMIN_PASSWORD`, dev defaults `admin@example.com`/`dev-admin-password`) — no separate seed migration needed, and the password always matches the current property.
- Spring Data JDBC quirk: never call `repository.save()` for an upsert on an entity whose `@Id` is manually assigned (not DB-generated) — a non-null id always triggers an `UPDATE`, which fails with 0-rows-affected on first insert. Use a raw `JdbcClient`/`INSERT ... ON CONFLICT` instead (see `AdminBootstrapRunner`).

## Testing conventions
- Full REST→DB / login→DB tests: `@SpringBootTest` + `@AutoConfigureMockMvc` + `@Import(TestcontainersConfiguration.class)` + `@Transactional` (rollback keeps the shared container clean between test methods). Deliberately not `@WebMvcTest` — that slice mocks out the repository layer.
- For auth-gated endpoints, prefer a real `MockMvc` login (`SecurityMockMvcRequestBuilders.formLogin(...)`, reuse the returned session) over `@WithMockUser`/`.with(user(...))` shortcuts — exercises the actual `SecurityFilterChain`, not just the controller.
- Test packages mirror main packages (e.g. the `ingest` controller test lives under `src/test/.../ingest/`).

## Migrations
- Single-file schema so far (`V1__create_database.sql`). While pre-release (no real deployed data), edit it in place rather than adding `V2`/`V3` migrations; switch to additive migrations once anything is actually deployed. See the dev-volume checksum gotcha under Local Postgres/Docker above — an in-place edit means the dev container needs a reset before it'll start again.
- Table names are singular (`auction`, `bid`, not `auctions`/`bids`). `"user"` is quoted in SQL — it's a reserved word in Postgres.

## Terraform / AWS deploy
- Three stacks under `terraform/aws/`: `bootstrap/` (state bucket + lock table, local backend, one-time), `deploy-role/` (the CI OIDC role `github-actions-ecs-role` + its IAM policy), `application/` (VPC/RDS/ECR/secrets/ECS Express). See README "Deployment (AWS)" for the full commands.
- **`deploy-role/` is applied manually, never by CI** — CI *assumes* that role and can't edit it. A permission added to `deploy-role/iam.tf` and merged has NO effect until someone runs `terraform apply` on that stack with account-admin creds (`AWS_PROFILE=spring-auction/AdministratorAccess`; `init -backend-config=backend.hcl`; `apply -var state_bucket_name=spring-auction-tf-state-039314425267`). Symptom of forgetting: the next CI deploy/destroy 403s on the newly-added action.
- `aws-deploy` / `aws-destroy` are manual `workflow_dispatch` workflows, guarded to `main` (`aws-destroy` also needs `confirm=destroy`).
- `terraform destroy` reads deletion-affecting attributes from **state**, not config: a `force_delete=true` added to a resource but not yet `apply`d won't help a destroy. That's why `aws-destroy` force-deletes the ECR repo via the AWS CLI before `terraform destroy`.

## Terraform / GCP deploy
- Parallel to AWS, same three-stack shape under `terraform/gcp/`: `bootstrap/` (GCS state bucket + project API enablement, local backend, one-time — **no lock table**, GCS locks natively), `deploy-role/` (Workload Identity Federation pool/provider + the `github-actions-deploy` SA), `application/` (VPC/Cloud SQL/Artifact Registry/Secret Manager/IAM/Cloud Run). See README "Deployment (GCP)" and `terraform/gcp/README.md` for commands. Project id is `spring-auction`; state bucket `spring-auction-tf-state-spring-auction`.
- **`deploy-role/` is applied manually, never by CI** — CI *impersonates* the deploy SA and can't edit it (same trap as AWS). A role added to `deploy-role/iam.tf` and merged has NO effect until someone runs `terraform apply` with ADC admin creds (`gcloud auth application-default login`; `init -backend-config=backend.hcl`; `apply -var project_id=spring-auction -var state_bucket_name=spring-auction-tf-state-spring-auction`). Symptom of forgetting: the next CI deploy/destroy fails with `403 PERMISSION_DENIED` on the new action.
- `gcp-deploy` / `gcp-destroy` are manual `workflow_dispatch` workflows, guarded to `main` (`gcp-destroy` also needs `confirm=destroy`). `gcp-deploy` is **two-phase**: targeted apply of the Artifact Registry repo → build/push image → full apply (`container_image` has no default). Auth is keyless via WIF (`google-github-actions/auth`), not a service-account key.
- **Destroy peering gotcha** (the GCP analog of AWS's ECR force-delete, same state-not-config lesson): the servicenetworking-managed VPC peering blocks a plain `terraform destroy`. `google_service_networking_connection` has `deletion_policy = "ABANDON"` (already applied into state), and `gcp-destroy` sequences it: destroy Cloud SQL first (frees the range) → `gcloud compute networks peerings delete servicenetworking-googleapis-com` → full destroy.
- **Cloud SQL edition gotcha**: `db-f1-micro` (shared-core) is only valid under `edition = "ENTERPRISE"`; Postgres 18 otherwise defaults new instances to `ENTERPRISE_PLUS`, which rejects it with `Error 400: Invalid Tier`.
- **`MAIL_FROM` is set on GCP** (unlike AWS, where it's unset and email silently fails) via `TF_VAR_mail_from` / repo var `GCP_MAIL_FROM` — a verified Mailjet sender, so notifications actually send. Consider backporting to AWS.
- **Separate OAuth client per cloud**: GCP uses its own Google OAuth client ("GCP client", under the spring-auction project) via `GCP_GOOGLE_CLIENT_ID` (var) + `GCP_GOOGLE_CLIENT_SECRET` (secret), so each cloud's redirect URIs live on its own client. AWS keeps the shared `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET`.
- Repo **variables**: `GCP_WIF_PROVIDER`, `GCP_DEPLOY_SA`, `GCP_PROJECT_ID`, `GCP_REGION`, `GCP_APP_BASE_URL`, `GCP_AR_REPOSITORY`, `GCP_MAIL_FROM`, `GCP_GOOGLE_CLIENT_ID`. **Secrets**: `GCP_GOOGLE_CLIENT_SECRET` is GCP-specific; `MAILJET_*`, `ADMIN_PASSWORD`, `INGESTION_API_KEY` are reused across both clouds.
