\echo === notification_db: counting rows for :'uid' ===

SELECT 'notification' tbl, COUNT(*) cnt FROM notification             WHERE user_id = :'uid'
UNION ALL SELECT 'notification_preference', COUNT(*) FROM notification_preference  WHERE user_id = :'uid'
UNION ALL SELECT 'device_token',            COUNT(*) FROM device_token             WHERE user_id = :'uid';
