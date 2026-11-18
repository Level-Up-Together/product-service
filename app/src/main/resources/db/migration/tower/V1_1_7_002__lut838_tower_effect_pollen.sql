-- LUT-838: 꽃가루 효과 레이어 — 종류 POLLEN 추가(kind 는 VARCHAR, 제약 없음). 기준 색 color(#rrggbb 소문자) — 화면이 이 색과 비슷한 계열 여러 색으로 그린다.
-- POLLEN 은 amount = 밀도 1~100, speed = 떨어지는 속도 1~100(둘 다 50 = 시안 그대로), 크기(size_min·size_max)는 쓰지 않는다(1 로 저장).
ALTER TABLE tower_floor_effect_layer ADD COLUMN IF NOT EXISTS color VARCHAR(7);
COMMENT ON COLUMN tower_floor_effect_layer.color IS 'POLLEN 기준 색(#rrggbb 소문자) — 다른 종류는 NULL';
COMMENT ON COLUMN tower_floor_effect_layer.amount IS 'SNOW = 밀도(동시 눈송이 수), STARS = 별 개수, CLOUD = 짙기 0~100, RAIN = 맺힌 물방울 밀도 1~100, POLLEN = 밀도 1~100';
COMMENT ON TABLE tower_floor_effect_layer IS '레벨업 탑 층 효과 레이어 (LUT-725 SNOW·STARS, LUT-740 CLOUD, LUT-785 RAIN, LUT-838 POLLEN — 종류당 1개)';
