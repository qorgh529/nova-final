# infra (담당: A)

GCP(Primary)·AWS(Standby)·VPN·DNS를 **루트 하나에서 한 번의 `terraform apply`** 로 만든다.
VPN은 양쪽 IP를 서로 참조해야 해서 루트를 클라우드별로 나누지 않았다.

```
infra/
├── main.tf / variables.tf / outputs.tf / providers.tf / versions.tf
├── backend.hcl.example        # GCS 원격 state 설정 예시
├── terraform.tfvars.example
└── modules/
    ├── gcp/   # VPC, NAT, GKE(프라이빗 노드), Cloud SQL(Private IP, logical_decoding=on)
    ├── aws/   # VPC, EKS(On-Demand 최소 노드), RDS(rds.logical_replication=1)
    ├── eks-addons/  # AWS Load Balancer Controller (Helm + Pod Identity)
    ├── vpn/   # GCP HA VPN ↔ AWS VGW, 터널 4개 + BGP
    └── dns/   # Route 53 Health Check + Failover 레코드 (앱 배포 후 활성화)
```

## 네트워크 대역

| 대역 | CIDR |
|---|---|
| GCP 노드 서브넷 | 10.10.0.0/20 |
| GKE 파드 / 서비스 | 10.11.0.0/16 / 10.12.0.0/20 |
| GKE 컨트롤 플레인 | 10.12.16.0/28 |
| Cloud SQL (PSA) | 10.13.0.0/20 (BGP로 AWS에 광고) |
| AWS VPC | 10.20.0.0/16 (private /20 ×2, public /24 ×2, database /24 ×2) |

## 사전 준비 (최초 1회)

```bash
# 1. 인증
gcloud auth application-default login
aws configure   # 또는 aws sso login

# 2. state 버킷 (버전 관리 켜기)
gcloud storage buckets create gs://<BUCKET> --location=asia-northeast3 --uniform-bucket-level-access
gcloud storage buckets update gs://<BUCKET> --versioning

# 3. 설정 파일
cp backend.hcl.example backend.hcl            # bucket 수정
cp terraform.tfvars.example terraform.tfvars  # gcp_project_id 수정
```

## 적용

```bash
terraform init -backend-config=backend.hcl
terraform plan -out tfplan
terraform apply tfplan

# kubeconfig 등록
$(terraform output -raw gke_get_credentials)
$(terraform output -raw eks_update_kubeconfig)
```

적용 후 확인:
- VPN 터널 4개 `ESTABLISHED`, BGP 세션 4개 `UP`: `$(terraform output -raw vpn_tunnel_status_command)`
- AWS 콘솔 → VPN 연결 → 터널 상태 `UP`

## 적용 후 수동 작업: 복제 연결 (담당: B)

Terraform은 양쪽 DB와 네트워크까지만 만든다. 복제 설정은 SQL로 한다.

```sql
-- [Cloud SQL] Publisher
ALTER ROLE payflow_admin WITH REPLICATION;
CREATE PUBLICATION payflow_pub FOR ALL TABLES;

-- [RDS] Subscriber  (테이블 스키마를 먼저 동일하게 생성해야 함)
CREATE SUBSCRIPTION payflow_sub
  CONNECTION 'host=<cloudsql_private_ip> dbname=payflow user=payflow_admin password=<...>'
  PUBLICATION payflow_pub;
```

- Cloud SQL 비밀번호: `terraform output -raw cloudsql_admin_password`
- RDS 비밀번호: `rds_master_secret_arn`이 가리키는 Secrets Manager 값
- RDS는 프라이빗이라 EKS 안의 임시 파드(`postgres` 이미지)에서 접속한다
- 복제 지연 확인 (Cloud SQL): `SELECT slot_name, confirmed_flush_lsn FROM pg_replication_slots;`

## DNS Failover 켜기 (D14 이후)

GKE Ingress IP는 Terraform이 고정 IP(`gke_ingress_ip`)로 미리 만든다. EKS ALB가 생긴 뒤 `terraform.tfvars`에서 `enable_dns_failover = true`와 ALB 값(`standby_alb_dns_name`, `standby_alb_zone_id`)을 채우고 다시 apply.

## AWS Load Balancer Controller

`modules/eks-addons`가 EKS에 Helm 차트(v3.5.0)로 설치한다. EKS Ingress(`ingressClassName: alb`)를 실제 ALB로 만들어 주는 컨트롤러다.

- 권한: 공식 IAM 정책(`iam-policy-lbc.json`, v3.5.0)을 EKS Pod Identity로 연결
- Helm 설치는 로컬 `aws` CLI로 EKS 토큰을 받으므로 `apply`하는 PC에 aws CLI 필요
- 차트 버전을 올리면 `iam-policy-lbc.json`도 같은 버전의 공식 파일로 교체
- 확인: `kubectl -n kube-system get deploy aws-load-balancer-controller`

### destroy 주의

ALB는 컨트롤러가 만든 것이라 Terraform이 모른다. **Ingress를 먼저 지우지 않으면 ALB가 남아 VPC 삭제가 실패한다.**

```bash
kubectl delete ingress --all -n payflow   # ArgoCD 앱이 있으면 앱 먼저 삭제
# ALB가 사라진 것 확인 후
terraform destroy
```

## 비용 관리

```bash
# 야간: 노드만 0으로 (DB·VPN은 유지 → 복제 재설정 불필요)
gcloud container clusters resize payflow-gke --node-pool primary --num-nodes 0 --location asia-northeast3-a
aws eks update-nodegroup-config --cluster-name payflow-eks --nodegroup-name $(aws eks list-nodegroups --cluster-name payflow-eks --query "nodegroups[0]" --output text) \
  --scaling-config minSize=0,maxSize=4,desiredSize=0
```

- 노드를 0으로 줄여도 계속 과금되는 것: NAT(양쪽), VPN 터널 4개, Cloud SQL, RDS, EKS 컨트롤 플레인
- `deletion_protection` 기본값은 `false` (프로젝트 기간 중 destroy 가능하게). 발표 리허설 기간에는 `true` 권장

## 원칙

- state는 원격(GCS)에만, `*.tfvars`·`backend.hcl`은 커밋 금지
- state 안에 DB 비밀번호가 평문으로 들어가므로 버킷 접근 권한은 팀원으로 제한
- PR마다 GitHub Actions가 `fmt -check` / `validate` 실행 (`.github/workflows/terraform.yml`)
