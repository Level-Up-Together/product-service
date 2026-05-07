-- user_db: 사용자 본체 + 친구 관계 + 약관 동의 + 블랙리스트 + MVP 제외
-- 사용법: psql -v ON_ERROR_STOP=1 -v uid="'<USER-UUID>'" -f user_db.sql

\echo === user_db: deleting data for :'uid' ===

BEGIN;

-- friendship: user_id 또는 friend_id 양쪽 매칭 (양방향 관계)
DELETE FROM friendship           WHERE user_id = :'uid' OR friend_id = :'uid';
DELETE FROM user_blacklist       WHERE user_id = :'uid';
DELETE FROM user_term_agreements WHERE user_id = :'uid';
DELETE FROM daily_mvp_exclusion  WHERE user_id = :'uid';

-- users 본체 (마지막)
DELETE FROM users                WHERE id = :'uid';

COMMIT;

\echo === user_db: done ===
