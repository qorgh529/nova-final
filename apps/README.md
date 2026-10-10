# apps (담당: C)

결제 시나리오용 경량 마이크로서비스.

| 서비스 | 역할 |
|---|---|
| `auth` | 토큰 발급·검증 |
| `payment` | 결제 승인 (DB 쓰기) |
| `order` | 주문 생성·조회 |

공통 요구사항 (`deploy/base` 매니페스트와 맞춰야 함)
- 포트 `8080`
- `/healthz`(liveness), `/readyz`(readiness) 엔드포인트
- `/metrics` Prometheus 메트릭 노출
- 환경변수: `CLOUD`, `APP_MODE`, `DB_HOST`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`
- `APP_MODE`
  - `primary`: 읽기·쓰기
  - `standby`: 읽기만 (쓰기 요청은 503)
  - `readonly`: Failover fencing용 쓰기 차단 (동작은 standby와 같음)
- 루트 파일시스템 읽기 전용(`readOnlyRootFilesystem`), UID 10001로 실행 → 임시 파일이 필요하면 emptyDir 사용
- 실 결제 데이터 사용 금지, 더미 데이터만
