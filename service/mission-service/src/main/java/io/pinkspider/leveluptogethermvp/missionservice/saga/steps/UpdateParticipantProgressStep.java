package io.pinkspider.leveluptogethermvp.missionservice.saga.steps;

import io.pinkspider.global.saga.SagaStep;
import io.pinkspider.global.saga.SagaStepResult;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
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

    @Override
    public String getName() {
        return "UpdateParticipantProgress";
    }

    @Override
    public Predicate<MissionCompletionContext> shouldExecute() {
        return ctx -> !ctx.isPinned();
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

            // 진행도 계산
            long totalExecutions = executionRepository.findByParticipantId(participant.getId()).size();
            long completedExecutions = executionRepository.countByParticipantIdAndStatus(
                participant.getId(), ExecutionStatus.COMPLETED);

            int progress = totalExecutions > 0
                ? (int) ((completedExecutions * 100) / totalExecutions)
                : 0;

            participant.updateProgress(progress);
            participantRepository.save(participant);

            log.info("Participant progress updated: participantId={}, progress={}%",
                participant.getId(), progress);

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
