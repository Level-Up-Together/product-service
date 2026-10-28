# Flyway 마이그레이션 (LUT-595)

서버가 기동하면서 `db/migration/<db>/` 의 SQL 을 번호 순서로 적용한다 (`app.flyway.enabled=true` 인 환경만 — dev 부터, prod 는 v1.1.4 부터).

## 폴더 = DB

`user` `mission` `guild` `chat` `meta` `feed` `notification` `gamification` `stats`(LUT-598, 지표 집계 전용 신설 DB) — 각 `XxxDataSourceConfig` 의 `xxxFlywayMigration` 빈이 읽는다.

## 파일 이름

```
V<major>_<minor>_<patch>_<seq3>__<ticket>_<desc>.sql
V1_1_4_001__lut600_add_mission_tag.sql      → 버전 1.1.4.1
```

- 릴리스 번호가 앞이라 번호가 릴리스를 넘어도 계속 커진다 (Flyway 는 되돌아가는 번호를 거부한다)
- **한 번 적용한 파일은 고치지 않는다** — checksum 이 바뀌면 기동이 실패한다. 고칠 것은 새 파일로
- `FlywayMigrationNamingTest` 가 이름 규칙을 검사한다

## 여기 두지 않는 것

큰 데이터 보정(레벨 재산정·다이아 소급·백필)은 `level-up-together-sql/queries/developing/<버전>/` 에 두고 사람이 적용한다.
서버 기동 시점에 자동으로 돌리기엔 위험하다(배포 중 옛 서버 공존, 실패 시 기동 불가). 그 보정이 먼저 필요한 스키마 변경은 파일 머리에 적는다.

## 배포 중 주의

prod 는 2대 롤링이라 새 스키마와 옛 코드가 잠시 같이 돈다. 컬럼 이름 변경·삭제는 바로 하지 말고
"새 컬럼 추가 → 코드 배포 → 다음 릴리스에 옛 컬럼 삭제" 순서로 한다.
