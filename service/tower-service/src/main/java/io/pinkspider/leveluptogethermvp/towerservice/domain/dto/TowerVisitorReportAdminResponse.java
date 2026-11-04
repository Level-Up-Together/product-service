package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerVisitorReport;
import java.time.LocalDateTime;

/**
 * LUT-705: 방문객 신고 1건(어드민 전용 — 원문·IP·기기 ID 포함). {@code recent_messages} 는 신고 시점의 최근 원문(JSON 배열 문자열,
 * 최신 먼저)
 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerVisitorReportAdminResponse(
        Long id,
        String reporterUserId,
        Long instanceId,
        Integer floorNumber,
        Long targetOccupantId,
        Integer visitorNo,
        String visitorTag,
        String clientIp,
        String deviceId,
        String userAgent,
        String reason,
        String recentMessages,
        LocalDateTime createdAt) {

    public static TowerVisitorReportAdminResponse from(TowerVisitorReport r, String visitorTag) {
        return new TowerVisitorReportAdminResponse(
                r.getId(),
                r.getReporterUserId(),
                r.getInstanceId(),
                r.getFloorNumber(),
                r.getTargetOccupantId(),
                r.getVisitorNo(),
                visitorTag,
                r.getClientIp(),
                r.getDeviceId(),
                r.getUserAgent(),
                r.getReason(),
                r.getRecentMessages(),
                r.getCreatedAt());
    }
}
