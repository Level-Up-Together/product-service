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

    // LUT-276: 레벨은 작성 당시 스냅샷을 유지한다 (닉네임/프로필 사진만 동기화).
    // P2-2(32f11c5d)에서 레벨까지 현재값으로 덮었으나, 피드는 "작성 시점의 유저 레벨"을
    // 보여주는 것으로 제품 정책이 확정됨 — event.level() 은 피드에서는 사용하지 않는다.
    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserProfileChanged(UserProfileChangedEvent event) {
        try {
            int feedCount = activityFeedRepository.updateUserProfileByUserId(
                event.userId(), event.nickname(), event.profileImageUrl());
            int commentCount = feedCommentRepository.updateUserProfileByUserId(
                event.userId(), event.nickname(), event.profileImageUrl());
            log.info("Feed 스냅샷 동기화: userId={}, feeds={}, comments={}", event.userId(), feedCount, commentCount);
        } catch (Exception e) {
            log.error("FeedProfileSync 실패: {}", e.getMessage(), e);
        }
    }
}
