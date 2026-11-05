package io.pinkspider.global.config;

import io.pinkspider.global.security.AuthEntryPointJwt;
import io.pinkspider.global.security.InternalApiKeyFilter;
import io.pinkspider.global.security.JwtAuthenticationFilter;
import io.pinkspider.global.security.OAuth2Properties;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
@Profile("!test")
public class SecurityConfig {

    @Value("${management.endpoints.web.base-path}")
    private String ACTUATOR_PATH;

    private final AuthEntryPointJwt unauthorizedHandler;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final InternalApiKeyFilter internalApiKeyFilter;
    private final OAuth2Properties oAuth2Properties;

    /**
     * LUT-731: {@code /uploads/**}(비로그인 공개 정적 이미지)를 읽을 수 있는 출처 패턴. 어드민이 바닥 마스크 픽셀을 읽으려고 {@code
     * crossOrigin="anonymous"} 로 받으면 Origin 이 붙는데, 웹 출처만 허용하던 전역 설정이 403 을 냈다. 우리 도메인(웹·어드민, 하위
     * 도메인·포트 포함)과 로컬만 — 웹 출처 목록({@code oauth2.allowed-origins})은 자동으로 더해진다.
     */
    @Value(
            "${app.cors.uploads.allowed-origin-patterns:https://level-up-together.com:[*],"
                + "https://*.level-up-together.com:[*],http://localhost:[*],https://localhost:[*]}")
    private String[] uploadsAllowedOriginPatterns = new String[0];

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig)
            throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // OAuth2Properties의 allowedOrigins 사용 (credentials: include 지원)
        List<String> allowedOrigins = oAuth2Properties.getAllowedOrigins();
        if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
            configuration.setAllowedOrigins(allowedOrigins);
            configuration.setAllowCredentials(true);
        } else {
            configuration.setAllowedOrigins(List.of("*"));
            configuration.setAllowCredentials(false);
        }
        configuration.setAllowedMethods(
                Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // 명시적 허용목록이다 — 여기 없는 커스텀 헤더를 클라이언트가 붙이면 preflight(OPTIONS)가 그 헤더를 돌려주지 않아
        // 브라우저가 본 요청을 아예 보내지 않고 "CORS error" 로 끝난다. 증상은 "그 API 만 실패"로 보인다 (LUT-587).
        // **프론트/RN 에 커스텀 헤더를 추가할 때는 이 목록에도 함께 추가할 것** — SecurityConfigCorsTest 가 회귀를 막는다.
        configuration.setAllowedHeaders(
                Arrays.asList(
                        "X-Requested-With",
                        "Content-Type",
                        "Authorization",
                        "X-XSRF-token",
                        "Origin",
                        "Accept",
                        // 커스텀 헤더 (브라우저 직접 호출 + admin-service Feign 통합)
                        "X-Signup-Token", // OAuth 신규 가입 세션 토큰
                        "X-Timezone", // 홈/오늘의 미션 등 사용자 타임존
                        "X-Device-Type", // 세션 기기 구분 (LUT-336) — 웹 로그아웃이 직접 호출 (LUT-587)
                        "Idempotency-Key", // 미션 생성 멱등 키 (LUT-577/578)
                        "X-Admin-Id", // 어드민 인증 컨텍스트
                        "X-User-Id", // 어드민 → 사용자 위임 호출
                        "X-User-Email",
                        "X-User-Nickname"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 먼저 등록한 패턴이 먼저 맞는다 — /uploads/** 를 전역보다 앞에
        source.registerCorsConfiguration("/uploads/**", uploadsCorsConfiguration(allowedOrigins));
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * LUT-731: 공개 정적 이미지 — 읽기(GET/HEAD)만, 쿠키 없음. 응답 Access-Control-Allow-Origin 은 요청 출처를 그대로 돌려주고
     * {@code Vary: Origin} 이 붙는다.
     */
    private CorsConfiguration uploadsCorsConfiguration(List<String> webOrigins) {
        List<String> patterns = new ArrayList<>(Arrays.asList(uploadsAllowedOriginPatterns));
        if (webOrigins != null) {
            patterns.addAll(webOrigins);
        }
        CorsConfiguration uploads = new CorsConfiguration();
        uploads.setAllowedOriginPatterns(patterns);
        uploads.setAllowedMethods(Arrays.asList("GET", "HEAD", "OPTIONS"));
        uploads.setAllowCredentials(false);
        uploads.setMaxAge(3600L);
        return uploads;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AuthEntryPointJwt entryPoint)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(
                        exception -> exception.authenticationEntryPoint(unauthorizedHandler))
                .authorizeHttpRequests(
                        authorizeHttpRequests ->
                                authorizeHttpRequests
                                        // 공개 엔드포인트
                                        .requestMatchers(
                                                new AntPathRequestMatcher(ACTUATOR_PATH + "/**"))
                                        .permitAll()
                                        .requestMatchers("/favicon.ico", "/error")
                                        .permitAll()
                                        // 정적 리소스 (이미지 업로드)
                                        .requestMatchers("/uploads/**")
                                        .permitAll()
                                        // WebSocket 엔드포인트
                                        .requestMatchers("/ws/**")
                                        .permitAll()

                                        // 인증 관련 API (로그인, 회원가입, OAuth)
                                        .requestMatchers("/api/v1/auth/**")
                                        .permitAll()
                                        .requestMatchers("/api/v1/oauth/**")
                                        .permitAll()
                                        .requestMatchers("/oauth/**")
                                        .permitAll() // OAuth 컨트롤러 (/oauth/uri/*, /oauth/callback/*)
                                        .requestMatchers("/oauth2/**")
                                        .permitAll()
                                        .requestMatchers("/login/oauth2/**")
                                        .permitAll()
                                        // JWT 토큰 재발급 - refresh_token 검증으로 보안 확보
                                        .requestMatchers("/jwt/reissue")
                                        .permitAll()

                                        // 테스트 로그인 API (dev/test/local 환경에서만 컨트롤러가 활성화됨)
                                        .requestMatchers("/api/test/**")
                                        .permitAll()

                                        // 약관 목록 API (공개)
                                        .requestMatchers("/terms/list")
                                        .permitAll()

                                        // 내부 서비스 간 API (Admin Backend → MVP)
                                        .requestMatchers("/api/internal/**")
                                        .permitAll()

                                        // LUT-452: 스토어 구독 웹훅 (스토어 서버가 호출 — JWT 없음.
                                        // Apple 은 JWS 서명 검증, Google 은 Play API 재조회가 인증을 대신한다)
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/v1/webhooks/subscriptions/apple",
                                                "/api/v1/webhooks/subscriptions/google")
                                        .permitAll()

                                        // LUT-476: 소셜 연결 해제 웹훅 (소셜 서버가 호출 — JWT 없음.
                                        // kakao 는 어드민 키/SET 서명, apple 은 JWS 서명 검증이 인증을 대신한다.
                                        // kakao unlink 웹훅은 GET/POST 모두 올 수 있어 메서드 무제한)
                                        .requestMatchers("/api/v1/oauth/kakao/webhook/**")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.POST, "/api/v1/oauth/apple/webhook")
                                        .permitAll()

                                        // Browse-first: 비인증 열람 허용 API (GET만)
                                        .requestMatchers(HttpMethod.GET, "/api/v1/bff/home")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/api/v1/bff/home/mvp")
                                        .permitAll() // LUT-252: 비로그인 오늘의 MVP (QA-222 분리 시 누락)
                                        .requestMatchers(HttpMethod.GET, "/api/v1/bff/guild/list")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.GET, "/api/v1/bff/guild/{guildId}")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/api/v1/feeds/public")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/api/v1/feeds/{feedId}")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.GET, "/api/v1/feeds/{feedId}/comments")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/api/v1/feeds/search")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.GET, "/api/v1/feeds/category/**")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.GET, "/api/v1/feeds/user/{targetUserId}")
                                        .permitAll() // LUT-334: 비로그인 프로필 피드 탭 (PUBLIC 만 조회)
                                        .requestMatchers(HttpMethod.GET, "/api/v1/guilds/public")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/api/v1/guilds/ranking")
                                        .permitAll() // LUT-483: 길드 랭킹 (browse-first)
                                        .requestMatchers(HttpMethod.GET, "/api/v1/guilds/search")
                                        .permitAll()
                                        // QA-201: /guilds/my 는 인증 필요. 아래 {guildId} permitAll 패턴이
                                        // 리터럴 "my" 를 매칭해 인증을 우회하던 문제를 막기 위해 먼저 평가한다.
                                        .requestMatchers(HttpMethod.GET, "/api/v1/guilds/my")
                                        .authenticated()
                                        .requestMatchers(HttpMethod.GET, "/api/v1/guilds/{guildId}")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.GET, "/api/v1/guilds/{guildId}/posts")
                                        .permitAll() // QA-172: 비로그인 공개 길드 게시판 목록
                                        .requestMatchers(
                                                HttpMethod.GET, "/api/v1/guilds/{guildId}/members")
                                        .permitAll() // QA-172 댓글: 비로그인 공개 길드 멤버 목록
                                        .requestMatchers(
                                                HttpMethod.GET, "/api/v1/guilds/{guildId}/posts/**")
                                        .permitAll() // QA-179: 비로그인/비멤버 공개 길드 게시판 read
                                        .requestMatchers(
                                                HttpMethod.GET, "/api/v1/guild-invite-links/{code}")
                                        .permitAll() // LUT-519: 비로그인 초대 링크 미리보기
                                        .requestMatchers(
                                                HttpMethod.GET, "/api/v1/mission-categories")
                                        .permitAll() // QA-172: 비로그인 길드 카테고리 셀렉터
                                        .requestMatchers(
                                                HttpMethod.GET, "/api/v1/mypage/profile/{userId}")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/v1/missions/executions/weekly/{userId}")
                                        .permitAll() // LUT-320: 비로그인 프로필 주간 캘린더 (공개범위 마스킹)
                                        .requestMatchers(
                                                HttpMethod.GET, "/api/v1/mypage/nickname/check")
                                        .permitAll() // QA-108: 신규 가입 닉네임 중복 체크
                                        .requestMatchers(HttpMethod.GET, "/api/v1/notices")
                                        .permitAll() // QA-117: 비로그인 공지사항 목록
                                        .requestMatchers(HttpMethod.GET, "/api/v1/notices/{id}")
                                        .permitAll() // QA-117: 비로그인 공지사항 상세
                                        // LUT-297: 비로그인 랭킹 목록 (개인 랭킹 /my*, /nearby 는 인증 유지)
                                        .requestMatchers(HttpMethod.GET, "/api/v1/rankings")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/v1/rankings/missions",
                                                "/api/v1/rankings/streaks",
                                                "/api/v1/rankings/achievements",
                                                "/api/v1/rankings/realtime")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/v1/rankings/level",
                                                "/api/v1/rankings/level/**")
                                        .permitAll()
                                        // LUT-350: 비로그인 상점 열람 (레벨 1 = COMMON 기준가).
                                        // 구매는 POST 라 여기 걸리지 않고 인증이 유지된다.
                                        // LUT-629: 탑 층 목록 — 비로그인 열람 허용(방문객 입구)
                                        .requestMatchers(HttpMethod.GET, "/api/v1/tower/floors")
                                        .permitAll()
                                        // LUT-631: 입장 스냅샷 — 방문객은 visitor_key 로 점유자 확인(서비스 403)
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/v1/tower/instances/*/presence")
                                        .permitAll()
                                        // LUT-630: 탑 입장·퇴장 — 방문객(비로그인)도 허용 층에 들어온다.
                                        // 허용 층 판정은 TowerEntryService 가 한다(비허용 층은 150202)
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/v1/tower/floors/*/enter",
                                                "/api/v1/tower/leave")
                                        .permitAll()
                                        // LUT-633: 탑 채팅·리액션 — 방문객도 보낸다(점유자 판정은 서비스 150301)
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/v1/tower/instances/*/chat",
                                                "/api/v1/tower/instances/*/reactions")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/v1/tower/instances/*/chat",
                                                "/api/v1/tower/chat/reactions")
                                        .permitAll()
                                        // LUT-701: 입장 대기 상태·대기 그만하기 — 방문객도 줄을 선다(표 = 본인 확인,
                                        // 로그인 표는 서비스가 주인을 본다)
                                        .requestMatchers(HttpMethod.GET, "/api/v1/tower/queue/*")
                                        .permitAll()
                                        // LUT-738: 방 목록·방 이동 — 방문객도 같은 층 안에서 옮긴다(visitor_key, 판정은
                                        // 서비스)
                                        .requestMatchers(
                                                HttpMethod.GET, "/api/v1/tower/floors/*/instances")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/v1/tower/instances/*/move-in")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.DELETE, "/api/v1/tower/queue/*")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/api/v1/shop-items")
                                        .permitAll()
                                        // LUT-356: 비로그인 핑크다이아 묶음상품 열람 (상점 Browse-First)
                                        .requestMatchers(HttpMethod.GET, "/api/v1/diamond-bundles")
                                        .permitAll()

                                        // 관리자 전용 API
                                        .requestMatchers("/api/v1/users/experience/levels")
                                        .hasRole("ADMIN")
                                        .requestMatchers("/api/v1/attendance/init")
                                        .hasRole("ADMIN")

                                        // 나머지는 인증 필요
                                        .anyRequest()
                                        .authenticated())
                .logout(logout -> logout.logoutSuccessUrl("/"))
                // JWT 필터 추가
                .addFilterBefore(
                        jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // 내부 API 공유 시크릿 검증 필터 (LUT-244) — JWT 필터보다 먼저 실행해
                // /api/internal/** 를 헤더 인증으로 선차단한다.
                .addFilterBefore(internalApiKeyFilter, JwtAuthenticationFilter.class);

        http.exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint));

        return http.build();
    }
}
