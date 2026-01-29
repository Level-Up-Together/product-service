package io.pinkspider.leveluptogethermvp.missionservice.scheduler;

import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 미션 자동 종료 스케줄러
 *
 * 2시간 이상 진행 중인 미션을 자동으로 종료하여 어뷰징 방지
 * - 5분마다 실행
 * - MissionExecution (일반 미션)과 DailyMissionInstance (고정 미션) 모두 처리
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MissionAutoCompleteScheduler {

    private final MissionExecutionRepository executionRepository;
    private final DailyMissionInstanceRepository instanceRepository;

    /**
     * 최대 미션 수행 시간 (분) - 2시간
     */
    private static final long MAXIMUM_EXECUTION_MINUTES = 120;

    /**
     * 5분마다 실행: 2시간 초과 미션 자동 종료
     */
    @Scheduled(fixedRate = 300000) // 5분 = 300,000ms
    @Transactional(transactionManager = "missionTransactionManager")
    public void autoCompleteExpiredMissions() {
        log.debug("=== 미션 자동 종료 스케줄러 시작 ===");

        LocalDateTime expireThreshold = LocalDateTime.now().minusMinutes(MAXIMUM_EXECUTION_MINUTES);

        try {
            // 1. 일반 미션 (MissionExecution) 자동 종료
            int executionCount = autoCompleteExpiredExecutions(expireThreshold);

            // 2. 고정 미션 (DailyMissionInstance) 자동 종료
            int instanceCount = autoCompleteExpiredInstances(expireThreshold);

            if (executionCount > 0 || instanceCount > 0) {
                log.info("미션 자동 종료 완료: executions={}, instances={}", executionCount, instanceCount);
            }
        } catch (Exception e) {
            log.error("미션 자동 종료 스케줄러 실패", e);
        }

        log.debug("=== 미션 자동 종료 스케줄러 종료 ===");
    }

    /**
     * 일반 미션 (MissionExecution) 자동 종료
     */
    private int autoCompleteExpiredExecutions(LocalDateTime expireThreshold) {
        List<MissionExecution> expiredExecutions = executionRepository.findExpiredInProgressExecutions(expireThreshold);

        int count = 0;
        for (MissionExecution execution : expiredExecutions) {
            if (execution.autoCompleteIfExpired()) {
                count++;
                log.info("일반 미션 자동 종료: executionId={}, userId={}, startedAt={}",
                    execution.getId(),
                    execution.getParticipant().getUserId(),
                    execution.getStartedAt());
            }
        }
        return count;
    }

    /**
     * 고정 미션 (DailyMissionInstance) 자동 종료
     */
    private int autoCompleteExpiredInstances(LocalDateTime expireThreshold) {
        List<DailyMissionInstance> expiredInstances = instanceRepository.findExpiredInProgressInstances(expireThreshold);

        int count = 0;
        for (DailyMissionInstance instance : expiredInstances) {
            if (instance.autoCompleteIfExpired()) {
                count++;
                log.info("고정 미션 자동 종료: instanceId={}, userId={}, missionTitle={}, startedAt={}",
                    instance.getId(),
                    instance.getParticipant().getUserId(),
                    instance.getMissionTitle(),
                    instance.getStartedAt());
            }
        }
        return count;
    }
}
