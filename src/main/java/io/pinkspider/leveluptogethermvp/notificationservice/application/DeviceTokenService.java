package io.pinkspider.leveluptogethermvp.notificationservice.application;

import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.DeviceTokenRequest;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.DeviceTokenResponse;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.entity.DeviceToken;
import io.pinkspider.leveluptogethermvp.notificationservice.infrastructure.DeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 디바이스 토큰 관리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final FcmPushService fcmPushService;

    /**
     * 디바이스 토큰 등록/업데이트
     */
    @Transactional(transactionManager = "notificationTransactionManager")
    public DeviceTokenResponse registerToken(String userId, DeviceTokenRequest request) {
        log.info("Registering device token for user: {}, deviceType: {}", userId, request.deviceType());

        // 기존 토큰 확인 (동일 FCM 토큰이 있는 경우)
        Optional<DeviceToken> existingByToken = deviceTokenRepository.findByFcmToken(request.fcmToken());

        if (existingByToken.isPresent()) {
            DeviceToken existing = existingByToken.get();

            // 다른 사용자의 토큰이면 기존 것을 비활성화
            if (!existing.getUserId().equals(userId)) {
                existing.deactivate();
                deviceTokenRepository.save(existing);
            } else {
                // 같은 사용자의 토큰이면 활성화 및 정보 업데이트
                existing.activate();
                existing.setDeviceId(request.deviceId());
                existing.setDeviceName(request.deviceName());
                existing.setAppVersion(request.appVersion());
                DeviceToken saved = deviceTokenRepository.save(existing);
                return DeviceTokenResponse.from(saved);
            }
        }

        // 동일 디바이스의 기존 토큰이 있으면 업데이트
        if (request.deviceId() != null && !request.deviceId().isEmpty()) {
            Optional<DeviceToken> existingByDevice =
                    deviceTokenRepository.findByUserIdAndDeviceId(userId, request.deviceId());

            if (existingByDevice.isPresent()) {
                DeviceToken existing = existingByDevice.get();
                existing.updateToken(request.fcmToken());
                existing.setDeviceName(request.deviceName());
                existing.setAppVersion(request.appVersion());
                DeviceToken saved = deviceTokenRepository.save(existing);
                return DeviceTokenResponse.from(saved);
            }
        }

        // 새 토큰 생성
        DeviceToken newToken = DeviceToken.builder()
                .userId(userId)
                .fcmToken(request.fcmToken())
                .deviceType(request.deviceType())
                .deviceId(request.deviceId())
                .deviceName(request.deviceName())
                .appVersion(request.appVersion())
                .isActive(true)
                .badgeCount(0)
                .build();

        DeviceToken saved = deviceTokenRepository.save(newToken);
        log.info("Device token registered successfully: {}", saved.getId());

        return DeviceTokenResponse.from(saved);
    }

    /**
     * 디바이스 토큰 해제
     */
    @Transactional(transactionManager = "notificationTransactionManager")
    public void unregisterToken(String userId, String fcmToken) {
        log.info("Unregistering device token for user: {}", userId);

        deviceTokenRepository.findByFcmToken(fcmToken)
                .ifPresent(token -> {
                    if (token.getUserId().equals(userId)) {
                        token.deactivate();
                        deviceTokenRepository.save(token);
                    }
                });
    }

    /**
     * 사용자의 모든 토큰 해제 (로그아웃 시)
     */
    @Transactional(transactionManager = "notificationTransactionManager")
    public void unregisterAllTokens(String userId) {
        log.info("Unregistering all device tokens for user: {}", userId);
        deviceTokenRepository.deactivateAllByUserId(userId);
    }

    /**
     * 사용자의 디바이스 토큰 목록 조회
     */
    @Transactional(readOnly = true, transactionManager = "notificationTransactionManager")
    public List<DeviceTokenResponse> getTokensByUserId(String userId) {
        return deviceTokenRepository.findByUserIdAndIsActiveTrue(userId)
                .stream()
                .map(DeviceTokenResponse::from)
                .toList();
    }

    /**
     * 배지 카운트 초기화 (앱 접속 시)
     */
    @Transactional(transactionManager = "notificationTransactionManager")
    public void resetBadgeCount(String userId) {
        log.debug("Resetting badge count for user: {}", userId);
        deviceTokenRepository.resetBadgeCountByUserId(userId);
    }

    /**
     * 사용자를 길드 토픽에 구독
     */
    public void subscribeToGuildTopic(String userId, Long guildId) {
        String topic = "guild-" + guildId;
        fcmPushService.subscribeToTopic(userId, topic);
    }

    /**
     * 사용자를 길드 토픽에서 구독 해제
     */
    public void unsubscribeFromGuildTopic(String userId, Long guildId) {
        String topic = "guild-" + guildId;
        fcmPushService.unsubscribeFromTopic(userId, topic);
    }
}
