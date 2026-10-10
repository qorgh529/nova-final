# Runbook: Failback (AWS → GCP)

> 초안. 22일 범위에서는 설계 문서 수준까지 다루고, 시간이 남으면 리허설한다.

1. GCP 환경 복구 확인 (클러스터, Cloud SQL)
2. AWS RDS(현재 Primary) → Cloud SQL 역방향 복제 구성
3. 복제 지연 0 근접 확인
4. 점검 시간 공지 후 AWS 쓰기 차단
5. Cloud SQL 승격, GKE 앱 쓰기 활성화
6. Route 53 Primary를 GCP로 복원
7. AWS RDS를 다시 Cloud SQL의 복제본으로 재구성
