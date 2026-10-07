# 아키텍처

- 원본: [`architecture.drawio`](./architecture.drawio). [app.diagrams.net](https://app.diagrams.net)에서 열거나 VS Code Draw.io 확장으로 편집하고, Confluence draw.io 매크로로 가져올 수 있다
- 번호 ①~⑦은 `CLAUDE.md`에 있는 발표 스토리 순서와 같다

## 구역

| 구역 | 역할 | 평시 상태 |
|---|---|---|
| **GCP 조직** | 주 운영 환경. CI/CD, 사내 서비스, 탐지, 격리 | 운영 중 |
| **AWS 백업 계정** | 데이터만 불변 보관 (Object Lock Compliance) | 항상 존재 (저장 비용만 발생) |
| **AWS DR 계정** | 클린 빌드와 복원으로 서비스 재가동 | 평시에는 없음. 사고 시 Terraform으로 생성 |

AWS 계정을 **백업용과 DR용으로 나눈다**. DR 환경을 만드는 과정에서 자격증명이 노출되더라도 백업 원본은 지워지지 않게 하려는 목적이다. DR 계정에는 백업 버킷에 대한 교차 계정 **읽기 전용** 권한만 준다.

## 핵심 설계 결정

1. **Pull 방식 백업**: AWS 백업 계정의 스케줄 작업이 Workload Identity Federation으로 GCP Export 버킷을 읽기 전용으로 가져온다. 그래서 GCP에는 AWS 자격증명이 없고, 장기 키도 남지 않는다
2. **데이터만 백업**: DB 덤프, 첨부파일, IaC 코드만 백업한다. 컨테이너 이미지와 바이너리는 오염됐을 수 있으므로 백업하지 않는다
3. **무결성 해시**: 백업할 때 SHA-256 매니페스트를 기록하고 복원할 때 다시 비교한다
4. **클린 빌드 파이프라인**: GCP 파이프라인과 신원·실행 환경을 공유하지 않는다. 마지막으로 검증된 커밋, 의존성 lock, SBOM, Trivy 스캔, cosign 서명을 거친다. 서명이 확인된 이미지만 ECR에서 배포한다
5. **침해 이전 시점 복원**: 첨부파일 악성코드 스캔과 DB 권한 테이블 점검을 통과한 백업만 RDS와 S3로 복원한다
6. **행위 기반 탐지**: VPC Flow Logs, Cloud Audit Logs, Falco를 쓴다. 신종 백도어는 시그니처로 잡히지 않는다는 전제다

## 미확정 (다이어그램에 "A / B"로 표기)

- GKE와 Cloud Run 중 무엇을 쓸지
- EKS와 ECS 중 무엇을 쓸지
- Cloud Build와 GitHub Actions 중 무엇을 쓸지
- 백업 Pull 작업을 Lambda와 ECS Task 중 무엇으로 실행할지 (덤프 크기와 실행 시간 15분 제한을 고려)
- DNS를 Route 53과 Cloudflare 중 무엇으로 할지
