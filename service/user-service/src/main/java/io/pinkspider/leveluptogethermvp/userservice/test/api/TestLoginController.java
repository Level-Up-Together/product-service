package io.pinkspider.leveluptogethermvp.userservice.test.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.jwt.CreateJwtResponseDto;
import io.pinkspider.leveluptogethermvp.userservice.test.application.TestLoginService;
import io.pinkspider.leveluptogethermvp.userservice.test.domain.dto.TestLoginRequestDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 테스트 전용 로그인 API
 *
 * E2E 테스트 및 개발 환경에서 소셜 로그인 없이 JWT 토큰을 발급받을 수 있습니다.
 * 이 컨트롤러는 dev, test, local 프로파일에서만 활성화됩니다.
 *
 * 사용 예시:
 * POST /api/test/login
 * {
 *   "test_user_id": "test-user-001",
 *   "email": "test@example.com",
 *   "nickname": "테스터",
 *   "device_type": "web",
 *   "device_id": "playwright-test-device"
 * }
 */
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
@Slf4j
@Profile({"dev", "test", "local", "local-dev"})
public class TestLoginController {

    private final TestLoginService testLoginService;

    /**
     * 테스트용 로그인 - 소셜 로그인 없이 JWT 발급
     *
     * 지정된 테스트 사용자가 없으면 자동 생성됩니다.
     * 이미 존재하는 사용자면 해당 사용자로 로그인합니다.
     */
    @PostMapping("/login")
    public ApiResult<CreateJwtResponseDto> testLogin(
        @Valid @RequestBody TestLoginRequestDto request,
        HttpServletRequest httpRequest
    ) {
        log.info("Test login request - testUserId: {}, email: {}",
            request.getTestUserId(), request.getEmail());

        CreateJwtResponseDto response = testLoginService.loginAsTestUser(
            httpRequest,
            request.getTestUserId(),
            request.getEmail(),
            request.getNickname(),
            request.getDeviceType(),
            request.getDeviceId()
        );

        return ApiResult.<CreateJwtResponseDto>builder()
            .value(response)
            .build();
    }
}
