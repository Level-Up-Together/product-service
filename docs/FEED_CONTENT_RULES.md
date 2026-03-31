# 메인 페이지 피드 노출 규칙

## 1. 기본 필터링

| 조건        | 규칙                                           |
|-----------|----------------------------------------------|
| **공개 범위** | `PUBLIC` 피드만 노출 (FRIENDS, GUILD, PRIVATE 제외) |
| **정렬**    | `createdAt DESC` (최신순)                       |
| **페이징**   | 기본 20건                                       |

## 2. 카테고리별 조회 시 (3-tier 하이브리드)

1. **Tier 1 — 어드민 추천 피드**: `adminInternalFeignClient.getFeaturedFeedIds(categoryId)` (첫 페이지만)
2. **Tier 2 — 자동 선별**: 해당 카테고리의 최근 PUBLIC 피드
3. **중복 제거**: `addedFeedIds` Set으로 추천+자동 중복 방지

## 3. 피드 생성 트리거 (12종)

| 이벤트      | ActivityType             | 자동 공개범위 |
|----------|--------------------------|:-------:|
| 미션 완료 공유 | `MISSION_SHARED`         | PUBLIC  |
| 미션 참여    | `MISSION_JOINED`         | PUBLIC  |
| 미션 완료    | `MISSION_COMPLETED`      | PUBLIC  |
| 미션 전체 완료 | `MISSION_FULL_COMPLETED` | PUBLIC  |
| 업적 달성    | `ACHIEVEMENT_UNLOCKED`   | PUBLIC  |
| 칭호 획득    | `TITLE_ACQUIRED`         | PUBLIC  |
| 레벨업      | `LEVEL_UP`               | PUBLIC  |
| 길드 창설    | `GUILD_CREATED`          | PUBLIC  |
| 길드 가입    | `GUILD_JOINED`           | PUBLIC  |
| 길드 레벨업   | `GUILD_LEVEL_UP`         | PUBLIC  |
| 친구 추가    | `FRIEND_ADDED`           | PUBLIC  |
| 연속 출석    | `ATTENDANCE_STREAK`      | PUBLIC  |

모든 자동 생성 피드는 `PUBLIC` 고정. 유저가 직접 생성하는 피드만 공개범위 선택 가능.

## 4. 공개 범위별 노출 규칙

| 조회 위치           | PUBLIC | FRIENDS | GUILD | PRIVATE |
|-----------------|:------:|:-------:|:-----:|:-------:|
| **메인 피드**       |   O    |    X    |   X   |    X    |
| **타임라인** (나+친구) |   O    |    O    |   X   |    X    |
| **유저 프로필** (본인) |   O    |    O    |   O   |    O    |
| **유저 프로필** (친구) |   O    |    O    |   X   |    X    |
| **유저 프로필** (타인) |   O    |    X    |   X   |    X    |
| **길드 피드**       |   O    |    X    |   O   |    X    |
| **검색**          |   O    |    X    |   X   |    X    |

## 5. 제외/필터링되지 않는 것

- **신고 상태**: 신고된 피드도 숨기지 않음 — `isUnderReview` 플래그만 응답에 포함 (프론트에서 처리)
- **참여도 기반 필터**: 좋아요/댓글 수에 의한 필터링 없음
- **유저 평판 기반 필터**: 없음

## 6. 핵심 코드 위치

| 파일                                 | 역할                      |
|------------------------------------|-------------------------|
| `BffHomeService.java:112-139`      | 메인 페이지 피드 조회 로직         |
| `FeedQueryService.java:70-98`      | PUBLIC 피드 쿼리 + 24시간 윈도우 |
| `FeedProjectionEventListener.java` | 이벤트 → 피드 자동 생성          |
| `FeedCommandService.java:266-312`  | 미션 공유 피드 생성             |QA
| `ActivityFeed.java`                | 피드 엔티티 + 인덱스            |
