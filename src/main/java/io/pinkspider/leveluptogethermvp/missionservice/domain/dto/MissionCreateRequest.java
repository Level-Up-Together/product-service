package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionInterval;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.global.annotation.NoProfanity;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MissionCreateRequest {

    @NotBlank(message = "미션 제목은 필수입니다.")
    @Size(max = 200, message = "미션 제목은 200자 이하여야 합니다.")
    @NoProfanity(fieldName = "미션 제목")
    private String title;

    @NoProfanity(fieldName = "미션 설명")
    private String description;

    @NotNull(message = "공개 여부는 필수입니다.")
    private MissionVisibility visibility;

    @NotNull(message = "미션 타입은 필수입니다.")
    private MissionType type;

    private String guildId;

    private Integer maxParticipants;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    @Builder.Default
    private MissionInterval missionInterval = MissionInterval.DAILY;

    @Min(value = 1, message = "미션 기간은 최소 1일 이상이어야 합니다.")
    @Max(value = 365, message = "미션 기간은 최대 365일까지 가능합니다.")
    private Integer durationDays;

    @Min(value = 1, message = "수행 시간은 최소 1분 이상이어야 합니다.")
    @Max(value = 480, message = "수행 시간은 최대 480분(8시간)까지 가능합니다.")
    private Integer durationMinutes;

    @Min(value = 1, message = "경험치는 최소 1 이상이어야 합니다.")
    @Builder.Default
    private Integer expPerCompletion = 10;

    @Min(value = 0, message = "보너스 경험치는 0 이상이어야 합니다.")
    @Builder.Default
    private Integer bonusExpOnFullCompletion = 50;

    // 카테고리: categoryId 또는 customCategory 중 하나 선택
    private Long categoryId;

    @Size(max = 50, message = "사용자 정의 카테고리는 50자 이하여야 합니다.")
    @NoProfanity(fieldName = "커스텀 카테고리")
    private String customCategory;

    // 고정 미션 여부 (삭제할 때까지 목록에 유지)
    @Builder.Default
    private Boolean isPinned = false;
}
