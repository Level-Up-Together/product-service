-- chat_db: 채팅 메시지 + 참여자 + 읽음 상태 + DM
-- 사용법: psql -v ON_ERROR_STOP=1 -v uid="'<USER-UUID>'" -f chat_db.sql

\echo === chat_db: deleting data for :'uid' ===

BEGIN;

DELETE FROM guild_chat_read_status     WHERE user_id   = :'uid';
DELETE FROM guild_chat_participant     WHERE user_id   = :'uid';
DELETE FROM guild_chat_message         WHERE sender_id = :'uid';
DELETE FROM guild_direct_message       WHERE sender_id = :'uid';
DELETE FROM guild_direct_conversation  WHERE user_id_1 = :'uid' OR user_id_2 = :'uid';

COMMIT;

\echo === chat_db: done ===
