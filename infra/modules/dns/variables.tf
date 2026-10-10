variable "zone_id" { type = string }

variable "record_name" {
  description = "서비스 도메인 (예: api.payflow.example.com)"
  type        = string
}

variable "primary_ip" {
  description = "GKE Ingress 외부 IP"
  type        = string
}

variable "standby_alb_dns_name" { type = string }
variable "standby_alb_zone_id" { type = string }
variable "health_check_path" { type = string }

variable "health_check_port" {
  type    = number
  default = 80
}

variable "ttl" {
  description = "Primary 레코드 TTL. 짧을수록 절체가 빠르다"
  type        = number
  default     = 60
}
