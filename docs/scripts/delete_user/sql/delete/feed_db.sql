-- feed_db: 활동 피드 + 댓글 + 좋아요 + 댓글 좋아요(LUT-691 추가)
-- 주의: 지운 좋아요·댓글만큼 다른 사용자 피드의 like_count/comment_count(비정규화)는 남는다.
--       댓글의 답글(parent_id CASCADE)은 다른 사용자 것도 함께 지워진다.
-- 사용법: psql -v ON_ERROR_STOP=1 -v uid="'<USER-UUID>'" -f feed_db.sql

\echo === feed_db: deleting data for :'uid' ===

BEGIN;

DELETE FROM feed_comment_like WHERE user_id = :'uid';   -- 이 사용자가 남의 댓글에 누른 좋아요 (남이 내 댓글에 누른 것은 댓글 삭제 시 CASCADE)
DELETE FROM feed_like         WHERE user_id = :'uid';
DELETE FROM feed_comment      WHERE user_id = :'uid';
DELETE FROM activity_feed     WHERE user_id = :'uid';

COMMIT;

\echo === feed_db: done ===
