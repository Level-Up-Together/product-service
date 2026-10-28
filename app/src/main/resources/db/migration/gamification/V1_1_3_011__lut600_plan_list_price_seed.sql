-- LUT-600: 구독 정가 초기값 — 2026-10-23 Play Console(membership 기본 요금제 1m/1y)·App Store Connect(membership_1m/1y) 대한민국 가격 실측.
-- iOS MONTHLY 4,900 은 V1_1_3_010 에서 적재됨(운영 원장 11건 실결제와 일치). 가격이 바뀌면 새 effective_from 행을 추가한다(기존 행 수정 금지).
INSERT INTO plan_list_price (plan, platform, currency, amount, effective_from) VALUES
  ('ANNUAL',  'ios',     'KRW', 44000.00, '2026-01-01'),
  ('MONTHLY', 'android', 'KRW',  4900.00, '2026-01-01'),
  ('ANNUAL',  'android', 'KRW', 44000.00, '2026-01-01')
ON CONFLICT ON CONSTRAINT uk_plan_list_price DO NOTHING;
