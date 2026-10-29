# DB 마이그레이션 — Flyway + 릴리스 SQL (LUT-595)

product-service 9개 DB 의 스키마 변경을 **서버 기동 시 Flyway 가 적용**하고, 큰 데이터 보정은 지금처럼 **`level-up-together-sql` 레포 + 사람이 수동 적용**한다.
결정 배경은 [ADR-007](adr/ADR-007-Flyway%20Per-Database%20Migrations-20261023.md). 기준: `epic/v1.1.3`, 2026-10-29.

## 1. 한눈에

| 종류 | 위치 | 적용 주체 · 시점 |
|---|---|---|
| 스키마 변경(표·컬럼·인덱스) · 작은 멱등 수정 | `app/src/main/resources/db/migration/<db>/` | Flyway — product 기동 시 (`app.flyway.enabled=true` 환경만) |
| 큰 데이터 보정(재산정·소급·백필) | `level-up-together-sql/queries/developing/<버전>/` → 적용 후 `done/migration/<버전>/` | 사람 — psql 수동, 런북에 시점 명시 |
| `admin_db` (admin-service) | SQL 레포 | 사람 — admin-service 에는 Flyway 없음 |
| `saga_db` | — | **삭제됨**(2026-10-29, LUT-665) — 한 번도 쓰이지 않아 dev·prod 모두 백업 후 `DROP DATABASE`(ADR-003 갱신). 백업은 로컬 `~/lut-backup/saga_db_{dev,prod}_20261029_*.sql` |

## 2. 동작 구조 (코드)

- Boot 자동설정은 DB 하나(primary)만 다루므로 끈다 — `app/src/main/resources/config/application.yml` `spring.flyway.enabled: false`.
- DB 마다 `global/config/datasource/XxxDataSourceConfig` 가 `xxxFlywayMigration` 빈을 `initMethod = "migrate"` 로 등록하고, 같은 DB 의 `xxxEntityManagerFactory` 가 `@DependsOn("xxxFlywayMigration")` 한다 → **Flyway 가 스키마를 맞춘 뒤 Hibernate `validate`** 가 돈다.

```
xxxDataSource ──▶ xxxFlywayMigration (initMethod=migrate, classpath:db/migration/<db>)
                        │ @DependsOn
                        ▼
                 xxxEntityManagerFactory (hibernate.hbm2ddl.auto=validate) ──▶ xxxTransactionManager
```

| 폴더(`<db>`) | 설정 클래스 | Flyway 빈 |
|---|---|---|
| `user` | `UserDataSourceConfig` (DataSource·EMF·TM `@Primary`, Flyway 빈은 아님) | `userFlywayMigration` |
| `mission` | `MissionDataSourceConfig` | `missionFlywayMigration` |
| `guild` | `GuildDataSourceConfig` | `guildFlywayMigration` |
| `chat` | `ChatDataSourceConfig` | `chatFlywayMigration` |
| `meta` | `MetaDataSourceConfig` | `metaFlywayMigration` |
| `feed` | `FeedDataSourceConfig` | `feedFlywayMigration` |
| `notification` | `NotificationDataSourceConfig` | `notificationFlywayMigration` |
| `gamification` | `GamificationDataSourceConfig` | `gamificationFlywayMigration` |
| `stats` | `StatsDataSourceConfig` (LUT-598, 9번째) | `statsFlywayMigration` |

- 래퍼 `global/config/datasource/flyway/FlywayMigration` 의 구성(`configure()`): `locations=classpath:db/migration/<db>` · `baselineOnMigrate(true)` + `baselineVersion(app.flyway.baseline-version)` · `validateOnMigrate(true)` · `outOfOrder(false)` · `failOnMissingLocations(false)`(빈 폴더 허용).
  `enabled=false` 면 Flyway 객체를 만들지 않고 DataSource 에도 접속하지 않는다(지연 생성).
- **`validate` 는 yml 이 아니라 코드가 정한다** — 각 config 의 `jpaProperties()` 가 `hibernate.hbm2ddl.auto=validate` 를 지정한다. config-repository yml 의 `ddl-auto: none` 은 이 커스텀 EMF 에 적용되지 않는다. 엔티티와 스키마가 어긋나면 기동이 멈춘다.
- 의존성: `service/build.gradle` `flyway-core` + `flyway-database-postgresql` (Boot BOM, 10.20.1).
- 테스트: 9개 DataSourceConfig 는 `@Profile("!test & !push-test")` 라 `test` 프로필에선 아예 뜨지 않는다. 대신 `app` 테스트의 `TestDataSourceConfig`(`@Profile({"test","push-test"})`)가 같은 이름의 H2 DataSource·EMF(`hbm2ddl.auto=create-drop`)를 올리므로 **Flyway SQL 은 어떤 테스트에서도 실행되지 않는다** — dev 배포가 첫 실검증이다.
- 로그: `Flyway 완료: db=<db>, 적용 N건, 현재 버전 X (기동 전 Y)` / 꺼져 있으면 `Flyway 비활성 (app.flyway.enabled=false): db=<db>`. 이력 표는 DB 마다 `flyway_schema_history`.

## 3. 파일 규칙

```
app/src/main/resources/db/migration/<db>/V<major>_<minor>_<patch>_<seq3>__<ticket>_<desc>.sql
예) stats/V1_1_3_012__lut601_kpi_snapshot_tables.sql   → 버전 1.1.3.12
```

- 릴리스 번호가 앞이라 버전이 릴리스를 넘어 계속 커진다(예전 SQL 레포 규칙 "릴리스마다 V001 재시작"은 Flyway 와 맞지 않아 폐기).
- 관례: 일련번호는 **릴리스 안에서 DB 폴더를 가로질러 통번**이다 (1.1.3: stats 008 · user 009 · gamification 010/011 · stats 012 — SQL 레포 V### 와 같은 번호).
- `FlywayMigrationNamingTest`(app)가 검사: 9개 폴더 존재 · 이름 정규식 `^V\d+_\d+_\d+_\d{3}__[a-z0-9][a-z0-9_]*\.sql$`(설명은 소문자) · **폴더 안** 버전 중복 금지.
- **적용된 파일은 절대 고치지 않는다** — checksum 이 바뀌면 `validateOnMigrate` 로 기동이 실패한다. 고칠 것은 새 파일로.
- `outOfOrder(false)`: 이미 적용된 최고 버전보다 **낮은** 새 파일은 적용되지 않고 검증 실패로 기동이 멈춘다. 더 높은 릴리스 파일이 먼저 적용된 DB(공유 dev DB 등)에 옛 릴리스 번호의 파일을 넣지 말 것. ❓ 핫픽스에 스키마가 필요한 경우의 번호 정책은 아직 정해지지 않았다.
- 현재 파일들은 `CREATE TABLE IF NOT EXISTS` · `ADD COLUMN IF NOT EXISTS` · `ON CONFLICT DO NOTHING` 으로 멱등이다 — baseline 값을 잘못 잡아 재실행돼도 무해하게 쓰는 것을 권장.

## 4. 설정 `app.flyway` · 환경별 상태

| 키 | 기본값(`FlywayMigrationProperties`·`application.yml`) | 의미 |
|---|---|---|
| `app.flyway.enabled` | `false` | false 면 9개 DB 모두 no-op |
| `app.flyway.baseline-version` | `1.1.3.7` | `flyway_schema_history` 가 없는 **비어 있지 않은** DB 를 "이 버전까지 적용됨" 으로 받아들이는 지점. 그 이하 파일은 건너뛴다 |

baseline 은 Flyway `baselineOnMigrate` 의미 그대로라 **빈 스키마(새로 만든 DB)에는 baseline 행을 만들지 않고 모든 파일을 적용**한다 — dev `stats_db` 가 이렇게 `V1_1_3_008` 부터 적용됐다.

| 환경 | `enabled` | `baseline-version` | 출처 | 상태 |
|---|---|---|---|---|
| test · push-test | — | — | DataSourceConfig 미로딩 | Flyway 빈 없음, H2 create-drop |
| local | `false` | `1.1.3.7` | config-repository `product-service-local.yml` | 공유 dev DB 에 다른 브랜치 파일을 먼저 넣지 않게 off |
| dev | `true` | `1.1.3.7` | `product-service-dev.yml` (`47be0d5`, 2026-10-23) | 1.1.3 V001~V007 은 수동 적용분 → baseline, `V1_1_3_008`~`012` 는 Flyway 가 적용 |
| prod | `true` | `1.1.3.12` | `product-service-prod.yml` (`a86d78b`, 2026-10-28 12:26) | v1.1.3 SQL(V001~V013)은 2026-10-28 psql 수동 적용. 릴리스 중엔 false 였다 |

- prod 는 config 전환 이후 **첫 product 기동 때** 9개 DB 에 history 표 + baseline `1.1.3.12` 행만 생기고 적용 0건이다. 그 첫 기동은 2026-10-29 08:13~08:18 KST LUT-626 핫픽스 롤링 배포였다(배포 헬스체크 통과). classpath 최고 버전이 `V1_1_3_012` = baseline 이라 적용 파일은 없다. DB 에서 직접 확인하려면 `select to_regclass('flyway_schema_history')`.
- **v1.1.4 가 prod 에서 Flyway 가 실제로 파일을 적용하는 첫 릴리스**다. 1.1.3.12 = product classpath 의 1.1.3 마지막 파일(`V1_1_3_012`); SQL 레포 V013 은 `admin_db` 라 product 와 무관.
- dev 서버는 `develop`·`epic/**` push 로 배포된다(`gradle-dev.yml`) — 같은 dev DB 에 여러 브랜치가 번갈아 기동한다는 뜻이므로 3절의 번호 규칙을 지킬 것.

## 5. Flyway 에 넣을 것 / SQL 레포에 둘 것

기준은 하나 — **"서버가 뜨는 데 꼭 필요한가."** 필요하면 Flyway, 아니면 수동.

| 구분 | 1.1.3 의 예 (분류 기준) | v1.1.4 부터 위치 |
|---|---|---|
| 스키마 변경 | V005 슬롯 컬럼, V006 멱등 키 표, V008/V012 stats 표, V009 users 컬럼, V010 정가 표 | Flyway |
| 작은 멱등 시드 | V011 정가 시드 3행 | Flyway |
| 큰 데이터 보정 · 실행 시점 조건이 있는 수정 | V001 레벨 곡선(새 EXP 코드 뒤 + Redis evict), V002 경험치 재산정, V003 업적 선완료, V004 다이아 소급, V007 새싹 백필 | SQL 레포 + 수동 |

- 1.1.3 의 V001~V007 은 Flyway 도입 전 파일이라 SQL 레포에만 있고, V008~V012 는 prod Flyway 가 꺼져 있었으므로 **같은 내용이 양쪽에** 있다(product Flyway = dev, SQL 레포 = prod 수동).
- 큰 보정을 기동 시 자동 실행하지 않는 이유: 롤링 배포 중 옛 서버가 같이 돌고, 실패하면 서버가 뜨지 않는다. 보정에는 "새 코드가 뜬 뒤" 같은 실행 시점 조건이 붙는 경우가 많다(v1.1.3 은 새 EXP 공식 코드 배포 후에 V001~V004 를 돌려야 했다 — 옛 공식 + 새 곡선 = 과속 레벨업).
- 그 보정이 먼저 필요한 스키마 변경이면 Flyway 파일 머리 주석에 적는다.
- 큰 소급 계산은 rebuild API·수동 SQL 로 (예: KPI 소급은 `POST /api/internal/kpi/rebuild`, LUT-601).
- 환경마다 값이 다른 데이터는 id 를 하드코딩하지 말고 이름 등으로 해석한다(V007 이 `'성장의 새싹'` 이름으로 id 해석). Flyway placeholder 는 설정돼 있지 않다.

## 6. 릴리스 순서 · 롤링 배포

prod 는 ALB 뒤 2대를 **EC2 #1 → #2 순서로** 교체한다(`gradle-prod.yml`: deregister → drain → SSM 배포 → register → healthy 대기). 그래서:

1. **스키마는 코드보다 먼저.** Flyway 분은 #1 기동 시 자동으로 먼저 적용된다. Flyway 밖의 스키마(admin_db 등)는 코드 배포 전에 수동 적용한다 — `validate` 때문에 빠지면 기동이 멈춘다.
2. **#1 이 새 스키마를 적용하는 동안 #2 는 옛 코드로 새 스키마를 본다.** 스키마 변경은 옛 코드에 무해해야 한다 → 추가만. **컬럼 이름 변경·삭제는 "새 컬럼 추가 → 코드 배포 → 다음 릴리스에 옛 컬럼 삭제"** 3단계로. 실제 사례: v1.1.3 V005 의 `send_time` RENAME 은 2026-10-28 롤링 창 동안 옛 프로세스에서 `send_time does not exist` 오류 15건을 냈다.
3. **데이터 보정은 코드 배포·헬스 확인 뒤.** (v1.1.3 런북 `done/migration/1.1.3/apply_prod_v113.sh`: `schema` 단계 = 배포 전, `data` 단계 = 배포 후.)
4. **DB 간 순서 의존을 만들지 않는다.** Flyway 는 DB 하나 안의 순서만 지킨다. 9개 Flyway 빈 사이의 실행 순서는 보장하지 않는다.
5. Flyway 실패(checksum·검증·SQL 오류) = #1 기동 실패 → 워크플로가 #1 단계에서 실패해 #2 는 배포되지 않고 옛 코드로 서비스를 이어 간다.
6. 상위 원칙은 CLAUDE.md "배포 순서 · API 하위호환": SQL → platform publish → product → admin → web → RN.

## 7. 캐시되는 엔티티에 컬럼을 추가할 때 (LUT-534)

Redis 캐시(`@Cacheable`, 기본 TTL 없음)에 들어가는 엔티티에 컬럼을 추가하면, 옛 JSON 은 역직렬화 오류 없이 **새 필드만 `null`** 로 살아남는다 — `RedisConfig` 의 "역직렬화 오류 시 evict" 안전망에 걸리지 않는다(LUT-534: 길드 레벨 강등 사고, 이후 `guildLevelConfigs`·`userLevelConfigs` 는 TTL 1시간).

- Flyway 는 Redis 를 모른다 → **배포 직후 해당 캐시 키 evict 를 런북 단계로 둔다.** 캐시되는 설정값을 바꾸는 데이터 수정도 같다 (v1.1.3: V001 뒤 `userLevelConfigs::*` DEL).
- `@PostConstruct warmUpCache()` 같은 self-invocation 은 캐시 프록시를 타지 않아 갱신 효과가 없다.

## 8. 새 마이그레이션 추가 체크리스트

- [ ] Flyway 대상인가? ("서버가 뜨는 데 필요한가" — 아니면 SQL 레포 `queries/developing/<버전>/`)
- [ ] `db/migration/<db>/V<릴리스>_<seq3>__<ticket>_<desc>.sql` — 릴리스 내 다음 통번, 설명 소문자
- [ ] 머리 주석: 티켓·목적·선행 조건(필요한 수동 보정이 있으면 명시)
- [ ] 멱등(`IF NOT EXISTS`, `ON CONFLICT DO NOTHING`) · 추가만(이름 변경·삭제는 2릴리스로)
- [ ] 다른 DB 의 마이그레이션 순서에 기대지 않는다
- [ ] 엔티티 변경과 같은 커밋 — `validate` 통과 여부는 dev 배포 로그로 확인
- [ ] 캐시되는 엔티티·설정이면 배포 후 evict 단계를 런북에 추가 (7절)
- [ ] `./gradlew :app:test --tests "*FlywayMigrationNamingTest"`
- [ ] dev 배포 후 로그 `Flyway 완료: db=<db>, 적용 1건` 과 `flyway_schema_history` 행 확인
- [ ] 적용된 뒤에는 그 파일을 고치지 않는다 — 수정은 새 파일
- [ ] **새 DB 를 추가할 때**: DB 자체는 수동 `CREATE DATABASE`(Flyway 는 DB 를 만들지 않는다) · 폴더 + `FlywayMigrationNamingTest.DATABASES` · `XxxDataSourceConfig` 에 Flyway 빈 + `@DependsOn` · `DataSourceConfigWiringTest.CONFIGS` (⚠ 2026-10-29 현재 이 목록엔 `StatsDataSourceConfig` 가 빠져 있다)

## 9. 관련 코드 · 테스트 · 문서

- 코드: `service/src/main/java/io/pinkspider/global/config/datasource/flyway/{FlywayMigration,FlywayMigrationProperties}.java`, `.../datasource/*DataSourceConfig.java`
- 테스트: `FlywayMigrationTest`(service — on/off·지연 생성·구성값·기본값) · `DataSourceConfigWiringTest`(service — user 3빈 `@Primary`, EMF `@DependsOn`; 2026-10-23 dev 배포에서 `userEntityManagerFactory` 의 `@Primary` 가 떨어져 기동 실패한 뒤 추가, `94a4f1c9`) · `FlywayMigrationNamingTest`(app)
- 폴더 README: `app/src/main/resources/db/migration/README.md`
- SQL 레포: `level-up-together-sql/CLAUDE.md`(폴더·번호 규칙), v1.1.3 운영 적용 스크립트 `done/migration/1.1.3/apply_prod_v113.sh`
- 설정: `config-repository/product-service/product-service-{local,dev,prod}.yml` 의 `app.flyway`
