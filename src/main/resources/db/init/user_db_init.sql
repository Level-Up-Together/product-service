-- ============================================================
-- USER_DB 초기화 스크립트
-- UserService 테이블 전용
-- ============================================================

-- ============================================================
-- DROP EXISTING TABLES (for clean initialization)
-- ============================================================
DROP TABLE IF EXISTS feed_comment CASCADE;
DROP TABLE IF EXISTS feed_like CASCADE;
DROP TABLE IF EXISTS activity_feed CASCADE;
DROP TABLE IF EXISTS user_quest CASCADE;
DROP TABLE IF EXISTS quest CASCADE;
DROP TABLE IF EXISTS notification_preference CASCADE;
DROP TABLE IF EXISTS notification CASCADE;
DROP TABLE IF EXISTS attendance_reward_config CASCADE;
DROP TABLE IF EXISTS attendance_record CASCADE;
DROP TABLE IF EXISTS user_stats CASCADE;
DROP TABLE IF EXISTS user_title CASCADE;
DROP TABLE IF EXISTS user_achievement CASCADE;
DROP TABLE IF EXISTS achievement CASCADE;
DROP TABLE IF EXISTS title CASCADE;
DROP TABLE IF EXISTS friendship CASCADE;
DROP TABLE IF EXISTS user_term_agreements CASCADE;
DROP TABLE IF EXISTS term_versions CASCADE;
DROP TABLE IF EXISTS terms CASCADE;
DROP TABLE IF EXISTS experience_history CASCADE;
DROP TABLE IF EXISTS user_experience CASCADE;
DROP TABLE IF EXISTS users CASCADE;

-- ============================================================
-- 1. 사용자 기본 테이블
-- ============================================================
CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255),
    nickname VARCHAR(50),
    email VARCHAR(255) NOT NULL,
    picture VARCHAR(500),
    provider VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_provider ON users(provider);
CREATE INDEX idx_users_nickname ON users(nickname);

COMMENT ON TABLE users IS '사용자';
COMMENT ON COLUMN users.id IS '사용자 ID (UUID)';
COMMENT ON COLUMN users.name IS '이름';
COMMENT ON COLUMN users.nickname IS '닉네임 (표시용)';
COMMENT ON COLUMN users.email IS '이메일';
COMMENT ON COLUMN users.picture IS '프로필 이미지 URL';
COMMENT ON COLUMN users.provider IS 'OAuth 제공자 (GOOGLE, KAKAO, APPLE)';

-- ============================================================
-- 2. 사용자 경험치 테이블
-- ============================================================
CREATE TABLE user_experience (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL UNIQUE,
    current_level INTEGER DEFAULT 1,
    current_exp INTEGER DEFAULT 0,
    total_exp INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_exp_user ON user_experience(user_id);

COMMENT ON TABLE user_experience IS '사용자 경험치';
COMMENT ON COLUMN user_experience.user_id IS '사용자 ID';
COMMENT ON COLUMN user_experience.current_level IS '현재 레벨';
COMMENT ON COLUMN user_experience.current_exp IS '현재 레벨에서의 경험치';
COMMENT ON COLUMN user_experience.total_exp IS '총 누적 경험치';

-- ============================================================
-- 3. 경험치 획득 이력 테이블
-- ============================================================
CREATE TABLE experience_history (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    source_id BIGINT,
    exp_amount INTEGER NOT NULL,
    description VARCHAR(500),
    category_name VARCHAR(50),
    level_before INTEGER,
    level_after INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_exp_history_user ON experience_history(user_id);
CREATE INDEX idx_exp_history_source ON experience_history(source_type, source_id);
CREATE INDEX idx_exp_history_created ON experience_history(created_at);
CREATE INDEX idx_exp_history_category ON experience_history(category_name);

COMMENT ON TABLE experience_history IS '경험치 획득 이력';
COMMENT ON COLUMN experience_history.source_type IS '경험치 획득 유형 (MISSION_EXECUTION, ATTENDANCE, QUEST, ACHIEVEMENT 등)';
COMMENT ON COLUMN experience_history.source_id IS '경험치 획득 소스 ID';
COMMENT ON COLUMN experience_history.exp_amount IS '획득/차감 경험치';
COMMENT ON COLUMN experience_history.category_name IS '카테고리명 (미션 카테고리)';
COMMENT ON COLUMN experience_history.level_before IS '획득 전 레벨';
COMMENT ON COLUMN experience_history.level_after IS '획득 후 레벨';

-- ============================================================
-- 4. 약관 테이블
-- ============================================================
CREATE TABLE terms (
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

COMMENT ON TABLE terms IS '약관';
COMMENT ON COLUMN terms.code IS '약관 코드';
COMMENT ON COLUMN terms.is_required IS '필수 동의 여부';

CREATE TABLE term_versions (
    id BIGSERIAL PRIMARY KEY,
    term_id BIGINT NOT NULL REFERENCES terms(id) ON DELETE CASCADE,
    version VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    created_by VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_term_versions_term ON term_versions(term_id);

COMMENT ON TABLE term_versions IS '약관 버전';

CREATE TABLE user_term_agreements (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    term_version_id BIGINT NOT NULL REFERENCES term_versions(id) ON DELETE CASCADE,
    is_agreed BOOLEAN NOT NULL DEFAULT FALSE,
    agreed_at TIMESTAMP
);

CREATE INDEX idx_uta_user ON user_term_agreements(user_id);

COMMENT ON TABLE user_term_agreements IS '사용자 약관 동의';

-- ============================================================
-- 5. 친구 시스템 테이블
-- ============================================================
CREATE TABLE friendship (
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
    CONSTRAINT uk_friendship UNIQUE (user_id, friend_id),
    CONSTRAINT chk_friendship_status CHECK (status IN ('PENDING', 'ACCEPTED', 'BLOCKED', 'REJECTED'))
);

CREATE INDEX idx_friendship_user ON friendship(user_id);
CREATE INDEX idx_friendship_friend ON friendship(friend_id);
CREATE INDEX idx_friendship_status ON friendship(status);

COMMENT ON TABLE friendship IS '친구 관계';
COMMENT ON COLUMN friendship.status IS '친구 상태 (PENDING, ACCEPTED, BLOCKED, REJECTED)';

-- ============================================================
-- 6. 업적/칭호 시스템 테이블
-- ============================================================
CREATE TABLE title (
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
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_title_rarity CHECK (rarity IN ('COMMON', 'UNCOMMON', 'RARE', 'EPIC', 'LEGENDARY'))
);

COMMENT ON TABLE title IS '칭호';
COMMENT ON COLUMN title.rarity IS '희귀도 (COMMON, UNCOMMON, RARE, EPIC, LEGENDARY)';

CREATE TABLE achievement (
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
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_achievement_category CHECK (category IN ('MISSION', 'ATTENDANCE', 'SOCIAL', 'GUILD', 'SPECIAL'))
);

COMMENT ON TABLE achievement IS '업적';
COMMENT ON COLUMN achievement.achievement_type IS '업적 타입 코드';
COMMENT ON COLUMN achievement.category IS '카테고리 (MISSION, ATTENDANCE, SOCIAL, GUILD, SPECIAL)';

CREATE TABLE user_achievement (
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

CREATE INDEX idx_user_achievement_user ON user_achievement(user_id);

COMMENT ON TABLE user_achievement IS '사용자 업적 진행';

CREATE TABLE user_title (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    title_id BIGINT REFERENCES title(id),
    acquired_at TIMESTAMP,
    is_equipped BOOLEAN NOT NULL DEFAULT FALSE,
    equipped_position VARCHAR(10),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_title UNIQUE (user_id, title_id),
    CONSTRAINT chk_equipped_position CHECK (equipped_position IS NULL OR equipped_position IN ('LEFT', 'RIGHT'))
);

CREATE INDEX idx_user_title_user ON user_title(user_id);
CREATE INDEX idx_user_title_equipped ON user_title(user_id, is_equipped) WHERE is_equipped = TRUE;

COMMENT ON TABLE user_title IS '사용자 보유 칭호';
COMMENT ON COLUMN user_title.equipped_position IS '장착 위치 (LEFT: 좌측, RIGHT: 우측)';

CREATE TABLE user_stats (
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

CREATE INDEX idx_user_stats_user ON user_stats(user_id);
CREATE INDEX idx_user_stats_ranking ON user_stats(ranking_points DESC);

COMMENT ON TABLE user_stats IS '사용자 통계';

-- ============================================================
-- 7. 출석 시스템 테이블
-- ============================================================
CREATE TABLE attendance_record (
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

CREATE INDEX idx_attendance_user_date ON attendance_record(user_id, attendance_date);
CREATE INDEX idx_attendance_user_month ON attendance_record(user_id, year_month);

COMMENT ON TABLE attendance_record IS '출석 기록';

CREATE TABLE attendance_reward_config (
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

COMMENT ON TABLE attendance_reward_config IS '출석 보상 설정';

-- ============================================================
-- 8. 알림 시스템 테이블
-- ============================================================
CREATE TABLE notification (
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

CREATE INDEX idx_notification_user ON notification(user_id);
CREATE INDEX idx_notification_user_read ON notification(user_id, is_read);
CREATE INDEX idx_notification_created ON notification(created_at DESC);

COMMENT ON TABLE notification IS '알림';

CREATE TABLE notification_preference (
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

COMMENT ON TABLE notification_preference IS '알림 설정';

-- ============================================================
-- 9. 퀘스트 시스템 테이블
-- ============================================================
CREATE TABLE quest (
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
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_quest_type CHECK (quest_type IN ('DAILY', 'WEEKLY', 'MONTHLY', 'SPECIAL'))
);

CREATE INDEX idx_quest_type ON quest(quest_type);
CREATE INDEX idx_quest_active ON quest(is_active);

COMMENT ON TABLE quest IS '퀘스트';
COMMENT ON COLUMN quest.quest_type IS '퀘스트 유형 (DAILY, WEEKLY, MONTHLY, SPECIAL)';

CREATE TABLE user_quest (
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

CREATE INDEX idx_user_quest_user ON user_quest(user_id);
CREATE INDEX idx_user_quest_period ON user_quest(period_key);

COMMENT ON TABLE user_quest IS '사용자 퀘스트 진행';

-- ============================================================
-- 10. 활동 피드 시스템 테이블
-- ============================================================
CREATE TABLE activity_feed (
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
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_feed_visibility CHECK (visibility IN ('PUBLIC', 'FRIENDS_ONLY', 'GUILD_ONLY', 'PRIVATE'))
);

CREATE INDEX idx_feed_user ON activity_feed(user_id);
CREATE INDEX idx_feed_created ON activity_feed(created_at DESC);
CREATE INDEX idx_feed_visibility ON activity_feed(visibility);
CREATE INDEX idx_feed_guild ON activity_feed(guild_id);

COMMENT ON TABLE activity_feed IS '활동 피드';

CREATE TABLE feed_like (
    id BIGSERIAL PRIMARY KEY,
    feed_id BIGINT NOT NULL REFERENCES activity_feed(id) ON DELETE CASCADE,
    user_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_feed_like UNIQUE (feed_id, user_id)
);

CREATE INDEX idx_like_feed ON feed_like(feed_id);
CREATE INDEX idx_like_user ON feed_like(user_id);

COMMENT ON TABLE feed_like IS '피드 좋아요';

CREATE TABLE feed_comment (
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

CREATE INDEX idx_comment_feed ON feed_comment(feed_id);
CREATE INDEX idx_comment_user ON feed_comment(user_id);
CREATE INDEX idx_comment_created ON feed_comment(created_at DESC);

COMMENT ON TABLE feed_comment IS '피드 댓글';

-- ============================================================
-- 11. 홈 배너 시스템 테이블
-- ============================================================
CREATE TABLE home_banner (
    id BIGSERIAL PRIMARY KEY,
    banner_type VARCHAR(30) NOT NULL,
    title VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    image_url VARCHAR(500),
    link_type VARCHAR(30),
    link_url VARCHAR(500),
    guild_id BIGINT,
    sort_order INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    start_date TIMESTAMP,
    end_date TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_banner_type CHECK (banner_type IN ('GUILD_RECRUIT', 'EVENT', 'NOTICE', 'AD'))
);

CREATE INDEX idx_banner_active ON home_banner(is_active, start_date, end_date);
CREATE INDEX idx_banner_type ON home_banner(banner_type);
CREATE INDEX idx_banner_guild ON home_banner(guild_id);

COMMENT ON TABLE home_banner IS '홈 배너';
COMMENT ON COLUMN home_banner.banner_type IS '배너 유형 (GUILD_RECRUIT: 길드모집, EVENT: 이벤트, NOTICE: 공지, AD: 광고)';
COMMENT ON COLUMN home_banner.link_type IS '링크 유형 (GUILD, MISSION, EXTERNAL, INTERNAL)';
COMMENT ON COLUMN home_banner.guild_id IS '길드 모집 배너인 경우 연결된 길드 ID';

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
    ('초월자', '레벨 90+ 사용자', 'LEGENDARY', '[초월]', '#FF4500', true);

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
    ('GUILD_MASTER', '길드 마스터', '길드를 생성하세요', 'GUILD', 1, 200, true);

-- 기본 퀘스트 삽입
INSERT INTO quest (name, description, quest_type, category, action_type, required_count, reward_exp, is_active) VALUES
    ('오늘의 미션', '오늘 미션 1개 완료하기', 'DAILY', 'MISSION', 'COMPLETE_MISSION', 1, 20, true),
    ('출석 체크', '오늘 출석 체크하기', 'DAILY', 'ATTENDANCE', 'DAILY_ATTENDANCE', 1, 10, true),
    ('친구와 인사', '친구에게 메시지 보내기', 'DAILY', 'SOCIAL', 'SEND_MESSAGE', 1, 10, true),
    ('주간 미션 달인', '이번 주 미션 5개 완료하기', 'WEEKLY', 'MISSION', 'COMPLETE_MISSION', 5, 100, true),
    ('주간 출석왕', '이번 주 5일 출석하기', 'WEEKLY', 'ATTENDANCE', 'DAILY_ATTENDANCE', 5, 50, true);

-- 기본 출석 보상 설정
INSERT INTO attendance_reward_config (reward_type, required_days, reward_exp, description, is_active) VALUES
    ('DAILY', 1, 10, '일일 출석 보상', true),
    ('STREAK_3', 3, 20, '3일 연속 출석 보너스', true),
    ('STREAK_7', 7, 50, '7일 연속 출석 보너스', true),
    ('STREAK_14', 14, 100, '14일 연속 출석 보너스', true),
    ('STREAK_30', 30, 300, '30일 연속 출석 보너스', true),
    ('MONTHLY_PERFECT', NULL, 500, '월간 개근 보너스', true);

-- 기본 약관 삽입
INSERT INTO terms (code, title, description, type, is_required) VALUES
    ('SERVICE_TERMS', '서비스 이용약관', 'Level Up Together 서비스 이용에 관한 약관입니다.', 'SERVICE', true),
    ('PRIVACY_POLICY', '개인정보 처리방침', '개인정보 수집, 이용, 제공에 관한 약관입니다.', 'PRIVACY', true),
    ('MARKETING_CONSENT', '마케팅 정보 수신 동의', '이벤트, 프로모션 등 마케팅 정보 수신에 관한 약관입니다.', 'MARKETING', false);

-- 약관 버전 삽입
INSERT INTO term_versions (term_id, version, content) VALUES
    (1, '1.0', '## 서비스 이용약관

제1조 (목적)
이 약관은 Level Up Together(이하 "서비스")의 이용과 관련하여 회사와 회원 간의 권리, 의무 및 책임사항을 규정함을 목적으로 합니다.

제2조 (정의)
1. "서비스"란 회사가 제공하는 미션 관리 및 습관 형성 플랫폼을 의미합니다.
2. "회원"이란 서비스에 가입하여 이용하는 자를 말합니다.

제3조 (약관의 효력)
1. 이 약관은 서비스 화면에 게시하거나 기타의 방법으로 회원에게 공지함으로써 효력이 발생합니다.
2. 회사는 필요한 경우 관련 법령을 위반하지 않는 범위에서 이 약관을 개정할 수 있습니다.'),
    (2, '1.0', '## 개인정보 처리방침

1. 수집하는 개인정보 항목
- 필수항목: 이메일, 닉네임, 프로필 이미지
- 선택항목: 생년월일, 성별

2. 개인정보의 수집 및 이용목적
- 서비스 제공 및 회원 관리
- 서비스 개선 및 신규 서비스 개발
- 마케팅 및 광고에의 활용 (동의 시)

3. 개인정보의 보유 및 이용기간
- 회원 탈퇴 시까지 또는 법령에서 정한 기간까지

4. 개인정보의 파기절차 및 방법
- 회원 탈퇴 시 지체 없이 파기합니다.'),
    (3, '1.0', '## 마케팅 정보 수신 동의

1. 수신 정보의 종류
- 이벤트 및 프로모션 안내
- 신규 기능 및 서비스 안내
- 맞춤형 혜택 정보

2. 수신 방법
- 앱 푸시 알림
- 이메일

3. 동의 철회
- 언제든지 설정에서 수신 동의를 철회할 수 있습니다.');

-- ============================================================
-- END OF INITIALIZATION
-- ============================================================
