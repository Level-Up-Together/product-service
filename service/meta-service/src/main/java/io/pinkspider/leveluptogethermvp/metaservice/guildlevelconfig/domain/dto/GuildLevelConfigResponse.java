package io.pinkspider.leveluptogethermvp.metaservice.guildlevelconfig.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.metaservice.guildlevelconfig.domain.entity.GuildLevelConfig;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
public class GuildLevelConfigResponse {

    private Long id;
    private Integer level;
    private Integer requiredExp;
    private Integer cumulativeExp;
    private Integer requiredPoint;
    private Integer cumulativePoint;
    private Integer maxMembers;
    private String title;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;

    public static GuildLevelConfigResponse from(GuildLevelConfig entity) {
        return GuildLevelConfigResponse.builder()
            .id(entity.getId())
            .level(entity.getLevel())
            .requiredExp(entity.getRequiredExp())
            .cumulativeExp(entity.getCumulativeExp())
            .requiredPoint(entity.getRequiredPoint())
            .cumulativePoint(entity.getCumulativePoint())
            .maxMembers(entity.getMaxMembers())
            .title(entity.getTitle())
            .description(entity.getDescription())
            .createdAt(entity.getCreatedAt())
            .modifiedAt(entity.getModifiedAt())
            .build();
    }
}
