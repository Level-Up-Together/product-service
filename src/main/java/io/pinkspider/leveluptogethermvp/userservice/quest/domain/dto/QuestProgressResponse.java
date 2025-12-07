package io.pinkspider.leveluptogethermvp.userservice.quest.domain.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestProgressResponse {
    private String periodKey;
    private Integer totalQuests;
    private Integer completedQuests;
    private Integer claimableQuests;
    private Integer progressPercentage;
    private List<UserQuestResponse> quests;

    public static QuestProgressResponse of(String periodKey, List<UserQuestResponse> quests) {
        int total = quests.size();
        int completed = (int) quests.stream().filter(UserQuestResponse::getIsCompleted).count();
        int claimable = (int) quests.stream().filter(UserQuestResponse::getCanClaimReward).count();
        int percentage = total > 0 ? (completed * 100) / total : 0;

        return QuestProgressResponse.builder()
            .periodKey(periodKey)
            .totalQuests(total)
            .completedQuests(completed)
            .claimableQuests(claimable)
            .progressPercentage(percentage)
            .quests(quests)
            .build();
    }
}
