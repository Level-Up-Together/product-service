package io.pinkspider.leveluptogethermvp.missionservice.event.listener;

import static io.pinkspider.global.config.AsyncConfig.EVENT_EXECUTOR;

import io.pinkspider.global.event.UserProfileChangedEvent;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionCommentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 사용자 프로필 변경 시 Mission DB 스냅샷 동기화
 * MSA 전환 시 Kafka Consumer로 대체 예정
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MissionProfileSnapshotEventListener {

    private final MissionCommentRepository missionCommentRepository;

    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserProfileChanged(UserProfileChangedEvent event) {
        try {
            int commentCount = missionCommentRepository.updateUserProfileByUserId(
                event.userId(), event.nickname(), event.profileImageUrl(), event.level());
            log.info("Mission 스냅샷 동기화: userId={}, comments={}", event.userId(), commentCount);
        } catch (Exception e) {
            log.error("MissionProfileSync 실패: {}", e.getMessage(), e);
        }
    }
}
