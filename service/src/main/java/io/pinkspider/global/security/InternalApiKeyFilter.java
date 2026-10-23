package io.pinkspider.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/**
 * 공유 시크릿 키 인증 필터 — 내부 API(/api/internal/**, LUT-244) + 액추에이터(LUT-605).
 *
 * <p>내부 API는 기존에 SecurityConfig 의 permitAll + VPC 네트워크 격리에만 의존했다. prod 는 VPC 로 보호되지만 dev 는 공인망에 노출돼
 * 무인증 접근이 가능했다. 네트워크와 무관하게 방어하도록 Admin Backend 가 주입하는 {@code X-Internal-Api-Key} 헤더를 검증한다.
 *
 * <p>LUT-605: 액추에이터({@code management.endpoints.web.base-path}, 운영은 {@code /showmethemoney}) 도 같은
 * 키로 보호한다. SecurityConfig 가 base-path 전체를 permitAll 하고 exposure 가 {@code *} 라서 prod 의 {@code
 * /env}·{@code /beans}· {@code /prometheus}·{@code /heapdump} 가 인증 없이 열려 있었다. ALB·배포 스크립트가 호출하는
 * {@code /health} 만 공개로 남긴다. Prometheus/Grafana Alloy 스크레이퍼는 커스텀 헤더를 못 붙이는 경우가 있어 <b>HTTP Basic 의
 * 비밀번호</b>(사용자명 무관)로도 키를 받는다. 플랫폼 {@code JwtAuthenticationFilter} 는 {@code Bearer} 만 해석하므로 Basic
 * 헤더와 충돌하지 않는다.
 *
 * <p>안전 롤아웃: 키가 미설정(공백)이면 검증을 건너뛴다(fail-open). config 선배포 없이도 무중단 배포가 가능하고, 키가 설정된 환경부터 방어가 활성화된다.
 */
@Slf4j
@Component
@Profile("!test")
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private static final String INTERNAL_PATH_PREFIX = "/api/internal/";
    private static final String HEADER_NAME = "X-Internal-Api-Key";
    private static final String BASIC_PREFIX = "Basic ";
    static final String DEFAULT_ACTUATOR_BASE_PATH = "/actuator";

    private final String configuredKey;
    private final byte[] configuredKeyBytes;
    private final String actuatorBasePath;
    private final String actuatorHealthPath;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 액추에이터 base-path 기본값({@code /actuator}) — 테스트·호환용. */
    public InternalApiKeyFilter(String configuredKey) {
        this(configuredKey, DEFAULT_ACTUATOR_BASE_PATH);
    }

    @Autowired
    public InternalApiKeyFilter(
            @Value("${app.security.internal-api.key:}") String configuredKey,
            @Value("${management.endpoints.web.base-path:/actuator}") String actuatorBasePath) {
        this.configuredKey = configuredKey == null ? "" : configuredKey;
        this.configuredKeyBytes = this.configuredKey.getBytes(StandardCharsets.UTF_8);
        this.actuatorBasePath = normalizeBasePath(actuatorBasePath);
        this.actuatorHealthPath = this.actuatorBasePath + "/health";
    }

    private static String normalizeBasePath(String basePath) {
        String base =
                basePath == null || basePath.isBlank()
                        ? DEFAULT_ACTUATOR_BASE_PATH
                        : basePath.trim();
        if (!base.startsWith("/")) {
            base = "/" + base;
        }
        while (base.length() > 1 && base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String rawUri = request.getRequestURI();
        String path = normalizedPath(request);
        if ((requiresKey(path) || looksSuspicious(rawUri)) && !configuredKey.isBlank()) {
            boolean authorized =
                    matchesConfiguredKey(request.getHeader(HEADER_NAME))
                            || matchesBasicPassword(request.getHeader("Authorization"));
            if (!authorized) {
                log.warn("내부 API 인증 실패: path={}, remoteAddr={}", rawUri, request.getRemoteAddr());
                writeUnauthorized(request, response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 디스패처가 매핑에 쓰는 것과 같은 경로 — 컨텍스트 경로 제거, 퍼센트 디코딩, {@code ;param} 제거, 중복 슬래시 축약. 원시 {@code
     * requestURI} 로 접두사만 비교하면 {@code /showmethemoney/%65nv} 같은 표기 차이로 판정이 어긋날 수 있다(보안 리뷰 지적).
     */
    static String normalizedPath(HttpServletRequest request) {
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        return path == null ? "" : path.replaceAll("/{2,}", "/");
    }

    /**
     * 퍼센트 인코딩({@code %})·세그먼트 파라미터({@code ;})·상위 이동({@code ..})·역슬래시가 섞인 보호 접두사 요청은 어떻게 해석되든 키를
     * 요구한다 (fail-closed). Spring Security {@code StrictHttpFirewall} 이 대부분 400 으로 먼저 막지만, 설정이 바뀌어도
     * 열리지 않게 한 겹 더 둔다.
     */
    boolean looksSuspicious(String rawUri) {
        if (rawUri == null) {
            return false;
        }
        boolean odd =
                rawUri.contains("%")
                        || rawUri.contains(";")
                        || rawUri.contains("..")
                        || rawUri.contains("\\");
        if (!odd) {
            return false;
        }
        String lower = rawUri.toLowerCase(Locale.ROOT);
        return lower.startsWith(actuatorBasePath.toLowerCase(Locale.ROOT))
                || lower.startsWith(INTERNAL_PATH_PREFIX);
    }

    /** 키가 필요한 경로인가 — 내부 API 전부 + 액추에이터(health 제외). 인자는 {@link #normalizedPath} 결과. */
    boolean requiresKey(String path) {
        if (path == null) {
            return false;
        }
        return path.startsWith(INTERNAL_PATH_PREFIX) || isProtectedActuatorPath(path);
    }

    /**
     * 액추에이터 중 공개로 남기는 것은 {@code {base}/health}(및 그 하위 liveness/readiness) 뿐이다. base-path 자체(엔드포인트
     * 목록) 와 나머지 엔드포인트(env·beans·metrics·prometheus·heapdump·threaddump…) 는 전부 키 필수.
     */
    boolean isProtectedActuatorPath(String path) {
        if (path.equals(actuatorBasePath) || path.equals(actuatorBasePath + "/")) {
            return true;
        }
        if (!path.startsWith(actuatorBasePath + "/")) {
            return false; // "/showmethemoneyX" 처럼 접두사만 같은 다른 경로
        }
        return !(path.equals(actuatorHealthPath) || path.startsWith(actuatorHealthPath + "/"));
    }

    /** 타이밍 공격 방지를 위한 상수 시간 비교. */
    private boolean matchesConfiguredKey(String provided) {
        if (provided == null) {
            return false;
        }
        byte[] providedBytes = provided.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(configuredKeyBytes, providedBytes);
    }

    /** {@code Authorization: Basic base64(user:password)} 의 password 가 키와 같으면 통과 (사용자명은 보지 않는다). */
    private boolean matchesBasicPassword(String authorization) {
        if (authorization == null || !authorization.startsWith(BASIC_PREFIX)) {
            return false;
        }
        try {
            byte[] decoded =
                    Base64.getDecoder()
                            .decode(authorization.substring(BASIC_PREFIX.length()).trim());
            String credentials = new String(decoded, StandardCharsets.UTF_8);
            int separator = credentials.indexOf(':');
            if (separator < 0) {
                return false;
            }
            return matchesConfiguredKey(credentials.substring(separator + 1));
        } catch (IllegalArgumentException e) {
            return false; // base64 가 아님
        }
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
