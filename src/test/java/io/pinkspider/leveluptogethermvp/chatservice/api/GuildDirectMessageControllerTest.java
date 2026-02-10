package io.pinkspider.leveluptogethermvp.chatservice.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.SimpleType;
import com.fasterxml.jackson.core.type.TypeReference;
import io.pinkspider.global.component.LmObjectMapper;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.chatservice.application.GuildDirectMessageService;
import io.pinkspider.leveluptogethermvp.chatservice.domain.dto.DirectConversationResponse;
import io.pinkspider.leveluptogethermvp.chatservice.domain.dto.DirectMessageRequest;
import io.pinkspider.leveluptogethermvp.chatservice.domain.dto.DirectMessageResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@WebMvcTest(controllers = GuildDirectMessageController.class,
    excludeAutoConfiguration = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class
    }
)
@Import(ControllerTestConfig.class)
@AutoConfigureRestDocs
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GuildDirectMessageControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    private final LmObjectMapper objectMapper = new LmObjectMapper();

    @MockitoBean
    private GuildDirectMessageService dmService;

    private static final String MOCK_USER_ID = "test-user-123";
    private static final String MOCK_RECIPIENT_ID = "recipient-456";

    @Test
    @DisplayName("GET /api/v1/guilds/{guildId}/dm/conversations : DM 대화 목록 조회")
    void getConversationsTest() throws Exception {
        // given
        Long guildId = 1L;

        DirectConversationResponse response = DirectConversationResponse.builder()
            .id(1L)
            .guildId(guildId)
            .otherUserId(MOCK_RECIPIENT_ID)
            .otherUserNickname("상대방닉네임")
            .otherUserProfileImage("https://example.com/profile.jpg")
            .lastMessage("마지막 메시지입니다")
            .lastMessageAt(LocalDateTime.now())
            .unreadCount(3)
            .createdAt(LocalDateTime.now().minusDays(1))
            .build();

        when(dmService.getConversations(anyLong(), anyString()))
            .thenReturn(List.of(response));

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/guilds/{guildId}/dm/conversations", guildId)
                .with(user(MOCK_USER_ID))
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드DM-01. DM 대화 목록 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild DM")
                        .description("길드 내 DM 대화 목록 조회 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.ARRAY).description("대화 목록"),
                            fieldWithPath("value[].id").type(JsonFieldType.NUMBER).description("대화 ID"),
                            fieldWithPath("value[].guild_id").type(JsonFieldType.NUMBER).description("길드 ID"),
                            fieldWithPath("value[].other_user_id").type(JsonFieldType.STRING).description("상대방 사용자 ID"),
                            fieldWithPath("value[].other_user_nickname").type(JsonFieldType.STRING).description("상대방 닉네임"),
                            fieldWithPath("value[].other_user_profile_image").type(JsonFieldType.STRING).description("상대방 프로필 이미지").optional(),
                            fieldWithPath("value[].last_message").type(JsonFieldType.STRING).description("마지막 메시지").optional(),
                            fieldWithPath("value[].last_message_at").type(JsonFieldType.STRING).description("마지막 메시지 시간").optional(),
                            fieldWithPath("value[].unread_count").type(JsonFieldType.NUMBER).description("안읽은 메시지 수"),
                            fieldWithPath("value[].created_at").type(JsonFieldType.STRING).description("대화 생성 시간")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/guilds/{guildId}/dm/send/{recipientId} : DM 전송")
    void sendMessageTest() throws Exception {
        // given
        Long guildId = 1L;

        DirectMessageResponse response = DirectMessageResponse.builder()
            .id(1L)
            .conversationId(1L)
            .senderId(MOCK_USER_ID)
            .senderNickname("발신자닉네임")
            .content("안녕하세요!")
            .imageUrl(null)
            .isRead(false)
            .readAt(null)
            .createdAt(LocalDateTime.now())
            .build();

        when(dmService.sendMessage(anyLong(), anyString(), anyString(), any(DirectMessageRequest.class)))
            .thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/guilds/{guildId}/dm/send/{recipientId}", guildId, MOCK_RECIPIENT_ID)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"안녕하세요!\"}")
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드DM-02. DM 전송",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild DM")
                        .description("길드원에게 DM 전송 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID"),
                            parameterWithName("recipientId").type(SimpleType.STRING).description("수신자 사용자 ID")
                        )
                        .requestFields(
                            fieldWithPath("content").type(JsonFieldType.STRING).description("메시지 내용"),
                            fieldWithPath("image_url").type(JsonFieldType.STRING).description("이미지 URL").optional()
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("DM 메시지 정보"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("메시지 ID"),
                            fieldWithPath("value.conversation_id").type(JsonFieldType.NUMBER).description("대화 ID"),
                            fieldWithPath("value.sender_id").type(JsonFieldType.STRING).description("발신자 ID"),
                            fieldWithPath("value.sender_nickname").type(JsonFieldType.STRING).description("발신자 닉네임"),
                            fieldWithPath("value.content").type(JsonFieldType.STRING).description("메시지 내용"),
                            fieldWithPath("value.image_url").type(JsonFieldType.STRING).description("이미지 URL").optional(),
                            fieldWithPath("value.is_read").type(JsonFieldType.BOOLEAN).description("읽음 여부"),
                            fieldWithPath("value.read_at").type(JsonFieldType.STRING).description("읽은 시간").optional(),
                            fieldWithPath("value.created_at").type(JsonFieldType.STRING).description("생성 시간")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/guilds/{guildId}/dm/conversations/{conversationId}/messages : 대화별 메시지 조회")
    void getMessagesByConversationIdTest() throws Exception {
        // given
        Long guildId = 1L;
        Long conversationId = 1L;

        DirectMessageResponse response = DirectMessageResponse.builder()
            .id(1L)
            .conversationId(conversationId)
            .senderId(MOCK_USER_ID)
            .senderNickname("발신자닉네임")
            .content("테스트 메시지입니다")
            .isRead(true)
            .readAt(LocalDateTime.now())
            .createdAt(LocalDateTime.now().minusMinutes(5))
            .build();

        Page<DirectMessageResponse> page = new PageImpl<>(
            List.of(response),
            org.springframework.data.domain.PageRequest.of(0, 50),
            1
        );

        when(dmService.getMessagesByConversationId(anyLong(), anyString(), anyLong(), any(Pageable.class)))
            .thenReturn(page);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/guilds/{guildId}/dm/conversations/{conversationId}/messages",
                    guildId, conversationId)
                .with(user(MOCK_USER_ID))
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드DM-03. 대화별 메시지 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild DM")
                        .description("특정 대화의 메시지 목록 조회 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID"),
                            parameterWithName("conversationId").type(SimpleType.NUMBER).description("대화 ID")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/guilds/{guildId}/dm/conversations/{conversationId}/read : 메시지 읽음 처리")
    void markAsReadTest() throws Exception {
        // given
        Long guildId = 1L;
        Long conversationId = 1L;

        doNothing().when(dmService).markAsRead(anyLong(), anyString(), anyLong());

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/guilds/{guildId}/dm/conversations/{conversationId}/read",
                    guildId, conversationId)
                .with(user(MOCK_USER_ID))
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드DM-04. 메시지 읽음 처리",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild DM")
                        .description("대화의 모든 메시지를 읽음 처리 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID"),
                            parameterWithName("conversationId").type(SimpleType.NUMBER).description("대화 ID")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/guilds/{guildId}/dm/unread-count : 안읽은 DM 수 조회")
    void getTotalUnreadCountTest() throws Exception {
        // given
        Long guildId = 1L;

        when(dmService.getTotalUnreadCount(anyLong(), anyString())).thenReturn(5);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/guilds/{guildId}/dm/unread-count", guildId)
                .with(user(MOCK_USER_ID))
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드DM-05. 안읽은 DM 수 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild DM")
                        .description("전체 안읽은 DM 수 조회 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.NUMBER).description("안읽은 DM 수")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/v1/guilds/{guildId}/dm/messages/{messageId} : DM 삭제")
    void deleteMessageTest() throws Exception {
        // given
        Long guildId = 1L;
        Long messageId = 1L;

        doNothing().when(dmService).deleteMessage(anyLong(), anyString(), anyLong());

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.delete("/api/v1/guilds/{guildId}/dm/messages/{messageId}",
                    guildId, messageId)
                .with(user(MOCK_USER_ID))
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드DM-06. DM 삭제",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild DM")
                        .description("DM 메시지 삭제 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID"),
                            parameterWithName("messageId").type(SimpleType.NUMBER).description("메시지 ID")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/guilds/{guildId}/dm/conversations/{otherUserId} : 대화 시작/조회")
    void getOrCreateConversationTest() throws Exception {
        // given
        Long guildId = 1L;

        DirectConversationResponse response = DirectConversationResponse.builder()
            .id(1L)
            .guildId(guildId)
            .otherUserId(MOCK_RECIPIENT_ID)
            .otherUserNickname("상대방닉네임")
            .unreadCount(0)
            .createdAt(LocalDateTime.now())
            .build();

        when(dmService.getOrCreateConversation(anyLong(), anyString(), anyString()))
            .thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/guilds/{guildId}/dm/conversations/{otherUserId}",
                    guildId, MOCK_RECIPIENT_ID)
                .with(user(MOCK_USER_ID))
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드DM-07. 대화 시작/조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild DM")
                        .description("특정 사용자와의 대화 시작 또는 기존 대화 조회 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID"),
                            parameterWithName("otherUserId").type(SimpleType.STRING).description("상대방 사용자 ID")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }
}
