# chaos (담당: D)

| # | 시나리오 | 주입 방법 | 기대 결과 |
|---|---|---|---|
| S1 | GCP 서비스 전체 불능 | GCP 방화벽 deny 규칙 | AWS 절체 |
| S2 | GKE 컴퓨트 상실 | 노드풀 0 스케일 | AWS 절체 |
| S3 | 파드 장애 | Chaos Mesh pod-kill | 절체 없이 자가 복구 |

Chaos Mesh는 클러스터 내부 장애만 재현한다. CSP 장애는 S1·S2로 흉내 낸다.
리허설마다 결과를 `docs/runbook/failover.md` 실측 시간 열에 기록한다.
