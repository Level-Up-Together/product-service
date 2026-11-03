-- tower_db (LUT-629/630): 레벨업 탑 유저 상태(마지막 입장 층)·인스턴스 점유자. 층(tower_floor)은 어드민 데이터라 유저 행이 없다.
-- 사용법: psql -v ON_ERROR_STOP=1 -v uid="'<USER-UUID>'" -f tower_db.sql

\echo === tower_db: deleting data for :'uid' ===

BEGIN;

DELETE FROM tower_occupant WHERE user_id = :'uid';
-- 점유자가 빠져 빈 방이 되면 닫는다(LUT-630: 아무도 없는 인스턴스는 바로 닫음)
DELETE FROM tower_instance i WHERE NOT EXISTS (SELECT 1 FROM tower_occupant o WHERE o.instance_id = i.id);
DELETE FROM tower_user_state WHERE user_id = :'uid';

COMMIT;

\echo === tower_db: done ===
