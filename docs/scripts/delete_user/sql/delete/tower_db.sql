-- tower_db (LUT-629): 레벨업 탑 유저 상태(마지막 입장 층). 층(tower_floor)은 어드민 데이터라 유저 행이 없다.
-- 사용법: psql -v ON_ERROR_STOP=1 -v uid="'<USER-UUID>'" -f tower_db.sql

\echo === tower_db: deleting data for :'uid' ===

BEGIN;

DELETE FROM tower_user_state WHERE user_id = :'uid';

COMMIT;

\echo === tower_db: done ===
