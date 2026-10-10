# 상세 설계 (초안)

## 1. 범위

- **In scope:** GCP 전체 장애를 가정한 AWS 절체, 데이터 복제, GitOps 동시 배포, 통합 관제, 장애 훈련
- **Out of scope:** Active-Active, 양방향 쓰기, 실 결제망 연동, PCI-DSS 인증

## 2. DR 전략: Warm Standby

| 구분 | GCP (Primary) | AWS (Standby) |
|---|---|---|
| 클러스터 | GKE, 정규 replicas | EKS, 최소 replicas(1) + HPA |
| DB | Cloud SQL PostgreSQL (쓰기) | RDS PostgreSQL (읽기 전용 복제본) |
| 트래픽 | 100% | 0% (헬스체크 실패 시 100%) |
| 노드 | On-Demand | 최소 노드는 On-Demand (장애 시점 Spot 회수 위험 회피) |

## 3. 네트워크

- GCP HA VPN ↔ AWS Site-to-Site VPN (BGP), DB 복제 트래픽은 VPN 경유
- CIDR 사전 할당 (겹치지 않게):
  - GCP VPC: `10.10.0.0/16` (TBD)
  - AWS VPC: `10.20.0.0/16` (TBD)

## 4. 데이터 복제

- 방식: PostgreSQL logical replication (Cloud SQL `cloudsql.logical_decoding` 플래그 → RDS subscriber)
- 대안(복제가 막힐 경우): Debezium CDC 또는 주기적 덤프 — RPO 증가를 명시하고 발표
- 측정: 복제 지연(lag)을 Prometheus로 수집 → RPO 근거

### Split-brain 방지
1. DB 승격 전 GCP 쪽 쓰기 차단 (앱 read-only 전환 또는 GCP 방화벽 차단)
2. 복제 지연 확인 후 RDS 승격
3. 승격 이후에만 EKS 앱 쓰기 허용

## 5. 트래픽 절체

- Route 53 Failover 라우팅 + Health Check (GKE Ingress 대상)
- TTL 60초
- 헬스체크·절체는 Route 53 데이터 플레인에서 동작 → GCP 장애와 독립

## 6. 배포 (GitOps)

- GitHub Actions: 이미지 빌드 → Artifact Registry + ECR 동시 푸시 (DR 측 이미지 확보)
- ArgoCD: **AWS 쪽에 설치**, GKE·EKS를 멀티 클러스터로 등록
- Kustomize: `deploy/base` 공유 + `overlays/{gke,eks}`로 Ingress·StorageClass·replicas 차이만 분리

## 7. 관제

- Prometheus를 각 클러스터에 두고, Grafana(AWS 쪽)에서 데이터소스 2개로 통합 조회
- Blackbox Exporter: 외부 엔드포인트 종단 간 헬스체크
- 알림: Slack
- 핵심 대시보드: 요청 성공률, 지연 시간, 복제 지연, 클러스터별 트래픽 비율

## 8. 장애 시나리오

| # | 시나리오 | 주입 방법 | 기대 결과 |
|---|---|---|---|
| S1 | GCP 서비스 전체 불능 | GCP 방화벽 deny 규칙으로 Ingress 차단 | Route 53 절체 → EKS 응답 |
| S2 | GKE 컴퓨트 상실 | 노드풀 0 스케일 | 동일 |
| S3 | 파드 단위 장애 | Chaos Mesh pod-kill | **절체 없이** 자가 복구 (오탐 절체 방지 검증) |

## 9. 미결 사항

- [ ] DB 승격 자동화 수준 (완전 자동 vs 사람 승인)
- [ ] Failback 방식 (역방향 복제 vs 재구성)
- [ ] CIDR 최종 확정
