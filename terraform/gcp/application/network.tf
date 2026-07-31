# Custom VPC (analog of the AWS VPC). Cloud Run reaches Cloud SQL over this network's private
# range via Direct VPC egress (ADR 0005) — no public IP on the database, no NAT, no firewall
# rules needed (the managed private-services-access peering handles reachability).
resource "google_compute_network" "main" {
  name                    = "spring-auction"
  auto_create_subnetworks = false
}

resource "google_compute_subnetwork" "main" {
  name          = "spring-auction"
  region        = var.region
  network       = google_compute_network.main.id
  ip_cidr_range = "10.20.0.0/24"
}

# Private services access: the internal range Cloud SQL draws its private IP from. GCP peers
# our VPC with the Google-managed services network across this reserved block (the analog of
# the AWS private subnets that RDS lived in).
resource "google_compute_global_address" "private_services" {
  name          = "spring-auction-psa"
  purpose       = "VPC_PEERING"
  address_type  = "INTERNAL"
  prefix_length = 16
  network       = google_compute_network.main.id
}

resource "google_service_networking_connection" "private_services" {
  network                 = google_compute_network.main.id
  service                 = "servicenetworking.googleapis.com"
  reserved_peering_ranges = [google_compute_global_address.private_services.name]

  # The peering is the known teardown failure point — `terraform destroy` can hang or error on
  # it. The gcp-destroy workflow (ticket #34) removes it explicitly before destroy, the same way
  # aws-destroy force-deletes the ECR repo. Left standard here so a normal apply is clean.
}
