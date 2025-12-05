package io.pinkspider.global.component;

import static com.slack.api.webhook.WebhookPayloads.payload;

import com.slack.api.Slack;
import com.slack.api.model.Attachment;
import com.slack.api.model.Field;
import com.slack.api.webhook.WebhookResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class SlackNotifier {

    @Value("${app.slack.webhook.url}")
    private String webhookUrl;

    @Value(value = "${spring.application.name}")
    private String applicationName;

    private final Slack slackClient = Slack.getInstance();

    @Async
    public CompletableFuture<WebhookResponse> sendSlackAlert(Exception exception, HttpServletRequest request, String traceId, String spanId) {
        WebhookResponse response = null;
        try {
            response = slackClient.send(webhookUrl, payload(p -> p
                .text("서버 에러 발생! 백엔드 확인 요망")
                .attachments(
                    List.of(generateSlackAttachment(exception, request, traceId, spanId))
                )
            ));
        } catch (IOException slackError) {
            log.error("Slack 통신과의 예외 발생");
        }
        return CompletableFuture.completedFuture(response);
    }

    private Attachment generateSlackAttachment(Exception exception, HttpServletRequest request, String traceId, String spanId) {
        String requestTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").format(LocalDateTime.now());
        String targetName = Arrays.stream(exception.getStackTrace()).findFirst().toString();
        String xffHeader = request.getHeader("X-FORWARDED-FOR");

        return Attachment.builder()
            .color("danger")
            .title(requestTime + " 발생 에러 로그")
            .fields(List.of(
                    generateSlackField("Service Name", applicationName),
                    generateSlackField("Target Name", targetName),
                    generateSlackField("Request IP", xffHeader == null ? request.getRemoteAddr() : xffHeader),
                    generateSlackField("Request URL", request.getMethod() + " " + request.getRequestURI()),
                    generateSlackField("Trace ID", traceId),
                    generateSlackField("Span ID", spanId),
                    generateSlackField("Error Message", exception.getMessage())
                )
            )
            .build();
    }

    private Field generateSlackField(String title, String value) {
        return Field.builder()
            .title(title)
            .value(value)
            .valueShortEnough(false)
            .build();
    }
}
