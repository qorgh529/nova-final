# 아키텍처 (v2)

- 원본: [`architecture.drawio`](./architecture.drawio). [app.diagrams.net](https://app.diagrams.net)에서 열거나 VS Code Draw.io 확장으로 편집하고, Confluence draw.io 매크로로 가져올 수 있다
- 참고안(9단계 이미지)과 비교한 내용과 무엇을 채택하고 버렸는지: [`comparison.md`](./comparison.md)
- 번호 ①~⑦은 `CLAUDE.md`에 있는 발표 스토리 순서와 같다

## 구역

| 구역 | 역할 | 평시 상태 |
|---|---|---|
| **GCP 조직** | 주 운영 환경. 평시 CI/CD, 사내 서비스, 행위 기반 탐지, 격리 런북 | 운영 중 |
| **사고 대응 지휘** | 알림 수신, 분석, 승인(Human-in-the-loop), DNS 전환. **GCP 밖에서 수행** | 알림 채널과 DNS만 상시 운영 |
| **AWS 백업 계정** | 데이터만 불변 보관 (Object Lock Compliance) | 항상 존재 (저장 비용만 발생) |
| **AWS DR 계정** | 클린 빌드, 오케스트레이터, 복원으로 서비스 재가동 | 평시에는 없음. 사고 시 Terraform으로 생성 |

## 핵심 설계 원칙

1. **사고 시 GCP에서 나온 것은 데이터 말고는 아무것도 믿지 않는다**: 이미지, 시크릿, 서명 키, DNS, 로그 파이프라인이 모두 해당된다
2. **Pull 방식 백업**: AWS 백업 계정이 Workload Identity Federation으로 GCP Export 버킷을 읽기 전용으로 가져온다. GCP에는 AWS 자격증명이 없다
3. **데이터만 백업**: DB 덤프, 첨부파일, IaC 코드, 감사 로그 사본만 대상이다. 컨테이너 이미지는 백업하지도 사전 복제하지도 않는다
4. **무결성 해시**: 백업할 때 SHA-256 매니페스트를 기록하고 복원할 때 다시 비교한다
5. **클린 빌드 파이프라인**: AWS 안의 러너에서 마지막으로 검증된 커밋을 빌드한다. 의존성 lock, SBOM, Trivy, SAST를 거치고 AWS KMS 신규 키로 cosign 서명한다. ECR은 태그를 불변으로 두고, 배포 시 서명을 검증한다
6. **사람이 승인한다**: GCP '신뢰 불가' 선언, 격리, DR 발동, 복원 시점 확정은 관리자 승인을 거친다. 승인 이후의 단계는 오케스트레이터가 자동으로 실행하고 타임스탬프를 기록한다 (RTO 측정)
7. **시크릿은 새로 발급한다**: DB 비밀번호, API 키, JWT 서명 키를 모두 AWS Secrets Manager와 KMS에서 새로 만든다
8. **탐지가 무력화될 것에 대비한다**: 공격자는 조직 권한으로 로깅과 SCC를 끌 수 있다. 그래서 알림은 즉시 외부 채널로 보내고, 감사 로그 사본은 AWS에 불변으로 보관한다
9. **DNS는 GCP 밖에 둔다**: Route 53(별도 계정) 또는 Cloudflare를 쓴다

## 미확정 (다이어그램에 "A / B"로 표기)

- GKE와 Cloud Run 중 무엇을 쓸지
- EKS와 ECS 중 무엇을 쓸지
- Cloud Build와 GitHub Actions 중 무엇을 쓸지
- 백업 Pull 작업을 Lambda와 ECS Task 중 무엇으로 실행할지 (덤프 크기와 실행 시간 15분 제한을 고려)
- DNS를 Route 53과 Cloudflare 중 무엇으로 할지
- 복구 오케스트레이터 구현 방식: Step Functions로 할지, AWS 러너에서 셸이나 Make 스크립트로 할지
