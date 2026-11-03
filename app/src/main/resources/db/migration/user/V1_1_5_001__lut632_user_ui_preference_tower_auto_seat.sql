-- LUT-632: 유저 설정 "탑 자동 착석"(기본 켬) + 첫 안내를 봤는지.
ALTER TABLE user_ui_preference
    ADD COLUMN IF NOT EXISTS tower_auto_seat             BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS tower_auto_seat_notice_seen BOOLEAN NOT NULL DEFAULT FALSE;
COMMENT ON COLUMN user_ui_preference.tower_auto_seat IS '탑 자동 착석 — 탑 밖에서 미션을 시작하면 탑에 앉힐지 (LUT-632, 기본 켬)';
COMMENT ON COLUMN user_ui_preference.tower_auto_seat_notice_seen IS '탑 자동 착석 첫 안내를 봤는지 (LUT-632/713)';
