-- LUT-835: 계단 영역 마스크 — 탑 방에서 아바타가 이 영역에 닿으면 화면이 층 선택 시트를 연다.
-- 앞 가림 마스크(occlusion_mask_url, LUT-725)와 같은 빨간 칠 PNG 이고, 없으면 그 층은 기능이 꺼진다.
ALTER TABLE tower_floor ADD COLUMN IF NOT EXISTS stairs_mask_url VARCHAR(500);
COMMENT ON COLUMN tower_floor.stairs_mask_url IS '계단 영역 마스크 PNG (선택, 층당 1개) — 아바타가 닿으면 층 선택 시트를 연다 (LUT-835)';
