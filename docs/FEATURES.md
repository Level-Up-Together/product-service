# Feature-Specific Implementation Notes

도메인 기능별 구현 규칙 및 주의사항. 코드만 봐서는 알기 어려운 비즈니스 규칙과 의도를 정리.

## Pinned Mission (고정 미션) - Template-Instance 패턴

고정 미션(`isPinned=true`)은 `DailyMissionInstance` 엔티티를 사용:

- 매일 자동 생성 (스케줄러: `DailyMissionInstanceScheduler`, cron: `0 5 0 * * *`)
- 미션 정보 스냅샷 저장 (미션 변경 시 과거 기록 보존)
- 다중 실행 지원 (시퀀스 번호로 일일 복수 완료 추적)

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
TIMED   // 시간 측정 (기본값) — 분당 1 EXP, 2시간 자동완료
SIMPLE  // 수행 여부 — 즉시 완료, 고정 +5 EXP
```

- `Mission.executionMode` 필드로 미션 생성 시 선택 (TIMED/SIMPLE)
- `MissionExecutionLifecycle.complete(boolean awardSimpleExp)`에서 SIMPLE 모드 분기 — `awardSimpleExp=false`면 EXP 0
- SIMPLE 모드는 최소 수행 시간 제한 없음, 자동완료 불필요
- **SIMPLE 일일 EXP 한도 (QA-130)**: 하루 `SIMPLE_DAILY_LIMIT(10)`회까지 EXP +5 지급, 초과 시에도 수행은 가능하지만 EXP=0 처리
  - 카운트는 일반(`MissionExecution`) + 고정(`DailyMissionInstance`) SIMPLE 완료 합산 (mission 단위가 아닌 **유저 단위**)
  - 한도 도달 여부는 `MissionExecutionService.isSimpleDailyLimitReached(userId, date)`로 조회
  - 실제 EXP 0 처리는 Saga 단계(`CompleteExecutionStep`/`CompletePinnedInstanceStep`)에서 카운트 체크 후 `lifecycle.complete(false)` 호출
  - 응답 DTO(`MissionExecutionResponse`/`DailyMissionInstanceResponse`)의 `daily_simple_exp_capped` 플래그로 프론트 안내

## Mission 생성 한도 (QA-130)

사용자별 PERSONAL 미션 합산 최대 30개 (`Mission.MAX_PERSONAL_MISSIONS_PER_USER`):

- **길드 미션(GUILD)은 카운트 제외 / 무제한**
- 검증: `MissionService.validateMissionCreationLimit(userId, type)` — `type != PERSONAL`이면 통과
- 카운트 쿼리: `MissionRepository.countActivePersonalByCreatorId(creatorId)` — `type=PERSONAL AND isDeleted=false`
- 초과 시: `CustomException("050104", "error.mission.creation_limit_exceeded")` (i18n ko/en/ja)
- 미션북에서 추가한 미션(`source=SYSTEM, type=PERSONAL`)도 카운트에 포함

## Feed Visibility at Execution (피드 공개범위)

피드 공개범위를 미션 생성이 아닌 **실행 완료 시** 선택:

- `MissionExecutionController.completeExecution()` — `feedVisibility` 파라미터 (PUBLIC/FRIENDS/PRIVATE)
- `Users.preferredFeedVisibility` — 유저의 최근 선택값을 기억 (쿠팡 UX 패턴)
- `GET/PUT /api/v1/mypage/preferred-feed-visibility` — 선호 공개범위 조회/수정
- Saga의 `CreateFeedFromMissionStep`은 `context.getFeedVisibility()`를 직접 사용
- **피드 중복 생성 방지**: `shareExecutionToFeed()`에서 기존 피드가 있으면 업데이트, 없으면 생성
  (`FeedCommandService.updateFeedContentByExecutionId()`)

## Feed Search Type (홈피드 필터)

홈 피드 조회 시 필터별 뷰:

```java
FeedSearchType { ALL, FRIENDS, GUILD, MINE }
```

- `GET /api/v1/feeds/public?searchType=FRIENDS` — 피드 API에 searchType 파라미터
- `GET /api/v1/bff/home?feedSearchType=GUILD` — BFF에 feedSearchType 파라미터
- `FeedQueryService.getFilteredFeeds()` — searchType별 분기
  - FRIENDS: `findFriendsFeeds()` (친구의 PUBLIC+FRIENDS 피드)
  - GUILD: `findGuildFeedsByGuildIds()` (내 길드들의 PUBLIC+GUILD 피드)
  - MINE: `findByUserId()` (내 피드 전체)

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

## Mission Auto-Complete (미션 자동 종료)

`MissionAutoCompleteScheduler` (5분 간격 실행):

- 목표 시간 도달 미션: 자동 완료 처리 (목표 시간 기준 EXP 지급)
- 2시간 초과 미션 (목표 시간 없는 경우): 자동 완료 (기본 EXP만 지급, `isAutoCompleted=true`)
- 자동 종료 10분 전: `MissionAutoEndWarningEvent` 발행 → 경고 알림 발송

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
