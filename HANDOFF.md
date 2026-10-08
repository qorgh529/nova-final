# 인수인계 문서 (다른 세션에서 이어서 작업하기)

> 작성 시점: 2026-10-08. 저장소: `qorgh529/nova-final`, 작업 브랜치: `claude/new-session-bhc7ce`, PR: #1 (main으로)
> 이 문서는 대화 기록 없이도 이어서 작업할 수 있도록 현재 상태와 결정 사항, 함정, 다음 할 일을 정리한 것이다.
> **먼저 읽을 것**: 저장소 루트의 `CLAUDE.md` (팀 합의 사항·확정 결정. 자동 로드됨), 그다음 이 문서.

---

## 0. 새 세션 시작 체크리스트

1. `git fetch origin claude/new-session-bhc7ce && git checkout claude/new-session-bhc7ce && git pull`
2. 마지막 커밋이 `ac93de8` 이후인지 확인 (`git log --oneline -5`)
3. **최신 커밋의 CI 결과부터 확인** (아래 4절 참고. 마지막 수정의 CI 결과는 아직 확인하지 못했다)
4. `CLAUDE.md`의 "추가 확정 사항"은 **이미 합의된 결정**이다. 뒤집지 말고 보완하는 방향으로 진행한다
5. PR 만들기·푸시 규칙: 사용자가 요청할 때만 PR을 만든다. 푸시는 지정 브랜치(`claude/new-session-bhc7ce`)에만 한다

---

## 1. 프로젝트 한 줄 요약

**멀티클라우드 사이버 복구 프로젝트.** CI/CD 공급망 공격으로 GCP 조직 권한이 탈취되어 GCP를 신뢰할 수 없게 되면,
신원 체계가 분리된 AWS의 불변 백업과 **새로 빌드한** 이미지로 사내 서비스(전자결재 + 휴가 신청)를 복구한다.
핵심 과제 조건은 "반드시 멀티클라우드를 써야 하는 시나리오"이고, 우리는 "보안 침해로 인한 신뢰 상실"을 골랐다.

- 팀: 채정훈(시나리오 제안, 사고 사례 자료 보유), 김성현(아키텍처 초안, 컨플루언스 정리)
- 대상 앱: 가벼운 사내 서비스. 웹 프론트 + API 서버 + PostgreSQL + 첨부파일 스토리지

---

## 2. 확정된 설계 결정 (전부 `CLAUDE.md`와 `docs/`에 상세 근거 있음)

| 영역 | 결정 | 이유(요약) |
|---|---|---|
| AWS 계정 | **3개로 분리**: 백업(항상 존재) / DNS(항상 존재) / DR(평시엔 비용 0원 복구 제어 영역만) | DR 구축 중 자격증명이 새도 백업·DNS는 안전. AWS Organizations, 관리 계정엔 워크로드 없음 |
| DNS | DNS 전용 AWS 계정의 **Route 53**. 자동 failover 없이 관리자 승인 후 수동 전환. TTL 60초 | 침해된 GCP도 헬스체크에 '정상' 응답하므로 자동 전환 불가. Cloud DNS·Cloudflare 안 씀 |
| 이미지 | GCP→ECR **사전 복제 안 함**. 사고 시 클린 빌드 | 오염 시작 시점을 알 수 없고, 복제하려면 GCP에 AWS 쓰기 권한이 필요 |
| 컴퓨팅 | **GKE Standard**(GCP) + **EKS**(AWS DR) | Cloud Run은 Falco·포렌식 스냅샷 불가, Autopilot은 Falco 불확실. EKS는 같은 매니페스트 재사용 |
| CI/CD | 평시 **Cloud Build** / 사고 시 **AWS CodeBuild** / PR 테스트 **GitHub Actions**(클라우드 자격증명 없음) | 평시 빌드가 침해 범위 안에 있어야 "AWS에 클린 파이프라인이 따로 필요"가 선다 |
| 백업 | AWS **Lambda(Python)**가 GCP Export 버킷을 **pull**. WIF 읽기 전용. SHA-256 이중 확인. S3 Object Lock(Compliance) | GCP에 AWS 키를 두지 않음 |
| 복구 오케스트레이터 | CodeBuild의 Make 스크립트 + Step Functions(순서·병렬·승인 대기·시각 기록) 혼합. 제어 영역은 평시에 미리 둠 | RTO 단축, 평시 복구 리허설 가능 |
| 앱 | Spring Boot(Java 21, Gradle) + React/Vite, 컨테이너 2개, **앱 자체 로그인(JWT)** | Google SSO는 쓰지 않음(AWS 복구 후에도 Google 의존 방지) |
| 모의 백도어 | **Gradle 의존성**에 주입, 빌드 시점+런타임 두 단계 | 양쪽 탐지(Audit Logs/SCC, Falco/Flow Logs) 모두 시연 |

### 모의 백도어 안전 원칙 (반드시 지킬 것)
- 실제 악성 기능 금지. 비콘은 **호스트 이름·타임스탬프만**
- `DEMO_BACKDOOR=on` 킬 스위치, 격리된 데모 GCP 프로젝트 + 제한 권한 서비스 계정, 내부 전용 C2
- 코드·커밋에 `SIMULATED / DEMO ONLY` 표기, 데모 후 제거
- 상세: `docs/app/README.md` 7절

---

## 3. 저장소 구조

```
CLAUDE.md                     프로젝트 메모리 (확정 결정). 자동 로드됨
HANDOFF.md                    이 문서
.github/workflows/pr-test.yml PR 테스트 (api: JDK21 단위+통합테스트+bootJar / web: Node22 타입체크·빌드). 자격증명 없음
docs/
  architecture/
    README.md                 구역·설계 원칙 9개·컴퓨팅/CI·CD/백업/오케스트레이터 결정 근거
    architecture.drawio       아키텍처 다이어그램 v2 (app.diagrams.net에서 열기)
    comparison.md             v1 vs 참고안(9단계 이미지) 비교, 채택/미채택 이유
  app/README.md               앱 설계: 데이터 모델·API·데모 기능·백도어 안전 통제·E1 작업 목록
app/
  README.md                   로컬 실행·curl 예시·빌드·테스트 방법
  docker-compose.yml          api · web · postgres · minio
  api/                        Spring Boot (Gradle Kotlin DSL, gradle.lockfile, Flyway V1)
  web/                        React + Vite + TypeScript (package-lock.json, nginx.conf)
```

---

## 4. 구현 현황 (E1: 사내 서비스)

| 작업 | 상태 | 비고 |
|---|---|---|
| E1-1 골격 (Gradle, 의존성 잠금, SBOM, compose) | ✅ | |
| E1-2 Flyway 스키마 (8 테이블) | ✅ | `ddl-auto=validate` |
| E1-3 JWT 로그인·역할 | ✅ | 개발 시드: admin/approver1/approver2/emp1, 비밀번호 `password` (ID 1~4) |
| E1-4 결재 흐름 (작성→상신→단계별 승인/반려) | ✅ | |
| E1-5 휴가 신청 | ✅ | |
| E1-6 결재 이력 해시 체인 + `/api/admin/integrity` | ✅ | seq 직접 부여 + advisory lock 직렬화 |
| E1-7 첨부파일 + `StorageService`(S3/MinIO 드라이버) | ✅ | **GCS 드라이버는 아직 없음** |
| E1-8 `/version`, Actuator health | ✅ | |
| E1-9 프론트엔드 | ✅ | 로그인·목록·작성·상세·승인/반려·첨부·관리(무결성 검증) |
| 통합 테스트 (Testcontainers) | ⚠️ **진행 중** | 아래 참고 |
| E1-10 데모 프로파일 | ❌ | 데이터 생성기, 대량 시드, 권한 점검 SQL, 체인 검증 CLI |
| GCS 스토리지 드라이버 | ❌ | |

### ⚠️ 통합 테스트 현재 상태 (가장 먼저 확인할 것)
- `ApprovalE2EIntegrationTest`(결재 흐름, 잘못된 결재자 403, 첨부 업로드, 해시 체인 위변조 탐지)를 추가했다
- 첫 CI 실행이 **실패**했다. 원인: MinIO Testcontainer에 고정한 이미지 태그(`RELEASE.2024-06-13T22-53-53Z`)가 Docker Hub에 없어 pull 404
- 수정(`ac93de8`): MinIO 컨테이너를 없애고 **인메모리 `StorageService` 더블**(`TestStorageConfig`)로 대체. Postgres 컨테이너만 사용
- **수정 후 CI 결과는 아직 확인하지 못했다.** 새 세션은 `ac93de8`의 CI를 가장 먼저 확인할 것
  - 또 실패하면 `mcp__github__get_job_logs`(job_id 지정, `return_content=true`)로 원인을 본다. `gh run view --log`와 아티팩트 다운로드는 이 환경의 프록시에 막힌다
  - 테스트는 **로컬에서 실행할 수 없다**(이 환경에는 Docker 데몬이 없음). 컴파일 확인(`./gradlew compileTestJava`)만 가능하고 실행은 CI에서 한다
- 아직 한 번도 통과한 적 없는 코드이므로, 실패 시 테스트 코드와 앱 코드 양쪽을 의심할 것 (특히 `TestStorageConfig`가 `S3StorageService`와 충돌하는지, `nova.storage.driver=memory` 조건)

---

## 5. 환경 함정 (이미 겪은 문제)

- **Docker 데몬 없음**: `docker compose up`, Testcontainers 모두 이 환경에서 실행 불가. CI(GitHub Actions)에서만 검증된다. 그래서 compose 연동 end-to-end와 브라우저 동작은 **한 번도 실제로 확인하지 못했다**
- **Maven Central 429**: 의존성을 처음 받을 때 rate limit이 걸릴 수 있다. 코드 문제가 아니므로 20초 간격으로 재시도하면 된다. `cyclonedxBom` 태스크가 의존성을 전부 받으려 해서 자주 걸리니 로컬 테스트는 `-x cyclonedxBom`로 제외한다
- **GitHub는 `gh` CLI 대신 MCP 도구**(`mcp__github__*`)를 쓰는 것이 원칙이다. `gh api`는 일부 동작했지만 로그·아티팩트는 막힌다
- **스탠드얼론 `sleep` 금지**: 대기는 until-루프나 폴링으로 한다
- Docker Hub는 프록시 때문에 태그 존재 확인이 안 된다. 외부 이미지 태그를 고정할 때는 흔한 안정 태그(`postgres:16` 등)만 쓸 것
- 의존성 변경 시 `./gradlew dependencies --write-locks`로 `gradle.lockfile`을 갱신한다. 웹은 `npm ci` 기준이라 `package-lock.json`을 항상 커밋한다
- CI는 push와 pull_request 이벤트로 **두 번씩** 돈다(중복). 기능상 문제는 없고, 거슬리면 `push` 트리거를 지우면 된다

---

## 6. 진행 중인 PR 운영

- PR: https://github.com/qorgh529/nova-final/pull/1 (head `claude/new-session-bhc7ce` → base `main`)
- 원래 설계 문서용 PR이었지만 같은 브랜치라 구현 커밋도 함께 올라갔다. 설계와 구현을 분리하려면 구현 커밋을 별도 브랜치로 나눠야 한다 (사용자에게 아직 확인하지 못한 사항)
- PR 활동 구독(`subscribe_pr_activity`)과 안전망 점검 예약(`send_later`)이 걸려 있다. **예약은 세션에 묶여 있어서 새 세션에는 이어지지 않을 수 있다.** 새 세션에서 필요하면 다시 구독할 것
- 커밋 메시지 끝에는 시스템이 지정한 `Co-Authored-By`·`Claude-Session` 줄을 붙인다. PR 본문 끝에는 `🤖 Generated with [Claude Code](https://claude.com/claude-code)`와 세션 링크를 붙인다

---

## 7. 다음에 할 일 (우선순위 순)

1. **`ac93de8`의 CI 확인** → 실패하면 원인 파악·수정. 통과하면 통합 테스트가 처음으로 검증된 것
2. **E1-10 데모 프로파일** (`demo` 프로파일에서만 켠다)
   - 데이터 생성기 (`@Scheduled`로 결재 건 자동 생성·상신·승인 → RPO 측정)
   - 시드 데이터: 사용자 20명, 문서 수백 건, EICAR 테스트 파일이 든 첨부 1건
   - 권한 점검 SQL (`scripts/` 아래): 승인된 관리자 명단에 없거나 침해 시점 이후에 생긴 ADMIN 계정 탐지
   - 체인 검증 CLI (복원 스크립트에서 호출)
3. **GCS `StorageService` 드라이버** (`nova.storage.driver=gcs`, GCP 배포용)
4. **백업 지원 코드**: 백업 직전에 `chain_checkpoints`에 체인 헤드 해시를 기록하는 로직 (현재 테이블만 있고 기록하는 코드가 없다)
5. 다른 에픽(E2~E7)은 아직 시작하지 않았다
   - E2 GCP 운영 환경(Terraform, Cloud Build, GKE), E3 격리 백업(Lambda), E4 침해 시뮬레이션, E5 격리 런북, E6 클린 DR(Terraform, 오케스트레이터), E7 발표
6. 저장소 밖 할 일(`CLAUDE.md` 남은 할 일): 채정훈님 자료 공유받기, 컨플루언스 정리, **근거 사례 원문 확인**(UniSuper, Code Spaces, SolarWinds, 3CX, tj-actions/changed-files), 도메인·등록기관 확보

---

## 8. 알려진 한계·주의 (솔직한 현황)

- 컴파일과 **해시 체인 단위 테스트**만 확실히 통과했다. 결재 흐름·첨부·보안 설정은 통합 테스트가 통과하기 전까지 **동작이 검증되지 않았다**
- 프론트엔드는 타입체크·빌드만 통과했고, 실제 API와 붙여 브라우저에서 동작시킨 적은 없다
- 로컬 compose에서 첨부 다운로드(서명 URL이 `minio:9000`)는 브라우저에서 열리지 않는다 (`app/README.md`에 기록됨)
- 로그인 토큰을 `localStorage`에 둔다(데모용). 운영이라면 httpOnly 쿠키 등을 검토해야 한다
- 개발용 시드 계정(비밀번호 `password`)은 운영에서 반드시 끈다 (`nova.seed.dev=false`)
- 아키텍처 다이어그램(XML)은 유효성만 확인했고 렌더링 결과를 눈으로 확인하지 못했다. 겹치는 요소가 있을 수 있다
- 비용·가격 수치(GKE/EKS 요금 등)는 대략치다. 확정 전에 요금 계산기로 확인한다
- 근거 사례는 발표 전 **원문 기사로 사실 확인이 필요**하다
