# 다이아 IAP · 구독 결제 레퍼런스 (LUT-354/401/450~453/498~507/600/617)

앱 안 결제 두 종류를 다룬다. 핑크다이아 묶음(소모성, `gamificationservice/diamond`)과 멤버십 구독(자동갱신, `gamificationservice/subscription`)이다.
서버 검증, 원장, 스토어 서버 알림, 운영 점검을 정리한다. 상점 아이템 구매·할증·잠금은 CLAUDE.md "상점 · 다이아 경제" 절을 본다.
코드 기준일은 2026-10-29(`epic/v1.1.3`)이다. 시크릿 값은 적지 않는다.

## 0. 한눈에 보기

| | 핑크다이아 묶음 | 멤버십 구독 |
|---|---|---|
| 클라이언트 API | `POST /api/v1/diamond-bundles/{bundleId}/purchase` | `POST /api/v1/subscriptions/verify` (구매·복원 공용), `GET /api/v1/subscriptions/me`, `GET /api/v1/subscriptions/app-account-token` |
| 검증 서비스 | `IapVerificationService` | `SubscriptionVerificationService` |
| iOS 검증 | `receipt` 있으면 legacy verifyReceipt, 없으면 App Store Server API `getTransactionInfo` (LUT-498) | App Store Server API `getTransactionInfo`만 사용 (verifyReceipt 미사용) |
| Android 검증 | Play Developer API `purchases.products.get` | `purchases.subscriptionsv2.get` (+ LUT-617 `orders.get`) |
| 멱등 키 | `diamond_bundle_purchase.store_transaction_id` 유니크 (iOS transactionId / Android purchaseToken) | `user_subscription` 1행 upsert + 원장 uk `(user_id, event_type, expires_at)` |
| 원장 | `diamond_bundle_purchase` (`PAID`/`REFUNDED`) | `subscription_payment_history` (append-only, `PURCHASE`/`RENEWAL`/`REFUND`) |
| 금액 출처 | 스토어 값만, 없으면 null | `price_source` = `STORE`/`TRIAL`/`LIST`/null (LUT-600) |
| 스토어 알림 | 처리 안 함 (환불 자동 감지 미구현) | ASSN V2 + Google RTDN (LUT-452/617) |

## 1. 공통 설정 · 자격증명

| 키 | 의미 |
|---|---|
| `iap.verification.enabled` | 기본 `false`. false면 스토어를 호출하지 않고 요청 값을 그대로 믿는다(dev용). config-repository 에는 dev·prod 모두 설정돼 있다(prod는 LUT-499 커밋, 2026-09-16) |
| `iap.apple.key-id` / `issuer-id` | App Store Connect API 키 |
| `iap.apple.private-key` | .p8 PEM **값 자체**(파일 경로가 아님) |
| `iap.apple.bundle-id` | 앱 번들 ID |
| `iap.apple.app-id` | ASC 앱 숫자 ID. PRODUCTION `SignedDataVerifier` 에 필수(샌드박스는 null) |
| `iap.apple.root-cert-path` | 기본값 `classpath:apple/AppleRootCA-G3.cer` |
| `iap.apple.verify-url` / `sandbox-verify-url` | legacy verifyReceipt URL. 기본값은 Apple 공식 URL이다(다이아 경로만 사용) |
| `iap.google.package-name` | Play 패키지명 |
| `iap.google.service-account-json` | 서비스 계정 JSON **파일 경로** |

- 값은 config-repository 에 `{cipher}` 형태로 둔다. 코드베이스에 커밋하지 않는다.
- App Store Server API 클라이언트와 `SignedDataVerifier` 는 PRODUCTION/SANDBOX 별로 처음 쓸 때 만든다. 자격증명이 없는 환경에서도 서버가 뜨게 하기 위해서다. 조회는 항상 production 을 먼저 시도하고, 실패하면 sandbox 로 재시도한다(심사·TestFlight 흐름).
- Google 액세스 토큰은 서비스 계정 JWT(RS256)로 발급한다. `aud` 는 배열이 아니라 단일 문자열이어야 한다(배열이면 `invalid_grant`, LUT-493). 구독 서비스는 토큰을 50분 캐시한다(LUT-617). 다이아 서비스는 호출마다 새로 발급한다.
- 상품 → 내부 플랜 매핑은 `SubscriptionPlanMapping` 한 곳뿐이고, 키는 3개다 `(platform, product_id, base_plan_id)`. Android 는 두 플랜의 상품 ID가 같으므로 base plan 을 꼭 봐야 한다.

| platform | product_id | base_plan_id | 플랜 |
|---|---|---|---|
| ios | `membership_1m` / `dev_membership_1m` | — | `MONTHLY` |
| ios | `membership_1y` / `dev_membership_1y` | — | `ANNUAL` |
| android | `membership` | `1m` / `1y` | `MONTHLY` / `ANNUAL` |

매핑에 없는 조합이면 `120801` 이다. `dev_*` 는 iOS dev 앱 전용 상품이다. Apple 은 팀 전체에서 상품 ID가 유일해야 하기 때문이다.

## 2. 핑크다이아 묶음 결제 (`DiamondBundlePurchaseService`)

요청 바디는 `platform`(ios|android), `store_product_id`, `transaction_id`(iOS 필수), `receipt`(iOS 선택), `purchase_token`(Android 필수)이다.

1. 묶음이 존재하고 활성인지 확인한다. 아니면 `120704`. 묶음의 `store_product_id` 와 요청이 같은지 확인한다. 다르면 `120703`.
2. `IapVerificationService.verify` 를 호출한다. 외부 HTTP라서 트랜잭션 밖에서 돈다.
   - **iOS + receipt 있음**: verifyReceipt(prod)를 호출하고, `status=21007` 이면 sandbox URL로 다시 보낸다. `status≠0` 이면 `120702`. `receipt.in_app` 에 상품·트랜잭션이 함께 일치하는 항목이 없으면 `120703`. 가격은 App Store Server API 로 **best-effort** 보강한다(실패해도 가격 없이 진행).
   - **iOS + receipt 없음** (LUT-498, StoreKit 2): `getTransactionInfo` 로 JWS를 검증한다. 상품이 다르면 `120703`. `revocationDate` 가 있으면(환불됨) `120702`. 가격·통화는 같은 응답에서 얻는다.
   - **Android**: `purchases/products/{productId}/tokens/{token}` 의 `purchaseState≠0`(취소·보류)이면 `120702`. `priceAmountMicros`, `priceCurrencyCode` 를 파싱한다.
   - 단위 변환: Apple 가격은 밀리유닛(÷1,000), Google 은 마이크로(÷1,000,000)다.
3. 같은 `store_transaction_id` 가 이미 있으면 지급하지 않고 `already_processed=true` 와 현재 잔액을 돌려준다.
4. `DiamondBundlePurchaseTxService.recordAndGrant`(`gamificationTransactionManager`)가 구매 행을 `saveAndFlush` 한다. 유니크 위반은 여기서 먼저 걸린다. 그 뒤 `DiamondService.grantPinkDiamonds`(`DiamondType.PINK_PURCHASE`)를 호출한다.
5. `UserDiamond`(`@Version`) 낙관락 충돌이 나면 최대 3회까지 재시도한다. 대기는 50ms×시도 횟수다(LUT-504). 트랜잭션 전체가 롤백되므로 구매 기록은 중복되지 않는다. 3회를 다 쓰면 예외를 그대로 던지고, 클라이언트는 pending 을 유지한다.
6. 동시 요청으로 `DataIntegrityViolationException` 이 나면 기존 기록을 읽어 `already_processed` 로 응답한다.

응답은 `bundle_id`, `diamond_count`, `balance`(블루+핑크), `blue_balance`, `pink_balance`, `already_processed` 다.

- `status` 는 항상 `PAID` 로 저장한다. `REFUNDED` 전환 로직은 없다. RTDN `voidedPurchaseNotification` 은 구독 행만 찾으므로, 다이아 토큰이면 "RTDN 환불 매칭 구독 행 없음" 로그만 남는다.
- ⚠ 이미 처리된 트랜잭션이면, 원래 지급받은 유저와 요청 유저를 대조하지 않고 요청 유저의 잔액으로 `already_processed=true` 를 응답한다. 이때 지급은 없다.
- RN(`iapService.ts`): 서버 지급이 성공한 뒤에만 `finishTransaction` 을 부른다. 스토어 결제 대기 상한 `STORE_PURCHASE_TIMEOUT_MS` 는 **60초**다(LUT-502, 이전 4분). 늦게 끝난 트랜잭션은 전역 리스너나 앱 재시작 때 `restorePendingPurchases` 가 멱등하게 다시 처리한다.
- 어드민 결제이력은 `GET /api/internal/diamond-payments` 다. 필터는 `start_at`, `end_at`, `nickname`, `user_id`, `platform`, `bundle_id`, `status`, `page`, `size` 다.

## 3. 구독 상태 모델 (LUT-450)

**`user_subscription`** — 유저당 1행이다. 갱신, 플랜 변경, 해지는 모두 이 행을 갱신한다.

- 유니크 3개: `user_id`, `original_transaction_id`(iOS 매칭 키), `purchase_token`(Android 매칭 키).
- 상태 컬럼은 없다. `resolveStatus(now)` 로 그때그때 계산한다. `expires_at > now` 면 `ACTIVE`, 유예 종료가 아직이면 `GRACE_PERIOD`, 그 외는 `EXPIRED`, 행이 없으면 `NONE` 이다. 권한(`is_entitled`)은 `ACTIVE` 와 `GRACE_PERIOD` 에 있다.
- **상태의 진실은 항상 스토어 재조회 결과다.** 웹훅 payload 와 클라이언트 값은 재조회를 일으키는 트리거일 뿐이다.
- `/me` 응답 필드는 `status`, `is_entitled`, `plan`, `expires_at`, `grace_period_expires_at`, `auto_renew`, `trial_used`, `trial` 이다.
  - `trial_used` 는 유저의 **평생 이력**이다.
  - `trial` 은 **이번 verify 결제 건**이 체험으로 시작했는지를 뜻한다. `/me` 에서는 null 이다(LUT-500). 성공 문구는 반드시 `trial` 로 판정한다.

**`subscription_payment_history`** — CS·매출용 append-only 원장이다(LUT-486).

- verify 나 웹훅이 **만료를 엄격히 늘릴 때만** 1행을 쓴다. 그래서 verify·웹훅 이중 도착이나 재전송이 와도 결과가 같다. 마지막 방어선은 uk `uk_subscription_payment_dedup (user_id, event_type, expires_at)` 다.
- `transaction_id` 는 iOS transactionId 또는 Android 주문 ID(`GPA.xxxx` / `GPA.xxxx..N`)다. 컬럼 comment 의 "Android는 null" 은 LUT-499 이전 설명이다.
- 행을 고치는 예외는 `correctFromStoreOrder` 하나뿐이다(LUT-617, 값 출처는 항상 스토어 주문).

**구독 소비자**

- 스티펜드(LUT-453): `SubscriptionStipendScheduler` 가 `0 10 0 * * *` UTC 에 권한 보유 구독마다 블루 1개를 지급한다. 멱등 키는 uk `(subscription_id, stipend_date)` 다.
- 월간 통계 게이팅: `050301`.
- 피드 구독자 뱃지: `getEntitledUserIds`.

## 4. 구매·복원 검증 `POST /api/v1/subscriptions/verify` (LUT-451)

요청 바디는 `platform`, `product_id`, `transaction_id`(iOS 필수), `purchase_token`(Android 필수), `base_plan_id`(검증 비활성 모드에서만 사용)다.

**`SubscriptionVerificationService.verify`**

- **iOS**: `getTransactionInfo` 를 prod→sandbox 순서로 호출한다.
  - 상품이 다르거나 `expiresDate` 가 없으면(구독이 아님) `120703`.
  - `trial` 은 `offerType=1`(introductory)일 때다.
  - `autoRenew` 는 payload 에 없어서 true 로 둔다. 실제 값은 웹훅이 정정한다.
  - `appAccountToken` 을 꺼내고, 가격 `price`/`currency` 는 `STORE` 로 기록한다.
- **Android**: `subscriptionsv2.get` 을 호출한다.
  - `PENDING` 이면 `120702`, 상품이 다르면 `120703`.
  - `lineItems` 중 `expiryTime` 이 가장 늦은 항목이 현재 플랜이다.
  - `basePlanId`, `autoRenewEnabled`, `startTime`, `linkedPurchaseToken`, `latestOrderId`, `externalAccountIdentifiers.obfuscatedExternalAccountId` 를 읽는다.
  - `trial` = `offerId` 가 있고 **`latestOrderId` 에 `..` 이 없을 때**다(LUT-514). 오퍼 ID는 갱신 건에도 그대로 남기 때문이다.
  - subscriptionsv2 는 금액을 주지 않는다. 그래서 금액은 `LIST` 폴백이 된다(§9).
  - Play 4xx면 `120702`. 5xx(`StoreTransientException`)도 클라이언트 경로에서는 `120702` 로 감싸 응답 계약을 지킨다.
- **검증 비활성**: 요청 값을 그대로 믿는다. 만료는 now 에 플랜 기간(1개월/1년)을 더한 값이다.

**`SubscriptionGrantTxService.upsert`**

- 먼저 `resolveOwnership`(§5)을 돈다.
- 행이 없으면 insert 하고 `PURCHASE` 를 기록한다.
- 행이 있으면 `trial_used` 를 승격만 한다. 만료가 기존보다 늦지 않으면 아무것도 바꾸지 않는다. 오래된 Restore 가 상태를 되감지 못하게 하기 위해서다.
- 만료가 늦으면 플랜·상품·토큰·`auto_renew` 를 갱신하고 `RENEWAL` 을 기록한다.
- 같은 유저의 동시 insert 로 `DataIntegrityViolationException` 이 나면 1회 재시도한다(그때는 갱신 경로를 탄다).
- verify 는 **최신 주문 1건만** 원장에 쓴다. 주문 연번 백필(§8)은 RTDN, 자가 치유, reconcile 만 한다.

## 5. 소유권 정책 (LUT-507)

스토어 구독은 앱 계정이 아니라 Apple ID·Google 계정 단위다. 한 스토어 구독은 **한 번에 한 앱 계정**만 쓰고, 주인은 **마지막으로 결제한 계정**이다.

- **앱 계정 토큰**: `GET /api/v1/subscriptions/app-account-token` 는 `SubscriptionAccountToken.forUser` 값, 즉 userId UUID 소문자를 준다. 저장하지 않고 항상 같은 값이 나온다. userId 가 UUID 형식이 아니면 이름 기반 UUID 를 쓰고, 이 경우 역변환은 안 된다. RN 은 결제 직전에 이 값을 받아 iOS `appAccountToken`, Android `obfuscatedAccountIdAndroid` 에 싣는다.
- **`resolveOwnership` 순서**
  1. 거래에 토큰이 있고 요청 유저의 것이 아니면 `120802`.
  2. 스토어 키를 다른 유저가 보유 중인지 본다. iOS 는 `original_transaction_id`, Android 는 `purchase_token` 이고, 없으면 `linkedPurchaseToken` 소유자를 본다.
     - 그 유저가 아직 권한이 있거나, 이번 결제가 더 늦은 만료가 아니면 `120802`.
     - 그 외(만료 후 재구독)는 옛 행에서 `original_transaction_id`·`purchase_token` 을 null 로, `auto_renew` 를 false 로 바꾸고 `saveAndFlush` 한다(유니크 제약 때문). 그 다음 요청 유저 행에 upsert 해 **이전**한다.
  3. 토큰이 없는 예전 거래는 ①을 건너뛰고 ②만 본다.
- **웹훅**: Apple 의 `SUBSCRIBED`·`DID_RENEW`·`OFFER_REDEEMED` 와 Android RTDN 에서 거래 토큰이 현재 주인이 아니면, 같은 upsert 로 이전을 시도한다. `120802` 로 거절되면 이전하지 않고 기존 행에 그대로 적용한다.
- 웹 안내 문구는 "스토어 해지"가 아니라 "기존 이용 기간이 끝난 뒤 재구독"이다.

## 6. 연속성 키 · 플랜 변경 (LUT-499)

- **iOS**: `original_transaction_id` 는 갱신과 플랜 변경에도 유지되므로 그대로 매칭 키가 된다.
- **Android**: 재구독이나 플랜 변경은 새 `purchaseToken` 을 발급하고, 옛 토큰을 `linkedPurchaseToken` 으로 가리킨다. RTDN 에서 새 토큰이 어느 행에도 없으면 옛 토큰 행을 찾아 토큰을 교체한다(소유권 판정을 먼저 한다). verify 는 유저 행 기준이라 새 토큰으로 덮어쓴다.
- **플랜 변경** (RN `iapService.ts`): Android 활성 구매의 `currentPlanId` 가 새 base plan 과 다르면 `subscriptionProductReplacementParams` 에 옛 토큰과 `replacementMode` 를 싣는다. 업그레이드(1m→1y)는 `with-time-proration`, 다운그레이드는 `deferred` 다. 같은 플랜 재구매는 교체가 아니다.
- 서버 반영: Android 는 만료가 가장 늦은 line item 을 쓴다. iOS 는 `DID_CHANGE_RENEWAL_PREF` 를 받으면 `syncFromTransaction` 으로 상품·플랜을 갱신한다.

## 7. 스토어 서버 알림 (LUT-452/617)

| 엔드포인트 | 바디 | 인증 |
|---|---|---|
| `POST /api/v1/webhooks/subscriptions/apple` | `{"signedPayload": "<JWS>"}` | JWS 서명 검증이 인증을 대신한다 |
| `POST /api/v1/webhooks/subscriptions/google` | Pub/Sub push `{"message":{"data":"<base64>","messageId"}, "subscription"}` | 페이로드를 믿지 않고 Play API 를 재조회한다 |

- `SecurityConfig` 에서 POST 만 permitAll 이고, `SecurityConfigPublicEndpointTest` 에 짝으로 등록돼 있다. DAU 수집 인터셉터는 `/api/v1/webhooks/**` 를 뺀다.
- **응답 규약** (`SubscriptionWebhookService`)

| 상황 | 처리 | HTTP |
|---|---|---|
| 빈 payload, ASSN 서명 불량, RTDN 파싱 불가, 구독 알림 아님, Play 재조회 4xx·파싱 실패(`120702`), 매칭 행 없음, PENDING | 로그(+`dropped` 카운터)만 남기고 폐기 | 200 |
| `StoreTransientException`(Play 5xx·네트워크·토큰 발급 일시 실패), DB 오류 | 예외를 전파한다. 플랫폼 `RestExceptionHandler` 가 500 을 낸다 | 5xx → 스토어/Pub/Sub 재시도 |

- ⚠ 적용 중 `SubscriptionPlanMapping.resolve` 가 `120801`(매핑 없는 상품)을 던지면 잡지 않는다. 그러면 500 이 나고 Pub/Sub 가 보존 기간 동안 계속 재시도한다. 새 구독 상품을 추가할 때는 매핑부터 넣는다.

### 7.1 iOS — App Store Server Notifications V2

- `decodeAppleNotification` 은 production verifier 가 실패하면 sandbox 로 다시 검증한다. `TEST` 타입은 "ASSN TEST 알림 수신 확인" 로그만 남긴다.
- `originalTransactionId` 로 행을 찾는다. 없으면 스킵한다(그 뒤 verify·Restore 가 등록한다).

| 타입 | 처리 (`SubscriptionWebhookTxService.applyAppleNotification`) |
|---|---|
| `SUBSCRIBED`, `DID_RENEW`, `OFFER_REDEEMED`, `DID_CHANGE_RENEWAL_PREF` | `syncFromTransaction`: 상품·플랜·만료·`auto_renew`·trial 을 맞춘다. 만료가 늘면 `RENEWAL` 을 기록한다(`occurred_at=purchaseDate`, 가격 `STORE`). 앞의 세 타입은 소유권 이전을 먼저 검토한다 |
| `DID_CHANGE_RENEWAL_STATUS` | `auto_renew` 만 반영한다. 해지는 `AUTO_RENEW_DISABLED` 이고, 만료까지 권한은 남는다 |
| `DID_FAIL_TO_RENEW` | `gracePeriodExpiresDate` 가 있으면 유예에 들어간다(권한 유지) |
| `GRACE_PERIOD_EXPIRED` / `EXPIRED` | 유예를 해제하고 `auto_renew=false` 로 둔다. `EXPIRED` 는 만료도 트랜잭션 값으로 맞춘다 |
| `REFUND`, `REVOKE` | 만료를 `revocationDate` 로 당기고 `REFUND` 를 기록한다 |
| `PRICE_INCREASE` / 그 외 | 로그만 남긴다 |

- 등록 위치는 ASC 앱 정보 > App Store Server Notifications 의 Production/Sandbox URL 이다. dev 는 2026-09-16 에 등록했다(LUT-499). prod 등록 상태는 ❓ — ASC 에서 확인한다.
- ASSN 에는 Prometheus 카운터가 없다. 로그로만 본다.

### 7.2 Android — RTDN (GCP Pub/Sub push)

전달 경로는 다음과 같다.
Play Console 수익 창출 설정(RTDN 토픽, 콘텐츠 "정기 결제·무효화 구매") → Pub/Sub 토픽 `lut-rtdn-{dev|prod}` → push 구독 `lut-rtdn-{dev|prod}-push` → 웹훅.

- **분기**
  - `testNotification` 은 `kind=test` 로 센다.
  - `voidedPurchaseNotification` 과 `notificationType=12`(REVOKED)는 `revokeByPurchaseToken` 을 부른다. 만료를 now 로 당기고 `REFUND` 를 기록한다.
  - 그 외 타입(`8` PRICE_CHANGE_CONFIRMED 포함)은 subscriptionsv2 를 재조회한 뒤 `applyGoogleState(token, state, eventTimeMillis)` 를 부른다.
- **`applyGoogleState` 순서**
  1. 토큰으로 행을 찾고, 없으면 `linkedPurchaseToken` 으로 찾는다.
  2. PENDING 이면 스킵한다.
  3. 소유권 이전을 시도한다.
  4. 연속성 키로 찾은 행이면 토큰을 교체한다.
  5. 상품·플랜·**만료(단축도 그대로)**·`auto_renew` 를 반영한다. `IN_GRACE_PERIOD` 면 유예 종료를 `expiryTime` 으로 둔다.
  6. 만료가 늘었으면 `GoogleOrderLedgerSync.syncRenewals(full=false)` 를 부른다(§8).
- ⚠ **push 구독 생성 규칙** (LUT-617)
  - **반드시 `--expiration-period=never` 로 만든다.** 기본 정책은 "31일 비활성이면 삭제"이고, 삭제될 때 알림도 감사 로그도 없다. 2026-09-16 에 만든 dev 구독이 10월 중순에 사라졌고, 10/26 테스트 갱신 RTDN 9건이 구독 없는 토픽으로 발행돼 전부 폐기됐다.
  - LUT-617 재생성 값은 ack 30s, 메시지 보존 7d, 재시도 `--min-retry-delay=10s --max-retry-delay=600s` 다.
  - **push OIDC 인증(`--push-auth-service-account`)은 붙이지 않는다.** 플랫폼 `JwtAuthenticationFilter` 가 Bearer 헤더를 앱 JWT 로 해석해 401 이 날 수 있다(LUT-617 판단). 위조 방어는 Play API 재조회가 맡는다.
  - 토픽 IAM 에서 Google Play 시스템 서비스 계정에 Pub/Sub Publisher 권한이 있어야 한다. Pub/Sub 는 Terraform 관리 밖이라 gcloud 로 수동 관리한다.
  - 생성 예시(프로젝트 ID와 엔드포인트는 환경에 맞게 채운다):
    ```bash
    gcloud pubsub subscriptions create lut-rtdn-<env>-push --project <gcp-project> \
      --topic=lut-rtdn-<env> --push-endpoint=https://<host>/api/v1/webhooks/subscriptions/google \
      --expiration-period=never --ack-deadline=30 --message-retention-duration=7d \
      --min-retry-delay=10s --max-retry-delay=600s
    ```
- 현황(작업 기록 기준, 코드로는 확인할 수 없다): dev `lut-rtdn-dev-push` 는 2026-10-26 에 다시 만들었다. prod `lut-rtdn-prod-push` 는 2026-10-28 v1.1.3 릴리스 때 만들었고, Play Console 테스트 알림이 도착하는 것까지 확인했다.

### 7.3 `RtdnMetrics` 카운터 (`/showmethemoney/prometheus`)

| 메트릭 | 라벨 |
|---|---|
| `lut_rtdn_received_total` | `kind` = `test` / `voided` / `subscription-{type}` / `other` |
| `lut_rtdn_dropped_total` | `reason` = `empty` / `unparseable` / `not_subscription` / `refetch_rejected` |
| `lut_google_order_lookup_failed_total` | `reason` = `transient` / `rejected` / `not_found` |
| `lut_subscription_ledger_rows_written_total` | `source` = `rtdn` / `reconcile` |

- 카운터는 처음 증가할 때 등록된다. 시리즈가 아예 없다면 "재기동 이후 수신 0건"이라는 뜻이다.
- 라벨 값은 정해진 집합만 쓴다. Grafana Cloud Free 의 시리즈 한도 1만을 지키기 위해서다.

## 8. Android 원장 완결 — `GoogleOrderLedgerSync` (LUT-617)

예전에는 RTDN, 자가 치유, verify 가 모두 "처리 시점의 `latestOrderId` 1건"만 기록했다. RTDN 이 끊긴 구간의 중간 갱신은 영구히 빠졌다.
갱신 주문 ID 는 `GPA.xxxx..N` 연번이다. 그래서 원장의 마지막 N 부터 최신 N 까지를 Orders API 로 되짚어 채운다.

**`syncRenewals(subscription, latestOrderId, trial, latestExpiresAt, fallbackOccurredAt, full)`**

1. `latestOrderId` 가 없거나 접미사가 없으면(최초 주문) 예전처럼 1건만 기록한다.
2. 그 외에는 같은 base 로 시작하는 기존 행에서 최대 N 을 구한다.
3. 대조 범위는 `full ? 1 : maxN+1` 부터 `latestN` 까지다. 단, 하한은 `latestN-59` 다(`MAX_BACKFILL=60`).
4. 각 n 에 대해 `fetchGoogleOrder` 를 호출한다. 결과가 404면 empty, 그 외 4xx는 `120702`, 5xx는 transient 다.
   - 주문이 있으면 `upsertFromOrder` 를 부른다.
     - `expires_at` 은 `servicePeriodEndTime` 이다.
     - `occurred_at` 은 `createTime` 이고, 없으면 `servicePeriodStartTime`, 그것도 없으면 now 다.
     - 금액은 `total`(units+nanos)이고 `STORE` 로 기록한다.
     - 같은 주문 ID 행이 있으면 `correctFromStoreOrder` 로 고친다. 만료를 옮기면 uk 가 겹칠 때는 만료는 두고 시각·금액만 고친다.
     - 같은 만료에 다른 ID 를 단 행이 있으면 그 행의 ID·시각·금액을 바꾼다.
     - 둘 다 없으면 insert 한다.
   - 조회에 실패했고 그게 최신 주문이면, 그래도 1건을 쓴다. `occurred_at` 은 RTDN `eventTimeMillis`(없으면 now), 금액은 `LIST` 폴백이다. 중간 주문의 실패는 다음 호출 때 다시 시도된다.
5. 주문 조회는 best-effort 다. 실패해도 권한 동기화는 막지 않는다.

- 한계: 최초 주문(접미사 없음)은 대조 범위 밖이다. `PURCHASE` 행의 금액은 `LIST`/`TRIAL` 로 남는다.
- dev 실측(작업 기록): subscriptionsv2 `expiryTime` 과 orders `servicePeriodEnd` 가 1~2초 다를 수 있다. 원장의 `expires_at` 은 주문 값을 기준으로 한다. Orders API 는 라이선스 테스터 주문도 돌려준다.

### 8.1 수동 대조 `POST /api/internal/subscription-payments/reconcile?user_id=` (`X-Internal-Api-Key`)

- 한 유저의 Android 원장을 주문 1..latest 와 전부 대조한다(`full=true`). `user_subscription`(권한)은 바꾸지 않는다.
- 오류: 구독 행이 없으면 `120803`, Android 가 아니거나 토큰이 없으면 `120804`, Play 재조회가 일시 실패하면 `120702`.
- 응답: `user_id`, `latest_order_id`, `subscription_state`, `expires_at`, `rows_before`, `rows_after`, `rows_written`. 다시 호출하면 `rows_written=0` 이어야 한다(멱등).
- 목록 API `GET /api/internal/subscription-payments` 의 필터는 `start_at`/`end_at`(ISO date-time), `nickname`, `user_id`, `platform`, `plan`, `event_type`, `page`, `size` 다.

## 9. 금액 · `price_source` (LUT-401/600, `SubscriptionPriceResolver`)

원장에 쓸 때 금액을 다음 순서로 정한다. 캐시는 없다(건수가 적고, 무기한 캐시 사고 LUT-534 를 피하기 위해서다).

| 순서 | 조건 | amount / currency | `price_source` |
|---|---|---|---|
| 1 | 스토어 금액이 있다 (iOS JWS `price`, Android `orders.get total`) | 스토어 값 | `STORE` |
| 2 | 무료 체험 결제 | 0 / 스토어 통화 → 정가 통화 → KRW | `TRIAL` |
| 3 | `plan_list_price` 에 `(plan, platform 소문자, currency, effective_from ≤ 결제일)` 최신 행이 있다. 통화는 스토어 값, 없으면 KRW | 정가 | `LIST` |
| 4 | 정가도 없다 | null / 스토어 통화 | null (warn 로그) |

- 결제일은 `occurred_at` 의 날짜 부분이다(UTC 저장값).
- Android subscriptionsv2 는 통화도 주지 않는다. 그래서 verify·RTDN 경로는 KRW 정가가 된다.
- **`REFUND` 행**: 스토어가 환불액을 주지 않으므로 3번 규칙에 따라 정가(`LIST`)로 채운다. KPI 에서는 음수로 합산된다.
- 시드: 월 4,900 / 연 44,000 KRW × ios·android, `effective_from=2026-01-01` (`db/migration/gamification/V1_1_3_010`·`011`). 정가를 바꿀 때는 새 `effective_from` 행을 **추가**한다(삭제 금지). uk 는 `(plan, platform, currency, effective_from)` 다.
- 다이아 묶음에는 `price_source` 가 없다. 금액이 null 이면 미상이다.
- 운영 Android 17건(2026-09-20, Play 라이선스 테스터 결제)은 실매출이 아니라서 null 로 두고 백필하지 않기로 했다(LUT-600).

### 9-1. 스토어 테스트 결제 `test_purchase` (LUT-666)

금액 출처와 별개로 **실제 돈이 오간 결제인지**를 표시한다. KPI(`KPI_STATS.md` §3.5)는 true 인 행을 모두 뺀다.

| 대상 | true 조건 | 채우는 곳 |
|---|---|---|
| iOS 구독·다이아 | 트랜잭션 JWS `environment` 가 `Production` 이 아님(Sandbox = TestFlight·샌드박스 계정, Xcode, LocalTesting). 다이아 legacy 영수증은 21007 샌드박스 재시도면 true | `SubscriptionVerificationService.isAppleTestEnvironment`, `IapVerificationService` |
| Android 구독 | subscriptionsv2 응답에 `testPurchase` 객체가 있음(라이선스 테스터) | `fetchGoogleSubscription` → `GoogleSubscriptionState.testPurchase` |
| Android 다이아 | products 응답 `purchaseType = 0`(테스트). 1 프로모션·2 보상형은 실결제로 본다 | `IapVerificationService.verifyGoogle` |

- 구독은 결제 단위가 아니라 **구매(스토어 키)의 성질**이라 `user_subscription.test_purchase` 에 두고, verify(`SubscriptionGrantTxService`)·웹훅·자가 치유(`SubscriptionWebhookTxService`)·reconcile 이 기록 직전에 최신 값으로 맞춘다. 원장 행은 그 값을 복사한다(`SubscriptionPaymentHistoryRecorder`, `GoogleOrderLedgerSync`). 같은 유저가 테스트 구독 뒤 실제 구독을 하면 다음 결제부터 false 로 돌아온다.
- 환경을 모르면(Apple JWS 에 environment 없음, 검증 비활성 dev) 실결제(false)로 본다. Apple 알림에 환경이 없으면 기존 값을 유지한다.
- 이전 행 표시: SQL 레포 `queries/developing/hotfix-LUT-666/V001`. 규칙은 `expires_at - occurred_at < 2일`(실제 주기는 월·연, 테스트는 24시간·몇 분)인 PURCHASE/RENEWAL, 그 직후 REFUND, 최근 결제가 테스트인 구독 행. 다이아는 근거가 없어 건드리지 않는다(prod 3건은 금액 null 이라 원래 매출 밖).
- prod 실측(2026-10-29): 원장 38행 전부 테스터 3명(iOS TestFlight 2명 24시간 갱신, Android 라이선스 테스터 1명 3~30분 갱신)이었다.

## 10. 자가 치유 (LUT-499, `SubscriptionSelfHealService`)

- `/me` 를 처리하는 `SubscriptionService.getMyEntitlement` 는 `NOT_SUPPORTED` 로 트랜잭션 밖에서 돈다. readOnly 트랜잭션 안에서 쓰기를 부르면 flush 되지 않기 때문이다.
- 대상: `auto_renew=true` 인데 권한이 없는 행(만료·유예가 모두 지남)이다.
  - Android: `subscriptionsv2` 를 재조회하고 `applyGoogleState` 를 부른다. eventTime 이 없으므로, 주문 조회가 실패하면 결제 시각은 now 가 된다.
  - iOS: "Get All Subscription Statuses"(`fetchAppleLatestSubscription`, prod→sandbox)를 호출하고 `applyAppleSnapshot` 을 부른다. revocation 이 있으면 revoke 후 `REFUND`, 아니면 `syncFromTransaction` 이다.
- 반영한 뒤 행을 다시 읽어 응답한다. 실패하면 warn 로그만 남기고 DB 값으로 응답한다.
- 스토어가 해지를 알려주면 `auto_renew=false` 로 수렴하고, 다음 호출부터는 재조회하지 않는다.
- `iap.verification.enabled` 는 보지 않는다. 자격증명이 없는 환경이면 warn 로그만 남는다.

## 11. 에러 코드

CustomException 은 플랫폼 `RestExceptionHandler` 에서 HTTP 500 + `code` 로 나간다. 클라이언트는 `code` 로 분기한다.

| 코드 | 메시지 키 | 발생 |
|---|---|---|
| `120701` | `error.iap.receipt_required` | iOS `transaction_id` / Android `purchase_token` 누락 (다이아·구독) |
| `120702` | `error.iap.verification_failed` | 스토어 검증 실패, 환불된 트랜잭션, Android PENDING, Play 4xx·파싱 실패, 클라이언트·내부 API 경로의 스토어 일시 장애, 토큰 발급 실패 |
| `120703` | `error.iap.product_mismatch` | 묶음 `store_product_id` 불일치, 영수증·트랜잭션 상품 불일치, iOS 만료 없는 트랜잭션(구독 아님) |
| `120704` | `error.iap.bundle_not_available` | 묶음이 없거나 비활성 |
| `120801` | `error.subscription.unknown_product` | 3키 매핑에 없음 |
| `120802` | `error.subscription.transaction_already_used` | 소유권 정책(§5) — 토큰 불일치 또는 다른 계정 보유 |
| `120803` | `error.subscription.not_found` | reconcile 대상 구독 행 없음 |
| `120804` | `error.subscription.reconcile_unsupported` | reconcile 대상이 Android 가 아니거나 토큰 없음 |
| `050301` | `error.subscription.required` | (mission) 월간 통계 과거 월 조회에 구독 필요 |

## 12. 테스트 환경 갱신 주기

| 환경 | 주기 |
|---|---|
| Play 라이선스 테스터 | 체험 3분, 월 5분, 연 30분. 정상 갱신 6회 뒤 자동 취소 |
| iOS 샌드박스 | 1개월 = 5분, 1년 = 1시간 ❓(Apple 기본값 — 샌드박스 테스터별로 갱신 속도를 바꿀 수 있음) |
| TestFlight | 24시간 고정 |

## 13. 운영 점검

`$BASE` 는 환경별 product-service 주소다. `$KEY` 는 `app.security.internal-api.key` 값이다. 키는 셸 변수로만 다루고, 명령 기록·문서에 남기지 않는다.

1. **RTDN 이 도착하는지 확인**
   - Play Console > 수익 창출 설정 > "테스트 알림 보내기"를 누른다. 1~2분 뒤 `lut_rtdn_received_total{kind="test"}` 가 +1 이면 정상이다.
     ```bash
     curl -s -u "scrape:$KEY" "$BASE/showmethemoney/prometheus" | grep -E '^lut_(rtdn|google_order|subscription_ledger)'
     ```
   - 실제 갱신은 `kind="subscription-2"`(RENEWED)로 늘어난다. 시리즈가 없거나 0 에서 움직이지 않으면 전달 경로부터 의심한다.
   - push 구독 확인: `gcloud pubsub subscriptions describe lut-rtdn-<env>-push --project <gcp-project>`. `state: ACTIVE`, `expirationPolicy: {}`(빈 값 = 만료 없음), `pushConfig.pushEndpoint` 를 본다. `subscriptions list` 결과가 0개면 구독이 삭제된 것이다. 위 생성 예시로 다시 만든다.
   - Cloud Monitoring 에서 `pubsub.googleapis.com/topic/send_message_operation_count`(발행)와 `subscription/push_request_count`(ack 응답 코드)를 비교한다. 발행은 있는데 push 가 0 이면 구독 문제다.
   - 서버 로그 grep 키워드:
     - `RTDN 구독 동기화` / `RTDN 매칭 구독 행 없음`
     - `RTDN 상태 재조회 실패 — 폐기`
     - `구독 결제 이력 백필` / `Google 주문 조회 일시 실패`
2. **ASSN 확인**: 로그 `ASSN TEST 알림 수신 확인`·`ASSN 구독 동기화`·`ASSN 매칭 구독 행 없음`·`ASSN 서명 검증 실패 — 폐기` 를 본다(카운터 없음).
3. **Android 원장에 빠진 것이 의심될 때**
   ```bash
   curl -s -X POST -H "X-Internal-Api-Key: $KEY" "$BASE/api/internal/subscription-payments/reconcile?user_id=<user-uuid>"
   ```
   그 다음 `subscription_payment_history` 에서 해당 유저의 `transaction_id` 가 `..1`~`..N` 으로 끊김 없이 이어지는지, `price_source=STORE` 인지 확인한다. 다시 호출했을 때 `rows_written=0` 이어야 한다.
4. **금액 미상 점검**: `SELECT platform, plan, price_currency, count(*) FROM subscription_payment_history WHERE price_source IS NULL GROUP BY 1,2,3;` 로 본다. 정가 미등록 조합이면 `plan_list_price` 행을 추가하고, 기존 행은 수동 SQL 로 백필한다.
5. **다이아 미지급 CS**: `diamond_bundle_purchase.store_transaction_id` 로 기록이 있는지 본다. 없으면 클라이언트 pending 상태이므로 앱을 재시작하도록 안내한다(`restorePendingPurchases` 가 멱등하게 재처리한다).
6. **자가 치유**: 로그 `구독 자가 치유 수행` / `구독 자가 치유 실패 — DB 값으로 응답` 을 본다. 실패가 반복되면 스토어 자격증명이나 `iap.*` config 를 점검한다.
