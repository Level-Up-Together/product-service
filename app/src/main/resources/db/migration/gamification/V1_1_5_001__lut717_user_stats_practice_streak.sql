-- LUT-717: 연속 실천 일수 — 하루에 미션을 1개 이상 완료한 날의 연속 (유저 로컬 자정·완료 시각 기준).
-- 기존 current_streak/max_streak 은 출석이 섞인 "활동 스트릭"이라 그대로 두고 컬럼을 따로 둔다.
-- last_practice_date 가 NULL 인 유저(배포 전 가입)는 첫 조회 때 미션 완료 이력으로 재계산해 채운다 (UserStatsService.getPracticeStreak).
ALTER TABLE user_stats ADD COLUMN IF NOT EXISTS practice_streak INTEGER NOT NULL DEFAULT 0;
ALTER TABLE user_stats ADD COLUMN IF NOT EXISTS max_practice_streak INTEGER NOT NULL DEFAULT 0;
ALTER TABLE user_stats ADD COLUMN IF NOT EXISTS last_practice_date DATE;

COMMENT ON COLUMN user_stats.practice_streak IS '현재 연속 실천 일수 (LUT-717, 유저 로컬 날짜·미션 완료 기준)';
COMMENT ON COLUMN user_stats.max_practice_streak IS '최장 연속 실천 일수 (LUT-717)';
COMMENT ON COLUMN user_stats.last_practice_date IS '마지막 실천 날짜 (유저 로컬, LUT-717) — NULL 이면 첫 조회 때 이력으로 재계산';
