\echo === chat_db: counting rows for :'uid' ===

SELECT 'guild_chat_message'      tbl, COUNT(*) cnt FROM guild_chat_message        WHERE sender_id = :'uid'
UNION ALL SELECT 'guild_chat_participant',  COUNT(*)  FROM guild_chat_participant     WHERE user_id   = :'uid'
UNION ALL SELECT 'guild_chat_read_status',  COUNT(*)  FROM guild_chat_read_status     WHERE user_id   = :'uid'
UNION ALL SELECT 'guild_direct_message',    COUNT(*)  FROM guild_direct_message       WHERE sender_id = :'uid'
UNION ALL SELECT 'guild_direct_conversation', COUNT(*) FROM guild_direct_conversation  WHERE user_id_1 = :'uid' OR user_id_2 = :'uid';
