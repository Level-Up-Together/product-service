-- guild_db: 길드 멤버 + 게시글 + 댓글 + 가입요청 + 초대 + 경험치 이력
-- 주의: 길드 마스터(guild.master_id)는 정책 결정 필요 — 기본 미삭제
-- 사용법: psql -v ON_ERROR_STOP=1 -v uid="'<USER-UUID>'" -f guild_db.sql

\echo === guild_db: deleting data for :'uid' ===

-- 사전 안내: 이 사용자가 마스터인 길드 목록 (수동 처리용)
\echo "[WARN] 다음 길드의 master_id가 해당 사용자입니다. 별도 정책 적용 필요:"
SELECT id, name, master_id FROM guild WHERE master_id = :'uid';

BEGIN;

DELETE FROM guild_post_comment        WHERE author_id      = :'uid';
DELETE FROM guild_post                WHERE author_id      = :'uid';
DELETE FROM guild_join_request        WHERE requester_id   = :'uid' OR processed_by = :'uid';
DELETE FROM guild_invitation          WHERE inviter_id     = :'uid' OR invitee_id   = :'uid';
DELETE FROM guild_member              WHERE user_id        = :'uid';
DELETE FROM guild_experience_history  WHERE contributor_id = :'uid';

-- ⚠️ 마스터인 길드 자체 삭제는 기본 비활성. 필요 시 주석 해제:
-- DELETE FROM guild WHERE master_id = :'uid';

COMMIT;

\echo === guild_db: done ===
