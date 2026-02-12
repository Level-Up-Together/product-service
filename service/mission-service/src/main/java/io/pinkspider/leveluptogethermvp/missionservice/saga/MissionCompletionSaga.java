package io.pinkspider.leveluptogethermvp.missionservice.saga;

import io.pinkspider.global.saga.SagaEventPublisher;
import io.pinkspider.global.saga.SagaOrchestrator;
import io.pinkspider.global.saga.SagaResult;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.DailyMissionInstanceResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionExecutionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.CompleteExecutionStep;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.CompletePinnedInstanceStep;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.CreateFeedFromMissionStep;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.CreateNextPinnedInstanceStep;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.GrantGuildExperienceStep;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.GrantUserExperienceStep;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.LoadMissionDataStep;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.LoadPinnedMissionDataStep;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.UpdateParticipantProgressStep;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.UpdateUserStatsStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 미션 완료 통합 Saga (일반 미션 + 고정 미션)
 *
 * shouldExecute() 조건으로 일반/고정 미션 Step을 분기:
 *
 * 일반 미션 실행 순서:
 * 1. LoadMissionData - 미션 데이터 로드 및 검증
 * 2. CompleteExecution - 수행 기록 완료 처리
 * 3. GrantUserExperience - 사용자 경험치 지급
 * 4. GrantGuildExperience - 길드 경험치 지급 (길드 미션인 경우)
 * 5. UpdateParticipantProgress - 참가자 진행도 업데이트
 * 6. UpdateUserStats - 사용자 통계 및 업적 업데이트 (선택적)
 * 7. CreateFeedFromMission - 피드 생성 (사용자 선택시, 선택적)
 *
 * 고정 미션 실행 순서:
 * 1. LoadPinnedMissionData - 인스턴스 데이터 로드 및 검증
 * 2. CompletePinnedInstance - 인스턴스 완료 처리
 * 3. GrantUserExperience - 사용자 경험치 지급
 * 4. UpdateUserStats - 통계 및 업적 업데이트 (선택적)
 * 5. CreateFeedFromMission - 피드 생성 (사용자 선택시, 선택적)
 * 6. CreateNextPinnedInstance - 다음 수행용 새 인스턴스 생성
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MissionCompletionSaga {

    // Regular-only steps
    private final LoadMissionDataStep loadMissionDataStep;
    private final CompleteExecutionStep completeExecutionStep;
    private final UpdateParticipantProgressStep updateParticipantProgressStep;
    private final GrantGuildExperienceStep grantGuildExperienceStep;

    // Pinned-only steps
    private final LoadPinnedMissionDataStep loadPinnedMissionDataStep;
    private final CompletePinnedInstanceStep completePinnedInstanceStep;
    private final CreateNextPinnedInstanceStep createNextPinnedInstanceStep;

    // Unified steps (both regular and pinned)
    private final GrantUserExperienceStep grantUserExperienceStep;
    private final UpdateUserStatsStep updateUserStatsStep;
    private final CreateFeedFromMissionStep createFeedFromMissionStep;

    private final SagaEventPublisher sagaEventPublisher;

    /**
     * 일반 미션 완료 Saga 실행
     */
    public SagaResult<MissionCompletionContext> execute(Long executionId, String userId, String note) {
        return execute(executionId, userId, note, false);
    }

    /**
     * 일반 미션 완료 Saga 실행 (피드 공유 옵션 포함)
     */
    public SagaResult<MissionCompletionContext> execute(Long executionId, String userId, String note, boolean shareToFeed) {
        log.info("Starting MissionCompletionSaga (regular): executionId={}, userId={}, shareToFeed={}",
            executionId, userId, shareToFeed);

        MissionCompletionContext context = new MissionCompletionContext(executionId, userId, note, shareToFeed);
        return runSaga(context);
    }

    /**
     * 고정 미션 완료 Saga 실행
     */
    public SagaResult<MissionCompletionContext> executePinned(Long instanceId, String userId, String note) {
        return executePinned(instanceId, userId, note, false);
    }

    /**
     * 고정 미션 완료 Saga 실행 (피드 공유 옵션 포함)
     */
    public SagaResult<MissionCompletionContext> executePinned(Long instanceId, String userId, String note, boolean shareToFeed) {
        log.info("Starting MissionCompletionSaga (pinned): instanceId={}, userId={}, shareToFeed={}",
            instanceId, userId, shareToFeed);

        MissionCompletionContext context = MissionCompletionContext.forPinned(instanceId, userId, note, shareToFeed);
        return runSaga(context);
    }

    /**
     * Saga 실행 공통 로직
     *
     * 모든 Step을 등록하되, shouldExecute()로 조건부 실행
     * - Regular steps: shouldExecute = !isPinned
     * - Pinned steps: shouldExecute = isPinned
     * - Unified steps: shouldExecute = always (내부에서 isPinned 분기)
     */
    private SagaResult<MissionCompletionContext> runSaga(MissionCompletionContext context) {
        SagaOrchestrator<MissionCompletionContext> orchestrator =
            new SagaOrchestrator<>(sagaEventPublisher);

        // Step 등록 (순서 중요!)
        // 1. 데이터 로드 (regular 또는 pinned 중 하나만 실행)
        orchestrator
            .addStep(loadMissionDataStep)
            .addStep(loadPinnedMissionDataStep);

        // 2. 완료 처리 (regular 또는 pinned 중 하나만 실행)
        orchestrator
            .addStep(completeExecutionStep)
            .addStep(completePinnedInstanceStep);

        // 3. 경험치 지급
        orchestrator
            .addStep(grantUserExperienceStep)
            .addStep(grantGuildExperienceStep);

        // 4. 진행도/통계 업데이트
        orchestrator
            .addStep(updateParticipantProgressStep)
            .addStep(updateUserStatsStep);

        // 5. 피드 생성 + 다음 인스턴스 생성
        orchestrator
            .addStep(createFeedFromMissionStep)
            .addStep(createNextPinnedInstanceStep);

        SagaResult<MissionCompletionContext> result = orchestrator.execute(context);

        if (result.isSuccess()) {
            log.info("MissionCompletionSaga succeeded: sagaId={}, pinned={}",
                result.getSagaId(), context.isPinned());
        } else {
            log.error("MissionCompletionSaga failed: sagaId={}, pinned={}, reason={}",
                result.getSagaId(), context.isPinned(), result.getMessage());
        }

        return result;
    }

    /**
     * 일반 미션 Saga 결과에서 MissionExecutionResponse 추출
     */
    public MissionExecutionResponse toResponse(SagaResult<MissionCompletionContext> result) {
        if (result.isSuccess() && result.getContext().getExecution() != null) {
            return MissionExecutionResponse.from(result.getContext().getExecution());
        }
        return null;
    }

    /**
     * 고정 미션 Saga 결과에서 DailyMissionInstanceResponse 추출
     */
    public DailyMissionInstanceResponse toPinnedResponse(SagaResult<MissionCompletionContext> result) {
        if (result.isSuccess() && result.getContext().getInstance() != null) {
            return DailyMissionInstanceResponse.from(result.getContext().getInstance());
        }
        return null;
    }
}
