-- stats_db (LUT-598, LUT-691 추가): 유저 일별 활동(DAU 원천). 스냅샷 표(daily_kpi_snapshot·cohort_*)는 집계라 유저 행이 없다.
-- 주의: 이미 만들어진 daily_kpi_snapshot 의 과거 DAU 값은 그대로다 — 다시 맞추려면 POST /api/internal/kpi/rebuild.
-- 사용법: psql -v ON_ERROR_STOP=1 -v uid="'<USER-UUID>'" -f stats_db.sql

\echo === stats_db: deleting data for :'uid' ===

BEGIN;

DELETE FROM user_daily_activity WHERE user_id = :'uid';

COMMIT;

\echo === stats_db: done ===
