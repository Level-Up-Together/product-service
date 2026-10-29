# ADR-008: KPI Read Model via Direct Read-Only SQL

**Date:** 2026-10-23
**Status:** Accepted (Implemented)
**Deciders:** Backend Team
**Branch:** `epic/v1.1.3`

## Context

IR 용 KPI(DAU/WAU/MAU, D1~D30 리텐션, 매출, 코호트 LTV, CAC)를 시계열로, 어드민에서 내보낼 수 있게 만든다. 설계는 3층이다 — ① 활동 신호 수집(LUT-598) → ② 일별 스냅샷·코호트 집계(LUT-601, 이 ADR) → ③ 어드민 화면(LUT-602/603).

### 집계에 필요한 원천이 여러 서비스 DB 에 흩어져 있다

| 지표 | 원천 | DB (소유 서비스) |
|---|---|---|
| 가입·탈퇴·활성 유저, 코호트 분모, 채널별 가입 | `users` | user_db (userservice) |
| 요청 기반 DAU | `user_daily_activity` | stats_db (statsservice 자신) |
| 출석 DAU·출석 리텐션 | `attendance_record` | gamification_db |
| 구독·매출 | `user_subscription`, `subscription_payment_history`, `diamond_bundle_purchase` | gamification_db |
| 미션 완료 | `mission_execution`, `daily_mission_instance`, `mission_participant` | mission_db |

### 제약

- **서비스 경계 규칙**: 다른 서비스의 Repository/Service 직접 import 금지 → Facade(`lut-platform-kernel`) 경유 (CLAUDE.md "Cross-Service Boundary Rules").
- **집계는 product 안에서만 가능**: admin-service 는 `admin_db` 만 직접 접근하고 나머지는 Feign → `/api/internal/**` 이라 DB 간 조인을 할 수 없다.
- **소급이 필요하다**: 요청 기반 DAU 는 LUT-598 배포일부터만 있다. 그 전 기간은 출석·가입·결제 이력으로 거슬러 계산해야 한다(dev 에서 2026-04-01~10-22, 205일 소급).
- **집계 형태**: 기간 COUNT, `COUNT(DISTINCT)`, 월×채널 GROUP BY, 유저 ID 집합 — 엔티티 단건 조회가 아니라 집합 연산이다.
- 설계 문서(Notion "KPI 통계 체계 설계", 2026-10-23) §4-2 는 처음에 각 서비스 Facade 에 `findUserIdsCreatedBetween`, `countAttendanceByDate` 같은 읽기 메서드를 추가하는 안이었다.

## Decision

**statsservice 가 다른 서비스 DB 를 `NamedParameterJdbcTemplate` 로 읽기 전용 SQL 조회한다.** Facade·다른 서비스의 Repository/Entity 는 쓰지 않는다. KPI 는 읽기 모델이므로 원천 테이블 스키마와의 결합을 받아들이고, 그 결합을 `infrastructure/source/*SourceReader` 3개 클래스에 가둔다.

```
userDataSource ─────────▶ UserSourceReader ──────────┐
gamificationDataSource ─▶ GamificationSourceReader ──┼─▶ KpiSnapshotService / CohortRetentionService / CohortRevenueService
missionDataSource ──────▶ MissionSourceReader ───────┘        │ (+ stats JPA: UserDailyActivityRepository)
                                                               ▼
                                         stats_db: daily_kpi_snapshot · cohort_retention · cohort_revenue (· fx_rate)
                                                               ▼
                                         GET /api/internal/kpi/* ──▶ admin-service MvpKpiFeignClient (LUT-602)
```

- `statsservice/kpi/config/KpiSourceConfig` 가 기존 DataSource 빈(`userDataSource`·`gamificationDataSource`·`missionDataSource`)에 `NamedParameterJdbcTemplate` 를 붙여 리더 3개를 만든다. 새 커넥션 풀은 없다.
- 리더는 **SELECT 만** 던진다. SQL 은 각 리더의 `static final String` 상수.
- statsservice 자기 데이터(`user_daily_activity`, 스냅샷 표)는 `statsTransactionManager` + JPA 로 읽고 쓴다.
- 공통 규칙은 리더 SQL 에 고정: 날짜 경계 KST(`KpiDates`: KST 날짜 → UTC 구간 `[D-1 15:00, D 15:00)`), 테스트 계정 `provider='test'` 제외, 매출은 `price_source IS NOT NULL` 행만(LUT-600), 환불 음수.
- 실행: `KpiSnapshotScheduler` 00:40 KST(ShedLock, 활동 flush 00:20 뒤) + 소급 `POST /api/internal/kpi/rebuild?from&to`(최대 400일, 기동 시 자동 실행 없음).
- LUT-602 의 `GET /api/internal/kpi/signups/by-channel` 도 같은 방식으로 user_db 를 직접 집계한다(스냅샷 아님).

## Consequences

### Positive

- **platform·다른 서비스 코드 변경 없음**: Facade 메서드를 추가하면 kernel 인터페이스 → 각 서비스 구현 → platform SNAPSHOT publish 선행이 필요하다. 리더 방식은 statsservice 안에서 끝난다.
- **집합 연산을 SQL 그대로**: `COUNT(DISTINCT)`·GROUP BY·`to_char(created_at + INTERVAL '9 hours', 'YYYY-MM')` 를 DB 에서 처리해 유저 목록을 애플리케이션으로 끌어오지 않는다.
- **원천에서 소급 가능**: 스냅샷이 틀리거나 정의가 바뀌면 rebuild 로 다시 계산한다(멱등 upsert).
- **import 경계는 유지**: statsservice 는 다른 서비스의 패키지를 하나도 import 하지 않는다. 결합 지점은 리더 3개 + `KpiSourceConfig` 뿐이라 MSA 전환 시 교체 범위가 분명하다.

### Negative

- **스키마 결합**: user/gamification/mission 의 컬럼 이름 변경·의미 변경이 컴파일 오류 없이 KPI SQL 을 런타임에 깨뜨린다. 해당 서비스 개발자가 KPI 의존을 의식하지 못할 수 있다.
- **SQL 이 테스트에서 실행되지 않는다**: 리더 단위 테스트는 `NamedParameterJdbcTemplate` 를 mock 하고 SQL 문자열만 단언한다. 컨텍스트 테스트는 `TestDataSourceConfig` 의 H2 DataSource 가 같은 이름으로 주입될 뿐 쿼리는 돌지 않고, SQL 은 PostgreSQL 전용 문법을 쓴다. → **SQL 을 고치면 dev DB 에서 먼저 실행해 볼 것** (H2 통합 테스트 없음).
- **읽기 전용은 관례**: 각 서비스의 기존 Hikari 풀(각 최대 10)을 그대로 쓴다. 읽기 전용 커넥션이나 DB 롤로 강제하지 않는다.
- **OLTP 풀 공유**: 집계 쿼리가 서비스 트래픽과 같은 풀을 쓴다. 스케줄은 00:40 KST 로 피크를 피하고, rebuild 는 400일 상한.
- **경계 규칙의 예외**: "다른 서비스 DB 직접 접근 금지" 의 문서화된 예외가 생겼다 — 읽기 모델(KPI)에만 허용한다.

### Risks & Mitigations

| Risk | Mitigation |
|---|---|
| 원천 컬럼 변경으로 스냅샷 실패·오계산 | 리더에 의존 테이블·컬럼이 상수로 모여 있음 → 스키마 변경 시 `grep` 대상. 스냅샷 실패는 `lut_kpi_snapshot_age_days ≥ 2` 게이지·알람으로 감지(LUT-605) |
| SQL 회귀를 테스트가 못 잡음 | dev DB 실행 확인을 규칙으로(CLAUDE.md). LUT-601 은 dev 소급 205일 결과를 수동 SQL 과 대조 |
| 테스트 계정·운영자가 지표를 부풀림 | 모든 리더 SQL 에 `provider='test'` 제외, 활동은 `app.stats.activity.excluded-user-ids` 제외 |
| 집계 쿼리가 OLTP 에 부하 | 하루 1회 새벽 배치 + rebuild 상한. 필요 시 읽기 복제본 DataSource 로 리더만 교체 ❓(현재 복제본 없음) |

## Alternatives Considered

### 1. 각 서비스 Facade 에 읽기 메서드 추가 (최초 설계안)

- **Pros**: 경계 규칙을 예외 없이 지킴, 스키마 변경이 해당 서비스 안에 머묾
- **Cons**: KPI 정의가 바뀔 때마다 kernel 인터페이스·서비스 구현·platform publish 가 따라 움직인다. 집계용 메서드가 도메인 Facade 에 섞인다. 집합 연산을 메서드 단위로 쪼개야 한다
- **Not chosen**: 구현(LUT-601)에서 리더 방식으로 변경 — 커밋 메시지 "SourceReader(JdbcTemplate 읽기 전용) … 다른 서비스 Repository 미참조"

### 2. 도메인 이벤트로 statsservice 자체 프로젝션 구축

- **Pros**: 스키마 결합 없음, MSA 목표 구조에 가장 가깝다
- **Cons**: 과거 이력 소급이 불가능하다(이벤트는 배포 후부터). 가입·출석·결제·미션 완료마다 이벤트·리스너 배선이 필요하다
- **Not chosen**: 소급이 핵심 요구. MSA 전환 시 리더를 이 방식으로 바꾸는 것이 이후 경로

### 3. admin-service 에서 집계

- **Cons**: admin-service 는 `admin_db` 만 직접 접근하고 나머지는 Feign 이라 DB 간 조인 불가
- **Rejected**

### 4. GA4(Firebase Analytics)를 정본으로

- **Cons**: 결제는 RN 안에서 일어나 웹 GA 가 보지 못하고, 데이터 임계치·샘플링이 있다
- **Rejected**: GA4 는 보조(LUT-604). 서버 스냅샷이 정본

## Future Considerations

- MSA 전환 시 리더 3개만 이벤트 구독 또는 각 서비스의 집계 API 로 교체한다. 서비스·스냅샷·내부 API 는 그대로 둔다.
- 원천 서비스의 스키마 변경 PR 에서 `statsservice/kpi/infrastructure/source` 를 함께 확인하는 리뷰 체크 항목 ❓(아직 자동화 없음).

## References

- `service/stats-service/src/main/java/io/pinkspider/leveluptogethermvp/statsservice/kpi/` — `config/KpiSourceConfig`, `infrastructure/source/{User,Gamification,Mission}SourceReader`, `domain/KpiDates`
- CLAUDE.md "KPI 일별 스냅샷 · 코호트 (LUT-601)"
- 커밋 `34d0b0f7` (2026-10-23) feat: [LUT-601] KPI 일별 스냅샷·코호트 리텐션·코호트 매출 집계
- Notion "KPI 통계 체계 설계 — DAU/MAU · 리텐션 · 매출 · LTV · CAC (IR용)"

## Related ADRs

- [ADR-001: Multi-Service Monolith Architecture](ADR-001-Multi-Service%20Monolith%20Architecture-20260130.md) — 서비스별 DB·경계 규칙
- [ADR-002: Event-Driven Cross-Service Communication](ADR-002-Event-Driven%20Cross-Service%20Communication-20260130.md) — 대안 2 의 기반
- [ADR-006: MSA Architecture and Admin Service Strategy](ADR-006-MSA%20Architecture%20and%20Admin%20Service%20Strategy-20260212.md) — admin 의 Feign 패스스루 구조
- [ADR-007: Flyway Per-Database Migrations](ADR-007-Flyway%20Per-Database%20Migrations-20261023.md) — stats_db 스키마(`V1_1_3_008`, `V1_1_3_012`)
