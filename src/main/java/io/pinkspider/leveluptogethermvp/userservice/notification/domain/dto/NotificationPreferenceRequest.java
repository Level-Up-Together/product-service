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
    private Boolean missionNotifications;
    private Boolean achievementNotifications;
    private Boolean guildNotifications;
    private Boolean questNotifications;
    private Boolean attendanceNotifications;
    private Boolean rankingNotifications;
    private Boolean systemNotifications;
    private Boolean quietHoursEnabled;
    private String quietHoursStart;
    private String quietHoursEnd;
}
