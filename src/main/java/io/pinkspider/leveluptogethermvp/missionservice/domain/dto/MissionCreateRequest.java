package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class MissionCreateRequest {

    @NotBlank(message = "미션 제목은 필수입니다.")
    @Size(max = 200, message = "미션 제목은 200자 이하여야 합니다.")
    private String title;

    private String description;

    @NotNull(message = "공개 여부는 필수입니다.")
    private MissionVisibility visibility;

    @NotNull(message = "미션 타입은 필수입니다.")
    private MissionType type;

    private String guildId;

    private Integer maxParticipants;

    private LocalDateTime startDate;

    private LocalDateTime endDate;
}
