package io.pinkspider.global.event.listener;

import static io.pinkspider.global.config.AsyncConfig.EVENT_EXECUTOR;

import io.pinkspider.global.event.MissionStateChangedEvent;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionStateHistory;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionStateHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 미션 상태 변경 이벤트 리스너
 * 상태 변경 히스토리를 DB에 저장
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MissionStateHistoryEventListener {

    private final MissionStateHistoryRepository stateHistoryRepository;

    /**
     * 미션 상태 변경 이벤트 처리
     * 트랜잭션 커밋 후 새로운 트랜잭션에서 히스토리 저장
     */
    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(transactionManager = "missionTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void handleMissionStateChanged(MissionStateChangedEvent event) {
        try {
            log.debug("미션 상태 변경 이벤트 처리: missionId={}, {} -> {}, event={}",
                event.missionId(), event.fromStatus(), event.toStatus(), event.triggerEvent());

            MissionStateHistory history = MissionStateHistory.builder()
                .missionId(event.missionId())
                .fromStatus(event.fromStatus())
                .toStatus(event.toStatus())
                .triggerEvent(event.triggerEvent())
                .triggeredBy(event.userId())
                .reason(event.reason())
                .build();

            stateHistoryRepository.save(history);

            log.info("미션 상태 히스토리 저장 완료: missionId={}, {} -> {}",
                event.missionId(), event.fromStatus(), event.toStatus());
        } catch (Exception e) {
            log.error("미션 상태 히스토리 저장 실패: missionId={}, error={}",
                event.missionId(), e.getMessage(), e);
        }
    }
}
