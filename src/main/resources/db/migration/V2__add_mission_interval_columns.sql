-- ============================================================
-- Migration: Add mission_interval and experience columns to mission table
-- ============================================================

-- mission_interval 컬럼 추가
ALTER TABLE mission ADD COLUMN IF NOT EXISTS mission_interval VARCHAR(20) DEFAULT 'DAILY';

-- 경험치 관련 컬럼 추가
ALTER TABLE mission ADD COLUMN IF NOT EXISTS exp_per_completion INTEGER DEFAULT 10;
ALTER TABLE mission ADD COLUMN IF NOT EXISTS bonus_exp_on_full_completion INTEGER DEFAULT 50;
ALTER TABLE mission ADD COLUMN IF NOT EXISTS guild_exp_per_completion INTEGER DEFAULT 5;
ALTER TABLE mission ADD COLUMN IF NOT EXISTS guild_bonus_exp_on_full_completion INTEGER DEFAULT 20;

-- 인덱스 추가
CREATE INDEX IF NOT EXISTS idx_mission_interval ON mission(mission_interval);

-- mission_execution 테이블에 exp_earned 컬럼 추가
ALTER TABLE mission_execution ADD COLUMN IF NOT EXISTS exp_earned INTEGER DEFAULT 0;
