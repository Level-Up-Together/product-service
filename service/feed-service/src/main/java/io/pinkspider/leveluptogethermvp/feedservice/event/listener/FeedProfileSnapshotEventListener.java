package io.pinkspider.leveluptogethermvp.feedservice.event.listener;

import static io.pinkspider.global.config.AsyncConfig.EVENT_EXECUTOR;

import io.pinkspider.global.event.UserProfileChangedEvent;
import io.pinkspider.leveluptogethermvp.feedservice.infrastructure.ActivityFeedRepository;
import io.pinkspider.leveluptogethermvp.feedservice.infrastructure.FeedCommentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 사용자 프로필 변경 시 Feed DB 스냅샷 동기화
 * MSA 전환 시 Kafka Consumer로 대체 예정
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FeedProfileSnapshotEventListener {

    private final ActivityFeedRepository activityFeedRepository;
    private final FeedCommentRepository feedCommentRepository;

    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserProfileChanged(UserProfileChangedEvent event) {
        try {
            int feedCount = activityFeedRepository.updateUserProfileByUserId(
                event.userId(), event.nickname(), event.profileImageUrl(), event.level());
            int commentCount = feedCommentRepository.updateUserProfileByUserId(
                event.userId(), event.nickname(), event.profileImageUrl(), event.level());
            log.info("Feed 스냅샷 동기화: userId={}, feeds={}, comments={}", event.userId(), feedCount, commentCount);
        } catch (Exception e) {
            log.error("FeedProfileSync 실패: {}", e.getMessage(), e);
        }
    }
}
