module "gcp" {
  source = "./modules/gcp"

  name                = var.project_name
  region              = var.gcp_region
  subnet_cidr         = var.gcp_vpc_cidr
  pods_cidr           = var.gcp_pods_cidr
  services_cidr       = var.gcp_services_cidr
  master_cidr         = var.gcp_master_cidr
  psa_cidr            = var.gcp_psa_cidr
  peer_cidrs          = [var.aws_vpc_cidr]
  gke_location        = var.gke_location
  gke_machine_type    = var.gke_machine_type
  gke_min_nodes       = var.gke_min_nodes
  gke_max_nodes       = var.gke_max_nodes
  cloudsql_tier       = var.cloudsql_tier
  postgres_version    = var.postgres_major_version
  deletion_protection = var.deletion_protection
}

module "aws" {
  source = "./modules/aws"

  name                = var.project_name
  azs                 = var.aws_azs
  vpc_cidr            = var.aws_vpc_cidr
  peer_cidrs          = [var.gcp_vpc_cidr, var.gcp_pods_cidr, var.gcp_psa_cidr]
  eks_version         = var.eks_version
  eks_instance_types  = var.eks_instance_types
  eks_min_nodes       = var.eks_min_nodes
  eks_max_nodes       = var.eks_max_nodes
  rds_instance_class  = var.rds_instance_class
  postgres_version    = var.postgres_major_version
  deletion_protection = var.deletion_protection
}

module "vpn" {
  source = "./modules/vpn"

  name           = var.project_name
  gcp_region     = var.gcp_region
  gcp_network_id = module.gcp.network_id
  gcp_router_asn = var.gcp_router_asn
  # Cloud SQL(PSA) 범위는 서브넷이 아니라서 별도로 광고해야 AWS에서 닿는다
  gcp_advertised_ranges = [var.gcp_psa_cidr]

  aws_vpc_id          = module.aws.vpc_id
  aws_vgw_asn         = var.aws_vgw_asn
  aws_route_table_ids = module.aws.private_route_table_ids
}

module "dns" {
  source = "./modules/dns"
  count  = var.enable_dns_failover ? 1 : 0

  zone_id              = var.route53_zone_id
  record_name          = var.service_domain
  primary_ip           = var.primary_ingress_ip
  standby_alb_dns_name = var.standby_alb_dns_name
  standby_alb_zone_id  = var.standby_alb_zone_id
  health_check_path    = var.health_check_path
}
