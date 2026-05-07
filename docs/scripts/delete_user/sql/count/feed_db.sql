\echo === feed_db: counting rows for :'uid' ===

SELECT 'activity_feed' tbl, COUNT(*) cnt FROM activity_feed WHERE user_id = :'uid'
UNION ALL SELECT 'feed_comment',   COUNT(*)  FROM feed_comment  WHERE user_id = :'uid'
UNION ALL SELECT 'feed_like',      COUNT(*)  FROM feed_like     WHERE user_id = :'uid';
