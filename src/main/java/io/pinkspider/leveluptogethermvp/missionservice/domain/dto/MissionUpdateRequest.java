package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionInterval;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MissionUpdateRequest {

    @Size(max = 200, message = "미션 제목은 200자 이하여야 합니다.")
    private String title;

    private String description;

    private MissionVisibility visibility;

    private Integer maxParticipants;

    private LocalDateTime startDate;

    private LocalDateTime endDate;

    private MissionInterval missionInterval;

    @Min(value = 1, message = "미션 기간은 최소 1일 이상이어야 합니다.")
    @Max(value = 365, message = "미션 기간은 최대 365일까지 가능합니다.")
    private Integer durationDays;

    @Min(value = 1, message = "경험치는 최소 1 이상이어야 합니다.")
    private Integer expPerCompletion;

    @Min(value = 0, message = "보너스 경험치는 0 이상이어야 합니다.")
    private Integer bonusExpOnFullCompletion;
}
