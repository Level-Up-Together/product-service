# ADR-004: BFF (Backend for Frontend) API Aggregation Strategy

**Date:** 2026-01-30
**Status:** Accepted
**Deciders:** Backend Team, Frontend Team

## Context

Multi-Service Monolith 아키텍처(ADR-001)에서 프론트엔드(Web, Mobile)가 여러 서비스의 데이터를 조합하여 화면을 구성해야 합니다. 각 화면에서 필요한 데이터를 개별 API로 호출하면 다음과 같은 문제가 발생합니다.

### 개별 API 호출의 문제점

```
┌─────────────┐     GET /feeds ──────────────────▶ ┌─────────────┐
│             │     GET /rankings ───────────────▶ │             │
│  Frontend   │     GET /guilds/my ──────────────▶ │   Backend   │
│  (Mobile)   │     GET /guilds/public ──────────▶ │   Services  │
│             │     GET /notices ────────────────▶ │             │
│             │     GET /categories ─────────────▶ │             │
└─────────────┘                                    └─────────────┘
       │
       └── 6개의 순차 요청 = 높은 지연시간 + 네트워크 오버헤드
```

| 문제 | 설명 |
|------|------|
| **Chatty API** | 화면 하나에 N개의 API 호출 필요 |
| **Over-fetching** | 필요 없는 필드까지 모두 전송 |
| **Under-fetching** | 연관 데이터를 위해 추가 호출 필요 |
| **네트워크 오버헤드** | 모바일에서 특히 지연시간 증가 |
| **프론트엔드 복잡도** | 여러 API 응답 조합 로직 필요 |

### 주요 요구사항
- 화면별 최적화된 단일 API
- 프론트엔드 코드 단순화
- 네트워크 요청 최소화 (모바일 최적화)
- 병렬 데이터 조회로 성능 향상
- 화면 요구사항 변경에 유연한 대응

## Decision

**BFF (Backend for Frontend) 패턴**을 채택합니다.

화면별로 최적화된 API를 제공하는 BFF 레이어를 구성하여, 여러 서비스의 데이터를 서버 사이드에서 병렬로 조회하고 조합합니다.

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         BFF (Backend for Frontend)                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────┐                    ┌────────────────────────────────────┐  │
│  │  Frontend   │  GET /bff/home     │          BffHomeService            │  │
│  │  (Mobile)   │ ─────────────────▶ │                                    │  │
│  └─────────────┘      1 request     │  ┌──────────────────────────────┐  │  │
│                                     │  │   CompletableFuture.allOf()  │  │  │
│                                     │  │                              │  │  │
│                                     │  │  ┌─────┐ ┌─────┐ ┌─────┐    │  │  │
│                                     │  │  │Feed │ │Guild│ │Rank │    │  │  │
│                                     │  │  │Svc  │ │Svc  │ │Svc  │... │  │  │
│                                     │  │  └──┬──┘ └──┬──┘ └──┬──┘    │  │  │
│                                     │  └─────│──────│──────│─────────┘  │  │
│                                     └────────│──────│──────│────────────┘  │
│                                              │      │      │               │
│                                              ▼      ▼      ▼               │
│                                         ┌────────────────────────┐         │
│                                         │   Parallel Execution   │         │
│                                         │   (bffExecutor Pool)   │         │
│                                         └────────────────────────┘         │
│                                              │      │      │               │
│                                              ▼      ▼      ▼               │
│                                         ┌──────┐┌──────┐┌──────┐          │
│                                         │feed  ││guild ││user  │ ...      │
│                                         │ _db  ││ _db  ││ _db  │          │
│                                         └──────┘└──────┘└──────┘          │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### BFF API Endpoints

| Endpoint | Screen | Aggregated Data |
|----------|--------|-----------------|
| `GET /api/v1/bff/home` | 홈 화면 | 피드, 랭킹, MVP길드, 카테고리, 내 길드, 공개 길드, 공지, 이벤트, 시즌 |
| `GET /api/v1/bff/guild/{id}` | 길드 상세 | 길드 정보, 멤버, 게시글, 멤버 여부/역할 |
| `GET /api/v1/bff/guild/list` | 길드 목록 | 내 길드, 추천 길드, 공지사항, 활동 피드 |
| `GET /api/v1/bff/mission/today` | 오늘의 미션 | 내 미션, 실행 현황, 완료/진행중/미완료 통계 |
| `GET /api/v1/bff/search` | 통합 검색 | 피드, 미션, 사용자, 길드 (키워드 검색) |
| `GET /api/v1/bff/season/{id}` | 시즌 상세 | 시즌 정보, 플레이어 랭킹, 길드 랭킹, 내 랭킹 |

### Implementation Pattern

#### 1. BFF Service Structure
```java
@Service
@Slf4j
public class BffHomeService {

    private final ActivityFeedService activityFeedService;
    private final HomeService homeService;
    private final GuildService guildService;
    private final NoticeService noticeService;
    private final EventService eventService;
    private final SeasonRankingService seasonRankingService;
    private final Executor bffExecutor;  // 전용 스레드 풀

    public HomeDataResponse getHomeData(String userId, Long categoryId, ...) {
        // 병렬로 모든 데이터 조회
        CompletableFuture<FeedPageData> feedsFuture = CompletableFuture.supplyAsync(() ->
            activityFeedService.getPublicFeeds(userId, feedPage, feedSize),
            bffExecutor
        );

        CompletableFuture<List<TodayPlayerResponse>> rankingsFuture = CompletableFuture.supplyAsync(() ->
            homeService.getTodayPlayers(locale),
            bffExecutor
        );

        // ... 더 많은 병렬 조회

        // 모든 결과 취합
        CompletableFuture.allOf(feedsFuture, rankingsFuture, ...).join();

        return HomeDataResponse.builder()
            .feeds(feedsFuture.join())
            .rankings(rankingsFuture.join())
            // ...
            .build();
    }
}
```

#### 2. Graceful Degradation
개별 서비스 실패 시에도 전체 응답 반환

```java
CompletableFuture<List<NoticeResponse>> noticesFuture = CompletableFuture.supplyAsync(() -> {
    try {
        return noticeService.getActiveNotices();
    } catch (Exception e) {
        log.error("Failed to fetch notices", e);
        return Collections.emptyList();  // 빈 리스트로 대체
    }
}, bffExecutor);
```

#### 3. Dedicated Thread Pool
BFF 전용 스레드 풀로 병렬 처리 성능 최적화

```java
@Configuration
public class AsyncConfig {

    @Bean("bffExecutor")
    public Executor bffExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("bff-");
        executor.setRejectionPolicy(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
```

#### 4. Response DTO Design
화면에 필요한 데이터만 포함하는 응답 DTO

```java
@Builder
public record HomeDataResponse(
    FeedPageData feeds,              // 피드 (페이징)
    List<TodayPlayerResponse> rankings,  // 랭킹
    List<MvpGuildResponse> mvpGuilds,    // MVP 길드
    List<MissionCategoryResponse> categories,  // 카테고리
    List<GuildResponse> myGuilds,        // 내 길드
    GuildPageData publicGuilds,          // 공개 길드 (페이징)
    List<NoticeResponse> notices,        // 공지
    List<EventResponse> events,          // 이벤트
    SeasonResponse currentSeason,        // 현재 시즌
    List<SeasonMvpPlayer> seasonMvpPlayers,  // 시즌 MVP 플레이어
    List<SeasonMvpGuild> seasonMvpGuilds     // 시즌 MVP 길드
) {
    @Builder
    public record FeedPageData(
        List<ActivityFeedResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {}
}
```

### BFF Controller Pattern

```java
@RestController
@RequestMapping("/api/v1/bff")
@RequiredArgsConstructor
public class BffHomeController {

    private final BffHomeService bffHomeService;
    private final BffGuildService bffGuildService;
    private final BffMissionService bffMissionService;
    private final BffSearchService bffSearchService;

    /**
     * 홈 화면 데이터 조회 (BFF)
     * 9개 서비스의 데이터를 1개의 API로 제공
     */
    @GetMapping("/home")
    public ResponseEntity<ApiResult<HomeDataResponse>> getHomeData(
        @CurrentUser String userId,
        @RequestParam(required = false) Long categoryId,
        @RequestParam(defaultValue = "0") int feedPage,
        @RequestParam(defaultValue = "20") int feedSize,
        @RequestParam(defaultValue = "5") int publicGuildSize,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        HomeDataResponse response = bffHomeService.getHomeData(
            userId, categoryId, feedPage, feedSize, publicGuildSize, acceptLanguage
        );
        return ResponseEntity.ok(ApiResult.<HomeDataResponse>builder().value(response).build());
    }
}
```

### Data Flow: Home Screen Example

```
Frontend Request:
GET /api/v1/bff/home?categoryId=1&feedPage=0&feedSize=20

BffHomeService.getHomeData():
┌────────────────────────────────────────────────────────────────────┐
│                    CompletableFuture.allOf()                       │
├────────────────────────────────────────────────────────────────────┤
│  ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌────────────┐   │
│  │   feeds    │  │  rankings  │  │  mvpGuilds │  │ categories │   │
│  │  Future    │  │   Future   │  │   Future   │  │   Future   │   │
│  └─────┬──────┘  └─────┬──────┘  └─────┬──────┘  └─────┬──────┘   │
│        │               │               │               │          │
│        ▼               ▼               ▼               ▼          │
│   ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐       │
│   │ActivityFeed │ │ HomeService│ │HomeService│ │CategorySvc│       │
│   │  Service │   │         │   │         │   │         │          │
│   └─────────┘    └─────────┘   └─────────┘   └─────────┘          │
│        │               │               │               │          │
│        ▼               ▼               ▼               ▼          │
│   ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐       │
│   │ feed_db │    │ user_db │    │ guild_db│    │mission_db│       │
│   └─────────┘    └─────────┘    └─────────┘    └─────────┘        │
│                                                                    │
│  + myGuildsFuture, publicGuildsFuture, noticesFuture,             │
│    eventsFuture, seasonMvpFuture (병렬 실행)                       │
└────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
                    HomeDataResponse (Aggregated)
```

### Performance Comparison

| Metric | Individual APIs | BFF API |
|--------|-----------------|---------|
| **API Calls** | 9 requests | 1 request |
| **Network Latency** | 9 × RTT | 1 × RTT |
| **Total Time** (Sequential) | ~900ms | ~150ms |
| **Total Time** (BFF Parallel) | N/A | ~150ms |
| **Payload Size** | Over-fetching | Optimized |

### Additional Features

#### Category Filtering (Hybrid)
카테고리 ID가 지정되면 모든 데이터를 해당 카테고리로 필터링

```java
if (categoryId != null) {
    // 카테고리별 피드, 랭킹, 길드 조회
    feedPage = activityFeedService.getPublicFeedsByCategory(categoryId, userId, ...);
    rankings = homeService.getTodayPlayersByCategory(categoryId, locale);
    guilds = guildService.getPublicGuildsByCategory(userId, categoryId);
} else {
    // 전체 데이터 조회
    feedPage = activityFeedService.getPublicFeeds(userId, ...);
    rankings = homeService.getTodayPlayers(locale);
    guilds = guildService.getPublicGuilds(userId, ...);
}
```

#### Internationalization (i18n)
Accept-Language 헤더로 다국어 지원

```java
@GetMapping("/home")
public ResponseEntity<ApiResult<HomeDataResponse>> getHomeData(
    @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
) {
    // locale을 서비스로 전달하여 다국어 데이터 반환
    return bffHomeService.getHomeData(userId, categoryId, ..., acceptLanguage);
}
```

#### Background Tasks
홈 로딩을 차단하지 않는 비동기 백그라운드 작업

```java
// 업적 동기화 - 비동기로 처리
CompletableFuture.runAsync(() -> {
    try {
        achievementService.syncUserAchievements(userId);
    } catch (Exception e) {
        log.error("업적 동기화 실패: {}", e.getMessage());
    }
}, bffExecutor);
```

## Consequences

### Positive
- **단순한 프론트엔드**: 화면당 1개 API 호출
- **성능 향상**: 병렬 조회 + 네트워크 오버헤드 감소
- **최적화된 응답**: 화면에 필요한 데이터만 전송
- **중앙 집중 로직**: 데이터 조합 로직이 서버에 집중
- **유연한 대응**: 화면 변경 시 BFF만 수정
- **Graceful Degradation**: 일부 실패해도 전체 응답 가능

### Negative
- **추가 레이어**: BFF 서비스 유지보수 필요
- **버전 관리**: 프론트엔드별 다른 요구사항 관리
- **중복 코드**: 유사한 조합 로직 반복 가능
- **테스트 복잡도**: 여러 서비스 조합 테스트 필요

### Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| BFF 비대화 | 화면별 명확한 책임 분리, 공통 로직 추출 |
| 스레드 풀 고갈 | 적절한 풀 크기 설정, 모니터링 |
| 부분 실패 처리 | Graceful degradation, 빈 값 반환 |
| 응답 시간 증가 | 타임아웃 설정, 캐싱 전략 |

## Alternatives Considered

### 1. GraphQL
```graphql
query HomeScreen {
  feeds(page: 0, size: 20) { ... }
  rankings { ... }
  myGuilds { ... }
}
```
- **Pros**: 유연한 쿼리, Over-fetching 해결
- **Cons**: 학습 곡선, N+1 문제, 캐싱 복잡
- **Rejected**: MVP 단계에서 과도한 복잡도

### 2. API Gateway Aggregation
- **Pros**: 서비스 독립성 유지
- **Cons**: 복잡한 설정, 제한된 로직
- **Rejected**: 비즈니스 로직 포함 어려움

### 3. Frontend Aggregation
- **Pros**: 서버 수정 불필요
- **Cons**: 네트워크 오버헤드, 모바일 성능 저하
- **Rejected**: 모바일 최적화 요구사항 충족 불가

## Future Considerations

### Caching Strategy
자주 변경되지 않는 데이터 캐싱

```java
@Cacheable(value = "homeCategories", key = "'all'")
public List<MissionCategoryResponse> getActiveCategories() {
    return missionCategoryRepository.findByActiveTrue();
}
```

### Client-Specific BFF
플랫폼별 최적화된 BFF 분리 (필요시)

```
/api/v1/bff/web/home     → Web 최적화
/api/v1/bff/mobile/home  → Mobile 최적화 (경량 응답)
```

### Partial Response
필요한 필드만 요청하는 옵션 (필요시)

```
GET /api/v1/bff/home?fields=feeds,rankings,myGuilds
```

## References

- [BFF Pattern - Sam Newman](https://samnewman.io/patterns/architectural/bff/)
- [Backends for Frontends - Microsoft](https://docs.microsoft.com/en-us/azure/architecture/patterns/backends-for-frontends)
- [API Gateway vs BFF](https://blog.bitsrc.io/bff-pattern-backend-for-frontend-an-introduction-e4fa965128bf)

## Related ADRs

- [ADR-001: Multi-Service Monolith Architecture](./ADR-001-20260130.md)
- [ADR-002: Event-Driven Cross-Service Communication](./ADR-002-20260130.md)
- [ADR-003: Saga Pattern for Distributed Transactions](./ADR-003-20260130.md)
