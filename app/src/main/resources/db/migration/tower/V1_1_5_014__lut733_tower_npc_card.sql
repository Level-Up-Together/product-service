-- LUT-733: NPC 를 눌렀을 때 뜨는 창의 이미지·이동 링크 (모두 선택). 기간(KST, 양끝 포함) 밖이거나 링크 대상(판매 중 아이템·이벤트)이 없으면
-- 입장 스냅샷이 창 묶음(card)을 내리지 않는다.
ALTER TABLE tower_npc ADD COLUMN IF NOT EXISTS card_image_url VARCHAR(500);
ALTER TABLE tower_npc ADD COLUMN IF NOT EXISTS card_link_type VARCHAR(20);
ALTER TABLE tower_npc ADD COLUMN IF NOT EXISTS card_link_target_id BIGINT;
ALTER TABLE tower_npc ADD COLUMN IF NOT EXISTS card_link_path VARCHAR(300);
ALTER TABLE tower_npc ADD COLUMN IF NOT EXISTS card_link_label VARCHAR(30);
ALTER TABLE tower_npc ADD COLUMN IF NOT EXISTS card_link_label_en VARCHAR(30);
ALTER TABLE tower_npc ADD COLUMN IF NOT EXISTS card_link_label_ja VARCHAR(30);
ALTER TABLE tower_npc ADD COLUMN IF NOT EXISTS card_link_label_ar VARCHAR(30);
ALTER TABLE tower_npc ADD COLUMN IF NOT EXISTS card_starts_on DATE;
ALTER TABLE tower_npc ADD COLUMN IF NOT EXISTS card_ends_on DATE;
COMMENT ON COLUMN tower_npc.card_image_url IS 'NPC 창 이미지 (가로 2 : 세로 1 권장)';
COMMENT ON COLUMN tower_npc.card_link_type IS 'NPC 창 이동 링크 종류 SHOP_ITEM/EVENT/SUBSCRIPTION/PATH';
COMMENT ON COLUMN tower_npc.card_link_target_id IS '링크 대상 상점 아이템·이벤트 ID';
COMMENT ON COLUMN tower_npc.card_link_path IS '앱 안 경로 (/ 로 시작)';
COMMENT ON COLUMN tower_npc.card_link_label IS '버튼 문구 (비우면 기본 문구)';
COMMENT ON COLUMN tower_npc.card_starts_on IS 'NPC 창 이미지·링크 노출 시작일 (KST, 비우면 상시)';
COMMENT ON COLUMN tower_npc.card_ends_on IS 'NPC 창 이미지·링크 노출 종료일 (KST, 포함, 비우면 상시)';
