# observability (담당: D)

- 각 클러스터에 Prometheus, Grafana는 AWS 쪽에 두고 데이터소스 2개로 통합
- Blackbox Exporter: 외부 엔드포인트 종단 간 헬스체크
- Slack 알림
- 대시보드: 요청 성공률, 지연 시간, **DB 복제 지연**, 클라우드별 트래픽 비율
