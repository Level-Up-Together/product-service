package io.pinkspider.leveluptogethermvp.missionservice.saga.steps;

import io.pinkspider.global.saga.SagaStep;
import io.pinkspider.global.saga.SagaStepResult;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionContext;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step 1: 미션 데이터 로드 및 검증
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoadMissionDataStep implements SagaStep<MissionCompletionContext> {

    private final MissionExecutionRepository executionRepository;

    @Override
    public String getName() {
        return "LoadMissionData";
    }

    @Override
    public Predicate<MissionCompletionContext> shouldExecute() {
        return ctx -> !ctx.isPinned();
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public SagaStepResult execute(MissionCompletionContext context) {
        log.debug("Loading mission data for executionId: {}", context.getExecutionId());

        try {
            // 수행 기록 조회 (Participant, Mission 함께 FETCH JOIN으로 로드)
            MissionExecution execution = executionRepository.findByIdWithParticipantAndMission(context.getExecutionId())
                .orElse(null);

            if (execution == null) {
                return SagaStepResult.failure("수행 기록을 찾을 수 없습니다: " + context.getExecutionId());
            }

            // 소유권 검증
            if (!execution.getParticipant().getUserId().equals(context.getUserId())) {
                return SagaStepResult.failure("본인의 수행 기록만 완료할 수 있습니다.");
            }

            // 상태 검증 (중복 완료 요청 방지)
            if (execution.getStatus() == ExecutionStatus.COMPLETED) {
                return SagaStepResult.failure("이미 완료된 수행 기록입니다.");
            }
            if (execution.getStatus() == ExecutionStatus.MISSED) {
                return SagaStepResult.failure("미실행 처리된 수행 기록은 완료할 수 없습니다.");
            }
            if (execution.getStatus() != ExecutionStatus.IN_PROGRESS) {
                return SagaStepResult.failure("진행 중인 수행 기록만 완료할 수 있습니다. 현재 상태: " + execution.getStatus());
            }

            MissionParticipant participant = execution.getParticipant();
            Mission mission = participant.getMission();

            // 컨텍스트에 데이터 설정
            context.setExecution(execution);
            context.setParticipant(participant);
            context.setMission(mission);

            // 경험치 계산
            int userExp = mission.getExpPerCompletion() != null ? mission.getExpPerCompletion() : 10;
            context.setUserExpEarned(userExp);

            if (mission.isGuildMission() && mission.getGuildIdAsLong() != null) {
                context.setGuildId(mission.getGuildIdAsLong());
                int guildExp = mission.getGuildExpPerCompletion() != null ? mission.getGuildExpPerCompletion() : 5;
                context.setGuildExpEarned(guildExp);
            }

            // 보상을 위한 현재 상태 저장
            context.addCompensationData(
                MissionCompletionContext.CompensationKeys.EXECUTION_STATUS_BEFORE,
                execution.getStatus());
            context.addCompensationData(
                MissionCompletionContext.CompensationKeys.PARTICIPANT_PROGRESS_BEFORE,
                participant.getProgress());

            log.debug("Mission data loaded: missionId={}, userExp={}, guildExp={}",
                mission.getId(), userExp, context.getGuildExpEarned());

            return SagaStepResult.success("미션 데이터 로드 완료");

        } catch (Exception e) {
            log.error("Failed to load mission data: {}", e.getMessage(), e);
            return SagaStepResult.failure(e);
        }
    }

    @Override
    public SagaStepResult compensate(MissionCompletionContext context) {
        // 데이터 로드는 읽기 전용이므로 보상 불필요
        log.debug("LoadMissionData compensation - no action needed");
        return SagaStepResult.success();
    }
}
