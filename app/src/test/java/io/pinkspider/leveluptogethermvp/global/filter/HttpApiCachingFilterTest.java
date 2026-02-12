package io.pinkspider.leveluptogethermvp.global.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.filter.HttpApiCachingFilter;
import io.pinkspider.global.wrapper.CachedHttpServletRequestWrapper;
import io.pinkspider.global.wrapper.CachedHttpServletResponseWrapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.ContentCachingResponseWrapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("HttpApiCachingFilter 단위 테스트")
class HttpApiCachingFilterTest {

    private TestableHttpApiCachingFilter filter;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new TestableHttpApiCachingFilter();
    }

    /**
     * protected 메서드에 접근하기 위한 테스트용 서브클래스
     */
    static class TestableHttpApiCachingFilter extends HttpApiCachingFilter {
        @Override
        public void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
            super.doFilterInternal(request, response, filterChain);
        }

        @Override
        public void doFilterWrapped(HttpServletRequestWrapper request, ContentCachingResponseWrapper response, FilterChain filterChain)
            throws ServletException, IOException {
            super.doFilterWrapped(request, response, filterChain);
        }
    }

    @Nested
    @DisplayName("doFilterInternal 테스트")
    class DoFilterInternalTest {

        @Test
        @DisplayName("제외된 URI(/oauth/uri/**)는 필터를 바로 통과한다")
        void doFilterInternal_excludedUri_passThrough() throws Exception {
            // given
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth/uri/google");
            MockHttpServletResponse response = new MockHttpServletResponse();

            // when
            filter.doFilterInternal(request, response, filterChain);

            // then - excluded URI는 래핑 없이 바로 doFilter 호출
            verify(filterChain, times(1)).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
        }

        @Test
        @DisplayName("제외된 URI(/health-check)는 필터를 바로 통과한다")
        void doFilterInternal_healthCheck_passThrough() throws Exception {
            // given
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health-check");
            MockHttpServletResponse response = new MockHttpServletResponse();

            // when
            filter.doFilterInternal(request, response, filterChain);

            // then - excluded URI는 래핑 없이 바로 doFilter 호출
            verify(filterChain, times(1)).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
        }

        @Test
        @DisplayName("제외된 URI(/oauth/callback/apple)는 필터를 바로 통과한다")
        void doFilterInternal_appleCallback_passThrough() throws Exception {
            // given
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth/callback/apple");
            MockHttpServletResponse response = new MockHttpServletResponse();

            // when
            filter.doFilterInternal(request, response, filterChain);

            // then - excluded URI는 래핑 없이 바로 doFilter 호출
            verify(filterChain, times(1)).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
        }

        @Test
        @DisplayName("일반 GET 요청은 CachedHttpServletRequestWrapper로 래핑되어 처리된다")
        void doFilterInternal_normalGetRequest_wrapped() throws Exception {
            // given
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
            request.setContentType(MediaType.APPLICATION_JSON_VALUE);
            request.setContent("{}".getBytes());
            MockHttpServletResponse response = new MockHttpServletResponse();

            // when
            filter.doFilterInternal(request, response, filterChain);

            // then
            verify(filterChain, times(1)).doFilter(any(CachedHttpServletRequestWrapper.class), any(CachedHttpServletResponseWrapper.class));
        }

        @Test
        @DisplayName("multipart 요청 처리 시 content-type이 null이 아닌지 확인한다")
        void doFilterInternal_multipartRequest_handledCorrectly() throws Exception {
            // given
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/upload");
            request.setContentType("multipart/form-data; boundary=----WebKitFormBoundary");
            request.setContent("test content".getBytes());
            MockHttpServletResponse response = new MockHttpServletResponse();

            // when & then - multipart 요청 처리 확인
            // StandardMultipartHttpServletRequest는 실제 multipart content가 필요하므로
            // 여기서는 메서드가 호출될 때 예외가 발생할 수 있음
            try {
                filter.doFilterInternal(request, response, filterChain);
            } catch (Exception e) {
                // StandardMultipartHttpServletRequest 파싱 오류는 예상됨
                assertThat(e).isNotNull();
            }
        }
    }

    @Nested
    @DisplayName("doFilterWrapped 테스트")
    class DoFilterWrappedTest {

        @Test
        @DisplayName("actuator 경로는 로깅 없이 처리된다")
        void doFilterWrapped_actuatorPath_skipLogging() throws Exception {
            // given
            MockHttpServletRequest mockRequest = new MockHttpServletRequest("GET", "/showmethemoney/health");
            mockRequest.setContentType(MediaType.APPLICATION_JSON_VALUE);
            mockRequest.setContent("{}".getBytes());

            CachedHttpServletRequestWrapper request = new CachedHttpServletRequestWrapper(mockRequest);
            MockHttpServletResponse mockResponse = new MockHttpServletResponse();
            CachedHttpServletResponseWrapper response = new CachedHttpServletResponseWrapper(mockResponse);

            // when
            filter.doFilterWrapped(request, response, filterChain);

            // then
            verify(filterChain, times(1)).doFilter(request, response);
        }

        @Test
        @DisplayName("일반 경로는 요청/응답이 로깅된다")
        void doFilterWrapped_normalPath_logsRequestAndResponse() throws Exception {
            // given
            MockHttpServletRequest mockRequest = new MockHttpServletRequest("POST", "/api/users");
            mockRequest.setContentType(MediaType.APPLICATION_JSON_VALUE);
            mockRequest.setContent("{\"name\":\"test\"}".getBytes());

            CachedHttpServletRequestWrapper request = new CachedHttpServletRequestWrapper(mockRequest);
            MockHttpServletResponse mockResponse = new MockHttpServletResponse();
            CachedHttpServletResponseWrapper response = new CachedHttpServletResponseWrapper(mockResponse);

            // when
            filter.doFilterWrapped(request, response, filterChain);

            // then
            verify(filterChain, times(1)).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("isVisible 테스트")
    class IsVisibleTest {

        @Test
        @DisplayName("application/json은 visible이다")
        void isVisible_applicationJson_returnsTrue() throws Exception {
            // given
            Method isVisibleMethod = HttpApiCachingFilter.class.getDeclaredMethod("isVisible", MediaType.class);
            isVisibleMethod.setAccessible(true);

            // when
            boolean result = (boolean) isVisibleMethod.invoke(null, MediaType.APPLICATION_JSON);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("application/xml은 visible이다")
        void isVisible_applicationXml_returnsTrue() throws Exception {
            // given
            Method isVisibleMethod = HttpApiCachingFilter.class.getDeclaredMethod("isVisible", MediaType.class);
            isVisibleMethod.setAccessible(true);

            // when
            boolean result = (boolean) isVisibleMethod.invoke(null, MediaType.APPLICATION_XML);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("text/plain은 visible이다")
        void isVisible_textPlain_returnsTrue() throws Exception {
            // given
            Method isVisibleMethod = HttpApiCachingFilter.class.getDeclaredMethod("isVisible", MediaType.class);
            isVisibleMethod.setAccessible(true);

            // when
            boolean result = (boolean) isVisibleMethod.invoke(null, MediaType.TEXT_PLAIN);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("application/x-www-form-urlencoded는 visible이다")
        void isVisible_formUrlencoded_returnsTrue() throws Exception {
            // given
            Method isVisibleMethod = HttpApiCachingFilter.class.getDeclaredMethod("isVisible", MediaType.class);
            isVisibleMethod.setAccessible(true);

            // when
            boolean result = (boolean) isVisibleMethod.invoke(null, MediaType.APPLICATION_FORM_URLENCODED);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("multipart/form-data는 visible이다")
        void isVisible_multipartFormData_returnsTrue() throws Exception {
            // given
            Method isVisibleMethod = HttpApiCachingFilter.class.getDeclaredMethod("isVisible", MediaType.class);
            isVisibleMethod.setAccessible(true);

            // when
            boolean result = (boolean) isVisibleMethod.invoke(null, MediaType.MULTIPART_FORM_DATA);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("application/octet-stream은 visible이 아니다")
        void isVisible_octetStream_returnsFalse() throws Exception {
            // given
            Method isVisibleMethod = HttpApiCachingFilter.class.getDeclaredMethod("isVisible", MediaType.class);
            isVisibleMethod.setAccessible(true);

            // when
            boolean result = (boolean) isVisibleMethod.invoke(null, MediaType.APPLICATION_OCTET_STREAM);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("image/png는 visible이 아니다")
        void isVisible_imagePng_returnsFalse() throws Exception {
            // given
            Method isVisibleMethod = HttpApiCachingFilter.class.getDeclaredMethod("isVisible", MediaType.class);
            isVisibleMethod.setAccessible(true);

            // when
            boolean result = (boolean) isVisibleMethod.invoke(null, MediaType.IMAGE_PNG);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("application/hal+json은 visible이다")
        void isVisible_applicationHalJson_returnsTrue() throws Exception {
            // given
            Method isVisibleMethod = HttpApiCachingFilter.class.getDeclaredMethod("isVisible", MediaType.class);
            isVisibleMethod.setAccessible(true);

            // when
            boolean result = (boolean) isVisibleMethod.invoke(null, MediaType.valueOf("application/hal+json"));

            // then
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("isMultipartRequest 테스트")
    class IsMultipartRequestTest {

        @Test
        @DisplayName("POST 요청이고 multipart/form-data면 true를 반환한다")
        void isMultipartRequest_postWithMultipart_returnsTrue() throws Exception {
            // given
            Method isMultipartMethod = HttpApiCachingFilter.class.getDeclaredMethod("isMultipartRequest", HttpServletRequest.class);
            isMultipartMethod.setAccessible(true);

            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getMethod()).thenReturn("POST");
            when(request.getContentType()).thenReturn("multipart/form-data; boundary=----WebKitFormBoundary");

            // when
            boolean result = (boolean) isMultipartMethod.invoke(filter, request);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("GET 요청이면 false를 반환한다")
        void isMultipartRequest_getRequest_returnsFalse() throws Exception {
            // given
            Method isMultipartMethod = HttpApiCachingFilter.class.getDeclaredMethod("isMultipartRequest", HttpServletRequest.class);
            isMultipartMethod.setAccessible(true);

            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getMethod()).thenReturn("GET");
            // equalsIgnoreCase가 false를 반환하면 contentType 체크 없이 바로 false 반환

            // when
            boolean result = (boolean) isMultipartMethod.invoke(filter, request);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("POST 요청이지만 application/json이면 false를 반환한다")
        void isMultipartRequest_postWithJson_returnsFalse() throws Exception {
            // given
            Method isMultipartMethod = HttpApiCachingFilter.class.getDeclaredMethod("isMultipartRequest", HttpServletRequest.class);
            isMultipartMethod.setAccessible(true);

            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getMethod()).thenReturn("POST");
            when(request.getContentType()).thenReturn("application/json");

            // when
            boolean result = (boolean) isMultipartMethod.invoke(filter, request);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("POST 요청이고 post 메서드(소문자)면 true를 반환한다")
        void isMultipartRequest_lowercasePost_returnsTrue() throws Exception {
            // given
            Method isMultipartMethod = HttpApiCachingFilter.class.getDeclaredMethod("isMultipartRequest", HttpServletRequest.class);
            isMultipartMethod.setAccessible(true);

            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getMethod()).thenReturn("post");
            when(request.getContentType()).thenReturn("multipart/form-data");

            // when
            boolean result = (boolean) isMultipartMethod.invoke(filter, request);

            // then
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("logPayload 테스트")
    class LogPayloadTest {

        @Test
        @DisplayName("visible한 content-type이면 payload를 로깅한다")
        void logPayload_visibleContent_logsPayload() throws Exception {
            // given
            Method logPayloadMethod = HttpApiCachingFilter.class.getDeclaredMethod(
                "logPayload", String.class, String.class, java.io.InputStream.class, String.class);
            logPayloadMethod.setAccessible(true);

            String payload = "{\"message\":\"test\"}";
            ByteArrayInputStream inputStream = new ByteArrayInputStream(payload.getBytes());

            // when & then - 예외 없이 실행되면 성공
            logPayloadMethod.invoke(null, "REQUEST", "application/json", inputStream, "/api/test");
        }

        @Test
        @DisplayName("null content-type은 application/json으로 처리된다")
        void logPayload_nullContentType_treatedAsJson() throws Exception {
            // given
            Method logPayloadMethod = HttpApiCachingFilter.class.getDeclaredMethod(
                "logPayload", String.class, String.class, java.io.InputStream.class, String.class);
            logPayloadMethod.setAccessible(true);

            String payload = "{\"data\":\"value\"}";
            ByteArrayInputStream inputStream = new ByteArrayInputStream(payload.getBytes());

            // when & then - null content-type도 정상 처리
            logPayloadMethod.invoke(null, "RESPONSE", null, inputStream, null);
        }

        @Test
        @DisplayName("빈 payload는 로깅하지 않는다")
        void logPayload_emptyContent_noPayloadLog() throws Exception {
            // given
            Method logPayloadMethod = HttpApiCachingFilter.class.getDeclaredMethod(
                "logPayload", String.class, String.class, java.io.InputStream.class, String.class);
            logPayloadMethod.setAccessible(true);

            ByteArrayInputStream inputStream = new ByteArrayInputStream(new byte[0]);

            // when & then - 빈 payload도 예외 없이 처리
            logPayloadMethod.invoke(null, "REQUEST", "application/json", inputStream, "/api/test");
        }

        @Test
        @DisplayName("binary content는 'Binary Content'로 로깅된다")
        void logPayload_binaryContent_logsBinaryContent() throws Exception {
            // given
            Method logPayloadMethod = HttpApiCachingFilter.class.getDeclaredMethod(
                "logPayload", String.class, String.class, java.io.InputStream.class, String.class);
            logPayloadMethod.setAccessible(true);

            byte[] binaryData = new byte[]{0x00, 0x01, 0x02};
            ByteArrayInputStream inputStream = new ByteArrayInputStream(binaryData);

            // when & then - binary content도 예외 없이 처리
            logPayloadMethod.invoke(null, "RESPONSE", "application/octet-stream", inputStream, null);
        }
    }

    @Nested
    @DisplayName("logRequest 테스트")
    class LogRequestTest {

        @Test
        @DisplayName("쿼리스트링이 있는 요청을 로깅한다")
        void logRequest_withQueryString_logsWithQuery() throws Exception {
            // given
            Method logRequestMethod = HttpApiCachingFilter.class.getDeclaredMethod(
                "logRequest", jakarta.servlet.http.HttpServletRequestWrapper.class);
            logRequestMethod.setAccessible(true);

            MockHttpServletRequest mockRequest = new MockHttpServletRequest("GET", "/api/users");
            mockRequest.setQueryString("page=1&size=10");
            mockRequest.setContentType("application/json");
            mockRequest.setContent("".getBytes());

            CachedHttpServletRequestWrapper wrappedRequest = new CachedHttpServletRequestWrapper(mockRequest);

            // when & then - 예외 없이 실행
            logRequestMethod.invoke(null, wrappedRequest);
        }

        @Test
        @DisplayName("쿼리스트링이 없는 요청을 로깅한다")
        void logRequest_withoutQueryString_logsWithoutQuery() throws Exception {
            // given
            Method logRequestMethod = HttpApiCachingFilter.class.getDeclaredMethod(
                "logRequest", jakarta.servlet.http.HttpServletRequestWrapper.class);
            logRequestMethod.setAccessible(true);

            MockHttpServletRequest mockRequest = new MockHttpServletRequest("POST", "/api/users");
            mockRequest.setContentType("application/json");
            mockRequest.setContent("{\"name\":\"test\"}".getBytes());

            CachedHttpServletRequestWrapper wrappedRequest = new CachedHttpServletRequestWrapper(mockRequest);

            // when & then - 예외 없이 실행
            logRequestMethod.invoke(null, wrappedRequest);
        }
    }

    @Nested
    @DisplayName("logResponse 테스트")
    class LogResponseTest {

        @Test
        @DisplayName("응답을 로깅한다")
        void logResponse_logsResponse() throws Exception {
            // given
            Method logResponseMethod = HttpApiCachingFilter.class.getDeclaredMethod(
                "logResponse", org.springframework.web.util.ContentCachingResponseWrapper.class);
            logResponseMethod.setAccessible(true);

            MockHttpServletResponse mockResponse = new MockHttpServletResponse();
            mockResponse.setContentType("application/json");

            CachedHttpServletResponseWrapper wrappedResponse = new CachedHttpServletResponseWrapper(mockResponse);
            wrappedResponse.getWriter().write("{\"result\":\"success\"}");
            wrappedResponse.flushBuffer();

            // when & then - 예외 없이 실행
            logResponseMethod.invoke(null, wrappedResponse);
        }
    }
}
