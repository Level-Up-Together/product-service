package io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** MyPage 화면 전체 응답 DTO */
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
        private String email;
        private String provider;
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
        private String nameJa;
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

        // LUT-348: 레벨에서 환산한 희귀도 등급 (상점 할증 기준)
        private String rarity;
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

        // LUT-782: 미션을 한 번이라도 완료했는가 — 웹이 첫 완료 축하를 한 번만 띄우고 분석 is_first 에 쓴다(LUT-784)
        private Boolean hasCompletedMission;

        private Integer clearedMissionBooksCount;
        private Double rankingPercentile;
        private Integer acquiredTitlesCount;
        private Long rankingPoints;

        // LUT-717: 연속 실천 일수(하루 미션 1개 이상 완료, 유저 로컬 기준) — 연속 출석과 별개 / 최장
        private Integer practiceStreakDays;
        private Integer maxPracticeStreakDays;

        // LUT-723: 구독자 연속 실천 방어권 — 이번 달 남은 개수(비구독자 0)·마지막으로 방어된 날(유저 로컬, 없으면 null)
        private Integer practiceShieldsRemaining;
        private java.time.LocalDate lastPracticeShieldDate;
    }
}
