-- LUT-598: 유저 일별 활동 (DAU 신호). Redis active:{KST날짜} 집합을 매일 00:20 KST 에 flush 한 결과.
-- stats_db 는 이 릴리스에서 신설된 DB 라 빈 스키마에서 시작한다 (dev: Flyway 가 생성).
-- prod(v1.1.3, Flyway off)는 level-up-together-sql queries/developing/1.1.3/V008 로 수동 적용한다.
-- v1.1.4 에서 prod Flyway 를 켤 때 baseline-version 을 1.1.3.8 이상으로 올릴 것 (이 파일을 다시 적용하지 않게). IF NOT EXISTS 는 그 보험.
CREATE TABLE IF NOT EXISTS user_daily_activity (
    id            BIGSERIAL   PRIMARY KEY,
    user_id       VARCHAR(36) NOT NULL,
    activity_date DATE        NOT NULL,
    device_types  VARCHAR(50),
    created_at    TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT uk_user_daily_activity UNIQUE (user_id, activity_date)
);
CREATE INDEX IF NOT EXISTS idx_user_daily_activity_date ON user_daily_activity (activity_date);

COMMENT ON TABLE  user_daily_activity               IS '유저 일별 활동 — 그날(KST) 인증된 API 요청을 1건 이상 보낸 유저 (LUT-598)';
COMMENT ON COLUMN user_daily_activity.user_id       IS '사용자 ID';
COMMENT ON COLUMN user_daily_activity.activity_date IS '활동 날짜 (KST)';
COMMENT ON COLUMN user_daily_activity.device_types  IS '그날 사용한 기기 종류, 정렬·쉼표 구분 (예: ios,web)';
