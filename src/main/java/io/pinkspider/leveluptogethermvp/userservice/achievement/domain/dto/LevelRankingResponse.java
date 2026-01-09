package io.pinkspider.leveluptogethermvp.userservice.achievement.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserExperience;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class LevelRankingResponse {

    private Long rank;
    private String userId;
    private String nickname;
    private String profileImageUrl;
    private String equippedTitle;
    private Integer currentLevel;
    private Integer currentExp;
    private Integer totalExp;
    private Long totalUsers;
    private Double percentile;  // 상위 X%

    public static LevelRankingResponse from(UserExperience exp, long rank, long totalUsers) {
        double percentile = totalUsers > 0
            ? Math.round((double) rank / totalUsers * 1000) / 10.0
            : 100.0;

        return LevelRankingResponse.builder()
            .rank(rank)
            .userId(exp.getUserId())
            .currentLevel(exp.getCurrentLevel())
            .currentExp(exp.getCurrentExp())
            .totalExp(exp.getTotalExp())
            .totalUsers(totalUsers)
            .percentile(percentile)
            .build();
    }

    public static LevelRankingResponse from(
        UserExperience exp,
        long rank,
        long totalUsers,
        String nickname,
        String profileImageUrl,
        String equippedTitle
    ) {
        double percentile = totalUsers > 0
            ? Math.round((double) rank / totalUsers * 1000) / 10.0
            : 100.0;

        return LevelRankingResponse.builder()
            .rank(rank)
            .userId(exp.getUserId())
            .nickname(nickname)
            .profileImageUrl(profileImageUrl)
            .equippedTitle(equippedTitle)
            .currentLevel(exp.getCurrentLevel())
            .currentExp(exp.getCurrentExp())
            .totalExp(exp.getTotalExp())
            .totalUsers(totalUsers)
            .percentile(percentile)
            .build();
    }

    /**
     * 사용자 경험치 정보가 없는 경우 기본값 반환
     */
    public static LevelRankingResponse defaultResponse(String userId, long totalUsers) {
        return LevelRankingResponse.builder()
            .rank(totalUsers + 1)
            .userId(userId)
            .currentLevel(1)
            .currentExp(0)
            .totalExp(0)
            .totalUsers(totalUsers)
            .percentile(100.0)
            .build();
    }
}
