-- LUT-632: 미션 수행 한 건 동안의 탑 자리 기록 — 층·방·자동 착석 여부·받은 응원 수 (완료 응답 안내·지표 분리).
CREATE TABLE IF NOT EXISTS tower_mission_seat (
    id             BIGSERIAL PRIMARY KEY,
    user_id        VARCHAR(255) NOT NULL,
    mission_id     BIGINT,
    execution_id   BIGINT       NOT NULL,
    execution_kind VARCHAR(10)  NOT NULL,
    floor_number   INTEGER      NOT NULL,
    instance_id    BIGINT       NOT NULL,
    auto_seated    BOOLEAN      NOT NULL DEFAULT FALSE,
    cheer_count    INTEGER      NOT NULL DEFAULT 0,
    seated_at      TIMESTAMP    NOT NULL,
    ended_at       TIMESTAMP,
    created_at     TIMESTAMP,
    modified_at    TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_tower_mission_seat_user_open ON tower_mission_seat (user_id, ended_at);
CREATE INDEX IF NOT EXISTS idx_tower_mission_seat_execution ON tower_mission_seat (user_id, execution_kind, execution_id);
COMMENT ON TABLE tower_mission_seat IS '레벨업 탑 미션별 자리 기록 (LUT-632)';
COMMENT ON COLUMN tower_mission_seat.execution_kind IS 'REGULAR(일반 미션 실행) / PINNED(고정 미션 인스턴스)';
COMMENT ON COLUMN tower_mission_seat.auto_seated IS '자동 착석으로 시작한 자리인가 (지표 분리용)';
COMMENT ON COLUMN tower_mission_seat.cheer_count IS '받은 응원 수 (LUT-633 리액션이 올림)';
