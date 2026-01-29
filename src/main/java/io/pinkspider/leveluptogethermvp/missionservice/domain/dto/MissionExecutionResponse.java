package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
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
    private MissionType missionType;
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
    private String imageUrl;
    private Long feedId;

    // 자동 종료 여부 (2시간 초과 시 true, 프론트엔드에서 알림 모달 표시용)
    private Boolean isAutoCompleted;

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
            .missionType(execution.getParticipant().getMission().getType())
            .userId(execution.getParticipant().getUserId())
            .executionDate(execution.getExecutionDate())
            .status(execution.getStatus())
            .startedAt(execution.getStartedAt())
            .completedAt(execution.getCompletedAt())
            .durationMinutes(durationMinutes)
            .expEarned(execution.getExpEarned())
            .note(execution.getNote())
            .imageUrl(execution.getImageUrl())
            .feedId(execution.getFeedId())
            .isAutoCompleted(execution.getIsAutoCompleted())
            .createdAt(execution.getCreatedAt())
            .build();
    }

    /**
     * DailyMissionInstance를 MissionExecutionResponse로 변환
     * 고정 미션(pinned mission)의 일일 인스턴스를 동일한 응답 포맷으로 변환
     */
    public static MissionExecutionResponse fromDailyMissionInstance(DailyMissionInstance instance) {
        return MissionExecutionResponse.builder()
            .id(instance.getId())
            .participantId(instance.getParticipant().getId())
            .missionId(instance.getParticipant().getMission().getId())
            .missionTitle(instance.getMissionTitle())
            .missionCategoryName(instance.getCategoryName())
            .missionType(instance.getParticipant().getMission().getType())
            .userId(instance.getParticipant().getUserId())
            .executionDate(instance.getInstanceDate())
            .status(instance.getStatus())
            .startedAt(instance.getStartedAt())
            .completedAt(instance.getCompletedAt())
            .durationMinutes(instance.getDurationMinutes())
            .expEarned(instance.getExpEarned())
            .note(instance.getNote())
            .imageUrl(instance.getImageUrl())
            .feedId(instance.getFeedId())
            .isAutoCompleted(instance.getIsAutoCompleted())
            .createdAt(instance.getCreatedAt())
            .build();
    }
}
