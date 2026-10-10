output "gke_get_credentials" {
  description = "GKE kubeconfig 등록 명령"
  value       = "gcloud container clusters get-credentials ${module.gcp.gke_name} --location ${var.gke_location} --project ${var.gcp_project_id}"
}

output "eks_update_kubeconfig" {
  description = "EKS kubeconfig 등록 명령"
  value       = "aws eks update-kubeconfig --name ${module.aws.eks_name} --region ${var.aws_region}"
}

output "cloudsql_private_ip" {
  description = "복제 Publisher 주소 (RDS에서 VPN 경유로 접근)"
  value       = module.gcp.cloudsql_private_ip
}

output "cloudsql_instance" {
  value = module.gcp.cloudsql_instance_name
}

output "cloudsql_admin_password" {
  value     = module.gcp.admin_password
  sensitive = true
}

output "rds_endpoint" {
  description = "복제 Subscriber 주소"
  value       = module.aws.rds_endpoint
}

output "rds_master_secret_arn" {
  description = "RDS 마스터 비밀번호가 저장된 Secrets Manager ARN"
  value       = module.aws.rds_master_secret_arn
}

output "vpn_tunnel_status_command" {
  value = "gcloud compute vpn-tunnels list --filter='name~${var.project_name}' --project ${var.gcp_project_id}"
}

output "route53_health_check_id" {
  value = var.enable_dns_failover ? module.dns[0].health_check_id : null
}

output "gke_ingress_ip" {
  description = "GKE Ingress 고정 IP (overlays/gke Ingress가 사용)"
  value       = module.gcp.ingress_ip
}
