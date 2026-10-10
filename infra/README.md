# infra (담당: A)

Terraform으로 GCP·AWS 인프라를 프로비저닝한다.

- `gcp/`: VPC, GKE, Cloud SQL(PostgreSQL, logical decoding 활성화), HA VPN
- `aws/`: VPC, EKS, RDS(PostgreSQL), Site-to-Site VPN, Route 53 Health Check / Failover
- `modules/`: 공통 모듈

원칙
- state는 원격 백엔드(GCS 또는 S3)에 저장, 로컬 state 커밋 금지
- `*.tfvars`는 커밋하지 않고 `*.tfvars.example`만 커밋
- 최우선 작업: **VPN** (데이터 복제 트랙이 이것에 의존)
