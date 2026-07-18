package io.pinkspider.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 내부 API(/api/internal/**) 공유 시크릿 헤더 인증 필터 (LUT-244).
 *
 * <p>내부 API는 기존에 SecurityConfig 의 permitAll + VPC 네트워크 격리에만 의존했다. prod 는 VPC 로 보호되지만 dev 는 공인망에 노출돼
 * 무인증 접근이 가능했다. 네트워크와 무관하게 방어하도록 Admin Backend 가 주입하는 {@code X-Internal-Api-Key} 헤더를 검증한다.
 *
 * <p>안전 롤아웃: 키가 미설정(공백)이면 검증을 건너뛴다(fail-open). config 선배포 없이도 무중단 배포가 가능하고, 키가 설정된 환경부터 방어가 활성화된다.
 */
@Slf4j
@Component
@Profile("!test")
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private static final String INTERNAL_PATH_PREFIX = "/api/internal/";
    private static final String HEADER_NAME = "X-Internal-Api-Key";

    private final String configuredKey;
    private final byte[] configuredKeyBytes;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public InternalApiKeyFilter(@Value("${app.security.internal-api.key:}") String configuredKey) {
        this.configuredKey = configuredKey == null ? "" : configuredKey;
        this.configuredKeyBytes = this.configuredKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        if (path != null && path.startsWith(INTERNAL_PATH_PREFIX) && !configuredKey.isBlank()) {
            String provided = request.getHeader(HEADER_NAME);
            if (!matchesConfiguredKey(provided)) {
                log.warn("내부 API 인증 실패: path={}, remoteAddr={}", path, request.getRemoteAddr());
                writeUnauthorized(request, response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    /** 타이밍 공격 방지를 위한 상수 시간 비교. */
    private boolean matchesConfiguredKey(String provided) {
        if (provided == null) {
            return false;
        }
        byte[] providedBytes = provided.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(configuredKeyBytes, providedBytes);
    }

    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Map<String, Object> body =
                Map.of(
                        "code",
                        "000401",
                        "message",
                        "Unauthorized internal API access",
                        "value",
                        request.getRequestURI());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
