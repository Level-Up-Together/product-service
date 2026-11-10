-- LUT-705: 탑 방문객(비로그인) 운영 — 계정이 없으므로 제재는 기기·IP 단위로 건다. 신고에는 원문(tower_chat_log)과 기기 정보를 남긴다.
CREATE TABLE IF NOT EXISTS tower_visitor_sanction (
    id             BIGSERIAL PRIMARY KEY,
    target_type    VARCHAR(10)  NOT NULL,
    target_value   VARCHAR(128) NOT NULL,
    sanction_type  VARCHAR(10)  NOT NULL,
    reason         VARCHAR(500),
    expires_at     TIMESTAMP,
    lifted_at      TIMESTAMP,
    created_by     VARCHAR(100),
    created_at     TIMESTAMP,
    modified_at    TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_tower_visitor_sanction_active ON tower_visitor_sanction (lifted_at, expires_at);
COMMENT ON TABLE tower_visitor_sanction IS '레벨업 탑 방문객 제재 (기기·IP 단위, LUT-705)';
COMMENT ON COLUMN tower_visitor_sanction.target_type IS 'IP / DEVICE';
COMMENT ON COLUMN tower_visitor_sanction.sanction_type IS 'CHAT(채팅·리액션 금지) / ENTRY(입장 금지, 채팅도 막힘)';
COMMENT ON COLUMN tower_visitor_sanction.expires_at IS '끝나는 시각(UTC), NULL 이면 해제할 때까지';
COMMENT ON COLUMN tower_visitor_sanction.lifted_at IS '어드민이 해제한 시각(UTC)';

CREATE TABLE IF NOT EXISTS tower_visitor_report (
    id                 BIGSERIAL PRIMARY KEY,
    reporter_user_id   VARCHAR(255) NOT NULL,
    instance_id        BIGINT       NOT NULL,
    floor_number       INTEGER      NOT NULL,
    target_occupant_id BIGINT       NOT NULL,
    visitor_key        VARCHAR(64)  NOT NULL,
    visitor_no         INTEGER,
    client_ip          VARCHAR(64),
    device_id          VARCHAR(128),
    user_agent         VARCHAR(500),
    reason             VARCHAR(500),
    recent_messages    TEXT,
    created_at         TIMESTAMP,
    modified_at        TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_tower_visitor_report_created ON tower_visitor_report (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_tower_visitor_report_dup ON tower_visitor_report (reporter_user_id, visitor_key, created_at DESC);
COMMENT ON TABLE tower_visitor_report IS '레벨업 탑 방문객 신고 (원문·기기 정보 포함, 운영 전용, LUT-705)';
COMMENT ON COLUMN tower_visitor_report.recent_messages IS '신고 시점 그 방문객의 최근 원문 채팅(JSON 배열, tower_chat_log 에서)';
