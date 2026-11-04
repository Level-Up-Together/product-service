-- LUT-725: 층 효과 레이어(불빛 점·눈·별)와 앞 가림 영역. 좌표는 배경 원본 픽셀, 마스크는 배경과 같은 비율의 PNG(4분의 1 해상도).
ALTER TABLE tower_floor ADD COLUMN IF NOT EXISTS occlusion_mask_url VARCHAR(500);
COMMENT ON COLUMN tower_floor.occlusion_mask_url IS '앞 가림 영역 마스크 PNG (선택, 층당 1개) — 담장·문 아치·기둥처럼 캐릭터 앞을 가릴 부분 (LUT-725)';

-- 불빛 점(전등·촛불 깜빡임) — 층당 최대 50개, 항상 켜짐
CREATE TABLE IF NOT EXISTS tower_floor_light (
    floor_id          BIGINT     NOT NULL REFERENCES tower_floor (id) ON DELETE CASCADE,
    light_order       INTEGER    NOT NULL,
    x                 INTEGER    NOT NULL,
    y                 INTEGER    NOT NULL,
    radius            INTEGER    NOT NULL,
    color             VARCHAR(7) NOT NULL,
    brightness        INTEGER    NOT NULL,
    flicker_intensity INTEGER    NOT NULL,
    flicker_speed     INTEGER    NOT NULL,
    PRIMARY KEY (floor_id, light_order)
);
COMMENT ON TABLE tower_floor_light IS '레벨업 탑 층 불빛 점 (LUT-725, 층당 최대 50개)';
COMMENT ON COLUMN tower_floor_light.color IS '#RRGGBB';
COMMENT ON COLUMN tower_floor_light.brightness IS '밝기 0~100';
COMMENT ON COLUMN tower_floor_light.flicker_intensity IS '깜빡임 세기 0~100 (0 = 깜빡이지 않음)';
COMMENT ON COLUMN tower_floor_light.flicker_speed IS '깜빡임 속도 0~100';

-- 눈·별 레이어 — 종류당 1개, 칠한 영역 안에서만 그린다. 켜기·기간(비우면 상시)
CREATE TABLE IF NOT EXISTS tower_floor_effect_layer (
    floor_id  BIGINT       NOT NULL REFERENCES tower_floor (id) ON DELETE CASCADE,
    kind      VARCHAR(10)  NOT NULL,
    mask_url  VARCHAR(500) NOT NULL,
    enabled   BOOLEAN      NOT NULL DEFAULT TRUE,
    starts_on DATE,
    ends_on   DATE,
    amount    INTEGER      NOT NULL,
    speed     INTEGER      NOT NULL,
    size_min  INTEGER      NOT NULL,
    size_max  INTEGER      NOT NULL,
    PRIMARY KEY (floor_id, kind)
);
COMMENT ON TABLE tower_floor_effect_layer IS '레벨업 탑 층 효과 레이어 (LUT-725, SNOW·STARS 각 1개)';
COMMENT ON COLUMN tower_floor_effect_layer.amount IS 'SNOW = 밀도(동시 눈송이 수), STARS = 별 개수';
COMMENT ON COLUMN tower_floor_effect_layer.starts_on IS '적용 시작일(KST, 포함) — 비우면 상시';
COMMENT ON COLUMN tower_floor_effect_layer.ends_on IS '적용 종료일(KST, 포함) — 비우면 상시';
