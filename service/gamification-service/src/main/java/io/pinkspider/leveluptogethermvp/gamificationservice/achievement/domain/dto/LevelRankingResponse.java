package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserExperience;
import io.pinkspider.global.enums.TitleRarity;
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
    private TitleRarity equippedTitleRarity;
    private String equippedTitleColorCode;
    private String leftTitle;
    private TitleRarity leftTitleRarity;
    private String rightTitle;
    private TitleRarity rightTitleRarity;
    private Integer currentLevel;
    private Integer currentExp;
    private Integer totalExp;
    private Long totalUsers;
    private Double percentile;  // 상위 X%

    // LUT-297: 주간/월간 랭킹에서 해당 기간 획득 경험치 (정렬 기준). 그 외 랭킹에서는 null.
    private Long periodExp;

    // LUT-275: 현재 실시간 진행중인 미션 (없으면 null). 프로필(LUT-257)과 동일 스펙 —
    // 비노출 시 미션 정보는 null 마스킹되고 is_visible=false 로 내려간다.
    @lombok.Setter
    private InProgressMissionInfo inProgressMission;

    /** LUT-275: 진행중 미션 정보 (PublicProfileResponse.InProgressMissionInfo 와 동일 형태). */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class InProgressMissionInfo {
        private Long missionId;
        private Long categoryId;
        private String categoryName;
        private String title;
        private String visibility;
        @com.fasterxml.jackson.annotation.JsonProperty("is_visible")
        private Boolean isVisible;
        private java.time.LocalDateTime startedAt;
    }

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
        String equippedTitle,
        TitleRarity equippedTitleRarity,
        String equippedTitleColorCode,
        String leftTitle,
        TitleRarity leftTitleRarity,
        String rightTitle,
        TitleRarity rightTitleRarity
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
            .equippedTitleRarity(equippedTitleRarity)
            .equippedTitleColorCode(equippedTitleColorCode)
            .leftTitle(leftTitle)
            .leftTitleRarity(leftTitleRarity)
            .rightTitle(rightTitle)
            .rightTitleRarity(rightTitleRarity)
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
    public static LevelRankingResponse defaultResponse(
        String userId,
        long totalUsers,
        String nickname,
        String profileImageUrl,
        String equippedTitle,
        TitleRarity equippedTitleRarity,
        String equippedTitleColorCode,
        String leftTitle,
        TitleRarity leftTitleRarity,
        String rightTitle,
        TitleRarity rightTitleRarity
    ) {
        return LevelRankingResponse.builder()
            .rank(totalUsers + 1)
            .userId(userId)
            .nickname(nickname)
            .profileImageUrl(profileImageUrl)
            .equippedTitle(equippedTitle)
            .equippedTitleRarity(equippedTitleRarity)
            .equippedTitleColorCode(equippedTitleColorCode)
            .leftTitle(leftTitle)
            .leftTitleRarity(leftTitleRarity)
            .rightTitle(rightTitle)
            .rightTitleRarity(rightTitleRarity)
            .currentLevel(1)
            .currentExp(0)
            .totalExp(0)
            .totalUsers(totalUsers)
            .percentile(100.0)
            .build();
    }
}
