# deploy (담당: C)

Kustomize + ArgoCD.

- `base/`: 클라우드 공통 매니페스트
- `overlays/gke/`: GKE 전용 (GCE Ingress, StorageClass, 정규 replicas)
- `overlays/eks/`: EKS 전용 (ALB Ingress, StorageClass, 최소 replicas + HPA)
- `argocd/`: Application 정의, 클러스터 등록

ArgoCD는 **AWS(EKS) 쪽에 설치**한다. GCP 장애 시에도 배포가 가능해야 한다.
