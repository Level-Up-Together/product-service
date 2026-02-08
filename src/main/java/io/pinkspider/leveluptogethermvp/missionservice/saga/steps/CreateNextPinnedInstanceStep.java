package io.pinkspider.leveluptogethermvp.missionservice.saga.steps;

import io.pinkspider.global.saga.SagaStep;
import io.pinkspider.global.saga.SagaStepResult;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionContext;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Step 6: 다음 수행을 위한 새 인스턴스 생성
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreateNextPinnedInstanceStep implements SagaStep<MissionCompletionContext> {

    private final DailyMissionInstanceRepository instanceRepository;

    @Override
    public String getName() {
        return "CreateNextPinnedInstance";
    }

    @Override
    public Predicate<MissionCompletionContext> shouldExecute() {
        return MissionCompletionContext::isPinned;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, transactionManager = "missionTransactionManager")
    public SagaStepResult execute(MissionCompletionContext context) {
        MissionParticipant participant = context.getParticipant();
        LocalDate instanceDate = context.getInstanceDate();

        log.debug("Creating next pinned instance: participantId={}, date={}",
            participant.getId(), instanceDate);

        try {
            // 일일 수행 제한 체크
            Mission mission = participant.getMission();
            if (mission.getDailyExecutionLimit() != null) {
                long todayCompleted = instanceRepository
                    .countCompletedByParticipantIdAndDate(participant.getId(), instanceDate);
                if (todayCompleted >= mission.getDailyExecutionLimit()) {
                    log.info("Daily limit reached, skipping next instance creation: participantId={}, limit={}",
                        participant.getId(), mission.getDailyExecutionLimit());
                    return SagaStepResult.success("일일 제한 도달 - 인스턴스 생성 스킵");
                }
            }

            // 다음 시퀀스 번호 조회
            int nextSequence = instanceRepository.findMaxSequenceNumber(participant.getId(), instanceDate) + 1;

            // 새 인스턴스 생성
            DailyMissionInstance newInstance = DailyMissionInstance.createFrom(participant, instanceDate, nextSequence);
            instanceRepository.save(newInstance);

            context.setNextInstanceId(newInstance.getId());

            log.info("Next pinned instance created: instanceId={}, participantId={}, sequenceNumber={}",
                newInstance.getId(), participant.getId(), nextSequence);

            return SagaStepResult.success("다음 인스턴스 생성 완료", newInstance.getId());

        } catch (Exception e) {
            log.error("Failed to create next pinned instance: {}", e.getMessage(), e);
            return SagaStepResult.failure(e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, transactionManager = "missionTransactionManager")
    public SagaStepResult compensate(MissionCompletionContext context) {
        Long nextInstanceId = context.getNextInstanceId();
        if (nextInstanceId == null) {
            return SagaStepResult.success("삭제할 인스턴스 없음");
        }

        log.debug("Compensating next pinned instance: instanceId={}", nextInstanceId);

        try {
            instanceRepository.deleteById(nextInstanceId);
            log.info("Next pinned instance deleted: instanceId={}", nextInstanceId);
            return SagaStepResult.success("다음 인스턴스 삭제 완료");
        } catch (Exception e) {
            log.warn("Failed to delete next pinned instance: instanceId={}, error={}",
                nextInstanceId, e.getMessage());
            return SagaStepResult.failure("다음 인스턴스 삭제 실패", e);
        }
    }
}
