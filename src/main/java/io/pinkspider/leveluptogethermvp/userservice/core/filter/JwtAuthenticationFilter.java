package io.pinkspider.leveluptogethermvp.userservice.core.filter;

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

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        try {
            String token = getTokenFromRequest(request);

            if (token != null && jwtUtil.validateToken(token) && !tokenService.isTokenBlacklisted(token)) {
                String userId = jwtUtil.getUserIdFromToken(token);
                String email = jwtUtil.getEmailFromToken(token);
                String deviceId = jwtUtil.getDeviceIdFromToken(token);

                // SecurityContext에 인증 정보 설정
                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                    );

                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);

                // 요청 헤더에 사용자 정보 추가 (다운스트림 서비스용)
                request.setAttribute("X-User-Id", userId);
                request.setAttribute("X-User-Email", email);
                request.setAttribute("X-Device-Id", deviceId);

                log.debug("JWT 인증 성공: userId={}, deviceId={}", userId, deviceId);
            }
        } catch (Exception e) {
            log.warn("JWT 인증 처리 중 오류 발생: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
