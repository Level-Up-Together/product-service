package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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

    private LocalDate executionDate;

    private ExecutionStatus status;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    // 수행 시간 (분)
    private Integer durationMinutes;

    private Integer expEarned;
    private String note;

    /** 호환: 첫 장. QA-53 이후 imageUrls 의 0번 인덱스와 동일. */
    private String imageUrl;

    /** QA-53: 다중 이미지 (sort_order 순). null/미설정이면 JSON 응답에서 제외 (RestDocs 호환). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<String> imageUrls;

    private Boolean isSharedToFeed;

    // 연결된 피드의 공개범위 (피드 미생성 시 null)
    private String feedVisibility;

    // 자동 종료 여부 (2시간 초과 시 true, 프론트엔드에서 알림 모달 표시용)
    private Boolean isAutoCompleted;

    // SIMPLE 모드 일일 EXP 한도(10회) 도달로 EXP=0 처리됨 (프론트 안내 토스트용)
    private Boolean dailySimpleExpCapped;

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
            .isSharedToFeed(Boolean.TRUE.equals(execution.getIsSharedToFeed()))
            .isAutoCompleted(execution.getIsAutoCompleted())
            .createdAt(execution.getCreatedAt())
            .build();
    }

    /**
     * DailyMissionInstanceResponse를 MissionExecutionResponse로 변환 (하위 호환성)
     */
    public static MissionExecutionResponse fromDailyInstance(DailyMissionInstanceResponse instanceResponse) {
        return MissionExecutionResponse.builder()
            .id(instanceResponse.getId())
            .participantId(instanceResponse.getParticipantId())
            .missionId(instanceResponse.getMissionId())
            .missionTitle(instanceResponse.getMissionTitle())
            .missionCategoryName(instanceResponse.getMissionCategoryName())
            // QA-184: 길드미션 공개 옵션 노출 판단용 mission_type 전파
            .missionType(instanceResponse.getMissionType())
            .userId(instanceResponse.getUserId())
            .executionDate(instanceResponse.getInstanceDate())
            .status(instanceResponse.getStatus())
            .startedAt(instanceResponse.getStartedAt())
            .completedAt(instanceResponse.getCompletedAt())
            .durationMinutes(instanceResponse.getDurationMinutes())
            .expEarned(instanceResponse.getExpEarned())
            .note(instanceResponse.getNote())
            .imageUrl(instanceResponse.getImageUrl())
            .imageUrls(instanceResponse.getImageUrls())
            .isSharedToFeed(instanceResponse.getIsSharedToFeed())
            .dailySimpleExpCapped(instanceResponse.getDailySimpleExpCapped())
            .createdAt(instanceResponse.getCreatedAt())
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
            .isSharedToFeed(Boolean.TRUE.equals(instance.getIsSharedToFeed()))
            .isAutoCompleted(instance.getIsAutoCompleted())
            .createdAt(instance.getCreatedAt())
            .build();
    }
}
