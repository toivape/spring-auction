# CLAUDE.md

Project-specific notes for spring-auction. See PLAN.md (parent directory) for the full design/feature plan.

## Repo layout
- This directory is both the git repo root and the Maven project root. Always run `./mvnw` etc. from here.
- `PLAN.md` lives one level up, in the parent directory — it is NOT inside this git repo and is not version-controlled here. It's the living design doc: update it in the same turn a decision or implementation diverges from what it says.

## Stack (verify against pom.xml, don't assume)
- Maven (`pom.xml`, `./mvnw`), not Gradle.
- Spring Boot 4.0.7, Spring Framework 7.0.8, Java 26 (via sdkman).
- Spring Data JDBC, not JPA — no Hibernate. Entities are plain records/classes (`@Table`, `@Id`), repositories extend `ListCrudRepository`/`CrudRepository`.
- Package root `fi.petri.springauction`, package-by-feature (`auction/`, `ingest/`, `security/`, ...).

## Spring Boot 4 test-annotation relocations
- `@DataJdbcTest` → `org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest`
- `@WebMvcTest` / `@AutoConfigureMockMvc` → `org.springframework.boot.webmvc.test.autoconfigure`
- If a test-annotation import doesn't resolve, it likely moved packages in Boot 4 — check the jar (`jar tf ~/.m2/repository/org/springframework/boot/spring-boot-<x>-test/<version>/*.jar`) rather than guessing.

## Local Postgres / Docker
- `compose.yaml`'s `POSTGRES_DB`/`USER`/`PASSWORD` only take effect on a fresh volume. Changing them and hitting "database X does not exist" means the fix is `docker compose down -v` — that deletes the local dev volume, so confirm with the user before running it.
- Tests use a separate, ephemeral Testcontainers Postgres (`TestcontainersConfiguration`, `public`) — unrelated to the `compose.yaml` dev container, no reset needed there.

## Security
- With no `SecurityFilterChain` bean at all, Spring Boot's default auto-config secures every path and logs a random dev password on startup.
- Once any `SecurityFilterChain` bean exists, it's the *only* chain unless a catch-all is added — paths outside its `securityMatcher` become fully unsecured, not auto-protected by anything else. `ingestionChain` currently only covers `/api/ingest/**`.
- `anyRequest().authenticated()` with no custom `AuthenticationEntryPoint` returns **403** for a missing/invalid credential, not 401.
- `/api/ingest` requires header `X-API-Key` matching `app.ingestion.api-key` (env `INGESTION_API_KEY`, dev default `dev-ingestion-key`).

## Testing conventions
- Full REST→DB tests: `@SpringBootTest` + `@AutoConfigureMockMvc` + `@Import(TestcontainersConfiguration.class)` + `@Transactional` (rollback keeps the shared container clean between test methods). Deliberately not `@WebMvcTest` — that slice mocks out the repository layer.
- Test packages mirror main packages (e.g. the `ingest` controller test lives under `src/test/.../ingest/`).

## Migrations
- Single-file schema so far (`V1__create_database.sql`). While pre-release (no real deployed data), edit it in place rather than adding `V2`/`V3` migrations; switch to additive migrations once anything is actually deployed.
- Table names are singular (`auction`, `bid`, not `auctions`/`bids`). `"user"` is quoted in SQL — it's a reserved word in Postgres.
