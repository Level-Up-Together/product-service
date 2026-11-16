-- LUT-785: 물방울(유리창 빗물) 효과 레이어 — 종류 RAIN 추가(kind 는 VARCHAR, 제약 없음). 흐르는 물방울 수 drop_count(0~120).
-- RAIN 은 amount = 맺힌 물방울 밀도 1~100, speed = 흐르는 속도 1~100(둘 다 50 = 시안 그대로), 크기(size_min·size_max)는 쓰지 않는다(1 로 저장).
ALTER TABLE tower_floor_effect_layer ADD COLUMN IF NOT EXISTS drop_count INTEGER;
COMMENT ON COLUMN tower_floor_effect_layer.drop_count IS 'RAIN 흐르는 물방울 수(0~120) — 다른 종류는 NULL';
COMMENT ON COLUMN tower_floor_effect_layer.amount IS 'SNOW = 밀도(동시 눈송이 수), STARS = 별 개수, CLOUD = 짙기 0~100, RAIN = 맺힌 물방울 밀도 1~100';
COMMENT ON TABLE tower_floor_effect_layer IS '레벨업 탑 층 효과 레이어 (LUT-725 SNOW·STARS, LUT-740 CLOUD, LUT-785 RAIN — 종류당 1개)';
