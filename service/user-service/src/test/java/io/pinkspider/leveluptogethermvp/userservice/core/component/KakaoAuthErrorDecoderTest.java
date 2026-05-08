package io.pinkspider.leveluptogethermvp.userservice.core.component;

import static org.assertj.core.api.Assertions.assertThat;

import feign.Request;
import feign.Request.HttpMethod;
import feign.Response;
import io.pinkspider.leveluptogethermvp.userservice.core.exception.KakaoAuthClientException;
import io.pinkspider.leveluptogethermvp.userservice.core.exception.KakaoAuthServerException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("KakaoAuthErrorDecoder 테스트")
class KakaoAuthErrorDecoderTest {

    private KakaoAuthErrorDecoder decoder;

    @BeforeEach
    void setUp() {
        decoder = new KakaoAuthErrorDecoder();
    }

    private Response buildResponse(int status, String body) {
        Map<String, Collection<String>> headers = Collections.emptyMap();
        Request dummyRequest = Request.create(
            HttpMethod.POST,
            "https://kauth.kakao.com/oauth/token",
            headers,
            null,
            StandardCharsets.UTF_8,
            null
        );
        return Response.builder()
            .status(status)
            .reason("reason")
            .request(dummyRequest)
            .headers(headers)
            .body(body, StandardCharsets.UTF_8)
            .build();
    }

    @Nested
    @DisplayName("4xx 응답 처리")
    class ClientErrorTest {

        @Test
        @DisplayName("400 응답이면 KakaoAuthClientException을 반환한다")
        void decode_400response_returnsKakaoAuthClientException() {
            // given
            Response response = buildResponse(400, "{\"error\":\"invalid_grant\"}");

            // when
            Exception result = decoder.decode("KakaoOAuthClient#getToken()", response);

            // then
            assertThat(result).isInstanceOf(KakaoAuthClientException.class);
            assertThat(result.getMessage()).contains("Client Error");
        }

        @Test
        @DisplayName("401 응답이면 KakaoAuthClientException을 반환한다")
        void decode_401response_returnsKakaoAuthClientException() {
            // given
            Response response = buildResponse(401, "{\"error\":\"unauthorized\"}");

            // when
            Exception result = decoder.decode("KakaoOAuthClient#getToken()", response);

            // then
            assertThat(result).isInstanceOf(KakaoAuthClientException.class);
        }

        @Test
        @DisplayName("499 응답이면 KakaoAuthClientException을 반환한다")
        void decode_499response_returnsKakaoAuthClientException() {
            // given
            Response response = buildResponse(499, "{\"error\":\"client_closed\"}");

            // when
            Exception result = decoder.decode("KakaoOAuthClient#getToken()", response);

            // then
            assertThat(result).isInstanceOf(KakaoAuthClientException.class);
        }
    }

    @Nested
    @DisplayName("5xx 응답 처리")
    class ServerErrorTest {

        @Test
        @DisplayName("500 응답이면 KakaoAuthServerException을 반환한다")
        void decode_500response_returnsKakaoAuthServerException() {
            // given
            Response response = buildResponse(500, "{\"error\":\"internal_server_error\"}");

            // when
            Exception result = decoder.decode("KakaoOAuthClient#getToken()", response);

            // then
            assertThat(result).isInstanceOf(KakaoAuthServerException.class);
            assertThat(result.getMessage()).contains("Server Error");
        }

        @Test
        @DisplayName("503 응답이면 KakaoAuthServerException을 반환한다")
        void decode_503response_returnsKakaoAuthServerException() {
            // given
            Response response = buildResponse(503, "{\"error\":\"service_unavailable\"}");

            // when
            Exception result = decoder.decode("KakaoOAuthClient#getToken()", response);

            // then
            assertThat(result).isInstanceOf(KakaoAuthServerException.class);
        }
    }

    @Nested
    @DisplayName("기타 상태 코드 처리")
    class OtherStatusTest {

        @Test
        @DisplayName("300 응답이면 RuntimeException을 반환한다")
        void decode_300response_returnsRuntimeException() {
            // given
            Response response = buildResponse(301, "redirect");

            // when
            Exception result = decoder.decode("KakaoOAuthClient#getToken()", response);

            // then
            assertThat(result).isInstanceOf(RuntimeException.class);
            assertThat(result.getMessage()).contains("Unknown Kakao API error");
        }
    }
}
