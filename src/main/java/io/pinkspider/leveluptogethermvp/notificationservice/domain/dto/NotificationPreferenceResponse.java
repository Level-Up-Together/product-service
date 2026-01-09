package io.pinkspider.leveluptogethermvp.notificationservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.entity.NotificationPreference;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class NotificationPreferenceResponse {
    private Boolean pushEnabled;
    private Boolean friendNotifications;
    private Boolean guildNotifications;
    private Boolean socialNotifications;
    private Boolean systemNotifications;
    private Boolean quietHoursEnabled;
    private String quietHoursStart;
    private String quietHoursEnd;

    public static NotificationPreferenceResponse from(NotificationPreference pref) {
        return NotificationPreferenceResponse.builder()
            .pushEnabled(pref.getPushEnabled())
            .friendNotifications(pref.getFriendNotifications())
            .guildNotifications(pref.getGuildNotifications())
            .socialNotifications(pref.getSocialNotifications())
            .systemNotifications(pref.getSystemNotifications())
            .quietHoursEnabled(pref.getQuietHoursEnabled())
            .quietHoursStart(pref.getQuietHoursStart())
            .quietHoursEnd(pref.getQuietHoursEnd())
            .build();
    }
}
