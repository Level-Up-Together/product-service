package io.pinkspider.leveluptogethermvp.userservice.core.filter;

import io.pinkspider.leveluptogethermvp.userservice.core.application.UserExistsCacheService;
import io.pinkspider.leveluptogethermvp.userservice.core.util.JwtUtil;
import io.pinkspider.leveluptogethermvp.userservice.oauth.application.MultiDeviceTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!test")
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final MultiDeviceTokenService tokenService;
    private final UserExistsCacheService userExistsCacheService;

    private static final String REISSUE_PATH = "/jwt/reissue";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        try {
            String token = getTokenFromRequest(request);
            String requestPath = request.getRequestURI();

            if (token != null) {
                // /jwt/reissue 요청인 경우: 만료된 토큰도 서명만 유효하면 허용
                if (REISSUE_PATH.equals(requestPath)) {
                    handleReissueRequest(request, token);
                }
                // 일반 요청: 유효한 토큰만 허용
                else if (jwtUtil.validateToken(token) && !tokenService.isTokenBlacklisted(token)) {
                    handleValidToken(request, token);
                }
            }
        } catch (Exception e) {
            log.warn("JWT 인증 처리 중 오류 발생: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 토큰 재발급 요청 처리: 만료된 토큰도 서명이 유효하면 인증 컨텍스트 설정
     */
    private void handleReissueRequest(HttpServletRequest request, String token) {
        if (jwtUtil.validateTokenSignature(token) && !tokenService.isTokenBlacklisted(token)) {
            String userId = jwtUtil.getUserIdFromExpiredToken(token);
            String email = jwtUtil.getEmailFromExpiredToken(token);
            String deviceId = jwtUtil.getDeviceIdFromExpiredToken(token);

            if (userId != null) {
                // DB에 사용자가 존재하는지 확인 (Redis 캐싱)
                if (!userExistsCacheService.existsById(userId)) {
                    log.warn("JWT 재발급 요청 - DB에 사용자가 존재하지 않음: userId={}", userId);
                    return; // SecurityContext 설정 안함 → 401 반환
                }

                setAuthenticationContext(request, userId, email, deviceId);
                request.setAttribute("X-Token-Expired", true);  // 토큰 만료 플래그
                log.debug("JWT 재발급 요청 인증 성공 (만료된 토큰): userId={}, deviceId={}", userId, deviceId);
            }
        }
    }

    /**
     * 유효한 토큰 처리
     */
    private void handleValidToken(HttpServletRequest request, String token) {
        String userId = jwtUtil.getUserIdFromToken(token);
        String email = jwtUtil.getEmailFromToken(token);
        String deviceId = jwtUtil.getDeviceIdFromToken(token);

        // DB에 사용자가 존재하는지 확인 (Redis 캐싱)
        if (!userExistsCacheService.existsById(userId)) {
            log.warn("JWT는 유효하나 DB에 사용자가 존재하지 않음: userId={}", userId);
            return; // SecurityContext 설정 안함 → 401 반환
        }

        setAuthenticationContext(request, userId, email, deviceId);
        log.debug("JWT 인증 성공: userId={}, deviceId={}", userId, deviceId);
    }

    /**
     * SecurityContext에 인증 정보 설정
     */
    private void setAuthenticationContext(HttpServletRequest request, String userId, String email, String deviceId) {
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(
                userId,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
            );

        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 요청 속성에 사용자 정보 추가 (다운스트림 서비스용)
        request.setAttribute("X-User-Id", userId);
        request.setAttribute("X-User-Email", email);
        request.setAttribute("X-Device-Id", deviceId);
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
