-- LUT-600: 구독 플랜 정가 테이블 + 결제 이력 금액 출처.
-- Android 는 Google subscriptionsv2 가 결제 금액을 주지 않아 원장 price_amount 가 null 이었다 → 정가로 채우고 price_source=LIST 로 표시.
-- prod(v1.1.3, Flyway off)는 level-up-together-sql queries/developing/1.1.3/V010 수동 적용 → v1.1.4 Flyway 전환 시 baseline 1.1.3.10 이상.
CREATE TABLE IF NOT EXISTS plan_list_price (
    id             BIGSERIAL     PRIMARY KEY,
    plan           VARCHAR(20)   NOT NULL,   -- MONTHLY | ANNUAL
    platform       VARCHAR(10)   NOT NULL,   -- ios | android (user_subscription.platform 과 동일 표기)
    currency       VARCHAR(3)    NOT NULL,   -- ISO 4217
    amount         NUMERIC(14,2) NOT NULL,
    effective_from DATE          NOT NULL,   -- 이 정가가 적용되기 시작한 날 (가격 변경 시 새 행 추가)
    created_at     TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT uk_plan_list_price UNIQUE (plan, platform, currency, effective_from)
);
COMMENT ON TABLE plan_list_price IS '구독 플랜 정가 — 스토어가 금액을 안 줄 때(Android) 결제 이력 폴백 (LUT-600)';

ALTER TABLE subscription_payment_history ADD COLUMN IF NOT EXISTS price_source VARCHAR(10);
COMMENT ON COLUMN subscription_payment_history.price_source IS '금액 출처 STORE(스토어 제공)/LIST(정가 폴백)/TRIAL(체험 0원), null=미상 (LUT-600)';

-- 확인된 정가만 초기 적재 (iOS 월 4,900 KRW — 운영 원장 RENEWAL 11건 실측). Android·연간은 스토어 콘솔 확인 후 SQL 로 추가.
INSERT INTO plan_list_price (plan, platform, currency, amount, effective_from)
VALUES ('MONTHLY', 'ios', 'KRW', 4900.00, '2026-01-01')
ON CONFLICT ON CONSTRAINT uk_plan_list_price DO NOTHING;

-- 기존 행 출처 표기: 스토어 금액이 있는 행 = STORE (체험 0원은 trial 플래그로 구분되므로 그대로 STORE)
UPDATE subscription_payment_history SET price_source = 'STORE' WHERE price_amount IS NOT NULL AND price_source IS NULL;
