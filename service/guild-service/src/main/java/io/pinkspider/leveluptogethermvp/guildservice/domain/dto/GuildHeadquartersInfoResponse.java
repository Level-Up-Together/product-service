package io.pinkspider.leveluptogethermvp.guildservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 모든 길드 거점 정보 (지도 표시용)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GuildHeadquartersInfoResponse {

    private List<GuildHeadquartersInfo> guilds;
    private HeadquartersConfig config;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class GuildHeadquartersInfo {
        private Long guildId;
        private String guildName;
        private String guildImageUrl;
        private Integer guildLevel;
        private Long categoryId;
        private String categoryName;
        private String categoryIcon;
        private Double latitude;
        private Double longitude;
        private Integer protectionRadiusMeters;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class HeadquartersConfig {
        private Integer baseRadiusMeters;
        private Integer radiusIncreasePerLevelTier;
        private Integer levelTierSize;
    }
}
