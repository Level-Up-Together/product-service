package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 고정 미션 일일 인스턴스 응답 DTO
 *
 * MissionExecutionResponse와 호환되는 구조로 설계하여
 * 기존 프론트엔드 코드 수정 최소화
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class DailyMissionInstanceResponse {

    private Long id;
    private Long participantId;
    private Long missionId;
    private String userId;

    // ============ 미션 정보 (스냅샷) ============

    private String missionTitle;
    private String missionDescription;
    private String missionCategoryName;
    private Long categoryId;
    private Integer expPerCompletion;
    private Integer targetDurationMinutes;

    // ============ 수행 정보 ============

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate instanceDate;

    private ExecutionStatus status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completedAt;

    private Integer durationMinutes;
    private Integer expEarned;
    private Integer completionCount;
    private Integer totalExpEarned;
    private String note;
    private String imageUrl;

    // ============ 피드 연동 ============

    private Long feedId;
    private Boolean isSharedToFeed;

    // ============ 메타 정보 ============

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime modifiedAt;

    /**
     * 엔티티를 응답 DTO로 변환
     */
    public static DailyMissionInstanceResponse from(DailyMissionInstance instance) {
        return DailyMissionInstanceResponse.builder()
            .id(instance.getId())
            .participantId(instance.getParticipant().getId())
            .missionId(instance.getParticipant().getMission().getId())
            .userId(instance.getParticipant().getUserId())
            .missionTitle(instance.getMissionTitle())
            .missionDescription(instance.getMissionDescription())
            .missionCategoryName(instance.getCategoryName())
            .categoryId(instance.getCategoryId())
            .expPerCompletion(instance.getExpPerCompletion())
            .targetDurationMinutes(instance.getTargetDurationMinutes())
            .instanceDate(instance.getInstanceDate())
            .status(instance.getStatus())
            .startedAt(instance.getStartedAt())
            .completedAt(instance.getCompletedAt())
            .durationMinutes(instance.getDurationMinutes())
            .expEarned(instance.getExpEarned())
            .completionCount(instance.getCompletionCount())
            .totalExpEarned(instance.getTotalExpEarned())
            .note(instance.getNote())
            .imageUrl(instance.getImageUrl())
            .feedId(instance.getFeedId())
            .isSharedToFeed(instance.getIsSharedToFeed())
            .createdAt(instance.getCreatedAt())
            .modifiedAt(instance.getModifiedAt())
            .build();
    }

    /**
     * MissionExecutionResponse 호환 필드명으로 변환
     * (executionDate 필드를 instanceDate 대신 사용하는 경우)
     */
    public LocalDate getExecutionDate() {
        return this.instanceDate;
    }
}
