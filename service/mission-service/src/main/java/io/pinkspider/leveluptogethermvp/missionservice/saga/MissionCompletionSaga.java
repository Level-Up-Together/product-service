package io.pinkspider.leveluptogethermvp.missionservice.saga;

import io.pinkspider.global.event.MissionExecutionEndedEvent;
import io.pinkspider.global.event.MissionExecutionStartedEvent;
import io.pinkspider.global.facade.TowerQueryFacade;
import io.pinkspider.global.saga.SagaEventPublisher;
import io.pinkspider.global.saga.SagaOrchestrator;
import io.pinkspider.global.saga.SagaResult;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.FeedVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.DailyMissionInstanceResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionExecutionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.TowerSeatResponse;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.CompleteExecutionStep;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.CompletePinnedInstanceStep;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.CreateFeedFromMissionStep;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.CreateNextPinnedInstanceStep;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.GrantDailyMilestoneBonusStep;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.GrantGuildExperienceStep;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.GrantMissionBookDiamondStep;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.GrantUserExperienceStep;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.LoadMissionDataStep;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.LoadPinnedMissionDataStep;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.UpdateParticipantProgressStep;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.UpdateUserStatsStep;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * 미션 완료 통합 Saga (일반 미션 + 고정 미션)
 *
 * <p>shouldExecute() 조건으로 일반/고정 미션 Step을 분기:
 *
 * <p>일반 미션 실행 순서: 1. LoadMissionData - 미션 데이터 로드 및 검증 2. CompleteExecution - 수행 기록 완료 처리 3.
 * GrantUserExperience - 사용자 경험치 지급 (+ GrantDailyMilestoneBonus: 체크 일일 마일스톤 도달 시, LUT-568) 4.
 * GrantGuildExperience - 길드 경험치 지급 (길드 미션인 경우) 5. UpdateParticipantProgress - 참가자 진행도 업데이트 6.
 * UpdateUserStats - 사용자 통계 및 업적 업데이트 (선택적) 7. CreateFeedFromMission - 피드 생성 (사용자 선택시, 선택적)
 *
 * <p>고정 미션 실행 순서 (QA-194 이후): 1. LoadPinnedMissionData - 인스턴스 데이터 로드 및 검증 2. CompletePinnedInstance
 * - 인스턴스 완료 처리 3. GrantUserExperience - 사용자 경험치 지급 4. GrantGuildExperience - 길드 경험치 지급 (고정 길드 미션
 * 포함) 5. UpdateParticipantProgress - 참가자 progress 갱신 (status 는 IN_PROGRESS 유지) 6. UpdateUserStats -
 * 통계 및 업적 업데이트 (선택적) 7. CreateFeedFromMission - 피드 생성 (사용자 선택시, 선택적) 8. CreateNextPinnedInstance -
 * 다음 수행용 새 인스턴스 생성
 */
@Slf4j
@Component
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
    private final GrantDailyMilestoneBonusStep grantDailyMilestoneBonusStep;
    private final GrantMissionBookDiamondStep grantMissionBookDiamondStep;
    private final UpdateUserStatsStep updateUserStatsStep;
    private final CreateFeedFromMissionStep createFeedFromMissionStep;

    private final SagaEventPublisher sagaEventPublisher;
    private final ApplicationEventPublisher eventPublisher;
    private final TowerQueryFacade towerQueryFacade;
    private final Executor sagaTailExecutor;

    public MissionCompletionSaga(
            LoadMissionDataStep loadMissionDataStep,
            CompleteExecutionStep completeExecutionStep,
            UpdateParticipantProgressStep updateParticipantProgressStep,
            GrantGuildExperienceStep grantGuildExperienceStep,
            LoadPinnedMissionDataStep loadPinnedMissionDataStep,
            CompletePinnedInstanceStep completePinnedInstanceStep,
            CreateNextPinnedInstanceStep createNextPinnedInstanceStep,
            GrantUserExperienceStep grantUserExperienceStep,
            GrantDailyMilestoneBonusStep grantDailyMilestoneBonusStep,
            GrantMissionBookDiamondStep grantMissionBookDiamondStep,
            UpdateUserStatsStep updateUserStatsStep,
            CreateFeedFromMissionStep createFeedFromMissionStep,
            SagaEventPublisher sagaEventPublisher,
            ApplicationEventPublisher eventPublisher,
            @Lazy TowerQueryFacade towerQueryFacade,
            @Qualifier("generalExecutor") Executor sagaTailExecutor) {
        this.loadMissionDataStep = loadMissionDataStep;
        this.completeExecutionStep = completeExecutionStep;
        this.updateParticipantProgressStep = updateParticipantProgressStep;
        this.grantGuildExperienceStep = grantGuildExperienceStep;
        this.loadPinnedMissionDataStep = loadPinnedMissionDataStep;
        this.completePinnedInstanceStep = completePinnedInstanceStep;
        this.createNextPinnedInstanceStep = createNextPinnedInstanceStep;
        this.grantUserExperienceStep = grantUserExperienceStep;
        this.grantDailyMilestoneBonusStep = grantDailyMilestoneBonusStep;
        this.grantMissionBookDiamondStep = grantMissionBookDiamondStep;
        this.updateUserStatsStep = updateUserStatsStep;
        this.createFeedFromMissionStep = createFeedFromMissionStep;
        this.sagaEventPublisher = sagaEventPublisher;
        this.eventPublisher = eventPublisher;
        this.towerQueryFacade = towerQueryFacade;
        this.sagaTailExecutor = sagaTailExecutor;
    }

    /** 일반 미션 완료 Saga 실행 */
    public SagaResult<MissionCompletionContext> execute(
            Long executionId, String userId, String note) {
        return execute(executionId, userId, note, false);
    }

    /** 일반 미션 완료 Saga 실행 (피드 공유 옵션 포함) */
    public SagaResult<MissionCompletionContext> execute(
            Long executionId, String userId, String note, boolean shareToFeed) {
        log.info(
                "Starting MissionCompletionSaga (regular): executionId={}, userId={},"
                        + " shareToFeed={}",
                executionId,
                userId,
                shareToFeed);

        MissionCompletionContext context =
                new MissionCompletionContext(executionId, userId, note, shareToFeed);
        return runSaga(context);
    }

    /** 일반 미션 완료 Saga 실행 (피드 공개범위 지정) */
    public SagaResult<MissionCompletionContext> execute(
            Long executionId, String userId, String note, FeedVisibility feedVisibility) {
        log.info(
                "Starting MissionCompletionSaga (regular): executionId={}, userId={},"
                        + " feedVisibility={}",
                executionId,
                userId,
                feedVisibility);

        MissionCompletionContext context =
                new MissionCompletionContext(executionId, userId, note, feedVisibility);
        return runSaga(context);
    }

    /** 고정 미션 완료 Saga 실행 */
    public SagaResult<MissionCompletionContext> executePinned(
            Long instanceId, String userId, String note) {
        return executePinned(instanceId, userId, note, false);
    }

    /** 고정 미션 완료 Saga 실행 (피드 공유 옵션 포함) */
    public SagaResult<MissionCompletionContext> executePinned(
            Long instanceId, String userId, String note, boolean shareToFeed) {
        log.info(
                "Starting MissionCompletionSaga (pinned): instanceId={}, userId={}, shareToFeed={}",
                instanceId,
                userId,
                shareToFeed);

        MissionCompletionContext context =
                MissionCompletionContext.forPinned(instanceId, userId, note, shareToFeed);
        return runSaga(context);
    }

    /** 고정 미션 완료 Saga 실행 (피드 공개범위 지정) */
    public SagaResult<MissionCompletionContext> executePinned(
            Long instanceId, String userId, String note, FeedVisibility feedVisibility) {
        log.info(
                "Starting MissionCompletionSaga (pinned): instanceId={}, userId={},"
                        + " feedVisibility={}",
                instanceId,
                userId,
                feedVisibility);

        MissionCompletionContext context =
                MissionCompletionContext.forPinned(instanceId, userId, note, feedVisibility);
        return runSaga(context);
    }

    /**
     * Saga 실행 공통 로직
     *
     * <p>모든 Step을 등록하되, shouldExecute()로 조건부 실행 - Regular steps: shouldExecute = !isPinned - Pinned
     * steps: shouldExecute = isPinned - Unified steps: shouldExecute = always (내부에서 isPinned 분기)
     */
    private SagaResult<MissionCompletionContext> runSaga(MissionCompletionContext context) {
        SagaOrchestrator<MissionCompletionContext> orchestrator =
                new SagaOrchestrator<>(sagaEventPublisher);

        // Step 등록 (순서 중요!)
        // 동기 구간: 응답 데이터를 만들거나(mandatory) 실패 시 보상이 필요한 Step만 실행.
        // 비필수 후속 Step(다이아·통계/업적·피드)은 runTailStepsAsync 로 응답 이후 실행.
        // 1. 데이터 로드 (regular 또는 pinned 중 하나만 실행)
        orchestrator.addStep(loadMissionDataStep).addStep(loadPinnedMissionDataStep);

        // 2. 완료 처리 (regular 또는 pinned 중 하나만 실행)
        orchestrator.addStep(completeExecutionStep).addStep(completePinnedInstanceStep);

        // 3. 경험치 지급 (LUT-568: 체크 일일 마일스톤 보너스는 도달 시에만 실행)
        orchestrator
                .addStep(grantUserExperienceStep)
                .addStep(grantDailyMilestoneBonusStep)
                .addStep(grantGuildExperienceStep);

        // 4. 진행도 업데이트 + 다음 인스턴스 생성
        orchestrator.addStep(updateParticipantProgressStep).addStep(createNextPinnedInstanceStep);

        SagaResult<MissionCompletionContext> result = orchestrator.execute(context);

        if (result.isSuccess()) {
            log.info(
                    "MissionCompletionSaga succeeded: sagaId={}, pinned={}",
                    result.getSagaId(),
                    context.isPinned());
            publishExecutionEnded(context);
            runTailStepsAsync(context);
        } else {
            log.error(
                    "MissionCompletionSaga failed: sagaId={}, pinned={}, reason={}",
                    result.getSagaId(),
                    context.isPinned(),
                    result.getMessage());
        }

        return result;
    }

    /**
     * 비필수 후속 Step 비동기 실행 (다이아 지급 QA-220 → 통계/업적 → 피드 생성)
     *
     * <p>세 Step 모두 isMandatory=false 라 기존에도 실패가 완료를 롤백하지 않았으므로, 응답 이후로 미뤄도 실패 의미는 동일하다(로그만 남김). 응답은
     * 동기 구간에서 채워진 context 데이터만 사용한다. 이벤트 발행은 동기 오케스트레이터가 이미 수행했으므로 tail 은 publisher 없이 실행한다.
     */
    private void runTailStepsAsync(MissionCompletionContext context) {
        sagaTailExecutor.execute(
                () -> {
                    try {
                        SagaOrchestrator<MissionCompletionContext> tailOrchestrator =
                                new SagaOrchestrator<>(null);
                        tailOrchestrator
                                .addStep(grantMissionBookDiamondStep)
                                .addStep(updateUserStatsStep)
                                .addStep(createFeedFromMissionStep);

                        SagaResult<MissionCompletionContext> tailResult =
                                tailOrchestrator.execute(context);
                        if (!tailResult.isSuccess()) {
                            log.warn(
                                    "MissionCompletionSaga tail failed: sagaId={}, pinned={},"
                                            + " reason={}",
                                    tailResult.getSagaId(),
                                    context.isPinned(),
                                    tailResult.getMessage());
                        }
                    } catch (Exception e) {
                        log.error(
                                "MissionCompletionSaga tail execution error: sagaId={}, error={}",
                                context.getSagaId(),
                                e.getMessage(),
                                e);
                    }
                });
    }

    /**
     * LUT-632: 모든 완료 경로(유저 완료·목표 시간 자동 종료)의 단일 지점에서 수행 종료 이벤트를 낸다 — 탑이 자리를 정리하고 완료 연출을 연다. 발행 실패는
     * 완료를 막지 않는다.
     */
    private void publishExecutionEnded(MissionCompletionContext context) {
        try {
            Long missionId = context.getMission() != null ? context.getMission().getId() : null;
            eventPublisher.publishEvent(
                    new MissionExecutionEndedEvent(
                            context.getUserId(),
                            missionId,
                            context.isPinned() ? context.getInstanceId() : context.getExecutionId(),
                            context.isPinned()
                                    ? MissionExecutionStartedEvent.KIND_PINNED
                                    : MissionExecutionStartedEvent.KIND_REGULAR,
                            MissionExecutionEndedEvent.EndReason.COMPLETED));
        } catch (Exception e) {
            log.warn(
                    "미션 수행 종료 이벤트 발행 실패: userId={}, error={}", context.getUserId(), e.getMessage());
        }
    }

    /** LUT-632: 완료 응답에 탑 자리(층·자동 착석·응원 수)를 싣는다 — 조회 실패는 null */
    private TowerSeatResponse resolveTowerSeat(MissionCompletionContext context) {
        try {
            Long missionId = context.getMission() != null ? context.getMission().getId() : null;
            return towerQueryFacade
                    .findMissionSeat(
                            context.getUserId(),
                            missionId,
                            context.isPinned() ? context.getInstanceId() : context.getExecutionId(),
                            context.isPinned()
                                    ? MissionExecutionStartedEvent.KIND_PINNED
                                    : MissionExecutionStartedEvent.KIND_REGULAR)
                    .map(TowerSeatResponse::from)
                    .orElse(null);
        } catch (Exception e) {
            log.warn(
                    "탑 자리 조회 실패 — 완료 응답에서 생략: userId={}, error={}",
                    context.getUserId(),
                    e.getMessage());
            return null;
        }
    }

    /** 일반 미션 Saga 결과에서 MissionExecutionResponse 추출 */
    public MissionExecutionResponse toResponse(SagaResult<MissionCompletionContext> result) {
        if (result.isSuccess() && result.getContext().getExecution() != null) {
            MissionCompletionContext ctx = result.getContext();
            MissionExecutionResponse response = MissionExecutionResponse.from(ctx.getExecution());
            response.setDailySimpleExpCapped(ctx.isDailySimpleExpCapped());
            response.setTowerSeat(resolveTowerSeat(ctx));
            if (ctx.getMilestoneBonusExp() > 0) {
                response.setMilestoneBonusExp(ctx.getMilestoneBonusExp());
                response.setMilestoneCount(ctx.getMilestoneCount());
            }
            return response;
        }
        return null;
    }

    /** 고정 미션 Saga 결과에서 DailyMissionInstanceResponse 추출 */
    public DailyMissionInstanceResponse toPinnedResponse(
            SagaResult<MissionCompletionContext> result) {
        if (result.isSuccess() && result.getContext().getInstance() != null) {
            MissionCompletionContext ctx = result.getContext();
            DailyMissionInstanceResponse response =
                    DailyMissionInstanceResponse.from(ctx.getInstance());
            response.setDailySimpleExpCapped(ctx.isDailySimpleExpCapped());
            response.setTowerSeat(resolveTowerSeat(ctx));
            if (ctx.getMilestoneBonusExp() > 0) {
                response.setMilestoneBonusExp(ctx.getMilestoneBonusExp());
                response.setMilestoneCount(ctx.getMilestoneCount());
            }
            return response;
        }
        return null;
    }
}
