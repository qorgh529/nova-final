# apps (담당: C)

결제 시나리오용 경량 마이크로서비스.

| 서비스 | 역할 |
|---|---|
| `auth` | 토큰 발급·검증 |
| `payment` | 결제 승인 (DB 쓰기) |
| `order` | 주문 생성·조회 |

공통 요구사항
- `/healthz`(liveness), `/readyz`(readiness) 엔드포인트
- `/metrics` Prometheus 메트릭 노출
- 환경변수로 read-only 모드 전환 가능 (Failover fencing용)
- 실 결제 데이터 사용 금지, 더미 데이터만
