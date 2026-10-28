package io.pinkspider.leveluptogethermvp.missionservice.saga.steps;

import io.pinkspider.global.enums.ExpSourceType;
import io.pinkspider.global.facade.GamificationQueryFacade;
import io.pinkspider.global.saga.SagaStep;
import io.pinkspider.global.saga.SagaStepResult;
import io.pinkspider.leveluptogethermvp.missionservice.domain.policy.MissionExpPolicy;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionContext;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-568: 체크 미션 일일 마일스톤 보너스 지급 (일반 + 고정 통합).
 *
 * <p>완료 Step 이 {@link MissionCompletionContext#markDailyMilestone(long)} 으로 도달을 기록한 경우에만 실행된다. 미션
 * 경험치와 별도의 이력 행({@link ExpSourceType#DAILY_MISSION_MILESTONE}, sourceId = 마일스톤 수)으로 남겨 캘린더가 보상 행으로
 * 복원한다. 미션 경험치 지급과 같은 필수 Step 이라 실패하면 완료 전체가 보상된다 — 누계가 이미 3·5 를 지나면 다시 지급할 기회가 없기 때문이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrantDailyMilestoneBonusStep implements SagaStep<MissionCompletionContext> {

    private final GamificationQueryFacade gamificationQueryFacade;

    @Override
    public String getName() {
        return "GrantDailyMilestoneBonus";
    }

    @Override
    public Predicate<MissionCompletionContext> shouldExecute() {
        return ctx -> ctx.getMilestoneBonusExp() > 0;
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
        int bonus = context.getMilestoneBonusExp();
        int milestone = context.getMilestoneCount();

        try {
            gamificationQueryFacade.addExperience(
                    userId,
                    bonus,
                    ExpSourceType.DAILY_MISSION_MILESTONE,
                    (long) milestone,
                    description(milestone),
                    null,
                    MissionExpPolicy.MILESTONE_CATEGORY_NAME);

            log.info(
                    "Daily milestone bonus granted: userId={}, milestone={}, exp={}, pinned={}",
                    userId,
                    milestone,
                    bonus,
                    context.isPinned());
            return SagaStepResult.success("일일 마일스톤 보너스 지급 완료", bonus);
        } catch (Exception e) {
            log.error("Failed to grant daily milestone bonus: {}", e.getMessage(), e);
            return SagaStepResult.failure(e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SagaStepResult compensate(MissionCompletionContext context) {
        String userId = context.getUserId();
        int bonus = context.getMilestoneBonusExp();
        int milestone = context.getMilestoneCount();

        try {
            gamificationQueryFacade.subtractExperience(
                    userId,
                    bonus,
                    ExpSourceType.DAILY_MISSION_MILESTONE,
                    (long) milestone,
                    "일일 체크 미션 마일스톤 보너스 환수",
                    null,
                    MissionExpPolicy.MILESTONE_CATEGORY_NAME);

            log.info(
                    "Daily milestone bonus compensated: userId={}, milestone={}, exp={}",
                    userId,
                    milestone,
                    bonus);
            return SagaStepResult.success("일일 마일스톤 보너스 환수 완료");
        } catch (Exception e) {
            log.error(
                    "Failed to compensate daily milestone bonus: userId={}, error={}",
                    userId,
                    e.getMessage());
            return SagaStepResult.failure("일일 마일스톤 보너스 환수 실패", e);
        }
    }

    private static String description(int milestone) {
        return "일일 체크 미션 " + milestone + "개 달성 보너스";
    }
}
