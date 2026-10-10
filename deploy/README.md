# deploy (담당: C)

Kustomize(클라우드 차이 분리) + ArgoCD(멀티 클러스터 GitOps).

```
deploy/
├── base/                    # 클라우드 공통
│   ├── auth|payment|order/  #   Deployment, Service, HPA(min 2 / max 6)
│   ├── ingress.yaml         #   /auth /orders /payments /healthz
│   └── namespace.yaml       #   payflow
├── overlays/
│   ├── gke/                 # Artifact Registry 이미지, GCE Ingress(고정 IP), APP_MODE=primary
│   └── eks/                 # ECR 이미지, ALB Ingress, HPA min 1, APP_MODE=standby
└── argocd/
    ├── install/             # ArgoCD v3.5.3 (EKS에 설치)
    └── apps/                # AppProject + ApplicationSet (GKE·EKS 동시 배포)
```

## 클라우드별 차이 (overlay가 바꾸는 것만)

| 항목 | GKE (Primary) | EKS (Standby) |
|---|---|---|
| 이미지 레지스트리 | Artifact Registry | ECR |
| Ingress | GCE, Terraform 고정 IP `payflow-ingress-ip` | ALB (internet-facing, target-type ip) |
| HPA 최소 파드 | 2 | 1 |
| `APP_MODE` | `primary` | `standby` (읽기 전용) |
| `DB_HOST` | Cloud SQL 사설 IP | RDS 엔드포인트 |

## 설치 순서

```bash
# 0. 플레이스홀더 치환 (terraform output 값으로)
#    overlays/gke: GCP_PROJECT_ID, CLOUDSQL_PRIVATE_IP
#    overlays/eks: AWS_ACCOUNT_ID, RDS_ENDPOINT

# 1. DB 접속 Secret (Git에 올리지 않음) - 양쪽 클러스터 각각
kubectl create namespace payflow
kubectl -n payflow create secret generic payflow-db \
  --from-literal=DB_USER=payflow_admin --from-literal=DB_PASSWORD='<비밀번호>'

# 2. ArgoCD 설치 (EKS 컨텍스트에서)
kubectl apply -k deploy/argocd/install --server-side
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d

# 3. GKE를 ArgoCD에 등록 (이름은 반드시 gke)
argocd login <argocd-server>
argocd cluster add <gke-kube-context> --name gke

# 4. ApplicationSet 적용 → payflow-gke, payflow-eks 앱 생성
kubectl apply -k deploy/argocd/apps
```

### 사전 조건
- EKS의 ALB Ingress는 AWS Load Balancer Controller가 처리한다. `infra/modules/eks-addons`가 `terraform apply` 때 함께 설치한다
- 앱 이미지가 레지스트리에 올라가기 전까지 파드는 `ImagePullBackOff` 상태가 정상

## 운영 규칙

- 클러스터에 `kubectl edit`로 직접 고치지 않는다. `selfHeal: true`라서 ArgoCD가 Git 상태로 되돌린다
- 이미지 태그는 CI가 overlay의 `images.newTag`를 갱신하는 커밋으로 바꾼다 (양쪽 동시)
- **Failover 시 모드 전환도 Git 커밋으로 한다** (`APP_MODE` 변경 → ConfigMap 해시가 바뀌어 파드가 자동 재시작)
  - ArgoCD가 AWS에 있으므로 GCP 장애 중에도 EKS 배포가 가능하다
  - 상세 절차: [docs/runbook/failover.md](../docs/runbook/failover.md)

## 로컬 검증

```bash
kustomize build deploy/overlays/gke | kubeconform -strict -summary
kustomize build deploy/overlays/eks | kubeconform -strict -summary
```
PR마다 `.github/workflows/deploy.yml`이 같은 검사를 한다.
