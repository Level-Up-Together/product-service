package io.pinkspider.leveluptogethermvp.missionservice.saga;

import io.pinkspider.global.saga.SagaEventPublisher;
import io.pinkspider.global.saga.SagaOrchestrator;
import io.pinkspider.global.saga.SagaResult;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.DailyMissionInstanceResponse;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.CompletePinnedInstanceStep;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.CreateNextPinnedInstanceStep;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.CreatePinnedMissionFeedStep;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.GrantPinnedMissionExpStep;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.LoadPinnedMissionDataStep;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.UpdatePinnedMissionStatsStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 고정 미션 완료 Saga
 *
 * 고정 미션(DailyMissionInstance) 수행 완료 시 여러 서비스에 걸친 트랜잭션을 조율
 *
 * 실행 순서:
 * 1. LoadPinnedMissionData - 인스턴스 데이터 로드 및 검증
 * 2. CompletePinnedInstance - 인스턴스 완료 처리
 * 3. GrantPinnedMissionExp - 사용자 경험치 지급
 * 4. UpdatePinnedMissionStats - 통계 및 업적 업데이트 (선택적)
 * 5. CreatePinnedMissionFeed - 피드 생성 (사용자 선택시, 선택적)
 * 6. CreateNextPinnedInstance - 다음 수행용 새 인스턴스 생성
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PinnedMissionCompletionSaga {

    private final LoadPinnedMissionDataStep loadPinnedMissionDataStep;
    private final CompletePinnedInstanceStep completePinnedInstanceStep;
    private final GrantPinnedMissionExpStep grantPinnedMissionExpStep;
    private final UpdatePinnedMissionStatsStep updatePinnedMissionStatsStep;
    private final CreatePinnedMissionFeedStep createPinnedMissionFeedStep;
    private final CreateNextPinnedInstanceStep createNextPinnedInstanceStep;
    private final SagaEventPublisher sagaEventPublisher;

    /**
     * 고정 미션 완료 Saga 실행
     *
     * @param instanceId 인스턴스 ID
     * @param userId 사용자 ID
     * @param note 메모
     * @return Saga 실행 결과
     */
    public SagaResult<PinnedMissionCompletionContext> execute(Long instanceId, String userId, String note) {
        return execute(instanceId, userId, note, false);
    }

    /**
     * 고정 미션 완료 Saga 실행 (피드 공유 옵션 포함)
     *
     * @param instanceId 인스턴스 ID
     * @param userId 사용자 ID
     * @param note 메모
     * @param shareToFeed 피드 공유 여부
     * @return Saga 실행 결과
     */
    public SagaResult<PinnedMissionCompletionContext> execute(Long instanceId, String userId, String note, boolean shareToFeed) {
        log.info("Starting PinnedMissionCompletionSaga: instanceId={}, userId={}, shareToFeed={}",
            instanceId, userId, shareToFeed);

        // 컨텍스트 생성
        PinnedMissionCompletionContext context = new PinnedMissionCompletionContext(instanceId, userId, note, shareToFeed);

        // Saga Orchestrator 구성
        SagaOrchestrator<PinnedMissionCompletionContext> orchestrator =
            new SagaOrchestrator<>(sagaEventPublisher);

        // Step 등록 (순서 중요!)
        orchestrator
            .addStep(loadPinnedMissionDataStep)       // 1. 데이터 로드
            .addStep(completePinnedInstanceStep)      // 2. 인스턴스 완료
            .addStep(grantPinnedMissionExpStep)       // 3. 경험치 지급
            .addStep(updatePinnedMissionStatsStep)    // 4. 통계/업적 (선택적)
            .addStep(createPinnedMissionFeedStep)     // 5. 피드 생성 (선택적)
            .addStep(createNextPinnedInstanceStep);   // 6. 다음 인스턴스 생성

        // Saga 실행
        SagaResult<PinnedMissionCompletionContext> result = orchestrator.execute(context);

        if (result.isSuccess()) {
            log.info("PinnedMissionCompletionSaga succeeded: sagaId={}, instanceId={}, expEarned={}",
                result.getSagaId(), instanceId, context.getExpEarned());
        } else {
            log.error("PinnedMissionCompletionSaga failed: sagaId={}, instanceId={}, reason={}",
                result.getSagaId(), instanceId, result.getMessage());
        }

        return result;
    }

    /**
     * Saga 결과에서 DailyMissionInstanceResponse 추출
     */
    public DailyMissionInstanceResponse toResponse(SagaResult<PinnedMissionCompletionContext> result) {
        if (result.isSuccess() && result.getContext().getInstance() != null) {
            return DailyMissionInstanceResponse.from(result.getContext().getInstance());
        }
        return null;
    }
}
