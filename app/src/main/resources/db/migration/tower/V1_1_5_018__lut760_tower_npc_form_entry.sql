-- LUT-760: 탑 NPC 입력 받기 — NPC 카드 대사 아래 입력칸으로 유저 글을 받아 어드민이 보고 방에도 보여 준다.
-- 설정(tower_npc_form)은 NPC 당 1행, 글(tower_npc_entry)은 공개/숨김·시드·신고 수·노출 수를 가진다.
CREATE TABLE IF NOT EXISTS tower_npc_form (
    id               BIGSERIAL PRIMARY KEY,
    npc_id           BIGINT       NOT NULL REFERENCES tower_npc (id) ON DELETE CASCADE,
    form_enabled     BOOLEAN      NOT NULL DEFAULT FALSE,
    body_type        VARCHAR(10)  NOT NULL DEFAULT 'INPUT',
    placeholder      VARCHAR(100),
    placeholder_en   VARCHAR(100),
    placeholder_ja   VARCHAR(100),
    placeholder_ar   VARCHAR(100),
    max_length       INTEGER      NOT NULL DEFAULT 100,
    submit_label     VARCHAR(30),
    submit_label_en  VARCHAR(30),
    submit_label_ja  VARCHAR(30),
    submit_label_ar  VARCHAR(30),
    login_required   BOOLEAN      NOT NULL DEFAULT FALSE,
    email_enabled    BOOLEAN      NOT NULL DEFAULT FALSE,
    consent_text     VARCHAR(200),
    consent_text_en  VARCHAR(200),
    consent_text_ja  VARCHAR(200),
    consent_text_ar  VARCHAR(200),
    display_mode     VARCHAR(10)  NOT NULL DEFAULT 'NONE',
    display_mask_url VARCHAR(500),
    created_at       TIMESTAMP,
    modified_at      TIMESTAMP,
    CONSTRAINT uk_tower_npc_form_npc UNIQUE (npc_id)
);
COMMENT ON TABLE tower_npc_form IS '레벨업 탑 NPC 입력 받기 설정 (LUT-760, NPC 당 1행)';
COMMENT ON COLUMN tower_npc_form.body_type IS 'INPUT(한 줄) / TEXTAREA(여러 줄)';
COMMENT ON COLUMN tower_npc_form.max_length IS '본문 최대 글자 수 (기본 100, 상한 500)';
COMMENT ON COLUMN tower_npc_form.login_required IS '로그인 유저만 받기 — 이메일 받기를 켜면 서버가 항상 TRUE 로 강제';
COMMENT ON COLUMN tower_npc_form.email_enabled IS '이메일 칸 + 동의 체크박스';
COMMENT ON COLUMN tower_npc_form.display_mode IS 'NONE / TAG(쪽지) / FLOAT(떠오르는 글)';
COMMENT ON COLUMN tower_npc_form.display_mask_url IS '공개 영역 마스크 PNG (앞 가림 마스크와 같은 형식, 배경과 비율 동일)';

CREATE TABLE IF NOT EXISTS tower_npc_entry (
    id           BIGSERIAL PRIMARY KEY,
    npc_id       BIGINT       NOT NULL REFERENCES tower_npc (id) ON DELETE CASCADE,
    user_id      VARCHAR(255),
    visitor_key  VARCHAR(64),
    visitor_no   INTEGER,
    client_ip    VARCHAR(64),
    author_name  VARCHAR(50),
    body         VARCHAR(500) NOT NULL,
    email        VARCHAR(500),
    consent_at   TIMESTAMP,
    status       VARCHAR(10)  NOT NULL DEFAULT 'VISIBLE',
    is_seed      BOOLEAN      NOT NULL DEFAULT FALSE,
    report_count INTEGER      NOT NULL DEFAULT 0,
    view_count   INTEGER      NOT NULL DEFAULT 0,
    created_at   TIMESTAMP,
    modified_at  TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_tower_npc_entry_pick ON tower_npc_entry (npc_id, status, view_count);
CREATE INDEX IF NOT EXISTS idx_tower_npc_entry_user_day ON tower_npc_entry (npc_id, user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_tower_npc_entry_visitor_day ON tower_npc_entry (npc_id, visitor_key, created_at);
CREATE INDEX IF NOT EXISTS idx_tower_npc_entry_ip_day ON tower_npc_entry (npc_id, client_ip, created_at);
COMMENT ON TABLE tower_npc_entry IS '레벨업 탑 NPC 에 남긴 유저 글 (LUT-760, 시드 포함)';
COMMENT ON COLUMN tower_npc_entry.user_id IS '로그인 유저 (방문객·시드는 NULL)';
COMMENT ON COLUMN tower_npc_entry.visitor_key IS '방문객 세션 키 (하루 1회 판정·숨기기 기준)';
COMMENT ON COLUMN tower_npc_entry.client_ip IS '제출 때 요청 IP (방문객 하루 1회 판정)';
COMMENT ON COLUMN tower_npc_entry.author_name IS '제출 때 닉네임 (로그인 유저만, 방문객은 visitor_no, 시드는 NPC 이름)';
COMMENT ON COLUMN tower_npc_entry.body IS '욕설 가림(**) 적용 뒤 본문';
COMMENT ON COLUMN tower_npc_entry.email IS '이메일 (AES 암호화, 이벤트 종료 후 어드민이 일괄 파기)';
COMMENT ON COLUMN tower_npc_entry.status IS 'VISIBLE / HIDDEN (신고 3건이면 자동 HIDDEN)';
COMMENT ON COLUMN tower_npc_entry.view_count IS '공개 조회로 내려준 횟수 — 덜 보여 준 글부터 뽑는 기준 (정확하지 않아도 됨)';

-- 같은 사람이 같은 글을 여러 번 신고해도 1번으로 센다 (reporter_key = u:{userId} | v:{visitorKey})
CREATE TABLE IF NOT EXISTS tower_npc_entry_report (
    entry_id     BIGINT      NOT NULL REFERENCES tower_npc_entry (id) ON DELETE CASCADE,
    reporter_key VARCHAR(80) NOT NULL,
    created_at   TIMESTAMP,
    PRIMARY KEY (entry_id, reporter_key)
);
COMMENT ON TABLE tower_npc_entry_report IS '레벨업 탑 NPC 글 신고 (LUT-760, 신고자당 1회)';
