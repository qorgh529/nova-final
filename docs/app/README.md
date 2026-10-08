# 사내 서비스 설계 (E1): 전자결재 + 휴가 신청

이 아키텍처 위에서 운영하고 복구할 대상 애플리케이션의 설계 문서다.
목표는 **완성도 높은 그룹웨어가 아니다.** 복구 시나리오의 각 단계(탐지, 격리, 복원, 검증)가
데모에서 눈에 보이도록 만드는 데 필요한 만큼만 구현한다.

- 아키텍처: [`../architecture/README.md`](../architecture/README.md)
- 프로젝트 전제: [`../../CLAUDE.md`](../../CLAUDE.md)

## 1. 선정 이유

결재 문서는 **위변조되면 안 되는 업무 기록**이다. 그래서 다음 세 가지가 왜 필요한지 자연스럽게 설명할 수 있다.

- 침해 이전 시점으로 복원해야 하는 이유
- 복원 후 무결성을 검증해야 하는 이유
- DB 권한 테이블을 점검해야 하는 이유

| 후보 | 적합도 | 판단 |
|---|---|---|
| **전자결재 + 휴가 신청** | 높음 | 권한, 첨부파일, 이력 무결성이 모두 있어 복구 검증 데모의 재료가 많다 |
| 사내 위키 / 게시판 | 중간 | 권한과 무결성 이야기가 약하다 |
| 사내 메신저 | 낮음 | 실시간 통신이 복잡하고 데모의 초점이 흐려진다 |
| 쇼핑몰 / 티켓 예매 | 낮음 | 사내 서비스가 아니고, 팀이 버린 "대형 트래픽" 시나리오와 겹친다 |

## 2. 시나리오와 연결되는 기능

| 기능 | 연결 단계 | 데모 포인트 |
|---|---|---|
| 역할(직원, 결재권자, 관리자) | ⑤ DB 권한 테이블 점검 | 모의 백도어가 DB에 **숨은 관리자 계정**을 심는다. 복원 전 점검 쿼리가 이 계정을 찾아낸다 (IAM 지속성 장치의 앱 버전) |
| 결재 이력 해시 체인 | ⑥ 데이터 무결성 확인 | 복원 후 체인을 재계산하고, **불변 백업에 기록된 체인 헤드 해시**와 비교한다 |
| 첨부파일 업로드 | ⑤ 첨부파일 스캔 | EICAR 테스트 파일(무해한 표준 테스트 파일)을 섞어 두고, 복원 때 ClamAV가 걸러낸다 |
| `/version` | ⑤ 클린 빌드 검증 | 이미지 digest와 빌드 커밋을 보여준다. 전환 후 "클린 파이프라인이 서명한 이미지"가 돌고 있음을 화면으로 증명한다 |
| 데이터 생성기 | ⑥ RPO 측정 | 결재 건을 주기적으로 자동 생성한다. 사고 시각과 복원된 마지막 레코드 시각의 차이가 RPO다 |
| 자체 로그인 (JWT) | ⑤ 시크릿 재발급 | AWS에서 JWT 서명 키를 새로 발급하면 기존 세션이 전부 무효가 된다 |

## 3. 구성

```
[브라우저] ─▶ web (React 정적 파일, nginx) ─▶ api (Spring Boot) ─┬─▶ PostgreSQL (Cloud SQL / RDS)
                                                                  └─▶ 오브젝트 스토리지 (GCS / S3)
```

- **컨테이너 2개**(`web`, `api`)만 둔다. MSA로 나누지 않는다
- `api`는 상태를 갖지 않는다 (세션은 JWT, 파일은 오브젝트 스토리지). 그래서 GKE와 EKS 양쪽에 같은 매니페스트로 배포할 수 있다
- 같은 이미지가 GCP와 AWS 양쪽에서 돌아야 한다. 클라우드별 차이는 **환경변수와 스토리지 드라이버**로만 흡수한다

## 4. 기술 스택

| 영역 | 선택 | 비고 |
|---|---|---|
| 언어 / 런타임 | Java 21 (LTS) | |
| 프레임워크 | Spring Boot 3.x | Web, Validation, Data JPA, Security, Actuator |
| 인증 | Spring Security + OAuth2 Resource Server (JWT) | 토큰은 앱이 직접 발급한다. 서명 키는 시크릿 저장소에서 주입한다 |
| DB 마이그레이션 | Flyway | 복원한 덤프의 스키마 버전을 확인하는 근거로도 쓴다 |
| DB | PostgreSQL 16 | Cloud SQL / RDS |
| 스토리지 | `StorageService` 인터페이스 + GCS, S3 드라이버 | 로컬 개발은 MinIO(S3 호환)를 쓴다 |
| 빌드 | Gradle (Kotlin DSL) | **dependency locking**과 **verification-metadata.xml**(의존성 체크섬 검증)을 켠다 |
| SBOM | CycloneDX Gradle 플러그인 | 클린 파이프라인에서 Trivy 스캔 입력으로 쓴다 |
| 빌드 정보 | `springBoot { buildInfo() }` + git-properties 플러그인 | `/version`과 Actuator `info`로 노출한다 |
| 이미지 | Dockerfile, distroless(Java 21) 기반, non-root | 셸이 없는 이미지라 "Java 프로세스가 셸을 실행" 같은 Falco 탐지가 명확해진다 |
| 프론트엔드 | React + Vite + TypeScript | `npm ci`와 `package-lock.json`으로 의존성을 고정한다. nginx-unprivileged로 서빙한다 |
| 테스트 | JUnit 5, Testcontainers (PostgreSQL, MinIO) | |

### 꼭 지킬 규칙

1. **Google 계정 SSO를 쓰지 않는다.** 로그인이 Google 신원 체계에 묶이면 AWS로 복구해도 Google에 의존하게 된다
2. **클라우드 SDK는 스토리지 드라이버 안에서만** 쓴다. 다른 코드에는 GCP나 AWS 의존성을 두지 않는다
3. **설정은 모두 환경변수와 시크릿으로 주입한다**: DB 주소와 계정, 버킷 이름, JWT 키, 스토리지 드라이버 종류
4. **스키마 변경은 Flyway로만** 한다. JPA의 `ddl-auto`는 `validate`로 둔다

## 5. 데이터 모델

```
users            (id, login_id, password_hash, name, department, created_at)
user_roles       (user_id, role)                       -- EMPLOYEE / APPROVER / ADMIN
documents        (id, type, title, body, status, drafter_id, created_at, updated_at)
                                                       -- type: APPROVAL / LEAVE (비품 신청은 추후 EQUIPMENT)
                                                       -- status: DRAFT / SUBMITTED / APPROVED / REJECTED
leave_details    (document_id, leave_type, start_date, end_date, days)
approval_lines   (document_id, step, approver_id, status, acted_at)
approval_history (seq, document_id, actor_id, action, acted_at, prev_hash, hash)
attachments      (id, document_id, object_key, file_name, content_type, size, sha256, uploaded_at)
chain_checkpoints(seq, head_hash, created_at)          -- 백업 직전에 기록하는 체인 헤드
```

### 결재 이력 해시 체인

- `approval_history`는 **전역 단일 체인**이다. 각 행의 해시는 다음과 같이 계산한다
  `hash = SHA-256(prev_hash ‖ seq ‖ document_id ‖ actor_id ‖ action ‖ acted_at)`
- INSERT만 허용한다. 애플리케이션 DB 계정에는 이 테이블에 대한 UPDATE와 DELETE 권한을 주지 않는다
- **해시 체인만으로는 부족하다.** DB 권한을 가진 공격자는 체인 전체를 다시 계산해서 조작할 수 있다. 그래서 백업할 때 `chain_checkpoints`의 최신 헤드 해시를 **백업 매니페스트에 함께 기록**하고, 이 매니페스트를 AWS가 pull해서 Object Lock 버킷에 보관한다. 복원 후 검증은 이 불변 매니페스트의 헤드 해시와 비교한다

## 6. API

| 메서드 | 경로 | 설명 | 권한 |
|---|---|---|---|
| POST | `/api/auth/login` | 로그인, JWT 발급 | 공개 |
| GET | `/api/me` | 내 정보와 역할 | 로그인 |
| GET | `/api/documents` | 내가 쓴 문서와 내가 결재할 문서 목록 | 로그인 |
| POST | `/api/documents` | 결재 문서 또는 휴가 신청 작성 (결재선 포함) | 로그인 |
| GET | `/api/documents/{id}` | 문서 상세, 결재선, 이력 | 관련자 |
| POST | `/api/documents/{id}/submit` | 상신 | 기안자 |
| POST | `/api/documents/{id}/approve` | 승인 | 해당 단계 결재권자 |
| POST | `/api/documents/{id}/reject` | 반려 | 해당 단계 결재권자 |
| POST | `/api/documents/{id}/attachments` | 첨부파일 업로드 (SHA-256 기록) | 기안자 |
| GET | `/api/attachments/{id}` | 다운로드 (서명된 URL로 리다이렉트) | 관련자 |
| GET | `/api/admin/users` | 사용자와 역할 관리 | ADMIN |
| GET | `/api/admin/integrity` | 해시 체인 검증 결과 | ADMIN |
| GET | `/version` | 이미지 digest, 빌드 커밋, 빌드 시각, 실행 클라우드 | 공개 |
| GET | `/actuator/health` | 헬스체크 (liveness / readiness) | 공개 |

`/version`의 이미지 digest는 앱이 스스로 알 수 없다. 배포 매니페스트에서 `IMAGE_DIGEST` 환경변수로 넣어 준다.

## 7. 데모 지원 기능

데모 기능은 운영 코드와 섞이지 않게 **`demo` 프로파일에서만** 켠다.

| 기능 | 구현 | 쓰는 단계 |
|---|---|---|
| 데이터 생성기 | `@Scheduled`로 N초마다 결재 건을 생성하고 상신·승인한다 | ⑥ RPO 측정 |
| 시드 데이터 | 사용자 20명, 문서 수백 건, EICAR 테스트 파일이 포함된 첨부 1건 | ⑤ 첨부파일 스캔 |
| 권한 점검 쿼리 | `ADMIN` 역할 중 승인된 관리자 명단에 없거나, 침해 시작 이후에 생긴 계정을 찾는다 (`scripts/` 아래 SQL) | ⑤ DB 권한 점검 |
| 체인 검증 | `/api/admin/integrity`와 같은 로직을 CLI로도 실행할 수 있게 한다 (복원 스크립트에서 호출) | ⑥ 무결성 확인 |

### 모의 백도어 주입 지점 (E4에서 확정)

| 후보 | 방식 | 보이는 탐지 |
|---|---|---|
| **Gradle 의존성** (권장) | 가짜 내부 라이브러리의 새 버전이 앱 시작 시 셸을 띄워 모의 C2로 신호를 보내고, 메타데이터 서버에서 토큰을 읽고, DB에 숨은 관리자 계정을 만든다 | Falco(Java 프로세스의 셸 실행), VPC Flow Logs(C2 통신), Audit Logs(토큰으로 IAM 변경) |
| npm `postinstall` | 프론트엔드를 빌드할 때 CI 환경에서 실행된다 | 빌드 단계라서 런타임 탐지가 잘 드러나지 않는다 |

- 실제 악성 기능은 넣지 않는다 (CLAUDE.md 원칙). C2는 팀이 운영하는 모의 서버이고, 신호에는 호스트 이름만 담는다
- 데모는 **전용 GCP 프로젝트와 권한이 제한된 데모용 서비스 계정**으로만 한다. 실제 조직 관리자 권한은 쓰지 않는다

## 8. 저장소 구조 (예정)

```
app/
  api/                  Spring Boot (Gradle)
    src/main/java/...   auth, document, approval, attachment, storage, integrity, demo
    src/main/resources/db/migration/   Flyway SQL
    gradle/verification-metadata.xml
    Dockerfile
  web/                  React + Vite
    Dockerfile
  docker-compose.yml    로컬: api, web, postgres, minio
scripts/
  restore/              권한 점검 SQL, 체인 검증 실행
```

## 9. E1 작업 목록

| # | 작업 | 완료 조건 |
|---|---|---|
| E1-1 | 프로젝트 골격 | Gradle 멀티 설정, dependency locking과 verification-metadata 켜기, `docker-compose up`으로 api, web, postgres, minio가 뜬다 |
| E1-2 | 스키마와 마이그레이션 | Flyway V1이 위 데이터 모델을 만들고, `ddl-auto=validate`가 통과한다 |
| E1-3 | 로그인과 역할 | 로그인하면 JWT가 발급되고, 역할별 접근 제어 테스트가 통과한다. JWT 키는 환경변수로 주입한다 |
| E1-4 | 결재 문서 CRUD와 결재 흐름 | 작성 → 상신 → 단계별 승인/반려가 동작하고, 상태 전이 테스트가 통과한다 |
| E1-5 | 휴가 신청 | `LEAVE` 문서로 작성과 결재가 된다. 기간과 일수를 검증한다 |
| E1-6 | 해시 체인 | 모든 결재 행위가 체인에 기록되고, 행 하나를 조작하면 `/api/admin/integrity`가 실패한다 |
| E1-7 | 첨부파일과 스토리지 드라이버 | MinIO(S3)와 GCS 드라이버로 업로드와 다운로드가 되고, SHA-256이 저장된다 |
| E1-8 | `/version`과 헬스체크 | 커밋, 빌드 시각, `IMAGE_DIGEST`, 실행 클라우드가 나온다 |
| E1-9 | 프론트엔드 | 로그인, 문서 목록, 작성, 상세(결재선과 이력), 승인/반려, 첨부 화면이 있고, 화면에 `/version` 정보를 표시한다 |
| E1-10 | 데모 프로파일 | 데이터 생성기, 시드 데이터, 권한 점검 SQL, 체인 검증 CLI가 있다 |
| E1-11 | 컨테이너 이미지 | distroless와 non-root 기반 api 이미지, nginx-unprivileged 기반 web 이미지가 있고, CycloneDX SBOM이 생성된다 |

비품 신청(`EQUIPMENT`)은 휴가 신청과 구조가 같다. 시간이 남으면 E1-5와 같은 방식으로 추가한다.

## 10. 아직 정하지 않은 것

- Spring Boot 마이너 버전 (팀 개발 환경에 맞춰 3.x 최신 안정판으로)
- 결재선 규칙: 고정 2단계로 할지, 작성자가 지정하게 할지 (MVP는 작성자 지정을 권장)
- 프론트엔드 UI 라이브러리 (MUI, Ant Design, Tailwind 중 팀 선호)
- 모의 백도어 주입 지점 확정 (E4)
