-- LUT-633: 탑 채팅 운영 로그 — 원문은 다른 유저에게 나가지 않고 여기에만 남는다(신고 판단용). 거절된 메시지도 남긴다.
-- 최근 50건 표시용 이력은 Redis(tower:chat:history:{instanceId}). 보관 기간·어드민 화면은 미결.
CREATE TABLE IF NOT EXISTS tower_chat_log (
    id                BIGSERIAL PRIMARY KEY,
    instance_id       BIGINT        NOT NULL,
    floor_number      INTEGER       NOT NULL,
    occupant_id       BIGINT,
    user_id           VARCHAR(255),
    visitor_key       VARCHAR(64),
    original_content  VARCHAR(1000) NOT NULL,
    processed_content VARCHAR(1000),
    result            VARCHAR(20)   NOT NULL,
    reject_code       VARCHAR(10),
    matched_words     VARCHAR(500),
    created_at        TIMESTAMP,
    modified_at       TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_tower_chat_log_instance ON tower_chat_log (instance_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_tower_chat_log_user ON tower_chat_log (user_id, created_at DESC);
COMMENT ON TABLE tower_chat_log IS '레벨업 탑 채팅 로그 (원문 포함, 운영 전용, LUT-633)';
COMMENT ON COLUMN tower_chat_log.result IS 'SENT / MASKED(금칙어 가림) / REJECTED(HIGH 금칙어·방문객 내용 제한)';
