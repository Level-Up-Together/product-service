package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import org.springframework.data.domain.Page;

@JsonNaming(SnakeCaseStrategy.class)
public record AchievementAdminPageResponse(
        List<AchievementAdminResponse> content,
        int totalPages,
        long totalElements,
        int number,
        int size,
        boolean first,
        boolean last) {
    public static AchievementAdminPageResponse from(Page<AchievementAdminResponse> page) {
        return new AchievementAdminPageResponse(
                page.getContent(),
                page.getTotalPages(),
                page.getTotalElements(),
                page.getNumber(),
                page.getSize(),
                page.isFirst(),
                page.isLast());
    }
}
