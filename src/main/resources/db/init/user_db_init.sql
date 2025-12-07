-- ============================================================
-- USER_DB 초기화 스크립트
-- UserService + MissionService 테이블
-- ============================================================

-- 사용자 기본 테이블
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255),
    email VARCHAR(255) NOT NULL,
    picture VARCHAR(500),
    provider VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_provider ON users(provider);

-- 사용자 경험치 테이블
CREATE TABLE IF NOT EXISTS user_experience (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL UNIQUE,
    current_level INTEGER DEFAULT 1,
    current_exp INTEGER DEFAULT 0,
    total_exp INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_user_exp_user ON user_experience(user_id);

-- 경험치 획득 이력 테이블
CREATE TABLE IF NOT EXISTS experience_history (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    source_id BIGINT,
    exp_amount INTEGER NOT NULL,
    description VARCHAR(500),
    level_before INTEGER,
    level_after INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_exp_history_user ON experience_history(user_id);
CREATE INDEX IF NOT EXISTS idx_exp_history_source ON experience_history(source_type, source_id);

-- 약관 테이블
CREATE TABLE IF NOT EXISTS terms (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(100) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    type VARCHAR(100),
    is_required BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    modified_by VARCHAR(255)
);

-- 약관 버전 테이블
CREATE TABLE IF NOT EXISTS term_versions (
    id BIGSERIAL PRIMARY KEY,
    term_id BIGINT NOT NULL REFERENCES terms(id) ON DELETE CASCADE,
    version VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    created_by VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_term_versions_term ON term_versions(term_id);

-- 사용자 약관 동의 테이블
CREATE TABLE IF NOT EXISTS user_term_agreements (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    term_version_id BIGINT NOT NULL REFERENCES term_versions(id) ON DELETE CASCADE,
    is_agreed BOOLEAN NOT NULL DEFAULT FALSE,
    agreed_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_uta_user ON user_term_agreements(user_id);

-- ============================================================
-- 친구 시스템 테이블
-- ============================================================

CREATE TABLE IF NOT EXISTS friendship (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    friend_id VARCHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requested_at TIMESTAMP,
    accepted_at TIMESTAMP,
    blocked_at TIMESTAMP,
    message VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_friendship UNIQUE (user_id, friend_id)
);

CREATE INDEX IF NOT EXISTS idx_friendship_user ON friendship(user_id);
CREATE INDEX IF NOT EXISTS idx_friendship_friend ON friendship(friend_id);
CREATE INDEX IF NOT EXISTS idx_friendship_status ON friendship(status);

-- ============================================================
-- 업적 시스템 테이블
-- ============================================================

CREATE TABLE IF NOT EXISTS title (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    description VARCHAR(200),
    rarity VARCHAR(20) NOT NULL,
    prefix VARCHAR(20),
    suffix VARCHAR(20),
    color_code VARCHAR(10),
    icon_url VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS achievement (
    id BIGSERIAL PRIMARY KEY,
    achievement_type VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    category VARCHAR(30) NOT NULL,
    icon_url VARCHAR(500),
    required_count INTEGER NOT NULL,
    reward_exp INTEGER DEFAULT 0,
    reward_title_id BIGINT REFERENCES title(id),
    reward_points INTEGER DEFAULT 0,
    is_hidden BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_achievement (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    achievement_id BIGINT REFERENCES achievement(id),
    current_count INTEGER DEFAULT 0,
    is_completed BOOLEAN DEFAULT FALSE,
    completed_at TIMESTAMP,
    is_reward_claimed BOOLEAN DEFAULT FALSE,
    reward_claimed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_achievement UNIQUE (user_id, achievement_id)
);

CREATE INDEX IF NOT EXISTS idx_user_achievement_user ON user_achievement(user_id);

CREATE TABLE IF NOT EXISTS user_title (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    title_id BIGINT REFERENCES title(id),
    acquired_at TIMESTAMP,
    is_equipped BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_title UNIQUE (user_id, title_id)
);

CREATE INDEX IF NOT EXISTS idx_user_title_user ON user_title(user_id);

CREATE TABLE IF NOT EXISTS user_stats (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL UNIQUE,
    total_mission_completions INTEGER DEFAULT 0,
    total_mission_full_completions INTEGER DEFAULT 0,
    total_guild_mission_completions INTEGER DEFAULT 0,
    current_streak INTEGER DEFAULT 0,
    max_streak INTEGER DEFAULT 0,
    last_activity_date DATE,
    total_achievements_completed INTEGER DEFAULT 0,
    total_titles_acquired INTEGER DEFAULT 0,
    ranking_points BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_user_stats_user ON user_stats(user_id);
CREATE INDEX IF NOT EXISTS idx_user_stats_ranking ON user_stats(ranking_points DESC);

-- ============================================================
-- 출석 시스템 테이블
-- ============================================================

CREATE TABLE IF NOT EXISTS attendance_record (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    attendance_date DATE NOT NULL,
    year_month VARCHAR(7),
    day_of_month INTEGER NOT NULL,
    consecutive_days INTEGER DEFAULT 1,
    reward_exp INTEGER DEFAULT 0,
    bonus_reward_exp INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_attendance UNIQUE (user_id, attendance_date)
);

CREATE INDEX IF NOT EXISTS idx_attendance_user_date ON attendance_record(user_id, attendance_date);
CREATE INDEX IF NOT EXISTS idx_attendance_user_month ON attendance_record(user_id, year_month);

CREATE TABLE IF NOT EXISTS attendance_reward_config (
    id BIGSERIAL PRIMARY KEY,
    reward_type VARCHAR(30) NOT NULL,
    required_days INTEGER,
    reward_exp INTEGER DEFAULT 0,
    reward_points INTEGER DEFAULT 0,
    reward_title_id BIGINT REFERENCES title(id),
    description VARCHAR(200),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 알림 시스템 테이블
-- ============================================================

CREATE TABLE IF NOT EXISTS notification (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    notification_type VARCHAR(30) NOT NULL,
    title VARCHAR(100) NOT NULL,
    message VARCHAR(500),
    reference_type VARCHAR(30),
    reference_id BIGINT,
    action_url VARCHAR(500),
    icon_url VARCHAR(500),
    is_read BOOLEAN DEFAULT FALSE,
    read_at TIMESTAMP,
    is_pushed BOOLEAN DEFAULT FALSE,
    pushed_at TIMESTAMP,
    expires_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_notification_user ON notification(user_id);
CREATE INDEX IF NOT EXISTS idx_notification_user_read ON notification(user_id, is_read);
CREATE INDEX IF NOT EXISTS idx_notification_created ON notification(created_at DESC);

CREATE TABLE IF NOT EXISTS notification_preference (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL UNIQUE,
    push_enabled BOOLEAN DEFAULT TRUE,
    mission_notifications BOOLEAN DEFAULT TRUE,
    achievement_notifications BOOLEAN DEFAULT TRUE,
    guild_notifications BOOLEAN DEFAULT TRUE,
    quest_notifications BOOLEAN DEFAULT TRUE,
    attendance_notifications BOOLEAN DEFAULT TRUE,
    ranking_notifications BOOLEAN DEFAULT TRUE,
    system_notifications BOOLEAN DEFAULT TRUE,
    quiet_hours_enabled BOOLEAN DEFAULT FALSE,
    quiet_hours_start VARCHAR(5),
    quiet_hours_end VARCHAR(5),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_notification_pref_user ON notification_preference(user_id);

-- ============================================================
-- 퀘스트 시스템 테이블
-- ============================================================

CREATE TABLE IF NOT EXISTS quest (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    quest_type VARCHAR(20) NOT NULL,
    category VARCHAR(20) NOT NULL,
    action_type VARCHAR(30) NOT NULL,
    required_count INTEGER DEFAULT 1,
    reward_exp INTEGER DEFAULT 0,
    reward_points INTEGER DEFAULT 0,
    reward_title_id BIGINT REFERENCES title(id),
    icon_url VARCHAR(500),
    sort_order INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_quest_type ON quest(quest_type);
CREATE INDEX IF NOT EXISTS idx_quest_active ON quest(is_active);

CREATE TABLE IF NOT EXISTS user_quest (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    quest_id BIGINT REFERENCES quest(id),
    period_key VARCHAR(20),
    current_count INTEGER DEFAULT 0,
    is_completed BOOLEAN DEFAULT FALSE,
    completed_at TIMESTAMP,
    is_reward_claimed BOOLEAN DEFAULT FALSE,
    reward_claimed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_quest UNIQUE (user_id, quest_id, period_key)
);

CREATE INDEX IF NOT EXISTS idx_user_quest_user ON user_quest(user_id);
CREATE INDEX IF NOT EXISTS idx_user_quest_period ON user_quest(period_key);

-- ============================================================
-- 활동 피드 시스템 테이블
-- ============================================================

CREATE TABLE IF NOT EXISTS activity_feed (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    user_nickname VARCHAR(50),
    user_profile_image_url VARCHAR(500),
    activity_type VARCHAR(30) NOT NULL,
    title VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    reference_type VARCHAR(30),
    reference_id BIGINT,
    reference_name VARCHAR(100),
    guild_id BIGINT,
    image_url VARCHAR(500),
    icon_url VARCHAR(500),
    visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    like_count INTEGER DEFAULT 0,
    comment_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_feed_user ON activity_feed(user_id);
CREATE INDEX IF NOT EXISTS idx_feed_created ON activity_feed(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_feed_visibility ON activity_feed(visibility);
CREATE INDEX IF NOT EXISTS idx_feed_guild ON activity_feed(guild_id);

CREATE TABLE IF NOT EXISTS feed_like (
    id BIGSERIAL PRIMARY KEY,
    feed_id BIGINT NOT NULL REFERENCES activity_feed(id) ON DELETE CASCADE,
    user_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_feed_like UNIQUE (feed_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_like_feed ON feed_like(feed_id);
CREATE INDEX IF NOT EXISTS idx_like_user ON feed_like(user_id);

CREATE TABLE IF NOT EXISTS feed_comment (
    id BIGSERIAL PRIMARY KEY,
    feed_id BIGINT NOT NULL REFERENCES activity_feed(id) ON DELETE CASCADE,
    user_id VARCHAR(36) NOT NULL,
    user_nickname VARCHAR(50),
    content VARCHAR(500) NOT NULL,
    is_deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_comment_feed ON feed_comment(feed_id);
CREATE INDEX IF NOT EXISTS idx_comment_user ON feed_comment(user_id);
CREATE INDEX IF NOT EXISTS idx_comment_created ON feed_comment(created_at DESC);

-- ============================================================
-- 미션 시스템 테이블 (MissionService)
-- ============================================================

CREATE TABLE IF NOT EXISTS mission (
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
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_mission_creator ON mission(creator_id);
CREATE INDEX IF NOT EXISTS idx_mission_status ON mission(status);
CREATE INDEX IF NOT EXISTS idx_mission_visibility ON mission(visibility);
CREATE INDEX IF NOT EXISTS idx_mission_guild ON mission(guild_id);

CREATE TABLE IF NOT EXISTS mission_participant (
    id BIGSERIAL PRIMARY KEY,
    mission_id BIGINT NOT NULL REFERENCES mission(id) ON DELETE CASCADE,
    user_id VARCHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL,
    progress INTEGER,
    joined_at TIMESTAMP,
    completed_at TIMESTAMP,
    note VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_mission_participant UNIQUE (mission_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_participant_mission ON mission_participant(mission_id);
CREATE INDEX IF NOT EXISTS idx_participant_user ON mission_participant(user_id);
CREATE INDEX IF NOT EXISTS idx_participant_status ON mission_participant(status);

CREATE TABLE IF NOT EXISTS mission_execution (
    id BIGSERIAL PRIMARY KEY,
    participant_id BIGINT NOT NULL REFERENCES mission_participant(id) ON DELETE CASCADE,
    execution_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    completed_at TIMESTAMP,
    exp_earned INTEGER DEFAULT 0,
    note VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_mission_execution UNIQUE (participant_id, execution_date)
);

CREATE INDEX IF NOT EXISTS idx_execution_participant ON mission_execution(participant_id);
CREATE INDEX IF NOT EXISTS idx_execution_date ON mission_execution(execution_date);
CREATE INDEX IF NOT EXISTS idx_execution_status ON mission_execution(status);

-- ============================================================
-- Saga 시스템 테이블 (분산 트랜잭션 관리)
-- ============================================================

-- Saga 인스턴스 테이블
CREATE TABLE IF NOT EXISTS saga_instance (
    saga_id VARCHAR(36) PRIMARY KEY,
    saga_type VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'STARTED',
    executor_id VARCHAR(255),
    current_step VARCHAR(100),
    current_step_index INTEGER DEFAULT 0,
    context_data TEXT,
    compensation_data TEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    failure_reason VARCHAR(1000),
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_saga_type ON saga_instance(saga_type);
CREATE INDEX IF NOT EXISTS idx_saga_status ON saga_instance(status);
CREATE INDEX IF NOT EXISTS idx_saga_created ON saga_instance(created_at);
CREATE INDEX IF NOT EXISTS idx_saga_executor ON saga_instance(executor_id);

-- Saga Step 실행 로그 테이블
CREATE TABLE IF NOT EXISTS saga_step_log (
    id BIGSERIAL PRIMARY KEY,
    saga_id VARCHAR(36) NOT NULL,
    step_name VARCHAR(100) NOT NULL,
    step_index INTEGER,
    status VARCHAR(20) NOT NULL,
    execution_type VARCHAR(20) DEFAULT 'FORWARD',
    duration_ms BIGINT,
    input_data TEXT,
    output_data TEXT,
    error_message VARCHAR(1000),
    stack_trace TEXT,
    retry_attempt INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_step_log_saga ON saga_step_log(saga_id);
CREATE INDEX IF NOT EXISTS idx_step_log_status ON saga_step_log(status);
CREATE INDEX IF NOT EXISTS idx_step_log_created ON saga_step_log(created_at);

-- ============================================================
-- 초기 데이터 삽입
-- ============================================================

-- 기본 레벨별 칭호 삽입 (Title)
INSERT INTO title (name, description, rarity, prefix, color_code, is_active) VALUES
    ('초보 모험가', '레벨 1-9 사용자', 'COMMON', '[초보]', '#808080', true),
    ('견습 모험가', '레벨 10-19 사용자', 'COMMON', '[견습]', '#A0A0A0', true),
    ('정식 모험가', '레벨 20-29 사용자', 'UNCOMMON', '[정식]', '#00FF00', true),
    ('숙련 모험가', '레벨 30-39 사용자', 'UNCOMMON', '[숙련]', '#32CD32', true),
    ('베테랑 모험가', '레벨 40-49 사용자', 'RARE', '[베테랑]', '#0000FF', true),
    ('엘리트 모험가', '레벨 50-59 사용자', 'RARE', '[엘리트]', '#1E90FF', true),
    ('영웅 모험가', '레벨 60-69 사용자', 'EPIC', '[영웅]', '#9400D3', true),
    ('전설의 모험가', '레벨 70-79 사용자', 'EPIC', '[전설]', '#8B008B', true),
    ('신화의 모험가', '레벨 80-89 사용자', 'LEGENDARY', '[신화]', '#FFD700', true),
    ('초월자', '레벨 90+ 사용자', 'LEGENDARY', '[초월]', '#FF4500', true)
ON CONFLICT DO NOTHING;

-- 기본 업적 삽입
INSERT INTO achievement (achievement_type, name, description, category, required_count, reward_exp, is_active) VALUES
    ('FIRST_MISSION', '첫 미션 참여', '첫 미션에 참여하세요', 'MISSION', 1, 50, true),
    ('MISSION_10', '미션 마스터 I', '미션 10개 완료', 'MISSION', 10, 100, true),
    ('MISSION_50', '미션 마스터 II', '미션 50개 완료', 'MISSION', 50, 300, true),
    ('MISSION_100', '미션 마스터 III', '미션 100개 완료', 'MISSION', 100, 500, true),
    ('STREAK_7', '일주일 연속', '7일 연속 출석', 'ATTENDANCE', 7, 100, true),
    ('STREAK_30', '한 달 연속', '30일 연속 출석', 'ATTENDANCE', 30, 500, true),
    ('FIRST_FRIEND', '첫 친구', '첫 친구를 만드세요', 'SOCIAL', 1, 30, true),
    ('FRIENDS_10', '인싸', '친구 10명 달성', 'SOCIAL', 10, 100, true),
    ('FIRST_GUILD', '길드 합류', '첫 길드에 가입하세요', 'GUILD', 1, 50, true),
    ('GUILD_MASTER', '길드 마스터', '길드를 생성하세요', 'GUILD', 1, 200, true)
ON CONFLICT DO NOTHING;

-- 기본 퀘스트 삽입
INSERT INTO quest (name, description, quest_type, category, action_type, required_count, reward_exp, is_active) VALUES
    ('오늘의 미션', '오늘 미션 1개 완료하기', 'DAILY', 'MISSION', 'COMPLETE_MISSION', 1, 20, true),
    ('출석 체크', '오늘 출석 체크하기', 'DAILY', 'ATTENDANCE', 'DAILY_ATTENDANCE', 1, 10, true),
    ('친구와 인사', '친구에게 메시지 보내기', 'DAILY', 'SOCIAL', 'SEND_MESSAGE', 1, 10, true),
    ('주간 미션 달인', '이번 주 미션 5개 완료하기', 'WEEKLY', 'MISSION', 'COMPLETE_MISSION', 5, 100, true),
    ('주간 출석왕', '이번 주 5일 출석하기', 'WEEKLY', 'ATTENDANCE', 'DAILY_ATTENDANCE', 5, 50, true)
ON CONFLICT DO NOTHING;

-- 기본 출석 보상 설정
INSERT INTO attendance_reward_config (reward_type, required_days, reward_exp, description, is_active) VALUES
    ('DAILY', 1, 10, '일일 출석 보상', true),
    ('STREAK_3', 3, 20, '3일 연속 출석 보너스', true),
    ('STREAK_7', 7, 50, '7일 연속 출석 보너스', true),
    ('STREAK_14', 14, 100, '14일 연속 출석 보너스', true),
    ('STREAK_30', 30, 300, '30일 연속 출석 보너스', true),
    ('MONTHLY_PERFECT', NULL, 500, '월간 개근 보너스', true)
ON CONFLICT DO NOTHING;
