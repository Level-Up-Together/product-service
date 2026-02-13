package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import org.springframework.data.domain.Page;

@JsonNaming(SnakeCaseStrategy.class)
public record UserMissionHistoryAdminPageResponse(
    List<UserMissionHistoryAdminResponse> content,
    int totalPages,
    long totalElements,
    int number,
    int size,
    boolean first,
    boolean last
) {

    public static UserMissionHistoryAdminPageResponse from(
            Page<?> page, List<UserMissionHistoryAdminResponse> content) {
        return new UserMissionHistoryAdminPageResponse(
            content,
            page.getTotalPages(),
            page.getTotalElements(),
            page.getNumber(),
            page.getSize(),
            page.isFirst(),
            page.isLast()
        );
    }
}
