package io.pinkspider.leveluptogethermvp.notificationservice.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.global.enums.NotificationType;
import io.pinkspider.global.messaging.dto.AppPushMessageDto;
import io.pinkspider.leveluptogethermvp.notificationservice.application.FcmPushService;
import io.pinkspider.leveluptogethermvp.notificationservice.application.NotificationService;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.PushMessageRequest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.MapRecord;

@ExtendWith(MockitoExtension.class)
@DisplayName("AppPushMessageConsumer 테스트")
class AppPushMessageConsumerTest {

    private static final String STREAM_KEY = "stream:app-push";
    private static final String USER_ID = "user-1";

    @Mock private FcmPushService fcmPushService;

    @Mock private NotificationService notificationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AppPushMessageConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AppPushMessageConsumer(fcmPushService, notificationService, objectMapper);
    }

    private MapRecord<String, String, String> toRecord(AppPushMessageDto message) throws Exception {
        String payload = objectMapper.writeValueAsString(message);
        return MapRecord.create(STREAM_KEY, Map.of("payload", payload));
    }

    @Test
    @DisplayName("내부 발행 GUILD_DM 푸시는 재현지화 없이 원본 텍스트 그대로 전송한다 (LUT-262)")
    void guildDm_internalPush_keepsOriginalText() throws Exception {
        AppPushMessageDto message =
                AppPushMessageDto.builder()
                        .userId(USER_ID)
                        .title("백루미")
                        .body("Ep")
                        .notificationType(NotificationType.GUILD_DM.name())
                        .build();

        consumer.onMessage(toRecord(message));

        verify(notificationService, never()).localizePushText(anyString(), any(), any());
        ArgumentCaptor<PushMessageRequest> captor =
                ArgumentCaptor.forClass(PushMessageRequest.class);
        verify(fcmPushService).sendToUser(eq(USER_ID), captor.capture());
        assertThat(captor.getValue().title()).isEqualTo("백루미");
        assertThat(captor.getValue().body()).isEqualTo("Ep");
    }

    @Test
    @DisplayName("내부 발행 FRIEND_REQUEST 푸시도 재현지화 없이 원본 텍스트 그대로 전송한다")
    void friendRequest_internalPush_keepsOriginalText() throws Exception {
        AppPushMessageDto message =
                AppPushMessageDto.builder()
                        .userId(USER_ID)
                        .title("새 친구 요청")
                        .body("백루미님이 친구 요청을 보냈습니다.")
                        .notificationType(NotificationType.FRIEND_REQUEST.name())
                        .build();

        consumer.onMessage(toRecord(message));

        verify(notificationService, never()).localizePushText(anyString(), any(), any());
        ArgumentCaptor<PushMessageRequest> captor =
                ArgumentCaptor.forClass(PushMessageRequest.class);
        verify(fcmPushService).sendToUser(eq(USER_ID), captor.capture());
        assertThat(captor.getValue().title()).isEqualTo("새 친구 요청");
        assertThat(captor.getValue().body()).isEqualTo("백루미님이 친구 요청을 보냈습니다.");
    }

    @Test
    @DisplayName("외부 발행 INQUIRY_REPLIED 푸시는 사용자 locale로 재현지화하여 전송한다")
    void inquiryReplied_externalPush_localizesText() throws Exception {
        AppPushMessageDto message =
                AppPushMessageDto.builder()
                        .userId(USER_ID)
                        .title("문의 답변이 도착했어요")
                        .body("결제 문의")
                        .notificationType("INQUIRY_REPLIED")
                        .data(Map.of("inquiry_id", "42"))
                        .build();
        when(notificationService.localizePushText(
                        USER_ID, NotificationType.INQUIRY_REPLIED, "결제 문의"))
                .thenReturn(new String[] {"Your inquiry has been answered", "Check the reply to 결제 문의."});

        consumer.onMessage(toRecord(message));

        verify(notificationService).saveInquiryRepliedInApp(USER_ID, 42L, "결제 문의");
        ArgumentCaptor<PushMessageRequest> captor =
                ArgumentCaptor.forClass(PushMessageRequest.class);
        verify(fcmPushService).sendToUser(eq(USER_ID), captor.capture());
        assertThat(captor.getValue().title()).isEqualTo("Your inquiry has been answered");
        assertThat(captor.getValue().body()).isEqualTo("Check the reply to 결제 문의.");
    }

    @Test
    @DisplayName("알 수 없는 notification_type은 원본 텍스트 그대로 전송한다")
    void unknownType_keepsOriginalText() throws Exception {
        AppPushMessageDto message =
                AppPushMessageDto.builder()
                        .userId(USER_ID)
                        .title("제목")
                        .body("본문")
                        .notificationType("SOME_UNKNOWN_TYPE")
                        .build();

        consumer.onMessage(toRecord(message));

        verify(notificationService, never()).localizePushText(anyString(), any(), any());
        ArgumentCaptor<PushMessageRequest> captor =
                ArgumentCaptor.forClass(PushMessageRequest.class);
        verify(fcmPushService).sendToUser(eq(USER_ID), captor.capture());
        assertThat(captor.getValue().title()).isEqualTo("제목");
        assertThat(captor.getValue().body()).isEqualTo("본문");
    }
}
