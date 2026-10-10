provider "google" {
  project = var.gcp_project_id
  region  = var.gcp_region

  default_labels = {
    project    = var.project_name
    managed-by = "terraform"
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project   = var.project_name
      ManagedBy = "terraform"
    }
  }
}

# EKS에 Helm 차트를 설치하기 위한 연결. 인증 토큰은 실행 시점에 aws CLI로 발급한다
provider "helm" {
  kubernetes {
    host                   = module.aws.eks_endpoint
    cluster_ca_certificate = base64decode(module.aws.eks_ca_data)

    exec {
      api_version = "client.authentication.k8s.io/v1beta1"
      command     = "aws"
      args        = ["eks", "get-token", "--cluster-name", module.aws.eks_name, "--region", var.aws_region]
    }
  }
}
