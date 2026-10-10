# AWS Load Balancer Controller: EKS Ingress(ingressClassName: alb)를 ALB로 만든다.
# 권한은 EKS Pod Identity로 준다 (eks-pod-identity-agent 애드온은 modules/aws에서 설치).

locals {
  lbc_namespace       = "kube-system"
  lbc_service_account = "aws-load-balancer-controller"
}

data "aws_iam_policy_document" "pod_identity_trust" {
  statement {
    actions = ["sts:AssumeRole", "sts:TagSession"]

    principals {
      type        = "Service"
      identifiers = ["pods.eks.amazonaws.com"]
    }
  }
}

# 출처: kubernetes-sigs/aws-load-balancer-controller v3.5.0 docs/install/iam_policy.json
resource "aws_iam_policy" "lbc" {
  name   = "${var.name}-aws-load-balancer-controller"
  policy = file("${path.module}/iam-policy-lbc.json")
}

resource "aws_iam_role" "lbc" {
  name               = "${var.name}-aws-load-balancer-controller"
  assume_role_policy = data.aws_iam_policy_document.pod_identity_trust.json
}

resource "aws_iam_role_policy_attachment" "lbc" {
  role       = aws_iam_role.lbc.name
  policy_arn = aws_iam_policy.lbc.arn
}

resource "aws_eks_pod_identity_association" "lbc" {
  cluster_name    = var.cluster_name
  namespace       = local.lbc_namespace
  service_account = local.lbc_service_account
  role_arn        = aws_iam_role.lbc.arn
}

resource "helm_release" "lbc" {
  name       = "aws-load-balancer-controller"
  repository = "https://aws.github.io/eks-charts"
  chart      = "aws-load-balancer-controller"
  version    = var.lbc_chart_version
  namespace  = local.lbc_namespace

  values = [yamlencode({
    clusterName = var.cluster_name
    region      = var.region
    vpcId       = var.vpc_id
    # Standby 노드가 1개뿐이라 1개로 둔다. 컨트롤러가 잠시 죽어도 이미 만든 ALB는 계속 동작한다
    replicaCount = 1
    serviceAccount = {
      create = true
      name   = local.lbc_service_account
    }
  })]

  # 파드가 뜨기 전에 권한 연결이 있어야 자격 증명을 받는다
  depends_on = [
    aws_eks_pod_identity_association.lbc,
    aws_iam_role_policy_attachment.lbc,
  ]
}
