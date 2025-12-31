package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import java.time.LocalDate;
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
public class MissionExecutionResponse {

    private Long id;
    private Long participantId;
    private Long missionId;
    private String missionTitle;
    private String missionCategoryName;
    private String userId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate executionDate;

    private ExecutionStatus status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completedAt;

    // 수행 시간 (분)
    private Integer durationMinutes;

    private Integer expEarned;
    private String note;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    public static MissionExecutionResponse from(MissionExecution execution) {
        Integer durationMinutes = null;
        if (execution.getStartedAt() != null && execution.getCompletedAt() != null) {
            durationMinutes = (int) java.time.Duration.between(
                execution.getStartedAt(), execution.getCompletedAt()).toMinutes();
        }

        return MissionExecutionResponse.builder()
            .id(execution.getId())
            .participantId(execution.getParticipant().getId())
            .missionId(execution.getParticipant().getMission().getId())
            .missionTitle(execution.getParticipant().getMission().getTitle())
            .missionCategoryName(execution.getParticipant().getMission().getCategoryName())
            .userId(execution.getParticipant().getUserId())
            .executionDate(execution.getExecutionDate())
            .status(execution.getStatus())
            .startedAt(execution.getStartedAt())
            .completedAt(execution.getCompletedAt())
            .durationMinutes(durationMinutes)
            .expEarned(execution.getExpEarned())
            .note(execution.getNote())
            .createdAt(execution.getCreatedAt())
            .build();
    }
}
