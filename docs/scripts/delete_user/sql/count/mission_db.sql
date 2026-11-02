\echo === mission_db: counting rows for :'uid' ===

SELECT 'mission'             tbl, COUNT(*) cnt FROM mission              WHERE creator_id = :'uid'
UNION ALL SELECT 'mission_template',   COUNT(*)  FROM mission_template     WHERE creator_id = :'uid'
UNION ALL SELECT 'mission_participant',COUNT(*)  FROM mission_participant  WHERE user_id    = :'uid'
UNION ALL SELECT 'mission_comment',    COUNT(*)  FROM mission_comment      WHERE user_id    = :'uid'
UNION ALL SELECT 'mission_idempotency_key', COUNT(*) FROM mission_idempotency_key WHERE user_id = :'uid'
UNION ALL SELECT 'mission_state_history(triggered_by)', COUNT(*) FROM mission_state_history WHERE triggered_by = :'uid'
UNION ALL SELECT 'mission_state_history(created missions)', COUNT(*) FROM mission_state_history
          WHERE mission_id IN (SELECT id FROM mission WHERE creator_id = :'uid');
