locals {
  az_count = length(var.azs)
}

# ---------- Network ----------

module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "~> 5.21"

  name = "${var.name}-vpc"
  cidr = var.vpc_cidr
  azs  = var.azs

  private_subnets  = [for i in range(local.az_count) : cidrsubnet(var.vpc_cidr, 4, i)]
  public_subnets   = [for i in range(local.az_count) : cidrsubnet(var.vpc_cidr, 8, 100 + i)]
  database_subnets = [for i in range(local.az_count) : cidrsubnet(var.vpc_cidr, 8, 200 + i)]

  # Standby 비용 절감: NAT 1개
  enable_nat_gateway   = true
  single_nat_gateway   = true
  enable_dns_hostnames = true

  create_database_subnet_group = true

  public_subnet_tags = {
    "kubernetes.io/role/elb" = 1
  }

  private_subnet_tags = {
    "kubernetes.io/role/internal-elb" = 1
  }
}

# ---------- EKS ----------

module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 20.37"

  cluster_name    = "${var.name}-eks"
  cluster_version = var.eks_version

  cluster_endpoint_public_access           = true
  enable_cluster_creator_admin_permissions = true

  cluster_addons = {
    coredns    = {}
    kube-proxy = {}
    vpc-cni = {
      before_compute = true
    }
    eks-pod-identity-agent = {
      before_compute = true
    }
  }

  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets

  eks_managed_node_groups = {
    standby = {
      instance_types = var.eks_instance_types
      # 장애 시점 Spot 회수 위험을 피하기 위해 On-Demand
      capacity_type = "ON_DEMAND"
      min_size      = var.eks_min_nodes
      max_size      = var.eks_max_nodes
      desired_size  = var.eks_min_nodes
    }
  }
}

# ---------- RDS (Logical Replication Subscriber) ----------

resource "aws_security_group" "rds" {
  name        = "${var.name}-rds"
  description = "PostgreSQL from VPC (EKS) and GCP over VPN"
  vpc_id      = module.vpc.vpc_id

  ingress {
    description = "PostgreSQL"
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    cidr_blocks = concat([var.vpc_cidr], var.peer_cidrs)
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_db_parameter_group" "this" {
  name   = "${var.name}-pg${var.postgres_version}"
  family = "postgres${var.postgres_version}"

  # Failback(RDS → Cloud SQL 역방향 복제) 시 RDS가 Publisher가 되기 위해 필요
  parameter {
    name         = "rds.logical_replication"
    value        = "1"
    apply_method = "pending-reboot"
  }
}

resource "aws_db_instance" "this" {
  identifier     = "${var.name}-pg"
  engine         = "postgres"
  engine_version = tostring(var.postgres_version)
  instance_class = var.rds_instance_class

  allocated_storage     = 20
  max_allocated_storage = 100
  storage_type          = "gp3"
  storage_encrypted     = true

  db_name                     = "payflow"
  username                    = "payflow_admin"
  manage_master_user_password = true

  db_subnet_group_name   = module.vpc.database_subnet_group_name
  vpc_security_group_ids = [aws_security_group.rds.id]
  parameter_group_name   = aws_db_parameter_group.this.name
  publicly_accessible    = false
  multi_az               = false

  backup_retention_period   = 1
  apply_immediately         = true
  deletion_protection       = var.deletion_protection
  skip_final_snapshot       = !var.deletion_protection
  final_snapshot_identifier = var.deletion_protection ? "${var.name}-pg-final" : null
}
