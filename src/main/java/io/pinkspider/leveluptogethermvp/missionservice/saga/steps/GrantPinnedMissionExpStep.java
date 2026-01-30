package io.pinkspider.leveluptogethermvp.missionservice.saga.steps;

import io.pinkspider.global.saga.SagaStep;
import io.pinkspider.global.saga.SagaStepResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserExperience;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.ExpSourceType;
import io.pinkspider.leveluptogethermvp.missionservice.saga.PinnedMissionCompletionContext;
import io.pinkspider.leveluptogethermvp.userservice.experience.application.UserExperienceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step 3: 고정 미션 완료 경험치 지급
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrantPinnedMissionExpStep implements SagaStep<PinnedMissionCompletionContext> {

    private final UserExperienceService userExperienceService;

    @Override
    public String getName() {
        return "GrantPinnedMissionExp";
    }

    @Override
    public int getMaxRetries() {
        return 2;
    }

    @Override
    public long getRetryDelayMs() {
        return 500L;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, transactionManager = "gamificationTransactionManager")
    public SagaStepResult execute(PinnedMissionCompletionContext context) {
        String userId = context.getUserId();
        int expToGrant = context.getExpEarned();

        log.debug("Granting pinned mission experience: userId={}, exp={}", userId, expToGrant);

        try {
            // 현재 상태 저장 (보상용)
            UserExperience currentExp = userExperienceService.getOrCreateUserExperience(userId);
            context.addCompensationData(
                PinnedMissionCompletionContext.CompensationKeys.USER_EXP_BEFORE,
                currentExp.getCurrentExp());
            context.addCompensationData(
                PinnedMissionCompletionContext.CompensationKeys.USER_LEVEL_BEFORE,
                currentExp.getCurrentLevel());
            context.setUserLevelBefore(currentExp.getCurrentLevel());

            // 경험치 지급
            userExperienceService.addExperience(
                userId,
                expToGrant,
                ExpSourceType.MISSION_EXECUTION,
                context.getMission().getId(),
                "고정 미션 수행 완료: " + context.getMissionTitle(),
                context.getCategoryId(),
                context.getCategoryName()
            );

            // 지급 후 레벨 확인
            UserExperience afterExp = userExperienceService.getOrCreateUserExperience(userId);
            context.setUserLevelAfter(afterExp.getCurrentLevel());

            log.info("Pinned mission experience granted: userId={}, exp={}, level: {} -> {}",
                userId, expToGrant, context.getUserLevelBefore(), context.getUserLevelAfter());

            return SagaStepResult.success("고정 미션 경험치 지급 완료", expToGrant);

        } catch (Exception e) {
            log.error("Failed to grant pinned mission experience: {}", e.getMessage(), e);
            return SagaStepResult.failure(e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, transactionManager = "gamificationTransactionManager")
    public SagaStepResult compensate(PinnedMissionCompletionContext context) {
        String userId = context.getUserId();
        int expGranted = context.getExpEarned();

        log.debug("Compensating pinned mission experience: userId={}, exp={}", userId, expGranted);

        try {
            // 지급한 경험치 차감
            userExperienceService.subtractExperience(
                userId,
                expGranted,
                ExpSourceType.MISSION_EXECUTION,
                context.getMission().getId(),
                "고정 미션 완료 보상 - 경험치 환수",
                context.getCategoryId(),
                context.getCategoryName()
            );

            log.info("Pinned mission experience compensated: userId={}, exp={}", userId, expGranted);
            return SagaStepResult.success("고정 미션 경험치 환수 완료");

        } catch (Exception e) {
            log.error("Failed to compensate pinned mission experience: userId={}, error={}",
                userId, e.getMessage());
            return SagaStepResult.failure("경험치 환수 실패", e);
        }
    }
}
