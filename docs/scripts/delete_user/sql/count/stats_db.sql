\echo === stats_db: counting rows for :'uid' ===

SELECT 'user_daily_activity' tbl, COUNT(*) cnt FROM user_daily_activity WHERE user_id = :'uid';
