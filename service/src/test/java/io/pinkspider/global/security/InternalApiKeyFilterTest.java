package io.pinkspider.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
@DisplayName("InternalApiKeyFilter 단위 테스트")
class InternalApiKeyFilterTest {

    private static final String HEADER = "X-Internal-Api-Key";
    private static final String KEY = "s3cr3t-internal-key";
    private static final String ACTUATOR = "/showmethemoney";

    @Mock private FilterChain filterChain;

    private static String basic(String user, String password) {
        return "Basic "
                + Base64.getEncoder()
                        .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    @Nested
    @DisplayName("키가 설정된 환경")
    class KeyConfigured {

        private final InternalApiKeyFilter filter = new InternalApiKeyFilter(KEY);

        @Test
        @DisplayName("올바른 키가 있으면 내부 API 요청을 통과시킨다")
        void internalRequest_withValidKey_passes() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/internal/shop-items");
            request.addHeader(HEADER, KEY);
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            verify(filterChain, times(1)).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("키가 없으면 401 로 차단하고 체인을 진행하지 않는다")
        void internalRequest_withoutKey_blocked() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/internal/users/search");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            verify(filterChain, never()).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentAsString()).contains("000401");
        }

        @Test
        @DisplayName("키가 틀리면 401 로 차단한다")
        void internalRequest_withWrongKey_blocked() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/internal/guilds");
            request.addHeader(HEADER, "wrong-key");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            verify(filterChain, never()).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(401);
        }

        @Test
        @DisplayName("내부 API 가 아닌 경로는 키 없이도 통과시킨다")
        void nonInternalRequest_passesWithoutKey() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/v1/mypage");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            verify(filterChain, times(1)).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("base-path 를 지정하지 않으면 /actuator 가 보호 대상이다")
        void defaultActuatorBasePath_isProtected() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/actuator/env");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            verify(filterChain, never()).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(401);
        }
    }

    @Nested
    @DisplayName("액추에이터 보호 (LUT-605)")
    class ActuatorProtection {

        private final InternalApiKeyFilter filter = new InternalApiKeyFilter(KEY, ACTUATOR);

        @ParameterizedTest(name = "{0} 는 키 없이 401")
        @ValueSource(
                strings = {
                    "/showmethemoney",
                    "/showmethemoney/",
                    "/showmethemoney/env",
                    "/showmethemoney/beans",
                    "/showmethemoney/prometheus",
                    "/showmethemoney/heapdump",
                    "/showmethemoney/threaddump",
                    "/showmethemoney/healthcheck",
                    "/showmethemoney/metrics/jvm.memory.used"
                })
        void actuatorEndpoint_withoutKey_blocked(String uri) throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI(uri);
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            verify(filterChain, never()).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentAsString()).contains("000401").contains(uri);
        }

        @ParameterizedTest(name = "{0} 는 공개(ALB·배포 헬스체크)")
        @ValueSource(
                strings = {
                    "/showmethemoney/health",
                    "/showmethemoney/health/liveness",
                    "/showmethemoney/health/readiness"
                })
        void healthEndpoint_passesWithoutKey(String uri) throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI(uri);
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            verify(filterChain, times(1)).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("접두사만 같은 다른 경로(/showmethemoneyX)는 액추에이터가 아니므로 통과한다")
        void similarPrefix_isNotActuator() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/showmethemoneyX/env");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            verify(filterChain, times(1)).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("X-Internal-Api-Key 헤더로 prometheus 를 읽을 수 있다")
        void prometheus_withHeaderKey_passes() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/showmethemoney/prometheus");
            request.addHeader(HEADER, KEY);
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            verify(filterChain, times(1)).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("Basic 인증의 비밀번호가 키와 같으면 통과한다 (사용자명 무관 — Prometheus/Alloy 스크레이프)")
        void prometheus_withBasicPassword_passes() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/showmethemoney/prometheus");
            request.addHeader("Authorization", basic("alloy", KEY));
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            verify(filterChain, times(1)).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("Basic 비밀번호가 틀리면 401")
        void prometheus_withWrongBasicPassword_blocked() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/showmethemoney/prometheus");
            request.addHeader("Authorization", basic("alloy", "nope"));
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            verify(filterChain, never()).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(401);
        }

        @ParameterizedTest(name = "Authorization=\"{0}\" 은 키로 인정하지 않는다")
        @ValueSource(
                strings = {"Bearer some.jwt.token", "Basic !!!not-base64!!!", "Basic bm9jb2xvbg=="})
        void malformedAuthorization_blocked(String authorization) throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/showmethemoney/env");
            request.addHeader("Authorization", authorization);
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            verify(filterChain, never()).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(401);
        }

        @Test
        @DisplayName("Basic 인증은 내부 API 에도 통한다 (키 전달 방식 2종 동일 취급)")
        void internalApi_withBasicPassword_passes() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/internal/kpi/daily");
            request.addHeader("Authorization", basic("", KEY));
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            verify(filterChain, times(1)).doFilter(request, response);
        }

        @Test
        @DisplayName("base-path 표기가 달라도(끝 슬래시·슬래시 없음) 같은 경로로 정규화한다")
        void basePath_isNormalized() {
            InternalApiKeyFilter trailing = new InternalApiKeyFilter(KEY, "/showmethemoney/");
            InternalApiKeyFilter noLeading = new InternalApiKeyFilter(KEY, "showmethemoney");
            InternalApiKeyFilter blank = new InternalApiKeyFilter(KEY, " ");

            assertThat(trailing.isProtectedActuatorPath("/showmethemoney/env")).isTrue();
            assertThat(trailing.isProtectedActuatorPath("/showmethemoney/health")).isFalse();
            assertThat(noLeading.isProtectedActuatorPath("/showmethemoney/env")).isTrue();
            assertThat(blank.isProtectedActuatorPath("/actuator/env")).isTrue();
            assertThat(blank.requiresKey(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("키가 미설정(fail-open)된 환경")
    class KeyNotConfigured {

        @Test
        @DisplayName("키가 공백이면 내부 API 요청을 검증 없이 통과시킨다")
        void internalRequest_blankKey_passes() throws Exception {
            InternalApiKeyFilter filter = new InternalApiKeyFilter("");
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/internal/shop-items");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            verify(filterChain, times(1)).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("키가 null 이어도 안전하게 통과시킨다")
        void internalRequest_nullKey_passes() throws Exception {
            InternalApiKeyFilter filter = new InternalApiKeyFilter(null);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/internal/shop-items");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            verify(filterChain, times(1)).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("키가 공백이면 액추에이터도 기존처럼 열린다 (config 선배포 없는 무중단 롤아웃)")
        void actuator_blankKey_passes() throws Exception {
            InternalApiKeyFilter filter = new InternalApiKeyFilter("", ACTUATOR);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/showmethemoney/prometheus");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            verify(filterChain, times(1)).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
}
