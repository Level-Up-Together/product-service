package io.pinkspider.leveluptogethermvp.notificationservice.application;

import com.google.firebase.messaging.*;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.PushMessageRequest;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.entity.DeviceToken;
import io.pinkspider.leveluptogethermvp.notificationservice.infrastructure.DeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * FCM 푸시 알림 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FcmPushService {

    private final FirebaseMessaging firebaseMessaging;
    private final DeviceTokenRepository deviceTokenRepository;

    /**
     * 단일 사용자에게 푸시 알림 전송
     */
    @Transactional(transactionManager = "notificationTransactionManager")
    public void sendToUser(String userId, PushMessageRequest request) {
        if (firebaseMessaging == null) {
            log.warn("Firebase is not initialized. Skipping push notification.");
            return;
        }

        List<DeviceToken> tokens = deviceTokenRepository.findByUserIdAndIsActiveTrue(userId);
        if (tokens.isEmpty()) {
            log.debug("No active device tokens found for user: {}", userId);
            return;
        }

        // 배지 카운트 증가
        deviceTokenRepository.incrementBadgeCountByUserId(userId);

        for (DeviceToken token : tokens) {
            try {
                Message message = buildMessage(token, request);
                String response = firebaseMessaging.send(message);
                log.debug("Successfully sent message to user {}: {}", userId, response);
            } catch (FirebaseMessagingException e) {
                handleSendError(token, e);
            }
        }
    }

    /**
     * 여러 사용자에게 푸시 알림 전송
     */
    @Transactional(transactionManager = "notificationTransactionManager")
    public void sendToUsers(List<String> userIds, PushMessageRequest request) {
        if (firebaseMessaging == null) {
            log.warn("Firebase is not initialized. Skipping push notification.");
            return;
        }

        List<DeviceToken> tokens = deviceTokenRepository.findActiveTokensByUserIds(userIds);
        if (tokens.isEmpty()) {
            log.debug("No active device tokens found for users: {}", userIds);
            return;
        }

        // 배지 카운트 증가
        for (String userId : userIds) {
            deviceTokenRepository.incrementBadgeCountByUserId(userId);
        }

        List<Message> messages = tokens.stream()
                .map(token -> buildMessage(token, request))
                .collect(Collectors.toList());

        try {
            BatchResponse batchResponse = firebaseMessaging.sendEach(messages);
            log.info("Batch send completed. Success: {}, Failure: {}",
                    batchResponse.getSuccessCount(), batchResponse.getFailureCount());

            // 실패한 토큰 처리
            handleBatchErrors(tokens, batchResponse);
        } catch (FirebaseMessagingException e) {
            log.error("Failed to send batch messages", e);
        }
    }

    /**
     * 토픽으로 푸시 알림 전송 (길드 채팅 등)
     */
    public void sendToTopic(String topic, PushMessageRequest request) {
        if (firebaseMessaging == null) {
            log.warn("Firebase is not initialized. Skipping push notification.");
            return;
        }

        try {
            Message message = Message.builder()
                    .setTopic(topic)
                    .setNotification(Notification.builder()
                            .setTitle(request.title())
                            .setBody(request.body())
                            .setImage(request.imageUrl())
                            .build())
                    .putAllData(request.data() != null ? request.data() : Map.of())
                    .build();

            String response = firebaseMessaging.send(message);
            log.debug("Successfully sent message to topic {}: {}", topic, response);
        } catch (FirebaseMessagingException e) {
            log.error("Failed to send message to topic: {}", topic, e);
        }
    }

    /**
     * 사용자를 토픽에 구독
     */
    public void subscribeToTopic(String userId, String topic) {
        if (firebaseMessaging == null) {
            return;
        }

        List<DeviceToken> tokens = deviceTokenRepository.findByUserIdAndIsActiveTrue(userId);
        List<String> fcmTokens = tokens.stream()
                .map(DeviceToken::getFcmToken)
                .collect(Collectors.toList());

        if (fcmTokens.isEmpty()) {
            return;
        }

        try {
            TopicManagementResponse response = firebaseMessaging.subscribeToTopic(fcmTokens, topic);
            log.debug("Subscribed {} tokens to topic {}. Success: {}, Failure: {}",
                    fcmTokens.size(), topic, response.getSuccessCount(), response.getFailureCount());
        } catch (FirebaseMessagingException e) {
            log.error("Failed to subscribe to topic: {}", topic, e);
        }
    }

    /**
     * 사용자를 토픽에서 구독 해제
     */
    public void unsubscribeFromTopic(String userId, String topic) {
        if (firebaseMessaging == null) {
            return;
        }

        List<DeviceToken> tokens = deviceTokenRepository.findByUserIdAndIsActiveTrue(userId);
        List<String> fcmTokens = tokens.stream()
                .map(DeviceToken::getFcmToken)
                .collect(Collectors.toList());

        if (fcmTokens.isEmpty()) {
            return;
        }

        try {
            TopicManagementResponse response = firebaseMessaging.unsubscribeFromTopic(fcmTokens, topic);
            log.debug("Unsubscribed {} tokens from topic {}. Success: {}, Failure: {}",
                    fcmTokens.size(), topic, response.getSuccessCount(), response.getFailureCount());
        } catch (FirebaseMessagingException e) {
            log.error("Failed to unsubscribe from topic: {}", topic, e);
        }
    }

    /**
     * FCM 메시지 빌드
     */
    private Message buildMessage(DeviceToken token, PushMessageRequest request) {
        Message.Builder builder = Message.builder()
                .setToken(token.getFcmToken())
                .setNotification(Notification.builder()
                        .setTitle(request.title())
                        .setBody(request.body())
                        .setImage(request.imageUrl())
                        .build());

        // 데이터 페이로드 추가
        if (request.data() != null && !request.data().isEmpty()) {
            builder.putAllData(request.data());
        }

        // 플랫폼별 설정
        if (token.getDeviceType() == DeviceToken.DeviceType.IOS) {
            builder.setApnsConfig(ApnsConfig.builder()
                    .setAps(Aps.builder()
                            .setBadge(token.getBadgeCount() + 1)
                            .setSound("default")
                            .build())
                    .build());
        } else {
            builder.setAndroidConfig(AndroidConfig.builder()
                    .setNotification(AndroidNotification.builder()
                            .setSound("default")
                            .setClickAction(request.clickAction())
                            .build())
                    .build());
        }

        return builder.build();
    }

    /**
     * 단일 전송 에러 처리
     */
    private void handleSendError(DeviceToken token, FirebaseMessagingException e) {
        MessagingErrorCode errorCode = e.getMessagingErrorCode();

        if (errorCode == MessagingErrorCode.UNREGISTERED ||
            errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
            // 토큰이 더 이상 유효하지 않음
            log.warn("Invalid token detected, deactivating: {}", token.getFcmToken());
            token.deactivate();
            deviceTokenRepository.save(token);
        } else {
            log.error("Failed to send message to token: {}", token.getFcmToken(), e);
        }
    }

    /**
     * 배치 전송 에러 처리
     */
    private void handleBatchErrors(List<DeviceToken> tokens, BatchResponse batchResponse) {
        List<SendResponse> responses = batchResponse.getResponses();
        List<DeviceToken> tokensToDeactivate = new ArrayList<>();

        for (int i = 0; i < responses.size(); i++) {
            SendResponse response = responses.get(i);
            if (!response.isSuccessful()) {
                FirebaseMessagingException exception = response.getException();
                if (exception != null) {
                    MessagingErrorCode errorCode = exception.getMessagingErrorCode();
                    if (errorCode == MessagingErrorCode.UNREGISTERED ||
                        errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                        tokensToDeactivate.add(tokens.get(i));
                    }
                }
            }
        }

        // 유효하지 않은 토큰 비활성화
        if (!tokensToDeactivate.isEmpty()) {
            tokensToDeactivate.forEach(DeviceToken::deactivate);
            deviceTokenRepository.saveAll(tokensToDeactivate);
            log.info("Deactivated {} invalid tokens", tokensToDeactivate.size());
        }
    }
}
