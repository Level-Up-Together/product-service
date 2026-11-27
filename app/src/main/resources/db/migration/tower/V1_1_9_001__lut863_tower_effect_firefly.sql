-- LUT-863: 반딧불 효과 레이어 — 종류 FIREFLY 추가(kind 는 VARCHAR(10), 제약 없음). 새 컬럼은 없다 — 기존 color·size_min·size_max 를 쓴다.
-- FIREFLY 는 amount = 밀도 1~100, speed = 떠오르는 속도 1~100(둘 다 50 = 기본), size_min·size_max = 한 마리 지름(배경 px 1~20, 비우면 2·5), color = 빛 색(#rrggbb 소문자, 비우면 #e8ff8a).
COMMENT ON COLUMN tower_floor_effect_layer.color IS 'POLLEN 기준 색 · FIREFLY 빛 색(#rrggbb 소문자) — 다른 종류는 NULL';
COMMENT ON COLUMN tower_floor_effect_layer.amount IS 'SNOW = 밀도(동시 눈송이 수), STARS = 별 개수, CLOUD = 짙기 0~100, RAIN = 맺힌 물방울 밀도 1~100, POLLEN = 밀도 1~100, RAINFALL = 밀도 1~100, FIREFLY = 밀도 1~100';
COMMENT ON COLUMN tower_floor_effect_layer.size_min IS 'SNOW·STARS·FIREFLY 크기 최소(1~20) — 다른 종류는 1';
COMMENT ON COLUMN tower_floor_effect_layer.size_max IS 'SNOW·STARS·FIREFLY 크기 최대(1~20, 최소 이상) — 다른 종류는 1';
COMMENT ON TABLE tower_floor_effect_layer IS '레벨업 탑 층 효과 레이어 (LUT-725 SNOW·STARS, LUT-740 CLOUD, LUT-785 RAIN, LUT-838 POLLEN, LUT-839 RAINFALL, LUT-863 FIREFLY — 종류당 1개)';
