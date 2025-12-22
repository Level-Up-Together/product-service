package io.pinkspider.leveluptogethermvp.userservice.quest.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.userservice.quest.domain.entity.UserQuest;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UserQuestResponse {
    private Long id;
    private QuestResponse quest;
    private String periodKey;
    private Integer currentCount;
    private Integer requiredCount;
    private Integer progress;
    private Boolean isCompleted;
    private LocalDateTime completedAt;
    private Boolean isRewardClaimed;
    private LocalDateTime rewardClaimedAt;
    private Boolean canClaimReward;

    public static UserQuestResponse from(UserQuest userQuest) {
        return UserQuestResponse.builder()
            .id(userQuest.getId())
            .quest(QuestResponse.from(userQuest.getQuest()))
            .periodKey(userQuest.getPeriodKey())
            .currentCount(userQuest.getCurrentCount())
            .requiredCount(userQuest.getQuest().getRequiredCount())
            .progress(userQuest.getProgress())
            .isCompleted(userQuest.getIsCompleted())
            .completedAt(userQuest.getCompletedAt())
            .isRewardClaimed(userQuest.getIsRewardClaimed())
            .rewardClaimedAt(userQuest.getRewardClaimedAt())
            .canClaimReward(userQuest.canClaimReward())
            .build();
    }
}
