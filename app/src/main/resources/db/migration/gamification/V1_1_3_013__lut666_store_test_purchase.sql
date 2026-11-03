-- LUT-666: 스토어 테스트 결제(iOS Sandbox·TestFlight / Google 라이선스 테스터) 표시 — KPI 구독·매출 집계에서 제외한다.
-- 운영 원장의 금액 있는 결제가 전부 테스터 결제였다(2026-10-29 QA). 새 컬럼은 FALSE 로 시작하고,
-- 이미 쌓인 테스트 결제 표시는 level-up-together-sql 의 수동 백필(LUT-666) 몫이다(데이터 보정은 Flyway 에 두지 않는다).
-- 상수 DEFAULT 의 ADD COLUMN 은 PostgreSQL 11+ 에서 메타데이터만 바꿔 잠금이 짧다. 롤링 배포 중 옛 코드의 INSERT 는 DEFAULT 로 채워진다.
ALTER TABLE user_subscription
    ADD COLUMN IF NOT EXISTS test_purchase BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE subscription_payment_history
    ADD COLUMN IF NOT EXISTS test_purchase BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE diamond_bundle_purchase
    ADD COLUMN IF NOT EXISTS test_purchase BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN user_subscription.test_purchase IS '스토어 테스트 결제 여부 — iOS Sandbox·Google 라이선스 테스터 (LUT-666)';
COMMENT ON COLUMN subscription_payment_history.test_purchase IS '스토어 테스트 결제 여부 — iOS Sandbox·Google 라이선스 테스터 (LUT-666)';
COMMENT ON COLUMN diamond_bundle_purchase.test_purchase IS '스토어 테스트 결제 여부 — iOS Sandbox·Google 테스트 구매 (LUT-666)';
