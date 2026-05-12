package io.pinkspider.leveluptogethermvp.metaservice.attendancerewardconfig.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.metaservice.attendancerewardconfig.domain.entity.AttendanceRewardConfig;
import io.pinkspider.leveluptogethermvp.metaservice.attendancerewardconfig.domain.enums.AttendanceRewardType;
import java.time.LocalDate;
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
public class AttendanceRewardConfigResponse {

    private Long id;
    private AttendanceRewardType rewardType;
    private String rewardTypeDisplayName;
    private Integer requiredDays;
    private Integer rewardExp;
    private Long rewardTitleId;
    private String description;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;

    public static AttendanceRewardConfigResponse from(AttendanceRewardConfig entity) {
        return AttendanceRewardConfigResponse.builder()
            .id(entity.getId())
            .rewardType(entity.getRewardType())
            .rewardTypeDisplayName(entity.getRewardType().getDisplayName())
            .requiredDays(entity.getRequiredDays())
            .rewardExp(entity.getRewardExp())
            .rewardTitleId(entity.getRewardTitleId())
            .description(entity.getDescription())
            .startDate(entity.getStartDate())
            .endDate(entity.getEndDate())
            .isActive(entity.getIsActive())
            .createdAt(entity.getCreatedAt())
            .modifiedAt(entity.getModifiedAt())
            .build();
    }
}
