-- LUT-779: 첫날 행동별 리텐션 — 유저 1행. 매일 00:50 KST FirstDayRetentionScheduler 가 첫날(가입 + 24시간)이 끝난 가입자를 분류하고,
-- D1·D7·D30(KST 가입일 + N 에 접속했는가)을 그날이 닫히면 채운다(null = 아직 확정 전). 어드민 리텐션 화면(LUT-780)은 이 표만 읽는다.
-- 대상: 접속 기록(user_daily_activity) 시작일 이후 가입자, 테스트 계정·제외 유저 빼고 탈퇴자 포함(분모에 남겨 "돌아오지 않음").
CREATE TABLE IF NOT EXISTS first_day_retention (
    user_id              VARCHAR(255) PRIMARY KEY,
    signup_at            TIMESTAMP    NOT NULL,
    signup_date          DATE         NOT NULL,
    platform             VARCHAR(10)  NOT NULL,
    first_day_completed  BOOLEAN      NOT NULL,
    first_day_interacted BOOLEAN      NOT NULL,
    first_day_group      VARCHAR(20)  NOT NULL,
    d1                   BOOLEAN,
    d7                   BOOLEAN,
    d30                  BOOLEAN,
    created_at           TIMESTAMP    NOT NULL DEFAULT now(),
    modified_at          TIMESTAMP,
    CONSTRAINT chk_first_day_retention_platform CHECK (platform IN ('IOS', 'ANDROID', 'WEB', 'UNKNOWN')),
    CONSTRAINT chk_first_day_retention_group CHECK (first_day_group IN ('NOT_COMPLETED', 'SOLO', 'SOCIAL'))
);
CREATE INDEX IF NOT EXISTS idx_first_day_retention_signup_date ON first_day_retention (signup_date);
-- 매일 미확정 행만 다시 본다 — D30 까지 채우면 이 인덱스에서 빠진다
CREATE INDEX IF NOT EXISTS idx_first_day_retention_open ON first_day_retention (signup_date) WHERE d30 IS NULL;

COMMENT ON TABLE first_day_retention IS '첫날 행동별 리텐션 — 유저 1행 (LUT-779)';
COMMENT ON COLUMN first_day_retention.signup_date IS '가입일(KST) — D1·D7·D30 기준일';
COMMENT ON COLUMN first_day_retention.first_day_completed IS '가입 후 24시간 안에 미션 완료 1회 이상';
COMMENT ON COLUMN first_day_retention.first_day_interacted IS '가입 후 24시간 안에 다른 사람과 주고받음 — 친구 요청·수락, 길드 가입, 피드 좋아요·댓글(주거나 받음), DM, 탑 채팅·받은 응원';
COMMENT ON COLUMN first_day_retention.first_day_group IS 'NOT_COMPLETED(첫날 미완료) / SOLO(완료·상호작용 없음) / SOCIAL(완료·상호작용 있음)';
COMMENT ON COLUMN first_day_retention.d1 IS '가입일+1(KST)에 접속(user_daily_activity) — null 이면 그날이 아직 안 지남';
