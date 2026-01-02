package io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공개 프로필 응답 DTO (타인이 볼 수 있는 정보)
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PublicProfileResponse {

    private String userId;
    private String nickname;
    private String profileImageUrl;
    private String bio;
    private EquippedTitleInfo leftTitle;
    private EquippedTitleInfo rightTitle;

    // 레벨 정보
    private Integer level;

    // 통계 정보
    private LocalDate startDate;
    private Long daysSinceJoined;
    private Integer clearedMissionsCount;
    private Integer acquiredTitlesCount;

    // 소속 길드 목록
    private java.util.List<GuildInfo> guilds;

    // 본인 여부
    @JsonProperty("is_owner")
    private Boolean isOwner;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class EquippedTitleInfo {
        private Long titleId;
        private String name;
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
    public static class GuildInfo {
        private Long guildId;
        private String name;
        private String imageUrl;
        private Integer level;
        private Integer memberCount;
    }
}
