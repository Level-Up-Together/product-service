-- LUT-599: 가입 유입 채널 (CAC 원천). 가입 시 서버(SignupChannelPolicy)가 1회 기록. 기존 행은 null = 유입 미상.
-- prod(v1.1.3, Flyway off)는 level-up-together-sql queries/developing/1.1.3/V009 로 수동 적용 → v1.1.4 Flyway 전환 시 baseline 1.1.3.9 이상.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS signup_channel     VARCHAR(50),
    ADD COLUMN IF NOT EXISTS utm_source         VARCHAR(100),
    ADD COLUMN IF NOT EXISTS utm_medium         VARCHAR(100),
    ADD COLUMN IF NOT EXISTS utm_campaign       VARCHAR(100),
    ADD COLUMN IF NOT EXISTS signup_device_type VARCHAR(20),
    ADD COLUMN IF NOT EXISTS invite_code        VARCHAR(50);
CREATE INDEX IF NOT EXISTS idx_users_signup_channel ON users (signup_channel);

COMMENT ON COLUMN users.signup_channel     IS '가입 채널 — invite | {utm_source} | store_ios | store_android | organic_web (LUT-599)';
COMMENT ON COLUMN users.utm_source         IS '가입 시 first-touch utm_source 원문';
COMMENT ON COLUMN users.utm_medium         IS '가입 시 first-touch utm_medium 원문';
COMMENT ON COLUMN users.utm_campaign       IS '가입 시 first-touch utm_campaign 원문';
COMMENT ON COLUMN users.signup_device_type IS '가입 기기 종류 (web/ios/ipad/android)';
COMMENT ON COLUMN users.invite_code        IS '가입 경로의 길드 초대 코드 (LUT-519)';
