# ADR-007: Flyway Per-Database Migrations

**Date:** 2026-10-23
**Status:** Accepted (Implemented — dev 2026-10-23, prod 활성화 2026-10-28, prod 첫 적용은 2026-10-29 LUT-666 핫픽스 `V1_1_3_013`)
**Deciders:** Backend Team
**Branch:** `epic/v1.1.3`

## Context

product-service 는 서비스별 DB 를 따로 쓴다(ADR-001). 스키마 변경은 별도 레포 `level-up-together-sql` 에 V### 파일로 두고, 사람이 배포 전에 psql 로 적용해 왔다.

### 기존 방식 (LUT-595 이전)

| 항목 | 상태 |
|---|---|
| SQL 위치 | `level-up-together-sql/queries/developing/<버전>/V###__*.sql` → prod 적용 후 `done/migration/<버전>/` 로 이동 |
| 번호 | 릴리스마다 `V001` 부터 다시 시작 |
| 대상 DB | 파일 머리 주석으로 표기 |
| 적용 기록 | 없음 — "적용됐는지"는 컬럼·인덱스를 직접 조회해 판단 (SQL 레포 CLAUDE.md: ledger 테이블 없음) |
| 적용 주체 | 사람 — SSH 터널 + 자격 복호화 + `psql -v ON_ERROR_STOP=1` |
| 스키마 검증 | 8개 `XxxDataSourceConfig` 가 코드로 `hibernate.hbm2ddl.auto=validate` (yml `ddl-auto: none` 은 이 EMF 에 미적용) |

### 문제

1. **SQL 적용을 사람이 기억해야 한다.** "배포 전 V### 적용 여부 확인" 규칙을 따로 두고 있었다.
2. **빠뜨리면 기동이 멈추는데, 고칠 수단이 수작업뿐이다.** `validate` 덕분에 누락은 배포 시점에 잡히지만, 복구는 "터널 열고 psql 적용 후 재배포"였다.
3. **적용 절차가 무겁다.** DB 8개(이후 stats 추가로 9개), 릴리스마다 SQL 5~13개. v1.1.3 은 13개 파일에 DB 간 순서 제약까지 있었다.
4. **Spring Boot Flyway 자동설정(`spring.flyway.*`)은 DataSource 하나만 다룬다.** 그대로 켜면 primary(user_db) 하나만 관리된다.

### 요구사항

- DB 마다 독립된 적용 이력과 순서
- Hibernate `validate` 보다 먼저 스키마가 맞춰질 것
- 환경마다 다른 시작점(dev 와 prod 의 적용 상태가 달랐다)
- 테스트·local 은 영향 없음
- 큰 데이터 보정을 서버 기동에 묶지 않을 것

## Decision

**DB 마다 Flyway 인스턴스를 하나씩 직접 만들어 EntityManagerFactory 앞에서 실행한다.** Boot 자동설정은 끈다.

### 구성

```
xxxDataSource ──▶ xxxFlywayMigration (initMethod=migrate, classpath:db/migration/<db>)
                        │ @DependsOn
                        ▼
                 xxxEntityManagerFactory (hbm2ddl=validate) ──▶ xxxTransactionManager
```

- `application.yml` `spring.flyway.enabled: false`.
- 래퍼 `global/config/datasource/flyway/FlywayMigration` + 설정 `FlywayMigrationProperties`(`app.flyway.enabled` 기본 false, `app.flyway.baseline-version` 기본 `1.1.3.7`).
- 9개 `XxxDataSourceConfig`(user·mission·guild·chat·meta·feed·notification·gamification·stats)가 각각 `xxxFlywayMigration` 빈을 등록하고 `xxxEntityManagerFactory` 가 `@DependsOn` 한다. stats 는 같은 날 LUT-598 로 9번째가 됐다.
- `validate` 는 유지한다 — Flyway 뒤의 마지막 안전망.

| Flyway 옵션 | 값 | 이유 |
|---|---|---|
| `locations` | `classpath:db/migration/<db>` | 폴더 = DB |
| `baselineOnMigrate` / `baselineVersion` | `true` / `app.flyway.baseline-version` | 이미 스키마가 있는 DB 를 "여기까지 적용됨" 으로 받아들인다. 환경별 값은 config-repository |
| `validateOnMigrate` | `true` | 적용된 파일의 checksum 변경 등은 조용히 넘기지 않고 기동 실패 |
| `outOfOrder` | `false` | 적용된 최고 버전보다 낮은 새 파일 거부 |
| `failOnMissingLocations` | `false` | 아직 파일이 없는 폴더 허용 |

`enabled=false` 면 Flyway 객체를 만들지 않고 DataSource 에도 접속하지 않는다. 9개 DataSourceConfig 는 `@Profile("!test & !push-test")` 라 테스트에는 아예 뜨지 않는다.

### 파일 규칙

`app/src/main/resources/db/migration/<db>/V<major>_<minor>_<patch>_<seq3>__<ticket>_<desc>.sql` — 릴리스 번호를 앞에 둬 버전이 계속 커지게 한다. `FlywayMigrationNamingTest` 가 형식·폴더 내 중복을 검사한다.

### 범위 — "서버가 뜨는 데 꼭 필요한가"

| 구분 | 담당 |
|---|---|
| 스키마 변경(표·컬럼·인덱스), 작은 멱등 시드 | Flyway |
| 큰 데이터 보정(재산정·소급·백필), 실행 시점 조건이 있는 수정 | `level-up-together-sql` + 사람 (런북에 시점 명시) |

### 도입 시점

| 일시 | 내용 |
|---|---|
| 2026-10-23 08:29 | config `47be0d5` (dev `enabled: true`, local·prod false) + product `f464a339` |
| 2026-10-23 09:03 | `94a4f1c9` — 1차 dev 배포에서 `userEntityManagerFactory` 의 `@Primary` 가 빠져 기동 실패 → 복원 + `DataSourceConfigWiringTest` |
| 2026-10-23 | dev 8개 DB baseline `1.1.3.7` 행 생성, 이후 `V1_1_3_008`~`012` Flyway 적용 |
| 2026-10-28 | v1.1.3 운영 SQL(V001~V013)은 기존 방식(psql 수동)으로 적용 → 12:26 config `a86d78b` prod `enabled: true`, `baseline-version: 1.1.3.12` |
| v1.1.4 | prod 에서 Flyway 가 파일을 적용하는 첫 릴리스(계획) |
| 2026-10-29 | 계획보다 먼저 LUT-666 핫픽스 `gamification/V1_1_3_013` 이 prod 첫 Flyway 적용 파일이 됐다(`main` 운영 배포) |

릴리스와 도구 교체를 한 번에 하지 않았다 — 문제가 나면 원인이 둘 중 무엇인지 가르기 어렵기 때문이다.

## Consequences

### Positive

- **적용 여부를 DB 가 기억한다** — DB 마다 `flyway_schema_history`. "SQL 먼저 적용" 수작업 단계가 product DB 에서는 사라진다.
- **스키마 변경이 코드와 같은 커밋**에 들어가 리뷰·배포 단위가 일치한다.
- **순서 보장**: Flyway → `validate` → JPA. 누락은 Flyway 가 채우고, 그래도 어긋나면 `validate` 가 막는다.
- **환경 차이 흡수**: baseline 값을 config 에 둬 dev(1.1.3.7)와 prod(1.1.3.12)가 다른 시작점을 갖는다.

### Negative

- **설정이 9배**: Flyway 빈·폴더·이력 표가 DB 마다 있다. 새 DB 를 추가할 때 손댈 곳이 늘었다(폴더, 네이밍 테스트 목록, Flyway 빈 + `@DependsOn`, 배선 테스트 목록).
- **Flyway SQL 은 테스트에서 실행되지 않는다** (테스트는 H2 + create-drop). dev 배포가 첫 실검증이다.
- **DB 간 순서는 Flyway 가 모른다.** 9개 빈 사이 실행 순서를 보장하지 않으므로 DB 간 의존은 설계로 피해야 한다.
- **두 갈래 유지**: 큰 데이터 보정은 여전히 SQL 레포 + 수동이다. `admin_db` 도 v1.1.3 까지는 SQL 레포였고, v1.1.4 부터 admin-service 자체 Flyway 다(LUT-676). v1.1.3 은 prod Flyway 가 꺼져 있어 V008~V012 가 두 레포에 같은 내용으로 존재했다.
- **기동 실패 반경**: 마이그레이션이 실패하면 서버가 뜨지 않는다. 롤링 배포가 EC2 #1 에서 멈추고 #2 는 옛 코드로 남는다.

### Risks & Mitigations

| Risk | Mitigation |
|---|---|
| 적용된 파일 수정 → checksum 불일치로 기동 실패 | "적용된 파일은 고치지 않는다" 규칙 + `validateOnMigrate` 로 즉시 드러남 |
| 롤링 배포 중 옛 코드 + 새 스키마 공존 | 추가만 허용. 이름 변경·삭제는 "추가 → 배포 → 다음 릴리스 삭제" (v1.1.3 V005 RENAME 이 롤링 창에서 옛 프로세스 오류 15건을 낸 사례) |
| baseline 을 낮게 잡아 수동 적용분 재실행 | prod baseline 을 classpath 최고 파일(1.1.3.12)로. 파일은 `IF NOT EXISTS` 등으로 멱등하게 작성 |
| DataSourceConfig 배선 실수(`@Primary` 유실 등)를 컨텍스트 테스트가 못 잡음 | `DataSourceConfigWiringTest` 리플렉션 가드. ⚠ 2026-10-29 현재 목록에 `StatsDataSourceConfig` 미포함 |
| local 이 공유 dev DB 에 다른 브랜치 파일을 먼저 적용 | local `enabled: false` — dev DB 는 dev 서버 배포만 적용 |
| 큰 보정이 기동 시 자동 실행돼 배포를 막음 | 범위 규칙으로 Flyway 에서 제외, 런북 수동 단계 유지 |
| 캐시되는 엔티티에 컬럼 추가 시 스테일 캐시(LUT-534) | Flyway 범위 밖 — 배포 직후 evict 를 런북 단계로 |

## Alternatives Considered

### 1. 현행 유지 (SQL 레포 + 수동 psql)

- **Pros**: 새 설정 없음, 적용 시점을 사람이 완전히 통제
- **Cons**: 위 문제 1~3 그대로. 적용 이력이 없어 상태 판단이 매번 조회 작업
- **Rejected**: 릴리스마다 반복되는 수작업과 누락 위험

### 2. Spring Boot Flyway 자동설정 (`spring.flyway.*`)

- **Pros**: 설정 몇 줄
- **Cons**: DataSource 하나만 관리 — 9개 중 primary(user_db)만 된다. 커스텀 EMF 들과의 순서도 보장되지 않는다
- **Rejected**: 다중 DB 구조에 맞지 않음

### 3. 데이터 보정까지 전부 Flyway 로

- **Pros**: SQL 레포를 완전히 없앨 수 있다
- **Cons**: 1.1.3 SQL 의 절반이 비즈니스 규칙이 든 큰 보정(V002 재산정, V004 소급, V007 백필)이다. 기동 시 자동 실행은 롤링 배포 중 옛 서버 공존·실패 시 기동 불가·"새 코드 뒤" 같은 시점 조건과 충돌한다
- **Rejected**: 범위를 "서버가 뜨는 데 필요한 것" 으로 제한

### 4. v1.1.3 운영 배포부터 바로 Flyway

- **Pros**: 이중 관리(V008~V012) 기간이 없다
- **Cons**: 릴리스와 도구 교체가 겹쳐 장애 시 원인 분리가 어렵다. prod 는 당시 1.1.1 까지만 적용된 상태라 baseline 지점이 dev 와 달랐다
- **Rejected**: v1.1.3 은 수동, 직후 baseline 을 잡고 v1.1.4 부터

### 5. DB 간 순서를 `@DependsOn` 체인으로 강제

- **Pros**: meta → gamification 같은 교차 순서를 코드로 표현
- **Cons**: 9개 빈이 서로 묶여 구조가 경직되고, 그런 의존 자체가 MSA 분리와 반대 방향
- **Rejected**: DB 간 순서 의존을 만들지 않는 쪽을 택함

## References

- `docs/DB_MIGRATION.md` — 운영 레퍼런스(파일 규칙·환경별 상태·체크리스트)
- `app/src/main/resources/db/migration/README.md`
- `service/src/main/java/io/pinkspider/global/config/datasource/flyway/`
- `level-up-together-sql/done/migration/1.1.3/apply_prod_v113.sh` — v1.1.3 schema/data 단계 분리 스크립트
- Flyway 10.20.1 (Boot BOM) 설정 옵션 `baselineOnMigrate` · `validateOnMigrate` · `outOfOrder`
- Notion "Flyway 도입 가이드 — 8개 DB 마이그레이션 자동화 (261022)"

## Related ADRs

- [ADR-001: Multi-Service Monolith Architecture](ADR-001-Multi-Service%20Monolith%20Architecture-20260130.md) — 서비스별 DB 분리
- [ADR-008: KPI Read Model via Direct Read-Only SQL](ADR-008-KPI%20Read%20Model%20via%20Direct%20Read-Only%20SQL-20261023.md) — stats_db 신설과 같은 릴리스
