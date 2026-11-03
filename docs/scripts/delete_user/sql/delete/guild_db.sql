-- guild_db: 길드 멤버 + 일별 포인트(LUT-483) + 게시글 + 댓글 + 가입요청 + 초대 + 경험치 이력
-- 주의: 길드 마스터(guild.master_id)는 정책 결정 필요 — 기본 미삭제
-- LUT-691: 다른 사람 행을 가리키는 "약한 참조"는 지우지 않고 NULL 로 바꾼다 —
--   guild_join_request.processed_by(이 사용자가 처리한 남의 요청), guild_experience_history.contributor_id(길드의 이력).
--   예전 스크립트는 둘 다 DELETE 해서 남의 가입 요청·길드 경험치 이력까지 없앴다.
-- 사용법: psql -v ON_ERROR_STOP=1 -v uid="'<USER-UUID>'" -f guild_db.sql

\echo === guild_db: deleting data for :'uid' ===

-- 사전 안내: 이 사용자가 마스터인 길드 목록 (수동 처리용)
\echo "[WARN] 다음 길드의 master_id가 해당 사용자입니다. 별도 정책 적용 필요:"
SELECT id, name, master_id FROM guild WHERE master_id = :'uid';

BEGIN;

-- 자식 댓글(parent_id CASCADE)까지 함께 지워진다 — 다른 사용자의 답글 포함
DELETE FROM guild_post_comment        WHERE author_id      = :'uid';
DELETE FROM guild_post                WHERE author_id      = :'uid';
DELETE FROM guild_join_request        WHERE requester_id   = :'uid';
UPDATE guild_join_request SET processed_by = NULL WHERE processed_by = :'uid';
DELETE FROM guild_invitation          WHERE inviter_id     = :'uid' OR invitee_id   = :'uid';
DELETE FROM guild_member_daily_point  WHERE user_id        = :'uid';   -- LUT-691 추가
DELETE FROM guild_member              WHERE user_id        = :'uid';
UPDATE guild_experience_history SET contributor_id = NULL WHERE contributor_id = :'uid';

-- ⚠️ 마스터인 길드 자체 삭제는 기본 비활성. 필요 시 주석 해제:
-- DELETE FROM guild WHERE master_id = :'uid';

COMMIT;

\echo === guild_db: done ===
