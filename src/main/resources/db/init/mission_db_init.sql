-- ============================================================
-- MISSION_DB 초기화 스크립트
-- MissionService 테이블 전용
-- ============================================================

-- ============================================================
-- DROP EXISTING TABLES (for clean initialization)
-- ============================================================
DROP TABLE IF EXISTS mission_execution CASCADE;
DROP TABLE IF EXISTS mission_participant CASCADE;
DROP TABLE IF EXISTS mission CASCADE;

-- ============================================================
-- 1. 미션 테이블
-- ============================================================
CREATE TABLE mission (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL,
    visibility VARCHAR(20) NOT NULL,
    type VARCHAR(20) NOT NULL,
    creator_id VARCHAR(36) NOT NULL,
    guild_id VARCHAR(36),
    max_participants INTEGER,
    start_date TIMESTAMP,
    end_date TIMESTAMP,
    mission_interval VARCHAR(20) DEFAULT 'DAILY',
    duration_days INTEGER,
    exp_per_completion INTEGER DEFAULT 10,
    bonus_exp_on_full_completion INTEGER DEFAULT 50,
    guild_exp_per_completion INTEGER DEFAULT 5,
    guild_bonus_exp_on_full_completion INTEGER DEFAULT 20,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_mission_status CHECK (status IN ('DRAFT', 'OPEN', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_mission_visibility CHECK (visibility IN ('PUBLIC', 'PRIVATE', 'GUILD_ONLY')),
    CONSTRAINT chk_mission_type CHECK (type IN ('PERSONAL', 'GUILD')),
    CONSTRAINT chk_mission_interval CHECK (mission_interval IN ('DAILY', 'WEEKLY', 'MONTHLY'))
);

CREATE INDEX idx_mission_creator ON mission(creator_id);
CREATE INDEX idx_mission_status ON mission(status);
CREATE INDEX idx_mission_visibility ON mission(visibility);
CREATE INDEX idx_mission_guild ON mission(guild_id);
CREATE INDEX idx_mission_type ON mission(type);
CREATE INDEX idx_mission_interval ON mission(mission_interval);
CREATE INDEX idx_mission_start_date ON mission(start_date);
CREATE INDEX idx_mission_end_date ON mission(end_date);

COMMENT ON TABLE mission IS '미션';
COMMENT ON COLUMN mission.id IS '미션 ID';
COMMENT ON COLUMN mission.title IS '미션 제목';
COMMENT ON COLUMN mission.description IS '미션 설명';
COMMENT ON COLUMN mission.status IS '미션 상태 (DRAFT, OPEN, IN_PROGRESS, COMPLETED, CANCELLED)';
COMMENT ON COLUMN mission.visibility IS '공개 여부 (PUBLIC, PRIVATE, GUILD_ONLY)';
COMMENT ON COLUMN mission.type IS '미션 타입 (PERSONAL, GUILD)';
COMMENT ON COLUMN mission.creator_id IS '생성자 ID';
COMMENT ON COLUMN mission.guild_id IS '길드 ID (길드 미션인 경우)';
COMMENT ON COLUMN mission.max_participants IS '최대 참여 인원';
COMMENT ON COLUMN mission.start_date IS '미션 시작일';
COMMENT ON COLUMN mission.end_date IS '미션 종료일';
COMMENT ON COLUMN mission.mission_interval IS '미션 수행 주기 (DAILY, WEEKLY, MONTHLY)';
COMMENT ON COLUMN mission.duration_days IS '미션 기간 (일수)';
COMMENT ON COLUMN mission.exp_per_completion IS '1회 완료시 경험치';
COMMENT ON COLUMN mission.bonus_exp_on_full_completion IS '전체 완료시 보너스 경험치';
COMMENT ON COLUMN mission.guild_exp_per_completion IS '1회 완료시 길드 경험치';
COMMENT ON COLUMN mission.guild_bonus_exp_on_full_completion IS '전체 완료시 길드 보너스 경험치';

-- ============================================================
-- 2. 미션 참여자 테이블
-- ============================================================
CREATE TABLE mission_participant (
    id BIGSERIAL PRIMARY KEY,
    mission_id BIGINT NOT NULL REFERENCES mission(id) ON DELETE CASCADE,
    user_id VARCHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL,
    progress INTEGER DEFAULT 0,
    joined_at TIMESTAMP,
    completed_at TIMESTAMP,
    note VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_mission_participant UNIQUE (mission_id, user_id),
    CONSTRAINT chk_participant_status CHECK (status IN ('PENDING', 'ACCEPTED', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'WITHDRAWN')),
    CONSTRAINT chk_participant_progress CHECK (progress >= 0 AND progress <= 100)
);

CREATE INDEX idx_participant_mission ON mission_participant(mission_id);
CREATE INDEX idx_participant_user ON mission_participant(user_id);
CREATE INDEX idx_participant_status ON mission_participant(status);
CREATE INDEX idx_participant_joined ON mission_participant(joined_at);

COMMENT ON TABLE mission_participant IS '미션 참여자';
COMMENT ON COLUMN mission_participant.id IS '참여자 ID';
COMMENT ON COLUMN mission_participant.mission_id IS '미션 ID';
COMMENT ON COLUMN mission_participant.user_id IS '사용자 ID';
COMMENT ON COLUMN mission_participant.status IS '참여 상태 (PENDING, ACCEPTED, IN_PROGRESS, COMPLETED, FAILED, WITHDRAWN)';
COMMENT ON COLUMN mission_participant.progress IS '진행률 (0-100)';
COMMENT ON COLUMN mission_participant.joined_at IS '참여 일시';
COMMENT ON COLUMN mission_participant.completed_at IS '완료 일시';
COMMENT ON COLUMN mission_participant.note IS '메모';

-- ============================================================
-- 3. 미션 수행 기록 테이블
-- ============================================================
CREATE TABLE mission_execution (
    id BIGSERIAL PRIMARY KEY,
    participant_id BIGINT NOT NULL REFERENCES mission_participant(id) ON DELETE CASCADE,
    execution_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    completed_at TIMESTAMP,
    exp_earned INTEGER DEFAULT 0,
    note VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_execution_participant_date UNIQUE (participant_id, execution_date),
    CONSTRAINT chk_execution_status CHECK (status IN ('PENDING', 'COMPLETED', 'MISSED'))
);

CREATE INDEX idx_execution_participant ON mission_execution(participant_id);
CREATE INDEX idx_execution_date ON mission_execution(execution_date);
CREATE INDEX idx_execution_status ON mission_execution(status);
CREATE INDEX idx_execution_completed ON mission_execution(completed_at);

COMMENT ON TABLE mission_execution IS '미션 수행 기록';
COMMENT ON COLUMN mission_execution.id IS '수행 기록 ID';
COMMENT ON COLUMN mission_execution.participant_id IS '참여자 ID';
COMMENT ON COLUMN mission_execution.execution_date IS '수행 예정 일자';
COMMENT ON COLUMN mission_execution.status IS '수행 상태 (PENDING, COMPLETED, MISSED)';
COMMENT ON COLUMN mission_execution.completed_at IS '완료 일시';
COMMENT ON COLUMN mission_execution.exp_earned IS '획득한 경험치';
COMMENT ON COLUMN mission_execution.note IS '메모';

-- ============================================================
-- 초기 데이터 삽입
-- ============================================================

-- 샘플 미션 데이터 (개발/테스트용)
-- INSERT INTO mission (title, description, status, visibility, type, creator_id, mission_interval, duration_days, exp_per_completion) VALUES
--     ('매일 운동하기', '하루 30분 이상 운동하기', 'OPEN', 'PUBLIC', 'PERSONAL', 'system', 'DAILY', 30, 15),
--     ('독서 챌린지', '매주 책 1권 읽기', 'OPEN', 'PUBLIC', 'PERSONAL', 'system', 'WEEKLY', 28, 30),
--     ('영어 공부', '매일 영어 단어 10개 암기', 'OPEN', 'PUBLIC', 'PERSONAL', 'system', 'DAILY', 30, 10);

-- ============================================================
-- END OF INITIALIZATION
-- ============================================================
