-- ============================================================
-- 데이터 정리 스크립트: 잘못된 칭호 및 레벨 수정
-- 대상 유저: e68321f2-d2a2-4eef-bc47-f48cb69f46ca
-- 날짜: 2026-01-30
-- 실행 DB: gamification_db
-- ============================================================

-- 1. 현재 상태 확인 (실행 전 백업용)
SELECT '=== 수정 전 user_experience ===' as info;
SELECT * FROM user_experience WHERE user_id = 'e68321f2-d2a2-4eef-bc47-f48cb69f46ca';

SELECT '=== 수정 전 user_title (업적 미달성 칭호) ===' as info;
SELECT ut.*, t.name, t.rarity
FROM user_title ut
JOIN title t ON ut.title_id = t.id
WHERE ut.user_id = 'e68321f2-d2a2-4eef-bc47-f48cb69f46ca'
AND ut.title_id IN (1, 26);

-- ============================================================
-- 2. 잘못된 칭호 삭제 (업적 미달성인데 보유한 칭호)
-- - title_id=1 (독창적인): CREATION_CATEGORY_EXP 1680 필요, 실제 1
-- - title_id=26 (완전체): ETC_CATEGORY_EXP 403200 필요, 실제 0
-- ============================================================

-- 먼저 장착 해제 (equipped_position이 설정되어 있을 수 있음)
UPDATE user_title
SET is_equipped = false, equipped_position = null
WHERE user_id = 'e68321f2-d2a2-4eef-bc47-f48cb69f46ca'
AND title_id IN (1, 26);

-- 칭호 삭제
DELETE FROM user_title
WHERE user_id = 'e68321f2-d2a2-4eef-bc47-f48cb69f46ca'
AND title_id IN (1, 26);

-- ============================================================
-- 3. 레벨 재계산 (total_exp 50 기준)
-- level_config: level 2 requires 500 exp
-- 50 < 500 이므로 레벨 1이어야 함
-- ============================================================

UPDATE user_experience
SET current_level = 1,
    current_exp = 50  -- total_exp와 동일 (레벨 1이므로)
WHERE user_id = 'e68321f2-d2a2-4eef-bc47-f48cb69f46ca'
AND total_exp < 500;  -- 안전 조건

-- ============================================================
-- 4. 레벨 2 달성 업적도 미달성으로 변경
-- ============================================================

UPDATE user_achievement
SET is_completed = false,
    completed_at = null,
    is_reward_claimed = false,
    reward_claimed_at = null,
    current_count = 1  -- 현재 레벨
WHERE user_id = 'e68321f2-d2a2-4eef-bc47-f48cb69f46ca'
AND achievement_id = 79;  -- 레벨 2 달성 업적

-- 레벨 2 달성 칭호도 삭제 (갓 태어난, title_id=79)
DELETE FROM user_title
WHERE user_id = 'e68321f2-d2a2-4eef-bc47-f48cb69f46ca'
AND title_id = 79;

-- ============================================================
-- 5. 수정 후 상태 확인
-- ============================================================

SELECT '=== 수정 후 user_experience ===' as info;
SELECT * FROM user_experience WHERE user_id = 'e68321f2-d2a2-4eef-bc47-f48cb69f46ca';

SELECT '=== 수정 후 user_title ===' as info;
SELECT ut.*, t.name, t.rarity
FROM user_title ut
JOIN title t ON ut.title_id = t.id
WHERE ut.user_id = 'e68321f2-d2a2-4eef-bc47-f48cb69f46ca';

SELECT '=== 수정 후 user_achievement (레벨 관련) ===' as info;
SELECT ua.*, a.name
FROM user_achievement ua
JOIN achievement a ON ua.achievement_id = a.id
WHERE ua.user_id = 'e68321f2-d2a2-4eef-bc47-f48cb69f46ca'
AND a.check_logic_type_id = 9  -- LEVEL_REACH
ORDER BY a.required_count;
