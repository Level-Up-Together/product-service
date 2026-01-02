package io.pinkspider.leveluptogethermvp.userservice.notification.application;

import io.pinkspider.leveluptogethermvp.userservice.notification.domain.dto.NotificationPreferenceRequest;
import io.pinkspider.leveluptogethermvp.userservice.notification.domain.dto.NotificationPreferenceResponse;
import io.pinkspider.leveluptogethermvp.userservice.notification.domain.dto.NotificationResponse;
import io.pinkspider.leveluptogethermvp.userservice.notification.domain.dto.NotificationSummaryResponse;
import io.pinkspider.leveluptogethermvp.userservice.notification.domain.entity.Notification;
import io.pinkspider.leveluptogethermvp.userservice.notification.domain.entity.NotificationPreference;
import io.pinkspider.leveluptogethermvp.userservice.notification.domain.enums.NotificationType;
import io.pinkspider.leveluptogethermvp.userservice.notification.infrastructure.NotificationPreferenceRepository;
import io.pinkspider.leveluptogethermvp.userservice.notification.infrastructure.NotificationRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;

    // 알림 생성
    @Transactional
    public NotificationResponse createNotification(String userId, NotificationType type,
                                                    String title, String message) {
        return createNotification(userId, type, title, message, null, null, null);
    }

    @Transactional
    public NotificationResponse createNotification(String userId, NotificationType type,
                                                    String title, String message,
                                                    String referenceType, Long referenceId,
                                                    String actionUrl) {
        // 알림 설정 확인
        NotificationPreference pref = getOrCreatePreference(userId);
        if (!pref.isCategoryEnabled(type.getCategory())) {
            log.debug("알림 비활성화됨: userId={}, type={}", userId, type);
            return null;
        }

        Notification notification = Notification.builder()
            .userId(userId)
            .notificationType(type)
            .title(title)
            .message(message)
            .referenceType(referenceType)
            .referenceId(referenceId)
            .actionUrl(actionUrl)
            .build();

        Notification saved = notificationRepository.save(notification);
        log.info("알림 생성: userId={}, type={}, title={}", userId, type, title);

        return NotificationResponse.from(saved);
    }

    // 알림 목록 조회
    public Page<NotificationResponse> getNotifications(String userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
            .map(NotificationResponse::from);
    }

    // 읽지 않은 알림 조회
    public List<NotificationResponse> getUnreadNotifications(String userId) {
        return notificationRepository.findUnreadByUserId(userId).stream()
            .map(NotificationResponse::from)
            .toList();
    }

    // 알림 요약 조회
    public NotificationSummaryResponse getNotificationSummary(String userId) {
        int unreadCount = notificationRepository.countUnreadByUserId(userId);
        return NotificationSummaryResponse.builder()
            .unreadCount(unreadCount)
            .build();
    }

    // 알림 읽음 처리
    @Transactional
    public NotificationResponse markAsRead(String userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new IllegalArgumentException("알림을 찾을 수 없습니다."));

        if (!notification.getUserId().equals(userId)) {
            throw new IllegalStateException("본인의 알림만 읽음 처리할 수 있습니다.");
        }

        notification.markAsRead();
        return NotificationResponse.from(notification);
    }

    // 모든 알림 읽음 처리
    @Transactional
    public int markAllAsRead(String userId) {
        int count = notificationRepository.markAllAsRead(userId);
        log.info("모든 알림 읽음 처리: userId={}, count={}", userId, count);
        return count;
    }

    // 알림 삭제
    @Transactional
    public void deleteNotification(String userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new IllegalArgumentException("알림을 찾을 수 없습니다."));

        if (!notification.getUserId().equals(userId)) {
            throw new IllegalStateException("본인의 알림만 삭제할 수 있습니다.");
        }

        notificationRepository.delete(notification);
        log.info("알림 삭제: userId={}, notificationId={}", userId, notificationId);
    }

    // 알림 설정 조회
    public NotificationPreferenceResponse getPreferences(String userId) {
        NotificationPreference pref = getOrCreatePreference(userId);
        return NotificationPreferenceResponse.from(pref);
    }

    // 알림 설정 수정
    @Transactional
    public NotificationPreferenceResponse updatePreferences(String userId,
                                                             NotificationPreferenceRequest request) {
        NotificationPreference pref = getOrCreatePreference(userId);

        if (request.getPushEnabled() != null) pref.setPushEnabled(request.getPushEnabled());
        if (request.getMissionNotifications() != null) pref.setMissionNotifications(request.getMissionNotifications());
        if (request.getAchievementNotifications() != null) pref.setAchievementNotifications(request.getAchievementNotifications());
        if (request.getGuildNotifications() != null) pref.setGuildNotifications(request.getGuildNotifications());
        if (request.getQuestNotifications() != null) pref.setQuestNotifications(request.getQuestNotifications());
        if (request.getAttendanceNotifications() != null) pref.setAttendanceNotifications(request.getAttendanceNotifications());
        if (request.getRankingNotifications() != null) pref.setRankingNotifications(request.getRankingNotifications());
        if (request.getSystemNotifications() != null) pref.setSystemNotifications(request.getSystemNotifications());
        if (request.getQuietHoursEnabled() != null) pref.setQuietHoursEnabled(request.getQuietHoursEnabled());
        if (request.getQuietHoursStart() != null) pref.setQuietHoursStart(request.getQuietHoursStart());
        if (request.getQuietHoursEnd() != null) pref.setQuietHoursEnd(request.getQuietHoursEnd());

        log.info("알림 설정 수정: userId={}", userId);
        return NotificationPreferenceResponse.from(pref);
    }

    // 만료된 알림 정리
    @Transactional
    public int cleanupExpiredNotifications() {
        int count = notificationRepository.deleteExpiredNotifications(LocalDateTime.now());
        if (count > 0) {
            log.info("만료된 알림 삭제: count={}", count);
        }
        return count;
    }

    private NotificationPreference getOrCreatePreference(String userId) {
        return preferenceRepository.findByUserId(userId)
            .orElseGet(() -> {
                NotificationPreference newPref = NotificationPreference.createDefault(userId);
                return preferenceRepository.save(newPref);
            });
    }

    // ==================== 편의 메서드 ====================

    @Transactional
    public void notifyMissionCompleted(String userId, String missionTitle, Long missionId) {
        createNotification(userId, NotificationType.MISSION_COMPLETED,
            "미션 완료!",
            "'" + missionTitle + "' 미션을 완료했습니다.",
            "MISSION", missionId, "/mission");
    }

    @Transactional
    public void notifyAchievementUnlocked(String userId, String achievementName, Long achievementId) {
        createNotification(userId, NotificationType.ACHIEVEMENT_UNLOCKED,
            "업적 달성!",
            "'" + achievementName + "' 업적을 달성했습니다!",
            "ACHIEVEMENT", achievementId, "/achievements");
    }

    @Transactional
    public void notifyTitleAcquired(String userId, String titleName, Long titleId) {
        createNotification(userId, NotificationType.TITLE_ACQUIRED,
            "새 칭호 획득!",
            "'" + titleName + "' 칭호를 획득했습니다!",
            "TITLE", titleId, "/achievements/titles");
    }

    @Transactional
    public void notifyLevelUp(String userId, int newLevel) {
        createNotification(userId, NotificationType.LEVEL_UP,
            "레벨 업!",
            "레벨 " + newLevel + "에 도달했습니다!",
            "LEVEL", null, "/profile");
    }

    @Transactional
    public void notifyQuestCompleted(String userId, String questName, Long questId) {
        createNotification(userId, NotificationType.QUEST_COMPLETED,
            "퀘스트 완료!",
            "'" + questName + "' 퀘스트를 완료했습니다. 보상을 수령하세요!",
            "QUEST", questId, "/quests");
    }

    @Transactional
    public void notifyGuildInvite(String userId, String guildName, Long guildId) {
        createNotification(userId, NotificationType.GUILD_INVITE,
            "길드 초대",
            "'" + guildName + "' 길드에 초대되었습니다.",
            "GUILD", guildId, "/guilds/" + guildId);
    }

    @Transactional
    public void notifyGuildJoinApproved(String userId, String guildName, Long guildId) {
        createNotification(userId, NotificationType.GUILD_JOIN_APPROVED,
            "길드 가입 승인",
            "'" + guildName + "' 길드에 가입되었습니다!",
            "GUILD", guildId, "/guilds/" + guildId);
    }

    @Transactional
    public void notifyAttendanceStreak(String userId, int streak) {
        createNotification(userId, NotificationType.ATTENDANCE_STREAK,
            streak + "일 연속 출석!",
            "대단해요! " + streak + "일 연속 출석을 달성했습니다!",
            "ATTENDANCE", null, "/attendance");
    }
}
