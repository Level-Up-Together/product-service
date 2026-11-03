package io.pinkspider.leveluptogethermvp.notificationservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.entity.AdminPushCampaign;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.enums.AdminPushCampaignStatus;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.enums.AdminPushTargetType;
import java.time.LocalDateTime;
import java.util.List;

/** LUT-508: 관리자 푸시 발송 이력 응답 */
@JsonNaming(SnakeCaseStrategy.class)
public record AdminPushCampaignResponse(
        Long id,
        String title,
        String body,
        String actionUrl,
        AdminPushTargetType targetType,
        List<String> targetUserIds,
        int targetCount,
        int sentCount,
        int skippedCount,
        int failedCount,
        AdminPushCampaignStatus status,
        Long requestedBy,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String errorMessage,
        LocalDateTime createdAt) {

    public static AdminPushCampaignResponse from(
            AdminPushCampaign campaign, List<String> targetUserIds) {
        return new AdminPushCampaignResponse(
                campaign.getId(),
                campaign.getTitle(),
                campaign.getBody(),
                campaign.getActionUrl(),
                campaign.getTargetType(),
                targetUserIds,
                campaign.getTargetCount(),
                campaign.getSentCount(),
                campaign.getSkippedCount(),
                campaign.getFailedCount(),
                campaign.getStatus(),
                campaign.getRequestedBy(),
                campaign.getStartedAt(),
                campaign.getCompletedAt(),
                campaign.getErrorMessage(),
                campaign.getCreatedAt());
    }
}
