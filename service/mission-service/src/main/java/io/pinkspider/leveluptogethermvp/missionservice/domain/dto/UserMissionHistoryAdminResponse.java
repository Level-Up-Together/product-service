package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.UserMissionEventRow;
import java.time.LocalDateTime;

/**
 * QA-205: 어드민 유저 미션 기록 응답 — 한 행 = 한 수행 건.
 * 일반 미션의 수행(mission_execution)과 고정 미션의 수행(daily_mission_instance)을 함께 노출한다.
 */
@JsonNaming(SnakeCaseStrategy.class)
public record UserMissionHistoryAdminResponse(
    Long participantId,
    Long missionId,
    String missionTitle,
    String missionType,
    String guildName,
    String status,
    Integer expEarned,
    LocalDateTime eventAt
) {

    public static UserMissionHistoryAdminResponse fromEvent(UserMissionEventRow row) {
        String missionType = resolveMissionType(row.getMissionType(), row.getMissionSource());
        String guildName = "GUILD".equals(missionType) ? row.getGuildName() : null;
        String status = resolveStatus(row.getStatus());
        Integer expEarned =
            "COMPLETED".equals(status) ? (row.getExpEarned() != null ? row.getExpEarned() : 0) : null;

        return new UserMissionHistoryAdminResponse(
            row.getParticipantId(),
            row.getMissionId(),
            row.getMissionTitle(),
            missionType,
            guildName,
            status,
            expEarned,
            row.getEventAt()
        );
    }

    /**
     * QA-205: 길드 여부는 Mission.type(GUILD)으로 판별한다.
     * 길드 미션도 source 는 USER 로 저장되므로 source 만으로는 구분할 수 없다.
     * 미션북은 시스템 템플릿 출처(source=SYSTEM)로 식별한다.
     */
    private static String resolveMissionType(String type, String source) {
        if (type == null) {
            return null;
        }
        if ("GUILD".equals(type)) {
            return "GUILD";
        }
        if ("SYSTEM".equals(source)) {
            return "MISSION_BOOK";
        }
        return "PERSONAL";
    }

    /**
     * ExecutionStatus → 어드민 표시 상태.
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
