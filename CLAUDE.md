# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development Commands

```bash
# Build
./gradlew clean build

# Run service + app tests (5,100+ — platform kernel/infra/saga 테스트는 platform 레포에서 별도 실행)
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

# Test coverage report (minimum INSTRUCTION 80% + BRANCH 80% — service 모듈 jacocoTestCoverageVerification 이 check/CI 에서 강제)
./gradlew test jacocoTestReport
# Report: service/build/reports/jacoco/html/index.html
```

## Architecture Overview

**Multi-Service Monolith**: Spring Boot 3.4.5, Java 21, Gradle multi-module (`service` + `app` +
`includeBuild ../level-up-together-platform`). 13개 서비스가 단일 배포 단위지만 **서비스별 별도 DB** + Saga 패턴으로 MSA 전환 준비.
(adminservice는 별도 레포 `admin-service`(Admin Backend)로 이전됨)

**Why single service module**: 서비스 간 순환 의존성(user↔guild, user↔gamification 등)으로 독립 Gradle 모듈 불가.
`sourceSets.main.java.srcDirs`로 단일 컴파일.

**Platform shared library** (`../level-up-together-platform`):

- `kernel` — ApiResult, CustomException, Base Entity, Domain Events, **Facade 인터페이스** (UserQueryFacade,
  GuildQueryFacade, GamificationQueryFacade, MissionQueryFacade)
- `infra` — RedisConfig, JpaAuditingConfig, QueryDslConfig, JwtAuthenticationFilter, RestExceptionHandler,
  CryptoConverter
- `saga` — SagaOrchestrator, AbstractSagaStep (실행 로그는 메모리에만 — `saga_db` 는 2026-10-29 삭제, ADR-003·LUT-665)

### Service Modules

| Service               | Database        | 주요 책임                                      |
|-----------------------|-----------------|--------------------------------------------|
| `userservice`         | user_db         | OAuth2, JWT, 프로필, 친구·차단, 약관, UI 환경설정       |
| `missionservice`      | mission_db      | 미션 정의/진행/Saga, 미션북, daily instance(pinned) |
| `guildservice`        | guild_db        | 길드, 멤버, 경험치, 게시판, 초대                       |
| `chatservice`         | chat_db         | 길드 채팅, DM, 읽음 상태                           |
| `metaservice`         | meta_db         | 공통 코드, 캘린더, 레벨/출석 보상·길드 레벨 설정, **어드민 서비스 설정 `app_setting`**(LUT-708) (Redis 캐시). 금칙어·콘텐츠 번역 캐시 저장소도 meta_db |
| `feedservice`         | feed_db         | 피드 (CQRS Read Model), 좋아요, 댓글              |
| `notificationservice` | notification_db | 알림 생성/조회, FCM 푸시, 실시간(WS), 디바이스 토큰      |
| `gamificationservice` | gamification_db | 칭호, 업적, 통계, 경험치, 출석, 이벤트, 시즌, **상점/인벤토리/다이아** |
| `bffservice`          | -               | BFF API 통합, 통합 검색                          |
| `noticeservice`       | -               | 공지/안내 (Admin Feign)                          |
| `supportservice`      | -               | 1:1 문의 + 신고 처리 (Admin Feign)               |
| `statsservice`        | stats_db        | **지표 집계(IR KPI)** — 유저 일별 활동(DAU 신호) 수집·적재 (LUT-598) + 일별 KPI 스냅샷·코호트 리텐션·코호트 매출 (LUT-601) |
| `towerservice`        | tower_db        | **레벨업 탑**(v1.1.5, LUT-629~) — 층(어드민 CRUD·유저 목록, 저장 규칙 7개), 입장·인스턴스 배정·자동 착석(630), 미션 연동·상태 브로드캐스트·자리 기록(632). presence·채팅·NPC·대기열은 후속 티켓 |

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
| statsservice        | `statsTransactionManager`          |
| towerservice        | `towerTransactionManager`          |

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

Facade 인터페이스는 `lut-platform-kernel`에 정의, 각 서비스에서 구현. Facade DTO는 `io.pinkspider.global.facade.dto` (27개). Entity/Enum
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
| notification-service | 14  | tower-service        | 15  |

## Testing

| Module            | Tests  | Content                                                       |
|-------------------|--------|---------------------------------------------------------------|
| `platform:kernel` | 205    | util/wrapper/aop/converter/api tests + `NotificationTypeTest`  |
| `platform:infra`  | 98     | JwtUtil/JwtAuthenticationFilter/RestExceptionHandler/Crypto/Feign 등 (resolver/profanity 테스트는 `service`) |
| `platform:saga`   | 72     | saga framework tests (persistence 엔티티 테스트 18개는 2026-10-29 삭제) |
| `service`         | 5,100  | all service unit + controller tests (2026-10-30)              |
| `app`             | 34     | `@SpringBootTest` (full context) + 벤치마크/통합 (2026-11-02, LUT-653 이벤트 전파 통합 테스트 추가) |

**커버리지 게이트** (`service/build.gradle` `jacocoTestCoverageVerification`, `check`에 연결): INSTRUCTION 80% + **BRANCH 80%**
두 룰. `jacocoExcludes`(config/dto/entity/enums/외부 의존 컴포넌트 등)가 리포트와 게이트에 동일 적용. 2026-09-30 기준 INSTRUCTION 91.8% /
BRANCH 88.0%. 분기 커버리지는 if/switch의 **모든 경로**를 요구하므로 새 조건 로직에는 양쪽 경로 테스트를 함께 작성할 것.
분기 공략 시 `jacocoTestReport.xml`의 `<line nr mb cb>`(mb=미커버 분기 수)로 정확한 라인을 찾을 수 있다. app 모듈의 75% 룰은
`check` 미연결·측정 대상 없음(무효).

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

**CORS 허용 헤더 회귀 가드** (`SecurityConfigCorsTest`, LUT-587): 같은 `security-guard` 프로필로 실제 `SecurityConfig` 만 올려
preflight(OPTIONS) 응답의 `Access-Control-Allow-Headers` 를 검증한다. 클라이언트가 붙이는 커스텀 헤더(`X-Timezone`·`X-Signup-Token`·
`X-Device-Type`·`Idempotency-Key`) 전부 + 모르는 헤더 거부(와일드카드 아님) + 401 에러 응답의 CORS 헤더까지 고정한다.
**커스텀 헤더를 추가할 때 `SecurityConfig` 허용목록과 이 테스트의 `ALLOWED_CLIENT_HEADERS` 양쪽에 등록할 것.**

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
검사 경로는 `src/**` + `*-service/src/**` + `shared-test/src/**` (LUT-532 — 이전엔 `service/src`만 검사되어 서비스 코드 90%가 빠져 있었다.
새 서비스 디렉토리는 `*-service/` 명명을 따를 것). 일괄 포맷 커밋은 `.git-blame-ignore-revs`에 등록 — `git config blame.ignoreRevsFile .git-blame-ignore-revs`.

```bash
./gradlew spotlessApply  # 적용
./gradlew spotlessCheck  # CI에서 사용 — 위반 시 빌드 실패
```

**IntelliJ 사용자 권장 설정**: `google-java-format` 플러그인 + AOSP 모드 + VM Options `--add-exports`. 또는 Code Style을 Default IDE로 두기. 상세는 [`docs/CODE_FORMATTING.md`](docs/CODE_FORMATTING.md).

**Claude는 Java 코드 수정 후 반드시 `./gradlew :모듈:spotlessApply`를 실행하여 형식을 정정한다.** 작성 시 IDE 결과와 어긋날 수 있어 마무리 단계로 spotless 적용 필수.

## 브랜치 흐름 (전 레포 공통, 2026-10-29)

대상: product-service · admin-service · level-up-together-platform · level-up-together-frontend · level-up-together-admin-frontend · LevelUpTogetherReactNative.

| 작업 | 어디서 분기 | 브랜치 | 끝나면 |
|---|---|---|---|
| Jira 핫픽스 (parent LUT-123) | `main` | `hotfix/LUT-###` | `main` 에 머지 → 운영 배포 → 진행 중인 `epic/vX.Y.Z` 에도 머지 |
| 그 외 작업 | 진행 중인 `epic/vX.Y.Z` | `feature/LUT-###` | `epic/vX.Y.Z` 에 머지 |
| 릴리스 | — | `epic/vX.Y.Z` | `main` 에 머지 → 운영 배포 |

- 새 릴리스는 `main` 에서 `epic/vX.Y.Z` 를 분기하고, 첫 커밋으로 버전을 맞춘다(`chore: [LUT-에픽] vX.Y.Z 버전 정렬`).
- 핫픽스를 epic 에도 머지하는 이유: 빠뜨리면 다음 릴리스(epic → `main`)에서 그 수정이 사라지거나 충돌한다.
- 이 레포: `develop`·`epic/**` push → 개발 서버 배포(`gradle-dev.yml`), `main` push → 운영 롤링 배포(`gradle-prod.yml`). 운영 배포가 필요 없는 `main` 커밋(문서 등)은 메시지에 `[skip ci]`.
  platform 을 함께 바꿨으면 platform 의 같은 epic 을 먼저 push 한다(`X.Y.Z-SNAPSHOT` 발행 → 이 레포 dev 빌드가 받는다).
  platform 핫픽스는 `X.Y.Z.N`(네 번째 자리, LUT-699)으로 발행된다 — 이 레포 핫픽스 브랜치에서 `gradle.properties` 의
  `lutPlatformReleaseVersion=X.Y.Z.N` 으로 받는다(운영 빌드가 그 정식 버전을 찾는다). epic 에 머지할 때는 epic 값을 유지한다.

## 작업 완료 시 규칙

- 작업이 끝나면 반드시 프로젝트별 커밋 메시지를 생성 (커밋은 직접 실행)
- 커밋 메시지: `type: 설명` (feat/fix/refactor/docs/test/chore), JIRA 티켓 번호 포함 시 `[QA-123]` 형식, 50자 이내, "with claude" 없음
- 한글로 커밋 메시지 작성
- 새로 작성된 코드에 대한 테스트 코드 병행 작성 필수
- 필요시 CLAUDE.md, README.md 업데이트

## 배포 순서 · API 하위호환 (앱 스토어 버전 정합) — 필수

RN 앱은 스토어 심사·단계적 배포를 거치므로 **구버전 앱이 새 백엔드와 몇 주 공존**한다. 심사에 올라간 앱 버전과 백엔드 API 가 어긋나면 되돌릴 방법이
없다(앱은 즉시 롤백 불가). 그래서:

1. **배포 순서는 항상 백엔드(product/admin) → 웹 → RN 심사 제출.** 앱이 의존하는 API 변경은 앱이 심사에 올라가기 전에 운영에 먼저 나가 있어야 한다.
   SQL 은 그보다 앞서(JPA validate), platform 은 그보다 앞서(SNAPSHOT publish).
2. **백엔드는 항상 하위호환을 유지한다** — 새 앱이 올라가도, 아직 업데이트 안 한 구앱이 남아 있어도 이슈가 없어야 한다.
   - 응답 필드·enum 값은 **추가만** 한다. 삭제·이름 변경·의미 변경 금지. 새 필드는 구앱이 무시해도 되게 optional 로.
   - 요청 필드는 구앱이 보내던 형식을 계속 받는다. 새 필드는 optional + 기본값. 필수화는 `app_version.min_version` 강제 업데이트가 끝난 뒤에만.
   - 엔드포인트 제거·경로 변경은 하지 않는다. 대체 API 를 추가하고 구 API 는 유지(필요하면 내부에서 새 로직으로 위임).
   - 공개 enum 에 값을 추가할 때는 구앱 파서가 모르는 값을 받아도 죽지 않는지(프론트 폴백) 함께 확인한다.
   - 예외는 `/api/internal/**`(admin-service 와 같은 트레인으로 배포) — 그래도 같은 릴리스에서 양쪽을 함께 바꾼다.
3. 호환을 깨는 변경이 불가피하면 **새 버전 API 를 추가**하고 구버전은 `min_version` 상향으로 구앱이 사라진 뒤 제거한다. 변경 PR·티켓에 "구앱 영향 없음" 근거를 적는다.

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

**AFTER_COMMIT 리스너 안에서 재발행한 이벤트의 수신자는 `fallbackExecution = true` 필수** (LUT-653): 커밋 후 콜백은 트랜잭션 동기화가 해제된 뒤라
재발행 이벤트는 "활성 트랜잭션 없음"으로 버려진다. 레벨업 → `UserLevelUpProfileSyncListener` → `UserProfileChangedEvent` → `*ProfileSnapshotEventListener` 4개가 그 경우였고
미션 댓글 `user_level` 이 레벨업 뒤에도 옛 값이었다. 단위 테스트는 publisher 를 mock 해 못 잡으니 실제 트랜잭션 통합 테스트(app `UserLevelUpSnapshotSyncIntegrationTest`)로 확인한다.

## 알림/푸시 파이프라인 (Kafka 미사용)

도메인 이벤트 → `NotificationEventListener`(AFTER_COMMIT + `@Async`) → `NotificationService`:
① 카테고리 off면 전부 스킵 → ② DB 저장 (유저 preferredLocale로 **발송 시점 현지화**) → ③ Redis Pub/Sub
`notification:realtime` → WebSocket `/user/queue/notifications` 실시간 릴레이 → ④ `pushEnabled` && 방해금지 아님이면
Redis Stream `stream:app-push` → `AppPushMessageConsumer` → `FcmPushService`(FCM).

- `NotificationType`은 platform kernel에 정의 (category, messageTemplate, actionUrlPattern, dedup 여부)
- 방해금지(quiet hours)는 유저 `preferred_timezone` 기준 판정, **푸시만 억제** (DB 저장은 유지)
- 카테고리 토글은 FRIEND/GUILD/SOCIAL/SYSTEM만 — MISSION/ACHIEVEMENT/INQUIRY/LEVEL/ITEM은 항상 발송 (`NotificationType` 36종, LUT-410 아이템·LUT-489 스티펜드·LUT-508 `ADMIN_PUSH`·LUT-634 `TOWER_FLOOR_UNLOCKED` 포함)
- GUILD_DM은 수신자가 DM방 열람 중이면 이벤트 자체 미발행 (`DmPresenceService`, Redis TTL 60초, LUT-263)
- Consumer 재현지화는 외부 발행 타입(INQUIRY_REPLIED, admin-service 발행)만 — 내부 발행분을 재현지화하면 `{1}` 리터럴 노출 (LUT-262)
- 뱃지 동기화 3경로: 푸시 발송 시 +1 / 읽음 처리 시 badge-only silent push(iOS, content-available 없음) / 웹→앱 `badgeSync` 브릿지(Android 유일 해제 경로) (LUT-291)
- `Notification.is_pushed/pushed_at`은 푸시 스트림 적재 성공 시점에 마킹 — FCM 실제 전달 여부가 아니라 "푸시가 나갔어야 하는 알림" 판별용 (LUT-301)
- **어드민 푸시 캠페인** (LUT-508, `notificationservice/application/AdminPushCampaign*`): 어드민이 제목·본문·이동 링크를 직접 써서
  전체(ACTIVE 유저 전원, `UserQueryFacade.findAllActiveUserIds`) 또는 일부 유저에게 보낸다. `POST /api/internal/push-campaigns`가
  `admin_push_campaign` 이력 행(PENDING)을 커밋하면 `AdminPushCampaignDispatcher`(AFTER_COMMIT + `@Async`)가 유저별로
  `createNotification(ADMIN_PUSH)`를 호출 — **표준 파이프라인을 그대로 타므로** SYSTEM 카테고리 토글·방해금지·현지화 규칙이 적용된다
  (`ADMIN_PUSH` 템플릿은 `{0}` 하나로 어드민 원문을 그대로 노출, 언어별 번역 없음). 결과는 sent/skipped(토글 off)/failed 건수와
  COMPLETED/FAILED 상태로 이력에 남고, ALL 대상은 **발송 시점에 재산출**되어 요청 시 `target_count`와 미세하게 다를 수 있다.
  중복 트리거는 PENDING 검사로 무시(재발송 없음). 에러 코드 140101(대상 없음)/140102(활성 유저 없음)/140103(캠페인 없음).

### 장착 아이템 푸시 (LUT-516/528/529/530/531/545/573, `gamificationservice/shop/{scheduler,application}`)

유저가 **장착 중인 HEAD 아이템**이 유저 로컬 시각에 맞춰 말을 거는 푸시(`EQUIPPED_ITEM_PUSH`). 매분 cron(`ItemPushScheduler`)
→ 아이템별 **슬롯 3개**(`item_push_setting`: 아침 `before_activity_time` 08:00~11:59 / 정오 **12:00 고정** / 오후 `after_complete_time`
12:00~20:59, LUT-573) → 유저 로컬 시각이 슬롯 이후 캐치업 창 안인 후보 → `ItemPushDispatchService.trySendForUser`(유저별 짧은 트랜잭션)에서
슬롯 조건·백오프·간격·로테이션·대사 선택·선점.

**발송 규칙 (LUT-573) — QA·기획 혼동이 반복된 지점이니 먼저 읽을 것:**

| 단계 | 규칙 |
|---|---|
| 슬롯 조건 | 아침 = 전날 완료 AND 오늘 미완료(`BEFORE_ACTIVITY` 대사) / 정오 = 전날 미완료 AND 오늘 미완료 AND 백오프 발송일(`INACTIVE`) / 오후 = 오늘 완료(`AFTER_COMPLETE`). **불일치면 그 슬롯 스킵**(다른 대사로 대체 안 함) |
| 설정 | 행 없음 또는 **두 시각 모두 null → 정오 포함 전체 미발송**. 한쪽만 설정해도 됨(비면 그 슬롯만 미발송). 미션 완료는 유저 로컬 날짜 기준 |
| 하루 1건 | `item_push_send_log` `(user_id, send_date)` 유니크 — 어느 슬롯이든 발송하면 그날 나머지 스킵. 장착을 바꿔도 그날은 재발송 없음 |
| **간격** | 유저+아이템 마지막 발송일로부터 **1일**(D 발송 → D+1, LUT-680 — LUT-573 의 2일에서 환원, 매일 최대 1건). INACTIVE 발송도 기산점. 새 아이템을 장착하면 그 아이템 이력이 없어 다음 슬롯에 바로 나감. LUT-573 이전 로그(`slot` null)는 간격 판정에서 제외 |
| **로테이션** | 직전 발송 슬롯이 아침이면 **바로 다음 날** 아침은 양보(= 아침→아침 2일, LUT-680), 오후에 기회(오후 조건 미충족이면 그날 푸시 없음 — 아침 대사로 대체 안 함). 오후가 못 나가면 그다음 날 아침 재개. INACTIVE 는 무관. 판정은 로그의 `slot`(ANY 폴백이어도 발송된 슬롯) |
| **백오프** | 정오 `INACTIVE` 는 **연속 미완료 일수 k ∈ {1,3,7,14,30,60,90} 인 날만**(LUT-543 — 예전 {1,3,7,14} 는 15일 이상 미완료·완료 이력 0건 유저가 영구 무음이었다, LUT-541). k 는 `BACKOFF_MAX_DAYS(90)` 초과 시 91로 saturate → **90일 넘게 미완료면 완료 전까지 중단**(그 뒤는 어드민 푸시 캠페인 LUT-508 몫). **완료 이력이 없는 유저는 가입일부터 센다**(`UserQueryFacade.getCreatedAts`). 스케줄러는 15일 창을 모두에게, 그 창에 완료가 없는 유저만 `[오늘-91, 오늘-15)` 를 추가 조회한다(활동 유저 비용 유지) |
| 대사 선택 | 슬롯 `trigger_type` 풀 → 비면 `ANY` 풀 → **그것도 비면 조용히 스킵**. 풀 안에서 **한 번도 안 보낸 대사(최신 등록 먼저) → 가장 오래전에 보낸 대사**. 랜덤 없음. 비활성·삭제 대사는 풀에서 빠지고 이력은 유지(재활성화 시 이전 이력대로) |

- **대사 미등록 = 그 슬롯의 유저는 무음**이다(LUT-541). 아이템을 추가하면 **상태 3종 대사를 모두 채웠는지 확인할 것**.
- 스킵 사유는 `ItemPushDispatchOutcome`(SENT/SKIP_ALREADY_SENT/SKIP_STATE_MISMATCH/SKIP_BACKOFF/SKIP_INTERVAL/SKIP_ROTATION/SKIP_NO_MESSAGE/SKIP_RACE)으로
  반환되고 로그에 남는다 — 진단은 `grep "장착 아이템 푸시" product-service.log`. 백오프 로그에는 k, 간격 로그에는 마지막 발송일·슬롯이 찍힌다.
- **슬롯 캐치업** (LUT-545): 유저 로컬 시각이 슬롯 이후 `item.push.catch-up-window-minutes`(기본 30분) 안이면 후보 — 그 1분 틱을 놓쳐도
  창 안의 다음 틱이 소급 발송한다. 자정을 넘기는 소급은 없다. 틱 시각은 run 진입 시 1회 스냅샷(루프 중 재계산 금지). 창 안 재평가는 인스턴스 로컬
  메모(아이템·**슬롯**·유저·로컬날짜)로 막고, 예외로 끝난 유저만 다음 틱에 재시도된다. `spring.task.scheduling.pool.size=4`(base yml).
- 어드민 내부 API `GET/PUT/DELETE /api/internal/shop-items/{itemId}/push-setting` — `{before_activity_time, after_complete_time}`(둘 다 선택, 서버 `@Pattern` 범위 검증). admin-service 패스스루 DTO 는 LUT-574.
- 알림 토글은 `notification_preference.item_push_notifications`, 카테고리는 `ITEM_PUSH`.

## WebSocket 실시간 전파 — Redis 릴레이 필수 (LUT-263/QA-224/LUT-549)

설계 결정: `docs/adr/ADR-009`.

`WebSocketConfig`는 `enableSimpleBroker("/topic", "/queue")` — **인스턴스 로컬 메모리 브로커**다. prod는 ALB 뒤 2대(스티키 세션 없음)라
`SimpMessagingTemplate.convertAndSend*`를 직접 호출하면 **발신자가 붙은 인스턴스의 구독자에게만** 전달된다. 증상은 항상 "내 것은 보이고
상대 것만 안 보이며, 재조회하면 나온다"(LUT-547) — dev(1대)에서는 재현되지 않으니 코드로 막아야 한다.

**모든 STOMP 전송은 Redis pub/sub 퍼블리셔 → 각 인스턴스 릴레이 경로를 탄다.** 신규 토픽/큐를 추가할 때 `SimpMessagingTemplate`을
컨트롤러·서비스에 주입하지 말 것(릴레이만 가진다).

| 경로 | 퍼블리셔 → 릴레이 | Redis 채널 |
|---|---|---|
| 알림 `/user/queue/notifications` | `NotificationRealtimePublisher` → `NotificationRealtimeRelay` | `notification:realtime` |
| 탑 인스턴스 `/topic/tower/instances/{id}` | `TowerRealtimePublisher.publishToInstance` → `TowerRealtimeRelay` | `tower:instance:realtime` (LUT-632 USER_STATE·MISSION_COMPLETED, LUT-631 ENTER·LEAVE·MOVE·LEVEL, LUT-633 CHAT·REACTION) |
| DM `/user/queue/dm*` | `DmRealtimePublisher.publishToUser` → `DmRealtimeRelay` | `chat:dm:realtime` |
| 길드 채팅 `/topic/guild/{id}/chat`·`/read` | `GuildChatRealtimePublisher.publishToTopic` → `GuildChatRealtimeRelay` | `chat:guild:realtime` |

리스너 컨테이너는 `ChatRealtimeConfig`(chat 2채널)·`NotificationRealtimeConfig`·`TowerRealtimeConfig`. 퍼블리셔는 활성 트랜잭션이 있으면 afterCommit에
발행하고, 릴레이는 body를 **JSON 문자열 그대로** `convertAndSend`한다(프론트는 content-type 무관 `JSON.parse`). 브로드캐스트 DTO는
`@JsonNaming(SnakeCase)`를 붙여야 프론트와 필드가 맞는다(LUT-395). 유일한 직접 전송 허용 예외는 **발신자 본인에게 보내는 에러 에코**
(`GuildDirectMessageWebSocketController` `/queue/dm/errors`) — 발신자 세션은 반드시 현재 인스턴스에 있다.

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
| `guildLevelConfigs`, `userLevelConfigs`               | 1시간 | 레벨 설정 (LUT-534 — 무기한 캐시가 컬럼 추가 후 스테일 JSON을 돌려줘 길드 레벨 강등 사고) |

그 외 설정·조회 캐시(출석 보상, 업적, `profanityWords`, `userDetailedTitleInfo`, `appSettings`(LUT-708 — 엔티티가 아니라 파싱한 값만 캐시) 등)는 각 `*CacheService`의 `@Cacheable` 사용 — **기본 설정은 TTL 없음(무기한)**이라 evict 이벤트에 전적으로 의존한다.
**캐시되는 엔티티에 컬럼을 추가할 때는 배포 직후 해당 키를 evict할 것** (LUT-534): 옛 JSON은 역직렬화 오류 없이 새 필드만 `null`로 살아남으므로 `RedisConfig`의 "오류 시 evict" 안전망에 걸리지 않는다. `@PostConstruct warmUpCache()`류 self-invocation은 캐시 프록시를 타지 않아 기동 시 갱신 효과가 없다.
길드 레벨 재판정(`GuildExperienceService.processLevelUp`)은 **강등 금지**(포인트 단조 증가 → 레벨도 단조) — 재판정 결과가 현재 레벨보다 낮으면 warn 로그 후 유지.

## 유저 일별 활동 수집 — DAU 신호 (LUT-598, `statsservice/activity`)

IR 용 KPI(DAU/WAU/MAU·리텐션)의 원천. 예전엔 "앱을 열었다" 신호가 없었다(`last_login_at` 은 소셜 로그인 때만, 세션은 이력 없음).

- **수집**: `UserActivityInterceptor`(`StatsActivityConfig` 의 `@Bean` — `@Component` 로 두면 `@WebMvcTest` 슬라이스가 HandlerInterceptor 를 스캔해 컨트롤러
  테스트가 깨진다)가 인증된 요청마다 `UserActivityRecorder.recordAsync` → Redis `SADD active:{KST날짜} userId` + `active:{KST날짜}:{deviceType}`(TTL 40일).
  `WebMvcConfig` 가 `ObjectProvider` 로 있을 때만 등록. 경로 `/api/**`·`/jwt/reissue`·`/graphql`, 제외 `/api/internal/**`·`/api/test/**`·`/api/v1/webhooks/**`.
  principal 이 userId 문자열인 인증만 인정(anonymousUser 제외). **어떤 예외도 요청을 막지 않는다**(비동기 + try/catch, Redis 장애 구간은 DAU 결측 허용).
- **적재**: `UserActivityFlushScheduler` 00:20 KST → `UserActivityFlushService.flush(date)` 가 집합을 읽어 `user_daily_activity`(user_id×activity_date 유니크, `device_types` 정렬 쉼표) 에 upsert. 멱등. 수동: `POST /api/internal/stats/activity/flush?date=YYYY-MM-DD`.
- **설정** `app.stats.activity.enabled`(기본 true) · `excluded-user-ids`(팀원·운영자·QA·E2E 봇 — config-repository 환경별, 운영 20명 LUT-678). 이 목록은 DAU 수집(인터셉터)뿐 아니라 **KPI 전 지표**에서 뺀다(아래). `DeviceTypeResolver` 는 LUT-598 에서 `global/web` 로 이동(user-service 전용 아님).
- **DB**: `stats_db` 신설(9번째). DB 자체는 수동 생성(`CREATE DATABASE stats_db`), 테이블은 dev Flyway `db/migration/stats/` · prod 는 SQL 레포 V008 수동. 날짜 경계는 **KST**.

## KPI 일별 스냅샷 · 코호트 (LUT-601, `statsservice/kpi`)

상세 레퍼런스(지표 정의·제외 규칙·API·운영 점검): [`docs/KPI_STATS.md`](docs/KPI_STATS.md), 설계 결정: `docs/adr/ADR-008`.

매일 00:40 KST(활동 flush 00:20 뒤) `KpiSnapshotScheduler` → `KpiRebuildService.runDaily` 가 어제·그제 `daily_kpi_snapshot` 과 창이 닫힌 `cohort_retention`, 13개월 전~당월 코호트의 `cohort_revenue` 를 upsert 한다(멱등. LUT-660 — month_12 점이 닫히는 달까지. 예전엔 전월·당월만이라 오래된 코호트의 1·3·6·12개월 점이 수동 rebuild 로만 채워졌다). 소급은 `POST /api/internal/kpi/rebuild?from&to`(어제까지, 최대 400일) — 서버 기동 시 자동 실행 없음(LUT-595 원칙).

- **원천 조회는 `infrastructure/source/*SourceReader`** — 다른 서비스 Repository/Service 를 import 하지 않고 각 DB 의 `DataSource` 빈에 `NamedParameterJdbcTemplate` 로 읽기 전용 SQL 을 던진다(`KpiSourceConfig`). KPI 는 읽기 모델이라 테이블 스키마 결합을 허용하며, MSA 전환 시 이 클래스들만 이벤트/API 로 바꾼다. SQL 을 고치면 dev DB 에서 먼저 실행해 볼 것(H2 통합 테스트 없음).
- **날짜 경계 KST** (`KpiDates`): KST 날짜 → UTC 구간 `[D-1 15:00, D 15:00)`. 테스트 계정(`users.provider='test'`)은 **user_db 기반 지표**(가입·탈퇴·활성 유저·코호트 모수)에서만 빠진다. 제외 유저(`app.stats.activity.excluded-user-ids`)는 **모든 지표**에서 빠진다(LUT-678 — `kpi/domain/ExcludedUsers` 를 각 리더 SQL 에 `NOT IN (:excluded)` 로 넘긴다. 빈 목록은 센티널 `__none__` 1개, 코호트 매출은 모수에서 빠져 자동 제외. 예전엔 DAU/WAU/MAU·REQUEST 리텐션만). 목록 변경 후엔 재기동 + `kpi/rebuild` 소급.
- **지표**: dau/wau/mau = `user_daily_activity`(REQUEST, LUT-598 배포일부터) · `attendance_dau` = 출석(소급 가능) · 미션 완료 = `mission_execution`+`daily_mission_instance` COMPLETED(completed_at) · 구독 = `subscription_payment_history` PURCHASE/trial/REFUND + 체험→유료 전환 + `user_subscription` 활성/연장 없는 만료 · 매출 = **`price_source IS NOT NULL` 행만**(LUT-600, 미상 제외), 환불 음수, **구독·매출·결제 유저 전부 `test_purchase = FALSE` 만**(LUT-666 — Sandbox·라이선스 테스터 제외, `GamificationSourceReaderTest` 가 쿼리 목록을 가드), KRW 외 통화는 `fx_rate`(월, 수동) 있을 때만 `revenue_krw` 에 합산(원본은 `revenue_by_currency` JSONB).
- **코호트 리텐션** `RetentionMetric` D1/D3/D7/D14/D30(classic)·W1(1~7일)·W2_4(8~30일) × `RetentionSource` REQUEST/ATTENDANCE. 창이 닫힌(어제 이내) 지표만 저장. REQUEST 는 활동 데이터 시작일(`min activity_date`) 이전 창은 계산하지 않는다. D0 없음(LUT-597 전 출석 0·요청은 가입 호출 포함).
- **코호트 매출** 가입 월 × month_n {0,1,3,6,12}: 그 월말이 지난 점만, KRW 누적(구독+다이아). LTV = 누적 ÷ cohort_size (응답 `ltv_krw`).
- **Micrometer 게이지** (LUT-605, `kpi/metrics/KpiMetricsExporter`): 최신 스냅샷 1행을 `lut_kpi_{dau,wau,mau,attendance_dau,signups,withdrawals,active_users,mission_completions,mission_active_users,paying_users,active_subscriptions,new_subscriptions,churned_subscriptions,revenue_krw}` + `lut_kpi_snapshot_age_days`(KST 오늘−snapshot_date, 정상 1 — 2 이상이면 00:40 스케줄러 실패) + `lut_kpi_snapshot_date_epoch_seconds` 로 `/showmethemoney/prometheus` 에 노출한다. 스크레이프마다 DB 를 치지 않게 5분 캐시, 조회 실패는 NaN(스크레이프를 깨지 않음). `@Scheduled` 가 아니라 인스턴스별 독립(ShedLock 불필요). Grafana Cloud 연동은 infra 레포 `docs/grafana-cloud.md`.
- **HTTP 지연 히스토그램은 SLO 버킷만** (LUT-615, `application.yml` `management.metrics.distribution.slo.[http.server.requests]=100ms,300ms,1s,3s`): Boot 기본은 버킷 off 라 p95 를 낼 수 없고, `percentiles-histogram=true` 는 버킷 ~70개 × uri·status 조합이라 Grafana Cloud Free 시리즈 한도(1만)를 넘긴다. 경계 5개(+Inf)만 노출해 `histogram_quantile` 로 추정한다. 새 지표를 추가할 때는 시리즈 수(라벨 조합)를 먼저 세어 볼 것.
- **내부 API 파라미터 오류는 400** (LUT-616, `statsservice/api/exception/StatsRequestExceptionHandler`): `@DateTimeFormat` 변환 실패·enum 허용값 밖 → `100101`, 필수 파라미터 누락 → `100102`. 플랫폼 `RestExceptionHandler` 의 `Exception.class` 핸들러가 Spring 기본 400 보다 먼저 500 을 내므로 statsservice 패키지 한정 `HIGHEST_PRECEDENCE` 어드바이스로 가로챈다(LUT-577 미션 어드바이스와 같은 구조). `@WebMvcTest` 슬라이스에는 플랫폼 핸들러가 없어 테스트만으로는 500 이 안 보인다.
- 내부 API `GET /api/internal/kpi/daily|retention|revenue/cohorts|signups/by-channel` 는 P3-a admin Feign(`MvpKpiFeignClient`, LUT-602) 이 그대로 읽는다. `signups/by-channel?from_month&to_month` 는 KST 월 × `signup_channel`(null→`unknown`, provider≠test) 가입자로 어드민 CAC 분모 — 스냅샷이 아니라 user_db 직접 집계. 날짜 파라미터는 ISO(`@DateTimeFormat`), 월은 `yyyy-MM`; 형식 오류 400, from>to 는 바꿔서 처리.

## 가입 유입 채널 — CAC 원천 (LUT-599, `userservice/oauth/domain/policy/SignupChannelPolicy`)

가입 완료(`POST /oauth/complete-signup`) 때 `users` 에 **1회 기록, 이후 불변**: `signup_channel`·`utm_source/medium/campaign`·`signup_device_type`·`invite_code`.
배포 전 가입자는 전부 null(= 유입 미상). 채널 이름은 **서버가 정한다** — 클라이언트는 UTM 원문·초대 코드만 보낸다(요청 DTO optional 4필드, 구앱·구웹 호환).

| 우선순위 | 조건 | `signup_channel` |
|---|---|---|
| 1 | `invite_code` 있음 (길드 초대 링크 LUT-519, 웹이 `return_url` 쿠키에서 읽음) | `invite` |
| 2 | `utm_source` 있음 | utm_source 소문자 (50자) |
| 3 | 가입 기기 ios/ipad/android (앱 웹뷰) | `store_ios` / `store_android` |
| 4 | 그 외 | `organic_web` |

웹은 `UtmCapture`(Providers)가 첫 진입 URL 의 UTM 을 **first-touch 쿠키** `{prefix}utm`(JSON, 30일, 덮어쓰지 않음)으로 보관하고, 가입 완료 요청에 `getSignupAttributionFields()` 로 실어 보낸 뒤 지운다(`src/lib/marketing/utm.ts`).
CAC = 채널별 마케팅비 ÷ 채널별 가입자 — 마케팅비 입력·계산은 P3-a(어드민).

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
| `DailyMissionInstanceScheduler.generateDailyInstances`      | `0 0 0 * * *` KST           | 고정 미션 일일 인스턴스 생성 + 자정 자동완료. 대상 = **활성 미션(DRAFT/OPEN/IN_PROGRESS)의 ACCEPTED·IN_PROGRESS 참여자**(LUT-566 — 예전엔 ACCEPTED만·미션 상태 미확인이라 종료 미션에 매일 MISSED 적립, 첫 완료 뒤엔 생성 중단) |
| `MissionAutoCompleteScheduler.autoCompleteExpiredMissions`  | 5분 fixedRate                | 만료(4시간) 미션 자동 종료(baseExp 120) + 경고 알림  |
| `MissionReminderScheduler.sendReminders`                    | 매시 0,30분 (zone 미지정 → JVM 기본 UTC) | 미션 리마인더 푸시 — 설정 요일·시각, 유저 preferred_timezone 기준 (LUT-282) |
| `ItemPushScheduler.sendEquippedItemPushes`                  | `0 * * * * *` (매분, lock 50s)  | 장착 아이템 푸시 — 유저 로컬 `HH:mm` 슬롯 + 30분 캐치업 창(LUT-545), 상태·백오프 판정 (LUT-516/529, 규칙은 위 "장착 아이템 푸시" 절) |
| `TokenMaintenanceScheduler.cleanupExpiredSessions`          | `0 0 2 * * *` KST           | 만료된 OAuth 세션 정리            |
| `TokenMaintenanceScheduler.cleanupOrphanedUserSessions`     | `0 30 2 * * *` KST          | 고아 user_sessions 참조 정리     |
| `DailyMvpHistoryScheduler.saveDailyMvpHistory{Kst,Ast,Utc}` | `0 0 0 * * *` (Asia/Seoul·Asia/Riyadh·UTC) | 타임존별 일간 MVP 기록             |
| `SeasonRewardScheduler.processEndedSeasonRewards`           | `0 0 3 * * *` KST           | 종료된 시즌 보상 자동 부여            |
| `SubscriptionStipendScheduler.grantDailyStipends`           | `0 10 0 * * *` UTC          | 구독자 일일 블루다이아 1개 지급 (LUT-453, 멱등=구독ID+지급일) |
| `SubscriptionLedgerReconcileScheduler.reconcileAndroidLedgers` | `0 30 0 * * *` UTC       | 활성·최근 만료(7일) Android 구독의 원장 증분 대조 + 초회 주문 STORE 정정 (LUT-677). 백필 카운터 `source=daily` 가 0 이 아니면 RTDN 경로 이상 |
| `MissionIdempotencyKeyCleanupScheduler.cleanupExpiredKeys`  | `0 15 * * * *` KST (매시)    | 24시간 지난 미션 생성 멱등 키 삭제 (LUT-577) |
| `UserActivityFlushScheduler.flushRecentDays`                 | `0 20 0 * * *` KST          | Redis `active:{날짜}` 활동 집합 → `stats_db.user_daily_activity` 적재(그제·어제 2일, 멱등) (LUT-598) |
| `KpiSnapshotScheduler.buildDailySnapshot`                    | `0 40 0 * * *` KST          | 어제·그제 `daily_kpi_snapshot` + 최근 31일 `cohort_retention` + 13개월 전~당월 `cohort_revenue` 재계산(멱등) (LUT-601·660) |
| `TowerSeatReconcileScheduler.reconcile`                       | 5분 fixedDelay (`tower.seat.reconcile-delay-ms`) | 탑 미션 중 점유자를 실제 미션 상태와 대조해 끝난 유저 정리 — 이벤트 없는 자정 MISSED·유실 대비 (LUT-632) |
| `TowerPresenceCleanupScheduler.cleanup`                       | 1분 fixedDelay (`tower.presence.cleanup-delay-ms`) | 탑 방문객 10분 무조작 퇴장·고아 presence 삭제·끊김 유실 정리 (LUT-631) |

## DB 마이그레이션 — Flyway (LUT-595, `global/config/datasource/flyway`)

**dev·prod 모두 Flyway** (dev 2026-10-23 · prod 는 v1.1.3 운영 배포를 수동 psql 로 마친 직후 config `app.flyway.enabled=true`·`baseline-version=1.1.3.12`, 2026-10-28).
prod 첫 적용 파일은 LUT-666 핫픽스 `gamification/V1_1_3_013`(2026-10-29). `admin_db` 는 이 레포가 아니라 admin-service 자체 Flyway(LUT-676, v1.1.4 부터).
상세 레퍼런스·새 마이그레이션 체크리스트: [`docs/DB_MIGRATION.md`](docs/DB_MIGRATION.md), 설계 결정: `docs/adr/ADR-007`. Boot 자동설정은 DB 하나만 다루므로
`spring.flyway.enabled=false` 로 끄고, 10개 `XxxDataSourceConfig`(LUT-598 `stats`·LUT-629 `tower` 포함) 가 각각 `xxxFlywayMigration` 빈(`initMethod=migrate`)을 두며 `xxxEntityManagerFactory`
가 `@DependsOn` 한다 — Flyway 가 스키마를 맞춘 뒤 Hibernate `validate`(각 config 의 `jpaProperties()` 가 코드로 지정, yml `ddl-auto` 와 무관)가 돈다.

- 파일: `app/src/main/resources/db/migration/<db>/V<major>_<minor>_<patch>_<seq3>__<ticket>_<desc>.sql` (예 `V1_1_4_001__lut600_add_x.sql`).
  릴리스 번호가 앞이라 번호가 계속 커진다. `FlywayMigrationNamingTest`(app)가 규칙을 검사. **적용된 파일은 고치지 않는다**(checksum → 기동 실패).
- `app.flyway.enabled`(기본 false — 테스트·local 은 off, local 이 공유 dev DB 에 다른 브랜치 파일을 먼저 넣지 않게) · `baseline-version`(기존 DB 를
  "여기까지 적용됨" 으로 받아들이는 지점, 1.1.3 V007 = `1.1.3.7`). history 표는 DB 마다 `flyway_schema_history`.
- **Flyway 에 두지 않는 것**: 큰 데이터 보정(재산정·소급·백필) — 서버 기동 시점 자동 실행은 위험. 지금처럼 `level-up-together-sql` + 수동.
  기준은 "서버가 뜨는 데 꼭 필요한가".
- 롤링 배포 중 옛 코드와 새 스키마가 공존한다 — 컬럼 이름 변경·삭제는 "추가 → 코드 배포 → 다음 릴리스에 삭제". DB 간 순서 의존은 만들지 않는다.

## Saga Pattern

미션 완료 등 다단계 비즈니스 로직은 `AbstractSagaStep<Context>` 상속하여 `executeInternal` / `compensateInternal`(보상 트랜잭션) 구현. 오케스트레이터는
`missionservice/saga/MissionCompletionSaga.java` 참고 — Regular/Pinned 분기 + 10단계 step. 새 step 추가 시 `getStepName()`, 보상 로직,
멱등성 고려 필수. 각 step 은 자기 서비스 트랜잭션 매니저로 따로 커밋한다.
**Saga 실행 상태는 DB 에 저장하지 않는다**(오케스트레이터 메모리 로그만, `saga_db` 는 한 번도 쓰이지 않아 2026-10-29 dev·prod 삭제 — ADR-003·LUT-665).
그래서 요청 처리 중 서버가 종료되면 일부 step 만 반영된 채 남고 복구 기록이 없다 — 긴 작업·외부 호출을 필수 step 에 넣지 말 것.

## 레벨업 탑 (towerservice, v1.1.5 LUT-629~)

기획서는 Claude Docs "레벨업 탑 기획서"(2026-10-23). 10번째 DB `tower_db`(LUT-629) — dev·prod 모두 `CREATE DATABASE tower_db` 는 수동, 표는 Flyway `db/migration/tower/`.

- **층 `tower_floor`** (LUT-629): 층 번호(유일)·이름 4 locale·입장 레벨(null = 제한 없음)·비로그인 허용·배경/마스크 URL·입장 지점(배경 원본 픽셀)·BGM·인스턴스 상한(30)·공개.
  어드민 내부 API `GET/POST/PUT/DELETE /api/internal/tower/floors[/{id}]`(admin-service 패스스루는 LUT-635). **저장 규칙 7개**는 `TowerFloorRuleValidator` 가 저장 뒤 전체 목록으로 판정 — 번호 중복 · 1층 입장 레벨 금지 · 입장 레벨 있는 층 비로그인 허용 금지 · 위층 입장 레벨 역전 금지(제한 없음 = 최저) · 비로그인 허용 1층부터 연속 · 공개 층 번호 1부터 연속 · 입장 지점 마스크 안(`ImageFloorMaskChecker` 가 마스크 PNG 픽셀을 읽는다 — **SSRF 방어**: https 만, `tower.floor.allowed-image-hosts`(CDN 호스트, config-repository 환경별) 만, 사설·루프백 주소 거부, 리다이렉트 금지; `tower.floor.mask-check.enabled` 로 local 에서만 끌 수 있음). 삭제도 남는 층끼리 같은 규칙. 거절은 **400 `1501xx`**(`TowerExceptionHandler`, 플랫폼 500 보다 먼저), 없음 404 `150101`.
- **유저 목록** `GET /api/v1/tower/floors` — **비로그인 허용**(permitAll GET + `@CurrentUser(required = false)`, `SecurityConfigPublicEndpointTest` 등록). 공개 층만 번호 순, 잠금은 서버 판정(로그인 = 레벨 부족, 방문객 = 비허용 층), `default_floor_number` = 마지막 입장 층(`tower_user_state`, 입장 API LUT-630 이 갱신) 아니면 입장 가능한 가장 낮은 층. 공개 층이 없으면 빈 목록(디폴트 층 없음).
  각 층에 `population`(층 전체 인원, LUT-630) 포함.
- **입장·배정** (LUT-630, `tower_instance`·`tower_occupant`, Flyway V1_1_5_003): `POST /api/v1/tower/floors/{n}/enter`(비로그인 허용 POST, 인스턴스 지정 파라미터 없음 — 처음 진입은 무조건 자동 배정) ·
  `POST /api/v1/tower/leave`(방문객은 입장 응답의 `visitor_key` 로 본인 확인). **판정은 서버**(`TowerEntryService`): 비공개/없는 층 404 `150201` · 비로그인 비허용 층 403 `150202`(제한 없는 층도 허용이 꺼져 있으면 로그인 필요) ·
  레벨 < 입장 레벨 403 `150203`(층 번호가 아니라 **그 층에 등록된 입장 레벨**로 판정). 배정은 층 행 `SELECT … FOR UPDATE`(`findByFloorNumberForUpdate`) 안에서 `TowerInstanceAllocator` —
  직접 입장·방문객은 **정원이 남은 방 중 사람이 가장 많은 곳**(균등 분산 금지), 전부 차면 새 방(31번째 → 2번 방). 정원(`instance_capacity`)에는 직접 입장(자리 유지 중 포함)·방문객만 세고 **자동 착석자는 세지 않는다**.
  서버는 유저 의사 없이 방을 합치거나 옮기지 않는다 — 유일한 예외는 직접 입장으로 방 전체 인원(자동 착석 포함)이 상한을 넘을 때 자동 착석자를 같은 층 다른 방(없으면 새 방)으로 보내는 자리 양보. 아무도 없는 방은 바로 닫는다(행 삭제).
  **복귀**: 미션 중(`mission_active`) 유저가 같은 층에 다시 들어오면 배정·대기열 없이 원래 방(`resumed`), 자동 착석 자리가 있는 유저는 그 자리에서 직접 입장(그 방이 직접 입장자로 차 있으면 일반 배정), 다른 층으로 들어오면 자리를 옮긴다. 퇴장은 멱등이고 미션 중이면 자리를 남긴다(`seat_held`).
  **자동 착석** `TowerSeatService.onMissionStarted(userId)`(호출·설정값 "탑 자동 착석"·브로드캐스트는 LUT-632): 입장 가능한 공개 층 중 **사람이 가장 적은 층**(층 전체 인원 `byFloorFresh`, 같으면 낮은 층) → 그 층에서 **사람이 가장 적은 방**(일반 입장과 반대), 자리는 입장 지점, 입장 레벨이 부족한 층은 비어 있어도 제외.
  `onMissionEnded(userId, connected)`: 자동 착석자·연결 끊긴 유저는 퇴장, 연결된 유저는 휴식 중. 층 인원 `TowerPopulationService`(모든 종류 합, NPC 제외, 인스턴스 로컬 메모 `tower.population.cache-seconds` 기본 30초).
  LUT-701(탑 전체 상한·대기열)은 복귀 판정 뒤·새 배정 앞, LUT-705(방문객 세션·이름·빈도 제한)는 `visitor_key` 를 세션으로 확장, 좌표·연결 여부는 LUT-631(Redis).
- **미션 연동·상태 브로드캐스트** (LUT-632): mission-service 가 수행(타이머) 시작·종료에 kernel 이벤트 `MissionExecutionStartedEvent`(일반 `RegularMissionExecutionStrategy.startExecution`·고정 `DailyMissionInstanceService.startInstance`) /
  `MissionExecutionEndedEvent`(완료 = `MissionCompletionSaga.runSaga` 성공 단일 지점 `COMPLETED`, 4시간 자동 종료 = `MissionAutoCompleteScheduler` `AUTO_COMPLETED`, 진행 중 취소 skip = `SKIPPED`)를 낸다 —
  미션 자체 상태 이벤트(`MissionStateChangedEvent`)와 다르다. `TowerMissionEventListener`(AFTER_COMMIT + `fallbackExecution`) → `TowerSeatService.onMissionStarted(userId, TowerMissionInfo)`: 탑에 있으면 미션 중 + 같은 방에
  `USER_STATE{MISSION}` 브로드캐스트(**공개(PUBLIC) 미션만 미션명·카테고리, 나머지는 "미션 중"만**), 없으면 유저 설정 **"탑 자동 착석"**(`user_ui_preference.tower_auto_seat` 기본 켬 + `tower_auto_seat_notice_seen`, `UserQueryFacade.isTowerAutoSeatEnabled`)이 켜진 유저만 자동 착석.
  `onMissionEnded(userId, completed)`: 완료면 `MISSION_COMPLETED{cheer_window_seconds:10}` 를 먼저 보내고, 자동 착석자·연결 끊긴 유저(`TowerPresenceGateway` — 631 전까지 `DefaultTowerPresenceGateway` 는 전부 연결로 봄)는 퇴장, 연결된 유저는 `USER_STATE{REST}`.
  자정 일괄 MISSED 처럼 이벤트가 없는 경로는 `TowerSeatReconcileScheduler`(5분, ShedLock)가 `MissionQueryFacade.findInProgressMissions` 로 대조해 정리. 탑 밖에서 시작한 미션도 입장하면 미션 중(`TowerEntryService.enter` 가 `findInProgressMission` 조회).
  **실시간은 Redis 릴레이** `TowerRealtimePublisher`(`tower:instance:realtime`) → `TowerRealtimeRelay` → `/topic/tower/instances/{id}`(`TowerRealtimeConfig`, LUT-631 이 ENTER/LEAVE/MOVE 를 같은 토픽에 보탠다).
  `POST /api/v1/tower/vacate`(로그인) = 자리 직접 비우기(미션은 계속, 자동 착석 자리 포함). 미션 단위 자리 기록 `tower_mission_seat`(층·자동 착석·응원 수, 응원은 LUT-633 이 `addCheer`) → 완료 응답 `tower_seat{floor_number, auto_seated, cheer_count}`(`TowerQueryFacade`, 탑에 앉지 않았으면 없음).
- **presence·실시간 위치** (LUT-631, `towerservice/presence`): "누가 어느 방에 있는가" 는 DB(`tower_occupant`), **좌표·상태·연결 여부·아바타 데이터는 Redis** `tower:presence:{instanceId}`(해시, 점유자 ID → `TowerPresence` JSON, `TowerPresenceStore`) — 노드 메모리 금지(prod 2대).
  단일 창구 `TowerPresenceService`: 점유자 행 생성 → `register`(프로필 썸네일·닉네임·레벨·장착 아이템을 한 번 읽어 저장 + `ENTER`), 삭제 → `unregister`(`LEAVE`, `TowerInstanceAllocator.remove` 가 호출), 자리 양보 → `moveInstance`.
  **클라이언트 순서**: `POST .../enter` → `/topic/tower/instances/{id}` 구독 → `/app/tower/instances/{id}/join {occupant_id, visitor_key?}`(세션 ↔ 점유자 묶기, 마지막 위치 마스크 재검증 후 `MOVE` 로 복원, 밖이면 입장 지점) → `GET /api/v1/tower/instances/{id}/presence[?visitor_key]`(스냅샷, 그 방 점유자만 — 아니면 403 `150205`; 요청자와 차단 관계는 제외).
  **이동** `/app/tower/instances/{id}/move {x, y}` 는 목적지만 — 서버가 `TowerFloorMaskCache`(마스크 PNG 격자, `tower.floor.mask-cache-seconds` 600초, 어드민 수정 시 evict)로 검증하고 밖이면 `WalkableMask.snapInside`(반지름 `tower.presence.snap-radius-mask-px` 150)로 가장 가까운 안쪽으로 보정해 `MOVE` 브로드캐스트(너무 멀면 무시).
  **끊김**: `WebSocketConfig` 가 STOMP heartbeat 10초(**전역** — 채팅·DM·알림 연결도 heartbeat 없이 조용하면 닫힌다)로 감지 → `SessionDisconnectEvent` → `TowerConnectionService.disconnect`: 미션 중 로그인 유저는 자리 유지(presence `connected=false`, 퇴장 이벤트 없음 — 다른 유저에겐 연결된 것과 같음), 휴식 중 유저·방문객은 즉시 퇴장. 재접속은 같은 점유자로 join(옛 세션 끊김은 무시).
  방문객 10분 무조작(`tower.presence.visitor-idle-minutes`)·고아 presence·끊김 유실은 `TowerPresenceCleanupScheduler`(매분, ShedLock)가 정리. 레벨업(`UserLevelUpEvent`) → `TowerLevelChangeListener` → `LEVEL` 브로드캐스트(이름표). 방문객 이름은 `visitor_no`(네 자리, 입장 때 발급)를 클라이언트가 locale 로 조립.
  미결(631 완료 조건 중 사람 몫): **부하 테스트**(동시 접속별 지연·오류율, 탑 전체 상한 확정 → LUT-701 설정값)와 인스턴스별 노드 고정(Redis 릴레이로 정합성은 보장되므로 성능 결과를 보고 결정).
- **어드민 이미지 업로드·공통 설정** (LUT-635, `application/image/`·`TowerSettingService`): 층 API 는 이미지 **주소**만 받으므로 `POST /api/internal/tower/floors/images?kind=BACKGROUND|MASK`(multipart `file`, jpg·png·webp 15MB, **마스크는 PNG 만** 150112)가
  저장 후 **절대 https 주소**를 돌려준다 — prod `S3TowerImageStorageService`(키 `tower/{kind}/{uuid}.{ext}`, CDN `images.level-up-together.com` = 허용 호스트), 그 외 `LocalTowerImageStorageService`(`uploads/tower/{kind}/`, `TowerImageWebConfig` 정적 서빙, 절대 주소는
  `app.upload.tower-image.public-base-url`(config-repository dev `https://dev.level-up-together.com:8443`) 로 조립 — 비면 상대 경로가 나와 층 API 가 거절한다). 응답 `width`/`height` 는 헤더만 읽어(webp 는 null) 어드민이 짧은 변 1536px 미만 경고에 쓴다.
  층 수정·삭제로 안 쓰게 된 **우리 저장소 이미지는 커밋 뒤 삭제**(다른 층이 같은 주소를 쓰면 유지, 외부 주소 무시). 거절 150110(파일 없음·크기·확장자)·150111(저장 실패 500)·`000012`(이미지 아님, LUT-710).
  **탑 공통 설정** `tower_setting`(키-값, Flyway V1_1_5_006) — `GET/PUT /api/internal/tower/settings {max_concurrent_users}`(null = 제한 없음, 캐시 없이 읽어 저장 즉시 반영). 대기열 LUT-701 이 `TowerSettingService.getMaxConcurrentUsers()` 로 판정하며 기본값은 631 부하 테스트 결과. admin-service 패스스루·화면은 LUT-635/636.
- **채팅·리액션·모더레이션** (LUT-633, `application/chat/`·`TowerChatService`): 전송은 **REST**(거부 사유 코드를 바로 받는다 — 비로그인 허용, 방문객은 `visitor_key`), 수신은 같은 인스턴스 토픽의 `CHAT`/`REACTION` 이벤트.
  `POST /api/v1/tower/instances/{id}/chat {content, visitor_key?}` → 처리된 메시지(원문 아님) · `POST .../reactions {emoji, target_user_id?, visitor_key?}` · `GET .../chat`(최근 50건, Redis `tower:chat:history:{id}`, 로그인 요청자는 차단 유저 제외) · `GET /api/v1/tower/chat/reactions`(프리셋 6종).
  **판정 순서**(서버가 최종): 점유자 아님 403 `150301` → 미션 중 403 `150304`(리액션은 허용) → 길이(로그인 200·방문객 40) 400 `150307` → 방문객 URL·전화·메신저 400 `150308` → 제한 중 403 `150306` → 간격(로그인 1초·방문객 5초, Redis SETNX) 429 `150302` → 같은 내용 연속 400 `150303` → 금칙어.
  **금칙어**는 어드민 비속어 목록(`ProfanityValidationService.getActiveProfanityWordsWithSeverity`, 캐시 `profanityWords` — 어드민 변경 시 evict)으로 **공백·특수문자를 지운 소문자**로 비교: LOW·MEDIUM 은 그 단어만 `**` 로 가리고 전송, HIGH 는 전체 거부 400 `150305`, MEDIUM 이 60초 안 3회면 300초 제한(`tower.chat.*` 설정). 원문은 `tower_chat_log`(Flyway V1_1_5_007, 거절도 기록)에만 남고 다른 유저에게는 절대 안 나간다.
  **응원** = 완료 연출 뒤 10초 창(`TowerCheerWindowStore`, `MISSION_COMPLETED` 때 `TowerSeatService` 가 연다) 안에 `target_user_id` 로 보낸 리액션 1회(점유자당) → `addCheer`, 보상 없음. 차단 유저 숨김은 토픽이 방 공용이라 **클라이언트가 `user_id` 로**(이력 API 는 서버가 뺀다). Redis 장애 시 제한은 통과(fail-open). 운영자 뮤트·로그 보관 기간·어드민 화면은 미결. 웹 UI 는 LUT-643.
- **보상** (LUT-634): **탑 전용 경험치 없음**(어디서 시작하든 같은 EXP, 응원 보상 없음). ① **층 해제 알림**: `TowerFloorUnlockListener`(tower, `UserLevelUpEvent` AFTER_COMMIT+fallback)가 공개 층 중 **입장 레벨 기준** "이전 레벨 < 입장 레벨 ≤ 새 레벨"(제한 없는 층 제외, 층 번호와 무관)인 층을 찾아 **가장 높은 층 1건**으로 kernel `TowerFloorUnlockedEvent` 를 재발행 → `NotificationEventListener`(`fallbackExecution = true` 필수, LUT-653) → `TOWER_FLOOR_UNLOCKED`(SYSTEM, referenceId=층 번호 dedup, 링크 `/tower/{n}`, 장소명은 수신자 locale). `UserLevelUpEvent` 에 `previousLevel` 이 생겼다(3-인자 생성자는 newLevel-1).
  ② **층 첫 입장 업적·칭호**: `tower_floor_visit`(유저×층 유니크, Flyway V008)에 `ON CONFLICT DO NOTHING` 으로 넣어 1행이면 `TowerFloorFirstEnteredEvent` → gamification `AchievementEventListener` 가 `TOWER_SERVICE` 데이터 소스 업적을 재판정(`TowerServiceCheckStrategy`: `visitedFloorCount`·`highestVisitedFloor`, 값은 `TowerQueryFacade.countVisitedFloors/highestVisitedFloor`). 보상 칭호는 기존 흐름(완료 → 자동 수령 → `TitleAcquiredEvent` → 웹 `TitleAcquisitionModal`). **업적·칭호 행은 어드민 마스터 데이터**(check_logic_type `TOWER_SERVICE` + achievement `category_code SPECIAL` + title) — 시드는 SQL 레포 `queries/developing/1.1.5/lut634_tower_achievements.sql`(dev·prod 수동). 방문객은 기록하지 않는다.

## 상점 · 다이아 경제 (gamificationservice/shop, LUT-327/348/349/350/354/356/401)

다이아(`DiamondType`: `LEVEL_UP` / `MISSION_BOOK` / `SHOP` / `PINK_PURCHASE` / `SUBSCRIPTION`)로 프로필 꾸미기 아이템을 사고 장착하는 구조.
**핑크다이아** (LUT-356): 결제 구매 재화 — `user_diamond.pink_balance`로 블루(기존 `balance`)와 분리 관리.
`/diamonds/me` 응답의 `balance`는 블루+핑크 합계(하위호환), `blue_balance`/`pink_balance`로 세부 제공.
묶음상품(`diamond_bundle`)은 어드민이 등록(이름·설명 다국어/개수/이미지/스토어 상품ID).

**IAP 결제** (LUT-354): 앱 전용(웹은 '앱에서 결제' 모달). 흐름: 웹 상점 → RN 브릿지(`requestIapPurchase`)
→ react-native-iap 스토어 결제 → RN이 `POST /api/v1/diamond-bundles/{id}/purchase`로 영수증 전달
→ `IapVerificationService` 검증(iOS verifyReceipt·21007 샌드박스 재시도 / Android Play Developer API·서비스계정 JWT)
→ 멱등 지급(`diamond_bundle_purchase.store_transaction_id` 유니크 — **다른 계정이 같은 결제를 보내면 `120705`**, LUT-662) → RN `finishTransaction`(지급 성공 후에만 —
실패 시 pending 유지, 앱 재시작 시 `restorePendingPurchases`가 멱등 재처리). 가격 표시는 스토어
`localizedPrice`만 사용(국가/환율별 상이 — 서버 저장가 표시 금지). `iap.verification.enabled=false`(기본)면
검증 스킵(dev용) — **prod는 config에 활성화 + Apple/Google 자격증명 필수**.

**결제 가격 캡처 · 결제이력** (LUT-401): `diamond_bundle_purchase`에 `price_amount`/`price_currency`/`status`
(`PAID`/`REFUNDED`)/`refunded_at` 저장 — 어드민 CS/환불 대응·매출 확인용. 플랫폼별 캡처 방식이 다르다:
- **Android**: `verifyGoogle()`이 Play Developer API 응답의 `priceAmountMicros`(÷1,000,000)/`priceCurrencyCode`를 파싱.
- **iOS**: 레거시 `verifyReceipt` 응답에는 가격 필드가 없어 **Apple App Store Server API**(`AppStoreServerAPIClient.getTransactionInfo`
  → `SignedDataVerifier.verifyAndDecodeTransaction`로 JWS 검증·디코딩 → `price`(밀리유닛, ÷1000)/`currency`)를 추가 호출.
  `IapAppleProperties`(`iap.apple.*`: `key-id`/`issuer-id`/`private-key`/`bundle-id`/`app-id`)로 자격증명 주입,
  `classpath:apple/AppleRootCA-G3.cer`를 신뢰 루트로 사용. `sandbox`/`prod` 환경별로 별도 클라이언트 인스턴스를 lazy 캐싱.
  가격 캡처는 **best-effort**(모든 예외를 잡아 `IapVerificationResult.withoutPrice(...)`로 폴백) — 실패해도 결제/지급 자체는
  막지 않는다(가격 없이 저장). Prod 배포 전 App Store Connect에서 .p8 키 발급 + Key ID/Issuer ID/App Apple ID 확보 후
  `config-repository/product-service/product-service-prod.yml`의 `iap.apple.*`에 등록 필수(코드베이스 직접 커밋 금지).
- 환불 자동 감지(Apple Server Notifications V2 / Google RTDN)는 미구현 — `status`/`refunded_at` 컬럼만 확보(후속 과제).
- **iOS 영수증 없는 요청** (LUT-498): react-native-iap v16(StoreKit 2)은 `getReceiptDataIOS()`가 빈 값을 줄 수 있어 RN이
  `transaction_id`만 보낸다. `receipt`가 없으면 `verifyReceipt` 대신 **App Store Server API**(`fetchAppleTransaction`,
  prod→sandbox 폴백)로 상품 일치·미환불(`revocationDate`)을 검증한다 — 구독 검증과 같은 경로. `receipt`가 있으면 기존
  `verifyReceipt` 경로 유지. 예전엔 receipt 필수(120701)라 iOS 다이아 결제가 dev에서 항상 실패했다.
  RN 스토어 결제 대기에는 60초 타임아웃(`STORE_PURCHASE_TIMEOUT_MS`, LUT-502 — 웹 브릿지 대기보다 짧게)이 있어 무응답 시 `store_timeout`으로 회신한다.
- **동시 지급 낙관락 충돌** (LUT-504): 앱 기동 시 StoreKit이 미완료 트랜잭션 여러 건을 한꺼번에 재전달하면 같은 유저의
  `UserDiamond`(@Version) 갱신이 충돌해 500이 났고, 클라이언트가 finish를 못 해 큐에 남았다. `DiamondBundlePurchaseService`가
  `ObjectOptimisticLockingFailureException`을 최대 3회 재시도하고(트랜잭션 전체 롤백이라 구매 기록 중복 없음), RN은 고아 재전달을
  직렬 큐로 하나씩 보낸다. iOS 구독은 결제 전 활성 구독을 서버 검증해 다른 계정 소유(120802)면 스토어에 가지 않고 즉시 회신한다 —
  StoreKit은 이미 구독 중이면 이벤트 없이 시스템 알럿만 띄워 60초 행이 걸렸다. 웹은 `timeout`/`store_timeout`을 "결과 미확인"으로 안내한다.

| 엔드포인트                                                             | 인증            | 용도                                    |
|-------------------------------------------------------------------|---------------|---------------------------------------|
| `GET /api/v1/shop-items`                                          | **비로그인 허용** (GET만) | 판매중 아이템 — 희귀도→가격→ID 순, 유저별 할증가·잠금 포함 |
| `POST /api/v1/shop-items/{id}/purchase`                           | 필요            | 구매 (다이아 차감 + 인벤토리 지급, 단일 트랜잭션)        |
| `GET /api/v1/user-items`                                          | 필요            | 인벤토리 조회                               |
| `POST /api/v1/user-items/{id}/equip`, `.../unequip`               | 필요            | 장착 / 해제                               |
| `GET /api/v1/diamonds/me`                                         | 필요            | 보유 다이아 잔액 (블루/핑크 분리, LUT-356)      |
| `GET /api/v1/diamond-bundles`                                     | **비로그인 허용** (GET만) | 판매중 핑크다이아 묶음상품 — 개수 오름차순 (LUT-356) |
| `POST /api/v1/diamond-bundles/{id}/purchase`                      | 필요            | IAP 영수증 검증 + 핑크다이아 멱등 지급 (LUT-354)  |
| `GET /api/internal/shop-items`, `/api/internal/shop-purchases`    | Internal      | Admin 아이템 관리 · 구매이력 (LUT-328)         |
| `GET /api/internal/diamond-bundles`                               | Internal      | Admin 다이아 묶음상품 관리 (LUT-356)          |
| `GET /api/internal/diamond-payments`                              | Internal      | Admin 다이아 결제이력 조회 — 어드민은 `DIAMOND_PAYMENT_READ` 권한 필요 (LUT-401). `user_id` 필터 지원 (LUT-486) |
| `GET /api/internal/subscription-payments`                         | Internal      | Admin 구독 결제 이력 — 유저 상세 탭(`user_id`, LUT-486)과 결제이력 통합 페이지(닉네임·기간·플랫폼·플랜·이벤트 필터, LUT-488) 공용. `subscription_payment_history` append-only 원장(만료 엄격 연장 시에만 기록, verify·웹훅 이중 도착 자연 멱등). 가격은 iOS JWS + Android Orders API(LUT-617) |
| `POST /api/internal/subscription-payments/reconcile`              | Internal      | Android 구독 원장을 Play 주문 연번(1..latest)과 전수 대조 — 누락 갱신 백필·어긋난 행 정정, 구독 권한은 불변 (LUT-617). `user_id` 필수 |
| `POST/GET /api/internal/item-grants`, `DELETE .../{id}`           | Internal      | Admin 아이템 수동 지급/이력/회수 — 멱등(보유 시 no-op), 회수=인벤토리 삭제+이력 revoked 마킹, `ITEM_GRANTED` 푸시 (LUT-472) |

**합산 차감** (LUT-354): 아이템 구매는 블루+핑크 합산 잔액에서 **블루(무상) 우선 소진**
(`UserDiamond.spendCombined`) — 유상 재화 환불 정산 관행. 원장(`diamond_history`)에 `pink_amount`로
유상 몫을 구분 기록, `balance_after`는 총잔액(블루+핑크) 의미로 통일.

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

### 가입 보상 아이템 — 성장의 새싹 (LUT-593 → LUT-607, `experience/application/LevelRewardItemService`)

**회원가입 완료 직후**(`UserSignedUpEventListener`, 가입 트랜잭션 커밋 후 비동기 — 기본 칭호·기본 아이템과 같은 지점) **성장의 새싹**을 미착용으로 지급하고
`ITEM_GRANTED` 알림(딥링크 인벤토리)을 보낸다(LUT-607). 신규 가입 환영 모달의 확인 버튼이 인벤토리로 가므로 가입 직후 보여야 한다. 예전(LUT-593)의
"레벨 4 이하 → 5 이상" 레벨업 트리거와 `sprout-level` 설정은 제거됐다 — 클래스·설정·이벤트 이름의 `LevelReward` 는 그때 이름(플랫폼 이벤트 호환 유지).
`shop_item_id` 는 환경별(dev 6 / prod 15)이라 `app.level-reward.sprout-item-id`(config-repository)로 받고, 비어 있으면 미지급.
보유 중이면(구매·시즌 보상 포함) 지급·알림 모두 없음(`UserItemService.grantItem` null). 지급 실패는 가입을 막지 않는다(리스너가 단계별로 예외를 삼킨다).
기존 유저 보정은 SQL 레포 1.1.4 `V002`(user_db 비탈퇴 유저 → gamification_db, 이름 `'성장의 새싹'` 으로 id 해석·알림 없음) 몫.

## 구독 (gamificationservice/subscription, LUT-450~453 · 486 · 499)

상세 레퍼런스(흐름·에러 코드·config 키·운영 점검): [`docs/SUBSCRIPTION_IAP.md`](docs/SUBSCRIPTION_IAP.md).

`user_subscription`(유저당 1행) + `subscription_payment_history`(원장). 상태의 **진실은 항상 스토어 재조회**
(Android `subscriptionsv2`, iOS App Store Server API) — 웹훅 payload·클라이언트 값은 트리거일 뿐이다.

- **스토어 서버 알림** (LUT-452): `POST /api/v1/webhooks/subscriptions/{apple|google}` permitAll. Android RTDN은 **GCP Pub/Sub push**로만
  온다 — 토픽 `lut-rtdn-{dev|prod}` + push 구독 → 웹훅 URL, Play Console 수익 창출 설정에 토픽 연결. iOS는 ASC 앱 정보에 Sandbox/Production
  URL 등록. dev는 2026-09-16 등록 완료(LUT-499). prod config `iap:`(검증 on·스토어 자격증명)도 LUT-499(2026-09-16)로 추가됐다.
  ⚠ **Pub/Sub push 구독은 반드시 `--expiration-period=never` 로 만든다** (LUT-617): 기본 만료 정책은 "31일 비활성 시 삭제"이고 삭제는 알림도
  감사 로그도 없다. dev 구독이 2026-10-17 쯤 사라져 10/26 테스트 갱신 RTDN 9건이 구독 없는 토픽으로 발행돼 전부 폐기됐다. 2026-10-26 `lut-rtdn-dev-push`
  재생성(만료 없음·재시도 10s~600s). 운영 push 구독 `lut-rtdn-prod-push` 는 2026-10-28 v1.1.3 릴리스 때 같은 방식(만료 없음)으로 생성했다. 수신 여부는 `lut_rtdn_received_total`
  (`RtdnMetrics`, kind 라벨) 카운터로 본다 — 0 이 계속되면 전달 경로부터 의심.
- **Android 원장 일일 정합** (LUT-677, `SubscriptionLedgerReconcileScheduler` 00:30 UTC): 활성·최근 만료(7일) Android 구독을 한 명씩 Play 재조회 → `syncRenewals(full=false, source="daily")` 로 빠진 연번을 채우고, 초회 주문(접미사 없는 `GPA.xxxx`, verify 가 정가 LIST 로 기록)도 `orders.get` 으로 STORE 정정한다(이미 STORE·무료 체험 행은 조회 안 함). RTDN 이 끊겨도 다음 날이면 원장이 맞는다. `lut_subscription_ledger_rows_written_total{source="daily"}` 는 **0 이 정상** — 양수면 RTDN 전달 경로(Pub/Sub push 구독) 점검, Grafana 알람 "Android 원장 일일 백필".
- **Android 갱신 원장은 Play 주문 연번으로 완결한다** (LUT-617, `GoogleOrderLedgerSync`): RTDN·자가 치유·verify 는 "처리 시점의 `latestOrderId`
  1건"만 알지만 갱신 주문 ID 는 `GPA.xxxx..N` 연번이다. 만료가 늘어날 때 원장의 마지막 N 다음부터 최신 N 까지 **Orders API**(`orders.get`,
  `SubscriptionVerificationService.fetchGoogleOrder`)로 되짚어 실제 결제 시각(`createTime`)·금액(`total` → `price_source=STORE`)·이용 기간(`servicePeriodEnd`
  = expires_at)으로 채운다. 주문 조회는 best-effort(권한 동기화를 막지 않음; 최신 주문만은 조회 실패 시 RTDN `eventTimeMillis`/now 로라도 기록, 중간 주문은
  다음 호출 때 재시도). 같은 주문 ID 행은 주문 기준으로 정정(append-only 의 유일한 예외, `correctFromStoreOrder`). 수동 전수 대조는
  `POST /api/internal/subscription-payments/reconcile?user_id=` (구독 권한은 안 바꿈, Android 만 — 120803/120804).
- **스토어 API 실패는 일시/영구를 가른다** (LUT-617): `fetchGoogleSubscription`/`fetchGoogleOrder` 는 5xx·네트워크·토큰 발급 지연을
  `StoreTransientException` 으로 던지고 웹훅은 이를 잡지 않아 5xx → Pub/Sub 백오프 재시도. 4xx(잘못된 토큰·권한)·파싱 실패만 `120702`(폐기). 매핑 없는 상품(`120801`)도 웹훅에서는 폐기(200, RTDN 은 `dropped{unknown_product}`) — 잡지 않으면 500 으로 스토어가 계속 재전송한다(LUT-661). 클라이언트
  verify·내부 reconcile 은 transient 를 120702 로 감싸 응답 계약 유지. 액세스 토큰은 50분 캐시(백필이 주문을 여러 번 조회).
- **자가 치유** (LUT-499, `SubscriptionSelfHealService`): `/subscriptions/me`가 `expires_at` 경과 + `auto_renew` 상태를 보면 응답 전 스토어를
  재조회해 웹훅과 같은 경로(`SubscriptionWebhookTxService.applyGoogleState` / `applyAppleSnapshot`)로 덮어쓴다. 실패는 삼키고 DB 값 응답.
  `getMyEntitlement`는 클래스 readOnly 트랜잭션 밖(`NOT_SUPPORTED`)에서 돈다 — readOnly 안에서 쓰기를 부르면 flush 되지 않는다.
- **구독 연속성 키** (LUT-499): Android 재구독·플랜 변경은 새 `purchase_token`을 발급하고 옛 토큰을 `linkedPurchaseToken`으로 가리킨다.
  새 토큰이 행에 매칭되지 않으면 옛 토큰 행에 이어 붙이고 토큰을 교체한다(이력 분리 방지). 교차 계정 가드(120802)도 옛 토큰 소유자를 본다.
  iOS는 `original_transaction_id`가 갱신·플랜 변경에도 유지된다. 원장 거래 ID: iOS=transactionId, Android=`latestOrderId`(가격은 Google이 안 줌).
- **플랜 변경**: RN `subscribeMembership`이 활성 구매의 `currentPlanId`와 다르면 `purchaseToken`(옛 토큰) + `subscriptionProductReplacementParams`
  (업그레이드 `with-time-proration`, 다운그레이드 `deferred`)를 전달. 같은 플랜 재구매는 교체 아님.
- **소유권 정책** (LUT-507): 스토어 구독은 **한 번에 한 앱 계정**만 쓰고 주인은 **마지막으로 결제한 계정**. RN이 결제 직전
  `GET /api/v1/subscriptions/app-account-token`(= 유저 UUID, `SubscriptionAccountToken`)을 받아 iOS `appAccountToken` /
  Android `obfuscatedAccountIdAndroid`로 싣고, 서버는 거래의 토큰(iOS JWS `appAccountToken` / Google
  `externalAccountIdentifiers.obfuscatedExternalAccountId`)을 요청 유저와 대조한다. `SubscriptionGrantTxService.resolveOwnership`:
  ① 토큰 불일치 → 120802 ② 다른 계정 보유 + 그 계정이 활성·유예 → 120802(구매·복원 모두) ③ 다른 계정 보유 + 만료 + 더 늦은 만료의
  새 결제 → 옛 행에서 스토어 키를 떼고(유니크 제약, flush 선행) 결제 계정으로 **이전**. 웹훅(ASSN SUBSCRIBED/DID_RENEW/OFFER_REDEEMED,
  RTDN)도 거래 토큰이 현재 주인이 아니면 같은 upsert로 이전을 시도하고 거절(120802)되면 기존 행에 적용한다. 토큰 없는 예전 거래는
  스토어 키 소유자 기준. 웹 안내 문구: 스토어 해지가 아니라 "기존 이용 기간 종료 후 재구독".
- **결제 이력 금액 출처 `price_source`** (LUT-600): Google `subscriptionsv2` 는 금액을 주지 않아 Android 원장 `price_amount` 가 null 이었다. `SubscriptionPriceResolver` 가 기록 시 결정 — 스토어 금액 있음 → `STORE` / 무료 체험 → 0원 `TRIAL` / 아니면 `plan_list_price`(플랜×플랫폼×통화, 결제일 이전 최신 `effective_from`) 정가로 채워 `LIST` / 정가도 없으면 null(warn). 정가 변경은 행 추가(삭제 금지). 캐시 없음(건수 적음). 어드민 응답 `price_source` 필드 추가(admin DTO 는 P3-a). ⚠ Play 라이선스 테스터 결제는 실매출이 아니다 — 운영 Android 17건(2026-09-20 하루 생성)은 null 유지(백필 안 함). 테스터 결제는 아래 `test_purchase`(LUT-666)로 표시·제외한다. **환불(`REFUND`) 행 금액**은 `SubscriptionRefundAmountResolver`(LUT-663): iOS 거래 price/currency · Android voided 주문 ID 로 Orders API total → 없으면 원 결제 행 → 최신 결제 행 → 정가. 예전엔 세 환불 경로가 전부 null 을 넘겨 다른 통화 결제도 KRW 정가로 빠졌다.
- **스토어 테스트 결제 `test_purchase`** (LUT-666): `user_subscription`·`subscription_payment_history`·`diamond_bundle_purchase` 컬럼. iOS 는 JWS `environment` 가 Production 이 아니면(Sandbox=TestFlight·샌드박스 계정, Xcode), Google 구독은 subscriptionsv2 에 `testPurchase` 가 있으면, Google 다이아는 `purchaseType=0` 이면 true. 결제 단위가 아니라 **구매(스토어 키)의 성질**이라 구독 행에 두고 verify·웹훅·자가 치유·reconcile 이 최신 값으로 맞춘 뒤, 원장 기록기(`SubscriptionPaymentHistoryRecorder`·`GoogleOrderLedgerSync`)가 복사한다. 환경을 모르면 실결제로 본다. KPI 는 이 값이 true 인 행을 전부 뺀다 — 2026-10-29 운영 원장의 금액 있는 결제 19건이 전부 TestFlight(24h)·테스터(5분) 갱신이라 KPI 매출이 테스트 결제로 채워져 있었다. 기존 행 표시는 SQL 레포 `hotfix-LUT-666`(주기 2일 미만 = 테스트) + KPI rebuild.
- **verify 응답 `trial`** (LUT-500): 이번 결제 건이 무료 체험으로 시작됐는지(원장 `subscription_payment_history.trial`과 동일). `trial_used`는
  **유저 평생 이력**이라 체험 소진 후 정가 재구독에도 true — 성공 문구 판정은 반드시 `trial`로. `/me` 응답의 `trial`은 null.
- 테스트 환경 갱신 주기: Play 테스터 체험 3분·월 5분·연 30분(정상 갱신 6회 후 자동 취소) / iOS 샌드박스 1개월=5분 / TestFlight는 24시간 고정.

## 기록 통계 · 랭킹 · UI 환경설정

- **기록 통계** (LUT-454): `GET /api/v1/statistics/summary`(gamification, 누적 달성·최장 스트릭·등급 도달 이력) +
  `GET /api/v1/missions/statistics/monthly`(mission, 월간 리포트). 무료 유저는 **최근 30일 범위의 월만**, 과거 월은 구독 필요 → `050301`.
  파사드 `isSubscriptionEntitled`로 판정하는 유일한 구독 게이팅 읽기 API. 게이팅은 "조회 월 말일 ≥ 오늘−30일"이라 **매월 31일엔 전월도 잠긴다**(LUT-563).
  월간 리포트 `scheduled_count`(예정) = 기간 내 `mission_execution`(전 상태) + `daily_mission_instance` 중 **1회차(seq 1) 전 상태 + 2회차 이상은 COMPLETED만**
  (LUT-566 — 완료 직후 자동 생성되는 seq+1 PENDING이 자정에 MISSED가 되어 완료 1건당 미완료 1건이 붙던 것을 제외). 달성률·완료 수는 **예정일**
  기준, 카테고리·요일·시간 분포는 **완료 시각 + `X-Timezone`** 기준이라 자정 넘김에서 1~2건 어긋날 수 있다. 종료된 고정 미션의 잔존 MISSED는
  쿼리가 아니라 **데이터 정리(sql V009)** 로 제거 — 미션 상태로 소급 제외하면 활성이던 달의 정당한 미수행까지 빠진다.
- **랭킹** `/api/v1/rankings` 14개: 전체·`/missions`·`/streaks`·`/achievements`·`/realtime`·`/level`·`/level/**`는 **비로그인 허용**(LUT-297,
  `SecurityConfig` permitAll + `SecurityConfigPublicEndpointTest`), `/my*`·`/nearby`는 인증 필요.
- **출석 응답 `store_review_due`** (LUT-708, 2026-11-02): 체크인 응답에 "이번 연속 일수가 어드민 설정 마일스톤(meta `app_setting.store_review_milestone_days`, 기본 `7,14,30`)과 같은가" 를 서버가 판정해 싣는다. 웹은 그 값이 true 일 때만 OS 인앱 평가 팝업을 요청(LUT-609)하고, 값이 없으면(구 서버) 7·14·30 으로 폴백한다. "평가했는지" 는 OS 가 알려 주지 않으므로 목록의 각 시점에 1회씩 요청하고 끝낸다. 설정 조회 실패는 출석을 막지 않는다(false). 어드민은 `/api/admin/attendance-reward-configs/store-review-milestones`(권한 `attendance-config:*`).
- **출석은 가입 당일부터 1일차** (LUT-597): 예전 `AttendanceController` 의 "가입 첫날 출석 제외" 규칙(2025-12-25, 이유 미기재)은 폐지됐다. `isNewUserToday` 가 UTC 날짜(JVM 기본)를 비교해 차단 창이 KST 09:00~다음날 09:00 로 어긋났고, 웹은 `already_checked_in` 응답을 조용히 삼켜 유저에게 아무 안내도 없었다. IR 리텐션 D1 정의(가입 다음 날 재방문)와 맞추기 위해 제거 — **배포 전 가입 코호트의 가입 당일 출석 행은 구조적으로 0건**이라 출석 기반 D0 는 그 구간에서 측정 불가. kernel `UserQueryFacade.isNewUserToday` 는 호환 위해 남아 있다(호출처 없음).
- **스트릭 소스는 3종이고 섞으면 안 된다** (LUT-405/555/717): ① **연속 출석** = `attendance_record.consecutive_days` 체인
  (`AttendanceService.getCurrentStreak`, 유저 타임존, 오늘·어제 출석 없으면 0) — 업적 화면 "현재 n일 출석중"·공개 프로필
  `consecutive_days`(파사드 `getCurrentAttendanceStreak`) ② **활동 스트릭** = `user_stats.current_streak/max_streak`
  (출석+미션 완료 날짜 기준, `UserStats.updateStreak`) — USER_STATS 업적·스트릭 랭킹(`/rankings/streaks`는 max_streak) ③ **연속 실천** = `user_stats.practice_streak/max_practice_streak/last_practice_date`(LUT-717 — 하루 **미션 1개 이상 완료**한 날의 연속, 유저 로컬 자정·**완료 시각** 기준, 출석 무관; saga `UpdateUserStatsStep` + saga 우회 자동 종료 2곳이 `GamificationQueryFacade.recordMissionPractice(userId, completedAtUtc)` 로 적립, 조회 `getPracticeStreak` 은 마지막 실천일이 오늘·어제일 때만 현재값, `last_practice_date` 가 NULL 인 기존 유저는 첫 조회 때 `MissionQueryFacade.findMissionCompletedLocalDates` 로 재계산) — 마이페이지 `user_info.practice_streak_days`·공개 프로필 `practice_streak_days`(+`max_…`). 세 값은 서로 다르다.
- **UI 환경설정** (LUT-437): `GET/PUT /api/v1/users/me/preferences` — 기기 간 동기화되는 화면 토글(완료 미션 접기 등). 알림 설정
  (`/api/v1/notifications/preferences`)과 별개 테이블(`user_ui_preference`). **홈 피드 카테고리 필터** (LUT-548):
  `home_feed_category_ids: number[]` — **빈 배열 = 전체**, null = 변경 없음(부분 업데이트). 쉼표 구분 VARCHAR(500) 컬럼을
  `LongListConverter`로 변환하며 빈 목록은 NULL 저장. 저장 시 **존재하지 않는 카테고리 ID는 400이 아니라 조용히 제거**(클라이언트
  카테고리 캐시 30분 지연 대비), 비활성은 저장하고 클라이언트가 적용 시 걸러낸다. 카테고리 조회(meta_db) 실패 시 검증 없이 저장.
- **홈 피드 카테고리 멀티셀렉트** (LUT-548): `GET /api/v1/feeds/public`·`GET /api/v1/bff/home`에 `categoryIds`(쉼표 구분 또는 반복,
  OR 조건) 추가. 비어 있으면 전체, 있으면 구버전 단일 `categoryId`는 무시(앱 호환용 유지). Featured 피드는 선택 카테고리 순서대로
  합쳐 첫 페이지에만 붙인다. BFF 공개 길드 섹션은 카테고리가 **정확히 1개**일 때만 카테고리별 조회. **MVP(`/bff/home/mvp`)는 항상
  전체 카테고리** — 프론트가 카테고리를 넘기지 않는다. 웹은 로그인 유저의 저장값을 RSC 프리페치(`bff-home-server.ts`)가 먼저 읽어
  첫 피드를 그 카테고리로 받아와 "전체→필터" 플리커를 없앤다.

## 길드 활동 포인트 (LUT-483)

길드 **랭킹·레벨의 기준은 누적 EXP 가 아니라 활동 포인트**다 (EXP 는 표기용 잔존).
유저 1명 하루 점수 = `floor(min(하루 길드미션 EXP, 60) / 10)` (상한 6점, `guild.point.*` 설정값,
기본 10/60). 적립은 건별이 아니라 **유저×일자 누적 차분**(`GuildPointService` + `guild_member_daily_point`
uk 멱등) — 쪼개서 수행해도 몰아서 한 것과 같은 점수. "하루" 경계는 **KST**. 포인트는 단조 증가만
(회수·리셋 없음, saga 보상도 EXP 만 차감). 레벨 판정은 `guild_level_config.cumulative_point`
(자동계산 `required_point(L)=30×(L-1)`), 랭킹은 `GET /api/v1/guilds/ranking` (비로그인 허용,
포인트 DESC → lastPointAt ASC → id) — **프론트 클라이언트 재정렬 금지**.

## 유저 차단 (LUT-367)

차단은 `friendship.status = BLOCKED` 행(방향 있음: userId=차단자)으로 표현. API는 `/api/v1/friends/block/{targetId}`
(POST/DELETE) + `/blocked`(목록, 닉네임·레벨·칭호 포함). 차단 시 반대방향 친구/요청 행은 정리된다(잔존 시 차단 후에도
친구로 집계되는 엣지). 파사드: `UserQueryFacade.getBlockedUserIds`(단방향, 피드·댓글 필터) / `isBlockedBetween`(양방향, DM·알림).

- **피드/댓글**: viewer 기준 차단 유저 콘텐츠를 쿼리 레벨에서 제외 (`excludedUserIds` NOT IN — 빈 리스트 오동작
  방지용 `__none__` 센티널). 친구 기반 피드는 차단=친구해제라 필터 불필요.
- **DM**: 전송/방 생성 시 양방향 차단 검사 (`error.dm.blocked_user` — 방향 무관 동일 메시지로 차단 사실 비노출).
- **친구요청**: 기존 양방향 BLOCKED 검사로 원천 차단.
- **알림**: 상호작용성(댓글·대댓글·댓글좋아요·길드초대)만 리스너에서 양방향 차단 스킵 (DM·친구요청은 원천 차단).
- **프로필**: 내가 차단한 경우만 `friendship_status=BLOCKED`(피차단자에겐 NONE — 차단 사실 비노출),
  웹은 주간캘린더/피드 탭 숨김 + '차단한 유저입니다'. 유저 피드 API도 서버에서 빈 목록.
- **웹**: 프로필 ⋯ 바텀시트 차단하기/해제(extraActions), 설정 > 차단 목록(유저별 해제).

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
`security-guard`는 파일 없는 테스트 전용 프로필(`SecurityConfigPublicEndpointTest`가 실제 `SecurityConfig`만 올리기 위해 사용).

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
- **Feign 전용 설정 클래스에 `@Configuration` 금지** (LUT-571): `@FeignClient(configuration = X.class)`의 X는 참조만으로 클라이언트별
  자식 컨텍스트에 로드된다. `@Configuration`을 붙이면 컴포넌트 스캔으로 부모 컨텍스트에도 올라가 그 안의 `RequestInterceptor`·`Client`·
  `Retryer`가 **모든 Feign 클라이언트에 전역 적용**된다 — 어드민 패스스루의 Authorization 전달 인터셉터가 Google Translation 호출에
  섞여 prod에서 유저 JWT가 Google로 나가 401이 났다. `FeignClientConfigIsolationTest`(어노테이션)·`FeignClientConfigScopeIntegrationTest`
  (실제 컨텍스트)가 회귀를 막는다. 새 Feign 설정 클래스는 두 테스트 목록에 추가할 것. LUT-648: `AdminInternalFeignConfig` 도 `@Configuration` 이라 인증서·호스트 검증을 끈 `Client` 와 `Retryer`(3회)가 카카오·애플·구글 번역 호출에 새고 있었다 — 떼고, trust-all Client 는 `@Profile("local")` 전용(dev·prod 어드민은 정식 인증서). user-service `FeignRetryConfig`(전역 `Retryer`, 시도 1회 = 재시도 없음)는 의도된 전역 설정이다.
- **브라우저에서만 "CORS error"로 실패 (커스텀 헤더 추가 시)** (LUT-587): `SecurityConfig.corsConfigurationSource()` 의
  `setAllowedHeaders(...)` 는 와일드카드가 아닌 **명시적 허용목록**이다. 목록에 없는 커스텀 헤더를 클라이언트가 붙이면 preflight(OPTIONS)가
  그 헤더를 돌려주지 않아 **브라우저가 본 요청을 아예 보내지 않는다** — 서버 로그엔 아무것도 안 남고 "그 API 만 실패"로 보인다.
  LUT-577/578 이 `Idempotency-Key` 를 빼먹어 미션 생성이 dev 에서 전부 CORS error 였고, 같이 점검하다 `X-Device-Type`(웹 로그아웃 직접
  호출)도 누락된 것을 발견했다(실패를 try/catch 로 삼켜 증상이 안 보였다). **프론트·RN 에 커스텀 헤더를 추가하면 이 목록과
  `SecurityConfigCorsTest.ALLOWED_CLIENT_HEADERS` 에 함께 추가할 것.** 에러 응답(401/409 등)에는 CORS 필터가 인가 필터보다 앞이라
  헤더가 정상 포함된다. `maxAge=3600` 이므로 수정 배포 후에도 브라우저가 최대 1시간 옛 preflight 를 캐시한다 — 검증은 하드 리로드/시크릿 창.
- **데이터 미저장/미조회**: `@Transactional`의 트랜잭션 매니저 확인
- **Integration test 실패**: SSH 터널/외부 서비스 의존, `@ActiveProfiles("test")` 확인
- **비로그인 API가 401** (LUT-350): 비로그인 허용은 **필터(`SecurityConfig`의 `permitAll`) + resolver(`@CurrentUser(required = false)`)
  두 짝**이 맞아야 완성된다. `@CurrentUser`는 argument resolver라 **필터체인을 통과한 뒤에야** 동작하므로, `required = false`만
  달면 요청이 필터에서 401로 끊긴다. `permitAll`은 메서드까지 명시할 것(`HttpMethod.GET` — 구매 같은 POST는 인증 유지).
  컨트롤러 슬라이스 테스트는 이 누락을 통과시키므로 `SecurityConfigPublicEndpointTest`에 경로를 등록해 검증할 것
- **Race condition** (중복 키, LUT-538): "없으면 생성" insert 는 **반드시 별도 트랜잭션(`REQUIRES_NEW`) 전용 빈**에 두고,
  호출부는 `catch (DataIntegrityViolationException)` 후 **재조회**해 managed 엔티티를 얻는다
  (`UserStatsCreator` / `UserAchievementCreator` 참고).
  같은 트랜잭션에서 `saveAndFlush + catch` 로 흡수하려 하면 **제약 위반이 영속성 컨텍스트를 오염시켜**
  (`don't flush the Session after an exception occurs`) 트랜잭션이 rollback-only 가 되고, 중복을 잡아 재조회해도
  커밋 시 `UnexpectedRollbackException` → 요청 전체가 500 이 된다. 내부 빈에서 예외를 삼키지도 말 것(같은 이유).
  내부 트랜잭션이 반환한 엔티티는 detached 라 이후 `setXxx` 가 flush 되지 않으므로 **반환값을 쓰지 말고 재조회**한다.
  LUT-539 로 `AttendanceRecordCreator` · `GuildMemberDailyPointCreator` · `UserItemCreator` · `NotificationCreator`(dedup) ·
  `DailyMissionInstanceCreator`(`CreateNextPinnedInstanceStep`) 까지 전부 이 구조로 전환됐다 — 새 "없으면 생성" 지점은 같은
  `*Creator` 빈 패턴을 따를 것. **경합이 잦은 지점은 insert 를 `ON CONFLICT DO NOTHING` 네이티브 쿼리로**(LUT-719 `UserAchievementCreator`):
  catch 로 흡수해도 Hibernate `SqlExceptionHelper`·log4jdbc `jdbc.sqltiming` 이 중복 키마다 ERROR 3줄을 남겨 운영 로그를 더럽힌다(홈 BFF 병렬
  섹션이 같은 유저 업적 동기화를 동시에 돌려 하루 수백 줄). 반환 행 수(1/0)로 생성 여부를 알린다. 트레이드오프: 내부 트랜잭션이 먼저 커밋되므로 호출자가 그 뒤에 실패하면 생성된 행은 남는다(빈 행·멱등 행만 둘 것).

## Image Moderation & Storage

- **검증**: `@ModerateImage` 어노테이션 + `ImageModerationAspect` (AOP). `moderation.image.provider` 설정 (`none` / `onnx-nsfw` /
  `aws-rekognition`). 위반 시 `ImageModerationException.blocked()` → **HTTP 400** `000010 error.moderation.inappropriate_image` (LUT-714 — `ImageUploadExceptionHandler`(`HIGHEST_PRECEDENCE`) 가 플랫폼 `RestExceptionHandler` 의 500 보다 먼저 받는다; 본문 `code`·`message` 는 예전과 같아 웹·앱 변경 없음)
  `onnx-nsfw` 모델은 jar 동봉 `app/src/main/resources/models/nsfw.onnx`(Yahoo open_nsfw, NHWC, BSD-2 — 출처·SHA-256 은 그 폴더 README, LUT-651). 레이아웃은 기동 시 자동 감지,
  전처리는 256 리사이즈 → 가운데 224 자르기 → BGR 평균 차감. `app` `OnnxNsfwModelSmokeTest` 가 실제 로드·추론을 검사한다. dev 는 2026-11-02 부터 `provider: onnx-nsfw`(config), prod 는 측정 뒤 결정.
  `List<MultipartFile>`·`MultipartFile[]` 인자도 전부 검사한다(LUT-650 — 예전엔 단일 인자만 봐서 미션 인증 사진이 통과). 검수 실패(파일 못 읽음·추론 예외)는 `moderation.image.fail-open`(기본 true=통과)으로 정책을 정하고, 어느 쪽이든 error 로그 + `lut_image_moderation_fail_open_total` 카운터가 남는다(false 면 **HTTP 503** `000011 error.moderation.unavailable`, LUT-714). **이미지 형식 검증** (LUT-710): 같은 애스펙트가 검수 on/off 와 무관하게 먼저 `ImageContentValidator`(헤더 시그니처 JPEG·PNG·GIF·WebP·BMP·HEIF)로 "진짜 이미지인가" 를 보고 아니면 **400** `000012 error.image.not_image` — 확장자·Content-Type 만 믿던 저장소를 지나 랜덤 바이트 `.png` 가 저장되던 구멍. 어드민 내부 업로드(상점 아이템·다이아 묶음)는 `@ModerateImage` 가 없어 서비스에서 직접 호출. 두 응답 모두 `ImageUploadExceptionHandler`. 상세는 `docs/IMAGE_INFRA.md`
- **저장**: `@Profile("prod")`에서 S3 + CloudFront CDN, 그 외 로컬 파일시스템. 서비스별 Strategy (`S3*ImageStorageService` /
  `Local*ImageStorageService`)

- **길드 채팅·DM 이미지** (LUT-590, `chatservice/application/ChatImage*`): 업로드와 전송이 분리된 2단계 — `POST /api/v1/guilds/{id}/chat/images`
  · `POST /api/v1/guilds/{id}/dm/images/{recipientId}`(multipart 파트 `image`, `@ModerateImage`, 권한은 전송과 동일)가 URL 을 주고 클라이언트가 그 URL 을
  기존 전송(WS/REST)의 `image_url` 로 보낸다. 전송은 **업로드 API 가 발급한 URL 만** 받는다(`ChatImageService.validateImageUrl` → 스토리지 `isOwnedBy`, 위반 `error.chat.invalid_image_url`) —
  임의 외부 URL 로 추적 이미지를 박는 것 방지. 저장 `uploads/chats/{guildId}/{userId}/` / S3 `chats/`, 미션처럼 `_thumb`/`_medium` 변형 생성.
  DM 본문은 `@NotBlank` → "본문 또는 사진" `@AssertTrue` 로 완화(구앱 호환). 메시지 삭제 시 `imageUrl=null` + 커밋 후 파일 삭제.
  사진만 보낸 메시지의 푸시 미리보기는 빈 본문 대신 `notification.preview.photo`(수신자 locale).

상세는 [`docs/IMAGE_INFRA.md`](docs/IMAGE_INFRA.md) 참조.

## Feature-Specific Notes

미션(고정/실행 모드/생성 한도/자동완료), 피드(공개범위/필터/동기화), 길드(초대/자동 참가), 인증(Browse-First/Signup Token Flow) 등 도메인별 비즈니스 규칙: [
`docs/FEATURES.md`](docs/FEATURES.md)

**미션 생성 멱등성** (LUT-577): `POST /api/v1/missions` 의 `Idempotency-Key`(UUID) 헤더 — 같은 유저·같은 키 재요청은 저장된 응답(같은 id) 재생,
처리 중이면 409(`050402`), 형식 오류 400(`050401`), 헤더 없으면 기존처럼 매번 생성. 선점/해제는 REQUIRES_NEW, 생성+완료는 한 트랜잭션. 400/409 는
플랫폼 `CustomException`(항상 500)이 아니라 미션 로컬 어드바이스가 낸다. 상세는 FEATURES.md "Mission 생성 멱등성" 절.

**미션 EXP 공식은 `missionservice/domain/policy/MissionExpPolicy` 한 곳** (LUT-568): 체크 10/회(하루 10회), 타이머 **120분**까지 분당 1 + 초과 5분당 1
(240분 = 144, 산정 상한 240분 — LUT-696 에서 60분/480분 → 120분/240분, 웹 `mission-exp.ts` 상수와 같이 바꾼다), 체크 일일 마일스톤 3·5개째 +20(`GrantDailyMilestoneBonusStep`, `ExpSourceType.DAILY_MISSION_MILESTONE`). 상세·응답 필드는
FEATURES.md "Mission EXP 공식" 절. 공식을 바꿀 때 완료 Step 두 곳·엔티티 `calculateExpByDuration`·스케줄러 자동 종료 클램프가 전부 이 클래스를 참조하는지 확인할 것.

JWT 발급/만료/슬라이딩 로직의 백엔드·웹·앱별 동작과 환경별 설정값: [`docs/JWT_TOKEN_LIFECYCLE.md`](docs/JWT_TOKEN_LIFECYCLE.md)

소셜 로그인 연동 해제 정합(탈퇴 시 unlink/revoke, 카카오·Apple 수신 웹훅, 콘솔·config 등록 현황): [`docs/SOCIAL_ACCOUNT_LIFECYCLE.md`](docs/SOCIAL_ACCOUNT_LIFECYCLE.md)

**회원 탈퇴에는 유예 기간이 없다** — `MyPageService.withdrawUser` 가 즉시 `WITHDRAWN` + 익명화(soft delete, 행 보존)하고 토큰 무효화·소셜 해제·`UserWithdrawnEvent` 를 낸다. 유일한 "유예"는 **재가입 cool-down**(QA-115, `app.user.withdrawal.cool-down-days` 기본 7일 — 같은 이메일+provider 가 `030001`). 어드민 **즉시 탈퇴**(LUT-584, `POST /api/internal/users/{id}/withdraw` → `withdrawUserByAdmin`)는 같은 정리를 하되 `users.withdrawal_cooldown_exempt=true` 로 cool-down 을 면제해 바로 재가입할 수 있다(`Oauth2Service.findExistingUser` 가 판정). **완전 삭제**(DB 행 제거)는 `docs/scripts/delete_user/`(10개 DB — LUT-691 2026-11-02 현행화, LUT-629 tower_db 추가) — 결제 기록 3표(`user_subscription`·`subscription_payment_history`·`diamond_bundle_purchase`)는 지우지 않는다(유저 결정, 회계·환불 근거). 새 유저 표를 만들면 그 스크립트의 `count/`·`delete/` 에도 넣을 것.

**세션 키는 `deviceId` 기준** (LUT-336) — `deviceType`은 세션 키의 일부가 아니다. 예전에는 재발급/로그아웃/모바일 로그인이 각각 다른 방식으로
`deviceType`을 해석해(요청 본문 / `X-Device-Type` 헤더 / `"mobile"` 폴백) 같은 기기가 로그인은 `mobile`, 재발급은 `ios`로 기록되며 세션이
갈라졌다. 지금은 `DeviceTypeResolver` 한 곳에서 정규화한다 — **클라이언트가 보낸 값 우선**, 없을 때만 User-Agent 추정, 최종 폴백 `web`.
UA로 덮어쓰지 않는 이유는 RN의 `Platform.OS`가 iPad에서도 `ios`를 주는데 서버가 `ipad`로 판정하면 같은 기기 값이 호출마다 달라지기 때문이다.
알려진 값은 `web` / `ios` / `ipad` / `android` 4개이며, 레거시 `"mobile"`은 미지정으로 취급한다.
UA 추정은 웹뷰·브라우저 UA(iphone/ipad/android) 외에 **RN 네이티브 fetch 기본 UA 도 앱으로 본다**(LUT-707): `okhttp` → android, `CFNetwork`/`Darwin` → ios.
RN 네이티브 호출(푸시 토큰 등록·재발급·구독 검증)은 `X-Device-Type` 를 안 보내는데, web 으로 폴백하면 앱 유저가 매일 web 활동으로도 적립돼 DAU web 이 부풀었다(LUT-667 의 웹 서버 fetch 수정만으로는 절반).

## Internal API (Admin Backend ↔ MVP)

`/api/internal/**` — VPC 내부 접근 + 공유 시크릿 헤더 인증(LUT-244). `InternalApiKeyFilter`가 `X-Internal-Api-Key` 헤더를
`app.security.internal-api.key`와 상수시간 비교 (키 미설정 시 fail-open). **운영은 ALB 에서 한 번 더 막는다**(LUT-673, 2026-11-02): `/api/internal/*` 는 **어떤 Host 로 오든** 우리 EC2 EIP 2개(admin 이 도는 lut-ec2-2 포함)에서 온 요청만 호스트별 TG(api→product, admin-api→admin)로 가고 그 외 출발지는 고정 404(infra `alb.tf` 규칙 7·8 허용, 9 차단 — 차단에 host 조건을 두지 않는 건 Host 헤더를 바꿔 기본 액션으로 비켜 가는 걸 막기 위해서). 운영자 수동 호출(KPI 소급·활동 flush·구독 reconcile)은 SSH 로 서버에 들어가 `https://localhost:8443` + 키로 부른다(sql 레포 `kpi_rebuild_prod.sh` 방식) — 노트북에서 공개 도메인으로는 404 가 정상이다. dev(홈서버, ALB 없음)는 키 필터만. 도메인별 베이스 경로, 신고 처리 워크플로우(WARNING/SUSPEND/BAN) 매핑: [
`docs/INTERNAL_API.md`](docs/INTERNAL_API.md)

**product → admin 호출도 같은 키를 보낸다** (LUT-686, 2026-10-30): admin 의 product 전용 경로(유저 문의 `/api/v1/inquiries/**`·`/api/internal/**`·신고
등록/`check`/`check-batch`)가 `X-Internal-Api-Key` 를 요구한다(admin `ProductCallKeyFilter`, 키 = admin 의 `app.mvp.internal-api-key` = 이 레포의
`app.security.internal-api.key`). `AdminInternalApiKeyInterceptor` 를 admin 을 부르는 Feign 설정 3개(`AdminFeignConfig`·`ReportFeignConfig`·
`AdminInternalFeignConfig`)가 `@Bean` 으로 등록한다 — 키가 비면(local) 헤더를 생략한다. 예전엔 이 경로들이 admin 의 IP 목록(비면 전체 허용)에만
의존해 누구나 `X-User-Id` 만 바꿔 남의 1:1 문의를 읽고 쓸 수 있었다. **배포 순서는 product(키 전송) → admin(키 요구)**.

**액추에이터도 같은 키로 보호된다** (LUT-605): `InternalApiKeyFilter` 가 `/showmethemoney/**` 중 **`/health`(하위 포함)만 공개**하고 나머지
(`env`·`beans`·`metrics`·`prometheus`·`heapdump`·`threaddump`, base-path 자체)는 `X-Internal-Api-Key` 헤더 **또는 HTTP Basic 의 비밀번호**(사용자명 무관,
Prometheus/Grafana Alloy 스크레이프용 — 플랫폼 `JwtAuthenticationFilter` 는 `Bearer` 만 해석해 충돌 없음)로 키를 받아야 200 이다. 예전엔 SecurityConfig
`permitAll` + exposure `*` 라 prod `api.level-up-together.com/showmethemoney/env` 가 공개돼 있었다. 운영 점검은 `curl -u alloy:$KEY …/prometheus`.
ALB·배포 스크립트(`gradle-*.yml`) 헬스체크는 `/showmethemoney/health` 라 영향 없다. 키 미설정이면 기존처럼 fail-open.
**노출 자체도 줄인다** (LUT-672·675, dev 2026-10-30 · prod 는 v1.1.4 운영 배포 때): config `product-service-{env}.yml` 의 `management.endpoints.web.exposure.include=health,info,prometheus`
(공용 `actuator/*.yml` 의 `*` 를 덮어씀 — admin LUT-625 와 같은 방식)라 키가 있어도 `env`·`beans`·`heapdump` 등은 404 다. 힙 덤프는 서버에서 `jcmd <pid> GC.heap_dump`.
공용 `actuator-{env}.yml` 의 `health.show-details` 는 `never` — 키 없이 공개되는 `/health` 는 `{"status":"UP"}` 만 준다.

어드민 대시보드용 `GET /api/internal/users/statistics` 의 `total_users` 는 **탈퇴(WITHDRAWN) 제외**(LUT-603 — 예전엔 `count()` 라 익명화된
탈퇴 행까지 세었다), 탈퇴 수는 `withdrawn_users`, 가입경로 분포(`users_by_provider`)도 탈퇴 제외라 합이 `total_users` 와 맞는다.
`daily_new_users` 는 가입 시점 집계라 이후 탈퇴해도 포함된다.

베이스 경로는 코드 기준 43개 — 서비스별 전체 목록·주요 엔드포인트·도입 티켓은 [`docs/INTERNAL_API.md`](docs/INTERNAL_API.md)(2026-11-04 tower 2개 추가).
README "Internal API" 절도 같은 40개를 서비스별로 요약한다(2026-10-29).
