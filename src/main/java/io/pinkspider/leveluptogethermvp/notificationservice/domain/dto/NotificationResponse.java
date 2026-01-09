package io.pinkspider.leveluptogethermvp.notificationservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.entity.Notification;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.enums.NotificationType;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class NotificationResponse {
    private Long id;
    private NotificationType notificationType;
    private String category;
    private String title;
    private String message;
    private String referenceType;
    private Long referenceId;
    private String actionUrl;
    private String iconUrl;
    private Boolean isRead;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;

    public static NotificationResponse from(Notification notification) {
        return NotificationResponse.builder()
            .id(notification.getId())
            .notificationType(notification.getNotificationType())
            .category(notification.getCategory())
            .title(notification.getTitle())
            .message(notification.getMessage())
            .referenceType(notification.getReferenceType())
            .referenceId(notification.getReferenceId())
            .actionUrl(notification.getActionUrl())
            .iconUrl(notification.getIconUrl())
            .isRead(notification.getIsRead())
            .readAt(notification.getReadAt())
            .createdAt(notification.getCreatedAt())
            .build();
    }
}
