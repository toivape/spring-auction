---
status: accepted
---

# Cloud Run reaches Cloud SQL over a private IP (Direct VPC egress), not the `/cloudsql` Auth Proxy socket

The GCP deployment runs the app on Cloud Run and needs it to reach the managed Cloud SQL Postgres
instance. GCP's most-documented path for this is the **Cloud SQL Auth Proxy**: Cloud Run mounts a
`/cloudsql/<connection-name>` Unix socket, and the app connects through it. On the JVM that means
adding the `postgres-socket-factory` (Cloud SQL JDBC socket factory) dependency to `pom.xml` and a
GCP-specific JDBC URL of the form `jdbc:postgresql:///auction?cloudSqlInstance=...&socketFactory=...`.

That approach would push cloud-provider specifics *into the application*. The app's datasource is
today a plain `jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}` — the exact same shape it uses
against local Postgres (compose) and against RDS on AWS. Introducing the socket factory would fork
that config per cloud, add a GCP-only runtime dependency, and mean the container image is no longer
provider-agnostic.

We chose instead to give Cloud SQL a **private IP** on a VPC and let Cloud Run reach it via **Direct
VPC egress** (GA April 2024) over plain TCP. The app connects with `DB_HOST` set to the Cloud SQL
private IP and an ordinary JDBC URL — **zero changes to `pom.xml` or the datasource config**, and the
same image runs unchanged across local, AWS, and GCP. It also mirrors the AWS topology (RDS on private
subnets), keeping the two deployments conceptually parallel.

Trade-off: this buys a whole VPC + a private-services-access peering
(`google_compute_global_address` + `google_service_networking_connection`) where the socket approach
needs only a flag on the service. That is more infrastructure to stand up and — more painfully — to
tear down: the servicenetworking-managed peering is a known `terraform destroy` snag that the
`gcp-destroy` workflow has to work around (Cloud SQL first, then delete the peering via `gcloud`, then
the rest). We accepted that infra cost specifically to keep the application untouched: no GCP-specific
dependency or JDBC URL leaks into the code. The single-instance pin (ADR 0003) is unaffected — this is
purely about how the one instance reaches the database.
