-- LUT-693: 탑 NPC(층 주민) — 층마다 어드민이 등록한다(층당 최대 3명). 걸어 다니지 않고 등록한 자리에 머문다.
-- 인원 표시·인스턴스 배정·지표에는 넣지 않는다(점유자 표 tower_occupant 와 별개).
CREATE TABLE IF NOT EXISTS tower_npc (
    id                BIGSERIAL PRIMARY KEY,
    floor_id          BIGINT       NOT NULL REFERENCES tower_floor (id) ON DELETE CASCADE,
    name              VARCHAR(30)  NOT NULL,
    name_en           VARCHAR(30)  NOT NULL,
    name_ja           VARCHAR(30)  NOT NULL,
    name_ar           VARCHAR(30)  NOT NULL,
    profile_image_url VARCHAR(500) NOT NULL,
    status_text       VARCHAR(30),
    status_text_en    VARCHAR(30),
    status_text_ja    VARCHAR(30),
    status_text_ar    VARCHAR(30),
    seat_x            INTEGER      NOT NULL,
    seat_y            INTEGER      NOT NULL,
    is_public         BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order        INTEGER      NOT NULL DEFAULT 0,
    created_at        TIMESTAMP,
    modified_at       TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_tower_npc_floor ON tower_npc (floor_id);
COMMENT ON TABLE tower_npc IS '레벨업 탑 NPC (LUT-693, 층당 최대 3명)';
COMMENT ON COLUMN tower_npc.seat_x IS '자리 X (배경 이미지 원본 픽셀, 마스크 안)';
COMMENT ON COLUMN tower_npc.seat_y IS '자리 Y (배경 이미지 원본 픽셀, 마스크 안)';
COMMENT ON COLUMN tower_npc.status_text IS '상태 문구 (연주 중 등, 선택) — 유저의 미션 중 표시 자리에 띄운다';

-- 대사 목록 — 등록 순서대로 돌아간다(클릭마다 다음 문장)
CREATE TABLE IF NOT EXISTS tower_npc_line (
    npc_id     BIGINT       NOT NULL REFERENCES tower_npc (id) ON DELETE CASCADE,
    line_order INTEGER      NOT NULL,
    text       VARCHAR(200) NOT NULL,
    text_en    VARCHAR(200) NOT NULL,
    text_ja    VARCHAR(200) NOT NULL,
    text_ar    VARCHAR(200) NOT NULL,
    PRIMARY KEY (npc_id, line_order)
);
COMMENT ON TABLE tower_npc_line IS '레벨업 탑 NPC 대사 (LUT-693, 한 문장 2줄 이내)';

-- 실제 흔적: 오늘 이 층에서 미션을 완료한 자리 (누구인지는 내리지 않는다)
ALTER TABLE tower_mission_seat ADD COLUMN IF NOT EXISTS seat_x INTEGER;
ALTER TABLE tower_mission_seat ADD COLUMN IF NOT EXISTS seat_y INTEGER;
ALTER TABLE tower_mission_seat ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP;
CREATE INDEX IF NOT EXISTS idx_tower_mission_seat_floor_completed
    ON tower_mission_seat (floor_number, completed_at);
COMMENT ON COLUMN tower_mission_seat.completed_at IS '미션 완료 시각 (완료로 끝난 자리만, LUT-693 실제 흔적)';
COMMENT ON COLUMN tower_mission_seat.seat_x IS '완료한 자리 X (배경 원본 픽셀, 모르면 NULL)';
COMMENT ON COLUMN tower_mission_seat.seat_y IS '완료한 자리 Y';
