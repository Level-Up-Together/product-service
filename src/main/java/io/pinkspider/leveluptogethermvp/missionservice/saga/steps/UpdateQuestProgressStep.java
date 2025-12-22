package io.pinkspider.leveluptogethermvp.missionservice.saga.steps;

import io.pinkspider.global.saga.SagaStep;
import io.pinkspider.global.saga.SagaStepResult;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionContext;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.UserStatsService;
import io.pinkspider.leveluptogethermvp.userservice.quest.application.QuestService;
import io.pinkspider.leveluptogethermvp.userservice.quest.domain.enums.QuestActionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step 6: 퀘스트 진행도 업데이트
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateQuestProgressStep implements SagaStep<MissionCompletionContext> {

    private final QuestService questService;
    private final UserStatsService userStatsService;

    @Override
    public String getName() {
        return "UpdateQuestProgress";
    }

    @Override
    public boolean isMandatory() {
        // 퀘스트 업데이트 실패는 미션 완료 자체를 실패시키지 않음
        return false;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SagaStepResult execute(MissionCompletionContext context) {
        String userId = context.getUserId();
        boolean isGuildMission = context.isGuildMission();
        int expEarned = context.getUserExpEarned();

        log.debug("Updating quest progress: userId={}", userId);

        try {
            // 미션 완료 퀘스트
            questService.incrementQuestProgress(userId, QuestActionType.COMPLETE_MISSION);

            // 총 완료 횟수 기반 퀘스트
            var userStats = userStatsService.getOrCreateUserStats(userId);
            questService.updateQuestProgress(userId, QuestActionType.COMPLETE_MISSIONS,
                userStats.getTotalMissionCompletions());

            // 길드 미션 퀘스트
            if (isGuildMission) {
                questService.incrementQuestProgress(userId, QuestActionType.COMPLETE_GUILD_MISSION);
            }

            // 경험치 획득 퀘스트
            questService.updateQuestProgress(userId, QuestActionType.GAIN_EXP, expEarned);

            log.info("Quest progress updated: userId={}", userId);
            return SagaStepResult.success("퀘스트 진행도 업데이트 완료");

        } catch (Exception e) {
            log.warn("Failed to update quest progress: userId={}, error={}", userId, e.getMessage());
            return SagaStepResult.failure("퀘스트 업데이트 실패", e);
        }
    }

    @Override
    public SagaStepResult compensate(MissionCompletionContext context) {
        // 퀘스트 진행도는 개별 보상이 어려우므로 로그만 남김
        // MSA 전환 시 이벤트 기반으로 재설계 필요
        log.debug("Quest progress compensation - manual intervention may be required");
        return SagaStepResult.success("퀘스트 보상은 수동 처리 필요");
    }
}
