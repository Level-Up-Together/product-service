-- LUT-708: 어드민이 바꾸는 서비스 설정 키-값 표(meta_db). 첫 키 = 스토어 인앱 평가 팝업 마일스톤(연속 출석 일수 목록).
-- 값은 문자열로 두고 해석은 AppSettingCacheService 가 한다. 캐시(appSettings)는 값만 담아 컬럼 추가에도 옛 캐시가 깨지지 않는다.
CREATE TABLE IF NOT EXISTS app_setting (
    setting_key   VARCHAR(100) PRIMARY KEY,
    setting_value VARCHAR(500) NOT NULL,
    description   VARCHAR(300),
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    modified_at   TIMESTAMP
);
COMMENT ON TABLE app_setting IS '어드민 서비스 설정 키-값 (LUT-708). 해석은 AppSettingCacheService';
COMMENT ON COLUMN app_setting.setting_value IS '문자열 값 — store_review_milestone_days 는 쉼표 구분 일수 목록, 빈 문자열이면 끔';

INSERT INTO app_setting (setting_key, setting_value, description)
VALUES ('store_review_milestone_days', '7,14,30',
        '연속 출석 일수가 이 값과 같을 때 스토어 인앱 평가 팝업을 요청 (쉼표 구분 오름차순, 1~365, 최대 10개, 비우면 끔)')
ON CONFLICT (setting_key) DO NOTHING;
