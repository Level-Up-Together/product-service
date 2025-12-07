package io.pinkspider.leveluptogethermvp.userservice.notification.domain.dto;

import io.pinkspider.leveluptogethermvp.userservice.notification.domain.entity.NotificationPreference;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreferenceResponse {
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

    public static NotificationPreferenceResponse from(NotificationPreference pref) {
        return NotificationPreferenceResponse.builder()
            .pushEnabled(pref.getPushEnabled())
            .missionNotifications(pref.getMissionNotifications())
            .achievementNotifications(pref.getAchievementNotifications())
            .guildNotifications(pref.getGuildNotifications())
            .questNotifications(pref.getQuestNotifications())
            .attendanceNotifications(pref.getAttendanceNotifications())
            .rankingNotifications(pref.getRankingNotifications())
            .systemNotifications(pref.getSystemNotifications())
            .quietHoursEnabled(pref.getQuietHoursEnabled())
            .quietHoursStart(pref.getQuietHoursStart())
            .quietHoursEnd(pref.getQuietHoursEnd())
            .build();
    }
}
