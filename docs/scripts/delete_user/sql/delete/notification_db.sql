-- notification_db: 알림 + 알림 설정 + FCM 디바이스 토큰
-- 사용법: psql -v ON_ERROR_STOP=1 -v uid="'<USER-UUID>'" -f notification_db.sql

\echo === notification_db: deleting data for :'uid' ===

BEGIN;

DELETE FROM device_token            WHERE user_id = :'uid';
DELETE FROM notification            WHERE user_id = :'uid';
DELETE FROM notification_preference WHERE user_id = :'uid';

COMMIT;

\echo === notification_db: done ===
