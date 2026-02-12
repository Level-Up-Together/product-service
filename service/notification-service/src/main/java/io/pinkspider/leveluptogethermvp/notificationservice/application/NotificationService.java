package io.pinkspider.leveluptogethermvp.notificationservice.application;

import io.pinkspider.global.messaging.dto.AppPushMessageDto;
import io.pinkspider.global.messaging.producer.AppPushMessageProducer;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.NotificationPreferenceRequest;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.NotificationPreferenceResponse;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.NotificationResponse;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.NotificationSummaryResponse;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.entity.Notification;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.entity.NotificationPreference;
import io.pinkspider.global.enums.NotificationType;
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
    private final AppPushMessageProducer appPushMessageProducer;

    // 알림 생성
    @Transactional(transactionManager = "notificationTransactionManager")
    public NotificationResponse createNotification(String userId, NotificationType type,
                                                    String title, String message) {
        return createNotificationInternal(userId, type, title, message, null, null, null, null, false);
    }

    @Transactional(transactionManager = "notificationTransactionManager")
    public NotificationResponse createNotification(String userId, NotificationType type,
                                                    String title, String message,
                                                    String referenceType, Long referenceId,
                                                    String actionUrl) {
        return createNotificationInternal(userId, type, title, message, referenceType, referenceId, actionUrl, null, false);
    }

    @Transactional(transactionManager = "notificationTransactionManager")
    public NotificationResponse createNotification(String userId, NotificationType type,
                                                    String title, String message,
                                                    String referenceType, Long referenceId,
                                                    String actionUrl, String iconUrl) {
        return createNotificationInternal(userId, type, title, message, referenceType, referenceId, actionUrl, iconUrl, false);
    }

    /**
     * NotificationType 메타데이터 기반 알림 생성.
     * 중복 방지 타입(requiresDeduplication=true)은 자동으로 pre-check + saveAndFlush 처리.
     *
     * @param iconUrl     아이콘 URL (칭호의 경우 "rarity:LEGENDARY" 형태), null 가능
     * @param messageArgs 메시지/제목 템플릿의 {0}, {1}, ... 인자 + actionUrl의 {0}, {1}, ... 치환용
     */
    @Transactional(transactionManager = "notificationTransactionManager")
    public void sendNotification(String userId, NotificationType type,
                                  Long referenceId, String iconUrl, Object... messageArgs) {
        String title = type.formatTitle(messageArgs);
        String message = type.formatMessage(messageArgs);
        String referenceType = type.getReferenceType();
        String actionUrl = type.resolveActionUrl(referenceId, messageArgs);

        if (type.isRequiresDeduplication()) {
            if (notificationRepository.existsByUserIdAndReferenceTypeAndReferenceId(
                    userId, referenceType, referenceId)) {
                log.debug("알림 중복 방지: userId={}, type={}, referenceId={}", userId, type, referenceId);
                return;
            }
            try {
                createNotificationInternal(userId, type, title, message,
                    referenceType, referenceId, actionUrl, iconUrl, true);
            } catch (DataIntegrityViolationException e) {
                log.debug("알림 중복 감지 (DB 제약조건): userId={}, type={}", userId, type);
            }
        } else {
            createNotificationInternal(userId, type, title, message,
                referenceType, referenceId, actionUrl, iconUrl, false);
        }
    }

    /**
     * 푸시 알림 전송 (Redis Stream 통해 비동기 처리)
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

            appPushMessageProducer.sendMessage(pushMessage);
            log.debug("푸시 알림 전송: userId={}, title={}", userId, title);
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

    private NotificationResponse createNotificationInternal(String userId, NotificationType type,
                                                             String title, String message,
                                                             String referenceType, Long referenceId,
                                                             String actionUrl, String iconUrl,
                                                             boolean flush) {
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

        Notification saved = flush
            ? notificationRepository.saveAndFlush(notification)
            : notificationRepository.save(notification);
        log.info("알림 생성: userId={}, type={}, title={}", userId, type, title);

        if (pref.getPushEnabled()) {
            sendPushNotification(userId, title, message, type.name(), referenceType, referenceId, actionUrl);
        }

        return NotificationResponse.from(saved);
    }

    // ==================== 편의 메서드 (특수 케이스) ====================

    // 콘텐츠 신고 알림 (신고 당한 유저에게)
    @Transactional(transactionManager = "notificationTransactionManager")
    public void notifyContentReported(String userId, String targetTypeDescription) {
        createNotification(userId, NotificationType.CONTENT_REPORTED,
            "콘텐츠 신고 알림",
            "회원님의 " + targetTypeDescription + "이(가) 다른 사용자로부터 신고되었습니다.",
            null, null, "/mypage");
    }

    // 길드 콘텐츠 신고 알림 (길드 마스터에게)
    @Transactional(transactionManager = "notificationTransactionManager")
    public void notifyGuildContentReported(String guildMasterId, String targetTypeDescription, Long guildId) {
        createNotification(guildMasterId, NotificationType.CONTENT_REPORTED,
            "길드 콘텐츠 신고 알림",
            "길드 내 " + targetTypeDescription + "이(가) 신고되었습니다. 길드 관리에 참고해주세요.",
            "GUILD", guildId, "/guild/" + guildId);
    }
}
