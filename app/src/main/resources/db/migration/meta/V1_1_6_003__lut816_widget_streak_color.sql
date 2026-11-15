-- LUT-816: 홈 화면 위젯 'N일' 숫자 색 구간(meta_db). 어드민이 구간(시작·끝·색)을 정하고, 위젯은 연속 실천 일수 N 이 드는 구간의 색으로 숫자를 그린다.
-- 문장마다 따로 두지 않는다 — 구간 한 벌을 모든 위젯 문장에 쓴다. 어느 구간에도 들지 않는 N 은 위젯의 기본 색(보라색 계열).
-- 구간은 [min_days, max_days] 양끝 포함, max_days 가 NULL 이면 "이상"(마지막 구간만). 겹침·빈 곳 검사는 저장할 때 서버가 한다(WidgetStreakColorRule).
CREATE TABLE IF NOT EXISTS widget_streak_color (
    id          BIGSERIAL PRIMARY KEY,
    min_days    INTEGER    NOT NULL,
    max_days    INTEGER,
    color       VARCHAR(7) NOT NULL,
    created_at  TIMESTAMP  NOT NULL DEFAULT now(),
    modified_at TIMESTAMP,
    CONSTRAINT uk_widget_streak_color_min_days UNIQUE (min_days),
    CONSTRAINT chk_widget_streak_color_min_days CHECK (min_days >= 0),
    CONSTRAINT chk_widget_streak_color_range CHECK (max_days IS NULL OR max_days >= min_days),
    CONSTRAINT chk_widget_streak_color_hex CHECK (color ~ '^#[0-9a-f]{6}$')
);
COMMENT ON TABLE widget_streak_color IS '홈 화면 위젯 연속 실천 일수(N일) 숫자 색 구간 (LUT-816). 모든 위젯 문장 공통';
COMMENT ON COLUMN widget_streak_color.min_days IS '구간 시작 일수(포함, 0 이상)';
COMMENT ON COLUMN widget_streak_color.max_days IS '구간 끝 일수(포함). NULL 이면 끝 없음(이상) — 마지막 구간만';
COMMENT ON COLUMN widget_streak_color.color IS '숫자 색(#rrggbb, 소문자)';

-- 초기값(티켓 예시): 1~7 = 레벨 칩 녹색, 8~30 = primary. 31 이상은 어드민에서 정한다(그때까지 기본 색).
INSERT INTO widget_streak_color (min_days, max_days, color)
VALUES (1, 7, '#1dec06'),
       (8, 30, '#00f9ff');
