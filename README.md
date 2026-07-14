# spring-auction

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
   Spring Boot's Docker Compose support automatically starts the `postgres` container defined in `compose.yaml` and runs Flyway migrations on startup.
3. The app is available at http://localhost:8080.

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

Set these in a `.env` file at the repo root (already gitignored) or export them in your shell before running.

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
