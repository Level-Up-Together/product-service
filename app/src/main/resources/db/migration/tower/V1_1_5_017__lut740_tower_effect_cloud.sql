-- LUT-740: 구름(안개) 효과 레이어 — 종류 CLOUD 추가(kind 는 VARCHAR, 제약 없음). 번짐 spread = 칠한 영역 바깥으로 옅게 퍼지는 거리(원본 px 0~200).
-- CLOUD 는 amount = 짙기 0~100, speed = 흐름 속도 0~100, 크기(size_min·size_max)는 쓰지 않는다(1 로 저장).
ALTER TABLE tower_floor_effect_layer ADD COLUMN IF NOT EXISTS spread INTEGER;
COMMENT ON COLUMN tower_floor_effect_layer.spread IS 'CLOUD 번짐(원본 px 0~200) — SNOW·STARS 는 NULL';
COMMENT ON COLUMN tower_floor_effect_layer.amount IS 'SNOW = 밀도(동시 눈송이 수), STARS = 별 개수, CLOUD = 짙기 0~100';
