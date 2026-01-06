package io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * MyPage 화면 전체 응답 DTO
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MyPageResponse {

    // 1. 프로필 정보
    private ProfileInfo profile;

    // 2. 경험치 정보
    private ExperienceInfo experience;

    // 3. 유저 정보/통계
    private UserInfo userInfo;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class ProfileInfo {
        private String userId;
        private String nickname;
        private String profileImageUrl;
        private String bio;
        private EquippedTitleInfo leftTitle;
        private EquippedTitleInfo rightTitle;
        private Integer followerCount;
        private Integer followingCount;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class EquippedTitleInfo {
        private Long userTitleId;
        private Long titleId;
        private String name;
        private String nameEn;
        private String nameAr;
        private String displayName;
        private String rarity;
        private String colorCode;
        private String iconUrl;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class ExperienceInfo {
        private Integer currentLevel;
        private Integer currentExp;
        private Integer totalExp;
        private Integer nextLevelRequiredExp;
        private Double expPercentage;
        private Integer expForPercentage;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class UserInfo {
        private LocalDate startDate;
        private Long daysSinceJoined;
        private Integer clearedMissionsCount;
        private Integer clearedMissionBooksCount;
        private Double rankingPercentile;
        private Integer acquiredTitlesCount;
        private Long rankingPoints;
    }
}
