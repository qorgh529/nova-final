output "vpc_id" {
  value = module.vpc.vpc_id
}

output "private_route_table_ids" {
  value = module.vpc.private_route_table_ids
}

output "eks_name" {
  value = module.eks.cluster_name
}

output "rds_endpoint" {
  value = aws_db_instance.this.address
}

output "rds_master_secret_arn" {
  value = aws_db_instance.this.master_user_secret[0].secret_arn
}
