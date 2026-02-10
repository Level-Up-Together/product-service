package io.pinkspider.global.event.listener;

import static io.pinkspider.global.config.AsyncConfig.EVENT_EXECUTOR;

import io.pinkspider.global.event.UserProfileChangedEvent;
import io.pinkspider.leveluptogethermvp.feedservice.infrastructure.ActivityFeedRepository;
import io.pinkspider.leveluptogethermvp.feedservice.infrastructure.FeedCommentRepository;
import io.pinkspider.leveluptogethermvp.chatservice.infrastructure.GuildChatMessageRepository;
import io.pinkspider.leveluptogethermvp.chatservice.infrastructure.GuildChatParticipantRepository;
import io.pinkspider.leveluptogethermvp.chatservice.infrastructure.GuildDirectMessageRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildPostCommentRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildPostRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionCommentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 사용자 프로필 변경 시 스냅샷 데이터 동기화 리스너
 * 3개 DB(feed, guild, mission)의 스냅샷을 각각 독립적으로 업데이트
 * MSA 전환 시 Kafka Consumer로 대체 예정
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class UserProfileSnapshotEventListener {

    // Feed DB
    private final ActivityFeedRepository activityFeedRepository;
    private final FeedCommentRepository feedCommentRepository;
    // Guild DB
    private final GuildChatMessageRepository guildChatMessageRepository;
    private final GuildPostRepository guildPostRepository;
    private final GuildPostCommentRepository guildPostCommentRepository;
    private final GuildDirectMessageRepository guildDirectMessageRepository;
    private final GuildChatParticipantRepository guildChatParticipantRepository;
    // Mission DB
    private final MissionCommentRepository missionCommentRepository;

    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserProfileChanged(UserProfileChangedEvent event) {
        String userId = event.userId();
        log.info("프로필 스냅샷 동기화 시작: userId={}", userId);

        safeHandle("FeedProfileSync", () -> syncFeedSnapshots(event));
        safeHandle("GuildProfileSync", () -> syncGuildSnapshots(event));
        safeHandle("MissionProfileSync", () -> syncMissionSnapshots(event));
    }

    private void syncFeedSnapshots(UserProfileChangedEvent event) {
        int feedCount = activityFeedRepository.updateUserProfileByUserId(
            event.userId(), event.nickname(), event.profileImageUrl(), event.level());
        int commentCount = feedCommentRepository.updateUserProfileByUserId(
            event.userId(), event.nickname(), event.profileImageUrl(), event.level());
        log.info("Feed 스냅샷 동기화: userId={}, feeds={}, comments={}", event.userId(), feedCount, commentCount);
    }

    private void syncGuildSnapshots(UserProfileChangedEvent event) {
        int chatCount = guildChatMessageRepository.updateSenderNicknameByUserId(event.userId(), event.nickname());
        int postCount = guildPostRepository.updateAuthorNicknameByUserId(event.userId(), event.nickname());
        int postCommentCount = guildPostCommentRepository.updateAuthorNicknameByUserId(event.userId(), event.nickname());
        int dmCount = guildDirectMessageRepository.updateSenderNicknameByUserId(event.userId(), event.nickname());
        int participantCount = guildChatParticipantRepository.updateUserNicknameByUserId(event.userId(), event.nickname());
        log.info("Guild 스냅샷 동기화: userId={}, chats={}, posts={}, postComments={}, dms={}, participants={}",
            event.userId(), chatCount, postCount, postCommentCount, dmCount, participantCount);
    }

    private void syncMissionSnapshots(UserProfileChangedEvent event) {
        int commentCount = missionCommentRepository.updateUserProfileByUserId(
            event.userId(), event.nickname(), event.profileImageUrl(), event.level());
        log.info("Mission 스냅샷 동기화: userId={}, comments={}", event.userId(), commentCount);
    }

    private void safeHandle(String name, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.error("{} 실패: {}", name, e.getMessage(), e);
        }
    }
}
