package io.pinkspider.global.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.slack.api.Slack;
import com.slack.api.webhook.Payload;
import com.slack.api.webhook.WebhookResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("SlackNotifier 단위 테스트")
class SlackNotifierTest {

    @InjectMocks private SlackNotifier slackNotifier;

    @Mock private HttpServletRequest httpServletRequest;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(slackNotifier, "webhookUrl", "https://hooks.slack.com/test");
        ReflectionTestUtils.setField(slackNotifier, "applicationName", "product-service-test");
    }

    @Nested
    @DisplayName("sendSlackAlert 테스트")
    class SendSlackAlertTest {

        @Test
        @DisplayName("예외와 요청 정보를 슬랙 알림으로 전송한다")
        void sendSlackAlert_success() throws Exception {
            // given
            Exception exception = new RuntimeException("테스트 예외");
            WebhookResponse mockResponse =
                    WebhookResponse.builder().code(200).message("ok").build();

            Slack mockSlack = mock(Slack.class);
            when(mockSlack.send(anyString(), any(Payload.class))).thenReturn(mockResponse);
            ReflectionTestUtils.setField(slackNotifier, "slackClient", mockSlack);

            when(httpServletRequest.getHeader("X-FORWARDED-FOR")).thenReturn(null);
            when(httpServletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
            when(httpServletRequest.getMethod()).thenReturn("GET");
            when(httpServletRequest.getRequestURI()).thenReturn("/api/v1/test");

            // when
            CompletableFuture<WebhookResponse> result =
                    slackNotifier.sendSlackAlert(exception, httpServletRequest);

            // then
            assertThat(result).isNotNull();
            assertThat(result.get()).isNotNull();
            verify(mockSlack).send(anyString(), any(Payload.class));
        }

        @Test
        @DisplayName("X-FORWARDED-FOR 헤더가 있으면 해당 IP를 사용한다")
        void sendSlackAlert_withForwardedHeader() throws Exception {
            // given
            Exception exception = new RuntimeException("헤더 테스트 예외");
            WebhookResponse mockResponse =
                    WebhookResponse.builder().code(200).message("ok").build();

            Slack mockSlack = mock(Slack.class);
            when(mockSlack.send(anyString(), any(Payload.class))).thenReturn(mockResponse);
            ReflectionTestUtils.setField(slackNotifier, "slackClient", mockSlack);

            when(httpServletRequest.getHeader("X-FORWARDED-FOR")).thenReturn("10.0.0.1");
            when(httpServletRequest.getMethod()).thenReturn("POST");
            when(httpServletRequest.getRequestURI()).thenReturn("/api/v1/users");

            // when
            CompletableFuture<WebhookResponse> result =
                    slackNotifier.sendSlackAlert(exception, httpServletRequest);

            // then
            assertThat(result.get()).isNotNull();
            verify(mockSlack).send(anyString(), any(Payload.class));
        }

        @Test
        @DisplayName("Slack API 통신 실패 시 null response로 CompletableFuture를 반환한다")
        void sendSlackAlert_slackIoException() throws Exception {
            // given
            Exception exception = new RuntimeException("IO 예외 테스트");

            Slack mockSlack = mock(Slack.class);
            when(mockSlack.send(anyString(), any(Payload.class)))
                    .thenThrow(new IOException("Slack 통신 오류"));
            ReflectionTestUtils.setField(slackNotifier, "slackClient", mockSlack);

            when(httpServletRequest.getHeader("X-FORWARDED-FOR")).thenReturn(null);
            when(httpServletRequest.getRemoteAddr()).thenReturn("192.168.1.1");
            when(httpServletRequest.getMethod()).thenReturn("DELETE");
            when(httpServletRequest.getRequestURI()).thenReturn("/api/v1/resource");

            // when
            CompletableFuture<WebhookResponse> result =
                    slackNotifier.sendSlackAlert(exception, httpServletRequest);

            // then
            // IOException 발생 시 null response를 CompletableFuture로 감싸 반환
            assertThat(result).isNotNull();
            assertThat(result.get()).isNull();
        }
    }

    @Nested
    @DisplayName("sendAlert 테스트")
    class SendAlertTest {

        @Test
        @DisplayName("sendAlert 호출 시 sendSlackAlert를 위임 호출한다")
        void sendAlert_delegatesToSendSlackAlert() throws Exception {
            // given
            Exception exception = new RuntimeException("위임 테스트 예외");
            WebhookResponse mockResponse =
                    WebhookResponse.builder().code(200).message("ok").build();

            Slack mockSlack = mock(Slack.class);
            when(mockSlack.send(anyString(), any(Payload.class))).thenReturn(mockResponse);
            ReflectionTestUtils.setField(slackNotifier, "slackClient", mockSlack);

            when(httpServletRequest.getHeader("X-FORWARDED-FOR")).thenReturn(null);
            when(httpServletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
            when(httpServletRequest.getMethod()).thenReturn("GET");
            when(httpServletRequest.getRequestURI()).thenReturn("/api/v1/health");

            // when
            slackNotifier.sendAlert(exception, httpServletRequest);

            // then
            verify(mockSlack).send(anyString(), any(Payload.class));
        }
    }
}
