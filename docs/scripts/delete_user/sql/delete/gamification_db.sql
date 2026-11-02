-- gamification_db: 업적 + 칭호 + 경험치 + 출석 + MVP + 시즌 보상 + 상점/인벤토리 + 다이아 잔액·원장 + 스티펜드 + 아이템 푸시 로그
-- ⚠ 결제 기록은 지우지 않는다 (LUT-691 결정 2026-11-02, 회계·환불 대응 근거):
--   user_subscription · subscription_payment_history · diamond_bundle_purchase 는 user_id 를 그대로 둔 채 보존한다.
--   (users 행이 사라져도 이 표들은 FK 가 없어 남는다. 스토어 키 유니크 제약은 유지되어 같은 스토어 구독을 다른 계정이
--    이어받는 규칙(LUT-507)도 그대로 동작한다.)
--   user_diamond(잔액)·diamond_history(재화 원장)는 지운다 — 구매 사실은 diamond_bundle_purchase 에 남는다.
-- 사용법: psql -v ON_ERROR_STOP=1 -v uid="'<USER-UUID>'" -f gamification_db.sql

\echo === gamification_db: deleting data for :'uid' ===

BEGIN;

DELETE FROM user_achievement          WHERE user_id = :'uid';
DELETE FROM user_title                WHERE user_id = :'uid';
DELETE FROM user_stats                WHERE user_id = :'uid';
DELETE FROM user_experience           WHERE user_id = :'uid';
DELETE FROM user_category_experience  WHERE user_id = :'uid';
DELETE FROM experience_history        WHERE user_id = :'uid';
DELETE FROM attendance_record         WHERE user_id = :'uid';
DELETE FROM season_reward_history     WHERE user_id = :'uid';
DELETE FROM daily_mvp_history         WHERE user_id = :'uid';
DELETE FROM daily_mvp_category_stats  WHERE user_id = :'uid';
-- LUT-691 추가 (상점·다이아·구독 부가 데이터)
DELETE FROM item_push_send_log        WHERE user_id = :'uid';
DELETE FROM item_grant                WHERE user_id = :'uid';
DELETE FROM user_item                 WHERE user_id = :'uid';
DELETE FROM diamond_history           WHERE user_id = :'uid';
DELETE FROM user_diamond              WHERE user_id = :'uid';
DELETE FROM subscription_stipend      WHERE user_id = :'uid';
-- 보존: user_subscription / subscription_payment_history / diamond_bundle_purchase (위 주석 참고)

COMMIT;

\echo === gamification_db: done ===
