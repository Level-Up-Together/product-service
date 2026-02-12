package io.pinkspider.leveluptogethermvp.userservice.home.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * MVP 길드 응답 DTO
 * 금일 EXP 획득량 기준 상위 길드 정보
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MvpGuildResponse {

    private Long guildId;
    private String name;
    private String imageUrl;
    private Integer level;
    private Integer memberCount;
    private Long earnedExp;
    private Integer rank;

    public static MvpGuildResponse of(
            Long guildId,
            String name,
            String imageUrl,
            Integer level,
            Integer memberCount,
            Long earnedExp,
            Integer rank) {
        return MvpGuildResponse.builder()
            .guildId(guildId)
            .name(name)
            .imageUrl(imageUrl)
            .level(level)
            .memberCount(memberCount)
            .earnedExp(earnedExp)
            .rank(rank)
            .build();
    }
}
