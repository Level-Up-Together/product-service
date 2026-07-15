# Event-Driven 이벤트 흐름

서비스 간 비동기 통신을 위한 `ApplicationEvent` + `@TransactionalEventListener(AFTER_COMMIT)` 흐름 정리.

> **자동 피드 생성 축소 (QA-35)**: 칭호 획득/업적 달성/길드 가입/친구 추가 피드는 비활성화. 레벨업/길드 레벨업 피드는 **10단위 마일스톤**(Lv 10, 20, 30…)에서만 생성.

## 이벤트 매핑 테이블

| 발행 서비스                       | 이벤트                                | 수신 리스너                               | 처리 내용                                   |
|------------------------------|------------------------------------|--------------------------------------|-----------------------------------------|
| GuildService                 | `GuildJoinedEvent`                 | `AchievementEventListener`           | 길드 가입 업적 체크                             |
| GuildService                 | `GuildJoinedEvent`                 | `FeedProjectionEventListener`        | 길드 가입 피드 생성                             |
| GuildService                 | `GuildJoinedEvent`                 | `UserStatsCounterEventListener`      | guildJoinCount 증가 + 업적 체크               |
| GuildService                 | `GuildCreatedEvent`                | `FeedProjectionEventListener`        | 길드 창설 피드 생성                             |
| GuildService                 | `GuildInvitationEvent`             | `NotificationEventListener`          | 초대 알림 발송                                |
| GuildExperienceService       | `GuildLevelUpEvent`                | `FeedProjectionEventListener`        | 길드 레벨업 피드 생성                            |
| FriendService                | `FriendRequestAcceptedEvent`       | `NotificationEventListener`          | 친구 수락 알림                                |
| FriendService                | `FriendRequestAcceptedEvent`       | `FeedProjectionEventListener`        | 친구 추가 피드 생성 (양쪽)                        |
| FriendService                | `FriendRequestAcceptedEvent`       | `UserStatsCounterEventListener`      | friendCount 증가 + 업적 체크                  |
| FriendService                | `FriendRemovedEvent`               | `UserStatsCounterEventListener`      | friendCount 감소 (양쪽)                     |
| GamificationService          | `TitleAcquiredEvent`               | `NotificationEventListener`          | 칭호 획득 알림                                |
| GamificationService          | `TitleAcquiredEvent`               | `FeedProjectionEventListener`        | 칭호 획득 피드 생성                             |
| GamificationService          | `AchievementCompletedEvent`        | `NotificationEventListener`          | 업적 달성 알림                                |
| GamificationService          | `AchievementCompletedEvent`        | `FeedProjectionEventListener`        | 업적 달성 피드 생성                             |
| GamificationService          | `TitleEquippedEvent`               | `FeedProjectionEventListener`        | 칭호 변경 피드 업데이트                           |
| UserExperienceService        | `UserLevelUpEvent`                 | `FeedProjectionEventListener`        | 레벨업 피드 생성                               |
| UserExperienceService        | `UserLevelUpEvent`                 | `UserLevelUpProfileSyncListener`     | 유저 프로필 레벨 동기화                           |
| AttendanceService            | `AttendanceStreakEvent`            | `FeedProjectionEventListener`        | 연속 출석 피드 생성                             |
| MissionService               | `MissionStateChangedEvent`         | `MissionStateHistoryEventListener`   | 미션 상태 이력 저장                             |
| GuildMemberService           | `GuildMemberJoinedChatNotifyEvent` | `ChatEventListener`                  | 채팅방 입장 알림                               |
| GuildMemberService           | `GuildMemberLeftChatNotifyEvent`   | `ChatEventListener`                  | 채팅방 퇴장 알림                               |
| GuildMemberService           | `GuildMemberKickedChatNotifyEvent` | `ChatEventListener`                  | 채팅방 추방 알림                               |
| UserService                  | `UserSignedUpEvent`                | `UserSignedUpEventListener`          | 기본 칭호 부여                                |
| UserService                  | `UserProfileChangedEvent`          | `*ProfileSnapshotEventListener` (x4) | 비정규화 닉네임 동기화 (chat/feed/guild/mission)  |
| FeedCommandService           | `FeedLikedEvent`                   | `UserStatsCounterEventListener`      | likesReceived 증가 + 업적 체크                |
| FeedCommandService           | `FeedUnlikedEvent`                 | `UserStatsCounterEventListener`      | likesReceived 감소                        |
| MissionCompletionSaga        | `MissionCompletedCountEvent`       | `UserStatsCounterEventListener`      | totalMissionCompletions 증가 + 업적 체크      |
| MissionCompletionSaga        | `GuildMissionCompletedCountEvent`  | `UserStatsCounterEventListener`      | totalGuildMissionCompletions 증가 + 업적 체크 |
| MissionAutoCompleteScheduler | `MissionAutoEndWarningEvent`       | `NotificationEventListener`          | 미션 자동 종료 10분 전 경고 알림                    |
| GuildChatService             | `GuildChatMessageEvent`            | `NotificationEventListener`          | 길드 채팅 알림 (발송자 제외 멤버 전원)                 |
| GuildDirectMessageService    | `GuildDirectMessageEvent`          | `NotificationEventListener`          | 길드 DM 알림 — 레코드+실시간+푸시 통합 (LUT-224)      |
