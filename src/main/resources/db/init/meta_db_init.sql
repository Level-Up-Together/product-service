-- =====================================================
-- Meta DB Initialization SQL
-- Database: meta_db
-- Services: MetaService
-- =====================================================

-- =====================================================
-- DROP EXISTING TABLES (for clean initialization)
-- =====================================================
DROP TABLE IF EXISTS calendar_holiday CASCADE;
DROP TABLE IF EXISTS level_config CASCADE;
DROP TABLE IF EXISTS common_code CASCADE;

-- =====================================================
-- 1. Common Code (공통코드)
-- =====================================================
CREATE TABLE common_code (
    id VARCHAR(4) PRIMARY KEY,
    code_name VARCHAR(50),
    code_title VARCHAR(50),
    description VARCHAR(64),
    parent_id VARCHAR(4)
);

COMMENT ON TABLE common_code IS '공통코드 관리 테이블';
COMMENT ON COLUMN common_code.id IS 'M (Member)  I (Invest). B (Borrow). T (Ticket)  N (Note)  A (Admin)';
COMMENT ON COLUMN common_code.code_name IS '코드명';
COMMENT ON COLUMN common_code.code_title IS '코드 타이틀';
COMMENT ON COLUMN common_code.description IS '설명';
COMMENT ON COLUMN common_code.parent_id IS '상위 그룹 아이디 : 비슷한 코드를 같은 그룹으로 묶기 위해';

CREATE INDEX idx_common_code_parent ON common_code(parent_id);

-- =====================================================
-- 2. Calendar Holiday (휴일)
-- =====================================================
CREATE TABLE calendar_holiday (
    id SERIAL PRIMARY KEY,
    years INTEGER NOT NULL,
    mmdd VARCHAR(4),
    is_holiday BOOLEAN,
    year_count INTEGER
);

COMMENT ON TABLE calendar_holiday IS '휴일';
COMMENT ON COLUMN calendar_holiday.id IS '휴일 ID';
COMMENT ON COLUMN calendar_holiday.years IS '연';
COMMENT ON COLUMN calendar_holiday.mmdd IS '월일';
COMMENT ON COLUMN calendar_holiday.is_holiday IS '휴일여부';
COMMENT ON COLUMN calendar_holiday.year_count IS '연간 휴일 수';

CREATE INDEX idx_calendar_holiday_years ON calendar_holiday(years);
CREATE INDEX idx_calendar_holiday_mmdd ON calendar_holiday(mmdd);
CREATE UNIQUE INDEX uk_calendar_holiday_years_mmdd ON calendar_holiday(years, mmdd);

-- =====================================================
-- 3. Level Config (레벨 설정)
-- =====================================================
CREATE TABLE level_config (
    id BIGSERIAL PRIMARY KEY,
    level INTEGER NOT NULL,
    required_exp INTEGER NOT NULL,
    cumulative_exp INTEGER,
    title VARCHAR(50),
    description VARCHAR(200),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_level_config_level UNIQUE (level),
    CONSTRAINT chk_level CHECK (level >= 1),
    CONSTRAINT chk_required_exp CHECK (required_exp >= 0)
);

COMMENT ON TABLE level_config IS '레벨 설정';
COMMENT ON COLUMN level_config.id IS 'ID';
COMMENT ON COLUMN level_config.level IS '레벨';
COMMENT ON COLUMN level_config.required_exp IS '다음 레벨까지 필요한 경험치';
COMMENT ON COLUMN level_config.cumulative_exp IS '이 레벨까지 누적 필요 경험치';
COMMENT ON COLUMN level_config.title IS '레벨 칭호';
COMMENT ON COLUMN level_config.description IS '레벨 설명';

-- =====================================================
-- INITIAL DATA
-- =====================================================

-- Common Codes (공통코드 예시 데이터)
INSERT INTO common_code (id, code_name, code_title, description, parent_id) VALUES
-- 상위 그룹
('M', 'MEMBER', '회원', '회원 관련 코드 그룹', NULL),
('G', 'GUILD', '길드', '길드 관련 코드 그룹', NULL),
('N', 'NOTI', '알림', '알림 관련 코드 그룹', NULL),
('S', 'SYS', '시스템', '시스템 관련 코드 그룹', NULL),

-- 회원 관련 코드
('M001', 'MEMBER_STATUS_ACTIVE', '활성', '활성 회원', 'M'),
('M002', 'MEMBER_STATUS_INACTIVE', '비활성', '비활성 회원', 'M'),
('M003', 'MEMBER_STATUS_SUSPENDED', '정지', '정지 회원', 'M'),
('M004', 'MEMBER_STATUS_WITHDRAWN', '탈퇴', '탈퇴 회원', 'M'),

-- 길드 관련 코드
('G001', 'GUILD_VISIBILITY_PUBLIC', '공개', '공개 길드', 'G'),
('G002', 'GUILD_VISIBILITY_PRIVATE', '비공개', '비공개 길드', 'G'),
('G003', 'GUILD_ROLE_MASTER', '길드장', '길드 마스터', 'G'),
('G004', 'GUILD_ROLE_ADMIN', '관리자', '길드 관리자', 'G'),
('G005', 'GUILD_ROLE_MEMBER', '멤버', '일반 멤버', 'G'),

-- 알림 관련 코드
('N001', 'NOTI_TYPE_FRIEND', '친구', '친구 관련 알림', 'N'),
('N002', 'NOTI_TYPE_GUILD', '길드', '길드 관련 알림', 'N'),
('N003', 'NOTI_TYPE_MISSION', '미션', '미션 관련 알림', 'N'),
('N004', 'NOTI_TYPE_SYSTEM', '시스템', '시스템 알림', 'N'),

-- 시스템 관련 코드
('S001', 'SYS_STATUS_ON', '활성', '시스템 활성', 'S'),
('S002', 'SYS_STATUS_OFF', '비활성', '시스템 비활성', 'S'),
('S003', 'SYS_MODE_NORMAL', '일반', '일반 모드', 'S'),
('S004', 'SYS_MODE_MAINTENANCE', '점검', '점검 모드', 'S');

-- Level Config (레벨 1~30 설정)
INSERT INTO level_config (level, required_exp, cumulative_exp, title, description) VALUES
(1, 0, 0, '초보자', '여정을 시작한 초보자입니다'),
(2, 100, 100, '견습생', '배움의 길을 걷는 견습생입니다'),
(3, 200, 300, '훈련생', '꾸준히 훈련하는 훈련생입니다'),
(4, 300, 600, '수습', '실력을 쌓아가는 수습생입니다'),
(5, 400, 1000, '도전자', '도전을 두려워하지 않는 도전자입니다'),
(6, 500, 1500, '탐험가', '미지의 영역을 탐험하는 탐험가입니다'),
(7, 600, 2100, '모험가', '모험을 즐기는 모험가입니다'),
(8, 700, 2800, '전사', '강인한 의지의 전사입니다'),
(9, 800, 3600, '숙련자', '기술을 숙련한 숙련자입니다'),
(10, 1000, 4600, '베테랑', '풍부한 경험의 베테랑입니다'),
(11, 1200, 5800, '정예', '선발된 정예 대원입니다'),
(12, 1400, 7200, '전문가', '분야의 전문가입니다'),
(13, 1600, 8800, '달인', '기술의 달인입니다'),
(14, 1800, 10600, '명인', '이름난 명인입니다'),
(15, 2000, 12600, '대가', '분야의 대가입니다'),
(16, 2500, 15100, '고수', '뛰어난 고수입니다'),
(17, 3000, 18100, '영웅', '모두의 영웅입니다'),
(18, 3500, 21600, '챔피언', '챔피언의 반열에 오른 자입니다'),
(19, 4000, 25600, '마스터', '마스터 칭호를 얻은 자입니다'),
(20, 5000, 30600, '그랜드마스터', '최고 경지에 이른 그랜드마스터입니다'),
(21, 6000, 36600, '전설', '전설로 기록될 자입니다'),
(22, 7000, 43600, '신화', '신화가 된 자입니다'),
(23, 8000, 51600, '불멸', '불멸의 존재입니다'),
(24, 9000, 60600, '초월자', '한계를 초월한 자입니다'),
(25, 10000, 70600, '신', '신의 경지에 이른 자입니다'),
(26, 12000, 82600, '창조자', '새로운 것을 창조하는 자입니다'),
(27, 14000, 96600, '무한', '무한한 가능성의 소유자입니다'),
(28, 16000, 112600, '영원', '영원히 기억될 자입니다'),
(29, 18000, 130600, '절대자', '절대적인 힘의 소유자입니다'),
(30, 20000, 150600, '궁극', '궁극의 경지에 도달한 자입니다');

-- Calendar Holiday (2025년 한국 공휴일 예시)
INSERT INTO calendar_holiday (years, mmdd, is_holiday, year_count) VALUES
(2025, '0101', TRUE, 1),   -- 신정
(2025, '0128', TRUE, 2),   -- 설날 전날
(2025, '0129', TRUE, 3),   -- 설날
(2025, '0130', TRUE, 4),   -- 설날 다음날
(2025, '0301', TRUE, 5),   -- 삼일절
(2025, '0505', TRUE, 6),   -- 어린이날
(2025, '0506', TRUE, 7),   -- 부처님 오신 날
(2025, '0606', TRUE, 8),   -- 현충일
(2025, '0815', TRUE, 9),   -- 광복절
(2025, '1003', TRUE, 10),  -- 개천절
(2025, '1005', TRUE, 11),  -- 추석 전날
(2025, '1006', TRUE, 12),  -- 추석
(2025, '1007', TRUE, 13),  -- 추석 다음날
(2025, '1009', TRUE, 14),  -- 한글날
(2025, '1225', TRUE, 15);  -- 크리스마스

-- =====================================================
-- END OF INITIALIZATION
-- =====================================================
