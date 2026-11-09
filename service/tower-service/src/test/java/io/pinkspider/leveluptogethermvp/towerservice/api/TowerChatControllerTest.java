package io.pinkspider.leveluptogethermvp.towerservice.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.towerservice.application.TowerChatService;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerChatException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerChatHistoryResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerChatMessage;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerReactionPresetsResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerReactionResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = TowerChatController.class,
        excludeAutoConfiguration = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class
        })
@Import(ControllerTestConfig.class)
@AutoConfigureRestDocs
@ActiveProfiles("test")
@DisplayName("TowerChatController 테스트 (LUT-633)")
class TowerChatControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private TowerChatService chatService;

    @MockitoBean private io.pinkspider.global.web.ClientIpResolver clientIpResolver;

    private static TowerChatMessage message() {
        return new TowerChatMessage(
                "m-1",
                5L,
                7L,
                "user-1",
                null,
                "루미",
                "안녕 **야",
                true,
                LocalDateTime.of(2026, 11, 4, 13, 0),
                null);
    }

    private static String[] messageFields(String prefix) {
        return new String[] {
            prefix + "id",
            prefix + "instance_id",
            prefix + "occupant_id",
            prefix + "user_id",
            prefix + "visitor_no",
            prefix + "nickname",
            prefix + "content",
            prefix + "masked",
            prefix + "sent_at"
        };
    }

    @Test
    @DisplayName("POST /api/v1/tower/instances/{instanceId}/chat : 전송 — 처리된 문장을 돌려준다")
    void send() throws Exception {
        when(chatService.send(eq(5L), eq("user-1"), isNull(), eq("안녕 바보야"), any()))
                .thenReturn(message());
        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                        "/api/v1/tower/instances/{instanceId}/chat", 5L)
                                .with(user("user-1"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"content\":\"안녕 바보야\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.content").value("안녕 **야"))
                .andExpect(jsonPath("$.value.masked").value(true))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "tower-chat-send",
                                ResourceSnippetParameters.builder()
                                        .tag("Tower")
                                        .description(
                                                "탑 채팅 전송 (LUT-633) — 처리된 메시지(금칙어 가림)를 돌려준다. 거절"
                                                        + " 1503xx. 비로그인 허용(visitor_key)"),
                                pathParameters(
                                        parameterWithName("instanceId").description("인스턴스 ID")),
                                requestFields(
                                        fieldWithPath("content")
                                                .description("본문 (로그인 200자·방문객 40자, 서버가 금칙어를 가린다)"),
                                        fieldWithPath("visitor_key")
                                                .type(JsonFieldType.STRING)
                                                .description("방문객 키 (비로그인)")
                                                .optional()),
                                responseFields(
                                        fieldWithPath("code").description("응답 코드"),
                                        fieldWithPath("message").description("응답 메시지"),
                                        fieldWithPath("value")
                                                .description("처리된 메시지 (브로드캐스트 CHAT 과 같은 모양)"),
                                        fieldWithPath("value.id").description("메시지 ID"),
                                        fieldWithPath("value.instance_id").description("인스턴스 ID"),
                                        fieldWithPath("value.occupant_id").description("보낸 점유자"),
                                        fieldWithPath("value.user_id")
                                                .description("보낸 유저 (방문객은 null)")
                                                .optional(),
                                        fieldWithPath("value.visitor_no")
                                                .description("방문객 번호 (방문객만)")
                                                .optional(),
                                        fieldWithPath("value.nickname")
                                                .description("닉네임 (로그인)")
                                                .optional(),
                                        fieldWithPath("value.content").description("가린 뒤 문장"),
                                        fieldWithPath("value.masked").description("금칙어를 가렸는가"),
                                        fieldWithPath("value.sent_at").description("보낸 시각"),
                                        fieldWithPath("value.visitor_tag")
                                                .description(
                                                        "방문객 세션 공개 식별자 (LUT-705 — 세션 기준 숨기기, 로그인"
                                                                + " 유저는 null)")
                                                .optional())));
    }

    @Test
    @DisplayName("거절: 방 밖 403 150301 · 너무 빠름 429 150302 · HIGH 금칙어 400 150305 · 빈 본문 400")
    void rejected() throws Exception {
        when(chatService.send(eq(5L), eq("user-1"), isNull(), eq("a"), any()))
                .thenThrow(TowerChatException.notInInstance());
        mockMvc.perform(
                        RestDocumentationRequestBuilders.post("/api/v1/tower/instances/5/chat")
                                .with(user("user-1"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"content\":\"a\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("150301"));
        when(chatService.send(eq(5L), eq("user-1"), isNull(), eq("b"), any()))
                .thenThrow(TowerChatException.rateLimited());
        mockMvc.perform(
                        RestDocumentationRequestBuilders.post("/api/v1/tower/instances/5/chat")
                                .with(user("user-1"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"content\":\"b\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("150302"));
        when(chatService.send(eq(5L), isNull(), eq("vk"), eq("c"), any()))
                .thenThrow(TowerChatException.profanityRejected());
        mockMvc.perform(
                        RestDocumentationRequestBuilders.post("/api/v1/tower/instances/5/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"content\":\"c\",\"visitor_key\":\"vk\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("150305"));
        mockMvc.perform(
                        RestDocumentationRequestBuilders.post("/api/v1/tower/instances/5/chat")
                                .with(user("user-1"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"content\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /instances/{instanceId}/reactions : 프리셋 이모지, 응원 창 안이면 cheer")
    void react() throws Exception {
        when(chatService.react(eq(5L), eq("user-1"), isNull(), eq("👏"), eq("user-9"), any()))
                .thenReturn(new TowerReactionResponse("👏", true, 3));
        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                        "/api/v1/tower/instances/{instanceId}/reactions", 5L)
                                .with(user("user-1"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"emoji\":\"👏\",\"target_user_id\":\"user-9\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.cheer").value(true))
                .andExpect(jsonPath("$.value.cheer_count").value(3))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "tower-chat-react",
                                ResourceSnippetParameters.builder()
                                        .tag("Tower")
                                        .description(
                                                "리액션 보내기 (LUT-633) — 프리셋 이모지, 완료 응원 창 안에서"
                                                    + " target_user_id 를 주면 응원으로 센다. 미션 중에도 허용"),
                                pathParameters(
                                        parameterWithName("instanceId").description("인스턴스 ID")),
                                requestFields(
                                        fieldWithPath("emoji")
                                                .description(
                                                        "프리셋 이모지 (GET"
                                                                + " /api/v1/tower/chat/reactions)"),
                                        fieldWithPath("target_user_id")
                                                .description("응원 대상 (완료 연출 10초 창 안이면 응원으로 집계)")
                                                .optional(),
                                        fieldWithPath("visitor_key")
                                                .type(JsonFieldType.STRING)
                                                .description("방문객 키")
                                                .optional()),
                                responseFields(
                                        fieldWithPath("code").description("응답 코드"),
                                        fieldWithPath("message").description("응답 메시지"),
                                        fieldWithPath("value.emoji").description("보낸 이모지"),
                                        fieldWithPath("value.cheer").description("응원으로 집계됐는가"),
                                        fieldWithPath("value.cheer_count")
                                                .description("대상의 이번 미션 응원 누계 (응원일 때만)")
                                                .optional())));
        when(chatService.react(eq(5L), eq("user-1"), isNull(), eq("🎉"), isNull(), any()))
                .thenThrow(TowerChatException.invalidReaction());
        mockMvc.perform(
                        RestDocumentationRequestBuilders.post("/api/v1/tower/instances/5/reactions")
                                .with(user("user-1"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"emoji\":\"🎉\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("150309"));
    }

    @Test
    @DisplayName(
            "GET /instances/{instanceId}/chat : 최근 50건 (방문객은 visitor_key) · GET /chat/reactions :"
                    + " 프리셋")
    void historyAndPresets() throws Exception {
        when(chatService.history(eq(5L), isNull(), eq("k-1")))
                .thenReturn(new TowerChatHistoryResponse(5L, List.of(message())));
        mockMvc.perform(
                        RestDocumentationRequestBuilders.get(
                                        "/api/v1/tower/instances/{instanceId}/chat", 5L)
                                .queryParam("visitor_key", "k-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.messages[0].id").value("m-1"))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "tower-chat-history",
                                ResourceSnippetParameters.builder()
                                        .tag("Tower")
                                        .description(
                                                "최근 채팅 50건 (LUT-633) — 로그인 요청자는 차단 유저를 뺀다. 방문객은"
                                                        + " visitor_key"),
                                pathParameters(
                                        parameterWithName("instanceId").description("인스턴스 ID")),
                                queryParameters(
                                        parameterWithName("visitor_key")
                                                .description("방문객 키 (비로그인)")
                                                .optional()),
                                responseFields(
                                        fieldWithPath("code").description("응답 코드"),
                                        fieldWithPath("message").description("응답 메시지"),
                                        fieldWithPath("value.instance_id").description("인스턴스 ID"),
                                        fieldWithPath("value.messages")
                                                .description("오래된 것부터, 최대 50건, 차단 유저 제외"),
                                        fieldWithPath("value.messages[].id").description("메시지 ID"),
                                        fieldWithPath("value.messages[].instance_id")
                                                .description("인스턴스 ID"),
                                        fieldWithPath("value.messages[].occupant_id")
                                                .description("보낸 점유자"),
                                        fieldWithPath("value.messages[].user_id")
                                                .description("보낸 유저")
                                                .optional(),
                                        fieldWithPath("value.messages[].visitor_no")
                                                .description("방문객 번호")
                                                .optional(),
                                        fieldWithPath("value.messages[].nickname")
                                                .description("닉네임")
                                                .optional(),
                                        fieldWithPath("value.messages[].content")
                                                .description("가린 뒤 문장"),
                                        fieldWithPath("value.messages[].masked")
                                                .description("금칙어를 가렸는가"),
                                        fieldWithPath("value.messages[].sent_at")
                                                .description("보낸 시각"),
                                        fieldWithPath("value.messages[].visitor_tag")
                                                .description(
                                                        "방문객 세션 공개 식별자 (LUT-705 — 세션 기준 숨기기, 로그인"
                                                                + " 유저는 null)")
                                                .optional())));
        when(chatService.history(eq(5L), isNull(), isNull()))
                .thenThrow(TowerChatException.notInInstance());
        mockMvc.perform(RestDocumentationRequestBuilders.get("/api/v1/tower/instances/5/chat"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("150301"));

        when(chatService.presets())
                .thenReturn(new TowerReactionPresetsResponse(List.of("👍", "❤️")));
        mockMvc.perform(RestDocumentationRequestBuilders.get("/api/v1/tower/chat/reactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.emojis[0]").value("👍"))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "tower-chat-reaction-presets",
                                ResourceSnippetParameters.builder()
                                        .tag("Tower")
                                        .description("리액션 프리셋 6종 (LUT-633)"),
                                responseFields(
                                        fieldWithPath("code").description("응답 코드"),
                                        fieldWithPath("message").description("응답 메시지"),
                                        fieldWithPath("value.emojis")
                                                .description("서버가 정한 리액션 이모지"))));
    }
}
