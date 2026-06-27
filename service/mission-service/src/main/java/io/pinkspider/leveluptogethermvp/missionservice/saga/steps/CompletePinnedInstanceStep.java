package io.pinkspider.leveluptogethermvp.missionservice.saga.steps;

import io.pinkspider.global.saga.SagaStep;
import io.pinkspider.global.saga.SagaStepResult;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionExecutionMode;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionContext;
import java.time.Duration;
import java.util.function.Predicate;
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
public class CompletePinnedInstanceStep implements SagaStep<MissionCompletionContext> {

    private final DailyMissionInstanceRepository instanceRepository;
    private final MissionExecutionRepository executionRepository;

    @Override
    public String getName() {
        return "CompletePinnedInstance";
    }

    @Override
    public Predicate<MissionCompletionContext> shouldExecute() {
        return MissionCompletionContext::isPinned;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, transactionManager = "missionTransactionManager")
    public SagaStepResult execute(MissionCompletionContext context) {
        DailyMissionInstance instance = context.getInstance();

        if (instance == null) {
            return SagaStepResult.failure("Instance not loaded");
        }

        log.debug("Completing pinned instance: instanceId={}", instance.getId());

        try {
            // SIMPLE 모드: 일일 EXP 한도(10회) 도달 시 EXP=0으로 처리
            Mission mission = context.getMission();
            boolean awardSimpleExp = true;
            if (mission != null && mission.getExecutionMode() == MissionExecutionMode.SIMPLE
                && instance.getInstanceDate() != null) {
                long regular = executionRepository.countSimpleCompletedByUserIdAndDate(
                    context.getUserId(), instance.getInstanceDate());
                long pinned = instanceRepository.countSimpleCompletedByUserIdAndDate(
                    context.getUserId(), instance.getInstanceDate());
                if ((regular + pinned) >= MissionExecutionMode.SIMPLE_DAILY_LIMIT) {
                    awardSimpleExp = false;
                    context.setDailySimpleExpCapped(true);
                }
            }

            // 완료 처리 (시간 기반 경험치 계산 포함, SIMPLE은 awardSimpleExp 반영)
            instance.complete(awardSimpleExp);
            if (context.getNote() != null) {
                instance.setNote(context.getNote());
            }

            // SIMPLE 모드는 complete()에서 이미 고정 EXP(또는 한도 도달 시 0) 설정됨 — 오버라이드 불필요
            if (mission != null && mission.getExecutionMode() != MissionExecutionMode.SIMPLE) {
                // QA-212: 목표시간 미설정 고정 미션의 수동 완료는 complete() 의 시간 기반 EXP 를
                // 그대로 유지한다. baseExp 패널티는 스케줄러 자동종료 전용이어야 한다.
                // QA-153: 목표 시간 달성 여부 식별 → UpdateUserStatsStep 에서 클리어 미션북 카운트 증가
                if (instance.getTargetDurationMinutes() != null && instance.getTargetDurationMinutes() > 0
                    && instance.getStartedAt() != null && instance.getCompletedAt() != null) {
                    long elapsed = Duration.between(instance.getStartedAt(), instance.getCompletedAt()).toMinutes();
                    if (elapsed >= instance.getTargetDurationMinutes()) {
                        int bonus = instance.getBonusExpOnFullCompletion() != null
                            ? instance.getBonusExpOnFullCompletion() : 0;
                        context.setFullCompletionBonusGranted(true);
                        context.setFullCompletionBonusExp(bonus);
                    }
                }
            }

            // 계산된 경험치를 context에 반영
            context.setUserExpEarned(instance.getExpEarned());
            // QA-198: 고정 길드 미션도 일반 미션과 동일하게 시간 기반 EXP 를 길드 EXP 로 부여한다.
            // (CompleteExecutionStep QA-174 와 같은 정책)
            if (context.isGuildMission()) {
                context.setGuildExpEarned(instance.getExpEarned());
            }

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
    public SagaStepResult compensate(MissionCompletionContext context) {
        DailyMissionInstance instance = context.getInstance();

        if (instance == null) {
            return SagaStepResult.success("Nothing to compensate");
        }

        log.debug("Compensating pinned instance completion: instanceId={}", instance.getId());

        try {
            // 이전 상태로 복원
            ExecutionStatus previousStatus = context.getCompensationData(
                MissionCompletionContext.CompensationKeys.INSTANCE_STATUS_BEFORE,
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
