# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development Commands

```bash
# Build
./gradlew clean build

# Run ALL tests (3,600+ tests across 5 modules)
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

**Multi-Service Monolith**: Spring Boot 3.4.5, Java 21, Gradle multi-module (`service` + `app` +
`includeBuild ../level-up-together-platform`). 11개 서비스가 단일 배포 단위지만 **서비스별 별도 DB** + Saga 패턴으로 MSA 전환 준비.
(adminservice는 별도 레포 `admin-service`(Admin Backend)로 이전됨)

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
| `notificationservice` | notification_db | 알림 생성/조회, FCM 푸시, 실시간(WS), 디바이스 토큰      |
| `gamificationservice` | gamification_db | 칭호, 업적, 통계, 경험치, 출석, 이벤트, 시즌, **상점/인벤토리/다이아** |
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

| Module            | Tests  | Content                                                       |
|-------------------|--------|---------------------------------------------------------------|
| `platform:kernel` | 43     | util tests + `NotificationTypeTest`                            |
| `platform:infra`  | 2      | `RestExceptionHandlerTest` (resolver/profanity/crypto 테스트는 `service`로 이동) |
| `platform:saga`   | 29     | saga framework tests                                          |
| `service`         | 3,585  | all service unit + controller tests                           |
| `app`             | 15     | `@SpringBootTest` (full context) + 벤치마크/통합                  |

**Shared utilities**: `service/shared-test/src/test/java/` (`ControllerTestConfig`, `BaseTestController`, `MockUtil`,
`TestApplication`). `kernel`의 `TestReflectionUtils`는 `java-test-fixtures` plugin으로 공유.

**Controller test**: `@WebMvcTest` + `@Import(ControllerTestConfig.class)` + `@AutoConfigureRestDocs` +
`@AutoConfigureMockMvc(addFilters = false)` + `@ActiveProfiles("test")`.

**Unit test**: `@ExtendWith(MockitoExtension.class)` + `@Mock`/`@InjectMocks`. JPA 엔티티 ID는 reflection으로 설정.

**Integration test** (`:app` only): `@SpringBootTest` + `@ActiveProfiles("test")` + 명시적
`@Transactional(transactionManager = "...")`.

**Test fixtures**: `service/{name}/src/test/resources/fixture/{servicename}/` JSON 파일 →`MockUtil.readJsonFileToClass()`.

**공개 엔드포인트 회귀 가드** (`SecurityConfigPublicEndpointTest`, LUT-350): 비로그인 허용 경로가 실제로 `SecurityConfig`에서
`permitAll`인지 검증한다.

기존 컨트롤러 테스트는 **구조적으로 경로 인가를 검증할 수 없다** — ① `ControllerTestConfig`의 테스트 필터체인이
`anyRequest().permitAll()`로 시큐리티를 통째로 끄고, ② `SecurityConfig` 자체가 `@Profile("!test")`라 `@ActiveProfiles("test")`
슬라이스에는 아예 올라오지 않는다.

그래서 이 테스트는 `@ActiveProfiles("security-guard")`(= `test`가 아님)로 **실제 `SecurityConfig`만** 올리고 컨트롤러는 하나도
등록하지 않는다. 차단되면 `AuthEntryPointJwt`가 401, 허용되면 핸들러가 없어 404가 나므로 단언은 "401인가 아닌가"로 충분하다.
**비로그인 허용 API를 추가할 때 이 테스트의 경로 목록에 함께 등록할 것.**

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

`app/src/main/resources/i18n/` 하위에 `errors_{ko,en,ja,ar}.properties`, `notifications_{ko,en,ja,ar}.properties`,
`messages_{ko,en,ja,ar}.properties`. `MessageConfig`가 `ReloadableResourceBundleMessageSource` Bean 등록 (UTF-8,
`useCodeAsDefaultMessage=true`). `LocaleInterceptor`가 `Accept-Language` 헤더를 `LocaleContextHolder`로 설정.

### 콘텐츠 번역 (Google Translation API)

`TranslationService` — 3-tier 캐시 (Redis 7일 → DB → Google API). Feed/Guild 게시판·댓글 조회 시 `Accept-Language` 헤더로 on-demand
번역. `ContentType` enum: `FEED`, `FEED_COMMENT`, `GUILD_POST`, `GUILD_COMMENT`. 설정: `google.translation.enabled`.

### 유저 언어 설정

`Users.preferredLocale` (`VARCHAR(5) DEFAULT 'en'`). `PUT /api/v1/mypage/preferred-locale`로 변경. 푸시 발송 시 유저 locale 조회 후
MessageSource로 다국어 메시지 생성.

### 금칙어 (Profanity)

`ProfanityWord.locale` 컬럼으로 언어별(`ko`/`en`/`ar`/`ja`) 관리. Unique `(locale, word)`. 통합 검사 + locale별 검사 모두 지원.

## Event-Driven 패턴

```java
// 발행
eventPublisher.publishEvent(new YourEvent(userId, data));

// 수신
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleEvent(YourEvent event) { ...}
```

**주요 이벤트 흐름 매핑은 [`docs/EVENT_FLOWS.md`](docs/EVENT_FLOWS.md) 참조** (서비스별 발행/수신 30+ 매핑, 자동 피드 생성 축소 규칙 포함).

## 알림/푸시 파이프라인 (Kafka 미사용)

도메인 이벤트 → `NotificationEventListener`(AFTER_COMMIT + `@Async`) → `NotificationService`:
① 카테고리 off면 전부 스킵 → ② DB 저장 (유저 preferredLocale로 **발송 시점 현지화**) → ③ Redis Pub/Sub
`notification:realtime` → WebSocket `/user/queue/notifications` 실시간 릴레이 → ④ `pushEnabled` && 방해금지 아님이면
Redis Stream `stream:app-push` → `AppPushMessageConsumer` → `FcmPushService`(FCM).

- `NotificationType`은 platform kernel에 정의 (category, messageTemplate, actionUrlPattern, dedup 여부)
- 방해금지(quiet hours)는 유저 `preferred_timezone` 기준 판정, **푸시만 억제** (DB 저장은 유지)
- 카테고리 토글은 FRIEND/GUILD/SOCIAL/SYSTEM만 — MISSION/ACHIEVEMENT/INQUIRY/LEVEL은 항상 발송
- GUILD_DM은 수신자가 DM방 열람 중이면 이벤트 자체 미발행 (`DmPresenceService`, Redis TTL 60초, LUT-263)
- Consumer 재현지화는 외부 발행 타입(INQUIRY_REPLIED, admin-service 발행)만 — 내부 발행분을 재현지화하면 `{1}` 리터럴 노출 (LUT-262)
- 뱃지 동기화 3경로: 푸시 발송 시 +1 / 읽음 처리 시 badge-only silent push(iOS, content-available 없음) / 웹→앱 `badgeSync` 브릿지(Android 유일 해제 경로) (LUT-291)
- `Notification.is_pushed/pushed_at`은 푸시 스트림 적재 성공 시점에 마킹 — FCM 실제 전달 여부가 아니라 "푸시가 나갔어야 하는 알림" 판별용 (LUT-301)

## Redis Caching

캐시 이름별 TTL은 platform `infra`의 `RedisConfig`에서 정의 (역직렬화 오류 시 자동 evict 후 원본 메서드 실행):

| 캐시 이름                                                | TTL | 용도                        |
|------------------------------------------------------|-----|---------------------------|
| `todayPlayers`, `todayPlayersByCategory`, `mvpGuilds` | 2분  | 홈 화면 (칭호/레벨 변경 시 이벤트 evict) |
| `currentSeason`, `seasonMvpData`                      | 10분 | 시즌 (Admin 변경 시 즉시 삭제)     |
| `userTitleInfo`                                       | 5분  | 칭호 (`TitleService`)       |
| `userFriendIds`                                       | 10분 | 친구 ID (`FriendCacheService`) |
| `userProfile`                                         | 5분  | 프로필 (`UserProfileCacheService`) |
| `userExists`                                          | 5분  | JWT 인증 필터 유저 존재 확인        |
| `reportUnderReview`                                   | 1분  | 신고 진행 상태                  |
| `missionCategories`, `activeMissionCategories`        | 1시간 | 마스터 데이터 (Admin evict+reload) |

그 외 meta/gamification 설정 캐시(`userLevelConfigs`, 길드 레벨, 출석 보상, 업적 등)는 각 `*CacheService`의 `@Cacheable` 사용 (기본 TTL).

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
| `MissionReminderScheduler.sendReminders`                    | 매시 0,30분 (서버 기본존)       | 미션 리마인더 푸시 — 설정 요일·시각, 유저 preferred_timezone 기준 (LUT-282) |
| `TokenMaintenanceScheduler.cleanupExpiredSessions`          | `0 0 2 * * *` KST           | 만료된 OAuth 세션 정리            |
| `TokenMaintenanceScheduler.cleanupOrphanedUserSessions`     | `0 30 2 * * *` KST          | 고아 user_sessions 참조 정리     |
| `DailyMvpHistoryScheduler.saveDailyMvpHistory{Kst,Ast,Utc}` | `0 0 0 * * *` (KST/AST/UTC) | 타임존별 일간 MVP 기록             |
| `SeasonRewardScheduler.processEndedSeasonRewards`           | `0 0 3 * * *` KST           | 종료된 시즌 보상 자동 부여            |

## Saga Pattern

미션 완료 등 다단계 비즈니스 로직은 `AbstractSagaStep<Context>` 상속하여 `executeInternal` / `compensateInternal`(보상 트랜잭션) 구현. 오케스트레이터는
`missionservice/saga/MissionCompletionSaga.java` 참고 — Regular/Pinned 분기 + 10단계 step. 새 step 추가 시 `getStepName()`, 보상 로직,
멱등성 고려 필수.

## 상점 · 다이아 경제 (gamificationservice/shop, LUT-327/348/349/350)

다이아(`DiamondType`: `LEVEL_UP` / `MISSION_BOOK` / `SHOP`)로 프로필 꾸미기 아이템을 사고 장착하는 구조.

| 엔드포인트                                                             | 인증            | 용도                                    |
|-------------------------------------------------------------------|---------------|---------------------------------------|
| `GET /api/v1/shop-items`                                          | **비로그인 허용** (GET만) | 판매중 아이템 — 희귀도→가격→ID 순, 유저별 할증가·잠금 포함 |
| `POST /api/v1/shop-items/{id}/purchase`                           | 필요            | 구매 (다이아 차감 + 인벤토리 지급, 단일 트랜잭션)        |
| `GET /api/v1/user-items`                                          | 필요            | 인벤토리 조회                               |
| `POST /api/v1/user-items/{id}/equip`, `.../unequip`               | 필요            | 장착 / 해제                               |
| `GET /api/v1/diamonds/me`                                         | 필요            | 보유 다이아 잔액                             |
| `GET /api/internal/shop-items`, `/api/internal/shop-purchases`    | Internal      | Admin 아이템 관리 · 구매이력 (LUT-328)         |

**가격 할증 (`io.pinkspider.global.policy.LevelRarityPolicy`)** — 자기 등급보다 높은 등급의 아이템은 등급 차이만큼 비싸다.

```
gap             = max(0, 아이템등급 − fromLevel(내레벨))     // 레벨 구간: <3 COMMON, <10 UNCOMMON, <200 RARE, <500 EPIC, <900 LEGENDARY, 그 이상 MYTHIC
effective_price = gap == 0 ? base : ceil(base × 배수[gap] / 10) × 10   // 실제 결제가
list_price      = COMMON 유저 기준가 = 최대 할증가                        // 프론트 취소선 anchor
배수[0..5]      = 1, 1.5, 2.2, 3.3, 5, 8  (정수 ×10 배열로 보관)
```

- **배수를 double로 두지 말 것** — `1000 × 2.2 == 2200.0000000000005`라 10단위 올림이 2210을 만든다. 프론트(`shop-access.ts`)와
  비트 단위로 일치시키려면 정수 연산이어야 한다.
- 프론트는 백엔드가 내려준 `effective_price`/`list_price`/`locked`를 **표시만** 한다. 결제 금액은 `ShopService.purchaseItem`이
  유저 레벨로 재계산하므로, 프론트가 같은 공식을 재구현하면 표시가와 결제가가 갈라진다.

**잠금 (LUT-349)** — 내 등급 이하는 전부 해금, 내 등급 위는 **각 섹션에서 가격이 가장 낮은 3개**(`DEFAULT_UNLOCK_COUNT`)만 해금.
섹션 = **탭(`ShopTabGroup`: `WINGS`=BASIC·FULL / `ETC`=나머지) × 희귀도**. 희귀도만으로 세면 한쪽 탭의 해금 슬롯을 다른 탭
아이템이 가져가 특정 탭의 상위 등급이 통째로 잠겨 보인다. 목록 조회와 구매 재판정은 **같은 정렬(`SHOP_ORDER`)·같은 섹션 기준**을 써야 한다.

**장착 배타 (LUT-308)** — `ShopItemType.equipConflictTypes()`. BASIC(날개)과 FULL(전신)은 몸 영역을 공유해 상호 배타, 나머지는 타입 단위.

**동시성** — 다이아 차감은 `UserDiamond` 낙관적 락(`@Version`), 지급은 `uk_user_item` 유니크 제약이 방어. `ShopService`는
`UserItemService.grantItem`(중복 insert 흡수)과 달리 **중복을 실패로 처리**해 이중 차감을 막는다.

**비로그인 열람 (LUT-350)** — `userId == null`이면 보유 아이템 없음 + 레벨 1(COMMON)로 계산한다. 화면에 보이는 값이 곧 가입 후 낼 값이라
로그인해도 가격이 오르지 않는다.

## 약관 관리 (userservice/terms, LUT-364)

`terms`(약관) → `term_versions`(버전, content) → `user_term_agreements`(유저×버전 동의). user_db 소속,
어드민은 admin-service가 `/api/internal/terms`로 패스스루한다.

- **버전 상태 머신**: 생성 = `DRAFT`(임시저장) → `POST .../versions/{id}/publish` = `PUBLISHED`(단방향, 철회 불가).
  DRAFT만 수정/삭제 가능, PUBLISHED는 불변(동의 이력의 증적). 게시된 버전이 있는 약관은 삭제 불가.
- **재동의는 게시가 트리거**: 공개 목록(`/terms/list`)과 pending 판정(`/terms/pending/{userId}`)은 PUBLISHED만
  대상이므로, 게시 순간 전 유저에게 미동의로 잡히고 웹 `PendingTermsChecker`가 동의 화면으로 보낸다.
  별도 알림/이벤트 없음.
- **최신 버전 판정은 `published_at DESC`로 통일** — 과거엔 `version::NUMERIC`(semver 입력 시 SQL 캐스팅
  에러)/`created_at`/`id` 3기준이 혼재했다. 어드민 응답의 `latest_version`만 id 최대값(DRAFT 포함, 작업 중 버전 노출용).
- 유저 동의 저장(`agreementTermsByUser`)은 미게시 버전을 거부한다 (`error.terms.version.not_published`).

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
- **비로그인 API가 401** (LUT-350): 비로그인 허용은 **필터(`SecurityConfig`의 `permitAll`) + resolver(`@CurrentUser(required = false)`)
  두 짝**이 맞아야 완성된다. `@CurrentUser`는 argument resolver라 **필터체인을 통과한 뒤에야** 동작하므로, `required = false`만
  달면 요청이 필터에서 401로 끊긴다. `permitAll`은 메서드까지 명시할 것(`HttpMethod.GET` — 구매 같은 POST는 인증 유지).
  컨트롤러 슬라이스 테스트는 이 누락을 통과시키므로 `SecurityConfigPublicEndpointTest`에 경로를 등록해 검증할 것
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

JWT 발급/만료/슬라이딩 로직의 백엔드·웹·앱별 동작과 환경별 설정값: [`docs/JWT_TOKEN_LIFECYCLE.md`](docs/JWT_TOKEN_LIFECYCLE.md)

**세션 키는 `deviceId` 기준** (LUT-336) — `deviceType`은 세션 키의 일부가 아니다. 예전에는 재발급/로그아웃/모바일 로그인이 각각 다른 방식으로
`deviceType`을 해석해(요청 본문 / `X-Device-Type` 헤더 / `"mobile"` 폴백) 같은 기기가 로그인은 `mobile`, 재발급은 `ios`로 기록되며 세션이
갈라졌다. 지금은 `DeviceTypeResolver` 한 곳에서 정규화한다 — **클라이언트가 보낸 값 우선**, 없을 때만 User-Agent 추정, 최종 폴백 `web`.
UA로 덮어쓰지 않는 이유는 RN의 `Platform.OS`가 iPad에서도 `ios`를 주는데 서버가 `ipad`로 판정하면 같은 기기 값이 호출마다 달라지기 때문이다.
알려진 값은 `web` / `ios` / `ipad` / `android` 4개이며, 레거시 `"mobile"`은 미지정으로 취급한다.

## Internal API (Admin Backend ↔ MVP)

`/api/internal/**` — VPC 내부 접근 + 공유 시크릿 헤더 인증(LUT-244). `InternalApiKeyFilter`가 `X-Internal-Api-Key` 헤더를
`app.security.internal-api.key`와 상수시간 비교 (키 미설정 시 fail-open). 도메인별 베이스 경로, 신고 처리 워크플로우(WARNING/SUSPEND/BAN) 매핑: [
`docs/INTERNAL_API.md`](docs/INTERNAL_API.md)
