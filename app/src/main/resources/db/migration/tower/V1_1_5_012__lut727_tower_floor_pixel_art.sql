-- LUT-727: 층 배경 픽셀아트 — 원본(배경)을 등록·교체하면 서버가 비동기로 고밀도(높이 576)·픽셀(높이 384) 두 장을 만든다.
-- 층당 1행. 결과 파일 이름은 내용 해시(버전)라 같은 원본·같은 설정이면 같은 주소다.
CREATE TABLE IF NOT EXISTS tower_floor_pixel_art (
    floor_id         BIGINT       PRIMARY KEY REFERENCES tower_floor (id) ON DELETE CASCADE,
    source_url       VARCHAR(500) NOT NULL,
    colors           INTEGER      NOT NULL DEFAULT 64,
    sharpness        INTEGER      NOT NULL DEFAULT 100,
    status           VARCHAR(10)  NOT NULL,
    request_seq      BIGINT       NOT NULL DEFAULT 0,
    dense_url        VARCHAR(500),
    dense_width      INTEGER,
    dense_height     INTEGER,
    dense_version    VARCHAR(32),
    standard_url     VARCHAR(500),
    standard_width   INTEGER,
    standard_height  INTEGER,
    standard_version VARCHAR(32),
    error_message    VARCHAR(300),
    requested_at     TIMESTAMP,
    completed_at     TIMESTAMP,
    created_at       TIMESTAMP,
    modified_at      TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_tower_floor_pixel_art_status ON tower_floor_pixel_art (status, requested_at);
COMMENT ON TABLE tower_floor_pixel_art IS '레벨업 탑 층 배경 픽셀아트 (LUT-727, 층당 1행)';
COMMENT ON COLUMN tower_floor_pixel_art.source_url IS '이 결과를 만든 원본 배경 URL — 층 배경과 다르면 결과를 쓰지 않는다';
COMMENT ON COLUMN tower_floor_pixel_art.colors IS '팔레트 색 수 (기본 64)';
COMMENT ON COLUMN tower_floor_pixel_art.sharpness IS '선명도 % (기본 100 = 시안 값)';
COMMENT ON COLUMN tower_floor_pixel_art.status IS 'PENDING / DONE / FAILED';
COMMENT ON COLUMN tower_floor_pixel_art.request_seq IS '요청 번호 — 늦게 끝난 옛 작업이 새 결과를 덮지 않게';
COMMENT ON COLUMN tower_floor_pixel_art.dense_version IS '고밀도 결과 내용 해시 (SHA-256 앞 16자리)';
