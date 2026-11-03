-- LUT-630: 탑 인스턴스(방)와 점유자 — 입장 배정·방 정원·층 인원 집계의 원천.
-- 좌표·연결 여부 같은 실시간 presence 는 Redis(LUT-631). 빈 방은 바로 닫는다(행 삭제).
CREATE TABLE IF NOT EXISTS tower_instance (
    id           BIGSERIAL PRIMARY KEY,
    floor_number INTEGER   NOT NULL,
    opened_at    TIMESTAMP NOT NULL,
    created_at   TIMESTAMP,
    modified_at  TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_tower_instance_floor ON tower_instance (floor_number);
COMMENT ON TABLE tower_instance IS '레벨업 탑 인스턴스(방) — 층당 상한(instance_capacity)을 넘으면 새 방 (LUT-630)';

CREATE TABLE IF NOT EXISTS tower_occupant (
    id             BIGSERIAL PRIMARY KEY,
    instance_id    BIGINT       NOT NULL REFERENCES tower_instance (id),
    floor_number   INTEGER      NOT NULL,
    user_id        VARCHAR(255),
    visitor_key    VARCHAR(64),
    kind           VARCHAR(20)  NOT NULL,
    mission_active BOOLEAN      NOT NULL DEFAULT FALSE,
    seat_x         INTEGER      NOT NULL,
    seat_y         INTEGER      NOT NULL,
    entered_at     TIMESTAMP    NOT NULL,
    last_active_at TIMESTAMP    NOT NULL,
    created_at     TIMESTAMP,
    modified_at    TIMESTAMP,
    CONSTRAINT uk_tower_occupant_user UNIQUE (user_id),
    CONSTRAINT uk_tower_occupant_visitor UNIQUE (visitor_key),
    CONSTRAINT ck_tower_occupant_identity CHECK ((user_id IS NOT NULL) <> (visitor_key IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS idx_tower_occupant_instance ON tower_occupant (instance_id);
CREATE INDEX IF NOT EXISTS idx_tower_occupant_floor ON tower_occupant (floor_number);
COMMENT ON TABLE tower_occupant IS '레벨업 탑 인스턴스 점유자 — 유저는 탑 전체에서 자리 하나 (LUT-630)';
COMMENT ON COLUMN tower_occupant.kind IS 'DIRECT(직접 입장) / VISITOR(방문객) / AUTO_SEATED(자동 착석 — 정원에 세지 않음)';
COMMENT ON COLUMN tower_occupant.mission_active IS '미션 중 — true 면 연결이 끊겨도 자리 유지';
COMMENT ON COLUMN tower_occupant.visitor_key IS '방문객 키 — 퇴장 등 본인 확인용 (세션·이름은 LUT-705)';
