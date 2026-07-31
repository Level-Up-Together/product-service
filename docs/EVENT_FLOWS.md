# Event-Driven 이벤트 흐름

서비스 간 비동기 통신을 위한 `ApplicationEvent` + `@TransactionalEventListener(AFTER_COMMIT)` 흐름 정리.

> **자동 피드 생성 축소 (QA-35)**: 칭호 획득/업적 달성/길드 가입/친구 추가 피드는 비활성화. 레벨업/길드 레벨업 피드는 **10단위 마일스톤**(Lv 10, 20, 30…)에서만 생성.

> 알림 생성 이후의 저장/실시간/푸시 처리 흐름은 CLAUDE.md **"알림/푸시 파이프라인"** 섹션 참조.

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
| FriendService                | `FriendRequestEvent`               | `NotificationEventListener`          | 친구 요청 알림 (요청 대상에게)                      |
| FriendService                | `FriendRequestRejectedEvent`       | `NotificationEventListener`          | 친구 거절 알림 (요청자에게)                        |
| FriendService                | `FriendRequestProcessedEvent`      | `NotificationEventListener`          | 처리 완료된 FRIEND_REQUEST 알림 삭제              |
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
| MyPageService                | `UserWithdrawnEvent`               | `UserWithdrawnEventListener` (guild) | 회원 탈퇴 시 길드 멤버십 정리 — 마스터는 승계/해체 (LUT-287) |
| MyPageService                | `UserWithdrawnEvent`               | `ChatEventListener`                  | 회원 탈퇴 시 전 길드 DM 대화방 비활성화 (LUT-287)      |
| GuildMemberService           | `GuildMemberRemovedEvent`          | `ChatEventListener`                  | 길드 탈퇴/추방 시 해당 길드 DM 대화방 비활성화 (LUT-287)  |
| FeedCommandService           | `FeedLikedEvent`                   | `UserStatsCounterEventListener`      | likesReceived 증가 + 업적 체크                |
| FeedCommandService           | `FeedUnlikedEvent`                 | `UserStatsCounterEventListener`      | likesReceived 감소                        |
| MissionCompletionSaga        | `MissionCompletedCountEvent`       | `UserStatsCounterEventListener`      | totalMissionCompletions 증가 + 업적 체크      |
| MissionCompletionSaga        | `GuildMissionCompletedCountEvent`  | `UserStatsCounterEventListener`      | totalGuildMissionCompletions 증가 + 업적 체크 |
| MissionAutoCompleteScheduler | `MissionAutoEndWarningEvent`       | `NotificationEventListener`          | 자동종료 경고 — 시작 후 180·230분 FIRST/FINAL 2단계 (dedup) |
| MissionReminderScheduler     | `MissionReminderEvent`             | `NotificationEventListener`          | 미션 리마인더 — 설정 요일·시각(유저 타임존) 푸시 (LUT-282) |
| GuildChatService             | `GuildChatMessageEvent`            | `NotificationEventListener`          | 길드 채팅 알림 (발송자 제외 멤버 전원)                 |
| GuildDirectMessageService    | `GuildDirectMessageEvent`          | `NotificationEventListener`          | 길드 DM 알림 — 레코드+실시간+푸시 통합 (LUT-224). 수신자가 방 열람 중이면 이벤트 미발행 (LUT-263) |
| MissionService               | `GuildMissionArrivedEvent`         | `NotificationEventListener`          | 길드 미션 도착 알림 (길드원 전원)                    |
| GuildPostService             | `GuildBulletinCreatedEvent`        | `NotificationEventListener`          | 길드 공지 알림 (길드원 전원)                       |
| GuildMemberService           | `GuildJoinRequestedEvent`          | `NotificationEventListener`          | 길드 가입 신청 알림 (임원 전원)                     |
| GuildMemberService           | `GuildJoinApprovedEvent`           | `NotificationEventListener`          | 길드 가입 승인 알림 (신청자) (LUT-300)             |
| GuildMemberService           | `GuildJoinRejectedEvent`           | `NotificationEventListener`          | 길드 가입 거절 알림 (신청자) (LUT-300)             |
| UserExperienceService        | `GuildCreationEligibleEvent`       | `NotificationEventListener`          | 길드 창설 가능 알림                             |
| FeedCommandService           | `FeedCommentEvent`                 | `NotificationEventListener`          | 피드 댓글 알림 (본인 댓글 제외)                     |
| FeedCommandService           | `FeedCommentReplyEvent`            | `NotificationEventListener`          | 대댓글 알림 — 부모 작성자+스레드 참여자 (QA-73)         |
| FeedCommandService           | `FeedCommentLikedEvent`            | `NotificationEventListener`          | 댓글 좋아요 알림 (QA-73)                       |
| MissionCommentService        | `MissionCommentEvent`              | `NotificationEventListener`          | 미션 댓글 알림 (미션 생성자)                       |
| ReportService                | `ContentReportedEvent`             | `NotificationEventListener`          | 신고 접수 알림 — 피신고 유저 + (길드 콘텐츠 시) 길드마스터    |
