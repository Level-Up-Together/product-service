package io.pinkspider.leveluptogethermvp.notificationservice.application;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * FCM 푸시 알림 통합 테스트
 * <p>
 * 실제 Firebase로 푸시를 전송하는 테스트입니다. 테스트 실행 전 아래 사항을 확인하세요: 1. src/main/resources/firebase-service-account.json 파일 존재 2. 아래 FCM_TOKEN을 실제 앱에서 발급받은 토큰으로
 * 교체
 *
 * @Disabled 어노테이션을 제거하고 실행하세요.
 */
@Disabled
class FcmPushIntegrationTest {

    // ⚠️ 실제 앱에서 발급받은 FCM 토큰으로 교체하세요
    private static final String FCM_TOKEN = "여기에_앱에서_발급받은_FCM_토큰을_입력하세요";

    @BeforeAll
    static void initFirebase() throws IOException {
        if (FirebaseApp.getApps().isEmpty()) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(
                new ClassPathResource("firebase-service-account.json").getInputStream()
            );

            FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .build();

            FirebaseApp.initializeApp(options);
            System.out.println("✅ Firebase initialized successfully");
        }
    }

    @Test
    @Disabled("실제 푸시 전송 테스트 - FCM_TOKEN 설정 후 @Disabled 제거")
    void sendPushNotification_실제전송테스트() throws FirebaseMessagingException {
        // Given
        Message message = Message.builder()
            .setToken(FCM_TOKEN)
            .setNotification(Notification.builder()
                .setTitle("테스트 알림 🔔")
                .setBody("Level Up Together 푸시 알림 테스트입니다!")
                .build())
            .putAllData(Map.of(
                "notification_type", "TEST",
                "click_action", "/home"
            ))
            // iOS 설정
            .setApnsConfig(ApnsConfig.builder()
                .setAps(Aps.builder()
                    .setBadge(1)
                    .setSound("default")
                    .build())
                .build())
            // Android 설정
            .setAndroidConfig(AndroidConfig.builder()
                .setNotification(AndroidNotification.builder()
                    .setSound("default")
                    .build())
                .build())
            .build();

        // When
        String response = FirebaseMessaging.getInstance().send(message);

        // Then
        System.out.println("✅ 푸시 전송 성공!");
        System.out.println("Response: " + response);
    }

    @Test
    @Disabled("친구 요청 알림 테스트 - FCM_TOKEN 설정 후 @Disabled 제거")
    void sendFriendRequestNotification() throws FirebaseMessagingException {
        Message message = Message.builder()
            .setToken(FCM_TOKEN)
            .setNotification(Notification.builder()
                .setTitle("새 친구 요청")
                .setBody("테스트유저님이 친구 요청을 보냈습니다.")
                .build())
            .putAllData(Map.of(
                "notification_type", "FRIEND_REQUEST",
                "reference_type", "FRIEND_REQUEST",
                "reference_id", "123",
                "action_url", "/mypage/friends/requests"
            ))
            .setApnsConfig(ApnsConfig.builder()
                .setAps(Aps.builder()
                    .setBadge(1)
                    .setSound("default")
                    .build())
                .build())
            .build();

        String response = FirebaseMessaging.getInstance().send(message);
        System.out.println("✅ 친구 요청 알림 전송 성공: " + response);
    }

    @Test
    @Disabled("길드 채팅 알림 테스트 - FCM_TOKEN 설정 후 @Disabled 제거")
    void sendGuildChatNotification() throws FirebaseMessagingException {
        Message message = Message.builder()
            .setToken(FCM_TOKEN)
            .setNotification(Notification.builder()
                .setTitle("테스트 길드")
                .setBody("길드원: 안녕하세요! 오늘 미션 같이 해요~")
                .build())
            .putAllData(Map.of(
                "notification_type", "GUILD_CHAT",
                "reference_type", "GUILD_CHAT",
                "guild_id", "1",
                "action_url", "/guild/1/chat"
            ))
            .setApnsConfig(ApnsConfig.builder()
                .setAps(Aps.builder()
                    .setBadge(3)
                    .setSound("default")
                    .build())
                .build())
            .build();

        String response = FirebaseMessaging.getInstance().send(message);
        System.out.println("✅ 길드 채팅 알림 전송 성공: " + response);
    }

    @Test
    @Disabled("댓글 알림 테스트 - FCM_TOKEN 설정 후 @Disabled 제거")
    void sendCommentNotification() throws FirebaseMessagingException {
        Message message = Message.builder()
            .setToken(FCM_TOKEN)
            .setNotification(Notification.builder()
                .setTitle("새 댓글")
                .setBody("친구님이 회원님의 글에 댓글을 남겼습니다.")
                .build())
            .putAllData(Map.of(
                "notification_type", "COMMENT_ON_MY_FEED",
                "reference_type", "FEED",
                "reference_id", "456",
                "action_url", "/feed/456"
            ))
            .setApnsConfig(ApnsConfig.builder()
                .setAps(Aps.builder()
                    .setBadge(2)
                    .setSound("default")
                    .build())
                .build())
            .build();

        String response = FirebaseMessaging.getInstance().send(message);
        System.out.println("✅ 댓글 알림 전송 성공: " + response);
    }

    @Test
    @Disabled("토큰 유효성 검사 - FCM_TOKEN 설정 후 @Disabled 제거")
    void validateToken() {
        try {
            // Dry run으로 토큰 유효성만 검사 (실제 전송 안 함)
            Message message = Message.builder()
                .setToken(FCM_TOKEN)
                .setNotification(Notification.builder()
                    .setTitle("Test")
                    .setBody("Test")
                    .build())
                .build();

            String response = FirebaseMessaging.getInstance().send(message, true); // dryRun = true
            System.out.println("✅ 토큰 유효함: " + response);
        } catch (FirebaseMessagingException e) {
            System.out.println("❌ 토큰 무효: " + e.getMessagingErrorCode());
            System.out.println("Error: " + e.getMessage());
        }
    }
}
