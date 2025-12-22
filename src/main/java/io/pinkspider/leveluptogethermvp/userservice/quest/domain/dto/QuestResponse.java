package io.pinkspider.leveluptogethermvp.userservice.quest.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.userservice.quest.domain.entity.Quest;
import io.pinkspider.leveluptogethermvp.userservice.quest.domain.enums.QuestActionType;
import io.pinkspider.leveluptogethermvp.userservice.quest.domain.enums.QuestCategory;
import io.pinkspider.leveluptogethermvp.userservice.quest.domain.enums.QuestType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class QuestResponse {
    private Long id;
    private String name;
    private String description;
    private QuestType questType;
    private QuestCategory category;
    private QuestActionType actionType;
    private Integer requiredCount;
    private Integer rewardExp;
    private Integer rewardPoints;
    private String iconUrl;

    public static QuestResponse from(Quest quest) {
        return QuestResponse.builder()
            .id(quest.getId())
            .name(quest.getName())
            .description(quest.getDescription())
            .questType(quest.getQuestType())
            .category(quest.getCategory())
            .actionType(quest.getActionType())
            .requiredCount(quest.getRequiredCount())
            .rewardExp(quest.getRewardExp())
            .rewardPoints(quest.getRewardPoints())
            .iconUrl(quest.getIconUrl())
            .build();
    }
}
