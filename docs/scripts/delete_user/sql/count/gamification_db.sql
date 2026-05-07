\echo === gamification_db: counting rows for :'uid' ===

SELECT 'user_achievement'         tbl, COUNT(*) cnt FROM user_achievement         WHERE user_id = :'uid'
UNION ALL SELECT 'user_title',                COUNT(*) FROM user_title                WHERE user_id = :'uid'
UNION ALL SELECT 'user_stats',                COUNT(*) FROM user_stats                WHERE user_id = :'uid'
UNION ALL SELECT 'user_experience',           COUNT(*) FROM user_experience           WHERE user_id = :'uid'
UNION ALL SELECT 'user_category_experience',  COUNT(*) FROM user_category_experience  WHERE user_id = :'uid'
UNION ALL SELECT 'experience_history',        COUNT(*) FROM experience_history        WHERE user_id = :'uid'
UNION ALL SELECT 'attendance_record',         COUNT(*) FROM attendance_record         WHERE user_id = :'uid'
UNION ALL SELECT 'season_reward_history',     COUNT(*) FROM season_reward_history     WHERE user_id = :'uid'
UNION ALL SELECT 'daily_mvp_history',         COUNT(*) FROM daily_mvp_history         WHERE user_id = :'uid'
UNION ALL SELECT 'daily_mvp_category_stats',  COUNT(*) FROM daily_mvp_category_stats  WHERE user_id = :'uid';
