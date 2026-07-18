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
