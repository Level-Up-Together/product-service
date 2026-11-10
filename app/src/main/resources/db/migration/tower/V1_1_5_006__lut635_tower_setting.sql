-- LUT-635: 탑 공통 설정(키-값). 층 설정이 아닌 탑 전체 값 — max_concurrent_users = 동시 접속 상한(대기열 LUT-701 기준).
-- 행이 없으면 제한 없음. 기본값은 부하 테스트(LUT-631) 결과로 어드민이 넣는다.
CREATE TABLE IF NOT EXISTS tower_setting (
    setting_key   VARCHAR(50)  PRIMARY KEY,
    setting_value VARCHAR(500) NOT NULL,
    created_at    TIMESTAMP,
    modified_at   TIMESTAMP
);
COMMENT ON TABLE tower_setting IS '레벨업 탑 공통 설정 (키-값, LUT-635)';
COMMENT ON COLUMN tower_setting.setting_key IS 'max_concurrent_users = 탑 전체 동시 접속 상한';
