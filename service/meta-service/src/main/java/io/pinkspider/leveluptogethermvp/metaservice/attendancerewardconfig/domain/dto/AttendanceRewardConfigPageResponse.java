package io.pinkspider.leveluptogethermvp.metaservice.attendancerewardconfig.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import org.springframework.data.domain.Page;

@JsonNaming(SnakeCaseStrategy.class)
public record AttendanceRewardConfigPageResponse(
        List<AttendanceRewardConfigResponse> content,
        int totalPages,
        long totalElements,
        int number,
        int size,
        boolean first,
        boolean last) {
    public static AttendanceRewardConfigPageResponse from(
            Page<AttendanceRewardConfigResponse> page) {
        return new AttendanceRewardConfigPageResponse(
                page.getContent(),
                page.getTotalPages(),
                page.getTotalElements(),
                page.getNumber(),
                page.getSize(),
                page.isFirst(),
                page.isLast());
    }
}
