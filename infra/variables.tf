variable "project_name" {
  description = "리소스 이름 접두사"
  type        = string
  default     = "payflow"
}

# ---------- GCP (Primary) ----------

variable "gcp_project_id" {
  description = "GCP 프로젝트 ID"
  type        = string
}

variable "gcp_region" {
  description = "GCP 리전"
  type        = string
  default     = "asia-northeast3"
}

variable "gcp_vpc_cidr" {
  description = "GCP 노드 서브넷 CIDR"
  type        = string
  default     = "10.10.0.0/20"
}

variable "gcp_pods_cidr" {
  description = "GKE 파드 보조 범위"
  type        = string
  default     = "10.11.0.0/16"
}

variable "gcp_services_cidr" {
  description = "GKE 서비스 보조 범위"
  type        = string
  default     = "10.12.0.0/20"
}

variable "gcp_master_cidr" {
  description = "GKE 컨트롤 플레인 /28 범위"
  type        = string
  default     = "10.12.16.0/28"
}

variable "gcp_psa_cidr" {
  description = "Cloud SQL Private Service Access 범위 (AWS로 BGP 광고됨)"
  type        = string
  default     = "10.13.0.0/20"
}

variable "gke_location" {
  description = "GKE 위치. 존(예: asia-northeast3-a)이면 무료 등급 대상, 리전이면 컨트롤 플레인 이중화"
  type        = string
  default     = "asia-northeast3-a"
}

variable "gke_machine_type" {
  type    = string
  default = "e2-standard-2"
}

variable "gke_min_nodes" {
  type    = number
  default = 1
}

variable "gke_max_nodes" {
  type    = number
  default = 4
}

variable "cloudsql_tier" {
  type    = string
  default = "db-custom-1-3840"
}

# ---------- AWS (Warm Standby) ----------

variable "aws_region" {
  description = "AWS 리전"
  type        = string
  default     = "ap-northeast-2"
}

variable "aws_azs" {
  type    = list(string)
  default = ["ap-northeast-2a", "ap-northeast-2c"]
}

variable "aws_vpc_cidr" {
  type    = string
  default = "10.20.0.0/16"
}

variable "eks_version" {
  description = "EKS 버전. 표준 지원 기간이 끝난 버전은 추가 요금이 붙으니 적용 전 확인"
  type        = string
  default     = "1.34"
}

variable "eks_instance_types" {
  type    = list(string)
  default = ["t3.medium"]
}

variable "eks_min_nodes" {
  description = "평시 Standby 노드 수 (On-Demand)"
  type        = number
  default     = 1
}

variable "eks_max_nodes" {
  type    = number
  default = 4
}

variable "rds_instance_class" {
  type    = string
  default = "db.t4g.micro"
}

# ---------- 공통 ----------

variable "postgres_major_version" {
  description = "양쪽 PostgreSQL 메이저 버전 (복제를 위해 동일하게 유지)"
  type        = number
  default     = 16
}

variable "deletion_protection" {
  description = "GKE·Cloud SQL·RDS 삭제 보호. 프로젝트 기간 중에는 false로 두고 destroy 가능하게 운영"
  type        = bool
  default     = false
}

# ---------- VPN ----------

variable "gcp_router_asn" {
  type    = number
  default = 65000
}

variable "aws_vgw_asn" {
  type    = number
  default = 64512
}

# ---------- DNS Failover (앱 배포 후 활성화) ----------

variable "enable_dns_failover" {
  description = "Ingress 엔드포인트가 생긴 뒤 true로 전환"
  type        = bool
  default     = false
}

variable "route53_zone_id" {
  type    = string
  default = ""
}

variable "service_domain" {
  description = "예: api.payflow.example.com"
  type        = string
  default     = ""
}

variable "primary_ingress_ip" {
  description = "GKE Ingress 외부 IP"
  type        = string
  default     = ""
}

variable "standby_alb_dns_name" {
  description = "EKS ALB DNS 이름"
  type        = string
  default     = ""
}

variable "standby_alb_zone_id" {
  description = "EKS ALB의 Hosted Zone ID"
  type        = string
  default     = ""
}

variable "health_check_path" {
  type    = string
  default = "/healthz"
}
