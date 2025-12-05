package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
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
}
