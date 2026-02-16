# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development Commands

```bash
# Build
./gradlew clean build

# Run ALL tests (1831 tests across 5 modules)
./gradlew test

# Run tests by module
./gradlew :service:test             # all service + global tests
./gradlew :app:test                 # application context, benchmark

# Run single test class
./gradlew :service:test --tests "*.Oauth2ControllerTest"

# Run single test method
./gradlew :service:test --tests "*.Oauth2ControllerTest.getOauth2LoginUri"

# Parallel build
./gradlew test --parallel

# Run application
./gradlew bootRun                                    # Default (port 8443)
./gradlew bootRun --args='--spring.profiles.active=test'  # Test profile (port 18080)

# Generate API documentation
./gradlew openapi3 && ./gradlew sortOpenApiJson && ./gradlew copySortedOpenApiJson

# Generate GraphQL classes from DGS schema
./gradlew generateJava

# Test coverage report (minimum 70%)
./gradlew test jacocoTestReport
# Report: app/build/reports/jacoco/html/index.html
```

## Architecture Overview

**Multi-Service Monolith**: Spring Boot 3.4.5 application organized as a Gradle multi-module project (2 modules +
composite build). Services share a single deployment unit but use separate databases per service. Designed for future
MSA migration with Saga pattern.

### Gradle Multi-Module Structure

```
level-up-together-mvp/
├── settings.gradle                    # 2 modules: service, app + includeBuild platform
├── build.gradle                       # Root: common settings, BOM, -parameters flag
├── service/build.gradle               # ALL 12 services + global infra (single compilation, multi-srcDirs)
│   ├── src/main/java/                 # Global infra (datasource, security, profanity, translation 등)
│   ├── shared-test/src/test/          # Shared test utils (ControllerTestConfig, MockUtil)
│   ├── user-service/src/main/java/
│   ├── guild-service/src/main/java/
│   ├── ... (12 directories)
│   └── support-service/src/main/java/
└── app/build.gradle                   # Bootstrap + DGS codegen + JaCoCo
    ├── src/main/java/                 # LevelUpTogetherMvpApplication.java
    ├── src/main/resources/            # All config files, schemas, keystores
    └── src/test/java/                 # @SpringBootTest tests only (3 files)
```

**Platform shared library** (`../level-up-together-platform`): `includeBuild`로 IDE에서 소스 편집 가능. CI에서는 GitHub Packages
Maven artifact 사용.

- `lut-platform-kernel` — 순수 공유 타입, audit entity, API result, enums
- `lut-platform-infra` — 공통 Spring infra (Redis, security, handler 등)
- `lut-platform-saga` — Saga 프레임워크 + SagaDataSourceConfig

**Why single service module**: Circular dependencies between services (user↔guild, user↔gamification, user↔support,
guild↔gamification) prevent independent Gradle modules. Directories are separated for logical boundaries, compiled as
one unit via `sourceSets.main.java.srcDirs`.

### Service Modules (`service/{name}/src/main/java/io/pinkspider/leveluptogethermvp/`)

| Service               | Database        | Purpose                                                                                                            |
|-----------------------|-----------------|--------------------------------------------------------------------------------------------------------------------|
| `userservice`         | user_db         | OAuth2 authentication (Google, Kakao, Apple), JWT tokens, profiles, friends, quests                                |
| `missionservice`      | mission_db      | Mission definition, progress tracking, Saga orchestration, mission book, daily mission instances (pinned missions) |
| `guildservice`        | guild_db        | Guild creation/management, members, experience/levels, bulletin board, territory, invitations                      |
| `chatservice`         | chat_db         | Guild chat messaging, chat participants, read status, direct messages                                              |
| `metaservice`         | meta_db         | Common codes, calendar holidays, Redis-cached metadata, level configuration                                        |
| `feedservice`         | feed_db         | Activity feed (CQRS Read Model), likes, comments, feed visibility, FeedProjectionEventListener                     |
| `notificationservice` | notification_db | Push notifications, notification preferences, notification management                                              |
| `adminservice`        | admin_db        | Home banners, featured content (players, guilds, feeds)                                                            |
| `gamificationservice` | gamification_db | Titles, achievements, user stats, experience/levels, attendance tracking, events, seasons                          |
| `bffservice`          | -               | Backend-for-Frontend API aggregation, unified search                                                               |
| `noticeservice`       | -               | Notice/announcement management (layered: api, application, core, domain)                                           |
| `supportservice`      | -               | Customer support and report handling (api, application, core, report)                                              |

### Global Infrastructure (`service/src/main/java/io/pinkspider/global/`)

MVP 전용 인프라 코드 (platform 공유 라이브러리와 별도):

- **Multi-datasource**: All 9 DataSourceProperties + DataSourceConfigs (`global.config.datasource`)
- **Security**: SecurityConfig, OAuth2Properties, CurrentUserArgumentResolver
- **Config**: HibernateConfig, RateLimiterConfig, WebMvcConfig, WebSocketConfig, FirebaseConfig
- **Translation**: Google Translation API integration (`global.translation`)
- **Profanity**: Profanity detection and validation (`leveluptogethermvp/profanity/`)
- **Messaging**: AppPushMessageProducer (Redis Streams)
- **Rate Limiting**: PerUserRateLimit + PerUserRateLimitAspect
- **GraphQL**: DGS context, scalars, fetchers
- **Feign**: AdminInternalFeignClient (Admin Backend 연동)

### Platform Shared Library (`level-up-together-platform` 별도 레포)

`includeBuild`로 IDE에서 소스 편집 가능. 공통 인프라:

- **kernel**: ApiResult, ApiStatus, CustomException, Base Entity, Domain Events, Enums, Utils
- **infra**: RedisConfig, AsyncConfig, JpaAuditingConfig, QueryDslConfig, JwtAuthenticationFilter, RestExceptionHandler,
  CryptoConverter
- **saga**: SagaOrchestrator, AbstractSagaStep, SagaDataSourceConfig

### Transaction Manager (Critical)

멀티 데이터소스 환경에서 `userTransactionManager`가 `@Primary`로 설정되어 있으므로, **각 서비스의 `@Transactional`에 명시적으로 트랜잭션 매니저를 지정해야 함**:

```java
// GuildService 예시
@Transactional(transactionManager = "guildTransactionManager")
public void updateGuild(...) { ...}

// MissionService 예시
@Transactional(transactionManager = "missionTransactionManager")
public void updateMission(...) { ...}
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

Each service module follows a consistent layered structure:

- `api/` - REST controllers returning `ApiResult<T>` wrapper
- `application/` - Business logic services with `@Transactional`
- `core/` - Core domain logic (some services use this instead of or alongside `application/`)
- `domain/` - Entities, DTOs, enums
- `infrastructure/` - JPA repositories
- `scheduler/` - Scheduled batch jobs (optional)
- `saga/` - Saga orchestration steps (optional)

Note: Some services vary slightly (e.g., `noticeservice`/`supportservice` use `core/` instead of `application/`).
`feedservice` follows CQRS pattern with `FeedQueryService` (read) + `FeedCommandService` (write).

### Cross-Service Boundary Rules (MSA 준비)

**다른 서비스의 DB에 직접 접근(Repository import) 금지** — 반드시 해당 서비스의 Service 계층을 통해 접근:

```java
// BAD — 다른 서비스의 Repository 직접 사용
@Service
public class MyPageService {

    private final UserTitleRepository userTitleRepository; // gamification_db 직접 접근
}

// GOOD — 해당 서비스의 Service를 통해 접근
@Service
public class MyPageService {

    private final TitleService titleService; // gamificationservice Service 계층
}
```

**현재 적용 완료:**

- userservice → gamification_db: Service 계층 전환 완료 (P5)
- Entity/Enum import는 현행 유지 (MSA 전환 시 DTO/common 라이브러리로 교체 예정)

**gamificationservice 주요 크로스-서비스 조회 API:**

| Service                 | Method                                       | 용도                |
|-------------------------|----------------------------------------------|-------------------|
| `UserExperienceService` | `getUserLevel(userId)`                       | 단건 레벨 조회          |
| `UserExperienceService` | `getUserLevelMap(userIds)`                   | 배치 레벨 조회 (N+1 방지) |
| `UserExperienceService` | `getOrCreateUserExperience(userId)`          | 경험치 엔티티 조회        |
| `UserExperienceService` | `findTopExpGainersByPeriod(...)`             | MVP 랭킹 조회         |
| `TitleService`          | `getEquippedLeftTitleNameMap(userIds)`       | 배치 칭호명 조회         |
| `TitleService`          | `getEquippedTitleEntitiesByUserIds(userIds)` | 배치 칭호 엔티티 조회      |
| `TitleService`          | `getEquippedTitleEntitiesByUserId(userId)`   | 단건 칭호 엔티티 조회      |
| `TitleService`          | `changeTitles(userId, leftId, rightId)`      | 칭호 변경 (WRITE)     |
| `UserStatsService`      | `getOrCreateUserStats(userId)`               | 통계 조회             |
| `UserStatsService`      | `calculateRankingPercentile(points)`         | 랭킹 퍼센타일 계산        |

### API Response Format

All REST endpoints return `ApiResult<T>` from `io.pinkspider.global.api`:

```json
{
  "code": "000000",
  "message": "success",
  "value": {
    ...
  }
}
```

### Exception Handling

Custom exceptions should extend `CustomException`:

```java
public class YourServiceException extends CustomException {

    public YourServiceException() {
        super("XXXXXX", "Error message");
    }
}
```

**ApiStatus 코드 규칙** (6자리: 서비스 2자리 + 카테고리 2자리 + 일련번호 2자리):
| 서비스 | 코드 접두사 |
|--------|------------|
| global | 00 |
| bff-service | 01 |
| api-gateway | 02 |
| user-service | 03 |
| guild-service | 04 |
| mission-service | 05 |
| app-push-service | 06 |
| payment-service | 07 |
| meta-service | 08 |
| logger-service | 09 |
| stats-service | 10 |
| batch-service | 11 |
| gamification-service | 12 |
| feed-service | 13 |
| notification-service | 14 |

## Testing

### Test Distribution (1831 tests across 5 modules)

| Module            | Tests | Content                                              |
|-------------------|-------|------------------------------------------------------|
| `platform:kernel` | 39    | util tests                                           |
| `platform:infra`  | 168   | resolver, validation, profanity, crypto, translation |
| `platform:saga`   | 29    | saga framework tests                                 |
| `service`         | 1583  | all service unit + controller tests (multi-srcDirs)  |
| `app`             | 12    | ApplicationTests, benchmark, TestDataSourceConfig    |

### Shared Test Utilities

- `kernel/src/testFixtures/` → `TestReflectionUtils` (shared via `java-test-fixtures` plugin)
- `service/shared-test/src/test/java/` → `TestApplication`, `ControllerTestConfig`, `BaseTestController`, `MockUtil`
- `service/shared-test/src/test/resources/application.yml` → `spring.cloud.config.enabled: false`

### Test Fixtures

JSON fixtures in `service/{name}/src/test/resources/fixture/{servicename}/` loaded via `MockUtil`:

```java
MockUtil.readJsonFileToClass("fixture/userservice/oauth/mockCreateJwtResponseDto.json",CreateJwtResponseDto .class);
```

### Controller Tests (in `:service` module)

```java
@WebMvcTest(controllers = YourController.class, excludeAutoConfiguration = {...})
@Import(ControllerTestConfig.class)
@AutoConfigureRestDocs
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
```

### Unit Tests (Service Layer, in `:service` module)

```java

@ExtendWith(MockitoExtension.class)
class YourServiceTest {

    @Mock
    private YourRepository repository;

    @InjectMocks
    private YourService service;

    // Reflection으로 엔티티 ID 설정 (JPA 엔티티는 ID가 auto-generated)
    private void setEntityId(Entity entity, Long id) {
        try {
            Field idField = Entity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
```

### Integration Tests (in `:app` module — needs full application context)

```java

@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "yourTransactionManager")
class YourIntegrationTest {

    @Autowired
    private YourService service;
}
```

## 코드 컨벤션

- **API 필드명**: 프론트와 백엔드간 통신은 snake_case 사용
- **들여쓰기**: 4 spaces
- **DTO**: record 사용 권장
- **레이어 구조**: Controller → Service → Repository
- **테스트**: JUnit 5 + Mockito

## Event-Driven 패턴

### 이벤트 발행

```java

@Service
@RequiredArgsConstructor
public class YourService {

    private final ApplicationEventPublisher eventPublisher;

    @Transactional(transactionManager = "yourTransactionManager")
    public void doSomething() {
        // 비즈니스 로직
        eventPublisher.publishEvent(new YourEvent(userId, data));
    }
}
```

### 이벤트 수신

```java

@Component
@RequiredArgsConstructor
public class YourEventListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleEvent(YourEvent event) {
        // 이벤트 처리 (다른 서비스 호출, 알림 발송 등)
    }
}
```

### 주요 이벤트 흐름

| 발행 서비스                 | 이벤트                                | 수신 리스너                             | 처리 내용            |
|------------------------|------------------------------------|------------------------------------|------------------|
| GuildService           | `GuildJoinedEvent`                 | `AchievementEventListener`         | 길드 가입 업적 체크      |
| GuildService           | `GuildJoinedEvent`                 | `FeedProjectionEventListener`      | 길드 가입 피드 생성      |
| GuildService           | `GuildCreatedEvent`                | `FeedProjectionEventListener`      | 길드 창설 피드 생성      |
| GuildService           | `GuildInvitationEvent`             | `NotificationEventListener`        | 초대 알림 발송         |
| GuildExperienceService | `GuildLevelUpEvent`                | `FeedProjectionEventListener`      | 길드 레벨업 피드 생성     |
| FriendService          | `FriendRequestAcceptedEvent`       | `NotificationEventListener`        | 친구 수락 알림         |
| FriendService          | `FriendRequestAcceptedEvent`       | `FeedProjectionEventListener`      | 친구 추가 피드 생성 (양쪽) |
| GamificationService    | `TitleAcquiredEvent`               | `NotificationEventListener`        | 칭호 획득 알림         |
| GamificationService    | `TitleAcquiredEvent`               | `FeedProjectionEventListener`      | 칭호 획득 피드 생성      |
| GamificationService    | `AchievementCompletedEvent`        | `NotificationEventListener`        | 업적 달성 알림         |
| GamificationService    | `AchievementCompletedEvent`        | `FeedProjectionEventListener`      | 업적 달성 피드 생성      |
| GamificationService    | `TitleEquippedEvent`               | `FeedProjectionEventListener`      | 칭호 변경 피드 업데이트    |
| UserExperienceService  | `UserLevelUpEvent`                 | `FeedProjectionEventListener`      | 레벨업 피드 생성        |
| AttendanceService      | `AttendanceStreakEvent`            | `FeedProjectionEventListener`      | 연속 출석 피드 생성      |
| MissionService         | `MissionStateChangedEvent`         | `MissionStateHistoryEventListener` | 미션 상태 이력 저장      |
| GuildMemberService     | `GuildMemberJoinedChatNotifyEvent` | `ChatEventListener`                | 채팅방 입장 알림        |
| GuildMemberService     | `GuildMemberLeftChatNotifyEvent`   | `ChatEventListener`                | 채팅방 퇴장 알림        |
| GuildMemberService     | `GuildMemberKickedChatNotifyEvent` | `ChatEventListener`                | 채팅방 추방 알림        |

## Redis Caching

| 캐시 서비스                    | 캐시 키                             | TTL |
|---------------------------|----------------------------------|-----|
| `UserProfileCacheService` | `userProfile:{userId}`           | 5분  |
| `FriendCacheService`      | `friendIds:{userId}`             | 10분 |
| `TitleService`            | `userTitleInfo:{userId}`         | 5분  |
| `MissionCategoryService`  | `missionCategories:{categoryId}` | 1시간 |

## Saga Pattern (Mission Completion 예시)

```
missionservice/saga/
├── MissionCompletionSaga.java        - Saga 오케스트레이터
├── MissionCompletionContext.java     - Saga 컨텍스트 (공유 데이터)
└── steps/
    ├── LoadMissionDataStep.java
    ├── CompleteExecutionStep.java
    ├── UpdateParticipantProgressStep.java
    ├── GrantUserExperienceStep.java
    ├── GrantGuildExperienceStep.java
    ├── UpdateUserStatsStep.java
    └── CreateFeedFromMissionStep.java
```

### Saga Step 구현

```java

@Component
public class YourStep extends AbstractSagaStep<YourContext> {

    @Override
    public String getStepName() {
        return "YOUR_STEP";
    }

    @Override
    protected SagaStepResult executeInternal(YourContext context) {
        // 비즈니스 로직
        return SagaStepResult.success();
    }

    @Override
    protected SagaStepResult compensateInternal(YourContext context) {
        // 보상 트랜잭션 (롤백 로직)
        return SagaStepResult.success();
    }
}
```

## HTTP API 테스트

`http/` 폴더에 IntelliJ HTTP Client 형식의 API 테스트 파일:

| 파일                     | 설명                                |
|------------------------|-----------------------------------|
| `oauth-jwt.http`       | OAuth2 로그인, JWT 토큰 관리, 모바일 소셜 로그인 |
| `mission.http`         | 미션 CRUD, 참가자, 실행 추적, 캘린더          |
| `guild.http`           | 길드 관리, 게시판, 거점                    |
| `guild-chat.http`      | 길드 채팅 (메시지, 참여자, 읽음)              |
| `activity-feed.http`   | 피드, 좋아요, 댓글, 검색                   |
| `friend.http`          | 친구 요청/수락/거절/차단                    |
| `mypage.http`          | 프로필, 닉네임, 칭호 관리                   |
| `achievement.http`     | 업적, 칭호, 레벨 랭킹                     |
| `attendance.http`      | 출석 체크                             |
| `notification.http`    | 알림 관리, 읽음 처리                      |
| `device-token.http`    | FCM 토큰 등록/삭제                      |
| `event.http`           | 이벤트 API                           |
| `bff.http`             | BFF 홈, 통합 검색, 시즌                  |
| `home.http`            | 홈 배너, 추천 콘텐츠                      |
| `meta.http`            | 메타데이터, 공통 코드                      |
| `user-terms.http`      | 약관 동의                             |
| `user-experience.http` | 경험치, 레벨                           |
| `guild-dm.http`        | 길드 DM (다이렉트 메시지)                  |
| `test-login.http`      | 테스트 로그인                           |

환경 설정: `http/http-client.env.json`

```json
{
  "dev": {
    "baseUrl": "https://dev-api.level-up-together.com"
  },
  "local": {
    "baseUrl": "https://local.level-up-together.com:8443"
  },
  "test": {
    "baseUrl": "http://localhost:18080"
  }
}
```

## Configuration Profiles

| 프로필                                            | 설명                                    |
|------------------------------------------------|---------------------------------------|
| `application.yml`                              | Default configuration                 |
| `application-test.yml`                         | H2 databases, test Kafka (port 18080) |
| `application-unit-test.yml`                    | Unit test configuration               |
| `application-push-test.yml`                    | Push notification test configuration  |
| `application-local.yml`                        | Config server integration             |
| `application-dev.yml` / `application-prod.yml` | Environment-specific                  |

Note: Config files are located in `app/src/main/resources/config/`, not the root of `resources/`.

## 관련 프로젝트

| 프로젝트              | 경로                                                                                  |
|-------------------|-------------------------------------------------------------------------------------|
| Admin Backend     | `/Users/pink-spider/Code/github/Level-Up-Together/level-up-together-mvp-admin`      |
| Admin Frontend    | `/Users/pink-spider/Code/github/Level-Up-Together/level-up-together-admin-frontend` |
| Product Backend   | `/Users/pink-spider/Code/github/Level-Up-Together/level-up-together-mvp`            |
| Product Frontend  | `/Users/pink-spider/Code/github/Level-Up-Together/level-up-together-frontend`       |
| SQL Scripts       | `/Users/pink-spider/Code/github/Level-Up-Together/level-up-together-sql/queries`    |
| Config Server     | `/Users/pink-spider/Code/github/Level-Up-Together/config-repository`                |
| Service Discovery | `/Users/pink-spider/Code/github/Level-Up-Together/service-discovery`                |
| React Native App  | `/Users/pink-spider/Code/github/Level-Up-Together/LevelUpTogetherReactNative`       |

## 자주 발생하는 이슈

### QueryDSL 빌드 오류

`Attempt to recreate a file for type Q*` 오류 발생 시:

```bash
./gradlew clean compileJava
```

### 트랜잭션 매니저 미지정 오류

데이터가 저장되지 않거나 조회되지 않는 경우, `@Transactional`에 올바른 트랜잭션 매니저가 지정되어 있는지 확인

### Integration Tests 실패

SSH 터널이나 외부 서비스 연결이 필요한 테스트는 로컬에서 실패할 수 있음. `@ActiveProfiles("test")` 확인

### Race Condition (중복 키 오류) 해결 패턴

동시 요청으로 인한 `DataIntegrityViolationException` (Unique constraint violation) 발생 시:

```java
// Check-then-insert 패턴 대신 saveAndFlush + 예외 처리 사용
private Entity getOrCreateEntity(String key) {
    return repository.findByKey(key)
        .orElseGet(() -> {
            try {
                Entity newEntity = Entity.builder().key(key).build();
                return repository.saveAndFlush(newEntity);  // 즉시 INSERT
            } catch (DataIntegrityViolationException e) {
                // 중복 발생 시 기존 레코드 조회
                return repository.findByKey(key)
                    .orElseThrow(() -> new IllegalStateException("Race condition 처리 실패"));
            }
        });
}
```

적용 사례: `AchievementService.getOrCreateUserAchievement()`, `AttendanceService.checkIn()`,
`NotificationService.createNotificationWithDeduplication()`

## Feature-Specific Implementation Notes

### Pinned Mission (고정 미션) - Template-Instance 패턴

고정 미션(`isPinned=true`)은 `DailyMissionInstance` 엔티티를 사용:

- 매일 자동 생성 (스케줄러: `DailyMissionInstanceScheduler`, cron: `0 5 0 * * *`)
- 미션 정보 스냅샷 저장 (미션 변경 시 과거 기록 보존)

API 라우팅 (하위 호환성 유지):

```java
// MissionExecutionService에서 isPinned 체크 후 분기
if(isPinnedMission(missionId, userId)){
    return dailyMissionInstanceService.

startInstanceByMission(...);
}
```

### Guild Invitation (길드 초대)

비공개 길드 초대 시스템:

- 초대 상태: `PENDING`, `ACCEPTED`, `REJECTED`, `CANCELLED`, `EXPIRED`
- 만료 시간: 7일
- 같은 카테고리의 다른 길드 가입자는 초대 불가

API 엔드포인트:

```
POST   /api/v1/guilds/{guildId}/invitations         - 초대 발송
GET    /api/v1/users/me/guild-invitations           - 내 대기중 초대 목록
POST   /api/v1/guild-invitations/{id}/accept        - 초대 수락
POST   /api/v1/guild-invitations/{id}/reject        - 초대 거절
DELETE /api/v1/guild-invitations/{id}               - 초대 취소 (마스터)
```