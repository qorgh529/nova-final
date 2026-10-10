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
