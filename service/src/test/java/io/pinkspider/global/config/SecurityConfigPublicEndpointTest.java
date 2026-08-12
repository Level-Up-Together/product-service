package io.pinkspider.global.config;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.pinkspider.global.security.AuthEntryPointJwt;
import io.pinkspider.global.security.InternalApiKeyFilter;
import io.pinkspider.global.security.JwtAuthenticationFilter;
import io.pinkspider.global.security.JwtUtil;
import io.pinkspider.global.security.OAuth2Properties;
import io.pinkspider.global.security.TokenBlacklistChecker;
import io.pinkspider.global.security.UserExistenceChecker;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * SecurityConfig 의 경로 인가 규칙 회귀 가드.
 *
 * <p><b>왜 별도 테스트가 필요한가</b> — 이 저장소의 컨트롤러 테스트는 전부 {@code ControllerTestConfig} 를 import 하고, 그 안의
 * 테스트용 필터체인이 {@code anyRequest().permitAll()} 로 시큐리티를 통째로 끈다. 게다가 {@link SecurityConfig} 자체가
 * {@code @Profile("!test")} 라 {@code @ActiveProfiles("test")} 인 슬라이스 테스트에는 아예 올라오지 않는다. 즉 <b>컨트롤러
 * 테스트는 구조적으로 경로 인가 규칙을 검증할 수 없다.</b>
 *
 * <p>LUT-350 이 이 구멍에 빠졌다. 컨트롤러를 {@code @CurrentUser(required = false)} 로 열고 "비로그인 열람" 테스트까지 통과했지만,
 * 그 앞단 필터체인에 permitAll 을 추가하지 않아 개발서버에서는 계속 401 이 났다. {@code @CurrentUser} 는 argument resolver 라
 * 필터체인을 통과한 뒤에야 동작한다 — 비로그인 허용은 <b>필터(permitAll) + resolver(required=false)</b> 두 짝이 맞아야 완성된다.
 *
 * <p>그래서 이 테스트는 실제 {@link SecurityConfig} 만 올려 익명 요청의 인가 결과를 본다. 컨트롤러는 하나도 등록하지 않는다 — 보려는 것은 핸들러
 * 동작이 아니라 인가 판정뿐이다. 차단되면 {@link AuthEntryPointJwt} 가 401 을 쓰고, 허용되면 핸들러가 없으니 404 가 난다. 그래서 단언은 "401
 * 인가 아닌가"로 충분하다.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(
        classes = {SecurityConfig.class, SecurityConfigPublicEndpointTest.SecurityTestBeans.class})
@ActiveProfiles("security-guard") // "test" 가 아니어야 @Profile("!test") 인 SecurityConfig 가 로드된다
@TestPropertySource(
        properties = {
            "management.endpoints.web.base-path=/actuator",
            "app.security.internal-api.key="
        })
class SecurityConfigPublicEndpointTest {

    @Autowired private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /**
     * 비로그인 열람을 허용해야 하는 GET 경로 (browse-first).
     *
     * <p>SecurityConfig 의 "Browse-first" 블록과 짝을 이룬다. 여기서 빠지면 LUT-350 처럼 조용히 401 로 돌아간다.
     */
    @ParameterizedTest(name = "익명 GET 허용: {0}")
    @ValueSource(
            strings = {
                "/api/v1/bff/home",
                "/api/v1/bff/home/mvp",
                "/api/v1/bff/guild/list",
                "/api/v1/bff/guild/1",
                "/api/v1/feeds/public",
                "/api/v1/feeds/1",
                "/api/v1/feeds/1/comments",
                "/api/v1/feeds/search",
                "/api/v1/feeds/category/DAILY",
                "/api/v1/feeds/user/user-1",
                "/api/v1/guilds/public",
                "/api/v1/guilds/search",
                "/api/v1/guilds/1",
                "/api/v1/guilds/1/posts",
                "/api/v1/guilds/1/members",
                "/api/v1/mission-categories",
                "/api/v1/mypage/profile/user-1",
                "/api/v1/missions/executions/weekly/user-1",
                "/api/v1/mypage/nickname/check",
                "/api/v1/notices",
                "/api/v1/notices/1",
                "/api/v1/rankings",
                "/api/v1/rankings/missions",
                "/api/v1/rankings/level",
                "/api/v1/shop-items" // LUT-350
            })
    void 익명_GET_은_401_이_아니어야_한다(String path) throws Exception {
        int status = mockMvc.perform(get(path)).andReturn().getResponse().getStatus();

        Assertions.assertThat(status)
                .as("%s 는 비로그인 열람이 허용돼야 한다 (SecurityConfig permitAll 누락 의심)", path)
                .isNotEqualTo(401);
    }

    /**
     * 반드시 인증을 요구해야 하는 경로 — 실수로 열리는 방향을 막는다.
     *
     * <p>QA-201 선례: {@code /api/v1/guilds/{guildId}} permitAll 패턴이 리터럴 "my" 를 매칭해 {@code
     * /api/v1/guilds/my} 가 인증 없이 뚫렸다. 공개 경로를 추가할 때 인접한 개인 데이터 경로가 같이 열리지 않는지 이 목록이 잡아낸다.
     */
    @ParameterizedTest(name = "익명 GET 차단: {0}")
    @ValueSource(
            strings = {
                "/api/v1/guilds/my",
                "/api/v1/rankings/my",
                "/api/v1/rankings/nearby",
                "/api/v1/mypage/profile",
                "/api/v1/diamonds/balance"
            })
    void 익명_GET_은_401_이어야_한다(String path) throws Exception {
        int status = mockMvc.perform(get(path)).andReturn().getResponse().getStatus();

        Assertions.assertThat(status).as("%s 는 인증이 필요한 개인 데이터 경로다", path).isEqualTo(401);
    }

    /** LUT-350: 목록은 열되 구매는 잠근다. GET 한정 permitAll 이 POST 로 새지 않는지 확인한다. */
    @ParameterizedTest(name = "익명 POST 차단: {0}")
    @ValueSource(strings = {"/api/v1/shop-items/1/purchase"})
    void 익명_POST_는_401_이어야_한다(String path) throws Exception {
        int status = mockMvc.perform(post(path)).andReturn().getResponse().getStatus();

        Assertions.assertThat(status).as("%s 는 로그인 필수다", path).isEqualTo(401);
    }

    /**
     * SecurityConfig 가 요구하는 협력 빈들.
     *
     * <p>JwtAuthenticationFilter 는 실물을 쓴다 — 토큰 없는 요청은 아무것도 하지 않고 체인을 통과시키므로 익명 경로 판정을 그대로 재현한다.
     * 모킹하면 doFilter 가 체인을 이어주지 않아 요청이 필터에서 멈춰 테스트가 무의미해진다.
     */
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    static class SecurityTestBeans {

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

        @Bean
        OAuth2Properties oAuth2Properties() {
            return new OAuth2Properties();
        }
    }
}
