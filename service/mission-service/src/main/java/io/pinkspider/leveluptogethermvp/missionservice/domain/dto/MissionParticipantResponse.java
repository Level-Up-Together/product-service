package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
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
public class MissionParticipantResponse {

    private Long id;
    private Long missionId;
    private String missionTitle;
    private String userId;
    private ParticipantStatus status;
    private Integer progress;
    private String note;

    private LocalDateTime joinedAt;

    private LocalDateTime completedAt;

    private LocalDateTime createdAt;

    // LUT-433: 참여자별 수행 통계 (길드 미션 상세 참여자 목록 전용 — getMissionParticipants 에서만 세팅).
    // 미세팅 경로에서는 직렬화되지 않아 기존 응답 스키마에 영향 없음. 미수행 참여자는 목록에서도 null.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer progressDays;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer executionCount;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer earnedExp;

    public static MissionParticipantResponse from(MissionParticipant participant) {
        return MissionParticipantResponse.builder()
            .id(participant.getId())
            .missionId(participant.getMission().getId())
            .missionTitle(participant.getMission().getTitle())
            .userId(participant.getUserId())
            .status(participant.getStatus())
            .progress(participant.getProgress())
            .note(participant.getNote())
            .joinedAt(participant.getJoinedAt())
            .completedAt(participant.getCompletedAt())
            .createdAt(participant.getCreatedAt())
            .build();
    }
}
