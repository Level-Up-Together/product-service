package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.GuildMissionEventRow;
import java.time.LocalDateTime;

/**
 * LUT-239: 어드민 길드 미션 기록 응답 — 한 행 = 한 수행 건.
 * 길드 소속 미션(일반 mission_execution + 고정 daily_mission_instance)의 수행 건을 수행자 정보와 함께 노출한다.
 */
@JsonNaming(SnakeCaseStrategy.class)
public record GuildMissionHistoryAdminResponse(
    Long participantId,
    Long missionId,
    String missionTitle,
    boolean isPinned,
    String userId,
    String userNickname,
    String status,
    Integer expEarned,
    LocalDateTime eventAt
) {

    public static GuildMissionHistoryAdminResponse fromEvent(
            GuildMissionEventRow row, String userNickname) {
        String status = resolveStatus(row.getStatus());
        Integer expEarned =
            "COMPLETED".equals(status) ? (row.getExpEarned() != null ? row.getExpEarned() : 0) : null;

        return new GuildMissionHistoryAdminResponse(
            row.getParticipantId(),
            row.getMissionId(),
            row.getMissionTitle(),
            Boolean.TRUE.equals(row.getIsPinned()),
            row.getUserId(),
            userNickname,
            status,
            expEarned,
            row.getEventAt()
        );
    }

    /**
     * ExecutionStatus → 어드민 표시 상태. {@code UserMissionHistoryAdminResponse} 와 동일한 매핑.
     * 조회 쿼리가 시작/완료된 수행만 반환하므로 실질적으로 STARTED/COMPLETED 만 노출된다.
     */
    private static String resolveStatus(String executionStatus) {
        if (executionStatus == null) {
            return null;
        }
        return switch (executionStatus) {
            case "IN_PROGRESS" -> "STARTED";
            case "PENDING" -> "CREATED";
            default -> executionStatus;
        };
    }
}
