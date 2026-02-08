package io.pinkspider.leveluptogethermvp.notificationservice.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.DeviceTokenRequest;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.DeviceTokenResponse;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.entity.DeviceToken;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.entity.DeviceToken.DeviceType;
import io.pinkspider.leveluptogethermvp.notificationservice.infrastructure.DeviceTokenRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceTokenServiceTest {

    @Mock
    private DeviceTokenRepository deviceTokenRepository;

    @Mock
    private FcmPushService fcmPushService;

    @InjectMocks
    private DeviceTokenService deviceTokenService;

    private static final String TEST_USER_ID = "test-user-123";
    private static final String TEST_FCM_TOKEN = "fcm-token-abc123";
    private static final String TEST_DEVICE_ID = "device-id-xyz";

    private DeviceToken createTestDeviceToken(Long id, String userId, String fcmToken, String deviceId) {
        DeviceToken token = DeviceToken.builder()
            .userId(userId)
            .fcmToken(fcmToken)
            .deviceType(DeviceType.IOS)
            .deviceId(deviceId)
            .deviceName("iPhone 15")
            .appVersion("1.0.0")
            .isActive(true)
            .badgeCount(0)
            .build();
        setId(token, id);
        return token;
    }

    @Nested
    @DisplayName("registerToken 테스트")
    class RegisterTokenTest {

        @Test
        @DisplayName("새 토큰을 등록한다")
        void registerToken_newToken_success() {
            // given
            DeviceTokenRequest request = new DeviceTokenRequest(
                TEST_FCM_TOKEN, DeviceType.IOS, TEST_DEVICE_ID, "iPhone 15", "1.0.0"
            );
            DeviceToken savedToken = createTestDeviceToken(1L, TEST_USER_ID, TEST_FCM_TOKEN, TEST_DEVICE_ID);

            when(deviceTokenRepository.findByFcmToken(TEST_FCM_TOKEN)).thenReturn(Optional.empty());
            when(deviceTokenRepository.findByUserIdAndDeviceId(TEST_USER_ID, TEST_DEVICE_ID))
                .thenReturn(Optional.empty());
            when(deviceTokenRepository.save(any(DeviceToken.class))).thenReturn(savedToken);

            // when
            DeviceTokenResponse result = deviceTokenService.registerToken(TEST_USER_ID, request);

            // then
            assertThat(result).isNotNull();
            verify(deviceTokenRepository).save(any(DeviceToken.class));
        }

        @Test
        @DisplayName("동일한 FCM 토큰이 같은 사용자에게 있으면 업데이트한다")
        void registerToken_existingTokenSameUser_updates() {
            // given
            DeviceTokenRequest request = new DeviceTokenRequest(
                TEST_FCM_TOKEN, DeviceType.IOS, TEST_DEVICE_ID, "iPhone 16", "1.1.0"
            );
            DeviceToken existingToken = createTestDeviceToken(1L, TEST_USER_ID, TEST_FCM_TOKEN, TEST_DEVICE_ID);

            when(deviceTokenRepository.findByFcmToken(TEST_FCM_TOKEN)).thenReturn(Optional.of(existingToken));
            when(deviceTokenRepository.save(any(DeviceToken.class))).thenReturn(existingToken);

            // when
            DeviceTokenResponse result = deviceTokenService.registerToken(TEST_USER_ID, request);

            // then
            assertThat(result).isNotNull();
            verify(deviceTokenRepository).save(existingToken);
        }

        @Test
        @DisplayName("동일한 FCM 토큰이 다른 사용자에게 있으면 현재 사용자로 이전한다")
        void registerToken_existingTokenDifferentUser_transfers() {
            // given
            DeviceTokenRequest request = new DeviceTokenRequest(
                TEST_FCM_TOKEN, DeviceType.IOS, TEST_DEVICE_ID, "iPhone 15", "1.0.0"
            );
            DeviceToken existingToken = createTestDeviceToken(1L, "other-user", TEST_FCM_TOKEN, "other-device");

            when(deviceTokenRepository.findByFcmToken(TEST_FCM_TOKEN)).thenReturn(Optional.of(existingToken));
            when(deviceTokenRepository.save(any(DeviceToken.class))).thenReturn(existingToken);

            // when
            DeviceTokenResponse result = deviceTokenService.registerToken(TEST_USER_ID, request);

            // then
            assertThat(result).isNotNull();
            assertThat(existingToken.getUserId()).isEqualTo(TEST_USER_ID);
            assertThat(existingToken.getDeviceId()).isEqualTo(TEST_DEVICE_ID);
            assertThat(existingToken.getIsActive()).isTrue();
            verify(deviceTokenRepository).deactivateAllByUserId(TEST_USER_ID);
            verify(deviceTokenRepository).save(existingToken);
        }

        @Test
        @DisplayName("동일한 디바이스에 기존 토큰이 있으면 토큰을 업데이트한다")
        void registerToken_existingDevice_updatesToken() {
            // given
            DeviceTokenRequest request = new DeviceTokenRequest(
                "new-fcm-token", DeviceType.IOS, TEST_DEVICE_ID, "iPhone 15", "1.0.0"
            );
            DeviceToken existingByDevice = createTestDeviceToken(1L, TEST_USER_ID, TEST_FCM_TOKEN, TEST_DEVICE_ID);

            when(deviceTokenRepository.findByFcmToken("new-fcm-token")).thenReturn(Optional.empty());
            when(deviceTokenRepository.findByUserIdAndDeviceId(TEST_USER_ID, TEST_DEVICE_ID))
                .thenReturn(Optional.of(existingByDevice));
            when(deviceTokenRepository.save(any(DeviceToken.class))).thenReturn(existingByDevice);

            // when
            DeviceTokenResponse result = deviceTokenService.registerToken(TEST_USER_ID, request);

            // then
            assertThat(result).isNotNull();
            verify(deviceTokenRepository).save(existingByDevice);
        }
    }

    @Nested
    @DisplayName("unregisterToken 테스트")
    class UnregisterTokenTest {

        @Test
        @DisplayName("토큰을 해제한다")
        void unregisterToken_success() {
            // given
            DeviceToken existingToken = createTestDeviceToken(1L, TEST_USER_ID, TEST_FCM_TOKEN, TEST_DEVICE_ID);

            when(deviceTokenRepository.findByFcmToken(TEST_FCM_TOKEN)).thenReturn(Optional.of(existingToken));

            // when
            deviceTokenService.unregisterToken(TEST_USER_ID, TEST_FCM_TOKEN);

            // then
            verify(deviceTokenRepository).save(existingToken);
        }

        @Test
        @DisplayName("다른 사용자의 토큰은 해제하지 않는다")
        void unregisterToken_differentUser_doesNotDeactivate() {
            // given
            DeviceToken existingToken = createTestDeviceToken(1L, "other-user", TEST_FCM_TOKEN, TEST_DEVICE_ID);

            when(deviceTokenRepository.findByFcmToken(TEST_FCM_TOKEN)).thenReturn(Optional.of(existingToken));

            // when
            deviceTokenService.unregisterToken(TEST_USER_ID, TEST_FCM_TOKEN);

            // then
            verify(deviceTokenRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("unregisterAllTokens 테스트")
    class UnregisterAllTokensTest {

        @Test
        @DisplayName("사용자의 모든 토큰을 해제한다")
        void unregisterAllTokens_success() {
            // when
            deviceTokenService.unregisterAllTokens(TEST_USER_ID);

            // then
            verify(deviceTokenRepository).deactivateAllByUserId(TEST_USER_ID);
        }
    }

    @Nested
    @DisplayName("getTokensByUserId 테스트")
    class GetTokensByUserIdTest {

        @Test
        @DisplayName("사용자의 활성 토큰 목록을 조회한다")
        void getTokensByUserId_success() {
            // given
            DeviceToken token1 = createTestDeviceToken(1L, TEST_USER_ID, TEST_FCM_TOKEN, TEST_DEVICE_ID);
            DeviceToken token2 = createTestDeviceToken(2L, TEST_USER_ID, "fcm-token-2", "device-2");

            when(deviceTokenRepository.findByUserIdAndIsActiveTrue(TEST_USER_ID))
                .thenReturn(List.of(token1, token2));

            // when
            List<DeviceTokenResponse> result = deviceTokenService.getTokensByUserId(TEST_USER_ID);

            // then
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("활성 토큰이 없으면 빈 목록을 반환한다")
        void getTokensByUserId_empty() {
            // given
            when(deviceTokenRepository.findByUserIdAndIsActiveTrue(TEST_USER_ID))
                .thenReturn(List.of());

            // when
            List<DeviceTokenResponse> result = deviceTokenService.getTokensByUserId(TEST_USER_ID);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("resetBadgeCount 테스트")
    class ResetBadgeCountTest {

        @Test
        @DisplayName("배지 카운트를 초기화한다")
        void resetBadgeCount_success() {
            // when
            deviceTokenService.resetBadgeCount(TEST_USER_ID);

            // then
            verify(deviceTokenRepository).resetBadgeCountByUserId(TEST_USER_ID);
        }
    }

    @Nested
    @DisplayName("subscribeToGuildTopic 테스트")
    class SubscribeToGuildTopicTest {

        @Test
        @DisplayName("길드 토픽에 구독한다")
        void subscribeToGuildTopic_success() {
            // given
            Long guildId = 100L;

            // when
            deviceTokenService.subscribeToGuildTopic(TEST_USER_ID, guildId);

            // then
            verify(fcmPushService).subscribeToTopic(TEST_USER_ID, "guild-100");
        }
    }

    @Nested
    @DisplayName("unsubscribeFromGuildTopic 테스트")
    class UnsubscribeFromGuildTopicTest {

        @Test
        @DisplayName("길드 토픽에서 구독 해제한다")
        void unsubscribeFromGuildTopic_success() {
            // given
            Long guildId = 100L;

            // when
            deviceTokenService.unsubscribeFromGuildTopic(TEST_USER_ID, guildId);

            // then
            verify(fcmPushService).unsubscribeFromTopic(TEST_USER_ID, "guild-100");
        }
    }
}
