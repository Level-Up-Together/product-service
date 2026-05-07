-- mission_db: 미션 + 미션 참가자 + 미션 댓글 + 미션 템플릿
-- mission 자식 (daily_mission_instance, mission_state_history)은 ON DELETE CASCADE 가정
-- 사용법: psql -v ON_ERROR_STOP=1 -v uid="'<USER-UUID>'" -f mission_db.sql

\echo === mission_db: deleting data for :'uid' ===

BEGIN;

DELETE FROM mission_comment      WHERE user_id    = :'uid';
DELETE FROM mission_participant  WHERE user_id    = :'uid';
DELETE FROM mission              WHERE creator_id = :'uid';
DELETE FROM mission_template     WHERE creator_id = :'uid';

COMMIT;

\echo === mission_db: done ===
