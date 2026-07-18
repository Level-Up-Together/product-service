package io.pinkspider.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
@DisplayName("InternalApiKeyFilter 단위 테스트")
class InternalApiKeyFilterTest {

    private static final String HEADER = "X-Internal-Api-Key";
    private static final String KEY = "s3cr3t-internal-key";

    @Mock private FilterChain filterChain;

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
    }
}
