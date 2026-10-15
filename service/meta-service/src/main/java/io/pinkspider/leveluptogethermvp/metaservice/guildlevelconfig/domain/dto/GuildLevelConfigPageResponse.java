package io.pinkspider.leveluptogethermvp.metaservice.guildlevelconfig.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import org.springframework.data.domain.Page;

@JsonNaming(SnakeCaseStrategy.class)
public record GuildLevelConfigPageResponse(
        List<GuildLevelConfigResponse> content,
        int totalPages,
        long totalElements,
        int number,
        int size,
        boolean first,
        boolean last) {
    public static GuildLevelConfigPageResponse from(Page<GuildLevelConfigResponse> page) {
        return new GuildLevelConfigPageResponse(
                page.getContent(),
                page.getTotalPages(),
                page.getTotalElements(),
                page.getNumber(),
                page.getSize(),
                page.isFirst(),
                page.isLast());
    }
}
