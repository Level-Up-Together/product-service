package io.pinkspider.leveluptogethermvp.missionservice.saga.steps;

import io.pinkspider.global.saga.SagaStep;
import io.pinkspider.global.saga.SagaStepResult;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import io.pinkspider.leveluptogethermvp.missionservice.saga.PinnedMissionCompletionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step 2: 고정 미션 인스턴스 완료 처리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompletePinnedInstanceStep implements SagaStep<PinnedMissionCompletionContext> {

    private final DailyMissionInstanceRepository instanceRepository;

    @Override
    public String getName() {
        return "CompletePinnedInstance";
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, transactionManager = "missionTransactionManager")
    public SagaStepResult execute(PinnedMissionCompletionContext context) {
        DailyMissionInstance instance = context.getInstance();

        if (instance == null) {
            return SagaStepResult.failure("Instance not loaded");
        }

        log.debug("Completing pinned instance: instanceId={}", instance.getId());

        try {
            // 완료 처리 (시간 기반 경험치 계산 포함)
            instance.complete();
            if (context.getNote() != null) {
                instance.setNote(context.getNote());
            }

            // 계산된 경험치를 context에 반영
            context.setExpEarned(instance.getExpEarned());

            // DB에 저장
            instanceRepository.save(instance);

            log.info("Pinned instance completed: instanceId={}, expEarned={}",
                instance.getId(), instance.getExpEarned());

            return SagaStepResult.success("고정 미션 인스턴스 완료 처리됨");

        } catch (Exception e) {
            log.error("Failed to complete pinned instance: {}", e.getMessage(), e);
            return SagaStepResult.failure(e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, transactionManager = "missionTransactionManager")
    public SagaStepResult compensate(PinnedMissionCompletionContext context) {
        DailyMissionInstance instance = context.getInstance();

        if (instance == null) {
            return SagaStepResult.success("Nothing to compensate");
        }

        log.debug("Compensating pinned instance completion: instanceId={}", instance.getId());

        try {
            // 이전 상태로 복원
            ExecutionStatus previousStatus = context.getCompensationData(
                PinnedMissionCompletionContext.CompensationKeys.INSTANCE_STATUS_BEFORE,
                ExecutionStatus.class);

            if (previousStatus != null) {
                instance.setStatus(previousStatus);
                instance.setCompletedAt(null);
                instance.setExpEarned(0);
                instance.setNote(null);
                instanceRepository.save(instance);
                log.info("Pinned instance compensated: instanceId={}, restoredStatus={}",
                    instance.getId(), previousStatus);
            }

            return SagaStepResult.success("고정 미션 인스턴스 완료 보상됨");

        } catch (Exception e) {
            log.error("Failed to compensate pinned instance: {}", e.getMessage(), e);
            return SagaStepResult.failure(e);
        }
    }
}
