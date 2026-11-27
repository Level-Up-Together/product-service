-- LUT-864: NPC 움직이는 그림(스프라이트 시트) — 시트 한 장 + 프레임 정보. 없으면 모두 NULL 이고 앱은 원형 썸네일로 그린다(구 데이터·미설정).
-- 시트는 층 이미지 업로드 kind=NPC_MOTION(PNG·WebP 5MB)이 발급한 주소, 프레임은 재생 순서대로 [{x,y,w,h,duration_ms}] (1~120장).
ALTER TABLE tower_npc ADD COLUMN IF NOT EXISTS motion_sheet_url VARCHAR(500);
ALTER TABLE tower_npc ADD COLUMN IF NOT EXISTS motion_frames JSONB;
ALTER TABLE tower_npc ADD COLUMN IF NOT EXISTS motion_foot_x INTEGER;
ALTER TABLE tower_npc ADD COLUMN IF NOT EXISTS motion_foot_y INTEGER;
ALTER TABLE tower_npc ADD COLUMN IF NOT EXISTS motion_content_top INTEGER;
ALTER TABLE tower_npc ADD COLUMN IF NOT EXISTS motion_height INTEGER;
ALTER TABLE tower_npc ADD COLUMN IF NOT EXISTS motion_flip BOOLEAN;
ALTER TABLE tower_npc ADD COLUMN IF NOT EXISTS motion_play_mode VARCHAR(12);
ALTER TABLE tower_npc ADD COLUMN IF NOT EXISTS motion_interval_seconds INTEGER;
COMMENT ON COLUMN tower_npc.motion_sheet_url IS '움직이는 그림 시트 URL (NULL = 원형 썸네일)';
COMMENT ON COLUMN tower_npc.motion_frames IS '재생 순서대로 시트 안 칸 자리·한 장 시간 [{x,y,w,h,duration_ms}] (1~120장)';
COMMENT ON COLUMN tower_npc.motion_foot_x IS '칸 안 발 가운데 x (px, 모든 장 공통)';
COMMENT ON COLUMN tower_npc.motion_foot_y IS '칸 안 발(그림 맨 아래 줄) y (px)';
COMMENT ON COLUMN tower_npc.motion_content_top IS '칸 안 그림 맨 위 y (px, 모든 장 중 가장 높은 곳)';
COMMENT ON COLUMN tower_npc.motion_height IS '화면 그림 키 (아바타 좌표 px 24~200, 원형 썸네일 = 40)';
COMMENT ON COLUMN tower_npc.motion_flip IS '좌우 뒤집기';
COMMENT ON COLUMN tower_npc.motion_play_mode IS 'LOOP(계속 반복) / OCCASIONAL(첫 장으로 쉬다가 interval 마다 한 번)';
COMMENT ON COLUMN tower_npc.motion_interval_seconds IS 'OCCASIONAL 쉬는 시간(초, 1~60)';
