package io.pinkspider.leveluptogethermvp.userservice.test.application;

import io.pinkspider.global.util.CryptoUtils;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.TitleService;
import io.pinkspider.leveluptogethermvp.userservice.core.util.JwtUtil;
import io.pinkspider.leveluptogethermvp.userservice.oauth.application.MultiDeviceTokenService;
import io.pinkspider.leveluptogethermvp.userservice.oauth.components.DeviceIdentifier;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.jwt.CreateJwtResponseDto;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 테스트 전용 로그인 서비스
 *
 * E2E 테스트 및 개발 환경에서 소셜 로그인 없이 JWT 토큰을 발급합니다.
 * 이 서비스는 dev, test, local 프로파일에서만 활성화됩니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Profile({"dev", "test", "local", "local-dev"})
public class TestLoginService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final MultiDeviceTokenService tokenService;
    private final DeviceIdentifier deviceIdentifier;
    private final TitleService titleService;

    private static final String TEST_PROVIDER = "test";

    /**
     * 테스트 사용자로 로그인하고 JWT 토큰을 발급합니다.
     *
     * @param httpRequest HTTP 요청
     * @param testUserId 테스트 사용자 ID (null이면 자동 생성)
     * @param email 이메일
     * @param nickname 닉네임 (null이면 이메일에서 추출)
     * @param deviceType 디바이스 타입 (null이면 "web")
     * @param deviceId 디바이스 ID (null이면 자동 생성)
     * @return JWT 토큰 응답
     */
    @Transactional(transactionManager = "userTransactionManager")
    public CreateJwtResponseDto loginAsTestUser(
        HttpServletRequest httpRequest,
        String testUserId,
        String email,
        String nickname,
        String deviceType,
        String deviceId
    ) {
        // 테스트 사용자 조회 또는 생성
        Users user = findOrCreateTestUser(testUserId, email, nickname);

        // 디바이스 정보 설정
        deviceType = deviceType != null ? deviceType : "web";
        if (deviceId == null || deviceId.trim().isEmpty()) {
            deviceId = deviceIdentifier.generateDeviceId(httpRequest, deviceType);
        }

        // JWT 토큰 생성
        String userId = user.getId();
        String userEmail = user.getEmail();

        String accessToken = jwtUtil.generateAccessToken(userId, userEmail, deviceId);
        String refreshToken = jwtUtil.generateRefreshToken(userId, userEmail, deviceId);

        log.info("Test login successful - userId: {}, email: {}, deviceId: {}",
            userId, userEmail, deviceId);

        // Redis에 토큰 저장
        tokenService.saveTokensToRedis(
            userId,
            deviceType,
            deviceId,
            accessToken,
            refreshToken
        );

        return CreateJwtResponseDto.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .tokenType("Bearer")
            .expiresIn(900) // 15분
            .userId(userId)
            .deviceId(deviceId)
            .build();
    }

    /**
     * 테스트 사용자를 조회하거나 새로 생성합니다.
     */
    private Users findOrCreateTestUser(String testUserId, String email, String nickname) {
        // 먼저 이메일로 기존 사용자 조회
        String encryptedEmail = CryptoUtils.encryptAes(email);
        Optional<Users> existingUser = userRepository.findByEncryptedEmailAndProvider(
            encryptedEmail,
            TEST_PROVIDER
        );

        if (existingUser.isPresent()) {
            log.info("기존 테스트 사용자 로그인: userId={}", existingUser.get().getId());
            return existingUser.get();
        }

        // testUserId가 지정되었으면 해당 ID로 조회
        if (testUserId != null && !testUserId.trim().isEmpty()) {
            Optional<Users> userById = userRepository.findById(testUserId);
            if (userById.isPresent()) {
                log.info("기존 테스트 사용자 로그인 (ID로 조회): userId={}", userById.get().getId());
                return userById.get();
            }
        }

        // 신규 테스트 사용자 생성
        return createTestUser(testUserId, email, nickname);
    }

    /**
     * 새 테스트 사용자를 생성합니다.
     */
    private Users createTestUser(String testUserId, String email, String nickname) {
        // 닉네임이 없으면 이메일에서 추출
        if (nickname == null || nickname.trim().isEmpty()) {
            nickname = email.split("@")[0];
            if (nickname.length() > 10) {
                nickname = nickname.substring(0, 10);
            }
        }

        // 닉네임 중복 확인
        if (userRepository.existsByNickname(nickname)) {
            nickname = generateUniqueNickname(nickname);
        }

        // 새 사용자 생성
        Users.UsersBuilder builder = Users.builder()
            .email(email)
            .nickname(nickname)
            .provider(TEST_PROVIDER)
            .nicknameSet(true); // 테스트 사용자는 닉네임 설정 완료로 처리

        // testUserId가 지정되었으면 해당 ID 사용
        if (testUserId != null && !testUserId.trim().isEmpty()) {
            builder.id(testUserId);
        }

        Users newUser = builder.build();
        Users savedUser = userRepository.save(newUser);

        log.info("신규 테스트 사용자 생성: userId={}, email={}, nickname={}",
            savedUser.getId(), email, nickname);

        // 기본 칭호 부여
        try {
            titleService.grantAndEquipDefaultTitles(savedUser.getId());
        } catch (Exception e) {
            log.warn("테스트 사용자 기본 칭호 부여 실패: {}", e.getMessage());
        }

        return savedUser;
    }

    /**
     * 중복되지 않는 유니크한 닉네임 생성
     */
    private String generateUniqueNickname(String baseNickname) {
        String prefix = baseNickname.length() > 6 ? baseNickname.substring(0, 6) : baseNickname;
        String uniqueNickname;
        int maxAttempts = 100;
        int attempts = 0;

        do {
            int randomNum = (int) (Math.random() * 10000);
            uniqueNickname = prefix + String.format("%04d", randomNum);
            attempts++;
        } while (userRepository.existsByNickname(uniqueNickname) && attempts < maxAttempts);

        if (attempts >= maxAttempts) {
            uniqueNickname = prefix + UUID.randomUUID().toString().substring(0, 4);
        }

        return uniqueNickname;
    }
}
