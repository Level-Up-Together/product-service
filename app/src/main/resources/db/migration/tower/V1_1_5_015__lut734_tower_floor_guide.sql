-- LUT-734: 층별 첫 방문 안내 — 켜기, 안내 NPC(그 층 NPC), 문장 1~5개(4 locale), 마지막 문장의 미션 시작 버튼·문구, 버전.
-- "다시 보여 주기" 로 저장하면 버전을 올리고, 유저별 본 버전(tower_floor_visit.guide_seen_version)보다 크면 한 번 더 보인다.
CREATE TABLE IF NOT EXISTS tower_floor_guide (
    id                      BIGSERIAL PRIMARY KEY,
    floor_id                BIGINT      NOT NULL REFERENCES tower_floor (id) ON DELETE CASCADE,
    enabled                 BOOLEAN     NOT NULL DEFAULT FALSE,
    npc_id                  BIGINT      REFERENCES tower_npc (id) ON DELETE SET NULL,
    show_mission_button     BOOLEAN     NOT NULL DEFAULT FALSE,
    mission_button_label    VARCHAR(30),
    mission_button_label_en VARCHAR(30),
    mission_button_label_ja VARCHAR(30),
    mission_button_label_ar VARCHAR(30),
    version                 INTEGER     NOT NULL DEFAULT 1,
    created_at              TIMESTAMP,
    modified_at             TIMESTAMP,
    CONSTRAINT uk_tower_floor_guide_floor UNIQUE (floor_id)
);
COMMENT ON TABLE tower_floor_guide IS '레벨업 탑 층별 첫 방문 안내 (LUT-734, 층당 1행)';
COMMENT ON COLUMN tower_floor_guide.npc_id IS '안내 NPC (그 층 NPC, 지우면 NULL → 안내 안 뜸)';
COMMENT ON COLUMN tower_floor_guide.version IS '안내 버전 — 다시 보여 주기로 저장할 때만 올린다';

CREATE TABLE IF NOT EXISTS tower_floor_guide_line (
    guide_id   BIGINT       NOT NULL REFERENCES tower_floor_guide (id) ON DELETE CASCADE,
    line_order INTEGER      NOT NULL,
    text       VARCHAR(200) NOT NULL,
    text_en    VARCHAR(200) NOT NULL,
    text_ja    VARCHAR(200) NOT NULL,
    text_ar    VARCHAR(200) NOT NULL,
    PRIMARY KEY (guide_id, line_order)
);
COMMENT ON TABLE tower_floor_guide_line IS '레벨업 탑 첫 방문 안내 문장 (LUT-734, 1~5개, 한 문장 2줄 이내)';

ALTER TABLE tower_floor_visit ADD COLUMN IF NOT EXISTS guide_seen_version INTEGER NOT NULL DEFAULT 0;
COMMENT ON COLUMN tower_floor_visit.guide_seen_version IS '이 유저가 본 첫 방문 안내 버전 (LUT-734, 0 = 안 봄)';
