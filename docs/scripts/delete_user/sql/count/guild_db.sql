\echo === guild_db: counting rows for :'uid' ===

SELECT 'guild_member'         tbl, COUNT(*) cnt FROM guild_member             WHERE user_id        = :'uid'
UNION ALL SELECT 'guild_post',         COUNT(*)  FROM guild_post               WHERE author_id      = :'uid'
UNION ALL SELECT 'guild_post_comment', COUNT(*)  FROM guild_post_comment       WHERE author_id      = :'uid'
UNION ALL SELECT 'guild_join_request', COUNT(*)  FROM guild_join_request       WHERE requester_id   = :'uid' OR processed_by = :'uid'
UNION ALL SELECT 'guild_invitation',   COUNT(*)  FROM guild_invitation         WHERE inviter_id     = :'uid' OR invitee_id   = :'uid'
UNION ALL SELECT 'guild_exp_history',  COUNT(*)  FROM guild_experience_history WHERE contributor_id = :'uid'
UNION ALL SELECT 'guild(master)',      COUNT(*)  FROM guild                    WHERE master_id      = :'uid';
