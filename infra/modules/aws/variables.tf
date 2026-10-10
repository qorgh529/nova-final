variable "name" { type = string }
variable "azs" { type = list(string) }
variable "vpc_cidr" { type = string }

variable "peer_cidrs" {
  description = "VPN 너머 GCP 대역 (보안 그룹 허용용)"
  type        = list(string)
}

variable "eks_version" { type = string }
variable "eks_instance_types" { type = list(string) }
variable "eks_min_nodes" { type = number }
variable "eks_max_nodes" { type = number }
variable "rds_instance_class" { type = string }
variable "postgres_version" { type = number }
variable "deletion_protection" { type = bool }
