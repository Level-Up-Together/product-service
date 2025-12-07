package io.pinkspider.leveluptogethermvp.missionservice.saga;

import io.pinkspider.global.saga.SagaEventPublisher;
import io.pinkspider.global.saga.SagaOrchestrator;
import io.pinkspider.global.saga.SagaResult;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionExecutionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.CompleteExecutionStep;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.GrantGuildExperienceStep;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.GrantUserExperienceStep;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.LoadMissionDataStep;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.SendNotificationStep;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.UpdateParticipantProgressStep;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.UpdateQuestProgressStep;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.UpdateUserStatsStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 미션 완료 Saga
 *
 * 미션 수행 완료 시 여러 서비스에 걸친 트랜잭션을 조율
 *
 * 실행 순서:
 * 1. LoadMissionData - 미션 데이터 로드 및 검증
 * 2. CompleteExecution - 수행 기록 완료 처리
 * 3. GrantUserExperience - 사용자 경험치 지급
 * 4. GrantGuildExperience - 길드 경험치 지급 (길드 미션인 경우)
 * 5. UpdateParticipantProgress - 참가자 진행도 업데이트
 * 6. UpdateUserStats - 사용자 통계 및 업적 업데이트 (선택적)
 * 7. UpdateQuestProgress - 퀘스트 진행도 업데이트 (선택적)
 * 8. SendNotification - 알림 발송 (선택적)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MissionCompletionSaga {

    private final LoadMissionDataStep loadMissionDataStep;
    private final CompleteExecutionStep completeExecutionStep;
    private final GrantUserExperienceStep grantUserExperienceStep;
    private final GrantGuildExperienceStep grantGuildExperienceStep;
    private final UpdateParticipantProgressStep updateParticipantProgressStep;
    private final UpdateUserStatsStep updateUserStatsStep;
    private final UpdateQuestProgressStep updateQuestProgressStep;
    private final SendNotificationStep sendNotificationStep;
    private final SagaEventPublisher sagaEventPublisher;

    /**
     * 미션 완료 Saga 실행
     *
     * @param executionId 수행 기록 ID
     * @param userId 사용자 ID
     * @param note 메모
     * @return Saga 실행 결과
     */
    public SagaResult<MissionCompletionContext> execute(Long executionId, String userId, String note) {
        log.info("Starting MissionCompletionSaga: executionId={}, userId={}", executionId, userId);

        // 컨텍스트 생성
        MissionCompletionContext context = new MissionCompletionContext(executionId, userId, note);

        // Saga Orchestrator 구성
        SagaOrchestrator<MissionCompletionContext> orchestrator =
            new SagaOrchestrator<>(sagaEventPublisher);

        // Step 등록 (순서 중요!)
        orchestrator
            .addStep(loadMissionDataStep)        // 1. 데이터 로드
            .addStep(completeExecutionStep)      // 2. 수행 완료
            .addStep(grantUserExperienceStep)    // 3. 사용자 경험치
            .addStep(grantGuildExperienceStep)   // 4. 길드 경험치 (조건부)
            .addStep(updateParticipantProgressStep) // 5. 참가자 진행도
            .addStep(updateUserStatsStep)        // 6. 통계/업적 (선택적)
            .addStep(updateQuestProgressStep)    // 7. 퀘스트 (선택적)
            .addStep(sendNotificationStep);      // 8. 알림 (선택적)

        // Saga 실행
        SagaResult<MissionCompletionContext> result = orchestrator.execute(context);

        if (result.isSuccess()) {
            log.info("MissionCompletionSaga succeeded: sagaId={}, executionId={}",
                result.getSagaId(), executionId);
        } else {
            log.error("MissionCompletionSaga failed: sagaId={}, executionId={}, reason={}",
                result.getSagaId(), executionId, result.getMessage());
        }

        return result;
    }

    /**
     * Saga 결과에서 MissionExecutionResponse 추출
     */
    public MissionExecutionResponse toResponse(SagaResult<MissionCompletionContext> result) {
        if (result.isSuccess() && result.getContext().getExecution() != null) {
            return MissionExecutionResponse.from(result.getContext().getExecution());
        }
        return null;
    }
}
