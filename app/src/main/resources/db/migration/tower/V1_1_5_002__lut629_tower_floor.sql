-- LUT-629: 레벨업 탑 층(floor) — 어드민이 등록하는 층과 유저의 마지막 입장 층.
-- tower_db 는 v1.1.5 에서 신설(10번째 DB). DB 자체는 수동 생성(CREATE DATABASE tower_db), 표는 Flyway.
CREATE TABLE IF NOT EXISTS tower_floor (
    id                   BIGSERIAL PRIMARY KEY,
    floor_number         INTEGER      NOT NULL,
    name                 VARCHAR(100) NOT NULL,
    name_en              VARCHAR(100) NOT NULL,
    name_ja              VARCHAR(100) NOT NULL,
    name_ar              VARCHAR(100) NOT NULL,
    entry_level          INTEGER,
    guest_allowed        BOOLEAN      NOT NULL DEFAULT FALSE,
    background_image_url VARCHAR(500) NOT NULL,
    floor_mask_url       VARCHAR(500) NOT NULL,
    entry_point_x        INTEGER      NOT NULL,
    entry_point_y        INTEGER      NOT NULL,
    bgm_url              VARCHAR(500),
    instance_capacity    INTEGER      NOT NULL DEFAULT 30,
    is_public            BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMP,
    modified_at          TIMESTAMP,
    CONSTRAINT uk_tower_floor_number UNIQUE (floor_number)
);
COMMENT ON TABLE tower_floor IS '레벨업 탑 층 (LUT-629)';
COMMENT ON COLUMN tower_floor.floor_number IS '층 번호 (1부터, 유일)';
COMMENT ON COLUMN tower_floor.entry_level IS '입장 레벨 (NULL = 제한 없음, 1층은 항상 NULL)';
COMMENT ON COLUMN tower_floor.guest_allowed IS '비로그인 방문객 입장 허용';
COMMENT ON COLUMN tower_floor.floor_mask_url IS '바닥 마스크 PNG (붉은색 = 이동 가능), 배경과 같은 좌표계';
COMMENT ON COLUMN tower_floor.entry_point_x IS '입장 지점 X (배경 이미지 원본 픽셀)';
COMMENT ON COLUMN tower_floor.entry_point_y IS '입장 지점 Y (배경 이미지 원본 픽셀)';
COMMENT ON COLUMN tower_floor.instance_capacity IS '인스턴스(방) 상한, 기본 30';
COMMENT ON COLUMN tower_floor.is_public IS '공개 여부 (비공개는 유저 목록에서 제외, 검수용)';

CREATE TABLE IF NOT EXISTS tower_user_state (
    user_id           VARCHAR(255) PRIMARY KEY,
    last_floor_number INTEGER   NOT NULL,
    last_entered_at   TIMESTAMP NOT NULL,
    created_at        TIMESTAMP,
    modified_at       TIMESTAMP
);
COMMENT ON TABLE tower_user_state IS '유저별 탑 상태 — 마지막으로 머문 층 (LUT-629, 기본 입장 층)';
