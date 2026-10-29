# Event-Driven 이벤트 흐름

서비스 간 비동기 통신을 위한 `ApplicationEvent` + `@TransactionalEventListener(AFTER_COMMIT)` 흐름 정리.
이벤트 레코드는 대부분 platform kernel `io.pinkspider.global.event`, 서비스 로컬 이벤트는 표에 (로컬)로 표기. 2026-10-29 코드 기준.

> **자동 피드 생성 축소 (QA-35)**: 칭호 획득/업적 달성/길드 가입/친구 추가/연속 출석 피드는 비활성화(`FeedProjectionEventListener` 핸들러가 로그만 남김). 레벨업/길드 레벨업 피드는 **10단위 마일스톤**(Lv 10, 20, 30…)에서만 생성. 길드 창설 피드는 **공개 길드만** (LUT-517).

> 알림 생성 이후의 저장/실시간/푸시 처리 흐름은 CLAUDE.md **"알림/푸시 파이프라인"** 섹션 참조.

## 이벤트 매핑 테이블

| 발행 서비스                       | 이벤트                                | 수신 리스너                               | 처리 내용                                   |
|------------------------------|------------------------------------|--------------------------------------|-----------------------------------------|
| `GuildService`·`GuildMemberService`·`GuildInvitationService` | `GuildJoinedEvent` | `AchievementEventListener` | 길드 가입 업적 체크(`GUILD_SERVICE`). 발행 = 창설·공개 가입·가입 승인·초대·초대 링크 합류(`addActiveMember`, LUT-519)·초대 수락 |
| 〃                            | `GuildJoinedEvent`                 | `FeedProjectionEventListener`        | 스킵 — 길드 가입 피드 비활성화 (QA-35)               |
| 〃                            | `GuildJoinedEvent`                 | `UserStatsCounterEventListener`      | guildJoinCount 를 distinct 가입 길드 수로 sync + 업적 체크(`USER_STATS`) (LUT-418) |
| 〃                            | `GuildJoinedEvent`                 | `GuildMissionEventListener` (mission) | 진행 중(OPEN·IN_PROGRESS) **고정** 길드 미션에만 자동 참여 — 일반 길드 미션 제외 (LUT-518) |
| GuildService                 | `GuildMasterAssignedEvent`         | `AchievementEventListener`           | 길드 창설 시 마스터 업적 체크(`GUILD_SERVICE`)          |
| GuildService                 | `GuildCreatedEvent`                | `FeedProjectionEventListener`        | 길드 창설 피드 생성 — 비공개 길드는 스킵 (LUT-517)       |
| GuildInvitationService       | `GuildInvitationEvent`             | `NotificationEventListener`          | 초대 알림 (초대 대상) — 양방향 차단 시 스킵              |
| GuildExperienceService       | `GuildLevelUpEvent`                | `FeedProjectionEventListener`        | 길드 레벨업 피드 생성 — 10단위 마일스톤만                 |
| FriendService                | `FriendRequestAcceptedEvent`       | `NotificationEventListener`          | 친구 수락 알림 (요청자에게)                         |
| FriendService                | `FriendRequestAcceptedEvent`       | `FeedProjectionEventListener`        | 스킵 — 친구 추가 피드 비활성화 (QA-35)               |
| FriendService                | `FriendRequestAcceptedEvent`       | `UserStatsCounterEventListener`      | friendCount 증가 (양쪽) + 업적 체크(`FRIEND_SERVICE`) |
| FriendService                | `FriendRemovedEvent`               | `UserStatsCounterEventListener`      | friendCount 감소 (양쪽). 발행 = 친구 삭제 + 친구였던 상대 차단 시 (LUT-367) |
| FriendService                | `FriendRequestEvent`               | `NotificationEventListener`          | 친구 요청 알림 (요청 대상에게). 상대가 나를 차단한 경우(shadow block) 미발행 (LUT-383) |
| — (발행처 없음)                  | `FriendRequestRejectedEvent`       | `NotificationEventListener`          | 핸들러만 잔존 — 거절은 요청자에게 알리지 않음 (LUT-396)       |
| FriendService                | `FriendRequestProcessedEvent`      | `NotificationEventListener`          | 수락·거절 시 처리 완료된 FRIEND_REQUEST 알림 삭제        |
| TitleService                 | `TitleAcquiredEvent`               | `NotificationEventListener`          | 칭호 획득 알림 (신규 획득 + notify 시에만)              |
| TitleService                 | `TitleAcquiredEvent`               | `FeedProjectionEventListener`        | 스킵 — 칭호 획득 피드 비활성화 (QA-35)               |
| AchievementService           | `AchievementCompletedEvent`        | `NotificationEventListener`          | 업적 달성 알림 — 숨김 업적은 미발행                     |
| AchievementService           | `AchievementCompletedEvent`        | `FeedProjectionEventListener`        | 스킵 — 업적 달성 피드 비활성화 (QA-35)               |
| TitleService                 | `TitleEquippedEvent`               | `FeedProjectionEventListener`        | 칭호 장착/해제 시 내 피드 칭호 일괄 업데이트                |
| TitleService                 | `TitleEquippedEvent`               | `HomeCacheEvictListener`             | `todayPlayers`·`todayPlayersByCategory`·`seasonMvpData` 무효화 (LUT-427) |
| UserExperienceService·AttendanceService | `AchievementCheckRequestedEvent` (로컬) | `AchievementEventListener`  | 커밋 후 업적 체크 — 경험치 `USER_EXPERIENCE`(+카테고리 `USER_CATEGORY_EXPERIENCE`), 출석 `USER_STATS` (QA-178) |
| SubscriptionStipendTxService | `SubscriptionStipendGrantedEvent`  | `NotificationEventListener`          | 구독 데일리 스티펜드 지급 알림 — referenceId=지급일 epochDay 로 하루 1회 dedup (LUT-489) |
| UserItemService·ItemGrantAdminService | `ItemEquippedEvent`       | `HomeCacheEvictListener`             | 아이템 장착/해제(+장착 중 아이템 관리자 회수) 시 MVP·시즌 캐시 무효화 (LUT-427) |
| ShopService                  | `ShopItemPurchasedEvent`           | `NotificationEventListener`          | 아이템 구매 알림 `ITEM_PURCHASED` — 수신자 locale 아이템명 (LUT-410) |
| SeasonRewardProcessorService | `SeasonRewardItemGrantedEvent`     | `NotificationEventListener`          | 시즌 보상 아이템 알림 `SEASON_REWARD_ITEM` — 신규 지급 시에만 (LUT-410) |
| ItemGrantAdminService        | `ItemGrantedByAdminEvent`          | `NotificationEventListener`          | 관리자 수동 지급 알림 `ITEM_GRANTED` (LUT-472)        |
| LevelRewardItemService       | `LevelRewardItemGrantedEvent`      | `NotificationEventListener`          | 레벨 5 도달 성장의 새싹 지급 알림 `ITEM_GRANTED` — 이미 보유면 미발행 (LUT-593) |
| ItemPushDispatchService      | `EquippedItemPushDueEvent`         | `NotificationEventListener`          | 장착 아이템 푸시 `EQUIPPED_ITEM_PUSH` — 유저별 트랜잭션 커밋 후 발송, 제목=아이템명·본문=대사(`{nickname}` 치환) (LUT-516/573) |
| UserExperienceService        | `UserLevelUpEvent`                 | `FeedProjectionEventListener`        | 레벨업 피드 생성 — 10단위 마일스톤만                    |
| UserExperienceService        | `UserLevelUpEvent`                 | `UserLevelUpProfileSyncListener`     | 프로필 캐시 evict + `UserProfileChangedEvent` 재발행(레벨 스냅샷 동기화) |
| UserExperienceService        | `UserLevelUpEvent`                 | `NotificationEventListener`          | 레벨업 알림 — 여러 레벨 동시 상승 시 최종 레벨 1건 (LUT-301) |
| UserExperienceService        | `UserLevelUpEvent`                 | `HomeCacheEvictListener`             | `todayPlayers`·`todayPlayersByCategory` 무효화      |
| UserExperienceService        | `GuildCreationEligibleEvent`       | `NotificationEventListener`          | 레벨 2 도달(이전 <2) 시 길드 창설 가능 알림 — 문구 레벨은 `event.level()` (LUT-626) |
| AttendanceService            | `AttendanceStreakEvent`            | `FeedProjectionEventListener`        | 스킵 — 연속 출석 피드 비활성화 (QA-35). 발행은 7·14·30·60·90일 |
| MissionService               | `MissionStateChangedEvent`         | `MissionStateHistoryEventListener`   | 미션 상태 이력 저장 (생성·모집·시작·완료·취소)              |
| MissionService               | `MissionDeletedEvent`              | `MissionFeedEventListener`           | 로그만 — 소프트 삭제 시 피드 유지                      |
| RegularMissionExecutionStrategy·DailyMissionInstanceService | `MissionFeedImageChangedEvent` | `MissionFeedEventListener` | 수행 기록 이미지 → 피드 이미지 동기화 (QA-53) |
| 〃                            | `MissionFeedNoteChangedEvent`      | `MissionFeedEventListener`           | 수행 기록 노트 → 피드 description 동기화             |
| 〃                            | `MissionFeedUnsharedEvent`         | `MissionFeedEventListener`           | 피드 공유 취소 → 피드 삭제                          |
| GuildMemberService·GuildInvitationService | `GuildMemberJoinedChatNotifyEvent` | `ChatEventListener`         | 채팅방 입장 알림                               |
| GuildMemberService           | `GuildMemberLeftChatNotifyEvent`   | `ChatEventListener`                  | 채팅방 퇴장 알림 (회원 탈퇴 정리·마스터 승계 포함)            |
| GuildMemberService           | `GuildMemberKickedChatNotifyEvent` | `ChatEventListener`                  | 채팅방 추방 알림                               |
| Oauth2Service                | `UserSignedUpEvent`                | `UserSignedUpEventListener`          | 기본 칭호 부여·장착 + 기본 아이템 지급 (LUT-296)         |
| MyPageService·UserLevelUpProfileSyncListener | `UserProfileChangedEvent` | `*ProfileSnapshotEventListener` (x4) | 비정규화 프로필 동기화 — chat·guild 닉네임 / feed 닉네임·이미지 / mission 댓글 닉네임·이미지·레벨 |
| MyPageService                | `UserWithdrawnEvent`               | `UserWithdrawnEventListener` (guild) | 회원 탈퇴 시 길드 멤버십 정리 — 마스터는 승계/해체 (LUT-287). 정리 중 `GuildMemberRemovedEvent` 재발행. 어드민 즉시 탈퇴도 같은 경로 (LUT-584) |
| MyPageService                | `UserWithdrawnEvent`               | `ChatEventListener`                  | 전 길드 DM 대화방 비활성화 + 단체 채팅 참여 정리 (LUT-287, LUT-471) |
| GuildMemberService·GuildService | `GuildMemberRemovedEvent`       | `ChatEventListener`                  | 해당 길드 DM 대화방 비활성화 + 단체 채팅 참여 정리 (LUT-287, LUT-471). 발행 = 탈퇴·추방·회원 탈퇴 정리·길드 해체(마스터, QA-213) |
| 〃                            | `GuildMemberRemovedEvent`          | `GuildMissionEventListener` (mission) | 해당 길드 미션 참여 철회                          |
| FeedCommandService           | `FeedLikedEvent`                   | `UserStatsCounterEventListener`      | likesReceived 증가 + 업적 체크(`FEED_SERVICE`)    |
| FeedCommandService           | `FeedUnlikedEvent`                 | `UserStatsCounterEventListener`      | likesReceived 감소                        |
| MissionAutoCompleteScheduler | `MissionAutoEndWarningEvent`       | `NotificationEventListener`          | 자동종료 경고 — 시작 후 180·230분 FIRST/FINAL 2단계 (dedup) |
| MissionAutoCompleteScheduler·MissionExecutionService | `MissionAutoEndedEvent` | `NotificationEventListener` | 목표시간 도달 자동 종료 알림 `MISSION_AUTO_ENDED` — 스케줄러 (LUT-510) + 클라이언트 `autoEnded=true` 완료 (LUT-551) |
| MissionReminderScheduler     | `MissionReminderEvent`             | `NotificationEventListener`          | 미션 리마인더 — 설정 요일·시각(유저 타임존) 푸시 (LUT-282) |
| GuildChatService             | `GuildChatMessageEvent`            | `NotificationEventListener`          | 길드 채팅 알림 (발송자 제외 멤버 전원) — 차단 관계 스킵 (LUT-373), 사진만이면 `notification.preview.photo` (LUT-590) |
| GuildDirectMessageService    | `GuildDirectMessageEvent`          | `NotificationEventListener`          | 길드 DM 알림 — 레코드+실시간+푸시 통합 (LUT-224). 수신자가 방 열람 중(LUT-263)·발신자 차단(shadow block, LUT-383)이면 미발행. 사진만이면 `notification.preview.photo` (LUT-590) |
| MissionService               | `GuildMissionArrivedEvent`         | `NotificationEventListener`          | 길드 미션 모집 시작(OPEN) 시 길드원 자동 참여 후 도착 알림 (생성자 제외 길드원 전원) |
| GuildPostService             | `GuildBulletinCreatedEvent`        | `NotificationEventListener`          | 길드 공지 알림 (작성자 제외 길드원 전원)                  |
| GuildMemberService           | `GuildJoinRequestedEvent`          | `NotificationEventListener`          | 길드 가입 신청 알림 (임원 전원)                     |
| GuildMemberService           | `GuildJoinApprovedEvent`           | `NotificationEventListener`          | 길드 가입 승인 알림 (신청자) (LUT-300)             |
| GuildMemberService           | `GuildJoinRejectedEvent`           | `NotificationEventListener`          | 길드 가입 거절 알림 (신청자) (LUT-300)             |
| FeedCommandService           | `FeedCommentEvent`                 | `NotificationEventListener`          | 피드 댓글 알림 (최상위 댓글, 본인 제외) — 차단 관계 스킵       |
| FeedCommandService           | `FeedCommentEvent`                 | `UserStatsCounterEventListener`      | commentsReceived 증가 + 업적 체크(`USER_STATS`) (QA-113) |
| FeedCommandService           | `FeedCommentReplyEvent`            | `NotificationEventListener`          | 대댓글 알림 — 부모 작성자+스레드 참여자, 차단 관계 스킵 (QA-73) |
| FeedCommandService           | `FeedCommentLikedEvent`            | `NotificationEventListener`          | 댓글 좋아요 알림 — 차단 관계 스킵 (QA-73)             |
| MissionCommentService        | `MissionCommentEvent`              | `NotificationEventListener`          | 미션 댓글 알림 (미션 생성자, 본인 댓글 제외)               |
| MissionCommentService        | `MissionCommentEvent`              | `UserStatsCounterEventListener`      | commentsReceived 증가 + 업적 체크(`USER_STATS`) (QA-113) |
| FeedCommandService           | `FeedCommentDeletedEvent`          | `UserStatsCounterEventListener`      | commentsReceived 감소 — 작성 이벤트와 대칭 가드 (LUT-418) |
| MissionCommentService        | `MissionCommentDeletedEvent`       | `UserStatsCounterEventListener`      | commentsReceived 감소 — 작성 이벤트와 대칭 가드 (LUT-418) |
| ReportService                | `ContentReportedEvent`             | `NotificationEventListener`          | 신고 접수 알림 — 피신고 유저 + (길드 콘텐츠 시) 길드마스터    |
| AdminPushCampaignService     | `AdminPushCampaignCreatedEvent` (로컬) | `AdminPushCampaignDispatcher`    | 캠페인 이력 커밋 후 대상 유저별 `createNotification(ADMIN_PUSH)` — PENDING 아니면 스킵 (LUT-508) |

"차단 관계 스킵" = `NotificationEventListener.isBlockedInteraction` → `UserQueryFacade.isBlockedBetween` 양방향 판정 (LUT-367).

## 리스너 실행 방식

- **`@Async(eventExecutor)` + AFTER_COMMIT** (executor 는 platform infra `AsyncConfig`): `NotificationEventListener`, `AchievementEventListener`,
  `UserStatsCounterEventListener`, `MissionFeedEventListener`, `*ProfileSnapshotEventListener` x4, `UserWithdrawnEventListener`(guild),
  `AdminPushCampaignDispatcher`, `GuildMissionEventListener`·`MissionStateHistoryEventListener`(+ `REQUIRES_NEW` `missionTransactionManager`).
- **`@Async`(기본 executor)**: `UserSignedUpEventListener`.
- **동기**(커밋 후 발행 스레드에서 실행): `FeedProjectionEventListener`, `ChatEventListener`, `HomeCacheEvictListener`, `UserLevelUpProfileSyncListener`.
- **`fallbackExecution = true`**(트랜잭션 밖 발행도 실행): `FeedProjectionEventListener`(`TitleEquippedEvent` 제외), `ChatEventListener` 전부,
  `UserSignedUpEventListener`, `UserWithdrawnEventListener`(guild), `NotificationEventListener.handleContentReported`. 그 외는 트랜잭션 밖에서 발행하면 실행되지 않는다.
- ⚠ `UserLevelUpProfileSyncListener` 는 동기 AFTER_COMMIT 콜백 안(트랜잭션 동기화 해제 후)에서 `UserProfileChangedEvent` 를 발행하는데, 수신 `*ProfileSnapshotEventListener` 는
  `fallbackExecution` 이 없어 **이벤트를 버린다** — dev 로그로 재현 확인(2026-10-28 레벨업에 스냅샷 동기화 로그 0건). 영향은 레벨을 저장하는 미션 댓글 `userLevel`. 수정은 LUT-653.

## 이벤트를 쓰지 않는 흐름 · 짝이 없는 이벤트

- **미션 완료 통계**: 이벤트가 아니라 saga step `UpdateUserStatsStep` 이 `GamificationQueryFacade.recordMissionCompletion` + `USER_STATS` 업적 체크를 직접 호출
  (예전 `MissionCompletedCountEvent`/`GuildMissionCompletedCountEvent` 는 코드에 없음).
- **레벨 5 보상 지급** (LUT-593): `UserExperienceService` 가 레벨업 트랜잭션 안에서 `LevelRewardItemService.grantOnLevelUp` 을 직접 호출, 알림만 이벤트.
- **채팅 이미지 파일 삭제** (LUT-590): 이벤트가 아니라 `ChatImageService.deleteAfterCommit` 이 `TransactionSynchronization.afterCommit` 으로 처리.
- **유저 일별 활동 수집** (LUT-598): 이벤트가 아니라 `UserActivityInterceptor` → `UserActivityRecorder.recordAsync`(`@Async`) 직접 호출.
- **실시간 STOMP 전파**: 이벤트가 아니라 Redis pub/sub 퍼블리셔(afterCommit 발행) → 릴레이. CLAUDE.md "WebSocket 실시간 전파" 참조.
- **발행처 없음**: `FriendRequestRejectedEvent`(LUT-396), `TokenRefreshEvent`(`TokenMetricsService.handleTokenRefreshEvent` `@EventListener` 만 존재).
- **수신처 없음**: platform saga `SagaCompletedEvent`/`SagaCompensatedEvent`(`SagaOrchestrator` → `DefaultSagaEventPublisher`).
- **프레임워크 이벤트**: `DmPresenceDisconnectListener` 가 `SessionDisconnectEvent` 로 DM presence 해제 (LUT-263). `ApplicationReadyEvent` 는 meta 캐시 워밍
  (`CommonCodeRedisConfig`·`CryptoMetaDataRedisConfig`·`MissionCategoryRedisConfig`) + `SeasonScheduledTaskManager.restorePendingSeasonTasks`.
