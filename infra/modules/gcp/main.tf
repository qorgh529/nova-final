data "google_client_config" "current" {}

locals {
  project = data.google_client_config.current.project
}

resource "google_project_service" "apis" {
  for_each = toset([
    "compute.googleapis.com",
    "container.googleapis.com",
    "sqladmin.googleapis.com",
    "servicenetworking.googleapis.com",
    "artifactregistry.googleapis.com",
  ])

  service            = each.value
  disable_on_destroy = false
}

# ---------- Network ----------

resource "google_compute_network" "this" {
  name                    = "${var.name}-vpc"
  auto_create_subnetworks = false
  routing_mode            = "GLOBAL"

  depends_on = [google_project_service.apis]
}

resource "google_compute_subnetwork" "gke" {
  name                     = "${var.name}-gke"
  region                   = var.region
  network                  = google_compute_network.this.id
  ip_cidr_range            = var.subnet_cidr
  private_ip_google_access = true

  secondary_ip_range {
    range_name    = "pods"
    ip_cidr_range = var.pods_cidr
  }

  secondary_ip_range {
    range_name    = "services"
    ip_cidr_range = var.services_cidr
  }
}

# 프라이빗 노드의 외부 이미지 pull 등을 위한 NAT
resource "google_compute_router" "nat" {
  name    = "${var.name}-nat-router"
  region  = var.region
  network = google_compute_network.this.id
}

resource "google_compute_router_nat" "this" {
  name                               = "${var.name}-nat"
  router                             = google_compute_router.nat.name
  region                             = var.region
  nat_ip_allocate_option             = "AUTO_ONLY"
  source_subnetwork_ip_ranges_to_nat = "ALL_SUBNETWORKS_ALL_IP_RANGES"
}

resource "google_compute_firewall" "from_aws" {
  name          = "${var.name}-allow-from-aws"
  network       = google_compute_network.this.id
  source_ranges = var.peer_cidrs

  allow {
    protocol = "icmp"
  }

  allow {
    protocol = "tcp"
    ports    = ["5432", "443", "80"]
  }
}

# ---------- GKE ----------

resource "google_service_account" "gke_nodes" {
  account_id   = "${var.name}-gke-nodes"
  display_name = "GKE node service account"
}

resource "google_project_iam_member" "gke_nodes" {
  for_each = toset([
    "roles/logging.logWriter",
    "roles/monitoring.metricWriter",
    "roles/monitoring.viewer",
    "roles/artifactregistry.reader",
  ])

  project = local.project
  role    = each.value
  member  = "serviceAccount:${google_service_account.gke_nodes.email}"
}

resource "google_container_cluster" "this" {
  name     = "${var.name}-gke"
  location = var.gke_location

  network         = google_compute_network.this.id
  subnetwork      = google_compute_subnetwork.gke.id
  networking_mode = "VPC_NATIVE"

  remove_default_node_pool = true
  initial_node_count       = 1
  deletion_protection      = var.deletion_protection

  release_channel {
    channel = "REGULAR"
  }

  ip_allocation_policy {
    cluster_secondary_range_name  = "pods"
    services_secondary_range_name = "services"
  }

  private_cluster_config {
    enable_private_nodes    = true
    enable_private_endpoint = false
    master_ipv4_cidr_block  = var.master_cidr
  }

  workload_identity_config {
    workload_pool = "${local.project}.svc.id.goog"
  }
}

resource "google_container_node_pool" "primary" {
  name     = "primary"
  cluster  = google_container_cluster.this.id
  location = var.gke_location

  autoscaling {
    min_node_count = var.gke_min_nodes
    max_node_count = var.gke_max_nodes
  }

  management {
    auto_repair  = true
    auto_upgrade = true
  }

  node_config {
    machine_type    = var.gke_machine_type
    disk_size_gb    = 50
    service_account = google_service_account.gke_nodes.email
    oauth_scopes    = ["https://www.googleapis.com/auth/cloud-platform"]

    workload_metadata_config {
      mode = "GKE_METADATA"
    }
  }
}

# ---------- Cloud SQL (Private Service Access) ----------

resource "google_compute_global_address" "psa" {
  name          = "${var.name}-psa"
  purpose       = "VPC_PEERING"
  address_type  = "INTERNAL"
  address       = split("/", var.psa_cidr)[0]
  prefix_length = tonumber(split("/", var.psa_cidr)[1])
  network       = google_compute_network.this.id
}

resource "google_service_networking_connection" "psa" {
  network                 = google_compute_network.this.id
  service                 = "servicenetworking.googleapis.com"
  reserved_peering_ranges = [google_compute_global_address.psa.name]
}

# VPN으로 배운 AWS 경로를 Cloud SQL 쪽 VPC에 내보내야 RDS와 양방향 통신이 된다
resource "google_compute_network_peering_routes_config" "psa" {
  peering              = google_service_networking_connection.psa.peering
  network              = google_compute_network.this.name
  import_custom_routes = false
  export_custom_routes = true
}

resource "google_sql_database_instance" "this" {
  name                = "${var.name}-pg"
  region              = var.region
  database_version    = "POSTGRES_${var.postgres_version}"
  deletion_protection = var.deletion_protection

  settings {
    tier              = var.cloudsql_tier
    availability_type = "ZONAL"
    disk_type         = "PD_SSD"
    disk_size         = 20
    disk_autoresize   = true

    ip_configuration {
      ipv4_enabled    = false
      private_network = google_compute_network.this.id
    }

    backup_configuration {
      enabled                        = true
      point_in_time_recovery_enabled = true
    }

    # AWS RDS로의 logical replication (Publisher)
    database_flags {
      name  = "cloudsql.logical_decoding"
      value = "on"
    }
  }

  depends_on = [google_service_networking_connection.psa]
}

resource "google_sql_database" "payflow" {
  name     = "payflow"
  instance = google_sql_database_instance.this.name
}

resource "random_password" "admin" {
  length  = 24
  special = false
}

# 생성 후 복제 권한 부여 필요: ALTER ROLE payflow_admin WITH REPLICATION;
resource "google_sql_user" "admin" {
  name     = "payflow_admin"
  instance = google_sql_database_instance.this.name
  password = random_password.admin.result
}
