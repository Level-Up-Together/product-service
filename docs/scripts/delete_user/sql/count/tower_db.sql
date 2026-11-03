\echo === tower_db: counting rows for :'uid' ===

SELECT 'tower_user_state' tbl, COUNT(*) cnt FROM tower_user_state WHERE user_id = :'uid';
SELECT 'tower_occupant' tbl, COUNT(*) cnt FROM tower_occupant WHERE user_id = :'uid';
