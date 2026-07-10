package io.pinkspider.leveluptogethermvp.missionservice.saga.steps;

import io.pinkspider.global.facade.GamificationQueryFacade;
import io.pinkspider.global.saga.SagaStep;
import io.pinkspider.global.saga.SagaStepResult;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionSource;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * QA-220: 미션북 최초 목표달성 다이아 지급 (일반 미션 + 고정 미션 통합)
 *
 * 지급 조건 (QA-158 has_achieved_target 정의와 동일):
 * - 미션북 출처 미션 (source=SYSTEM, base_mission_id 존재)
 * - 목표시간 설정 미션 (target_duration_minutes 존재)
 * - 이번 수행 exp 가 목표시간 이상 (exp_earned >= target_duration_minutes)
 *
 * 같은 템플릿 재달성 시 중복 지급은 DiamondService 가 이력으로 방지한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrantMissionBookDiamondStep implements SagaStep<MissionCompletionContext> {

    private final GamificationQueryFacade gamificationQueryFacadeService;

    @Override
    public String getName() {
        return "GrantMissionBookDiamond";
    }

    /** 다이아 지급 실패가 미션 완료 자체를 롤백해선 안 된다 */
    @Override
    public boolean isMandatory() {
        return false;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SagaStepResult execute(MissionCompletionContext context) {
        Mission mission = context.getMission();

        if (mission == null
            || mission.getSource() != MissionSource.SYSTEM
            || mission.getBaseMissionId() == null
            || mission.getTargetDurationMinutes() == null
            || context.getUserExpEarned() < mission.getTargetDurationMinutes()) {
            return SagaStepResult.success("미션북 목표달성 아님 - 다이아 지급 스킵");
        }

        try {
            boolean awarded = gamificationQueryFacadeService.awardMissionBookDiamond(
                context.getUserId(), mission.getBaseMissionId(), mission.getTitle());

            if (awarded) {
                log.info("미션북 다이아 지급: userId={}, templateId={}, mission={}",
                    context.getUserId(), mission.getBaseMissionId(), mission.getTitle());
                return SagaStepResult.success("미션북 다이아 지급 완료");
            }
            return SagaStepResult.success("이미 지급된 미션북 - 다이아 지급 스킵");

        } catch (Exception e) {
            log.error("Failed to grant mission book diamond: {}", e.getMessage(), e);
            return SagaStepResult.failure(e);
        }
    }

    /**
     * 보상 없음 — 최초 목표달성 사실은 유효하며, 이력 기반 중복 방지 특성상 회수 시
     * 재지급이 불가능해지는 부작용이 더 크다. (지급량 1개, 영향 미미)
     */
    @Override
    public SagaStepResult compensate(MissionCompletionContext context) {
        return SagaStepResult.success("미션북 다이아는 회수하지 않음");
    }
}
