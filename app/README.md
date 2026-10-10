# 사내 서비스 (E1) — 로컬 실행

설계 문서: [`../docs/app/README.md`](../docs/app/README.md)

## 구성

- `api/` — Spring Boot (Java 21, Gradle Kotlin DSL)
- `web/` — React + Vite + TypeScript (로그인, 문서 목록·작성·상세, 결재, 첨부, 관리/무결성 검증)
- `docker-compose.yml` — api · web · postgres · minio

## 전체 실행 (Docker)

```bash
cd app
docker compose up --build
```

- API: http://localhost:8080  (헬스체크 `/actuator/health`, 버전 `/version`)
- Web: http://localhost:5173
- MinIO 콘솔: http://localhost:9001 (minioadmin / minioadmin)

## API만 로컬 실행

Postgres와 MinIO만 띄우고 API는 IDE/Gradle로:

```bash
cd app
docker compose up -d postgres minio minio-init
cd api
DB_URL=jdbc:postgresql://localhost:5432/nova \
NOVA_JWT_SECRET=dev-only-insecure-secret-change-me-32bytes!! \
./gradlew bootRun
```

## 개발용 기본 계정 (users 테이블이 비어 있을 때만 생성)

| loginId | 비밀번호 | 역할 |
|---|---|---|
| admin | password | ADMIN, EMPLOYEE |
| approver1 | password | APPROVER, EMPLOYEE |
| approver2 | password | APPROVER, EMPLOYEE |
| emp1 | password | EMPLOYEE |

운영에서는 `NOVA_SEED_DEV=false`로 끈다.

## 데모 프로파일 (`demo`)

발표·리허설용 기능이다. 운영 배포에서는 켜지 않는다.

```bash
SPRING_PROFILES_ACTIVE=demo ./gradlew bootRun     # 또는 컨테이너 환경변수로 지정
```

| 기능 | 동작 | 설정 (환경변수, 기본값) |
|---|---|---|
| 대량 시드 | 기동 후 사용자 `demo01`~`demo20`(5명마다 결재자, 비밀번호 `password`)과 문서를 만든다. 문서 상태는 기안 10%, 상신 대기 10%, 반려 10%, 일부 승인 10%, 최종 승인 60%. **모자란 만큼만** 채우므로 재기동해도 중복되지 않는다 | `NOVA_DEMO_SEED_USERS=20`, `NOVA_DEMO_SEED_DOCUMENTS=300` |
| EICAR 첨부 | 무해한 표준 백신 테스트 파일(`eicar-test.txt`)을 첨부한 문서 1건. 복원 전 첨부 스캔(ClamAV)이 걸러내야 하는 대상이다. 스토리지가 안 되면 경고만 남기고, 다음 기동 때 같은 문서에 다시 시도한다 | `NOVA_DEMO_SEED_EICAR=true` |
| 데이터 생성기 | 주기마다 `[GEN]` 결재 1건을 만들어 최종 승인까지 진행한다. **RPO = 사고 시각 − 복원된 DB의 마지막 `approval_history.acted_at`** | `NOVA_DEMO_GENERATOR_ENABLED=true`, `NOVA_DEMO_GENERATOR_INTERVAL=PT10S` |

- EICAR 문자열은 소스에 **뒤집어서** 저장하고 실행 시점에만 복원한다. 원문이 소스·jar에 그대로 있으면 개발자 PC 백신과 클린 파이프라인의 이미지 스캔이 앱 자체를 악성으로 잡기 때문이다
- 시드는 실제 API와 같은 `DocumentService`를 거치므로 해시 체인도 운영과 똑같이 쌓인다
- 관리자(ADMIN) 계정은 만들지 않는다. 권한 점검의 기준선은 기본 계정 `admin` 하나다

## 복원 검증 도구

복원 스크립트(E6)가 복원한 DB를 대상으로 실행한다. 둘 다 결과를 **종료 코드**로 알려서 스크립트가 다음 단계로 갈지 판단할 수 있다.

### 해시 체인 검증 CLI

같은 api jar(이미지)를 `verify-chain` 프로파일로 실행한다. 웹 서버 없이 검증만 하고 끝나며, 마이그레이션과 기본 계정 시드를 꺼서 **대상 DB를 바꾸지 않는다**.

```bash
DB_URL=jdbc:postgresql://<복원 DB>:5432/nova DB_USER=... DB_PASSWORD=... \
java -jar build/libs/nova-approval-api-0.1.0.jar --spring.profiles.active=verify-chain \
  --nova.cli.expected-head=<불변 백업 매니페스트에 기록한 체인 헤드 해시>
```

출력 (JSON 한 줄):
```json
{"exitCode":0,"chain":{"valid":true,"count":657,"headHash":"b4f9...","brokenAtSeq":null,"reason":null},"expectedHead":"b4f9...","expectedHeadFound":true}
```

| 종료 코드 | 의미 |
|---|---|
| 0 | 체인 정상 (expected-head를 줬다면 체인 안에 있음) |
| 10 | 체인 손상: 위변조된 첫 행이 `brokenAtSeq`, 사유가 `reason` |
| 11 | 체인은 자체적으로 맞지만 expected-head가 체인에 없음 → 체인 전체가 다시 계산돼 바꿔치기됐을 수 있음 |
| 1 | 기동 실패 (DB 접속 실패, 스키마 불일치 등) |

- `expected-head`가 핵심이다. 공격자가 체인 전체를 다시 계산해 넣으면 자체 일관성은 맞출 수 있으므로, 체인 밖(불변 저장소)에 둔 헤드 해시로 고정해야 잡힌다
- `/api/admin/integrity`와 같은 검증 로직(`ApprovalHistoryService.verify`)을 쓴다

### 관리자 계정 점검 SQL

[`../scripts/db/check-admin-accounts.sql`](../scripts/db/check-admin-accounts.sql): 승인 명단에 없거나 침해 시작 이후에 생긴 ADMIN 계정을 찾는다.

```bash
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 \
  -v approved_admins='admin' -v compromised_at='2026-10-20T09:00:00Z' \
  -f scripts/db/check-admin-accounts.sql
```

- 의심 계정이 있거나 변수가 빠지면 종료 코드 3, 이상 없으면 0
- 한계: `user_roles`에 권한 부여 시각이 없어서, 침해 이전부터 있던 계정에 나중에 ADMIN을 붙인 경우는 **명단 대조로만** 잡힌다

## 빠른 확인 (curl)

```bash
# 로그인 → 토큰
TOKEN=$(curl -s localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"loginId":"emp1","password":"password"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')

# 휴가 신청 작성 (결재자 2명)
curl -s localhost:8080/api/documents -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"type":"LEAVE","title":"연차","approverIds":[2,3],"leave":{"leaveType":"ANNUAL","startDate":"2026-10-20","endDate":"2026-10-21"}}'

# 상신 → 결재자 토큰으로 승인 → 관리자 토큰으로 무결성 검증(/api/admin/integrity)
```

## 빌드·테스트

```bash
cd api
./gradlew test          # 단위 테스트 + Testcontainers 통합 테스트
./gradlew bootJar       # 실행 가능한 jar
```

- **통합 테스트**는 Testcontainers로 Postgres를 띄운다 (스토리지는 인메모리 더블). **실행에는 Docker가 필요하다** (CI는 ubuntu-latest에서 동작)
  - `ApprovalE2EIntegrationTest`: 로그인 → 작성 → 상신 → 단계별 승인 → 무결성 검증, 첨부 업로드, 체인 위변조 탐지, 잘못된 결재자 403
  - `DemoProfileIntegrationTest`: demo 시드(상태 분포, EICAR 첨부), 데이터 생성기, 관리자 점검 SQL(컨테이너 안 psql로 실행), 체인 검증 CLI의 종료 코드

- 의존성 잠금: `gradle.lockfile` (공급망 방어). 의존성을 바꾸면 `./gradlew dependencies --write-locks`로 갱신한다
- SBOM: `./gradlew cyclonedxBom` → `build/reports/bom.json` (클린 파이프라인 Trivy 입력)

## 프론트엔드 (web)

```bash
cd app/web
npm ci
npm run dev     # http://localhost:5173 (API는 /api, /version을 :8080으로 프록시)
npm run build   # 타입체크 + dist 빌드
```

- 운영 이미지는 non-root nginx가 `dist`를 서빙하고 `/api`·`/version`을 api 컨테이너로 프록시한다 (`web/nginx.conf`).
- **로컬 첨부 다운로드 한계**: 다운로드는 S3 서명 URL(302)로 간다. compose에서 API의 S3 엔드포인트가 `minio:9000`이라 브라우저가 그 호스트를 못 찾는다. 브라우저에서 바로 받으려면 API를 로컬에서 띄우고 MinIO를 `localhost:9000`으로 두면 된다. (운영은 GCS/S3 공개 엔드포인트라 문제없음)

## 아직 안 된 것 (다음 작업)

- GCS 스토리지 드라이버 (현재 S3/MinIO만. 인터페이스는 준비됨)
- 백업 직전 `chain_checkpoints`에 체인 헤드 해시를 기록하는 코드 (체인 검증 CLI의 `expected-head` 출처)
- 테스트 보강: 반려 흐름, 첨부 다운로드
