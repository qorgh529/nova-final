-- 복원 전 DB 권한 점검: 의심스러운 관리자(ADMIN) 계정을 찾는다.
--
-- 의심 기준
--   1. 승인된 관리자 명단(approved_admins)에 없는 ADMIN 계정
--   2. 침해 시작 시각(compromised_at) 이후에 생성된 ADMIN 계정
--
-- 한계: user_roles에는 권한 부여 시각이 없다. 침해 이전에 있던 계정에 나중에 ADMIN을 붙인 경우는
--       2번으로는 못 잡고 1번(명단 대조)으로만 잡힌다. 그래서 명단 대조가 기본 방어선이다.
--
-- 사용 (의심 계정이 있거나 변수가 빠지면 종료 코드 3, 이상 없으면 0)
--   psql "$DATABASE_URL" -v ON_ERROR_STOP=1 \
--     -v approved_admins='admin' \
--     -v compromised_at='2026-10-20T09:00:00Z' \
--     -f scripts/db/check-admin-accounts.sql

\set QUIET on
\set ON_ERROR_STOP on

\if :{?approved_admins}
\else
  \echo '필수 변수 누락: -v approved_admins=admin[,관리자2,...]'
  DO $$ BEGIN RAISE EXCEPTION 'missing variable: approved_admins'; END $$;
\endif
\if :{?compromised_at}
\else
  \echo '필수 변수 누락: -v compromised_at=<침해 시작 시각, 예: 2026-10-20T09:00:00Z>'
  DO $$ BEGIN RAISE EXCEPTION 'missing variable: compromised_at'; END $$;
\endif

CREATE TEMP TABLE suspicious_admins AS
WITH approved AS (
    SELECT trim(x) AS login_id
    FROM unnest(string_to_array(:'approved_admins', ',')) AS x
)
SELECT u.id,
       u.login_id,
       u.name,
       u.created_at,
       concat_ws(', ',
           CASE WHEN u.login_id NOT IN (SELECT login_id FROM approved) THEN '승인 명단에 없음' END,
           CASE WHEN u.created_at >= CAST(:'compromised_at' AS timestamptz) THEN '침해 시작 이후 생성' END
       ) AS reasons
FROM users u
JOIN user_roles r ON r.user_id = u.id AND r.role = 'ADMIN'
WHERE u.login_id NOT IN (SELECT login_id FROM approved)
   OR u.created_at >= CAST(:'compromised_at' AS timestamptz);

\set QUIET off
\echo '== 의심 관리자 계정 =='
SELECT * FROM suspicious_admins ORDER BY created_at;

SELECT count(*) > 0 AS found FROM suspicious_admins \gset
\if :found
  \echo '결과: 의심 관리자 계정 발견 → 복원 중단, 계정 확인 필요'
  -- ON_ERROR_STOP과 함께 psql 종료 코드 3을 낸다
  DO $$ BEGIN RAISE EXCEPTION 'suspicious admin accounts found'; END $$;
\else
  \echo '결과: 이상 없음'
\endif
