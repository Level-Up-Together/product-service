package io.pinkspider.leveluptogethermvp.userservice.experience.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserExperience;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UserExperienceResponse {

    private Long id;
    private String userId;
    private Integer currentLevel;
    private Integer currentExp;
    private Integer totalExp;
    private Integer nextLevelRequiredExp;
    private Integer expToNextLevel;
    private Double progressToNextLevel;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime modifiedAt;

    public static UserExperienceResponse from(UserExperience userExp, Integer nextLevelRequiredExp) {
        int expToNext = nextLevelRequiredExp != null
            ? nextLevelRequiredExp - userExp.getCurrentExp()
            : 0;
        double progress = nextLevelRequiredExp != null && nextLevelRequiredExp > 0
            ? (double) userExp.getCurrentExp() / nextLevelRequiredExp * 100
            : 0;

        return UserExperienceResponse.builder()
            .id(userExp.getId())
            .userId(userExp.getUserId())
            .currentLevel(userExp.getCurrentLevel())
            .currentExp(userExp.getCurrentExp())
            .totalExp(userExp.getTotalExp())
            .nextLevelRequiredExp(nextLevelRequiredExp)
            .expToNextLevel(Math.max(0, expToNext))
            .progressToNextLevel(Math.min(100, progress))
            .createdAt(userExp.getCreatedAt())
            .modifiedAt(userExp.getModifiedAt())
            .build();
    }
}
