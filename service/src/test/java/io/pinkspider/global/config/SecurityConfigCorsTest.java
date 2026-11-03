package io.pinkspider.global.config;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.pinkspider.global.security.AuthEntryPointJwt;
import io.pinkspider.global.security.InternalApiKeyFilter;
import io.pinkspider.global.security.JwtAuthenticationFilter;
import io.pinkspider.global.security.JwtUtil;
import io.pinkspider.global.security.OAuth2Properties;
import io.pinkspider.global.security.TokenBlacklistChecker;
import io.pinkspider.global.security.UserExistenceChecker;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * SecurityConfig CORS 허용 헤더 회귀 가드 (LUT-587).
 *
 * <p><b>왜 필요한가</b> — {@code setAllowedHeaders(...)} 는 와일드카드가 아닌 <b>명시적 허용목록</b>이다. 클라이언트가 목록에 없는
 * 커스텀 헤더를 붙이면 브라우저의 preflight(OPTIONS)가 그 헤더를 허용 목록으로 돌려받지 못해 <b>본 요청을 아예 보내지 않는다</b>. 서버 로그에는 아무것도
 * 남지 않고 브라우저 콘솔만 "CORS error" 라 원인 찾기가 오래 걸린다.
 *
 * <p>LUT-577/578 이 이 구멍에 빠졌다. 미션 생성에 {@code Idempotency-Key} 를 붙였는데 허용목록에 넣지 않아 개발서버에서 미션 생성만 CORS
 * error 로 실패했다 (목록 조회 등 커스텀 헤더가 없거나 이미 허용된 API 는 정상). 같이 점검하다 {@code X-Device-Type}(웹 로그아웃이 백엔드를 직접
 * 호출할 때 붙인다, LUT-336)도 빠져 있던 것을 발견했다 — 호출 실패를 try/catch 로 삼키고 있어 증상이 드러나지 않았다.
 *
 * <p>컨트롤러 슬라이스 테스트는 이것을 잡지 못한다 — {@code ControllerTestConfig} 가 시큐리티를 끄고, {@link SecurityConfig}
 * 자체가 {@code @Profile("!test")} 라 올라오지도 않는다. 그래서 {@link SecurityConfigPublicEndpointTest} 와 같은 방식으로
 * 실제 {@link SecurityConfig} 만 올려 preflight 응답을 본다. 컨트롤러는 등록하지 않는다 — 보려는 것은 CORS 필터의 판정뿐이다.
 *
 * <p><b>프론트·RN 에 커스텀 헤더를 추가하면 {@link #ALLOWED_CLIENT_HEADERS} 와 SecurityConfig 양쪽에 함께 추가할 것.</b>
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = {SecurityConfig.class, SecurityConfigCorsTest.CorsTestBeans.class})
@ActiveProfiles("security-guard") // "test" 가 아니어야 @Profile("!test") 인 SecurityConfig 가 로드된다
@TestPropertySource(
        properties = {
            "management.endpoints.web.base-path=/actuator",
            "app.security.internal-api.key="
        })
@DisplayName("SecurityConfig CORS 허용 헤더 (LUT-587)")
class SecurityConfigCorsTest {

    /** 테스트 빈이 등록하는 허용 오리진 (prod/dev 는 config 의 oauth2.allowed-origins) */
    private static final String ORIGIN = "https://dev.level-up-together.com";

    /**
     * 클라이언트(웹·RN WebView)가 실제로 붙이는 커스텀 헤더 전체. 하나라도 빠지면 그 API 는 브라우저에서 CORS error 로 실패한다.
     *
     * <p>소문자로 비교한다 — preflight 응답의 {@code Access-Control-Allow-Headers} 는 요청 헤더 이름을 그대로 echo 하므로
     * 대소문자가 요청에 따라 달라진다.
     */
    private static final List<String> ALLOWED_CLIENT_HEADERS =
            List.of(
                    "authorization",
                    "content-type",
                    "x-timezone", // 모든 요청 (fetch-client)
                    "x-signup-token", // OAuth 가입 플로우
                    "x-device-type", // 로그아웃 직접 호출 (LUT-336/587)
                    "idempotency-key"); // 미션 생성 (LUT-577/578)

    @Autowired private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private MvcResult preflight(String requestHeaders) throws Exception {
        return mockMvc.perform(
                        options("/api/v1/missions")
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                                .header("Access-Control-Request-Method", "POST")
                                .header("Access-Control-Request-Headers", requestHeaders))
                .andReturn();
    }

    @ParameterizedTest(name = "preflight 허용: {0}")
    @ValueSource(
            strings = {
                "Authorization",
                "Content-Type",
                "X-Timezone",
                "X-Signup-Token",
                "X-Device-Type",
                "Idempotency-Key"
            })
    @DisplayName("클라이언트가 붙이는 커스텀 헤더는 preflight 에서 허용된다")
    void preflight_allowsClientHeaders(String headerName) throws Exception {
        MvcResult result = preflight(headerName);

        Assertions.assertThat(result.getResponse().getStatus())
                .as("preflight 가 거부되면 브라우저가 본 요청을 보내지 않는다 (CORS error)")
                .isEqualTo(200);
        Assertions.assertThat(result.getResponse().getHeader("Access-Control-Allow-Headers"))
                .as("허용 목록에 %s 가 없으면 브라우저가 요청을 차단한다", headerName)
                .isNotNull()
                .containsIgnoringCase(headerName);
    }

    @Test
    @DisplayName("미션 생성 요청이 실제로 보내는 헤더 조합(Idempotency-Key 포함)이 한 번에 허용된다")
    void preflight_allowsMissionCreateHeaderSet() throws Exception {
        String sent = "content-type,authorization,x-timezone,idempotency-key";

        MvcResult result = preflight(sent);
        String allowed = result.getResponse().getHeader("Access-Control-Allow-Headers");

        Assertions.assertThat(result.getResponse().getStatus()).isEqualTo(200);
        Assertions.assertThat(allowed).isNotNull();
        String lowered = allowed.toLowerCase();
        for (String header : sent.split(",")) {
            Assertions.assertThat(lowered).contains(header);
        }
    }

    @Test
    @DisplayName("ALLOWED_CLIENT_HEADERS 전부가 한 번에 허용된다 (목록 누락 가드)")
    void preflight_allowsEveryKnownClientHeader() throws Exception {
        String sent = String.join(",", ALLOWED_CLIENT_HEADERS);

        MvcResult result = preflight(sent);
        String allowed = result.getResponse().getHeader("Access-Control-Allow-Headers");

        Assertions.assertThat(result.getResponse().getStatus()).isEqualTo(200);
        Assertions.assertThat(allowed).isNotNull();
        Assertions.assertThat(allowed.toLowerCase()).contains(ALLOWED_CLIENT_HEADERS);
    }

    @Test
    @DisplayName("허용목록에 없는 헤더는 거부된다 — 와일드카드가 아님을 고정한다")
    void preflight_rejectsUnknownHeader() throws Exception {
        MvcResult result = preflight("X-Totally-Unknown");

        Assertions.assertThat(result.getResponse().getStatus())
                .as("명시적 허용목록이므로 모르는 헤더는 403 이어야 한다 (이 단언이 깨지면 와일드카드로 바뀐 것)")
                .isEqualTo(403);
    }

    @Test
    @DisplayName("preflight 응답에 허용 메서드·오리진·credentials·maxAge 가 실린다")
    void preflight_returnsOriginAndMethods() throws Exception {
        mockMvc.perform(
                        options("/api/v1/missions")
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                                .header("Access-Control-Request-Method", "POST")
                                .header("Access-Control-Request-Headers", "idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().string("Access-Control-Max-Age", "3600"))
                .andExpect(
                        header().string(
                                        "Access-Control-Allow-Methods",
                                        "GET,POST,PUT,PATCH,DELETE,OPTIONS"));
    }

    @Test
    @DisplayName("허용되지 않은 오리진의 preflight 는 거부된다")
    void preflight_rejectsUnknownOrigin() throws Exception {
        MvcResult result =
                mockMvc.perform(
                                options("/api/v1/missions")
                                        .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                                        .header("Access-Control-Request-Method", "POST")
                                        .header(
                                                "Access-Control-Request-Headers",
                                                "idempotency-key"))
                        .andReturn();

        Assertions.assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    /**
     * 에러 응답에도 CORS 헤더가 실리는지 — 실리지 않으면 멱등 409 처럼 정상 흐름인 에러를 브라우저가 CORS error 로 바꿔 버린다.
     *
     * <p>CORS 필터가 인가 필터보다 앞이라 인증 실패(401)에도 헤더가 붙는다. 같은 체인을 타는 409/400 도 동일하다.
     */
    @Test
    @DisplayName("인증 실패(401) 응답에도 CORS 헤더가 실린다 (필터 순서 — 409/400 도 동일)")
    void errorResponse_carriesCorsHeaders() throws Exception {
        mockMvc.perform(
                        post("/api/v1/missions")
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                                .header("Idempotency-Key", "11111111-2222-4333-8444-555555555555")
                                .contentType("application/json")
                                .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    static class CorsTestBeans {

        @Bean
        static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        @Bean
        AuthEntryPointJwt authEntryPointJwt() {
            return new AuthEntryPointJwt();
        }

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            return new JwtAuthenticationFilter(
                    Mockito.mock(JwtUtil.class),
                    Mockito.mock(TokenBlacklistChecker.class),
                    Mockito.mock(UserExistenceChecker.class));
        }

        @Bean
        InternalApiKeyFilter internalApiKeyFilter() {
            return new InternalApiKeyFilter("");
        }

        /** allowCredentials 경로(명시적 오리진)를 타게 한다 — prod/dev 와 같은 설정 */
        @Bean
        OAuth2Properties oAuth2Properties() {
            OAuth2Properties properties = new OAuth2Properties();
            properties.setAllowedOrigins(List.of(ORIGIN));
            return properties;
        }
    }
}
