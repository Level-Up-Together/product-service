-- LUT-634: 업적 판정 소스에 레벨업 탑(TOWER_SERVICE) 추가 — CheckLogicDataSource enum 7종과 맞춘다.
-- 초기 DDL 의 chk_data_source 는 6종만 허용해 탑 업적 시드(SQL 레포 1.1.5/lut634_tower_achievements.sql)가 23514 로 실패했다.
-- 멱등: 있으면 지우고 다시 만든다(수동으로 먼저 넓혀 둔 DB 에서도 그대로 통과).
ALTER TABLE check_logic_type DROP CONSTRAINT IF EXISTS chk_data_source;
ALTER TABLE check_logic_type
    ADD CONSTRAINT chk_data_source CHECK (data_source IN (
        'USER_STATS',
        'USER_EXPERIENCE',
        'USER_CATEGORY_EXPERIENCE',
        'FRIEND_SERVICE',
        'GUILD_SERVICE',
        'FEED_SERVICE',
        'TOWER_SERVICE'));
