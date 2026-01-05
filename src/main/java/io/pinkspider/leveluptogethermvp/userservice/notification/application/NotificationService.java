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
        return createNotification(userId, type, title, message, referenceType, referenceId, actionUrl, null);
    }

    @Transactional
    public NotificationResponse createNotification(String userId, NotificationType type,
                                                    String title, String message,
                                                    String referenceType, Long referenceId,
                                                    String actionUrl, String iconUrl) {
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
            .iconUrl(iconUrl)
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

    // 참조 정보로 알림 삭제 (친구 요청 등 처리 후 알림 제거용)
    @Transactional
    public int deleteByReference(String referenceType, Long referenceId) {
        int count = notificationRepository.deleteByReference(referenceType, referenceId);
        if (count > 0) {
            log.info("참조 정보로 알림 삭제: referenceType={}, referenceId={}, count={}",
                referenceType, referenceId, count);
        }
        return count;
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
        if (request.getFriendNotifications() != null) pref.setFriendNotifications(request.getFriendNotifications());
        if (request.getGuildNotifications() != null) pref.setGuildNotifications(request.getGuildNotifications());
        if (request.getSocialNotifications() != null) pref.setSocialNotifications(request.getSocialNotifications());
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

    // 친구 요청 알림
    @Transactional
    public void notifyFriendRequest(String userId, String requesterNickname, Long friendshipId) {
        createNotification(userId, NotificationType.FRIEND_REQUEST,
            "새 친구 요청",
            requesterNickname + "님이 친구 요청을 보냈습니다.",
            "FRIEND_REQUEST", friendshipId, "/mypage/friends/requests");
    }

    // 친구 수락 알림
    @Transactional
    public void notifyFriendAccepted(String userId, String accepterNickname, Long friendshipId) {
        createNotification(userId, NotificationType.FRIEND_ACCEPTED,
            "친구 요청 수락",
            accepterNickname + "님이 친구 요청을 수락했습니다.",
            "FRIEND", friendshipId, "/mypage/friends");
    }

    // 친구 거절 알림
    @Transactional
    public void notifyFriendRejected(String userId, String rejecterNickname, Long friendshipId) {
        createNotification(userId, NotificationType.FRIEND_REJECTED,
            "친구 요청 거절",
            rejecterNickname + "님이 친구 요청을 거절했습니다.",
            "FRIEND_REQUEST", friendshipId, "/mypage/friends");
    }

    // 길드 초대 알림
    @Transactional
    public void notifyGuildInvite(String userId, String guildName, Long guildId) {
        createNotification(userId, NotificationType.GUILD_INVITE,
            "길드 초대",
            "'" + guildName + "' 길드에 초대되었습니다.",
            "GUILD", guildId, "/guild/" + guildId);
    }

    // 길드 가입 신청 알림 (길드장에게)
    @Transactional
    public void notifyGuildJoinRequest(String guildLeaderId, String applicantNickname, Long guildId) {
        createNotification(guildLeaderId, NotificationType.GUILD_JOIN_REQUEST,
            "길드 가입 신청",
            applicantNickname + "님이 길드 가입을 신청했습니다.",
            "GUILD", guildId, "/guild/" + guildId + "/members");
    }

    // 길드 가입 승인 알림
    @Transactional
    public void notifyGuildJoinApproved(String userId, String guildName, Long guildId) {
        createNotification(userId, NotificationType.GUILD_JOIN_APPROVED,
            "길드 가입 승인",
            "'" + guildName + "' 길드에 가입되었습니다!",
            "GUILD", guildId, "/guild/" + guildId);
    }

    // 길드 가입 거절 알림
    @Transactional
    public void notifyGuildJoinRejected(String userId, String guildName, Long guildId) {
        createNotification(userId, NotificationType.GUILD_JOIN_REJECTED,
            "길드 가입 거절",
            "'" + guildName + "' 길드 가입이 거절되었습니다.",
            "GUILD", guildId, "/guild");
    }

    // 길드 미션 도착 알림
    @Transactional
    public void notifyGuildMissionArrived(String userId, String missionTitle, Long missionId) {
        createNotification(userId, NotificationType.GUILD_MISSION_ARRIVED,
            "새 길드 미션",
            "'" + missionTitle + "' 길드 미션이 도착했습니다.",
            "MISSION", missionId, "/mission");
    }

    // 내 글에 댓글 알림
    @Transactional
    public void notifyCommentOnMyFeed(String feedOwnerId, String commenterNickname, Long feedId) {
        createNotification(feedOwnerId, NotificationType.COMMENT_ON_MY_FEED,
            "새 댓글",
            commenterNickname + "님이 회원님의 글에 댓글을 남겼습니다.",
            "FEED", feedId, "/feed/" + feedId);
    }

    // 칭호 획득 알림
    @Transactional
    public void notifyTitleAcquired(String userId, Long titleId, String titleName, String titleRarity) {
        // iconUrl 필드에 rarity 정보를 저장 (프론트엔드에서 모달 표시에 활용)
        String rarityMetadata = "rarity:" + titleRarity;
        createNotification(userId, NotificationType.TITLE_ACQUIRED,
            "🏆 새로운 칭호 획득!",
            "'" + titleName + "' 칭호를 획득했습니다!",
            "TITLE", titleId, "/mypage/titles", rarityMetadata);
    }

    // 업적 달성 알림
    @Transactional
    public void notifyAchievementCompleted(String userId, Long achievementId, String achievementName) {
        createNotification(userId, NotificationType.ACHIEVEMENT_COMPLETED,
            "🎯 업적 달성!",
            "'" + achievementName + "' 업적을 달성했습니다!",
            "ACHIEVEMENT", achievementId, "/mypage/achievements");
    }
}
