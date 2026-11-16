-- LUT-782: 첫 미션 온보딩 추천 미션 — 신규 유저 환영 모달·미션 탭 빈 화면에 뜨는 "누르면 바로 시작" 카드. 어드민(LUT-783)이 관리한다.
CREATE TABLE IF NOT EXISTS recommended_mission (
    id             BIGSERIAL    PRIMARY KEY,
    title_ko       VARCHAR(100) NOT NULL,
    title_en       VARCHAR(100),
    title_ja       VARCHAR(100),
    title_ar       VARCHAR(100),
    category_id    BIGINT,
    execution_mode VARCHAR(20)  NOT NULL,
    target_minutes INTEGER,
    icon           VARCHAR(16)  NOT NULL,
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order     INTEGER      NOT NULL DEFAULT 0,
    created_at     TIMESTAMP    NOT NULL DEFAULT now(),
    modified_at    TIMESTAMP,
    CONSTRAINT chk_recommended_mission_mode CHECK (execution_mode IN ('TIMED', 'SIMPLE')),
    CONSTRAINT chk_recommended_mission_minutes CHECK (
        (execution_mode = 'TIMED' AND target_minutes BETWEEN 1 AND 240)
        OR (execution_mode = 'SIMPLE' AND target_minutes IS NULL))
);
CREATE INDEX IF NOT EXISTS idx_recommended_mission_active_order ON recommended_mission (is_active, sort_order, id);

COMMENT ON TABLE recommended_mission IS '첫 미션 온보딩 추천 미션 (LUT-782)';
COMMENT ON COLUMN recommended_mission.title_ko IS '미션 이름(한국어, 필수) — 다른 언어가 비면 한국어로 보인다';
COMMENT ON COLUMN recommended_mission.category_id IS 'meta_db mission_category.id (DB 간 FK 없음)';
COMMENT ON COLUMN recommended_mission.execution_mode IS 'TIMED(타이머) / SIMPLE(체크)';
COMMENT ON COLUMN recommended_mission.target_minutes IS '타이머 목표 시간(분) — 체크 미션은 NULL';
COMMENT ON COLUMN recommended_mission.icon IS '카드 이모지';

-- 이 추천 미션으로 만든 미션 — 어드민 성과(시작 수·완료율) 집계용. 일반 생성은 NULL.
ALTER TABLE mission ADD COLUMN IF NOT EXISTS recommended_mission_id BIGINT;
COMMENT ON COLUMN mission.recommended_mission_id IS '첫 미션 온보딩 추천 미션 ID (LUT-782, 추천으로 만든 미션만)';
CREATE INDEX IF NOT EXISTS idx_mission_recommended_mission_id ON mission (recommended_mission_id)
    WHERE recommended_mission_id IS NOT NULL;

-- 초기 데이터 6개 (카테고리 1 운동 · 5 독서 · 10 일상 — dev·prod 같은 ID). 이미 행이 있으면 넣지 않는다.
INSERT INTO recommended_mission (title_ko, title_en, title_ja, title_ar, category_id, execution_mode, target_minutes, icon, sort_order)
SELECT v.title_ko, v.title_en, v.title_ja, v.title_ar, v.category_id, v.execution_mode, v.target_minutes, v.icon, v.sort_order
FROM (VALUES
    ('물 한 잔 마시기', 'Drink a glass of water', '水を一杯飲む', 'اشرب كوبًا من الماء', 10::BIGINT, 'SIMPLE', NULL::INTEGER, '💧', 1),
    ('5분 독서', 'Read for 5 minutes', '5分間読書', 'اقرأ لمدة 5 دقائق', 5::BIGINT, 'TIMED', 5, '📖', 2),
    ('10분 산책', 'Take a 10-minute walk', '10分間散歩', 'تمشَّ لمدة 10 دقائق', 1::BIGINT, 'TIMED', 10, '🚶', 3),
    ('책상 정리', 'Tidy up your desk', '机を片付ける', 'رتّب مكتبك', 10::BIGINT, 'SIMPLE', NULL::INTEGER, '🧹', 4),
    ('5분 스트레칭', 'Stretch for 5 minutes', '5分間ストレッチ', 'تمدّد لمدة 5 دقائق', 1::BIGINT, 'TIMED', 5, '🧘', 5),
    ('오늘 할 일 3개 적기', 'Write down 3 things to do today', '今日やることを3つ書く', 'اكتب 3 مهام لليوم', 10::BIGINT, 'SIMPLE', NULL::INTEGER, '📝', 6)
) AS v(title_ko, title_en, title_ja, title_ar, category_id, execution_mode, target_minutes, icon, sort_order)
WHERE NOT EXISTS (SELECT 1 FROM recommended_mission);
