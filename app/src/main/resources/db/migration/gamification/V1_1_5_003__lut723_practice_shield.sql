-- LUT-723: 구독 혜택 — 연속 실천 방어권(매달 2개, 유저 로컬 달). 미션을 놓친 날(연속 최대 2일)이 끝날 때 1개 써서 연속을 잇는다.
-- 방어권은 달마다 다시 2개(이월 없음) — practice_shield_month 가 지금 달이 아니면 사용 0 으로 본다(따로 채우는 작업 없음).
-- 방어된 날은 실천한 날이 아니다(practice_streak 에 더하지 않는다).
ALTER TABLE user_stats ADD COLUMN IF NOT EXISTS practice_shield_month VARCHAR(7);
ALTER TABLE user_stats ADD COLUMN IF NOT EXISTS practice_shields_used INTEGER NOT NULL DEFAULT 0;
ALTER TABLE user_stats ADD COLUMN IF NOT EXISTS last_practice_shield_date DATE;

COMMENT ON COLUMN user_stats.practice_shield_month IS '방어권 사용을 센 달 (유저 로컬 YYYY-MM, LUT-723) — 지금 달이 아니면 사용 0';
COMMENT ON COLUMN user_stats.practice_shields_used IS 'practice_shield_month 에 쓴 방어권 수 (최대 2, LUT-723)';
COMMENT ON COLUMN user_stats.last_practice_shield_date IS '마지막으로 방어된 날 (유저 로컬, LUT-723)';
