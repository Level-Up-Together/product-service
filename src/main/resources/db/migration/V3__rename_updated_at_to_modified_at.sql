-- ============================================================
-- Migration: Rename updated_at to modified_at in all tables
-- ============================================================

-- ============================================================
-- Guild DB 테이블
-- ============================================================
ALTER TABLE guild RENAME COLUMN updated_at TO modified_at;
ALTER TABLE guild_member RENAME COLUMN updated_at TO modified_at;
ALTER TABLE guild_join_request RENAME COLUMN updated_at TO modified_at;
ALTER TABLE guild_level_config RENAME COLUMN updated_at TO modified_at;
ALTER TABLE guild_chat_message RENAME COLUMN updated_at TO modified_at;

-- ============================================================
-- Meta DB 테이블
-- ============================================================
ALTER TABLE level_config RENAME COLUMN updated_at TO modified_at;
