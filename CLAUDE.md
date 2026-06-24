# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development Commands

```bash
# Build
./gradlew clean build

# Run ALL tests (2470 tests across 5 modules)
./gradlew test

# Run tests by module
./gradlew :service:test             # all service + global tests
./gradlew :app:test                 # application context, benchmark

# Run single test class / method
./gradlew :service:test --tests "*.Oauth2ControllerTest"
./gradlew :service:test --tests "*.Oauth2ControllerTest.getOauth2LoginUri"

# Run application
./gradlew bootRun                                          # Default (port 8443)
./gradlew bootRun --args='--spring.profiles.active=test'   # Test profile (port 18080)

# Generate API documentation
./gradlew openapi3 && ./gradlew sortOpenApiJson && ./gradlew copySortedOpenApiJson

# Generate GraphQL classes from DGS schema
./gradlew generateJava

# Test coverage report (minimum 70%)
./gradlew test jacocoTestReport
# Report: app/build/reports/jacoco/html/index.html
```

## Architecture Overview

**Multi-Service Monolith**: Spring Boot 3.4.5, Gradle multi-module (`service` + `app` +
`includeBuild ../level-up-together-platform`). 12개 서비스가 단일 배포 단위지만 **서비스별 별도 DB** + Saga 패턴으로 MSA 전환 준비.

**Why single service module**: 서비스 간 순환 의존성(user↔guild, user↔gamification 등)으로 독립 Gradle 모듈 불가.
`sourceSets.main.java.srcDirs`로 단일 컴파일.

**Platform shared library** (`../level-up-together-platform`):

- `kernel` — ApiResult, CustomException, Base Entity, Domain Events, **Facade 인터페이스** (UserQueryFacade,
  GuildQueryFacade, GamificationQueryFacade, MissionQueryFacade)
- `infra` — RedisConfig, JpaAuditingConfig, QueryDslConfig, JwtAuthenticationFilter, RestExceptionHandler,
  CryptoConverter
- `saga` — SagaOrchestrator, AbstractSagaStep, SagaDataSourceConfig

### Service Modules

| Service               | Database        | 주요 책임                                      |
|-----------------------|-----------------|--------------------------------------------|
| `userservice`         | user_db         | OAuth2, JWT, 프로필, 친구, quest                |
| `missionservice`      | mission_db      | 미션 정의/진행/Saga, 미션북, daily instance(pinned) |
| `guildservice`        | guild_db        | 길드, 멤버, 경험치, 게시판, 초대                       |
| `chatservice`         | chat_db         | 길드 채팅, DM, 읽음 상태                           |
| `metaservice`         | meta_db         | 공통 코드, 캘린더, 레벨/출석 보상 설정 (Redis 캐시)         |
| `feedservice`         | feed_db         | 피드 (CQRS Read Model), 좋아요, 댓글              |
| `notificationservice` | notification_db | 푸시, 알림 설정/조회                               |
| `adminservice`        | admin_db        | 홈 배너, featured content                     |
| `gamificationservice` | gamification_db | 칭호, 업적, 통계, 경험치, 출석, 이벤트, 시즌               |
| `bffservice`          | -               | BFF API 통합, 통합 검색                          |
| `noticeservice`       | -               | 공지/안내                                      |
| `supportservice`      | -               | 1:1 문의 + 신고 처리 (Admin Feign)               |

### Transaction Manager (Critical)

`userTransactionManager`가 `@Primary`로 설정됨 → **각 서비스의 `@Transactional`에 명시적으로 트랜잭션 매니저 지정 필수**:

```java

@Transactional(transactionManager = "guildTransactionManager")
public void updateGuild(...) { ...}
```

| Service             | Transaction Manager                |
|---------------------|------------------------------------|
| userservice         | `userTransactionManager` (Primary) |
| missionservice      | `missionTransactionManager`        |
| guildservice        | `guildTransactionManager`          |
| chatservice         | `chatTransactionManager`           |
| metaservice         | `metaTransactionManager`           |
| feedservice         | `feedTransactionManager`           |
| notificationservice | `notificationTransactionManager`   |
| adminservice        | `adminTransactionManager`          |
| gamificationservice | `gamificationTransactionManager`   |
| saga                | `sagaTransactionManager`           |

### Service Layer Pattern

각 서비스: `api/` (Controller) → `application/` 또는 `core/` (Service) → `domain/` (Entity/DTO/Enum) → `infrastructure/` (
Repository). 선택적으로 `scheduler/`, `saga/`. `feedservice`는 CQRS (`FeedQueryService` + `FeedCommandService`).

### Cross-Service Boundary Rules (MSA 준비)

**다른 서비스의 Repository/Service 직접 import 금지** — 반드시 Facade 인터페이스 사용:

```java
// BAD
private final UserTitleRepository userTitleRepository; // 다른 서비스 DB 직접 접근

// GOOD
private final GamificationQueryFacade gamificationQueryFacade;
```

Facade 인터페이스는 `lut-platform-kernel`에 정의, 각 서비스에서 구현. Facade DTO는 `io.pinkspider.global.facade.dto` (22개). Entity/Enum
import는 현행 유지 (MSA 전환 시 교체).

### API Response Format

All REST endpoints return `ApiResult<T>`:

```json
{
  "code": "000000",
  "message": "success",
  "value": {
    ...
  }
}
```

### Exception Handling (i18n)

`CustomException(code, messageKey)` — `RestExceptionHandler.resolveMessage()`가 `messageKey`로 MessageSource 조회, 없으면 원문
반환.

**ApiStatus 코드 규칙** (6자리: 서비스 2자리 + 카테고리 2자리 + 일련번호 2자리):

| 서비스                  | 접두사 | 서비스                  | 접두사 |
|----------------------|-----|----------------------|-----|
| global               | 00  | payment-service      | 07  |
| bff-service          | 01  | meta-service         | 08  |
| api-gateway          | 02  | logger-service       | 09  |
| user-service         | 03  | stats-service        | 10  |
| guild-service        | 04  | batch-service        | 11  |
| mission-service      | 05  | gamification-service | 12  |
| app-push-service     | 06  | feed-service         | 13  |
| notification-service | 14  |                      |     |

## Testing

| Module            | Tests | Content                                              |
|-------------------|-------|------------------------------------------------------|
| `platform:kernel` | 39    | util tests                                           |
| `platform:infra`  | 168   | resolver, validation, profanity, crypto, translation |
| `platform:saga`   | 29    | saga framework tests                                 |
| `service`         | 2222  | all service unit + controller tests                  |
| `app`             | 12    | `@SpringBootTest` (full context)                     |

**Shared utilities**: `service/shared-test/src/test/java/` (`ControllerTestConfig`, `BaseTestController`, `MockUtil`,
`TestApplication`). `kernel`의 `TestReflectionUtils`는 `java-test-fixtures` plugin으로 공유.

**Controller test**: `@WebMvcTest` + `@Import(ControllerTestConfig.class)` + `@AutoConfigureRestDocs` +
`@AutoConfigureMockMvc(addFilters = false)` + `@ActiveProfiles("test")`.

**Unit test**: `@ExtendWith(MockitoExtension.class)` + `@Mock`/`@InjectMocks`. JPA 엔티티 ID는 reflection으로 설정.

**Integration test** (`:app` only): `@SpringBootTest` + `@ActiveProfiles("test")` + 명시적
`@Transactional(transactionManager = "...")`.

**Test fixtures**: `service/{name}/src/test/resources/fixture/{servicename}/` JSON 파일 →`MockUtil.readJsonFileToClass()`.

## 코드 컨벤션

- **API 필드명**: snake_case (프론트 통신)
- **들여쓰기**: 4 spaces
- **DTO**: record 사용 권장
- **레이어 구조**: Controller → Service → Repository
- **테스트**: JUnit 5 + Mockito
- **날짜/시간**: ISO 8601 (`2026-03-24T14:30:45`), UTC 저장
- **에러 메시지**: i18n 메시지 키 (`error.xxx.yyy`), 한국어 하드코딩 금지
- **기본 언어**: 영어 (Default)
- **커밋 메시지**: `.claude/commands/commit.md` 규칙 — `type: [JIRA-번호] 설명`, 50자 이내, 한글, "with claude" 푸터 금지

### 코드 포맷팅 (Spotless + google-java-format AOSP)

전 Java 저장소는 Spotless + google-java-format(AOSP, 4-space, 100col)으로 자동 포맷팅 강제. Import 정렬 자동.

```bash
./gradlew spotlessApply  # 적용
./gradlew spotlessCheck  # CI에서 사용 — 위반 시 빌드 실패
```

**IntelliJ 사용자 권장 설정**: `google-java-format` 플러그인 + AOSP 모드 + VM Options `--add-exports`. 또는 Code Style을 Default IDE로 두기. 상세는 [`docs/CODE_FORMATTING.md`](docs/CODE_FORMATTING.md).

**Claude는 Java 코드 수정 후 반드시 `./gradlew :모듈:spotlessApply`를 실행하여 형식을 정정한다.** 작성 시 IDE 결과와 어긋날 수 있어 마무리 단계로 spotless 적용 필수.

## 작업 완료 시 규칙

- 작업이 끝나면 반드시 프로젝트별 커밋 메시지를 생성 (커밋은 직접 실행)
- 커밋 메시지: `type: 설명` (feat/fix/refactor/docs/test/chore), JIRA 티켓 번호 포함 시 `[QA-123]` 형식, 50자 이내, "with claude" 없음
- 한글로 커밋 메시지 작성
- 새로 작성된 코드에 대한 테스트 코드 병행 작성 필수
- 필요시 CLAUDE.md, README.md 업데이트

## Internationalization (i18n)

### 타임존

- JVM `TimeZone.setDefault(UTC)`, Hibernate `hibernate.jdbc.time_zone: UTC`, Jackson UTC, JDBC URL `TimeZone=UTC`
- 스케줄러는 `@Scheduled(cron = "...", zone = "Asia/Seoul")`로 비즈니스 시간대 명시

### 메시지 번역 (MessageSource)

`app/src/main/resources/i18n/` 하위에 `errors_{ko,en,ja}.properties`, `notifications_{ko,en,ja}.properties`,
`messages_{ko,en,ja}.properties`. `MessageConfig`가 `ReloadableResourceBundleMessageSource` Bean 등록 (UTF-8,
`useCodeAsDefaultMessage=true`). `LocaleInterceptor`가 `Accept-Language` 헤더를 `LocaleContextHolder`로 설정.

### 콘텐츠 번역 (Google Translation API)

`TranslationService` — 3-tier 캐시 (Redis 7일 → DB → Google API). Feed/Guild 게시판·댓글 조회 시 `Accept-Language` 헤더로 on-demand
번역. `ContentType` enum: `FEED`, `FEED_COMMENT`, `GUILD_POST`, `GUILD_COMMENT`. 설정: `google.translation.enabled`.

### 유저 언어 설정

`Users.preferredLocale` (`VARCHAR(5) DEFAULT 'en'`). `PUT /api/v1/mypage/preferred-locale`로 변경. 푸시 발송 시 유저 locale 조회 후
MessageSource로 다국어 메시지 생성.

### 금칙어 (Profanity)

`ProfanityWord.locale` 컬럼으로 언어별(`ko`/`en`/`ar`/`ja`) 관리. Unique `(locale, word)`. 통합 검사 + locale별 검사 모두 지원.

### 글로벌 서비스 로드맵

전체 마이그레이션 계획: `docs/GLOBAL_SERVICE_ROADMAP.md`

## Event-Driven 패턴

```java
// 발행
eventPublisher.publishEvent(new YourEvent(userId, data));

// 수신
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleEvent(YourEvent event) { ...}
```

**주요 이벤트 흐름 매핑은 [`docs/EVENT_FLOWS.md`](docs/EVENT_FLOWS.md) 참조** (서비스별 발행/수신 30+ 매핑, 자동 피드 생성 축소 규칙 포함).

## Redis Caching

| 캐시 서비스                    | 캐시 키                             | TTL |
|---------------------------|----------------------------------|-----|
| `UserProfileCacheService` | `userProfile:{userId}`           | 5분  |
| `FriendCacheService`      | `friendIds:{userId}`             | 10분 |
| `TitleService`            | `userTitleInfo:{userId}`         | 5분  |
| `MissionCategoryService`  | `missionCategories:{categoryId}` | 1시간 |

## Scheduler & Distributed Lock (ShedLock)

멀티 EC2에서 `@Scheduled` 동시 실행 방지를 위해 **모든 스케줄러는 `@SchedulerLock` 필수**. `ShedLockConfig`가 Redis SETNX 기반 LockProvider 등록 (
`prefix=lut`). `SchedulerLockCoverageTest`가 누락 락을 컴파일 타임에 검출.

```java

@Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
@SchedulerLock(name = "MyScheduler_method", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
public void run() { ...}
```

| 스케줄러                                                        | 주기/Zone                     | 비고                         |
|-------------------------------------------------------------|-----------------------------|----------------------------|
| `DailyMissionInstanceScheduler.generateDailyInstances`      | `0 0 0 * * *` KST           | 고정 미션 일일 인스턴스 생성 + 자정 자동완료 |
| `MissionAutoCompleteScheduler.autoCompleteExpiredMissions`  | 5분 fixedRate                | 만료(4시간) 미션 자동 종료(baseExp 120) + 경고 알림  |
| `TokenMaintenanceScheduler.cleanupExpiredSessions`          | `0 0 2 * * *` KST           | 만료된 OAuth 세션 정리            |
| `TokenMaintenanceScheduler.cleanupOrphanedUserSessions`     | `0 30 2 * * *` KST          | 고아 user_sessions 참조 정리     |
| `DailyMvpHistoryScheduler.saveDailyMvpHistory{Kst,Ast,Utc}` | `0 0 0 * * *` (KST/AST/UTC) | 타임존별 일간 MVP 기록             |
| `SeasonRewardScheduler.processEndedSeasonRewards`           | `0 0 3 * * *` KST           | 종료된 시즌 보상 자동 부여            |

## Saga Pattern

미션 완료 등 다단계 비즈니스 로직은 `AbstractSagaStep<Context>` 상속하여 `executeInternal` / `compensateInternal`(보상 트랜잭션) 구현. 오케스트레이터는
`missionservice/saga/MissionCompletionSaga.java` 참고 — Regular/Pinned 분기 + 10단계 step. 새 step 추가 시 `getStepName()`, 보상 로직,
멱등성 고려 필수.

## HTTP API 테스트

`http/` 폴더에 IntelliJ HTTP Client 형식 테스트 파일 (도메인별 분리). 환경 설정: `http/http-client.env.json` (`dev` / `local` / `test`).

## Configuration Profiles

설정 파일은 `app/src/main/resources/config/` (root가 아님). `application-{test,unit-test,push-test,local,dev,prod}.yml`.

## 관련 프로젝트

| 프로젝트              | 경로                                                                                  |
|-------------------|-------------------------------------------------------------------------------------|
| Admin Backend     | `/Users/pink-spider/Code/github/Level-Up-Together/admin-service`                    |
| Admin Frontend    | `/Users/pink-spider/Code/github/Level-Up-Together/level-up-together-admin-frontend` |
| Product Backend   | `/Users/pink-spider/Code/github/Level-Up-Together/product-service`                  |
| Product Frontend  | `/Users/pink-spider/Code/github/Level-Up-Together/level-up-together-frontend`       |
| SQL Scripts       | `/Users/pink-spider/Code/github/Level-Up-Together/level-up-together-sql/queries`    |
| Config Server     | `/Users/pink-spider/Code/github/Level-Up-Together/config-server`                    |
| Config Repo       | `/Users/pink-spider/Code/github/Level-Up-Together/config-repository`                |
| Service Discovery | `/Users/pink-spider/Code/github/Level-Up-Together/service-discovery`                |
| React Native App  | `/Users/pink-spider/Code/github/Level-Up-Together/LevelUpTogetherReactNative`       |

## 자주 발생하는 이슈

- **QueryDSL 빌드 오류** (`Attempt to recreate a file for type Q*`): `./gradlew clean compileJava`
- **데이터 미저장/미조회**: `@Transactional`의 트랜잭션 매니저 확인
- **Integration test 실패**: SSH 터널/외부 서비스 의존, `@ActiveProfiles("test")` 확인
- **Race condition** (중복 키): `saveAndFlush + DataIntegrityViolationException` 패턴 (예시: [
  `docs/FEATURES.md`](docs/FEATURES.md))

## Image Moderation & Storage

- **검증**: `@ModerateImage` 어노테이션 + `ImageModerationAspect` (AOP). `moderation.image.provider` 설정 (`none` / `onnx-nsfw` /
  `aws-rekognition`). 위반 시 `CustomException("000010", "error.moderation.inappropriate_image")`
- **저장**: `@Profile("prod")`에서 S3 + CloudFront CDN, 그 외 로컬 파일시스템. 서비스별 Strategy (`S3*ImageStorageService` /
  `Local*ImageStorageService`)

상세는 [`docs/IMAGE_INFRA.md`](docs/IMAGE_INFRA.md) 참조.

## Feature-Specific Notes

미션(고정/실행 모드/생성 한도/자동완료), 피드(공개범위/필터/동기화), 길드(초대/자동 참가), 인증(Browse-First/Signup Token Flow) 등 도메인별 비즈니스 규칙: [
`docs/FEATURES.md`](docs/FEATURES.md)

## Internal API (Admin Backend ↔ MVP)

`/api/internal/**` — VPC 내부 접근만 허용. 도메인별 베이스 경로, 신고 처리 워크플로우(WARNING/SUSPEND/BAN) 매핑: [
`docs/INTERNAL_API.md`](docs/INTERNAL_API.md)
