-- LUT-860: 완료한 미션 수행 취소.
-- 취소한 경험치는 지금 레벨 안에서만 빼고(레벨은 내리지 않는다), 모자란 만큼은 exp_debt(누적 마이너스 경험치)에 쌓는다.
-- 다음 미션 경험치를 줄 때 exp_debt 를 먼저 갚고 남은 만큼만 준다.
ALTER TABLE user_experience ADD COLUMN IF NOT EXISTS exp_debt INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN user_experience.exp_debt IS '누적 마이너스 경험치 (LUT-860) — 미션 취소로 생기고 다음 미션 지급에서 먼저 갚는다';

-- 그날의 첫 실천·첫 활동이 스트릭을 바꾸기 직전 값. 그날 마지막 미션 완료를 취소하면 이 값으로 되돌린다.
-- 형식은 UserStats 의 PracticeUndoState·StreakUndoState 가 정한다(날짜|값|…).
ALTER TABLE user_stats ADD COLUMN IF NOT EXISTS practice_undo_state VARCHAR(200);
ALTER TABLE user_stats ADD COLUMN IF NOT EXISTS streak_undo_state VARCHAR(100);

COMMENT ON COLUMN user_stats.practice_undo_state IS '그날 첫 실천 직전의 연속 실천 값 (LUT-860, 취소 때 되돌리기용)';
COMMENT ON COLUMN user_stats.streak_undo_state IS '그날 첫 활동 직전의 활동 스트릭 값 (LUT-860, 취소 때 되돌리기용)';
