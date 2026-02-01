package io.pinkspider.leveluptogethermvp.notificationservice.application;

import io.pinkspider.global.kafka.dto.AppPushMessageDto;
import io.pinkspider.global.kafka.producer.KafkaAppPushProducer;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.NotificationPreferenceRequest;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.NotificationPreferenceResponse;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.NotificationResponse;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.NotificationSummaryResponse;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.entity.Notification;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.entity.NotificationPreference;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.enums.NotificationType;
import io.pinkspider.leveluptogethermvp.notificationservice.infrastructure.NotificationPreferenceRepository;
import io.pinkspider.leveluptogethermvp.notificationservice.infrastructure.NotificationRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(transactionManager = "notificationTransactionManager", readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final KafkaAppPushProducer kafkaAppPushProducer;

    // 알림 생성
    @Transactional(transactionManager = "notificationTransactionManager")
    public NotificationResponse createNotification(String userId, NotificationType type,
                                                    String title, String message) {
        return createNotification(userId, type, title, message, null, null, null);
    }

    @Transactional(transactionManager = "notificationTransactionManager")
    public NotificationResponse createNotification(String userId, NotificationType type,
                                                    String title, String message,
                                                    String referenceType, Long referenceId,
                                                    String actionUrl) {
        return createNotification(userId, type, title, message, referenceType, referenceId, actionUrl, null);
    }

    @Transactional(transactionManager = "notificationTransactionManager")
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

        // 푸시 알림 전송 (푸시 설정이 활성화된 경우)
        if (pref.getPushEnabled()) {
            sendPushNotification(userId, title, message, type.name(), referenceType, referenceId, actionUrl);
        }

        return NotificationResponse.from(saved);
    }

    /**
     * 푸시 알림 전송 (Kafka 통해 비동기 처리)
     */
    private void sendPushNotification(String userId, String title, String body,
                                      String notificationType, String referenceType,
                                      Long referenceId, String actionUrl) {
        try {
            AppPushMessageDto pushMessage = AppPushMessageDto.builder()
                .userId(userId)
                .title(title)
                .body(body)
                .notificationType(notificationType)
                .clickAction(actionUrl)
                .data(Map.of(
                    "notification_type", notificationType,
                    "reference_type", referenceType != null ? referenceType : "",
                    "reference_id", referenceId != null ? referenceId.toString() : "",
                    "action_url", actionUrl != null ? actionUrl : ""
                ))
                .build();

            kafkaAppPushProducer.sendMessage(pushMessage);
            log.debug("푸시 알림 Kafka 전송: userId={}, title={}", userId, title);
        } catch (Exception e) {
            log.warn("푸시 알림 전송 실패: userId={}, error={}", userId, e.getMessage());
        }
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
    @Transactional(transactionManager = "notificationTransactionManager")
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
    @Transactional(transactionManager = "notificationTransactionManager")
    public int markAllAsRead(String userId) {
        int count = notificationRepository.markAllAsRead(userId);
        log.info("모든 알림 읽음 처리: userId={}, count={}", userId, count);
        return count;
    }

    // 알림 삭제
    @Transactional(transactionManager = "notificationTransactionManager")
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
    @Transactional(transactionManager = "notificationTransactionManager")
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
    @Transactional(transactionManager = "notificationTransactionManager")
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
    @Transactional(transactionManager = "notificationTransactionManager")
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
    @Transactional(transactionManager = "notificationTransactionManager")
    public void notifyFriendRequest(String userId, String requesterNickname, Long friendshipId) {
        createNotification(userId, NotificationType.FRIEND_REQUEST,
            "새 친구 요청",
            requesterNickname + "님이 친구 요청을 보냈습니다.",
            "FRIEND_REQUEST", friendshipId, "/mypage/friends/requests");
    }

    // 친구 수락 알림
    @Transactional(transactionManager = "notificationTransactionManager")
    public void notifyFriendAccepted(String userId, String accepterNickname, Long friendshipId) {
        createNotification(userId, NotificationType.FRIEND_ACCEPTED,
            "친구 요청 수락",
            accepterNickname + "님이 친구 요청을 수락했습니다.",
            "FRIEND", friendshipId, "/mypage/friends");
    }

    // 친구 거절 알림
    @Transactional(transactionManager = "notificationTransactionManager")
    public void notifyFriendRejected(String userId, String rejecterNickname, Long friendshipId) {
        createNotification(userId, NotificationType.FRIEND_REJECTED,
            "친구 요청 거절",
            rejecterNickname + "님이 친구 요청을 거절했습니다.",
            "FRIEND_REQUEST", friendshipId, "/mypage/friends");
    }

    // 길드 초대 알림
    @Transactional(transactionManager = "notificationTransactionManager")
    public void notifyGuildInvite(String userId, String guildName, Long guildId) {
        createNotification(userId, NotificationType.GUILD_INVITE,
            "길드 초대",
            "'" + guildName + "' 길드에 초대되었습니다.",
            "GUILD", guildId, "/guild/" + guildId);
    }

    // 길드 가입 신청 알림 (길드장에게)
    @Transactional(transactionManager = "notificationTransactionManager")
    public void notifyGuildJoinRequest(String guildLeaderId, String applicantNickname, Long guildId) {
        createNotification(guildLeaderId, NotificationType.GUILD_JOIN_REQUEST,
            "길드 가입 신청",
            applicantNickname + "님이 길드 가입을 신청했습니다.",
            "GUILD", guildId, "/guild/" + guildId + "/members");
    }

    // 길드 가입 승인 알림
    @Transactional(transactionManager = "notificationTransactionManager")
    public void notifyGuildJoinApproved(String userId, String guildName, Long guildId) {
        createNotification(userId, NotificationType.GUILD_JOIN_APPROVED,
            "길드 가입 승인",
            "'" + guildName + "' 길드에 가입되었습니다!",
            "GUILD", guildId, "/guild/" + guildId);
    }

    // 길드 가입 거절 알림
    @Transactional(transactionManager = "notificationTransactionManager")
    public void notifyGuildJoinRejected(String userId, String guildName, Long guildId) {
        createNotification(userId, NotificationType.GUILD_JOIN_REJECTED,
            "길드 가입 거절",
            "'" + guildName + "' 길드 가입이 거절되었습니다.",
            "GUILD", guildId, "/guild");
    }

    // 길드 미션 도착 알림
    @Transactional(transactionManager = "notificationTransactionManager")
    public void notifyGuildMissionArrived(String userId, String missionTitle, Long missionId) {
        createNotification(userId, NotificationType.GUILD_MISSION_ARRIVED,
            "새 길드 미션",
            "'" + missionTitle + "' 길드 미션이 도착했습니다.",
            "MISSION", missionId, "/mission");
    }

    // 길드 공지사항 알림
    @Transactional(transactionManager = "notificationTransactionManager")
    public void notifyGuildBulletin(String userId, String guildName, Long guildId, Long postId, String postTitle) {
        createNotification(userId, NotificationType.GUILD_BULLETIN,
            "새 길드 공지사항",
            "[" + guildName + "] " + postTitle,
            "GUILD_POST", postId, "/guild/" + guildId + "/posts/" + postId);
    }

    // 길드 채팅 알림
    @Transactional(transactionManager = "notificationTransactionManager")
    public void notifyGuildChat(String userId, String senderNickname, Long guildId,
                                String guildName, Long messageId, String messagePreview) {
        createNotification(userId, NotificationType.GUILD_CHAT,
            guildName,
            senderNickname + ": " + messagePreview,
            "GUILD_CHAT", messageId, "/guild/" + guildId + "/chat");
    }

    // 내 글에 댓글 알림
    @Transactional(transactionManager = "notificationTransactionManager")
    public void notifyCommentOnMyFeed(String feedOwnerId, String commenterNickname, Long feedId) {
        createNotification(feedOwnerId, NotificationType.COMMENT_ON_MY_FEED,
            "새 댓글",
            commenterNickname + "님이 회원님의 글에 댓글을 남겼습니다.",
            "FEED", feedId, "/feed/" + feedId);
    }

    // 내 미션에 댓글 알림
    @Transactional(transactionManager = "notificationTransactionManager")
    public void notifyCommentOnMyMission(String missionCreatorId, String commenterNickname,
                                         Long missionId, String missionTitle) {
        createNotification(missionCreatorId, NotificationType.COMMENT_ON_MY_MISSION,
            "새 댓글",
            commenterNickname + "님이 '" + missionTitle + "' 미션에 댓글을 남겼습니다.",
            "MISSION", missionId, "/mission/" + missionId);
    }

    // 칭호 획득 알림 (중복 방지 - 레이스 컨디션 안전)
    @Transactional(transactionManager = "notificationTransactionManager")
    public void notifyTitleAcquired(String userId, Long titleId, String titleName, String titleRarity) {
        // 동일한 칭호에 대한 알림이 이미 존재하면 스킵
        if (notificationRepository.existsByUserIdAndReferenceTypeAndReferenceId(
                userId, "TITLE", titleId)) {
            log.debug("칭호 획득 알림 중복 방지: userId={}, titleId={}", userId, titleId);
            return;
        }
        try {
            // iconUrl 필드에 rarity 정보를 저장 (프론트엔드에서 모달 표시에 활용)
            String rarityMetadata = "rarity:" + titleRarity;
            createNotificationWithDeduplication(userId, NotificationType.TITLE_ACQUIRED,
                "새로운 칭호 획득!",
                "'" + titleName + "' 칭호를 획득했습니다!",
                "TITLE", titleId, "/mypage/titles", rarityMetadata);
        } catch (DataIntegrityViolationException e) {
            // 레이스 컨디션으로 인한 중복 - 무시
            log.debug("칭호 획득 알림 중복 감지 (DB 제약조건): userId={}, titleId={}", userId, titleId);
        }
    }

    // 업적 달성 알림 (중복 방지 - 레이스 컨디션 안전)
    @Transactional(transactionManager = "notificationTransactionManager")
    public void notifyAchievementCompleted(String userId, Long achievementId, String achievementName) {
        // 동일한 업적에 대한 알림이 이미 존재하면 스킵
        if (notificationRepository.existsByUserIdAndReferenceTypeAndReferenceId(
                userId, "ACHIEVEMENT", achievementId)) {
            log.debug("업적 달성 알림 중복 방지: userId={}, achievementId={}", userId, achievementId);
            return;
        }
        try {
            createNotificationWithDeduplication(userId, NotificationType.ACHIEVEMENT_COMPLETED,
                "업적 달성!",
                "'" + achievementName + "' 업적을 달성했습니다!",
                "ACHIEVEMENT", achievementId, "/mypage", null);
        } catch (DataIntegrityViolationException e) {
            // 레이스 컨디션으로 인한 중복 - 무시
            log.debug("업적 달성 알림 중복 감지 (DB 제약조건): userId={}, achievementId={}", userId, achievementId);
        }
    }

    /**
     * 중복 방지가 필요한 알림 생성 (saveAndFlush 사용)
     */
    private NotificationResponse createNotificationWithDeduplication(String userId, NotificationType type,
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

        // saveAndFlush로 즉시 INSERT하여 중복 시 예외 발생
        Notification saved = notificationRepository.saveAndFlush(notification);
        log.info("알림 생성: userId={}, type={}, title={}", userId, type, title);

        // 푸시 알림 전송 (푸시 설정이 활성화된 경우)
        if (pref.getPushEnabled()) {
            sendPushNotification(userId, title, message, type.name(), referenceType, referenceId, actionUrl);
        }

        return NotificationResponse.from(saved);
    }

    // 길드 창설 가능 알림 (레벨 20 도달)
    @Transactional(transactionManager = "notificationTransactionManager")
    public void notifyGuildCreationEligible(String userId) {
        createNotification(userId, NotificationType.GUILD_CREATION_ELIGIBLE,
            "길드 창설이 가능해졌습니다!",
            "레벨 20에 도달하여 이제 나만의 길드를 만들 수 있습니다.",
            "LEVEL", null, "/guilds/create");
    }

    // 길드 초대 알림 (초대 ID 포함, 수락/거절 액션용)
    @Transactional(transactionManager = "notificationTransactionManager")
    public void notifyGuildInvitation(String inviteeId, String inviterNickname, Long guildId,
                                      String guildName, Long invitationId) {
        createNotification(inviteeId, NotificationType.GUILD_INVITE,
            "길드 초대",
            inviterNickname + "님이 '" + guildName + "' 길드로 초대했습니다.",
            "GUILD_INVITATION", invitationId, "/guild-invitations/" + invitationId);
    }

    // 가입 환영 알림
    @Transactional(transactionManager = "notificationTransactionManager")
    public void notifyWelcome(String userId, String nickname) {
        createNotification(userId, NotificationType.WELCOME,
            "Level Up Together에 오신 것을 환영합니다!",
            nickname + "님, 함께 성장하는 여정을 시작해보세요.",
            null, null, "/home");
    }
}
