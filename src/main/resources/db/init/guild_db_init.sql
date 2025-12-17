-- =====================================================
-- Guild DB Initialization SQL
-- Database: guild_db
-- Services: GuildService
-- =====================================================

-- =====================================================
-- DROP EXISTING TABLES (for clean initialization)
-- =====================================================
DROP TABLE IF EXISTS guild_post_comment CASCADE;
DROP TABLE IF EXISTS guild_post CASCADE;
DROP TABLE IF EXISTS guild_experience_history CASCADE;
DROP TABLE IF EXISTS guild_chat_message CASCADE;
DROP TABLE IF EXISTS guild_join_request CASCADE;
DROP TABLE IF EXISTS guild_member CASCADE;
DROP TABLE IF EXISTS guild_level_config CASCADE;
DROP TABLE IF EXISTS guild CASCADE;

-- =====================================================
-- 1. Guild (길드)
-- =====================================================
CREATE TABLE guild (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    visibility VARCHAR(20) NOT NULL,
    master_id VARCHAR(255) NOT NULL,
    max_members INTEGER DEFAULT 50,
    image_url VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    current_level INTEGER NOT NULL DEFAULT 1,
    current_exp INTEGER NOT NULL DEFAULT 0,
    total_exp INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_guild_visibility CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
    CONSTRAINT chk_guild_level CHECK (current_level >= 1),
    CONSTRAINT chk_guild_exp CHECK (current_exp >= 0 AND total_exp >= 0),
    CONSTRAINT chk_guild_max_members CHECK (max_members >= 1)
);

COMMENT ON TABLE guild IS '길드';
COMMENT ON COLUMN guild.id IS '길드 ID';
COMMENT ON COLUMN guild.name IS '길드명';
COMMENT ON COLUMN guild.description IS '길드 설명';
COMMENT ON COLUMN guild.visibility IS '공개 여부 (PUBLIC, PRIVATE)';
COMMENT ON COLUMN guild.master_id IS '길드 마스터 ID';
COMMENT ON COLUMN guild.max_members IS '최대 멤버 수';
COMMENT ON COLUMN guild.image_url IS '길드 이미지 URL';
COMMENT ON COLUMN guild.is_active IS '활성 여부';
COMMENT ON COLUMN guild.current_level IS '현재 길드 레벨';
COMMENT ON COLUMN guild.current_exp IS '현재 레벨에서의 경험치';
COMMENT ON COLUMN guild.total_exp IS '총 누적 경험치';

CREATE INDEX idx_guild_master ON guild(master_id);
CREATE INDEX idx_guild_visibility ON guild(visibility);
CREATE INDEX idx_guild_active ON guild(is_active);
CREATE INDEX idx_guild_name ON guild(name);

-- =====================================================
-- 2. Guild Member (길드 멤버)
-- =====================================================
CREATE TABLE guild_member (
    id BIGSERIAL PRIMARY KEY,
    guild_id BIGINT NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    joined_at TIMESTAMP,
    left_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_guild_member_guild FOREIGN KEY (guild_id) REFERENCES guild(id) ON DELETE CASCADE,
    CONSTRAINT uk_guild_member UNIQUE (guild_id, user_id),
    CONSTRAINT chk_member_role CHECK (role IN ('MASTER', 'ADMIN', 'MEMBER')),
    CONSTRAINT chk_member_status CHECK (status IN ('ACTIVE', 'LEFT', 'KICKED'))
);

COMMENT ON TABLE guild_member IS '길드 멤버';
COMMENT ON COLUMN guild_member.id IS '길드 멤버 ID';
COMMENT ON COLUMN guild_member.guild_id IS '길드 ID';
COMMENT ON COLUMN guild_member.user_id IS '사용자 ID';
COMMENT ON COLUMN guild_member.role IS '역할 (MASTER, ADMIN, MEMBER)';
COMMENT ON COLUMN guild_member.status IS '상태 (ACTIVE, LEFT, KICKED)';
COMMENT ON COLUMN guild_member.joined_at IS '가입 일시';
COMMENT ON COLUMN guild_member.left_at IS '탈퇴 일시';

CREATE INDEX idx_guild_member_guild ON guild_member(guild_id);
CREATE INDEX idx_guild_member_user ON guild_member(user_id);
CREATE INDEX idx_guild_member_status ON guild_member(status);

-- =====================================================
-- 3. Guild Join Request (길드 가입 신청)
-- =====================================================
CREATE TABLE guild_join_request (
    id BIGSERIAL PRIMARY KEY,
    guild_id BIGINT NOT NULL,
    requester_id VARCHAR(255) NOT NULL,
    message VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    processed_by VARCHAR(255),
    processed_at TIMESTAMP,
    reject_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_join_request_guild FOREIGN KEY (guild_id) REFERENCES guild(id) ON DELETE CASCADE,
    CONSTRAINT chk_request_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'))
);

COMMENT ON TABLE guild_join_request IS '길드 가입 신청';
COMMENT ON COLUMN guild_join_request.id IS '가입 신청 ID';
COMMENT ON COLUMN guild_join_request.guild_id IS '길드 ID';
COMMENT ON COLUMN guild_join_request.requester_id IS '신청자 ID';
COMMENT ON COLUMN guild_join_request.message IS '가입 신청 메시지';
COMMENT ON COLUMN guild_join_request.status IS '신청 상태 (PENDING, APPROVED, REJECTED, CANCELLED)';
COMMENT ON COLUMN guild_join_request.processed_by IS '처리자 ID';
COMMENT ON COLUMN guild_join_request.processed_at IS '처리 일시';
COMMENT ON COLUMN guild_join_request.reject_reason IS '거절 사유';

CREATE INDEX idx_join_request_guild ON guild_join_request(guild_id);
CREATE INDEX idx_join_request_requester ON guild_join_request(requester_id);
CREATE INDEX idx_join_request_status ON guild_join_request(status);

-- =====================================================
-- 4. Guild Level Config (길드 레벨 설정)
-- =====================================================
CREATE TABLE guild_level_config (
    id BIGSERIAL PRIMARY KEY,
    level INTEGER NOT NULL,
    required_exp INTEGER NOT NULL,
    cumulative_exp INTEGER,
    max_members INTEGER NOT NULL,
    title VARCHAR(50),
    description VARCHAR(200),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_guild_level_config_level UNIQUE (level),
    CONSTRAINT chk_level CHECK (level >= 1),
    CONSTRAINT chk_required_exp CHECK (required_exp >= 0),
    CONSTRAINT chk_config_max_members CHECK (max_members >= 1)
);

COMMENT ON TABLE guild_level_config IS '길드 레벨 설정';
COMMENT ON COLUMN guild_level_config.id IS 'ID';
COMMENT ON COLUMN guild_level_config.level IS '길드 레벨';
COMMENT ON COLUMN guild_level_config.required_exp IS '다음 레벨까지 필요한 경험치';
COMMENT ON COLUMN guild_level_config.cumulative_exp IS '이 레벨까지 누적 필요 경험치';
COMMENT ON COLUMN guild_level_config.max_members IS '해당 레벨에서 최대 멤버 수';
COMMENT ON COLUMN guild_level_config.title IS '길드 레벨 칭호';
COMMENT ON COLUMN guild_level_config.description IS '레벨 설명';

-- =====================================================
-- 5. Guild Experience History (길드 경험치 히스토리)
-- =====================================================
CREATE TABLE guild_experience_history (
    id BIGSERIAL PRIMARY KEY,
    guild_id BIGINT NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    source_id BIGINT,
    contributor_id VARCHAR(255),
    exp_amount INTEGER NOT NULL,
    description VARCHAR(500),
    level_before INTEGER,
    level_after INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_exp_history_guild FOREIGN KEY (guild_id) REFERENCES guild(id) ON DELETE CASCADE,
    CONSTRAINT chk_exp_source_type CHECK (source_type IN ('GUILD_MISSION_EXECUTION', 'GUILD_MISSION_FULL_COMPLETION', 'ADMIN_GRANT', 'EVENT_BONUS'))
);

COMMENT ON TABLE guild_experience_history IS '길드 경험치 히스토리';
COMMENT ON COLUMN guild_experience_history.id IS 'ID';
COMMENT ON COLUMN guild_experience_history.guild_id IS '길드 ID';
COMMENT ON COLUMN guild_experience_history.source_type IS '경험치 획득 유형';
COMMENT ON COLUMN guild_experience_history.source_id IS '경험치 획득 소스 ID';
COMMENT ON COLUMN guild_experience_history.contributor_id IS '기여자 ID (경험치를 획득해준 길드원)';
COMMENT ON COLUMN guild_experience_history.exp_amount IS '획득 경험치';
COMMENT ON COLUMN guild_experience_history.description IS '설명';
COMMENT ON COLUMN guild_experience_history.level_before IS '획득 전 레벨';
COMMENT ON COLUMN guild_experience_history.level_after IS '획득 후 레벨';

CREATE INDEX idx_exp_history_guild ON guild_experience_history(guild_id);
CREATE INDEX idx_exp_history_contributor ON guild_experience_history(contributor_id);
CREATE INDEX idx_exp_history_created ON guild_experience_history(created_at);

-- =====================================================
-- 6. Guild Chat Message (길드 채팅 메시지)
-- =====================================================
CREATE TABLE guild_chat_message (
    id BIGSERIAL PRIMARY KEY,
    guild_id BIGINT NOT NULL,
    sender_id VARCHAR(255),
    sender_nickname VARCHAR(50),
    message_type VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    content VARCHAR(1000) NOT NULL,
    image_url VARCHAR(500),
    reference_type VARCHAR(30),
    reference_id BIGINT,
    is_deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_chat_message_guild FOREIGN KEY (guild_id) REFERENCES guild(id) ON DELETE CASCADE,
    CONSTRAINT chk_message_type CHECK (message_type IN ('TEXT', 'IMAGE', 'SYSTEM_JOIN', 'SYSTEM_LEAVE', 'SYSTEM_KICK', 'SYSTEM_ACHIEVEMENT', 'SYSTEM_MISSION', 'SYSTEM_LEVEL_UP', 'SYSTEM_ANNOUNCEMENT'))
);

COMMENT ON TABLE guild_chat_message IS '길드 채팅 메시지';
COMMENT ON COLUMN guild_chat_message.id IS 'ID';
COMMENT ON COLUMN guild_chat_message.guild_id IS '길드 ID';
COMMENT ON COLUMN guild_chat_message.sender_id IS '발신자 ID (시스템 메시지는 null)';
COMMENT ON COLUMN guild_chat_message.sender_nickname IS '발신자 닉네임';
COMMENT ON COLUMN guild_chat_message.message_type IS '메시지 타입';
COMMENT ON COLUMN guild_chat_message.content IS '메시지 내용';
COMMENT ON COLUMN guild_chat_message.image_url IS '이미지 URL';
COMMENT ON COLUMN guild_chat_message.reference_type IS '참조 타입 (ACHIEVEMENT, MISSION 등)';
COMMENT ON COLUMN guild_chat_message.reference_id IS '참조 ID';
COMMENT ON COLUMN guild_chat_message.is_deleted IS '삭제 여부';
COMMENT ON COLUMN guild_chat_message.deleted_at IS '삭제 시간';

CREATE INDEX idx_chat_guild ON guild_chat_message(guild_id);
CREATE INDEX idx_chat_guild_created ON guild_chat_message(guild_id, created_at DESC);
CREATE INDEX idx_chat_sender ON guild_chat_message(sender_id);

-- =====================================================
-- 7. Guild Post (길드 게시글)
-- =====================================================
CREATE TABLE guild_post (
    id BIGSERIAL PRIMARY KEY,
    guild_id BIGINT NOT NULL,
    author_id VARCHAR(255) NOT NULL,
    author_nickname VARCHAR(50),
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    post_type VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    is_pinned BOOLEAN DEFAULT FALSE,
    view_count INTEGER DEFAULT 0,
    comment_count INTEGER DEFAULT 0,
    is_deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_guild_post_guild FOREIGN KEY (guild_id) REFERENCES guild(id) ON DELETE CASCADE,
    CONSTRAINT chk_post_type CHECK (post_type IN ('NOTICE', 'NORMAL'))
);

COMMENT ON TABLE guild_post IS '길드 게시글';
COMMENT ON COLUMN guild_post.id IS '게시글 ID';
COMMENT ON COLUMN guild_post.guild_id IS '길드 ID';
COMMENT ON COLUMN guild_post.author_id IS '작성자 ID';
COMMENT ON COLUMN guild_post.author_nickname IS '작성자 닉네임';
COMMENT ON COLUMN guild_post.title IS '제목';
COMMENT ON COLUMN guild_post.content IS '내용';
COMMENT ON COLUMN guild_post.post_type IS '게시글 유형 (NOTICE: 공지, NORMAL: 일반)';
COMMENT ON COLUMN guild_post.is_pinned IS '상단 고정 여부';
COMMENT ON COLUMN guild_post.view_count IS '조회수';
COMMENT ON COLUMN guild_post.comment_count IS '댓글 수';
COMMENT ON COLUMN guild_post.is_deleted IS '삭제 여부';
COMMENT ON COLUMN guild_post.deleted_at IS '삭제 시간';

CREATE INDEX idx_guild_post_guild ON guild_post(guild_id);
CREATE INDEX idx_guild_post_guild_created ON guild_post(guild_id, created_at DESC);
CREATE INDEX idx_guild_post_author ON guild_post(author_id);
CREATE INDEX idx_guild_post_type ON guild_post(guild_id, post_type);
CREATE INDEX idx_guild_post_pinned ON guild_post(guild_id, is_pinned DESC, created_at DESC);

-- =====================================================
-- 8. Guild Post Comment (길드 게시글 댓글)
-- =====================================================
CREATE TABLE guild_post_comment (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL,
    author_id VARCHAR(255) NOT NULL,
    author_nickname VARCHAR(50),
    content VARCHAR(1000) NOT NULL,
    parent_id BIGINT,
    is_deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_guild_post_comment_post FOREIGN KEY (post_id) REFERENCES guild_post(id) ON DELETE CASCADE,
    CONSTRAINT fk_guild_post_comment_parent FOREIGN KEY (parent_id) REFERENCES guild_post_comment(id) ON DELETE CASCADE
);

COMMENT ON TABLE guild_post_comment IS '길드 게시글 댓글';
COMMENT ON COLUMN guild_post_comment.id IS '댓글 ID';
COMMENT ON COLUMN guild_post_comment.post_id IS '게시글 ID';
COMMENT ON COLUMN guild_post_comment.author_id IS '작성자 ID';
COMMENT ON COLUMN guild_post_comment.author_nickname IS '작성자 닉네임';
COMMENT ON COLUMN guild_post_comment.content IS '댓글 내용';
COMMENT ON COLUMN guild_post_comment.parent_id IS '상위 댓글 ID (대댓글인 경우)';
COMMENT ON COLUMN guild_post_comment.is_deleted IS '삭제 여부';
COMMENT ON COLUMN guild_post_comment.deleted_at IS '삭제 시간';

CREATE INDEX idx_guild_post_comment_post ON guild_post_comment(post_id);
CREATE INDEX idx_guild_post_comment_post_created ON guild_post_comment(post_id, created_at);
CREATE INDEX idx_guild_post_comment_author ON guild_post_comment(author_id);
CREATE INDEX idx_guild_post_comment_parent ON guild_post_comment(parent_id);

-- =====================================================
-- INITIAL DATA
-- =====================================================

-- Guild Level Config (레벨 1~10 설정)
INSERT INTO guild_level_config (level, required_exp, cumulative_exp, max_members, title, description) VALUES
(1, 0, 0, 10, '신생 길드', '막 창설된 새로운 길드입니다'),
(2, 100, 100, 15, '성장하는 길드', '조금씩 성장하고 있는 길드입니다'),
(3, 300, 400, 20, '활발한 길드', '활발하게 활동 중인 길드입니다'),
(4, 600, 1000, 25, '단결된 길드', '단단한 결속력을 가진 길드입니다'),
(5, 1000, 2000, 30, '강력한 길드', '강력한 힘을 가진 길드입니다'),
(6, 1500, 3500, 35, '명예로운 길드', '명예를 쌓아가는 길드입니다'),
(7, 2000, 5500, 40, '전설적 길드', '전설이 되어가는 길드입니다'),
(8, 3000, 8500, 45, '영웅의 길드', '영웅들이 모인 길드입니다'),
(9, 4000, 12500, 50, '전설의 길드', '전설로 기록될 길드입니다'),
(10, 5000, 17500, 60, '최강의 길드', '최고의 경지에 오른 길드입니다');

-- =====================================================
-- 샘플 길드 데이터
-- =====================================================

-- 샘플 길드 (3개)
-- Guild 1: 레벨업 파이터즈 (공개) - 마스터: user-001, 멤버: user-002, user-003
-- Guild 2: 함께성장클럽 (공개) - 마스터: user-004, 멤버: user-005, user-006, user-007
-- Guild 3: 비밀 특공대 (비공개) - 마스터: user-008, 멤버: user-009
-- 길드 미가입: user-010 (가입신청중), user-011, user-012

INSERT INTO guild (id, name, description, visibility, master_id, max_members, image_url, is_active, current_level, current_exp, total_exp, created_at, modified_at) VALUES
    (1, '레벨업 파이터즈', '함께 레벨업하며 성장하는 모험가들의 길드입니다! 초보자 환영, 열정만 있으면 OK!', 'PUBLIC', 'user-001-uuid-0001-000000000001', 30, 'https://picsum.photos/seed/guild1/400/300', true, 3, 250, 650, NOW() - INTERVAL '28 days', NOW()),
    (2, '함께성장클럽', '꾸준함이 힘! 매일 미션을 완수하며 함께 성장해요. 활발한 커뮤니티 운영 중!', 'PUBLIC', 'user-004-uuid-0004-000000000004', 50, 'https://picsum.photos/seed/guild2/400/300', true, 5, 800, 2800, NOW() - INTERVAL '18 days', NOW()),
    (3, '비밀 특공대', '선택받은 모험가들만을 위한 비밀 길드. 초대를 통해서만 가입 가능합니다.', 'PRIVATE', 'user-008-uuid-0008-000000000008', 20, 'https://picsum.photos/seed/guild3/400/300', true, 4, 100, 1100, NOW() - INTERVAL '8 days', NOW());

-- 시퀀스 업데이트 (다음 INSERT 시 ID 충돌 방지)
SELECT setval('guild_id_seq', 3, true);

-- 길드 멤버 (1인 1길드 정책 준수)
INSERT INTO guild_member (id, guild_id, user_id, role, status, joined_at, created_at, modified_at) VALUES
    -- Guild 1: 레벨업 파이터즈
    (1, 1, 'user-001-uuid-0001-000000000001', 'MASTER', 'ACTIVE', NOW() - INTERVAL '28 days', NOW() - INTERVAL '28 days', NOW()),
    (2, 1, 'user-002-uuid-0002-000000000002', 'MEMBER', 'ACTIVE', NOW() - INTERVAL '26 days', NOW() - INTERVAL '26 days', NOW()),
    (3, 1, 'user-003-uuid-0003-000000000003', 'MEMBER', 'ACTIVE', NOW() - INTERVAL '24 days', NOW() - INTERVAL '24 days', NOW()),
    -- Guild 2: 함께성장클럽
    (4, 2, 'user-004-uuid-0004-000000000004', 'MASTER', 'ACTIVE', NOW() - INTERVAL '18 days', NOW() - INTERVAL '18 days', NOW()),
    (5, 2, 'user-005-uuid-0005-000000000005', 'ADMIN', 'ACTIVE', NOW() - INTERVAL '16 days', NOW() - INTERVAL '16 days', NOW()),
    (6, 2, 'user-006-uuid-0006-000000000006', 'MEMBER', 'ACTIVE', NOW() - INTERVAL '14 days', NOW() - INTERVAL '14 days', NOW()),
    (7, 2, 'user-007-uuid-0007-000000000007', 'MEMBER', 'ACTIVE', NOW() - INTERVAL '10 days', NOW() - INTERVAL '10 days', NOW()),
    -- Guild 3: 비밀 특공대
    (8, 3, 'user-008-uuid-0008-000000000008', 'MASTER', 'ACTIVE', NOW() - INTERVAL '8 days', NOW() - INTERVAL '8 days', NOW()),
    (9, 3, 'user-009-uuid-0009-000000000009', 'MEMBER', 'ACTIVE', NOW() - INTERVAL '6 days', NOW() - INTERVAL '6 days', NOW());

SELECT setval('guild_member_id_seq', 9, true);

-- 길드 가입 신청 (user-010이 Guild 1에 가입 신청 중)
INSERT INTO guild_join_request (id, guild_id, requester_id, message, status, created_at, modified_at) VALUES
    (1, 1, 'user-010-uuid-0010-000000000010', '안녕하세요! 열심히 활동하겠습니다. 가입 부탁드려요!', 'PENDING', NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days'),
    (2, 2, 'user-011-uuid-0011-000000000011', '함께 성장하고 싶습니다!', 'PENDING', NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day');

SELECT setval('guild_join_request_id_seq', 2, true);

-- 길드 채팅 메시지 샘플
INSERT INTO guild_chat_message (id, guild_id, sender_id, sender_nickname, message_type, content, created_at, modified_at) VALUES
    -- Guild 1 채팅
    (1, 1, NULL, NULL, 'SYSTEM_JOIN', 'user-002-uuid-0002-000000000002님이 길드에 가입했습니다.', NOW() - INTERVAL '26 days', NOW() - INTERVAL '26 days'),
    (2, 1, 'user-001-uuid-0001-000000000001', '길동이', 'TEXT', '철수님 환영합니다! 우리 길드에서 즐거운 시간 보내세요 😊', NOW() - INTERVAL '26 days' + INTERVAL '1 hour', NOW() - INTERVAL '26 days' + INTERVAL '1 hour'),
    (3, 1, 'user-002-uuid-0002-000000000002', '철수짱', 'TEXT', '감사합니다! 열심히 하겠습니다!', NOW() - INTERVAL '26 days' + INTERVAL '2 hours', NOW() - INTERVAL '26 days' + INTERVAL '2 hours'),
    (4, 1, NULL, NULL, 'SYSTEM_JOIN', 'user-003-uuid-0003-000000000003님이 길드에 가입했습니다.', NOW() - INTERVAL '24 days', NOW() - INTERVAL '24 days'),
    (5, 1, 'user-003-uuid-0003-000000000003', '영희님', 'TEXT', '안녕하세요~ 잘 부탁드려요!', NOW() - INTERVAL '24 days' + INTERVAL '30 minutes', NOW() - INTERVAL '24 days' + INTERVAL '30 minutes'),
    (6, 1, 'user-001-uuid-0001-000000000001', '길동이', 'TEXT', '오늘 미션 다들 완료하셨나요?', NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day'),
    (7, 1, 'user-002-uuid-0002-000000000002', '철수짱', 'TEXT', '넵! 저는 완료했어요~', NOW() - INTERVAL '1 day' + INTERVAL '30 minutes', NOW() - INTERVAL '1 day' + INTERVAL '30 minutes'),
    -- Guild 2 채팅
    (8, 2, NULL, NULL, 'SYSTEM_JOIN', 'user-005-uuid-0005-000000000005님이 길드에 가입했습니다.', NOW() - INTERVAL '16 days', NOW() - INTERVAL '16 days'),
    (9, 2, 'user-004-uuid-0004-000000000004', '지민파크', 'TEXT', '수아님 환영해요! 앞으로 함께 성장해요 💪', NOW() - INTERVAL '16 days' + INTERVAL '1 hour', NOW() - INTERVAL '16 days' + INTERVAL '1 hour'),
    (10, 2, NULL, NULL, 'SYSTEM_LEVEL_UP', '길드가 레벨 5로 레벨업했습니다!', NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days'),
    (11, 2, 'user-004-uuid-0004-000000000004', '지민파크', 'TEXT', '드디어 레벨 5 달성! 모두 수고하셨습니다! 🎉', NOW() - INTERVAL '3 days' + INTERVAL '10 minutes', NOW() - INTERVAL '3 days' + INTERVAL '10 minutes'),
    (12, 2, 'user-006-uuid-0006-000000000006', '민준킹', 'TEXT', '축하드려요~ 다들 화이팅!', NOW() - INTERVAL '3 days' + INTERVAL '20 minutes', NOW() - INTERVAL '3 days' + INTERVAL '20 minutes'),
    -- Guild 3 채팅
    (13, 3, NULL, NULL, 'SYSTEM_JOIN', 'user-009-uuid-0009-000000000009님이 길드에 초대되었습니다.', NOW() - INTERVAL '6 days', NOW() - INTERVAL '6 days'),
    (14, 3, 'user-008-uuid-0008-000000000008', '재현마스터', 'TEXT', '하늘님 환영합니다. 우리 길드 규칙 먼저 읽어주세요.', NOW() - INTERVAL '6 days' + INTERVAL '1 hour', NOW() - INTERVAL '6 days' + INTERVAL '1 hour'),
    (15, 3, 'user-009-uuid-0009-000000000009', '하늘이', 'TEXT', '네, 알겠습니다! 열심히 하겠습니다.', NOW() - INTERVAL '6 days' + INTERVAL '2 hours', NOW() - INTERVAL '6 days' + INTERVAL '2 hours');

SELECT setval('guild_chat_message_id_seq', 15, true);

-- 길드 게시글 샘플
INSERT INTO guild_post (id, guild_id, author_id, author_nickname, title, content, post_type, is_pinned, view_count, comment_count, created_at, modified_at) VALUES
    -- Guild 1 게시글
    (1, 1, 'user-001-uuid-0001-000000000001', '길동이', '[공지] 길드 가입을 환영합니다!', '레벨업 파이터즈에 오신 것을 환영합니다!\n\n우리 길드는 함께 성장하는 것을 목표로 합니다.\n\n길드 규칙:\n1. 매일 최소 1개 미션 완료하기\n2. 길드원끼리 서로 격려하기\n3. 비매너 행위 금지\n\n즐거운 모험 되세요!', 'NOTICE', true, 45, 3, NOW() - INTERVAL '28 days', NOW()),
    (2, 1, 'user-002-uuid-0002-000000000002', '철수짱', '오늘의 미션 인증합니다!', '오늘도 미션 완료했습니다!\n매일 꾸준히 하니까 실력이 느는 것 같아요.\n다들 화이팅!', 'NORMAL', false, 12, 2, NOW() - INTERVAL '5 days', NOW()),
    -- Guild 2 게시글
    (3, 2, 'user-004-uuid-0004-000000000004', '지민파크', '[공지] 함께성장클럽 운영 방침', '안녕하세요, 길드 마스터 지민입니다.\n\n우리 길드는 \"꾸준함이 힘\"이라는 모토 아래 운영됩니다.\n\n주간 목표:\n- 개인 미션 5개 이상 완료\n- 길드 채팅 참여\n\n매주 MVP를 선정하여 특별 보상을 드립니다!\n\n함께 성장해요 💪', 'NOTICE', true, 89, 7, NOW() - INTERVAL '18 days', NOW()),
    (4, 2, 'user-005-uuid-0005-000000000005', '수아링', '이번 주 MVP 발표!', '이번 주 MVP는 민준킹님입니다!\n미션 완료율 100%, 채팅 참여도 최고!\n\n다음 주도 모두 화이팅해요~', 'NORMAL', false, 34, 5, NOW() - INTERVAL '2 days', NOW()),
    (5, 2, 'user-007-uuid-0007-000000000007', '서연스타', '신규 가입자입니다 ㅎㅎ', '안녕하세요! 새로 가입한 서연입니다.\n아직 익숙하지 않은데 많이 도와주세요!\n잘 부탁드립니다 :)', 'NORMAL', false, 23, 4, NOW() - INTERVAL '9 days', NOW()),
    -- Guild 3 게시글
    (6, 3, 'user-008-uuid-0008-000000000008', '재현마스터', '[중요] 비밀 특공대 규칙', '비밀 특공대에 오신 것을 환영합니다.\n\n본 길드는 철저한 보안과 높은 활동량을 요구합니다.\n\n규칙:\n1. 길드 정보 외부 유출 금지\n2. 주 7일 중 5일 이상 활동 필수\n3. 미션 완료율 80% 이상 유지\n\n규칙 미준수 시 경고 없이 제명될 수 있습니다.', 'NOTICE', true, 15, 1, NOW() - INTERVAL '8 days', NOW());

SELECT setval('guild_post_id_seq', 6, true);

-- 길드 게시글 댓글 샘플
INSERT INTO guild_post_comment (id, post_id, author_id, author_nickname, content, created_at, modified_at) VALUES
    (1, 1, 'user-002-uuid-0002-000000000002', '철수짱', '환영해주셔서 감사합니다! 열심히 하겠습니다!', NOW() - INTERVAL '26 days', NOW()),
    (2, 1, 'user-003-uuid-0003-000000000003', '영희님', '저도 새로 왔어요~ 같이 열심히 해요!', NOW() - INTERVAL '24 days', NOW()),
    (3, 1, 'user-001-uuid-0001-000000000001', '길동이', '네~ 모두 환영합니다! 궁금한 거 있으면 물어보세요 ㅎㅎ', NOW() - INTERVAL '24 days' + INTERVAL '1 hour', NOW()),
    (4, 2, 'user-001-uuid-0001-000000000001', '길동이', '좋아요! 꾸준함이 최고입니다 👍', NOW() - INTERVAL '5 days' + INTERVAL '2 hours', NOW()),
    (5, 2, 'user-003-uuid-0003-000000000003', '영희님', '저도 오늘 완료했어요~', NOW() - INTERVAL '5 days' + INTERVAL '3 hours', NOW()),
    (6, 3, 'user-005-uuid-0005-000000000005', '수아링', '마스터님 항상 감사합니다!', NOW() - INTERVAL '18 days' + INTERVAL '1 day', NOW()),
    (7, 3, 'user-006-uuid-0006-000000000006', '민준킹', '열심히 하겠습니다!', NOW() - INTERVAL '18 days' + INTERVAL '2 days', NOW()),
    (8, 4, 'user-006-uuid-0006-000000000006', '민준킹', '감사합니다! 앞으로도 열심히 할게요!', NOW() - INTERVAL '2 days' + INTERVAL '1 hour', NOW()),
    (9, 5, 'user-004-uuid-0004-000000000004', '지민파크', '환영해요 서연님! 궁금한 거 있으면 편하게 물어보세요~', NOW() - INTERVAL '9 days' + INTERVAL '2 hours', NOW()),
    (10, 6, 'user-009-uuid-0009-000000000009', '하늘이', '네, 규칙 숙지했습니다!', NOW() - INTERVAL '6 days', NOW());

SELECT setval('guild_post_comment_id_seq', 10, true);

-- 길드 경험치 히스토리 샘플
INSERT INTO guild_experience_history (id, guild_id, source_type, contributor_id, exp_amount, description, level_before, level_after, created_at) VALUES
    -- Guild 1 경험치 히스토리
    (1, 1, 'GUILD_MISSION_EXECUTION', 'user-001-uuid-0001-000000000001', 50, '미션 완료 - 아침 운동', 1, 1, NOW() - INTERVAL '25 days'),
    (2, 1, 'GUILD_MISSION_EXECUTION', 'user-002-uuid-0002-000000000002', 50, '미션 완료 - 독서 30분', 1, 1, NOW() - INTERVAL '24 days'),
    (3, 1, 'GUILD_MISSION_FULL_COMPLETION', 'user-001-uuid-0001-000000000001', 100, '길드 미션 달성 보너스', 1, 2, NOW() - INTERVAL '20 days'),
    (4, 1, 'GUILD_MISSION_EXECUTION', 'user-003-uuid-0003-000000000003', 50, '미션 완료 - 영어 공부', 2, 2, NOW() - INTERVAL '18 days'),
    (5, 1, 'GUILD_MISSION_FULL_COMPLETION', 'user-002-uuid-0002-000000000002', 100, '길드 미션 달성 보너스', 2, 3, NOW() - INTERVAL '10 days'),
    -- Guild 2 경험치 히스토리
    (6, 2, 'GUILD_MISSION_EXECUTION', 'user-004-uuid-0004-000000000004', 100, '미션 완료 - 코딩 연습', 1, 1, NOW() - INTERVAL '17 days'),
    (7, 2, 'GUILD_MISSION_EXECUTION', 'user-005-uuid-0005-000000000005', 100, '미션 완료 - 운동', 1, 2, NOW() - INTERVAL '15 days'),
    (8, 2, 'GUILD_MISSION_FULL_COMPLETION', 'user-004-uuid-0004-000000000004', 200, '길드 미션 달성 보너스', 2, 3, NOW() - INTERVAL '12 days'),
    (9, 2, 'GUILD_MISSION_EXECUTION', 'user-006-uuid-0006-000000000006', 100, '미션 완료 - 독서', 3, 3, NOW() - INTERVAL '10 days'),
    (10, 2, 'EVENT_BONUS', NULL, 500, '신규 길드 이벤트 보너스', 3, 4, NOW() - INTERVAL '8 days'),
    (11, 2, 'GUILD_MISSION_FULL_COMPLETION', 'user-007-uuid-0007-000000000007', 200, '길드 미션 달성 보너스', 4, 5, NOW() - INTERVAL '3 days'),
    -- Guild 3 경험치 히스토리
    (12, 3, 'GUILD_MISSION_EXECUTION', 'user-008-uuid-0008-000000000008', 150, '미션 완료 - 고급 과제', 1, 2, NOW() - INTERVAL '7 days'),
    (13, 3, 'GUILD_MISSION_EXECUTION', 'user-009-uuid-0009-000000000009', 150, '미션 완료 - 특수 미션', 2, 3, NOW() - INTERVAL '5 days'),
    (14, 3, 'GUILD_MISSION_FULL_COMPLETION', 'user-008-uuid-0008-000000000008', 300, '길드 미션 달성 보너스', 3, 4, NOW() - INTERVAL '2 days');

SELECT setval('guild_experience_history_id_seq', 14, true);

-- =====================================================
-- END OF INITIALIZATION
-- =====================================================
