package io.pinkspider.leveluptogethermvp.towerservice.application;

import io.pinkspider.global.event.MissionExecutionEndedEvent;
import io.pinkspider.global.event.MissionExecutionStartedEvent;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerMissionInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * LUT-632: 미션 수행 시작·종료 이벤트 → 탑 자리. 미션 트랜잭션 커밋 뒤에 받고, 트랜잭션 밖 발행(saga·스케줄러)은 {@code
 * fallbackExecution} 으로 바로 받는다. 탑 처리 실패는 미션을 막지 않는다(로그만).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TowerMissionEventListener {

    private final TowerSeatService towerSeatService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onMissionStarted(MissionExecutionStartedEvent event) {
        try {
            towerSeatService.onMissionStarted(event.userId(), TowerMissionInfo.from(event));
        } catch (Exception e) {
            log.warn(
                    "[tower] 미션 시작 반영 실패: userId={} executionId={} error={}",
                    event.userId(),
                    event.executionId(),
                    e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onMissionEnded(MissionExecutionEndedEvent event) {
        try {
            towerSeatService.onMissionEnded(event.userId(), event.isCompletion());
        } catch (Exception e) {
            log.warn(
                    "[tower] 미션 종료 반영 실패: userId={} executionId={} error={}",
                    event.userId(),
                    event.executionId(),
                    e.getMessage());
        }
    }
}
