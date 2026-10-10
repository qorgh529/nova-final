variable "name" { type = string }
variable "cluster_name" { type = string }
variable "region" { type = string }
variable "vpc_id" { type = string }

variable "lbc_chart_version" {
  description = "aws-load-balancer-controller Helm 차트 버전. 바꾸면 iam-policy-lbc.json도 같은 버전으로 교체"
  type        = string
  default     = "3.5.0"
}
