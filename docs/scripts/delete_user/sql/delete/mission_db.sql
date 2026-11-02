-- mission_db: 미션 + 미션 참가자 + 미션 댓글 + 미션 템플릿 + 멱등 키(LUT-577) + 상태 이력
-- mission 의 자식(mission_participant → mission_execution·daily_mission_instance → *_image) 은 FK ON DELETE CASCADE.
-- mission_state_history 는 mission_id 에 FK 가 없어 CASCADE 되지 않는다(LUT-691 확인) → 직접 지운다.
-- 사용법: psql -v ON_ERROR_STOP=1 -v uid="'<USER-UUID>'" -f mission_db.sql

\echo === mission_db: deleting data for :'uid' ===

BEGIN;

DELETE FROM mission_comment         WHERE user_id    = :'uid';
DELETE FROM mission_idempotency_key WHERE user_id    = :'uid';   -- LUT-691 추가
DELETE FROM mission_participant     WHERE user_id    = :'uid';
-- 이 사용자가 만든 미션의 상태 이력(고아 방지) + 이 사용자가 일으킨 상태 이력 — LUT-691 추가
DELETE FROM mission_state_history   WHERE triggered_by = :'uid'
   OR mission_id IN (SELECT id FROM mission WHERE creator_id = :'uid');
-- ⚠ 길드 미션이면 다른 참가자의 participant/execution/instance 도 CASCADE 로 함께 지워진다
DELETE FROM mission                 WHERE creator_id = :'uid';
DELETE FROM mission_template        WHERE creator_id = :'uid';

COMMIT;

\echo === mission_db: done ===
