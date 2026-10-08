# 아키텍처 비교: v1 (Claude 초안) vs 참고안 (9단계 이미지) → v2

v2를 고르는 기준은 **"GCP 조직 수준 권한 탈취"라는 확정 시나리오에서도 버티는가**이다.
일반적인 DR 구성으로 보기 좋은지는 기준이 아니다.

## 총평

| 관점 | v1 | 참고안 | 시나리오 적합도 |
|---|---|---|---|
| 공격 경로 (공급망 → 키 탈취 → 지속성 → C2) | 표현함 | 없음 | v1 |
| 백업 데이터가 GCP에서 AWS로 넘어가는 경로 | Pull · Object Lock · 해시로 구체적 | 없음 (GCP 백업은 GCP 안에 있고, AWS Backup은 출처가 불명확) | v1 |
| 이미지 처리 | 사고 후 클린 빌드 | **GCP 이미지를 ECR로 사전 복제** | v1 (참고안은 시나리오와 충돌) |
| 사람의 판단·승인 단계 | 없음 | 분석과 승인(Human-in-the-loop) 단계가 있음 | **참고안** |
| 복구 자동화 | Terraform만 있음 | Recovery Orchestrator + Terraform | **참고안** |
| 시크릿 관리 | 없음 | Secret Manager / Secrets Manager | **참고안** (새로 발급한다는 원칙만 추가하면 됨) |
| 평시 CI 방어선 (SAST, 이미지 스캔, Binary Auth) | 없음 | 있음 | **참고안** ("다 갖췄는데도 뚫렸다"는 서사에 유리) |
| AWS 쪽 감시 (GuardDuty, CloudTrail, CloudWatch) | 없음 | 있음 | **참고안** |
| 진입 계층 (LB, WAF) | 없음 | 있음 | 참고안 (완성도 측면) |
| 앱 구조 | 웹 + API (CLAUDE.md 확정안) | 4개 MSA | v1 (가벼운 사내 서비스라는 합의 유지) |

**결론**: 뼈대는 v1이 시나리오에 더 맞는다. 참고안에서는 운영과 거버넌스 요소(승인, 오케스트레이터, 시크릿, 감시, 평시 방어선)를 가져와 v2로 합쳤다.

## 참고안에서 채택하지 않은 것과 그 이유

### 1. GCP Artifact Registry → AWS ECR "검증된 이미지 사전 복제 (Known-Good)"
이번 시나리오의 핵심 반론인 "같은 파이프라인이면 AWS도 감염되지 않나?"에 그대로 걸린다.
- 백도어는 **정상 파이프라인이 빌드하고 서명한** 이미지다. Binary Authorization을 통과했고 digest도 정상이다. SolarWinds처럼 잠복 기간이 있으면 언제부터 오염됐는지 알 수 없으므로, "Known-Good"이라고 보장할 수 없다
- 복제하려면 GCP가 ECR 쓰기 권한을 가져야 한다. 이는 "GCP 쪽에 AWS 키가 없도록 한다"는 원칙을 깨고, 조직 권한을 가진 공격자가 AWS로 넘어올 통로가 된다
- 그래서 v2에서는 사고가 난 뒤 **마지막으로 검증된 커밋에서 새로 빌드**한다. 다이어그램에도 "채택하지 않음"이라고 표시해 두어 발표 때 이 반론에 먼저 답할 수 있게 했다

### 2. Cloud SQL 자동 백업과 AWS Backup에 기대는 복원
- Cloud SQL 자동 백업은 GCP 안에 있으므로, 조직 권한을 가진 공격자가 지울 수 있다 (Code Spaces 사례)
- AWS Backup은 GCP 리소스를 백업하지 못한다. 그래서 v2는 **GCP Export 버킷 → AWS Pull → S3 Object Lock** 경로를 명시했다

### 3. 4개 서비스로 나눈 MSA (Auth, Employee, Board, Notification)
CLAUDE.md에서 "가벼운 사내 서비스, 웹 프론트 + API 서버"로 합의했다. MSA는 구현량만 늘고 멀티클라우드 필연성에는 기여하지 않는다.

### 4. 상시 대기(pilot-light) AWS 클러스터
참고안의 "최소 상태 → 필요 시 확장"은 평시에도 EKS 같은 리소스가 떠 있다는 뜻으로 읽힌다. 확정안은 "평시에는 백업 저장소만 두고 사고 시 IaC로 생성"이다. 이 선택이 "클라우드 두 개는 과하다"는 반론에 대한 비용 측면의 답이므로 유지한다. RTO가 목표에 못 미치면 그때 옵션으로 검토한다.

## 참고안에서 가져와 보완한 것 (v2 반영)

| 가져온 요소 | 그대로가 아니라 이렇게 보완함 |
|---|---|
| 관리자 분석 → 승인 (Human-in-the-loop) | **GCP 밖**의 "사고 대응 지휘" 구역에 배치했다. 승인 대상은 'GCP 신뢰 불가' 선언, 격리, DR 발동, **복원 시점 확정**이다. 분석 단계에서 **침해 시작 시점**을 추정하고, 이 시점이 복원 기준점이 된다 |
| Recovery Orchestrator + Terraform | AWS DR 계정 안에서 실행하며 GCP 자격증명은 쓰지 않는다. 단계별 타임스탬프를 자동으로 기록해서 **RTO 측정 근거**로 쓴다 |
| Secret Manager / Secrets Manager | GCP 시크릿은 전부 오염된 것으로 본다. AWS에서는 DB 비밀번호, API 키, JWT 서명 키까지 **모두 새로 발급**한다. JWT 키를 바꾸면 기존 세션이 모두 무효가 된다 |
| 평시 CI (Test, SAST, Image Scan) + Binary Authorization | "평시 방어선을 다 갖췄는데도 정상 빌드로 통과했다"는 메시지로 바꿔서 사용한다. 그래서 행위 기반 탐지와 클린 복구가 왜 필요한지 설명할 수 있다 |
| SCC (Security Command Center) | Event Threat Detection으로 SA 키 생성과 권한 상승 같은 행위를 탐지한다. 다만 공격자가 조직 권한으로 **SCC와 로깅을 끌 수 있으므로** 알림은 즉시 GCP 밖 채널로 보내고, 감사 로그 사본은 AWS가 pull해서 불변으로 보관한다 (포렌식용) |
| CloudTrail, GuardDuty, CloudWatch | 백업 계정에서는 CloudTrail과 EventBridge로 삭제나 정책 변경 시도를 알린다. DR 계정에서는 GuardDuty와 VPC Flow Logs로 복구 후 이상 통신과 재감염 여부를 검증한다 |
| ALB + WAF / Cloud Armor + LB | 진입 계층을 완성도 차원에서 추가했다 |
| Route 53 트래픽 전환 | **DNS는 GCP 밖**(Route 53 별도 계정 또는 Cloudflare)에 둔다. Cloud DNS를 쓰면 공격자가 DNS까지 장악할 수 있기 때문이다 |

## v2에서 새로 추가한 것 (두 안 모두에 없던 것)

- **AWS 계정을 백업용과 DR용으로 분리**: DR을 구축하다 자격증명이 노출돼도 백업 원본을 지울 수 없다
- **ECR 태그 불변, 배포 시 cosign 서명 검증, AWS KMS 신규 서명 키**: 클린 파이프라인이 내보낸 이미지만 실행되도록 막는다
- **감사 로그 사본을 AWS 쪽에 불변 보관**: 공격자가 GCP 로그를 지워도 포렌식 자료가 남는다
