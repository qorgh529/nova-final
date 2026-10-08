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

- **통합 테스트**(`ApprovalE2EIntegrationTest`)는 Testcontainers로 Postgres와 MinIO를 띄워 결재 흐름을
  end-to-end 검증한다: 로그인 → 작성 → 상신 → 단계별 승인 → 무결성 검증, 첨부 업로드·다운로드 왕복,
  체인 위변조 탐지, 잘못된 결재자 403. **실행에는 Docker가 필요하다** (CI는 ubuntu-latest에서 동작).

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

- E1-10 데모 프로파일 (데이터 생성기, 대량 시드, 권한 점검 SQL, 체인 검증 CLI)
- GCS 스토리지 드라이버 (현재 S3/MinIO만. 인터페이스는 준비됨)
- Testcontainers 통합 테스트 (결재 흐름 end-to-end)
