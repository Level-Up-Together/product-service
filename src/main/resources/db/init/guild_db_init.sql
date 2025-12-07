-- =====================================================
-- Guild DB Initialization SQL
-- Database: guild_db
-- Services: GuildService
-- =====================================================

-- =====================================================
-- DROP EXISTING TABLES (for clean initialization)
-- =====================================================
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
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

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
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

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
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

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
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

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
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

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
-- END OF INITIALIZATION
-- =====================================================
