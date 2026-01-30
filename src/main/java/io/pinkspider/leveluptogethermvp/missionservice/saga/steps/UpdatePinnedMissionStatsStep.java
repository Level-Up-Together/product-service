package io.pinkspider.leveluptogethermvp.missionservice.saga.steps;

import io.pinkspider.global.saga.SagaStep;
import io.pinkspider.global.saga.SagaStepResult;
import io.pinkspider.leveluptogethermvp.missionservice.saga.PinnedMissionCompletionContext;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.AchievementService;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.UserStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step 4: 고정 미션 통계 및 업적 업데이트
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpdatePinnedMissionStatsStep implements SagaStep<PinnedMissionCompletionContext> {

    private final UserStatsService userStatsService;
    private final AchievementService achievementService;

    @Override
    public String getName() {
        return "UpdatePinnedMissionStats";
    }

    @Override
    public boolean isMandatory() {
        return false; // 실패해도 전체 Saga 실패 아님
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, transactionManager = "gamificationTransactionManager")
    public SagaStepResult execute(PinnedMissionCompletionContext context) {
        String userId = context.getUserId();

        log.debug("Updating pinned mission stats: userId={}", userId);

        try {
            // 통계 업데이트 (고정 미션은 isPinned=true)
            userStatsService.recordMissionCompletion(userId, true);

            // 업적 체크
            achievementService.checkAchievementsByDataSource(userId, "USER_STATS");

            log.info("Pinned mission stats updated: userId={}", userId);
            return SagaStepResult.success("통계 및 업적 업데이트 완료");

        } catch (Exception e) {
            log.warn("Failed to update pinned mission stats (optional step): userId={}, error={}",
                userId, e.getMessage());
            return SagaStepResult.success("통계 업데이트 스킵됨 (선택적 단계)");
        }
    }

    @Override
    public SagaStepResult compensate(PinnedMissionCompletionContext context) {
        // 선택적 단계이므로 보상 작업도 선택적
        log.debug("Compensating pinned mission stats (optional): userId={}", context.getUserId());
        return SagaStepResult.success("통계 보상 스킵됨 (선택적 단계)");
    }
}
