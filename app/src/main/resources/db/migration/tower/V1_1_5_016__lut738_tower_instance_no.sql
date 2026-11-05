-- LUT-738: 방 번호 — 층 안에서 1부터, 방을 열 때 비어 있는 가장 작은 번호(닫힌 방 번호는 다시 쓴다). 화면의 "n번 방".
-- 롤링 배포 중 옛 코드가 번호 없이 방을 열 수 있어 NULL 을 허용한다(새 코드가 다음에 방을 열 때 채운다, 목록은 빈 번호로 대신 보여 준다).
ALTER TABLE tower_instance ADD COLUMN IF NOT EXISTS instance_no INTEGER;
UPDATE tower_instance t
SET instance_no = s.rn
FROM (SELECT id, ROW_NUMBER() OVER (PARTITION BY floor_number ORDER BY id) AS rn FROM tower_instance) s
WHERE t.id = s.id
  AND t.instance_no IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_tower_instance_floor_no ON tower_instance (floor_number, instance_no);
COMMENT ON COLUMN tower_instance.instance_no IS '방 번호 (층 안에서 1부터, 화면의 n번 방)';
