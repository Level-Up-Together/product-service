# 지표 집계 레퍼런스 — statsservice (LUT-598/599/601/605/615/616)

IR 용 KPI(DAU/WAU/MAU·리텐션·매출·LTV·CAC 분모)를 모으고 적재하고 노출하는 `statsservice` 를 정리한다.
범위는 활동 신호 수집(`activity/`), 일별 스냅샷·코호트(`kpi/`), Micrometer 지표, 내부 API 다. 어드민 화면·CAC 계산은 admin-service(LUT-602/603) 담당이다.
Grafana Cloud 연동(Alloy, 대시보드, 알람)은 infra 레포 `docs/grafana-cloud.md` 를 본다. 코드 기준일은 2026-10-29(`epic/v1.1.3`)이다.

## 0. 파이프라인

```
인증된 /api/** 요청 ─ UserActivityInterceptor ─@Async→ Redis SADD active:{KST날짜}[:device]   (TTL 40일)
                                                                │ 00:20 KST UserActivityFlushScheduler (그제·어제)
                                                                ▼
                                             stats_db.user_daily_activity
user_db · gamification_db · mission_db ─ *SourceReader(JDBC 읽기 전용) ─┐
                                                                ▼ 00:40 KST KpiSnapshotScheduler
                         daily_kpi_snapshot · cohort_retention · cohort_revenue  (+ fx_rate 수동)
                              │                                   │
              /api/internal/kpi/* → admin MvpKpiFeignClient    KpiMetricsExporter → lut_kpi_* → Alloy → Grafana
```

## 1. 저장소 — `stats_db`

- 9번째 DB 다. 트랜잭션 매니저는 `statsTransactionManager` 이고, 설정은 `StatsDataSourceConfig`(`statsDataSource` → `statsFlywayMigration` → `statsEntityManagerFactory`)에 있다.
- DB 자체는 수동으로 만든다(`CREATE DATABASE stats_db`). 테이블은 Flyway `app/src/main/resources/db/migration/stats/` 에 있다.
  - `V1_1_3_008`: `user_daily_activity`
  - `V1_1_3_012`: KPI 테이블
- prod 는 v1.1.3 때 SQL 레포로 수동 적용했다. 지금은 config-repository 기준 Flyway 가 켜져 있다(`baseline-version 1.1.3.12`).

| 테이블 | 키 | 내용 |
|---|---|---|
| `user_daily_activity` | uk `(user_id, activity_date)` | KST 날짜별 활동 1행. `device_types` 는 정렬된 쉼표 목록(최대 50자) |
| `daily_kpi_snapshot` | PK `snapshot_date` | 하루치 KPI(§3.5) + `revenue_by_currency` JSON |
| `cohort_retention` | PK `(cohort_date, metric, source)` | `cohort_size`, `retained` |
| `cohort_revenue` | PK `(cohort_month 'YYYY-MM', month_n)` | `cohort_size`, `paying_users`, `cumulative_revenue_krw` |
| `fx_rate` | PK `(rate_month 'YYYY-MM', currency)` | `krw_per_unit`. **수동 입력**(자동 수집 없음) |

## 2. 활동 수집 — DAU 신호 (LUT-598, `statsservice/activity`)

### 2.1 인터셉터

- `UserActivityInterceptor.preHandle` 은 항상 `true` 를 반환한다. 모든 예외는 debug 로그로 삼킨다. **어떤 경우에도 요청을 막지 않는다.**
- `@Component` 가 아니라 **`StatsActivityConfig` 의 `@Bean`** 으로 등록한다.
  - `@WebMvcTest` 슬라이스는 `HandlerInterceptor` 컴포넌트를 전부 스캔한다. `@Component` 면 Redis·Recorder 빈이 없는 컨트롤러 테스트 수백 개가 함께 깨진다.
  - `@Configuration` 은 슬라이스에 올라오지 않는다. `WebMvcConfig` 는 `ObjectProvider<UserActivityInterceptor>` 로 받아서 빈이 있을 때만 등록한다.
- 경로
  - 포함: `/api/**`, `/jwt/reissue`, `/graphql`
  - 제외: `/api/internal/**`, `/api/test/**`, `/api/v1/webhooks/**`
- 인정하는 인증
  - `SecurityContext` principal 이 비어 있지 않은 **String(userId)** 인 경우만 센다.
  - 미인증, `AnonymousAuthenticationToken`, 다른 principal 타입은 무시한다.
- 기기 종류는 `io.pinkspider.global.web.DeviceTypeResolver.resolve(request, X-Device-Type)` 로 정한다.
  - 클라이언트 헤더가 우선이고, 없으면 UA 로 추정하며, 최종 폴백은 `web` 이다.
  - 값은 `web`/`ios`/`ipad`/`android` 넷 중 하나다.

### 2.2 Redis (`UserActivityKeys`, `UserActivityRecorder`)

- `@Async` 로 `SADD active:{yyyy-MM-dd} userId` 와 `SADD active:{yyyy-MM-dd}:{deviceType} userId` 를 실행한다. 날짜는 **기록 시점 KST** 다.
- 새 멤버가 들어갔을 때만 `EXPIRE` 40일을 건다.
- Redis 실패는 warn 로그만 남긴다. 그 구간의 DAU 는 빠진다(허용).
- flush 는 키를 지우지 않는다(TTL 로 자연 소멸). 그래서 **40일이 지난 날짜는 다시 flush 할 수 없다.**

### 2.3 적재 (`UserActivityFlushService`)

- `UserActivityFlushScheduler.flushRecentDays` 는 `0 20 0 * * *` Asia/Seoul 에 돈다.
  - ShedLock 이름은 `UserActivityFlushScheduler_flushRecentDays`(PT20M/PT1M)다.
  - **그제와 어제** 를 flush 한다(`DAYS_BACK=2`). 그래서 하루 누락은 자동으로 메워진다. 날짜별로 따로 try/catch 한다.
- `flush(date)` 순서
  1. 일별 집합을 `SMEMBERS` 한다.
  2. 기기 집합 4개를 유저별로 합친다.
  3. 기존 행은 `device_types` 만 병합하고, 없는 유저는 insert 한다. 멱등이다.
- 수동 실행: `POST /api/internal/stats/activity/flush?date=YYYY-MM-DD` → `{date, flushed_rows, active_users}`.
  - `flushed_rows` 는 이번에 넣거나 바꾼 행 수다.
  - `active_users` 는 적재된 그날 행 수다.

### 2.4 설정 `app.stats.activity.*` (`UserActivityProperties`)

| 키 | 기본 | 의미 |
|---|---|---|
| `enabled` | `true` | false 면 인터셉터가 아무것도 기록하지 않는다(긴급 차단) |
| `excluded-user-ids` | 빈 집합 | DAU·REQUEST 리텐션에서 뺄 userId(E2E 봇·운영자). 2026-10-29 현재 config-repository 어디에도 설정돼 있지 않다 → 제외 0명 |

## 3. KPI 스냅샷 · 코호트 (LUT-601, `statsservice/kpi`)

### 3.1 실행 경로

- **스케줄러** `KpiSnapshotScheduler.buildDailySnapshot`
  - 주기는 `0 40 0 * * *` Asia/Seoul 이고, ShedLock 은 `KpiSnapshotScheduler_buildDailySnapshot`(PT30M/PT1M)다.
  - `KpiRebuildService.runDaily(today)` 를 호출한다.
    - **그제·어제** 스냅샷을 다시 만든다.
    - 코호트 리텐션은 cohort `[어제−31, 어제]` 를 대상으로, `lastComplete=어제` 로 계산한다.
    - 코호트 매출은 **전월·당월 코호트** 만 계산한다.
  - 실패하면 `KPI 일별 스냅샷 실패` error 로그를 남기고 삼킨다.
- **소급** `POST /api/internal/kpi/rebuild?from&to` (`KpiRebuildService.rebuild`)
  - from>to 면 바꿔서 처리한다.
  - `to` 는 어제까지로 자른다.
  - 400일(`MAX_DAYS`)을 넘으면 **start 를 end−399 로 당긴다.** 즉 최근 400일만 처리한다.
  - 날짜별 스냅샷은 각자 트랜잭션이다. 그 다음 같은 범위의 코호트 리텐션과 범위 월의 코호트 매출을 다시 계산한다.
  - 응답은 정규화된 `{from, to, snapshot_days, retention_rows, revenue_rows}` 다.
  - 어드민 패스스루는 `POST /api/admin/kpi/rebuild`(`KPI_MANAGE`)다.
- 서버 기동 시 자동 실행은 없다(LUT-595 원칙). 모든 upsert 는 멱등이라 재실행해도 안전하다.
- ⚠ **코호트 매출 `month_n ≥ 1` 은 스케줄러만으로는 채워지지 않는다.**
  - `runDaily` 는 cohort_month ∈ {전월, 당월} 만 계산한다.
  - 그런데 `month_n` 점은 "가입 월 + n 개월의 월말"이 지나야 생긴다. 그 시점이면 그 코호트는 이미 전전월 이전이다.
  - 그래서 스케줄러는 전월 코호트의 `month_n=0` 만 만든다.
  - LTV 곡선을 갱신하려면 매월 초 `rebuild` 를 넉넉한 범위(최대 400일)로 돌려야 한다. 의도된 설계인지는 ❓ 다.

### 3.2 원천 리더 — Facade 가 아니라 JDBC 인 이유

- `KpiSourceConfig` 가 `userDataSource`·`gamificationDataSource`·`missionDataSource` 빈에 `NamedParameterJdbcTemplate` 를 붙여 `UserSourceReader`·`GamificationSourceReader`·`MissionSourceReader` 를 만든다.
- 이유
  - KPI 는 범위 집계(`COUNT DISTINCT`, `GROUP BY`, 기간 합)를 하는 **읽기 모델**이다. 유저 단건 조회 위주인 Facade 로는 N회 호출하거나 DTO 를 키워야 한다.
  - 다른 서비스의 Repository/Service 를 import 하지 않는다는 경계 규칙은 지킨다.
  - 테이블 스키마에 결합되는 것은 대가로 받아들인다. MSA 로 전환할 때는 이 세 클래스만 이벤트/API 로 바꾸면 된다.
- 원천 테이블의 컬럼 이름을 바꾸면 리더 SQL 도 함께 고친다. H2 통합 테스트는 없다(`TestDataSourceConfig` 의 H2 는 같은 빈 이름 주입용). **SQL 을 수정하면 dev DB 에서 먼저 실행해 본다.**

### 3.3 날짜 경계 (`KpiDates`)

- 모든 지표에서 "하루"는 KST 다. 원천 timestamp 는 UTC 로 저장되므로 `utcStart(D)=D 00:00 KST`, `utcEnd(D)=D+1 00:00 KST` 를 UTC 로 바꿔 `[from, to)` 로 조회한다. 예: 2026-10-23(KST) = `[10-22T15:00, 10-23T15:00)` UTC.
- 예외
  - `user_daily_activity.activity_date` 는 기록 시점의 KST 날짜다.
  - `attendance_record.attendance_date` 는 **유저 타임존 날짜**(`preferred_timezone`, 폴백 Asia/Seoul)다. 이 값을 그대로 비교한다. 해외 유저는 KST 경계와 어긋난다.

### 3.4 제외 규칙 (코드 기준)

| 지표 | `users.provider='test'` 제외 | `excluded-user-ids` 제외 | 스토어 테스트 결제(`test_purchase`) 제외 |
|---|---|---|---|
| signups, withdrawals, active_users, 코호트 멤버(리텐션·매출 분모), 채널별 가입자 | O | X | — |
| dau/wau/mau/dau_*, REQUEST 리텐션의 활동 | **X** | O | — |
| attendance_dau, ATTENDANCE 리텐션, 미션 | X | X | — |
| 구독, 결제 유저, 매출(일별·코호트) | X | X | **O** (LUT-666) |

- 테스트 로그인(`/api/test/**`) 호출 자체는 수집에서 빠진다. 하지만 그 토큰으로 이후 보낸 `/api/**` 요청은 DAU 에 들어간다. 그래서 봇·테스트 계정은 `excluded-user-ids` 에 등록해야 DAU 에서 빠진다.
- `excluded-user-ids` 에 있는 유저도 코호트 멤버로는 남는다(REQUEST 리텐션 분모에는 포함, 활동은 0).

### 3.5 `daily_kpi_snapshot` 지표 정의 (KST 날짜 D)

| 컬럼 | 정의 (원천) |
|---|---|
| `signups` / `withdrawals` | `users.created_at` / `withdrawn_at` ∈ D |
| `active_users` | D 끝 시점 기준으로 가입했고 탈퇴하지 않은 유저 수. 상태 무관(정지 포함) |
| `dau` / `wau` / `mau` | `user_daily_activity` 고유 유저 수. 각각 D / D−6..D / D−29..D |
| `dau_web` / `dau_ios` / `dau_android` | 그날 `device_types` 에 해당 기기가 있는 유저 수. `dau_ios` 는 ios+ipad 다. 여러 기기를 쓰면 중복되므로 합이 dau 이상일 수 있다 |
| `attendance_dau` | `attendance_record` 에서 `attendance_date = D` 인 고유 유저 수. 소급 계산 가능 |
| `mission_completions` / `mission_active_users` | `mission_execution` + `daily_mission_instance` 중 `status='COMPLETED'` 이고 `completed_at` ∈ D 인 건수 / 고유 유저 수(`mission_participant.user_id`) |
| `new_subscriptions` / `trial_starts` | 원장 `PURCHASE` 건수(체험 포함) / 그중 `trial=true` |
| `trial_conversions` | D 의 비체험 `RENEWAL` 중, 그 유저에게 이전 체험 `PURCHASE` 가 있고 이전 비체험 `RENEWAL` 은 없는 건(체험 뒤 첫 유료 갱신) |
| `active_subscriptions` | D 끝 기준 `user_subscription.expires_at` 또는 `grace_period_expires_at` 이 아직 안 지난 행 수 |
| `churned_subscriptions` | `expires_at` ∈ D 이고 유예도 D 안에 끝난 행 + D 의 `REFUND` 건수 |
| `paying_users` | 금액 > 0 인 결제를 한 고유 유저 수. 구독(비환불, `price_source` 있음) + 다이아(`PAID`). 테스트 결제 제외(아래) |
| `revenue_krw` | 아래 매출 규칙의 KRW 합(소수 2자리) |
| `revenue_by_currency` | 통화별 원본 합 JSON(통화 정렬) |

- ⚠ `active_subscriptions`·`churned_subscriptions` 는 **현재** `user_subscription` 행으로 계산한다(`started_at` 조건 없음, 유저당 1행 덮어쓰기). 오래된 날짜를 rebuild 하면 그 뒤에 생긴 구독이 섞이거나 갱신된 만료가 반영돼 **과거 값이 근사치**가 된다. 당일 계산값을 정본으로 본다.
- **스토어 테스트 결제 제외** (LUT-666): 위 표의 구독 지표(`new_subscriptions`~`churned_subscriptions`)·`paying_users`·매출·코호트 매출은 `test_purchase = FALSE` 인 행만 센다(`subscription_payment_history`·`user_subscription`·`diamond_bundle_purchase`). iOS Sandbox(TestFlight·샌드박스 계정)·Google 라이선스 테스터 결제가 여기에 해당한다. 판정 규칙은 `SUBSCRIPTION_IAP.md` §9. 새 결제 쿼리를 추가하면 `GamificationSourceReaderTest.paymentQueriesExcludeTestPurchases` 목록에도 넣는다. 2026-10-29 이전 운영 스냅샷의 매출(58,800원)·결제 유저는 전부 테스트 결제였다 — 핫픽스 배포·백필 뒤 rebuild 로 다시 계산한다.
- **매출 규칙**
  - 구독: `price_amount`·`price_currency`·`price_source` 가 모두 NOT NULL 인 행만 더한다. `STORE`/`LIST`/`TRIAL(0)` 이 모두 포함된다. `REFUND` 는 음수다(환불 행 금액은 보통 정가 `LIST`. `SUBSCRIPTION_IAP.md` §9 참고).
  - 다이아: `status='PAID'` 이고 금액·통화가 있는 행만 더한다. 환불 차감은 없다(`REFUNDED` 전환 로직 자체가 없음).
  - 통화 환산: KRW 는 그대로 더한다. 다른 통화는 그 달의 `fx_rate` 가 있을 때만 환산해 더한다. 없으면 `환율 없음 — revenue_krw 에서 제외` warn 로그를 남기고, 원본은 JSON 에만 남긴다.

### 3.6 코호트 리텐션 (`CohortRetentionService`)

- 코호트는 KST 가입일(`provider≠test`)이다. `cohort_size` 는 그날 가입자 수다.

| metric | 창 (가입일 + n일) | 방식 |
|---|---|---|
| `D1` `D3` `D7` `D14` `D30` | 정확히 n일째 하루 | classic |
| `W1` | 1~7일 | 창 안에 1회 이상 |
| `W2_4` | 8~30일 | 창 안에 1회 이상 |

- `RetentionSource`
  - `REQUEST` = `user_daily_activity`.
  - `ATTENDANCE` = `attendance_record`(전 기간 소급 가능).
  - 어드민은 두 선을 나눠 그린다.
- 창의 마지막 날이 `lastComplete`(보통 어제) 이내인 지표만 저장한다. REQUEST 는 창 시작이 활동 데이터 시작일(`min(activity_date)`, LUT-598 배포일) 이후일 때만 계산한다. 그 전 창을 0 으로 오염시키지 않기 위해서다.
- D0 는 없다.
  - LUT-597 전에는 가입 당일 출석이 규칙상 0 이었다.
  - 요청 기반 활동은 가입 호출 자체가 포함돼 의미가 없다.
- 응답의 `rate` 는 `retained ÷ cohort_size`(소수 4자리, 분모가 0 이면 null)이고, 조회 시점에 계산한다.

### 3.7 코호트 매출 · LTV (`CohortRevenueService`)

- 코호트는 KST 가입 월이고, `month_n ∈ {0,1,3,6,12}` 이다.
- cutoff 는 `(가입 월 + n + 1)` 월의 1일 00:00 KST 다. 즉 n개월 뒤 월말까지의 누적이다. cutoff 가 오늘보다 뒤면 그 점은 저장하지 않는다.
- 누적액은 **KRW 결제만** 더한다(`price_currency='KRW'`). 이 경로는 `fx_rate` 를 쓰지 않으므로 외화 결제는 빠진다. 구독은 `price_source` 가 있는 행만 더하고 환불은 음수다. 다이아는 `PAID` 만 더한다.
- `paying_users` 는 cutoff 전에 금액 > 0 결제를 한 코호트 유저 수다.
- 응답의 `ltv_krw` 는 `cumulative_revenue_krw ÷ cohort_size`(소수 2자리)다.
- 스케줄러 한계는 §3.1 ⚠ 를 본다.

### 3.8 가입 채널별 가입자 — CAC 분모 (LUT-599/602)

- `GET /api/internal/kpi/signups/by-channel?from_month&to_month` 는 스냅샷이 아니라 user_db 를 직접 집계한다.
  - 월 키는 `to_char(created_at + 9h, 'YYYY-MM')` 다.
  - 채널은 `COALESCE(signup_channel, 'unknown')` 이고, `provider≠test` 다.
  - 탈퇴자도 가입 시점 기준으로 포함한다.
- `signup_channel` 값은 가입 완료 때 `SignupChannelPolicy` 가 1회 정한다.
  - `invite`
  - `utm_source` 소문자(≤50자)
  - `store_ios`(ios·ipad)
  - `store_android`
  - `organic_web`
  - `unknown` = LUT-599 배포 전 가입자
- 입력은 클라이언트가 보내는 optional 필드다: `invite_code`, `utm_source/medium/campaign`. 기기는 `DeviceTypeResolver` 로 판정한다. 웹은 first-touch 쿠키 `{prefix}utm`(30일)에서 꺼낸다.

## 4. 내부 API (`X-Internal-Api-Key`)

모든 날짜는 ISO `yyyy-MM-dd`, 월은 `yyyy-MM` 이다. 조회 API 는 from>to 면 바꿔서 처리한다. 응답은 전역 snake_case 다. 어드민은 admin-service `MvpKpiFeignClient` 로 `/api/admin/kpi/*`(`KPI_READ`, rebuild 는 `KPI_MANAGE`)에 그대로 패스스루한다.

| 메서드 · 경로 | 파라미터 | 응답 |
|---|---|---|
| `POST /api/internal/stats/activity/flush` | `date` | `{date, flushed_rows, active_users}` |
| `POST /api/internal/kpi/rebuild` | `from`, `to` | `{from, to, snapshot_days, retention_rows, revenue_rows}` |
| `GET /api/internal/kpi/daily` | `from`, `to` | 스냅샷 행 목록(§3.5 컬럼 + `snapshot_date`) |
| `GET /api/internal/kpi/retention` | `cohort_from`, `cohort_to`, `source`(선택: `REQUEST`/`ATTENDANCE`) | `{cohort_date, metric, source, cohort_size, retained, rate}` 목록 |
| `GET /api/internal/kpi/revenue/cohorts` | `from_month`, `to_month` | `{cohort_month, month_n, cohort_size, paying_users, cumulative_revenue_krw, ltv_krw}` 목록 |
| `GET /api/internal/kpi/signups/by-channel` | `from_month`, `to_month` | `{month, channel, signups}` 목록 |

**파라미터 오류는 400** (LUT-616, `statsservice/api/exception/StatsRequestExceptionHandler`)

| 예외 | code | 메시지 키 (인자) |
|---|---|---|
| `MethodArgumentTypeMismatchException`: 날짜·월 형식 오류, enum 허용값 밖 | `100101` | `error.stats.param.invalid` (파라미터명, 기대 형식 `yyyy-MM-dd`/`yyyy-MM`/enum 목록) |
| `MissingServletRequestParameterException`: 필수 파라미터 누락 | `100102` | `error.stats.param.missing` (파라미터명) |

- 플랫폼 `RestExceptionHandler` 의 `@ExceptionHandler(Exception.class)` 가 Spring 기본 400 보다 먼저 잡아 500 `000500` 을 낸다. 그래서 `@RestControllerAdvice(basePackages = "...statsservice")` + `@Order(HIGHEST_PRECEDENCE)` 로 가로챈다. 다른 서비스 응답은 영향이 없다(LUT-577 미션 어드바이스와 같은 구조).
- `@WebMvcTest` 슬라이스에는 플랫폼 핸들러가 없으므로, 테스트만으로는 500 이 보이지 않는다.
- 그 외 예외(DB 오류 등)는 여전히 500 이다.

## 5. 지표 노출 (LUT-605/615)

### 5.1 `KpiMetricsExporter` — `lut_kpi_*` 게이지

- 최신 스냅샷 1행(`findTopByOrderBySnapshotDateDesc`)을 게이지로 노출한다.
  - 값: `dau`, `wau`, `mau`, `attendance_dau`, `signups`, `withdrawals`, `active_users`, `mission_completions`, `mission_active_users`, `paying_users`, `active_subscriptions`, `new_subscriptions`, `churned_subscriptions`, `revenue_krw`
  - 메타: `lut_kpi_snapshot_age_days`, `lut_kpi_snapshot_date_epoch_seconds`(최신 `snapshot_date` 의 KST 자정 epoch)
- 스크레이프마다 DB 를 치지 않도록 5분(`CACHE_TTL`) 캐시한다. 스냅샷이 없거나 조회에 실패하면 NaN 을 낸다(스크레이프는 깨지지 않는다).
- `@Scheduled` 가 아니라서 인스턴스마다 독립적으로 동작한다. ShedLock 은 필요 없다.
- `lut_kpi_snapshot_age_days` 는 `KST 오늘 − snapshot_date` 다.
  - 정상값은 **1** 이다.
  - 단, 00:00 ~ 00:40 KST(+캐시 5분)에는 어제 행이 아직 없으므로 **2 가 정상**이다.
  - 알람은 "2 이상이 1시간 넘게 지속" 처럼 이 창을 피해 건다.
- 노출 경로는 `/showmethemoney/prometheus` 다. `InternalApiKeyFilter` 가 `X-Internal-Api-Key` 헤더 또는 HTTP Basic 비밀번호(사용자명 무관)로 키를 요구한다. `/health` 만 공개다.

### 5.2 HTTP 지연 히스토그램 — SLO 버킷만 (LUT-615)

- `application.yml` 설정: `management.metrics.distribution.slo.[http.server.requests]=100ms,300ms,1s,3s`.
- 결과로 `http_server_requests_seconds_bucket` 에 `le` 0.1/0.3/1/3/+Inf 다섯 버킷만 생긴다. p95/p99 는 `histogram_quantile(0.95, sum by (le) (rate(...[5m])))` 로 **추정**한다. 경계 사이는 선형 보간이라 정밀도에 한계가 있다.
- 이유: Boot 기본값은 버킷이 꺼져 있어 p95 를 낼 수 없다. 반대로 `percentiles-histogram=true` 는 버킷 약 70개 × uri·status 조합이라 Grafana Cloud Free 활성 시리즈 한도(1만)를 넘긴다.
- 새 지표를 추가할 때는 **라벨 조합 수(시리즈 수)를 먼저 세어 본다.** 라벨 값은 고정 집합만 쓴다(예: `RtdnMetrics`).
- Alloy 설정, 대시보드(`lut-overview`), 알람은 infra 레포 `docs/grafana-cloud.md` 에 있다.

## 6. 운영 점검

`$BASE` 는 환경별 product-service 주소, `$KEY` 는 내부 API 키다(셸 변수로만 다룬다). 날짜는 KST 다.

1. **어제 활동이 적재됐는지**
   - Redis 의 `SCARD active:<D>` 와 stats_db 의 `SELECT count(*) FROM user_daily_activity WHERE activity_date = '<D>';` 를 비교한다. 같아야 한다(`excluded-user-ids` 유저는 인터셉터 단계에서 이미 빠져 Redis 에 없다).
   - 로그에 `활동 flush 완료: date=<D>, redis 활동 유저 N명, 신규·갱신 M건` 이 있는지 본다.
   - 빠졌으면 다음을 실행한다(Redis TTL 40일 이내만 가능):
     ```bash
     curl -s -X POST -H "X-Internal-Api-Key: $KEY" "$BASE/api/internal/stats/activity/flush?date=<D>"
     ```
2. **어제 스냅샷이 만들어졌는지**
   - Grafana 에서 `lut_kpi_snapshot_age_days == 1` 인지 본다(01:00 KST 이후).
   - 로그에 `KPI 일별 스냅샷 완료` 가 있는지 본다. 실패했다면 `KPI 일별 스냅샷 실패: today=` 가 있다.
   - API 또는 SQL 로 직접 확인한다.
     ```bash
     curl -s -H "X-Internal-Api-Key: $KEY" "$BASE/api/internal/kpi/daily?from=<D>&to=<D>"
     ```
     ```sql
     SELECT snapshot_date, dau, wau, mau, attendance_dau, signups, revenue_krw
     FROM daily_kpi_snapshot ORDER BY snapshot_date DESC LIMIT 3;
     ```
3. **누락을 복구하는 순서**
   1. 활동 flush(1번)를 먼저 한다. 스냅샷의 DAU 가 `user_daily_activity` 를 읽기 때문이다.
   2. 그 다음 rebuild 한다.
      ```bash
      curl -s -X POST -H "X-Internal-Api-Key: $KEY" "$BASE/api/internal/kpi/rebuild?from=<D1>&to=<D2>"
      ```
   3. 응답의 `from`/`to` 가 요청과 다르면 정규화된 것이다(어제까지로 자르기·400일 상한).
4. **LTV 곡선 갱신**: 매월 초에 코호트 범위를 덮는 rebuild 를 1회 돌린다(§3.1 ⚠). 확인은 `SELECT cohort_month, month_n, cumulative_revenue_krw FROM cohort_revenue ORDER BY 1,2;` 로 한다.
5. **외화 매출**: 로그에 `환율 없음 — revenue_krw 에서 제외: month=…, currency=…` 가 보이면 `fx_rate(rate_month, currency, krw_per_unit)` 행을 넣고, 그 달 범위를 rebuild 한다.
6. **파라미터 오류**: 형식이 틀린 호출은 HTTP 400 + `100101`/`100102` 여야 한다. 500 이 나오면 어드바이스가 적용되는 패키지 범위를 점검한다.
