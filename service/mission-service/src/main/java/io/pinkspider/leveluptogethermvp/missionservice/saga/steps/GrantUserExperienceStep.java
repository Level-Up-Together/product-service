package io.pinkspider.leveluptogethermvp.missionservice.saga.steps;

import io.pinkspider.global.enums.ExpSourceType;
import io.pinkspider.global.facade.GamificationQueryFacade;
import io.pinkspider.global.facade.dto.UserExperienceDto;
import io.pinkspider.global.saga.SagaStep;
import io.pinkspider.global.saga.SagaStepResult;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 사용자 경험치 지급 (일반 미션 + 고정 미션 통합) */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrantUserExperienceStep implements SagaStep<MissionCompletionContext> {

    private final GamificationQueryFacade gamificationQueryFacadeService;

    @Override
    public String getName() {
        return "GrantUserExperience";
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SagaStepResult execute(MissionCompletionContext context) {
        String userId = context.getUserId();
        int expToGrant = context.getUserExpEarned();

        log.debug(
                "Granting user experience: userId={}, exp={}, pinned={}",
                userId,
                expToGrant,
                context.isPinned());

        try {
            // 현재 상태 저장 (보상용)
            UserExperienceDto currentExp =
                    gamificationQueryFacadeService.getOrCreateUserExperience(userId);
            context.addCompensationData(
                    MissionCompletionContext.CompensationKeys.USER_EXP_BEFORE,
                    currentExp.currentExp());
            context.addCompensationData(
                    MissionCompletionContext.CompensationKeys.USER_LEVEL_BEFORE,
                    currentExp.currentLevel());
            context.setUserLevelBefore(currentExp.currentLevel());

            // 카테고리 정보 및 설명 (일반/고정 분기)
            Long categoryId;
            String categoryName;
            String description;

            if (context.isPinned()) {
                categoryId = context.getCategoryId();
                categoryName = context.getCategoryName();
                description = "고정 미션 수행 완료: " + context.getMissionTitle();
            } else {
                categoryId = context.getMission().getCategoryId();
                categoryName = context.getMission().getCategoryName();
                description = "미션 수행 완료: " + context.getMission().getTitle();
            }

            // 경험치 지급
            UserExperienceDto granted =
                    gamificationQueryFacadeService.addExperience(
                            userId,
                            expToGrant,
                            ExpSourceType.MISSION_EXECUTION,
                            context.getMission().getId(),
                            description,
                            categoryId,
                            categoryName);
            // LUT-860: 누적 마이너스 경험치를 갚느라 덜 받은 양 — 완료 응답(exp_deducted)과 보상에 쓴다
            context.setExpDeducted(
                    granted != null && granted.expDeducted() != null ? granted.expDeducted() : 0);

            // 지급 후 레벨 확인
            UserExperienceDto afterExp =
                    gamificationQueryFacadeService.getOrCreateUserExperience(userId);
            context.setUserLevelAfter(afterExp.currentLevel());

            log.info(
                    "User experience granted: userId={}, exp={}, level: {} -> {}, pinned={}",
                    userId,
                    expToGrant,
                    context.getUserLevelBefore(),
                    context.getUserLevelAfter(),
                    context.isPinned());

            return SagaStepResult.success("사용자 경험치 지급 완료", expToGrant);

        } catch (Exception e) {
            log.error("Failed to grant user experience: {}", e.getMessage(), e);
            return SagaStepResult.failure(e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SagaStepResult compensate(MissionCompletionContext context) {
        String userId = context.getUserId();
        int expGranted = context.getUserExpEarned();

        log.debug("Compensating user experience: userId={}, exp={}", userId, expGranted);

        try {
            Long categoryId;
            String categoryName;
            String description;

            if (context.isPinned()) {
                categoryId = context.getCategoryId();
                categoryName = context.getCategoryName();
                description = "고정 미션 완료 보상 - 경험치 환수";
            } else {
                categoryId = context.getMission().getCategoryId();
                categoryName = context.getMission().getCategoryName();
                description = "미션 완료 보상 - 경험치 환수";
            }

            // LUT-860: 지급 때 갚은 누적 마이너스 경험치는 다시 마이너스로 돌려놓고 실제로 받은 양만 뺀다
            gamificationQueryFacadeService.subtractExperience(
                    userId,
                    expGranted,
                    context.getExpDeducted(),
                    ExpSourceType.MISSION_EXECUTION,
                    context.getMission().getId(),
                    description,
                    categoryId,
                    categoryName);

            log.info("User experience compensated: userId={}, exp={}", userId, expGranted);
            return SagaStepResult.success("사용자 경험치 환수 완료");

        } catch (Exception e) {
            log.error(
                    "Failed to compensate user experience: userId={}, error={}",
                    userId,
                    e.getMessage());
            return SagaStepResult.failure("경험치 환수 실패", e);
        }
    }
}
