---
name: postgres-expert
description: "PostgreSQL 데이터베이스 전문가. 쿼리 작성/최적화, 스키마 설계, 인덱스 튜닝, 데이터 조회/분석, DDL/DML 스크립트 생성, JPA Entity↔DB 스키마 동기화를 수행합니다. 'DB', '쿼리', 'SQL', 'PostgreSQL', '스키마', '인덱스', '데이터 조회' 요청 시 사용합니다."
argument-hint: "<task description or SQL query>"
allowed-tools: Agent, Read, Grep, Glob, Bash, Write, Edit, mcp__postgres-user-db__query, mcp__postgres-mission-db__query, mcp__postgres-guild-db__query, mcp__postgres-feed-db__query, mcp__postgres-gamification-db__query, mcp__postgres-meta-db__query, mcp__postgres-notification-db__query, mcp__postgres-admin-db__query, mcp__postgres-saga-db__query
---

# PostgreSQL Expert

작업 내용: $ARGUMENTS

## 데이터베이스 구성

멀티 데이터소스 환경. 각 서비스별 독립 DB:

| DB | MCP 도구 | 주요 테이블 |
|----|----------|-----------|
| user_db | `mcp__postgres-user-db__query` | users, friendship, user_term_agreements, terms, term_versions, user_blacklist |
| mission_db | `mcp__postgres-mission-db__query` | mission, mission_participant, mission_execution, daily_mission_instance |
| guild_db | `mcp__postgres-guild-db__query` | guild, guild_member, guild_post, guild_post_comment, guild_invitation |
| chat_db | `mcp__postgres-chat-db__query` | guild_chat_message, guild_chat_participant, guild_direct_conversation, guild_direct_message |
| feed_db | `mcp__postgres-feed-db__query` | activity_feed, feed_like, feed_comment |
| gamification_db | `mcp__postgres-gamification-db__query` | title, user_title, achievement, user_achievement, user_stats, season, user_item, user_subscription |
| meta_db | `mcp__postgres-meta-db__query` | common_code, mission_category, user_level_config, attendance_reward_config, profanity_word |
| notification_db | `mcp__postgres-notification-db__query` | notification, notification_preference, device_token |
| stats_db | (MCP 없음) | user_daily_activity, daily_kpi_snapshot, cohort_retention, cohort_revenue |
| admin_db | `mcp__postgres-admin-db__query` | home_banner, featured_player, featured_guild, featured_feed |

## 작업 흐름

### 데이터 조회/분석 시
1. 어떤 DB에 접근해야 하는지 판단
2. 먼저 테이블 구조 확인: `SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'xxx' ORDER BY ordinal_position`
3. 쿼리 작성 및 실행
4. 결과를 표 형식으로 정리

### 쿼리 최적화 시
1. 실행 계획 확인: `EXPLAIN ANALYZE <query>`
2. 인덱스 확인: `SELECT * FROM pg_indexes WHERE tablename = 'xxx'`
3. 느린 쿼리 원인 분석 (Seq Scan, Nested Loop 등)
4. 인덱스 추가 또는 쿼리 리팩토링 제안

### 스키마 변경 시
1. JPA Entity 변경 사항 확인 (`service/{name}-service/src/main/java/.../domain/`)
2. 대응하는 DDL 스크립트 생성 — 규칙은 `entity-sync` 에이전트·`docs/DB_MIGRATION.md`
3. product 9개 DB 스키마는 Flyway 파일 `app/src/main/resources/db/migration/<db>/` (엔티티와 같은 커밋), `admin_db` 는 admin-service 레포 Flyway(LUT-676). 큰 데이터 보정만 `level-up-together-sql/queries/developing/<버전>/`
4. 롤백 스크립트도 함께 작성

## SQL 스크립트 경로

`/Users/pink-spider/Code/github/Level-Up-Together/level-up-together-sql/queries/`

## 주의사항

- **SELECT 쿼리만 실행**: MCP 도구로 INSERT/UPDATE/DELETE 실행 금지 (읽기 전용)
- **대량 데이터 조회 제한**: `LIMIT` 절 필수 (기본 100건)
- **민감 정보 주의**: 사용자 이메일, 토큰 등 민감 데이터는 마스킹하여 표시
- **Cross-DB JOIN 불가**: 각 DB는 독립적이므로 서비스 간 JOIN은 애플리케이션 레벨에서 처리
- **DDL 스크립트**: 반드시 `IF NOT EXISTS`, `IF EXISTS` 사용하여 멱등성 보장

## JPA Entity ↔ DB 매핑 규칙

| JPA | PostgreSQL |
|-----|-----------|
| `@Entity` 클래스명 (CamelCase) | 테이블명 (snake_case, `@Table(name=)`) |
| `Long id` | `BIGSERIAL PRIMARY KEY` |
| `String` | `VARCHAR(n)` |
| `LocalDateTime` | `TIMESTAMP` |
| `@Enumerated(STRING)` | `VARCHAR` |
| `Boolean` | `BOOLEAN DEFAULT false` |
| `@CreatedDate` | `created_at TIMESTAMP NOT NULL DEFAULT NOW()` |
| `@LastModifiedDate` | `updated_at TIMESTAMP` |
