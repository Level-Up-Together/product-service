package io.pinkspider.leveluptogethermvp.missionservice.saga.steps;

import io.pinkspider.global.saga.SagaStep;
import io.pinkspider.global.saga.SagaStepResult;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionContext;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step 8: 참가자 진행도 업데이트
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateParticipantProgressStep implements SagaStep<MissionCompletionContext> {

    private final MissionExecutionRepository executionRepository;
    private final MissionParticipantRepository participantRepository;
    private final DailyMissionInstanceRepository dailyMissionInstanceRepository;

    @Override
    public String getName() {
        return "UpdateParticipantProgress";
    }

    @Override
    public Predicate<MissionCompletionContext> shouldExecute() {
        // QA-194: 고정(pinned) 미션도 한 번이라도 인스턴스 완료 시 참여자 progress 를 갱신해
        // 길드 미션 상세 UI에서 "참여" → "완료" 표시가 동작하도록 한다. 단, 고정 미션은
        // 매일 반복되므로 status 자체는 IN_PROGRESS 로 유지한다.
        return ctx -> true;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SagaStepResult execute(MissionCompletionContext context) {
        MissionParticipant participant = context.getParticipant();

        if (participant == null) {
            return SagaStepResult.failure("Participant not loaded");
        }

        log.debug("Updating participant progress: participantId={}", participant.getId());

        try {
            // 현재 상태 저장 (보상용)
            context.addCompensationData(
                MissionCompletionContext.CompensationKeys.PARTICIPANT_PROGRESS_BEFORE,
                participant.getProgress());
            context.addCompensationData(
                MissionCompletionContext.CompensationKeys.PARTICIPANT_STATUS_BEFORE,
                participant.getStatus());

            // QA-180: 일반 미션은 1회성이므로 COMPLETED execution 이 하나라도 있으면 100% 로 본다.
            // QA-194: 고정 미션은 DailyMissionInstance 의 완료 카운트도 합산해야 한다.
            long completedExecutions = executionRepository.countByParticipantIdAndStatus(
                participant.getId(), ExecutionStatus.COMPLETED);
            long completedPinnedInstances = dailyMissionInstanceRepository
                .countByParticipantIdAndStatus(participant.getId(), ExecutionStatus.COMPLETED);
            long totalCompleted = completedExecutions + completedPinnedInstances;

            int progress = totalCompleted > 0 ? 100 : 0;

            participant.updateProgress(progress);

            // 일반 미션만 진행도 100% 도달 시 참여 상태를 COMPLETED로 변경.
            // 고정 미션은 매일 반복되므로 status 는 IN_PROGRESS 로 유지해 "나의 미션"에 계속 노출되어야 한다.
            if (!context.isPinned()
                && progress >= 100
                && participant.getStatus() == ParticipantStatus.IN_PROGRESS) {
                participant.complete();
                log.info("Participant completed: participantId={}, missionId={}",
                    participant.getId(), participant.getMission().getId());
            }

            participantRepository.save(participant);

            log.info("Participant progress updated: participantId={}, progress={}%, status={}",
                participant.getId(), progress, participant.getStatus());

            return SagaStepResult.success("참가자 진행도 업데이트 완료", progress);

        } catch (Exception e) {
            log.error("Failed to update participant progress: {}", e.getMessage(), e);
            return SagaStepResult.failure(e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SagaStepResult compensate(MissionCompletionContext context) {
        MissionParticipant participant = context.getParticipant();

        if (participant == null) {
            return SagaStepResult.success("Nothing to compensate");
        }

        log.debug("Compensating participant progress: participantId={}", participant.getId());

        try {
            Integer previousProgress = context.getCompensationData(
                MissionCompletionContext.CompensationKeys.PARTICIPANT_PROGRESS_BEFORE,
                Integer.class);
            ParticipantStatus previousStatus = context.getCompensationData(
                MissionCompletionContext.CompensationKeys.PARTICIPANT_STATUS_BEFORE,
                ParticipantStatus.class);

            if (previousProgress != null) {
                participant.setProgress(previousProgress);
            }
            if (previousStatus != null) {
                participant.setStatus(previousStatus);
            }

            participantRepository.save(participant);
            log.info("Participant progress compensated: participantId={}", participant.getId());

            return SagaStepResult.success("참가자 진행도 복원 완료");

        } catch (Exception e) {
            log.error("Failed to compensate participant progress: error={}", e.getMessage());
            return SagaStepResult.failure("진행도 복원 실패", e);
        }
    }
}
