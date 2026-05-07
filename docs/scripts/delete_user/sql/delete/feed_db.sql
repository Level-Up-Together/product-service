-- feed_db: 활동 피드 + 댓글 + 좋아요
-- 사용법: psql -v ON_ERROR_STOP=1 -v uid="'<USER-UUID>'" -f feed_db.sql

\echo === feed_db: deleting data for :'uid' ===

BEGIN;

DELETE FROM feed_like      WHERE user_id = :'uid';
DELETE FROM feed_comment   WHERE user_id = :'uid';
DELETE FROM activity_feed  WHERE user_id = :'uid';

COMMIT;

\echo === feed_db: done ===
