-- admin_db: 1:1 문의 + 추천 플레이어 + (선택) 신고 이력
-- 신고 이력(content_report)은 감사용 보존 권장 — 기본 미삭제
-- 사용법: psql -v ON_ERROR_STOP=1 -v uid="'<USER-UUID>'" -f admin_db.sql

\echo === admin_db: deleting data for :'uid' ===

BEGIN;

DELETE FROM customer_inquiry  WHERE user_id = :'uid';
DELETE FROM featured_player   WHERE user_id = :'uid';

-- ⚠️ 신고 이력 삭제는 기본 비활성. 필요 시 주석 해제:
-- DELETE FROM content_report WHERE reporter_id = :'uid' OR target_user_id = :'uid';

COMMIT;

\echo === admin_db: done ===
