-- LUT-634: 층 첫 입장 기록 — 유저×층 1행. 첫 입장 때만 insert 되고(ON CONFLICT DO NOTHING) 그 순간 업적·칭호 재판정 이벤트를 낸다.
-- 방문객(비로그인)은 기록하지 않는다. 층을 삭제해도 기록은 남긴다(업적 집계는 "들어가 본 층 수"·"가장 높은 층").
CREATE TABLE IF NOT EXISTS tower_floor_visit (
    id               BIGSERIAL    PRIMARY KEY,
    user_id          VARCHAR(255) NOT NULL,
    floor_number     INTEGER      NOT NULL,
    first_entered_at TIMESTAMP    NOT NULL,
    created_at       TIMESTAMP,
    modified_at      TIMESTAMP,
    CONSTRAINT uk_tower_floor_visit UNIQUE (user_id, floor_number)
);
COMMENT ON TABLE tower_floor_visit IS '레벨업 탑 층 첫 입장 기록 (LUT-634)';
