\echo === mission_db: counting rows for :'uid' ===

SELECT 'mission'             tbl, COUNT(*) cnt FROM mission              WHERE creator_id = :'uid'
UNION ALL SELECT 'mission_template',   COUNT(*)  FROM mission_template     WHERE creator_id = :'uid'
UNION ALL SELECT 'mission_participant',COUNT(*)  FROM mission_participant  WHERE user_id    = :'uid'
UNION ALL SELECT 'mission_comment',    COUNT(*)  FROM mission_comment      WHERE user_id    = :'uid';
