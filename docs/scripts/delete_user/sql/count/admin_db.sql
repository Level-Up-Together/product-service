\echo === admin_db: counting rows for :'uid' ===

SELECT 'customer_inquiry'  tbl, COUNT(*) cnt FROM customer_inquiry WHERE user_id        = :'uid'
UNION ALL SELECT 'featured_player',   COUNT(*)  FROM featured_player  WHERE user_id        = :'uid'
UNION ALL SELECT 'content_report(reporter)',  COUNT(*) FROM content_report WHERE reporter_id    = :'uid'
UNION ALL SELECT 'content_report(target)',    COUNT(*) FROM content_report WHERE target_user_id = :'uid';
