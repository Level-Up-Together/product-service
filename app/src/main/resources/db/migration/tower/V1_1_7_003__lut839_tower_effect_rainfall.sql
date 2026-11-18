-- LUT-839: 비 내림 효과 레이어 — 종류 RAINFALL 추가(kind 는 VARCHAR, 제약 없음). 유리창에 맺히는 RAIN 과 다른 효과다(빗줄기가 위에서 아래로 내린다).
-- RAINFALL 은 amount = 밀도 1~100, speed = 내리는 속도 1~100(둘 다 50 = 기본), 크기(size_min·size_max)는 쓰지 않는다(1 로 저장).
-- direction = STRAIGHT(일자) / SLANTED(사선), angle = 사선일 때 수직에서 기운 각도(도) — 양수 = 오른쪽 아래로, 음수 = 왼쪽 아래로, 절댓값 5~45.
ALTER TABLE tower_floor_effect_layer ADD COLUMN IF NOT EXISTS direction VARCHAR(10);
ALTER TABLE tower_floor_effect_layer ADD COLUMN IF NOT EXISTS angle INTEGER;
COMMENT ON COLUMN tower_floor_effect_layer.direction IS 'RAINFALL 방향 STRAIGHT(일자)/SLANTED(사선) — 다른 종류는 NULL';
COMMENT ON COLUMN tower_floor_effect_layer.angle IS 'RAINFALL 사선 각도(도, 양수 = 오른쪽 아래로·음수 = 왼쪽 아래로, 절댓값 5~45) — 일자와 다른 종류는 NULL';
COMMENT ON COLUMN tower_floor_effect_layer.amount IS 'SNOW = 밀도(동시 눈송이 수), STARS = 별 개수, CLOUD = 짙기 0~100, RAIN = 맺힌 물방울 밀도 1~100, POLLEN = 밀도 1~100, RAINFALL = 밀도 1~100';
COMMENT ON TABLE tower_floor_effect_layer IS '레벨업 탑 층 효과 레이어 (LUT-725 SNOW·STARS, LUT-740 CLOUD, LUT-785 RAIN, LUT-838 POLLEN, LUT-839 RAINFALL — 종류당 1개)';
