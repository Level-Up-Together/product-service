-- LUT-732: NPC 자동 말풍선 — 켜면 터치 없이 n초마다 등록된 대사를 순서대로 머리 위 말풍선으로 띄운다(터치 대사 카드는 그대로).
ALTER TABLE tower_npc ADD COLUMN IF NOT EXISTS auto_bubble BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tower_npc ADD COLUMN IF NOT EXISTS bubble_interval_seconds INTEGER NOT NULL DEFAULT 10;
COMMENT ON COLUMN tower_npc.auto_bubble IS '자동 말풍선 사용 (기본 꺼짐 — 터치 대사 카드만)';
COMMENT ON COLUMN tower_npc.bubble_interval_seconds IS '자동 말풍선 대사 간격(초, 5~60, 기본 10)';
