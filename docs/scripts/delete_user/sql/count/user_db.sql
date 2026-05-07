-- user_db: 영향 row 수 사전 확인
\echo === user_db: counting rows for :'uid' ===

SELECT 'users'                tbl, COUNT(*) cnt FROM users               WHERE id      = :'uid'
UNION ALL SELECT 'friendship',         COUNT(*)    FROM friendship           WHERE user_id = :'uid' OR friend_id = :'uid'
UNION ALL SELECT 'user_blacklist',     COUNT(*)    FROM user_blacklist       WHERE user_id = :'uid'
UNION ALL SELECT 'user_term_agreements', COUNT(*)  FROM user_term_agreements WHERE user_id = :'uid'
UNION ALL SELECT 'daily_mvp_exclusion', COUNT(*)   FROM daily_mvp_exclusion  WHERE user_id = :'uid';
