package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
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
public class MissionExecutionResponse {

    private Long id;
    private Long participantId;
    private Long missionId;
    private String missionTitle;
    private String userId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate executionDate;

    private ExecutionStatus status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completedAt;

    private Integer expEarned;
    private String note;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    public static MissionExecutionResponse from(MissionExecution execution) {
        return MissionExecutionResponse.builder()
            .id(execution.getId())
            .participantId(execution.getParticipant().getId())
            .missionId(execution.getParticipant().getMission().getId())
            .missionTitle(execution.getParticipant().getMission().getTitle())
            .userId(execution.getParticipant().getUserId())
            .executionDate(execution.getExecutionDate())
            .status(execution.getStatus())
            .completedAt(execution.getCompletedAt())
            .expEarned(execution.getExpEarned())
            .note(execution.getNote())
            .createdAt(execution.getCreatedAt())
            .build();
    }
}
