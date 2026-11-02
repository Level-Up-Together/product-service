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
UNION ALL SELECT 'daily_mvp_category_stats',  COUNT(*) FROM daily_mvp_category_stats  WHERE user_id = :'uid'
UNION ALL SELECT 'user_item',                 COUNT(*) FROM user_item                 WHERE user_id = :'uid'
UNION ALL SELECT 'item_grant',                COUNT(*) FROM item_grant                WHERE user_id = :'uid'
UNION ALL SELECT 'item_push_send_log',        COUNT(*) FROM item_push_send_log        WHERE user_id = :'uid'
UNION ALL SELECT 'user_diamond',              COUNT(*) FROM user_diamond              WHERE user_id = :'uid'
UNION ALL SELECT 'diamond_history',           COUNT(*) FROM diamond_history           WHERE user_id = :'uid'
UNION ALL SELECT 'subscription_stipend',      COUNT(*) FROM subscription_stipend      WHERE user_id = :'uid'
-- 아래 3개는 결제 기록 — 지우지 않는다(LUT-691 결정 2026-11-02). 참고용 건수.
UNION ALL SELECT '[보존] user_subscription',             COUNT(*) FROM user_subscription             WHERE user_id = :'uid'
UNION ALL SELECT '[보존] subscription_payment_history',  COUNT(*) FROM subscription_payment_history  WHERE user_id = :'uid'
UNION ALL SELECT '[보존] diamond_bundle_purchase',       COUNT(*) FROM diamond_bundle_purchase       WHERE user_id = :'uid';
