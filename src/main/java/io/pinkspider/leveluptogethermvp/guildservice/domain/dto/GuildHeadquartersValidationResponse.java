package io.pinkspider.leveluptogethermvp.guildservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GuildHeadquartersValidationResponse {

    private boolean valid;
    private String message;
    private List<NearbyGuildInfo> nearbyGuilds;
    private Integer baseRadiusMeters;
    private Integer radiusIncreasePerLevelTier;
    private Integer levelTierSize;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class NearbyGuildInfo {
        private Long guildId;
        private String guildName;
        private Integer guildLevel;
        private Double latitude;
        private Double longitude;
        private Integer protectionRadiusMeters;
        private Double distanceMeters;
    }
}
