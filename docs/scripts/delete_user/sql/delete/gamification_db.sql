-- gamification_db: 업적 + 칭호 + 경험치 + 출석 + MVP + 시즌 보상
-- 사용법: psql -v ON_ERROR_STOP=1 -v uid="'<USER-UUID>'" -f gamification_db.sql

\echo === gamification_db: deleting data for :'uid' ===

BEGIN;

DELETE FROM user_achievement          WHERE user_id = :'uid';
DELETE FROM user_title                WHERE user_id = :'uid';
DELETE FROM user_stats                WHERE user_id = :'uid';
DELETE FROM user_experience           WHERE user_id = :'uid';
DELETE FROM user_category_experience  WHERE user_id = :'uid';
DELETE FROM experience_history        WHERE user_id = :'uid';
DELETE FROM attendance_record         WHERE user_id = :'uid';
DELETE FROM season_reward_history     WHERE user_id = :'uid';
DELETE FROM daily_mvp_history         WHERE user_id = :'uid';
DELETE FROM daily_mvp_category_stats  WHERE user_id = :'uid';

COMMIT;

\echo === gamification_db: done ===
