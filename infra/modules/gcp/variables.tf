variable "name" { type = string }
variable "region" { type = string }
variable "subnet_cidr" { type = string }
variable "pods_cidr" { type = string }
variable "services_cidr" { type = string }
variable "master_cidr" { type = string }
variable "psa_cidr" { type = string }

variable "peer_cidrs" {
  description = "VPN 너머 AWS 대역 (방화벽 허용용)"
  type        = list(string)
}

variable "gke_location" { type = string }
variable "gke_machine_type" { type = string }
variable "gke_min_nodes" { type = number }
variable "gke_max_nodes" { type = number }
variable "cloudsql_tier" { type = string }
variable "postgres_version" { type = number }
variable "deletion_protection" { type = bool }
