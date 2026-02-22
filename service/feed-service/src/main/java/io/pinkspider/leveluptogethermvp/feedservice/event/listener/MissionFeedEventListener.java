package io.pinkspider.leveluptogethermvp.feedservice.event.listener;

import static io.pinkspider.global.config.AsyncConfig.EVENT_EXECUTOR;

import io.pinkspider.global.event.MissionDeletedEvent;
import io.pinkspider.global.event.MissionFeedImageChangedEvent;
import io.pinkspider.global.event.MissionFeedUnsharedEvent;
import io.pinkspider.leveluptogethermvp.feedservice.application.FeedCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 미션 → 피드 이벤트 리스너
 * 미션 서비스에서 발행한 이벤트를 수신하여 피드 서비스를 호출
 * MSA 전환 시 Kafka Consumer로 대체 예정
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MissionFeedEventListener {

    private final FeedCommandService feedCommandService;

    /**
     * 미션 수행 기록의 이미지 변경 시 피드 이미지 동기화
     */
    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFeedImageChanged(MissionFeedImageChangedEvent event) {
        safeHandle("MissionFeedImageChanged", () ->
            feedCommandService.updateFeedImageUrlByExecutionId(
                event.executionId(), event.imageUrl()));
    }

    /**
     * 미션 피드 공유 취소 시 피드 삭제
     */
    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFeedUnshared(MissionFeedUnsharedEvent event) {
        safeHandle("MissionFeedUnshared", () ->
            feedCommandService.deleteFeedByExecutionId(event.executionId()));
    }

    /**
     * 미션 소프트 삭제 시 피드 유지 (수행 기록 및 히스토리 보존)
     */
    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMissionDeleted(MissionDeletedEvent event) {
        log.info("미션 소프트 삭제 이벤트 수신 (피드 유지): missionId={}", event.missionId());
    }

    private void safeHandle(String eventName, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.error("{} 이벤트 처리 실패: {}", eventName, e.getMessage(), e);
        }
    }
}
