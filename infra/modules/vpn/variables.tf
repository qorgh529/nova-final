variable "name" { type = string }
variable "gcp_region" { type = string }
variable "gcp_network_id" { type = string }
variable "gcp_router_asn" { type = number }

variable "gcp_advertised_ranges" {
  description = "서브넷 외에 AWS로 추가 광고할 대역 (Cloud SQL PSA 등)"
  type        = list(string)
  default     = []
}

variable "aws_vpc_id" { type = string }
variable "aws_vgw_asn" { type = number }

variable "aws_route_table_ids" {
  description = "VPN 경로를 전파할 라우트 테이블 (private + database 서브넷)"
  type        = list(string)
}
