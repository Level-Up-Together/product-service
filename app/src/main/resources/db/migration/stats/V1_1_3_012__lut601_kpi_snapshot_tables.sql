-- LUT-601: KPI 2층 — 일별 스냅샷·가입 코호트 리텐션·가입 월 코호트 매출·월별 환율.
-- 매일 00:40 KST KpiSnapshotScheduler 가 어제 행을 upsert, 과거는 POST /api/internal/kpi/rebuild 로 소급(서버 기동 시 자동 실행 없음).
-- prod(v1.1.3, Flyway off)는 level-up-together-sql V012 수동 적용 → v1.1.4 Flyway 전환 시 baseline 1.1.3.12 이상.
CREATE TABLE IF NOT EXISTS daily_kpi_snapshot (
    snapshot_date         DATE          PRIMARY KEY,
    signups               INTEGER       NOT NULL DEFAULT 0,
    withdrawals           INTEGER       NOT NULL DEFAULT 0,
    active_users          INTEGER       NOT NULL DEFAULT 0,
    dau                   INTEGER       NOT NULL DEFAULT 0,
    wau                   INTEGER       NOT NULL DEFAULT 0,
    mau                   INTEGER       NOT NULL DEFAULT 0,
    dau_web               INTEGER       NOT NULL DEFAULT 0,
    dau_ios               INTEGER       NOT NULL DEFAULT 0,
    dau_android           INTEGER       NOT NULL DEFAULT 0,
    attendance_dau        INTEGER       NOT NULL DEFAULT 0,
    mission_completions   INTEGER       NOT NULL DEFAULT 0,
    mission_active_users  INTEGER       NOT NULL DEFAULT 0,
    new_subscriptions     INTEGER       NOT NULL DEFAULT 0,
    active_subscriptions  INTEGER       NOT NULL DEFAULT 0,
    churned_subscriptions INTEGER       NOT NULL DEFAULT 0,
    trial_starts          INTEGER       NOT NULL DEFAULT 0,
    trial_conversions     INTEGER       NOT NULL DEFAULT 0,
    paying_users          INTEGER       NOT NULL DEFAULT 0,
    revenue_krw           NUMERIC(14,2) NOT NULL DEFAULT 0,
    revenue_by_currency   JSONB,
    created_at            TIMESTAMP     NOT NULL DEFAULT now(),
    modified_at           TIMESTAMP
);
COMMENT ON TABLE daily_kpi_snapshot IS 'KST 날짜당 1행 KPI 스냅샷 — DAU/WAU/MAU·가입·미션·구독·매출 (LUT-601)';
COMMENT ON COLUMN daily_kpi_snapshot.dau IS '인증 API 요청 기준 활동 유저 (user_daily_activity, LUT-598 배포일부터)';
COMMENT ON COLUMN daily_kpi_snapshot.attendance_dau IS '홈 진입 자동 출석 기준 활동 유저 (소급 가능한 대체 신호)';
COMMENT ON COLUMN daily_kpi_snapshot.revenue_krw IS 'KRW 합 + 환율(fx_rate) 있는 통화 환산. 금액 출처 미상(price_source null) 제외';
COMMENT ON COLUMN daily_kpi_snapshot.revenue_by_currency IS '통화별 매출 원본 JSON';

CREATE TABLE IF NOT EXISTS cohort_retention (
    cohort_date  DATE        NOT NULL,
    metric       VARCHAR(10) NOT NULL,   -- D1 D3 D7 D14 D30 W1 W2_4
    source       VARCHAR(12) NOT NULL,   -- REQUEST | ATTENDANCE
    cohort_size  INTEGER     NOT NULL,
    retained     INTEGER     NOT NULL,
    created_at   TIMESTAMP   NOT NULL DEFAULT now(),
    modified_at  TIMESTAMP,
    PRIMARY KEY (cohort_date, metric, source)
);
COMMENT ON TABLE cohort_retention IS '가입(KST) 코호트 리텐션 — 창이 닫힌 지표만 저장. 비율 = retained / cohort_size (LUT-601)';

CREATE TABLE IF NOT EXISTS cohort_revenue (
    cohort_month           VARCHAR(7)    NOT NULL,   -- YYYY-MM
    month_n                INTEGER       NOT NULL,   -- 0 1 3 6 12
    cohort_size            INTEGER       NOT NULL,
    paying_users           INTEGER       NOT NULL,
    cumulative_revenue_krw NUMERIC(14,2) NOT NULL,
    created_at             TIMESTAMP     NOT NULL DEFAULT now(),
    modified_at            TIMESTAMP,
    PRIMARY KEY (cohort_month, month_n)
);
COMMENT ON TABLE cohort_revenue IS '가입 월 코호트의 month_n 월말까지 누적 KRW 매출 — LTV = cumulative / cohort_size (LUT-601)';

CREATE TABLE IF NOT EXISTS fx_rate (
    rate_month   VARCHAR(7)    NOT NULL,   -- YYYY-MM
    currency     VARCHAR(3)    NOT NULL,
    krw_per_unit NUMERIC(14,6) NOT NULL,
    created_at   TIMESTAMP     NOT NULL DEFAULT now(),
    PRIMARY KEY (rate_month, currency)
);
COMMENT ON TABLE fx_rate IS '월별 환율(1 외화 = krw_per_unit 원), 수동 입력. 없는 통화는 revenue_krw 에서 제외 (LUT-601)';
