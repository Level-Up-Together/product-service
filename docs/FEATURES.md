# Feature-Specific Implementation Notes

도메인 기능별 구현 규칙 및 주의사항. 코드만 봐서는 알기 어려운 비즈니스 규칙과 의도를 정리.

## Pinned Mission (고정 미션) - Template-Instance 패턴

고정 미션(`isPinned=true`)은 `DailyMissionInstance` 엔티티를 사용:

- 매일 자동 생성 (스케줄러: `DailyMissionInstanceScheduler`, cron: `0 0 0 * * *` KST)
  - 대상(`findAllActivePinnedMissionParticipants`, LUT-566): **미션 status ∈ DRAFT/OPEN/IN_PROGRESS · is_pinned · 미삭제** 이고 참여자
    **ACCEPTED 또는 IN_PROGRESS**. 고정 미션 참여자는 완료해도 `IN_PROGRESS` 로 유지되므로(`UpdateParticipantProgressStep`) 두 상태가 곧
    활성 참여자다. 예전엔 ACCEPTED 만·미션 상태 미확인이라 ① 종료(COMPLETED)/취소된 고정 길드미션에 매일 MISSED 가 쌓이고(LUT-482 로 목록에선
    숨겨져 수행 불가) ② 첫 완료 뒤엔 미수행일 인스턴스가 생기지 않았다. 종료 미션의 잔존 MISSED 는 sql `V009`(1.1.1) 데이터 정리로 삭제.
  - 하루 중 수행 시작 시점에 인스턴스가 없으면 on-demand 생성(`createOrGetTodayInstance`) — 스케줄러 실패/가입 당일 대비.
- 미션 정보 스냅샷 저장 (미션 변경 시 과거 기록 보존)
- 다중 실행 지원 (시퀀스 번호로 일일 복수 완료 추적) — 완료 saga 의 `CreateNextPinnedInstanceStep` 이 같은 날 seq+1 PENDING 을 자동 생성
  (`daily_execution_limit` 도달 시 생략). 이 2회차는 자정에 MISSED 가 되므로 **월간 리포트 예정 수에서는 2회차 이상 미완료를 제외**한다
  (`countScheduledInPeriod`: seq 1 전 상태 + seq≥2 COMPLETED, LUT-566).

API 라우팅 — Strategy Pattern (`MissionExecutionStrategy` 인터페이스):

```java
// MissionExecutionService에서 Strategy 패턴으로 분기
// PinnedMissionExecutionStrategy  → DailyMissionInstanceService 위임
// RegularMissionExecutionStrategy → Saga 기반 처리
```

**Strategy 메서드** (8개): `startExecution`, `endExecution`, `cancelExecution`, `getActiveExecution`,
`shareExecutionToFeed`, `unshareExecutionFromFeed`, `updateExecutionNote`, `updateExecutionImage`

## Mission Execution Mode (수행 방식)

미션의 수행 방식을 결정하는 `MissionExecutionMode` enum:

```java
TIMED   // 시간 측정 (기본값) — 60분까지 분당 1 EXP, 초과 구간 5분당 1 (LUT-568), 4시간 초과 시 자동종료
SIMPLE  // 수행 여부 — 즉시 완료, 고정 +10 EXP (LUT-568: 5 → 10)
```

- `Mission.executionMode` 필드로 미션 생성 시 선택 (TIMED/SIMPLE)
- `MissionExecutionLifecycle.complete(boolean awardSimpleExp)`에서 SIMPLE 모드 분기 — `awardSimpleExp=false`면 EXP 0
- SIMPLE 모드는 최소 수행 시간 제한 없음, 자동완료 불필요
- **SIMPLE 일일 EXP 한도 (QA-130)**: 하루 `SIMPLE_DAILY_LIMIT(10)`회까지 EXP +10 지급, 초과 시에도 수행은 가능하지만 EXP=0 처리
  - 카운트는 일반(`MissionExecution`) + 고정(`DailyMissionInstance`) SIMPLE 완료 합산 (mission 단위가 아닌 **유저 단위**)
  - 한도 도달 여부는 `MissionExecutionService.isSimpleDailyLimitReached(userId, date)`로 조회
  - 실제 EXP 0 처리는 Saga 단계(`CompleteExecutionStep`/`CompletePinnedInstanceStep`)에서 카운트 체크 후 `lifecycle.complete(false)` 호출
  - 응답 DTO(`MissionExecutionResponse`/`DailyMissionInstanceResponse`)의 `daily_simple_exp_capped` 플래그로 프론트 안내

## Mission EXP 공식 (LUT-568) — `missionservice/domain/policy/MissionExpPolicy`

공식의 단일 출처. 출처(개인·길드·미션북) 무관, `execution_mode` 기준으로만 적용. 프론트는 서버 값을 **표시만** 한다(재구현 금지).

| 모드 | 공식 | 예 |
|---|---|---|
| 체크(SIMPLE) | 완료당 **10**, 하루 10회까지 | 체크 10개 = 100 |
| 타이머(TIMED) | **60분까지 분당 1, 초과 구간 5분당 1**(`timedExp`). 완료 기본 EXP 없음(쪼개기 유인 방지: 10분×6 = 60분×1 = 60) | 33분 = 33, 240분 = 60+36 = **96** |
| 체크 일일 마일스톤 | 당일 체크 완료 **3개째 +20, 5개째 +20**(각 1회). TIMED 완료는 집계 제외, 한도 초과로 EXP 0 인 체크 완료도 횟수에 포함 | 체크 5개 = 50 + 40 = 90, 체크 10개 = 140 |

- 목표시간 도달: 미션북(`source=SYSTEM`) = `timedExp(target) + bonusExpOnFullCompletion`, 유저 생성(USER/GUILD) = `timedExp(target)` 캡.
  미달 = `timedExp(실제 분)`. 목표 없는 레거시 타이머는 `timedExp(min(실제, 480))`. 240분 목표는 이제 240 이 아니라 96 이다.
- **마일스톤 판정은 완료 Step** (`CompleteExecutionStep`/`CompletePinnedInstanceStep`)이 일일 한도와 **같은 카운트·같은 날짜 경계**
  (`executionDate`/`instanceDate`, 일반+고정 합산)로 "이번 완료 포함 누계 == 3 또는 5" 를 검사해 `context.markDailyMilestone` 로 기록하고,
  **`GrantDailyMilestoneBonusStep`**(필수 Step, `GrantUserExperience` 직후)이 `ExpSourceType.DAILY_MISSION_MILESTONE` /
  `sourceId=마일스톤 수` / 카테고리 `기타`(출석과 동일 — 일별 합계·MVP 집계 포함)로 별도 이력을 남긴다. 실패 시 완료 전체가 보상된다
  (누계가 3·5 를 지나면 다시 줄 기회가 없다). 동시 완료 레이스는 일일 한도와 같은 수준(락 없음)으로 수용한다.
- 응답: 완료 응답에 `milestone_bonus_exp`/`milestone_count`(미도달 시 필드 생략). 월간 캘린더(`/executions/monthly`)에
  `daily_rewards: { "yyyy-MM-dd": [ {type: ATTENDANCE|DAILY_MISSION_MILESTONE, exp, milestone?} ] }` — 경험치 이력(EVENT=출석,
  DAILY_MISSION_MILESTONE)을 (날짜·출처·sourceId) 순합으로 복원(환수된 건은 합 ≤ 0 이라 제외). `daily_exp` 는 유지(구버전 폴백).
- 스케줄러 자동 종료(4시간 초과, 목표 없는 레거시) EXP 는 `autoCompleteExp(base-exp)` = `min(설정값, timedExp(240)=96)` — 설정 120 은
  96 으로 클램프된다. "정상 완료의 절반" 패널티를 유지하려면 config `mission.execution.base-exp` 를 48 로 내릴 것(코드 변경 불필요).
- 길드 EXP 는 유저 EXP 와 같은 값(체크 10)이라 길드 포인트(`floor(min(일 EXP,60)/10)`)는 체크 6개로 상한 6점. 마일스톤 보너스는 길드 EXP 에 포함하지 않는다.
- 어드민 미션북 템플릿 `target_duration_minutes` 도 유저 생성과 동일하게 `@Max(240)`.

## User Level Curve (LUT-570) — `meta_db.user_level_config`

`required_exp(L)` = 레벨 L-1 → L 비용, `cumulative_exp(L)` = 레벨 L 도달 누적(= Σ required 2..L), 레벨 1 = 0/0.
레벨업 판정(`UserExperienceService.processLevelUp`)은 **현재 레벨 + 1 행**의 `required_exp` 와 `current_exp`(현재 레벨 안에서
쌓인 값)를 비교한다. 응답의 `next_level_required_exp` 도 같은 행을 봐야 한다 — LUT-570 전엔 현재 레벨 행을 읽어 2~11 레벨이
전부 500 인 균일 곡선에선 드러나지 않던 off-by-one 이 있었다. 테이블이 비면 기본 공식 `100 + (L-1)×50`, 최대 레벨이면 null.

| L | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11+ |
|---|---|---|---|---|---|---|---|---|---|---|
| required | 50 | 150 | 200 | 300 | 400 | 500 | 600 | 800 | 1000 | 1000 (12+ 는 `cum = 1000L − 6000`) |
| cumulative | 50 | 200 | 400 | 700 | 1100 | 1600 | 2200 | 3000 | 4000 | 5000 |

- 곡선을 바꿀 때 **테이블만 바꾸면 안 된다**: 판정이 증분식이라 옛 임계값 기준 `current_exp` 를 들고 간다. `total_exp` 로
  `current_level = max{L : cum(L) ≤ total}`, `current_exp = total − cum(level)` 을 재산정하는 SQL(상향만·멱등, sql 1.1.3 V002)이 동반돼야
  하고, 적용 후 Redis `userLevelConfigs::*`(all + 레벨별 키) 를 지운다. SQL 재산정은 LEVEL_UP 푸시·피드를 타지 않고, 레벨업 다이아는
  `last_rewarded_level` 이후분을 다음 자연 레벨업 때 받으며, 레벨 달성 업적은 상태 기반이라 다음 앱 접속 때 소급 완료된다.
- 상점 등급(`LevelRarityPolicy`: <3 COMMON, <10 UNCOMMON)·레벨 랭킹은 재산정 즉시 반영. 길드 레벨 테이블은 별개.

## Mission 생성 멱등성 (LUT-577) — `missionservice/application/MissionIdempotencyService`

`POST /api/v1/missions` 는 요청 헤더 `Idempotency-Key`(UUID, 클라이언트 생성)를 받는다. 네트워크가 나쁠 때 요청은 서버에 도착해 미션이 만들어졌는데
응답만 유실되면 클라이언트 가드(LUT-293 `isSubmittingRef`)는 재시도를 막지 못한다 — 서버가 같은 시도를 알아보는 키가 필요하다.

| 상황 | 동작 |
|---|---|
| 헤더 없음 | 기존처럼 매번 생성 (구버전 앱 호환, 필수 아님) |
| UUID 형식 아님 | **400** `050401` `error.mission.idempotency_key.invalid` |
| 처음 보는 키 | `mission_idempotency_key` 에 PROCESSING 선점 → 미션 생성 + COMPLETED(응답 JSON 보관)를 한 트랜잭션으로 → **200** (상태 코드는 기존 유지) |
| 같은 유저·같은 키·완료 | 새로 만들지 않고 **저장된 응답 그대로**(같은 `id`). 본문이 없으면 `mission_id` 로 재조회 |
| 같은 키·처리 중(동시 요청) | **409** `050402` `error.mission.idempotency_key.in_progress` — 같은 키로 재시도하면 완료 응답 |
| 같은 키·다른 유저 | 유니크가 `(user_id, idempotency_key)` 라 충돌 없음, 각각 생성 |
| 선점 후 5분 넘게 PROCESSING (JVM 중단 등) | stale 로 보고 행을 지운 뒤 새 요청으로 처리 |
| 24시간 경과 | `MissionIdempotencyKeyCleanupScheduler`(매시 15분 KST)가 삭제. 삭제 전에 같은 키가 와도 그 자리에서 새 요청으로 취급 |

- 트랜잭션 경계는 `MissionIdempotencyTxService` 가 가진다. 선점(claim)·해제(release)는 **REQUIRES_NEW** — 유니크 충돌이 호출자 영속성 컨텍스트를
  오염시키지 않고(LUT-538 패턴), 선점 행이 즉시 커밋돼 다른 인스턴스의 동시 요청도 충돌을 본다. 생성이 실패하면 호출자가 선점 행을 release 해
  같은 키 재시도가 다시 생성을 시도할 수 있다.
- 400/409 는 플랫폼 `CustomException`(항상 500) 대신 `MissionIdempotencyException` + 미션 로컬 어드바이스 `MissionIdempotencyExceptionHandler`
  (user-service `JwtExceptionHandler` 와 같은 구조)가 응답한다. 본문은 다른 에러와 같은 `ApiResult`(code + 현지화 message).
- 키는 trim + 소문자로 정규화해 저장한다. 다른 생성 API(길드·댓글)로의 확장은 별도 티켓. 프론트 키 발급·전송은 [Web] 후속 티켓.

## Mission 생성 한도 (QA-130)

사용자별 PERSONAL 미션 합산 최대 30개 (`Mission.MAX_PERSONAL_MISSIONS_PER_USER`):

- **길드 미션(GUILD)은 카운트 제외 / 무제한**
- 검증: `MissionService.validateMissionCreationLimit(userId, type)` — `type != PERSONAL`이면 통과
- 카운트 쿼리: `MissionRepository.countActivePersonalByCreatorId(creatorId)` — `type=PERSONAL AND isDeleted=false`
- 초과 시: `CustomException("050104", "error.mission.creation_limit_exceeded")` (i18n ko/en/ja)
- 미션북에서 추가한 미션(`source=SYSTEM, type=PERSONAL`)도 카운트에 포함

## Feed Visibility at Execution (피드 공개범위)

피드 공개범위를 미션 생성이 아닌 **실행 완료 시** 선택:

- **완료만으로는 피드가 생성되지 않는다** (LUT-318). 피드 작성 여부는 완료 후 유저가 미션 상세 등록(기록 공유)에서
  공개범위를 직접 선택할 때 결정된다
- `MissionExecutionController.completeExecution()`의 `feedVisibility`(PUBLIC/FRIENDS/GUILD/PRIVATE)는
  "완료와 동시에 공유" 케이스용 선택 파라미터 — 웹 완료 버튼은 보내지 않으며, 미지정 시 PRIVATE 취급(공유 의사 없음 = 피드 미생성)
- `Users.preferredFeedVisibility` — **공개범위 기본 설정** (LUT-280): 마이페이지 설정 메뉴에서만 변경 (단일 소스).
  용도는 **미션 생성 폼·미션 상세 등록 폼의 프리필 값** 뿐 — 완료 시 자동 공유 의사가 아님 (LUT-318).
  완료 시 명시 선택해도 이 값을 덮어쓰지 않음 (기존 last-used 방식 폐기)
- `GET/PUT /api/v1/mypage/preferred-feed-visibility` — 공개범위 기본 설정 조회/수정.
  웹 미션 생성/피드 작성 폼이 이 값을 기본값으로 주입
- Saga의 `CreateFeedFromMissionStep`은 `context.getFeedVisibility()`를 직접 사용
- **피드 중복 생성 방지**: `shareExecutionToFeed()`에서 기존 피드가 있으면 업데이트, 없으면 생성
  (`FeedCommandService.updateFeedContentByExecutionId()`)
- **피드 응답의 미션 카테고리명** (LUT-560): `ActivityFeedResponse.mission_category_name` — 피드의 `category_id` 를 Accept-Language 기준
  표시명으로 풀어 내린다 (`FeedQueryService.enrichMissionCategoryNames`, 페이지 단위 distinct 1회 조회·메타 실패 시 생략). `category` 는
  활동 분류(MISSION/ACHIEVEMENT…)라 이름을 분리했다. 비미션 피드는 null. 웹 피드 상세는 이 값을 cyan 칩으로, `visibility` 를
  공개범위 태그(작성글 목록과 같은 `getFeedVisibilityLabel`)로 열람자 전원에게 노출한다.

## Feed Search Type (홈피드 필터)

홈 피드 조회 시 필터별 뷰:

```java
FeedSearchType { ALL, FRIENDS, GUILD, MINE }
```

- `GET /api/v1/feeds/public?searchType=FRIENDS` — 피드 API에 searchType 파라미터
- `GET /api/v1/bff/home?feedSearchType=GUILD` — BFF에 feedSearchType 파라미터
- `FeedQueryService.getFilteredFeeds()` — searchType별 분기
  - FRIENDS: `findFriendsFeeds(friendIds)` (친구의 PUBLIC+FRIENDS 피드). 친구가 없으면 빈 페이지
  - GUILD: `findGuildOnlyFeedsByGuildIds(guildIds, excludedUserIds)` (내 길드들의 GUILD 공개 피드, 차단 유저 제외)
  - MINE: `findPublicFeedsByUserId()` (내 피드, PRIVATE 제외)
- **본인 피드 제외** (LUT-559): FRIENDS·GUILD 탭은 **다른 유저의 피드만** — FRIENDS 는 friendIds 에서 본인을 빼고, GUILD 는 본인을
  `excludedUserIds`(NOT IN) 에 넣어 WHERE 로 제외한다(count 쿼리에도 같이 걸려 `total_elements` 정합). 내 피드는 ALL·MINE 에서만 보인다.
  QA-168 때 "내가 친구공개로 올린 피드도 친구 탭에 보이게" 본인을 friendIds 에 넣었던 정책은 폐기됐다.
- **카테고리와의 조합**: `searchType`이 ALL 이 아니면 `categoryIds`는 **무시**된다 (`ActivityFeedController`·`BffHomeService` 모두
  searchType 분기가 우선). 탭 필터와 카테고리 필터는 동시에 적용되지 않는다.

## Mission Feed Sync (미션 기록 ↔ 피드 동기화)

미션 실행의 note/image 변경 시 연관된 ActivityFeed에 자동 동기화:

- `MissionFeedNoteChangedEvent` → `FeedCommandService.updateFeedDescriptionByExecutionId()`
- `MissionFeedImageChangedEvent` → `FeedCommandService.updateFeedImageUrlByExecutionId()`
- `isSharedToFeed` 여부와 무관하게 항상 이벤트 발행 (Saga 생성 PRIVATE 피드도 동기화)

## Mission Template Propagation (템플릿 수정 전파)

어드민에서 `MissionTemplate`의 시간 설정을 수정하면 이미 복제된 Mission에도 전파:

- `MissionTemplateAdminService.updateTemplate()` → `MissionRepository.updateDurationByBaseMissionId()`
- `duration_minutes`, `target_duration_minutes` 일괄 업데이트 (`baseMissionId` 기반)

## Mission Soft Delete (미션 소프트 삭제)

미션 삭제 시 물리적 삭제 대신 소프트 삭제 사용:

- `Mission.isDeleted` (boolean) + `Mission.deletedAt` (LocalDateTime)
- 목록 조회 시 `isDeleted=false` 필터링
- **타이머 미션 최대 목표시간 (LUT-551)**: 생성 폼의 TIMED 선택 시 `target_duration_minutes` 를 반드시 보낸다 — 10분 간격 10~240분, 기본 30분
  (백엔드 `MissionCreateRequest` `@Max(240)`; 미션북 템플릿의 목표시간은 어드민 DTO 가 별도 관리). 폼 기본 선택은 **체크(SIMPLE)**, 순서 체크→타이머.
  길드 미션 생성 폼(LUT-553)도 같은 `createMission` API·같은 컴포넌트로 동일 규칙을 따른다 — 길드 미션은 `source=USER` 라 보너스 없이 목표분 캡, 길드 EXP 도 동일.
  - 목표 도달 시 수행 화면이 즉시 `complete?autoEnded=true` 로 완료하고(5분 주기 스케줄러보다 먼저), 서버는 스케줄러 자동 종료와 동일한
    `MissionAutoEndedEvent`(MISSION_AUTO_ENDED 푸시)를 발행한다. 화면을 닫은 뒤에는 스케줄러가 종료·발송한다. 유저 직접 완료(버튼)는 플래그 없음.
  - **EXP: 목표 달성 보너스(`bonusExpOnFullCompletion`)는 미션북(`source=SYSTEM`) 전용**. 유저 생성 미션(USER/GUILD)은 목표 도달 시 보너스 없이
    `expEarned = targetDurationMinutes` 로 캡한다(30분 미션 = 30 EXP). 미션북 다이아(`GrantMissionBookDiamondStep`)도 SYSTEM 게이트라 영향 없음.

## Mission Auto-Complete (미션 자동 종료)

`MissionAutoCompleteScheduler` (5분 간격 실행, 설정: `MissionExecutionProperties`):

- 목표 시간 도달 미션: 자동 완료 처리 (목표 시간 기준 EXP 지급)
- 4시간(`maxExecutionMinutes=240`) 초과 미션 (목표 시간 없는 경우): 자동 완료 (`baseExp=120` 설정이지만 LUT-568 이후 `min(baseExp, timedExp(240)=96)` 으로 클램프 — 방치가 정상 완료보다 유리해지지 않게. `isAutoCompleted=true`, QA-171)
- 경고 알림: 시작 후 180·230분(`warningMinutesAfterStart`) 시점에 `MissionAutoEndWarningEvent` 발행 → FIRST/FINAL 2단계 푸시 (dedup)

## Guild Mission Auto-Enrollment (길드 미션 자동 참가)

길드원이 가입하거나 길드 미션이 공개되면 자동으로 참가자 등록:

- `MissionParticipantService.addGuildMemberAsParticipant()` — 중복 방지 포함
- 탈퇴/추방 시 참가자 정리

## Guild Invitation (길드 초대)

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

## Browse-First (비로그인 열람) — QA-110/QA-111

비로그인 상태에서도 홈/피드/길드의 GET 요청을 허용 (작성/수정/삭제는 인증 필요). `SecurityConfig`에서 `HttpMethod.GET`만 `permitAll`로 화이트리스트:

```
/api/v1/bff/home, /api/v1/bff/guild/list, /api/v1/bff/guild/{guildId}
/api/v1/feeds/public, /api/v1/feeds/{feedId}, /api/v1/feeds/{feedId}/comments
/api/v1/feeds/search, /api/v1/feeds/category/**
/api/v1/guilds/public, /api/v1/guilds/search, /api/v1/guilds/{guildId}
/api/v1/mypage/profile/{userId}, /api/v1/mypage/nickname/check
```

비인증 요청은 `Principal == null`이므로 컨트롤러/서비스에서 `currentUserId` Optional 처리 필요. 친구/좋아요/내 활동 등은 비로그인에서 빈 결과 또는 404 반환.

## Signup Token Flow (QA-108) — 회원가입 흐름

OAuth 콜백 시점에 `Users` row를 곧바로 INSERT하지 않고 **임시 signup token**으로 Redis 세션을 만든다. 닉네임/약관 동의가 모두 완료된 시점에 비로소 INSERT → 중도 이탈 시 잔여 row가 남지 않는다.

- `SignupTokenService` (TTL 30분, Redis)
  - `signup:{provider}:{emailHash}` → `SignupSessionData` JSON
  - `signup-token:{token}` → `{provider}:{emailHash}` 인덱스 (양방향)
- 클라이언트 흐름: OAuth 콜백 → `signup_token` 수신 → `POST /api/v1/auth/signup` (닉네임 + 약관 동의) → 정식 가입 + JWT 발급
- 신규/기존 사용자 응답 형태가 다름 (`SocialLoginResponseDto` 분기)
- 닉네임 중복 체크: `GET /api/v1/mypage/nickname/check` (비인증 허용)

## Race Condition (중복 키 오류) 해결 패턴

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

적용 사례: `AchievementService.getOrCreateUserAchievement()`, `AttendanceService.checkIn()`, `NotificationService.createNotificationWithDeduplication()`
