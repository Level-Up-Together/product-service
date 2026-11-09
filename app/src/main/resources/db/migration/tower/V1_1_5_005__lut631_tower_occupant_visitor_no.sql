-- LUT-631/705: 방문객 이름의 네 자리 숫자("방문객 4821") — 입장 때 서버가 임의로 준다.
ALTER TABLE tower_occupant ADD COLUMN IF NOT EXISTS visitor_no INTEGER;
COMMENT ON COLUMN tower_occupant.visitor_no IS '방문객 표시 번호 (네 자리, 로그인 유저는 NULL)';
