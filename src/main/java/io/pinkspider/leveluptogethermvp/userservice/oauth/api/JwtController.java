package io.pinkspider.leveluptogethermvp.userservice.oauth.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.userservice.oauth.application.JwtService;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.jwt.ReissueJwtResponseDto;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.request.RefreshTokenRequestDto;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.response.SessionsResponseDto;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.response.TokenStatusResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/jwt")
@CrossOrigin(origins = "*", maxAge = 3600)
@RequiredArgsConstructor
@Validated
public class JwtController {

    private final JwtService jwtService;

    @PostMapping("/reissue")
    public ApiResult<ReissueJwtResponseDto> reissue(@Valid @RequestBody RefreshTokenRequestDto requestDto) {

//        String accessToken = headers.getFirst(HttpHeaders.AUTHORIZATION);
//        String refreshToken = requestDto.getRefreshToken();
//
//        assert accessToken != null;
//        JwtTokenVo jwtTokenVo = jwtService.reissueJwt(accessToken, refreshToken);
//
//        return ApiResult.<ReissueJwtResponseDto>builder()
//            .value(new ReissueJwtResponseDto(jwtTokenVo))
//            .build();
        ReissueJwtResponseDto tokenResponseDto = jwtService.reissue(requestDto);

        return ApiResult.<ReissueJwtResponseDto>builder()
            .value(tokenResponseDto)
            .build();
    }

    @PostMapping("/logout")
    public ApiResult<?> logout(HttpServletRequest request) {
        jwtService.logout(request);
        return ApiResult.getBase();
    }

    @PostMapping("/logout-all")
    public ApiResult<?> logoutAll(HttpServletRequest request) {
        jwtService.logoutAll(request);
        return ApiResult.getBase();
    }

    @GetMapping("/sessions")
    public ApiResult<SessionsResponseDto> getActiveSessions(HttpServletRequest request) {
        SessionsResponseDto sessions = jwtService.getActiveSessions(request);

        return ApiResult.<SessionsResponseDto>builder()
            .value(sessions)
            .build();
    }

    @GetMapping("/token-status")
    public ApiResult<TokenStatusResponseDto> getTokenStatus(HttpServletRequest request) {
        TokenStatusResponseDto status = jwtService.getTokenStatus(request);

        return ApiResult.<TokenStatusResponseDto>builder()
            .value(status)
            .build();
    }
}
