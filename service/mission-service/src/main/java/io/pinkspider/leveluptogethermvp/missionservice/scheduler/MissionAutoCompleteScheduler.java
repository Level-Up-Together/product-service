package io.pinkspider.leveluptogethermvp.missionservice.scheduler;

import io.pinkspider.leveluptogethermvp.missionservice.application.DailyMissionInstanceService;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionExecutionService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 미션 자동 종료 스케줄러
 *
 * 1. 목표시간 도달 미션: Saga 경유하여 경험치 정상 지급 후 자동 종료
 * 2. 2시간 초과 미션 (목표시간 미설정): 어뷰징 방지용 자동 종료
 * - 5분마다 실행
 * - MissionExecution (일반 미션)과 DailyMissionInstance (고정 미션) 모두 처리
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MissionAutoCompleteScheduler {

    private final MissionExecutionRepository executionRepository;
    private final DailyMissionInstanceRepository instanceRepository;
    private final DailyMissionInstanceService dailyMissionInstanceService;
    private final MissionExecutionService missionExecutionService;

    /**
     * 최대 미션 수행 시간 (분) - 2시간
     */
    private static final long MAXIMUM_EXECUTION_MINUTES = 120;

    /**
     * 5분마다 실행: 미션 자동 종료
     */
    @Scheduled(fixedRate = 300000) // 5분 = 300,000ms
    @Transactional(transactionManager = "missionTransactionManager")
    public void autoCompleteExpiredMissions() {
        log.debug("=== 미션 자동 종료 스케줄러 시작 ===");

        try {
            // 1. 목표시간 도달 인스턴스 자동 종료 (Saga 경유)
            int targetInstanceCount = autoCompleteTargetReachedInstances();

            // 2. 목표시간 도달 실행 자동 종료 (Saga 경유)
            int targetExecutionCount = autoCompleteTargetReachedExecutions();

            // 3. 2시간 초과 일반 미션 자동 종료 (목표시간 미설정)
            LocalDateTime expireThreshold = LocalDateTime.now().minusMinutes(MAXIMUM_EXECUTION_MINUTES);
            int executionCount = autoCompleteExpiredExecutions(expireThreshold);
            int instanceCount = autoCompleteExpiredInstances(expireThreshold);

            if (targetInstanceCount > 0 || targetExecutionCount > 0 || executionCount > 0 || instanceCount > 0) {
                log.info("미션 자동 종료 완료: targetInstances={}, targetExecutions={}, executions={}, instances={}",
                    targetInstanceCount, targetExecutionCount, executionCount, instanceCount);
            }
        } catch (Exception e) {
            log.error("미션 자동 종료 스케줄러 실패", e);
        }

        log.debug("=== 미션 자동 종료 스케줄러 종료 ===");
    }

    /**
     * 목표시간 도달 고정 미션 인스턴스 자동 종료 (Saga 경유)
     */
    private int autoCompleteTargetReachedInstances() {
        List<DailyMissionInstance> instances = instanceRepository.findInProgressWithTargetDuration();

        int count = 0;
        for (DailyMissionInstance instance : instances) {
            long elapsed = Duration.between(instance.getStartedAt(), LocalDateTime.now()).toMinutes();
            if (elapsed >= instance.getTargetDurationMinutes()) {
                String userId = instance.getParticipant().getUserId();
                try {
                    dailyMissionInstanceService.completeInstance(
                        instance.getId(), userId, null, false);
                    count++;
                    log.info("목표시간 도달 자동 종료 (고정): instanceId={}, target={}분",
                        instance.getId(), instance.getTargetDurationMinutes());
                } catch (Exception e) {
                    log.warn("목표시간 자동 종료 실패 (고정): instanceId={}, error={}",
                        instance.getId(), e.getMessage());
                }
            }
        }
        return count;
    }

    /**
     * 목표시간 도달 일반 미션 실행 자동 종료 (Saga 경유)
     */
    private int autoCompleteTargetReachedExecutions() {
        List<MissionExecution> executions = executionRepository.findInProgressWithTargetDuration();

        int count = 0;
        for (MissionExecution execution : executions) {
            long elapsed = Duration.between(execution.getStartedAt(), LocalDateTime.now()).toMinutes();
            Integer targetMinutes = execution.getParticipant().getMission().getTargetDurationMinutes();
            if (targetMinutes != null && elapsed >= targetMinutes) {
                String userId = execution.getParticipant().getUserId();
                try {
                    missionExecutionService.completeExecution(
                        execution.getId(), userId, null, false);
                    count++;
                    log.info("목표시간 도달 자동 종료 (일반): executionId={}, target={}분",
                        execution.getId(), targetMinutes);
                } catch (Exception e) {
                    log.warn("목표시간 자동 종료 실패 (일반): executionId={}, error={}",
                        execution.getId(), e.getMessage());
                }
            }
        }
        return count;
    }

    /**
     * 일반 미션 (MissionExecution) 2시간 초과 자동 종료
     * 목표시간 설정 미션은 위에서 Saga로 처리하므로 스킵
     */
    private int autoCompleteExpiredExecutions(LocalDateTime expireThreshold) {
        List<MissionExecution> expiredExecutions = executionRepository.findExpiredInProgressExecutions(expireThreshold);

        int count = 0;
        for (MissionExecution execution : expiredExecutions) {
            // 목표시간 설정 미션은 Saga로 처리하므로 스킵
            if (execution.getParticipant() != null
                && execution.getParticipant().getMission() != null
                && execution.getParticipant().getMission().getTargetDurationMinutes() != null) {
                continue;
            }

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
     * 고정 미션 (DailyMissionInstance) 2시간 초과 자동 종료
     * 목표시간 설정 미션은 DailyMissionInstance.autoCompleteIfExpired()에서 false 반환하여 자동 스킵
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
