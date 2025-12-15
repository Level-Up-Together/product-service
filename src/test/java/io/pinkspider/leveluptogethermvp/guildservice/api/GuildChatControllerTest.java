package io.pinkspider.leveluptogethermvp.guildservice.api;

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
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.ChatMessageRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.ChatMessageResponse;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildChatService;
import io.pinkspider.util.MockUtil;
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

@WebMvcTest(controllers = GuildChatController.class,
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
class GuildChatControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    private final LmObjectMapper objectMapper = new LmObjectMapper();

    @MockitoBean
    private GuildChatService guildChatService;

    private static final String X_USER_NICKNAME = "X-User-Nickname";
    private static final String MOCK_USER_ID = "test-user-123";
    private static final String MOCK_NICKNAME = "테스트유저";

    @Test
    @DisplayName("POST /api/v1/guilds/{guildId}/chat : 채팅 메시지 전송")
    void sendMessageTest() throws Exception {
        // given
        Long guildId = 1L;

        ChatMessageResponse response = MockUtil.readJsonFileToClass(
            "fixture/guildchat/chatMessageResponse.json", ChatMessageResponse.class);

        when(guildChatService.sendMessage(anyLong(), anyString(), anyString(), any(ChatMessageRequest.class)))
            .thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/guilds/{guildId}/chat", guildId)
                .with(user(MOCK_USER_ID))
                .header(X_USER_NICKNAME, MOCK_NICKNAME)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"안녕하세요!\",\"message_type\":\"TEXT\"}")
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드채팅-01. 채팅 메시지 전송",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild Chat")
                        .description("길드 채팅 메시지 전송 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID")
                        )
                        .requestFields(
                            fieldWithPath("content").type(JsonFieldType.STRING).description("메시지 내용"),
                            fieldWithPath("message_type").type(JsonFieldType.STRING).description("메시지 타입 (TEXT, IMAGE)").optional()
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("채팅 메시지 정보"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("메시지 ID"),
                            fieldWithPath("value.guild_id").type(JsonFieldType.NUMBER).description("길드 ID"),
                            fieldWithPath("value.sender_id").type(JsonFieldType.STRING).description("발신자 ID").optional(),
                            fieldWithPath("value.sender_nickname").type(JsonFieldType.STRING).description("발신자 닉네임").optional(),
                            fieldWithPath("value.message_type").type(JsonFieldType.STRING).description("메시지 타입"),
                            fieldWithPath("value.content").type(JsonFieldType.STRING).description("메시지 내용"),
                            fieldWithPath("value.image_url").type(JsonFieldType.STRING).description("이미지 URL").optional(),
                            fieldWithPath("value.reference_type").type(JsonFieldType.STRING).description("참조 타입").optional(),
                            fieldWithPath("value.reference_id").type(JsonFieldType.NUMBER).description("참조 ID").optional(),
                            fieldWithPath("value.is_system_message").type(JsonFieldType.BOOLEAN).description("시스템 메시지 여부"),
                            fieldWithPath("value.created_at").type(JsonFieldType.STRING).description("생성 일시")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/guilds/{guildId}/chat : 채팅 메시지 조회")
    void getMessagesTest() throws Exception {
        // given
        Long guildId = 1L;
        List<ChatMessageResponse> responses = MockUtil.readJsonFileToClassList(
            "fixture/guildchat/chatMessageResponseList.json",
            new TypeReference<List<ChatMessageResponse>>() {});

        Page<ChatMessageResponse> page = new PageImpl<>(responses, org.springframework.data.domain.PageRequest.of(0, 50), responses.size());

        when(guildChatService.getMessages(anyLong(), anyString(), any(Pageable.class)))
            .thenReturn(page);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/guilds/{guildId}/chat", guildId)
                .with(user(MOCK_USER_ID))
                .param("page", "0")
                .param("size", "50")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드채팅-02. 채팅 메시지 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild Chat")
                        .description("길드 채팅 메시지 조회 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID")
                        )
                        .queryParameters(
                            parameterWithName("page").type(SimpleType.NUMBER).description("페이지 번호 (기본값: 0)").optional(),
                            parameterWithName("size").type(SimpleType.NUMBER).description("페이지 크기 (기본값: 50)").optional()
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("페이지 정보"),
                            fieldWithPath("value.content[]").type(JsonFieldType.ARRAY).description("채팅 메시지 목록"),
                            fieldWithPath("value.content[].id").type(JsonFieldType.NUMBER).description("메시지 ID"),
                            fieldWithPath("value.content[].guild_id").type(JsonFieldType.NUMBER).description("길드 ID"),
                            fieldWithPath("value.content[].sender_id").type(JsonFieldType.STRING).description("발신자 ID").optional(),
                            fieldWithPath("value.content[].sender_nickname").type(JsonFieldType.STRING).description("발신자 닉네임").optional(),
                            fieldWithPath("value.content[].message_type").type(JsonFieldType.STRING).description("메시지 타입"),
                            fieldWithPath("value.content[].content").type(JsonFieldType.STRING).description("메시지 내용"),
                            fieldWithPath("value.content[].image_url").type(JsonFieldType.STRING).description("이미지 URL").optional(),
                            fieldWithPath("value.content[].reference_type").type(JsonFieldType.STRING).description("참조 타입").optional(),
                            fieldWithPath("value.content[].reference_id").type(JsonFieldType.NUMBER).description("참조 ID").optional(),
                            fieldWithPath("value.content[].is_system_message").type(JsonFieldType.BOOLEAN).description("시스템 메시지 여부"),
                            fieldWithPath("value.content[].created_at").type(JsonFieldType.STRING).description("생성 일시"),
                            fieldWithPath("value.pageable").type(JsonFieldType.OBJECT).description("페이지 정보").optional(),
                            fieldWithPath("value.pageable.sort").type(JsonFieldType.OBJECT).description("정렬 정보").optional(),
                            fieldWithPath("value.pageable.sort.empty").type(JsonFieldType.BOOLEAN).description("정렬 비어있음 여부").optional(),
                            fieldWithPath("value.pageable.sort.sorted").type(JsonFieldType.BOOLEAN).description("정렬됨 여부").optional(),
                            fieldWithPath("value.pageable.sort.unsorted").type(JsonFieldType.BOOLEAN).description("정렬되지 않음 여부").optional(),
                            fieldWithPath("value.pageable.offset").type(JsonFieldType.NUMBER).description("오프셋").optional(),
                            fieldWithPath("value.pageable.page_number").type(JsonFieldType.NUMBER).description("페이지 번호").optional(),
                            fieldWithPath("value.pageable.page_size").type(JsonFieldType.NUMBER).description("페이지 크기").optional(),
                            fieldWithPath("value.pageable.paged").type(JsonFieldType.BOOLEAN).description("페이징됨 여부").optional(),
                            fieldWithPath("value.pageable.unpaged").type(JsonFieldType.BOOLEAN).description("페이징되지 않음 여부").optional(),
                            fieldWithPath("value.last").type(JsonFieldType.BOOLEAN).description("마지막 페이지 여부").optional(),
                            fieldWithPath("value.total_elements").type(JsonFieldType.NUMBER).description("전체 요소 수").optional(),
                            fieldWithPath("value.total_pages").type(JsonFieldType.NUMBER).description("전체 페이지 수").optional(),
                            fieldWithPath("value.first").type(JsonFieldType.BOOLEAN).description("첫 페이지 여부").optional(),
                            fieldWithPath("value.size").type(JsonFieldType.NUMBER).description("페이지 크기").optional(),
                            fieldWithPath("value.number").type(JsonFieldType.NUMBER).description("현재 페이지 번호").optional(),
                            fieldWithPath("value.sort").type(JsonFieldType.OBJECT).description("정렬 정보").optional(),
                            fieldWithPath("value.sort.empty").type(JsonFieldType.BOOLEAN).description("정렬 비어있음 여부").optional(),
                            fieldWithPath("value.sort.sorted").type(JsonFieldType.BOOLEAN).description("정렬됨 여부").optional(),
                            fieldWithPath("value.sort.unsorted").type(JsonFieldType.BOOLEAN).description("정렬되지 않음 여부").optional(),
                            fieldWithPath("value.number_of_elements").type(JsonFieldType.NUMBER).description("현재 페이지 요소 수").optional(),
                            fieldWithPath("value.empty").type(JsonFieldType.BOOLEAN).description("비어있음 여부").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/guilds/{guildId}/chat/new : 새 메시지 조회 (폴링)")
    void getNewMessagesTest() throws Exception {
        // given
        Long guildId = 1L;
        List<ChatMessageResponse> responses = MockUtil.readJsonFileToClassList(
            "fixture/guildchat/chatMessageResponseList.json",
            new TypeReference<List<ChatMessageResponse>>() {});

        when(guildChatService.getNewMessages(anyLong(), anyString(), any(LocalDateTime.class)))
            .thenReturn(responses);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/guilds/{guildId}/chat/new", guildId)
                .with(user(MOCK_USER_ID))
                .param("since", "2025-01-15T10:00:00")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드채팅-03. 새 메시지 조회 (폴링)",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild Chat")
                        .description("특정 시간 이후의 새 메시지 조회 (폴링용) (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID")
                        )
                        .queryParameters(
                            parameterWithName("since").type(SimpleType.STRING).description("기준 시간 (ISO 8601 형식)")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value[]").type(JsonFieldType.ARRAY).description("새 채팅 메시지 목록"),
                            fieldWithPath("value[].id").type(JsonFieldType.NUMBER).description("메시지 ID"),
                            fieldWithPath("value[].guild_id").type(JsonFieldType.NUMBER).description("길드 ID"),
                            fieldWithPath("value[].sender_id").type(JsonFieldType.STRING).description("발신자 ID").optional(),
                            fieldWithPath("value[].sender_nickname").type(JsonFieldType.STRING).description("발신자 닉네임").optional(),
                            fieldWithPath("value[].message_type").type(JsonFieldType.STRING).description("메시지 타입"),
                            fieldWithPath("value[].content").type(JsonFieldType.STRING).description("메시지 내용"),
                            fieldWithPath("value[].image_url").type(JsonFieldType.STRING).description("이미지 URL").optional(),
                            fieldWithPath("value[].reference_type").type(JsonFieldType.STRING).description("참조 타입").optional(),
                            fieldWithPath("value[].reference_id").type(JsonFieldType.NUMBER).description("참조 ID").optional(),
                            fieldWithPath("value[].is_system_message").type(JsonFieldType.BOOLEAN).description("시스템 메시지 여부"),
                            fieldWithPath("value[].created_at").type(JsonFieldType.STRING).description("생성 일시")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/guilds/{guildId}/chat/after/{lastMessageId} : 특정 메시지 이후 조회")
    void getMessagesAfterIdTest() throws Exception {
        // given
        Long guildId = 1L;
        Long lastMessageId = 10L;
        List<ChatMessageResponse> responses = MockUtil.readJsonFileToClassList(
            "fixture/guildchat/chatMessageResponseList.json",
            new TypeReference<List<ChatMessageResponse>>() {});

        when(guildChatService.getMessagesAfterId(anyLong(), anyString(), anyLong()))
            .thenReturn(responses);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/guilds/{guildId}/chat/after/{lastMessageId}", guildId, lastMessageId)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드채팅-04. 특정 메시지 이후 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild Chat")
                        .description("특정 메시지 ID 이후의 메시지 조회 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID"),
                            parameterWithName("lastMessageId").type(SimpleType.NUMBER).description("기준 메시지 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value[]").type(JsonFieldType.ARRAY).description("채팅 메시지 목록"),
                            fieldWithPath("value[].id").type(JsonFieldType.NUMBER).description("메시지 ID"),
                            fieldWithPath("value[].guild_id").type(JsonFieldType.NUMBER).description("길드 ID"),
                            fieldWithPath("value[].sender_id").type(JsonFieldType.STRING).description("발신자 ID").optional(),
                            fieldWithPath("value[].sender_nickname").type(JsonFieldType.STRING).description("발신자 닉네임").optional(),
                            fieldWithPath("value[].message_type").type(JsonFieldType.STRING).description("메시지 타입"),
                            fieldWithPath("value[].content").type(JsonFieldType.STRING).description("메시지 내용"),
                            fieldWithPath("value[].image_url").type(JsonFieldType.STRING).description("이미지 URL").optional(),
                            fieldWithPath("value[].reference_type").type(JsonFieldType.STRING).description("참조 타입").optional(),
                            fieldWithPath("value[].reference_id").type(JsonFieldType.NUMBER).description("참조 ID").optional(),
                            fieldWithPath("value[].is_system_message").type(JsonFieldType.BOOLEAN).description("시스템 메시지 여부"),
                            fieldWithPath("value[].created_at").type(JsonFieldType.STRING).description("생성 일시")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/guilds/{guildId}/chat/before/{beforeId} : 특정 메시지 이전 조회")
    void getMessagesBeforeIdTest() throws Exception {
        // given
        Long guildId = 1L;
        Long beforeId = 10L;
        List<ChatMessageResponse> responses = MockUtil.readJsonFileToClassList(
            "fixture/guildchat/chatMessageResponseList.json",
            new TypeReference<List<ChatMessageResponse>>() {});

        if (responses == null) {
            responses = createMockChatMessages();
        }

        Page<ChatMessageResponse> page = new PageImpl<>(responses, org.springframework.data.domain.PageRequest.of(0, 50), responses.size());

        when(guildChatService.getMessagesBeforeId(anyLong(), anyString(), anyLong(), any(Pageable.class)))
            .thenReturn(page);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/guilds/{guildId}/chat/before/{beforeId}", guildId, beforeId)
                .with(user(MOCK_USER_ID))
                .param("page", "0")
                .param("size", "50")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드채팅-05. 특정 메시지 이전 조회 (무한스크롤)",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild Chat")
                        .description("특정 메시지 ID 이전의 메시지 조회 (무한스크롤 용) (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID"),
                            parameterWithName("beforeId").type(SimpleType.NUMBER).description("기준 메시지 ID")
                        )
                        .queryParameters(
                            parameterWithName("page").type(SimpleType.NUMBER).description("페이지 번호 (기본값: 0)").optional(),
                            parameterWithName("size").type(SimpleType.NUMBER).description("페이지 크기 (기본값: 50)").optional()
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("페이지 정보"),
                            fieldWithPath("value.content[]").type(JsonFieldType.ARRAY).description("채팅 메시지 목록"),
                            fieldWithPath("value.content[].id").type(JsonFieldType.NUMBER).description("메시지 ID"),
                            fieldWithPath("value.content[].guild_id").type(JsonFieldType.NUMBER).description("길드 ID"),
                            fieldWithPath("value.content[].sender_id").type(JsonFieldType.STRING).description("발신자 ID").optional(),
                            fieldWithPath("value.content[].sender_nickname").type(JsonFieldType.STRING).description("발신자 닉네임").optional(),
                            fieldWithPath("value.content[].message_type").type(JsonFieldType.STRING).description("메시지 타입"),
                            fieldWithPath("value.content[].content").type(JsonFieldType.STRING).description("메시지 내용"),
                            fieldWithPath("value.content[].image_url").type(JsonFieldType.STRING).description("이미지 URL").optional(),
                            fieldWithPath("value.content[].reference_type").type(JsonFieldType.STRING).description("참조 타입").optional(),
                            fieldWithPath("value.content[].reference_id").type(JsonFieldType.NUMBER).description("참조 ID").optional(),
                            fieldWithPath("value.content[].is_system_message").type(JsonFieldType.BOOLEAN).description("시스템 메시지 여부"),
                            fieldWithPath("value.content[].created_at").type(JsonFieldType.STRING).description("생성 일시"),
                            fieldWithPath("value.pageable").type(JsonFieldType.OBJECT).description("페이지 정보").optional(),
                            fieldWithPath("value.pageable.sort").type(JsonFieldType.OBJECT).description("정렬 정보").optional(),
                            fieldWithPath("value.pageable.sort.empty").type(JsonFieldType.BOOLEAN).description("정렬 비어있음 여부").optional(),
                            fieldWithPath("value.pageable.sort.sorted").type(JsonFieldType.BOOLEAN).description("정렬됨 여부").optional(),
                            fieldWithPath("value.pageable.sort.unsorted").type(JsonFieldType.BOOLEAN).description("정렬되지 않음 여부").optional(),
                            fieldWithPath("value.pageable.offset").type(JsonFieldType.NUMBER).description("오프셋").optional(),
                            fieldWithPath("value.pageable.page_number").type(JsonFieldType.NUMBER).description("페이지 번호").optional(),
                            fieldWithPath("value.pageable.page_size").type(JsonFieldType.NUMBER).description("페이지 크기").optional(),
                            fieldWithPath("value.pageable.paged").type(JsonFieldType.BOOLEAN).description("페이징됨 여부").optional(),
                            fieldWithPath("value.pageable.unpaged").type(JsonFieldType.BOOLEAN).description("페이징되지 않음 여부").optional(),
                            fieldWithPath("value.last").type(JsonFieldType.BOOLEAN).description("마지막 페이지 여부").optional(),
                            fieldWithPath("value.total_elements").type(JsonFieldType.NUMBER).description("전체 요소 수").optional(),
                            fieldWithPath("value.total_pages").type(JsonFieldType.NUMBER).description("전체 페이지 수").optional(),
                            fieldWithPath("value.first").type(JsonFieldType.BOOLEAN).description("첫 페이지 여부").optional(),
                            fieldWithPath("value.size").type(JsonFieldType.NUMBER).description("페이지 크기").optional(),
                            fieldWithPath("value.number").type(JsonFieldType.NUMBER).description("현재 페이지 번호").optional(),
                            fieldWithPath("value.sort").type(JsonFieldType.OBJECT).description("정렬 정보").optional(),
                            fieldWithPath("value.sort.empty").type(JsonFieldType.BOOLEAN).description("정렬 비어있음 여부").optional(),
                            fieldWithPath("value.sort.sorted").type(JsonFieldType.BOOLEAN).description("정렬됨 여부").optional(),
                            fieldWithPath("value.sort.unsorted").type(JsonFieldType.BOOLEAN).description("정렬되지 않음 여부").optional(),
                            fieldWithPath("value.number_of_elements").type(JsonFieldType.NUMBER).description("현재 페이지 요소 수").optional(),
                            fieldWithPath("value.empty").type(JsonFieldType.BOOLEAN).description("비어있음 여부").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/guilds/{guildId}/chat/search : 채팅 메시지 검색")
    void searchMessagesTest() throws Exception {
        // given
        Long guildId = 1L;
        List<ChatMessageResponse> responses = MockUtil.readJsonFileToClassList(
            "fixture/guildchat/chatMessageResponseList.json",
            new TypeReference<List<ChatMessageResponse>>() {});

        if (responses == null) {
            responses = createMockChatMessages();
        }

        Page<ChatMessageResponse> page = new PageImpl<>(responses, org.springframework.data.domain.PageRequest.of(0, 20), responses.size());

        when(guildChatService.searchMessages(anyLong(), anyString(), anyString(), any(Pageable.class)))
            .thenReturn(page);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/guilds/{guildId}/chat/search", guildId)
                .with(user(MOCK_USER_ID))
                .param("keyword", "안녕")
                .param("page", "0")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드채팅-06. 채팅 메시지 검색",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild Chat")
                        .description("키워드로 채팅 메시지 검색 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID")
                        )
                        .queryParameters(
                            parameterWithName("keyword").type(SimpleType.STRING).description("검색 키워드"),
                            parameterWithName("page").type(SimpleType.NUMBER).description("페이지 번호 (기본값: 0)").optional(),
                            parameterWithName("size").type(SimpleType.NUMBER).description("페이지 크기 (기본값: 20)").optional()
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("페이지 정보"),
                            fieldWithPath("value.content[]").type(JsonFieldType.ARRAY).description("검색된 채팅 메시지 목록"),
                            fieldWithPath("value.content[].id").type(JsonFieldType.NUMBER).description("메시지 ID"),
                            fieldWithPath("value.content[].guild_id").type(JsonFieldType.NUMBER).description("길드 ID"),
                            fieldWithPath("value.content[].sender_id").type(JsonFieldType.STRING).description("발신자 ID").optional(),
                            fieldWithPath("value.content[].sender_nickname").type(JsonFieldType.STRING).description("발신자 닉네임").optional(),
                            fieldWithPath("value.content[].message_type").type(JsonFieldType.STRING).description("메시지 타입"),
                            fieldWithPath("value.content[].content").type(JsonFieldType.STRING).description("메시지 내용"),
                            fieldWithPath("value.content[].image_url").type(JsonFieldType.STRING).description("이미지 URL").optional(),
                            fieldWithPath("value.content[].reference_type").type(JsonFieldType.STRING).description("참조 타입").optional(),
                            fieldWithPath("value.content[].reference_id").type(JsonFieldType.NUMBER).description("참조 ID").optional(),
                            fieldWithPath("value.content[].is_system_message").type(JsonFieldType.BOOLEAN).description("시스템 메시지 여부"),
                            fieldWithPath("value.content[].created_at").type(JsonFieldType.STRING).description("생성 일시"),
                            fieldWithPath("value.pageable").type(JsonFieldType.OBJECT).description("페이지 정보").optional(),
                            fieldWithPath("value.pageable.sort").type(JsonFieldType.OBJECT).description("정렬 정보").optional(),
                            fieldWithPath("value.pageable.sort.empty").type(JsonFieldType.BOOLEAN).description("정렬 비어있음 여부").optional(),
                            fieldWithPath("value.pageable.sort.sorted").type(JsonFieldType.BOOLEAN).description("정렬됨 여부").optional(),
                            fieldWithPath("value.pageable.sort.unsorted").type(JsonFieldType.BOOLEAN).description("정렬되지 않음 여부").optional(),
                            fieldWithPath("value.pageable.offset").type(JsonFieldType.NUMBER).description("오프셋").optional(),
                            fieldWithPath("value.pageable.page_number").type(JsonFieldType.NUMBER).description("페이지 번호").optional(),
                            fieldWithPath("value.pageable.page_size").type(JsonFieldType.NUMBER).description("페이지 크기").optional(),
                            fieldWithPath("value.pageable.paged").type(JsonFieldType.BOOLEAN).description("페이징됨 여부").optional(),
                            fieldWithPath("value.pageable.unpaged").type(JsonFieldType.BOOLEAN).description("페이징되지 않음 여부").optional(),
                            fieldWithPath("value.last").type(JsonFieldType.BOOLEAN).description("마지막 페이지 여부").optional(),
                            fieldWithPath("value.total_elements").type(JsonFieldType.NUMBER).description("전체 요소 수").optional(),
                            fieldWithPath("value.total_pages").type(JsonFieldType.NUMBER).description("전체 페이지 수").optional(),
                            fieldWithPath("value.first").type(JsonFieldType.BOOLEAN).description("첫 페이지 여부").optional(),
                            fieldWithPath("value.size").type(JsonFieldType.NUMBER).description("페이지 크기").optional(),
                            fieldWithPath("value.number").type(JsonFieldType.NUMBER).description("현재 페이지 번호").optional(),
                            fieldWithPath("value.sort").type(JsonFieldType.OBJECT).description("정렬 정보").optional(),
                            fieldWithPath("value.sort.empty").type(JsonFieldType.BOOLEAN).description("정렬 비어있음 여부").optional(),
                            fieldWithPath("value.sort.sorted").type(JsonFieldType.BOOLEAN).description("정렬됨 여부").optional(),
                            fieldWithPath("value.sort.unsorted").type(JsonFieldType.BOOLEAN).description("정렬되지 않음 여부").optional(),
                            fieldWithPath("value.number_of_elements").type(JsonFieldType.NUMBER).description("현재 페이지 요소 수").optional(),
                            fieldWithPath("value.empty").type(JsonFieldType.BOOLEAN).description("비어있음 여부").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/v1/guilds/{guildId}/chat/{messageId} : 채팅 메시지 삭제")
    void deleteMessageTest() throws Exception {
        // given
        Long guildId = 1L;
        Long messageId = 1L;

        doNothing().when(guildChatService).deleteMessage(anyLong(), anyLong(), anyString());

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.delete("/api/v1/guilds/{guildId}/chat/{messageId}", guildId, messageId)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드채팅-07. 채팅 메시지 삭제",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild Chat")
                        .description("자신이 보낸 채팅 메시지 삭제 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID"),
                            parameterWithName("messageId").type(SimpleType.NUMBER).description("메시지 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    private List<ChatMessageResponse> createMockChatMessages() {
        return List.of(
            ChatMessageResponse.builder()
                .id(1L)
                .guildId(1L)
                .senderId("user-123")
                .senderNickname("사용자1")
                .messageType(io.pinkspider.leveluptogethermvp.guildservice.domain.enums.ChatMessageType.TEXT)
                .content("안녕하세요!")
                .isSystemMessage(false)
                .createdAt(java.time.LocalDateTime.of(2025, 1, 15, 10, 30))
                .build(),
            ChatMessageResponse.builder()
                .id(2L)
                .guildId(1L)
                .senderId("user-456")
                .senderNickname("사용자2")
                .messageType(io.pinkspider.leveluptogethermvp.guildservice.domain.enums.ChatMessageType.TEXT)
                .content("반갑습니다!")
                .isSystemMessage(false)
                .createdAt(java.time.LocalDateTime.of(2025, 1, 15, 10, 31))
                .build()
        );
    }
}
