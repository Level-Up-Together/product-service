package io.pinkspider.leveluptogethermvp.userservice.notification.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class NotificationPreferenceRequest {
    private Boolean pushEnabled;
    private Boolean friendNotifications;
    private Boolean guildNotifications;
    private Boolean socialNotifications;
    private Boolean systemNotifications;
    private Boolean quietHoursEnabled;
    private String quietHoursStart;
    private String quietHoursEnd;
}
