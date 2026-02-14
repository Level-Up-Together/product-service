package io.pinkspider.leveluptogethermvp.missionservice.saga.steps;

import io.pinkspider.global.saga.SagaStep;
import io.pinkspider.global.saga.SagaStepResult;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildQueryFacadeService;
import io.pinkspider.global.enums.GuildExpSourceType;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionContext;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step 4: 길드 경험치 지급 (길드 미션인 경우에만)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrantGuildExperienceStep implements SagaStep<MissionCompletionContext> {

    private final GuildQueryFacadeService guildQueryFacadeService;

    @Override
    public String getName() {
        return "GrantGuildExperience";
    }

    @Override
    public int getMaxRetries() {
        return 2; // 2회 재시도
    }

    @Override
    public long getRetryDelayMs() {
        return 500L;
    }

    @Override
    public Predicate<MissionCompletionContext> shouldExecute() {
        // 길드 미션인 경우에만 실행
        return MissionCompletionContext::isGuildMission;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SagaStepResult execute(MissionCompletionContext context) {
        Long guildId = context.getGuildId();
        int expToGrant = context.getGuildExpEarned();
        String userId = context.getUserId();

        log.debug("Granting guild experience: guildId={}, exp={}, contributor={}",
            guildId, expToGrant, userId);

        try {
            // 현재 상태 저장 (보상용)
            GuildQueryFacadeService.GuildExpInfo guildExpInfo = guildQueryFacadeService.getGuildExpInfo(guildId);
            if (guildExpInfo == null) {
                log.warn("Guild not found: {}", guildId);
                return SagaStepResult.failure("길드를 찾을 수 없습니다: " + guildId);
            }

            context.addCompensationData(
                MissionCompletionContext.CompensationKeys.GUILD_EXP_BEFORE,
                guildExpInfo.currentExp());
            context.addCompensationData(
                MissionCompletionContext.CompensationKeys.GUILD_LEVEL_BEFORE,
                guildExpInfo.currentLevel());

            // 길드 경험치 지급
            guildQueryFacadeService.addGuildExperience(
                guildId,
                expToGrant,
                GuildExpSourceType.GUILD_MISSION_EXECUTION,
                context.getMission().getId(),
                userId,
                "길드 미션 수행: " + context.getMission().getTitle()
            );

            log.info("Guild experience granted: guildId={}, exp={}, contributor={}",
                guildId, expToGrant, userId);

            return SagaStepResult.success("길드 경험치 지급 완료", expToGrant);

        } catch (Exception e) {
            log.error("Failed to grant guild experience: {}", e.getMessage(), e);
            return SagaStepResult.failure(e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SagaStepResult compensate(MissionCompletionContext context) {
        if (!context.isGuildMission()) {
            return SagaStepResult.success("Not a guild mission - no compensation needed");
        }

        Long guildId = context.getGuildId();
        int expGranted = context.getGuildExpEarned();

        log.debug("Compensating guild experience: guildId={}, exp={}", guildId, expGranted);

        try {
            // 지급한 경험치 차감
            guildQueryFacadeService.subtractGuildExperience(
                guildId,
                expGranted,
                GuildExpSourceType.GUILD_MISSION_EXECUTION,
                context.getMission().getId(),
                context.getUserId(),
                "미션 완료 보상 - 길드 경험치 환수"
            );

            log.info("Guild experience compensated: guildId={}, exp={}", guildId, expGranted);
            return SagaStepResult.success("길드 경험치 환수 완료");

        } catch (Exception e) {
            log.error("Failed to compensate guild experience: guildId={}, error={}", guildId, e.getMessage());
            return SagaStepResult.failure("길드 경험치 환수 실패", e);
        }
    }
}
