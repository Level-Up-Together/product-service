package io.pinkspider.leveluptogethermvp.metaservice.attendancerewardconfig.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.metaservice.attendancerewardconfig.domain.enums.AttendanceRewardType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
public class AttendanceRewardConfigRequest {

    @NotNull(message = "보상 타입은 필수입니다.")
    private AttendanceRewardType rewardType;

    @Min(value = 1, message = "필요 일수는 1 이상이어야 합니다.")
    private Integer requiredDays;

    @Min(value = 0, message = "보상 경험치는 0 이상이어야 합니다.")
    private Integer rewardExp;

    private Long rewardTitleId;

    @Size(max = 200, message = "설명은 200자 이하이어야 합니다.")
    private String description;

    private LocalDate startDate;

    private LocalDate endDate;

    private Boolean isActive;
}
