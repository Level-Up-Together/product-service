package io.pinkspider.leveluptogethermvp.notificationservice.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.TopicManagementResponse;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.PushMessageRequest;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.entity.DeviceToken;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.entity.DeviceToken.DeviceType;
import io.pinkspider.leveluptogethermvp.notificationservice.infrastructure.DeviceTokenRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FcmPushServiceTest {

    @Mock
    private FirebaseMessaging firebaseMessaging;

    @Mock
    private DeviceTokenRepository deviceTokenRepository;

    @InjectMocks
    private FcmPushService fcmPushService;

    private String testUserId;
    private DeviceToken testDeviceToken;
    private PushMessageRequest testRequest;

    @BeforeEach
    void setUp() {
        testUserId = "test-user-id";

        testDeviceToken = DeviceToken.builder()
            .userId(testUserId)
            .fcmToken("test-fcm-token")
            .deviceType(DeviceType.ANDROID)
            .isActive(true)
            .build();
        setId(testDeviceToken, 1L);

        testRequest = PushMessageRequest.of(
            "테스트 알림",
            "테스트 메시지 내용",
            Map.of("type", "TEST")
        );
    }

    @Nested
    @DisplayName("단일 사용자 푸시 전송 테스트")
    class SendToUserTest {

        @Test
        @DisplayName("활성 토큰이 있으면 푸시를 전송한다")
        void sendToUser_success() throws FirebaseMessagingException {
            // given
            when(deviceTokenRepository.findByUserIdAndIsActiveTrue(testUserId))
                .thenReturn(List.of(testDeviceToken));
            when(firebaseMessaging.send(any(Message.class)))
                .thenReturn("projects/test/messages/12345");

            // when
            fcmPushService.sendToUser(testUserId, testRequest);

            // then
            verify(firebaseMessaging).send(any(Message.class));
            verify(deviceTokenRepository).incrementBadgeCountByUserId(testUserId);
        }

        @Test
        @DisplayName("활성 토큰이 없으면 푸시를 전송하지 않는다")
        void sendToUser_noActiveTokens() throws FirebaseMessagingException {
            // given
            when(deviceTokenRepository.findByUserIdAndIsActiveTrue(testUserId))
                .thenReturn(List.of());

            // when
            fcmPushService.sendToUser(testUserId, testRequest);

            // then
            verify(firebaseMessaging, never()).send(any());
        }

        @Test
        @DisplayName("여러 토큰이 있으면 모든 토큰에 전송한다")
        void sendToUser_multipleTokens() throws FirebaseMessagingException {
            // given
            DeviceToken secondToken = DeviceToken.builder()
                .userId(testUserId)
                .fcmToken("second-fcm-token")
                .deviceType(DeviceType.IOS)
                .isActive(true)
                .build();
            setId(secondToken, 2L);

            when(deviceTokenRepository.findByUserIdAndIsActiveTrue(testUserId))
                .thenReturn(List.of(testDeviceToken, secondToken));
            when(firebaseMessaging.send(any(Message.class)))
                .thenReturn("projects/test/messages/12345");

            // when
            fcmPushService.sendToUser(testUserId, testRequest);

            // then
            verify(firebaseMessaging, times(2)).send(any(Message.class));
        }
    }

    @Nested
    @DisplayName("다중 사용자 푸시 전송 테스트")
    class SendToUsersTest {

        @Test
        @DisplayName("여러 사용자에게 푸시를 전송한다")
        void sendToUsers_success() throws FirebaseMessagingException {
            // given
            String secondUserId = "second-user-id";
            DeviceToken secondUserToken = DeviceToken.builder()
                .userId(secondUserId)
                .fcmToken("second-user-token")
                .deviceType(DeviceType.ANDROID)
                .isActive(true)
                .build();
            setId(secondUserToken, 2L);

            List<String> userIds = List.of(testUserId, secondUserId);

            // sendToUsers uses findActiveTokensByUserIds and sendEach (batch)
            when(deviceTokenRepository.findActiveTokensByUserIds(userIds))
                .thenReturn(List.of(testDeviceToken, secondUserToken));

            BatchResponse batchResponse = org.mockito.Mockito.mock(BatchResponse.class);
            when(batchResponse.getSuccessCount()).thenReturn(2);
            when(batchResponse.getFailureCount()).thenReturn(0);
            when(batchResponse.getResponses()).thenReturn(List.of());
            when(firebaseMessaging.sendEach(any())).thenReturn(batchResponse);

            // when
            fcmPushService.sendToUsers(userIds, testRequest);

            // then
            verify(firebaseMessaging).sendEach(any());
            verify(deviceTokenRepository).incrementBadgeCountByUserId(testUserId);
            verify(deviceTokenRepository).incrementBadgeCountByUserId(secondUserId);
        }

        @Test
        @DisplayName("활성 토큰이 없으면 푸시를 전송하지 않는다")
        void sendToUsers_noActiveTokens() throws FirebaseMessagingException {
            // given
            List<String> userIds = List.of(testUserId, "other-user");
            when(deviceTokenRepository.findActiveTokensByUserIds(userIds))
                .thenReturn(List.of());

            // when
            fcmPushService.sendToUsers(userIds, testRequest);

            // then
            verify(firebaseMessaging, never()).sendEach(any());
        }
    }

    @Nested
    @DisplayName("토픽 푸시 전송 테스트")
    class SendToTopicTest {

        @Test
        @DisplayName("토픽으로 푸시를 전송한다")
        void sendToTopic_success() throws FirebaseMessagingException {
            // given
            String topic = "guild-123";
            when(firebaseMessaging.send(any(Message.class)))
                .thenReturn("projects/test/messages/12345");

            // when
            fcmPushService.sendToTopic(topic, testRequest);

            // then
            verify(firebaseMessaging).send(any(Message.class));
        }

        @Test
        @DisplayName("푸시 전송 실패시 예외를 로깅하고 계속 진행한다")
        void sendToTopic_failure_logsError() throws FirebaseMessagingException {
            // given
            String topic = "guild-123";
            FirebaseMessagingException mockException = org.mockito.Mockito.mock(FirebaseMessagingException.class);
            when(mockException.getMessage()).thenReturn("FCM 전송 실패");
            when(firebaseMessaging.send(any(Message.class)))
                .thenThrow(mockException);

            // when & then - FirebaseMessagingException is caught internally, not rethrown
            assertThatCode(() -> fcmPushService.sendToTopic(topic, testRequest))
                .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("토픽 구독 테스트")
    class TopicSubscriptionTest {

        @Test
        @DisplayName("토픽을 구독한다")
        void subscribeToTopic_success() throws FirebaseMessagingException {
            // given
            String topic = "guild-123";
            TopicManagementResponse response = org.mockito.Mockito.mock(TopicManagementResponse.class);
            when(response.getSuccessCount()).thenReturn(1);

            when(deviceTokenRepository.findByUserIdAndIsActiveTrue(testUserId))
                .thenReturn(List.of(testDeviceToken));
            when(firebaseMessaging.subscribeToTopic(any(), any()))
                .thenReturn(response);

            // when
            fcmPushService.subscribeToTopic(testUserId, topic);

            // then
            verify(firebaseMessaging).subscribeToTopic(any(), any());
        }

        @Test
        @DisplayName("토큰이 없으면 구독하지 않는다")
        void subscribeToTopic_noTokens() throws FirebaseMessagingException {
            // given
            String topic = "guild-123";
            when(deviceTokenRepository.findByUserIdAndIsActiveTrue(testUserId))
                .thenReturn(List.of());

            // when
            fcmPushService.subscribeToTopic(testUserId, topic);

            // then
            verify(firebaseMessaging, never()).subscribeToTopic(any(), any());
        }

        @Test
        @DisplayName("토픽 구독을 해제한다")
        void unsubscribeFromTopic_success() throws FirebaseMessagingException {
            // given
            String topic = "guild-123";
            TopicManagementResponse response = org.mockito.Mockito.mock(TopicManagementResponse.class);
            when(response.getSuccessCount()).thenReturn(1);

            when(deviceTokenRepository.findByUserIdAndIsActiveTrue(testUserId))
                .thenReturn(List.of(testDeviceToken));
            when(firebaseMessaging.unsubscribeFromTopic(any(), any()))
                .thenReturn(response);

            // when
            fcmPushService.unsubscribeFromTopic(testUserId, topic);

            // then
            verify(firebaseMessaging).unsubscribeFromTopic(any(), any());
        }
    }
}
