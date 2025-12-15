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
import io.pinkspider.global.component.LmObjectMapper;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildPostService;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildPostCommentCreateRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildPostCommentResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildPostCreateRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildPostListResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildPostResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildPostUpdateRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildPostType;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@WebMvcTest(controllers = GuildPostController.class,
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
class GuildPostControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    private final LmObjectMapper objectMapper = new LmObjectMapper();

    @MockitoBean
    private GuildPostService guildPostService;

    private static final String X_USER_NICKNAME = "X-User-Nickname";
    private static final String MOCK_USER_ID = "test-user-123";
    private static final String MOCK_NICKNAME = "테스트유저";

    @Test
    @DisplayName("POST /api/v1/guilds/{guildId}/posts : 게시글 작성")
    void createPostTest() throws Exception {
        // given
        GuildPostCreateRequest request = GuildPostCreateRequest.builder()
            .title("첫 번째 게시글")
            .content("게시글 내용입니다.")
            .postType(GuildPostType.NORMAL)
            .build();

        GuildPostResponse response = GuildPostResponse.builder()
            .id(1L)
            .guildId(1L)
            .authorId(MOCK_USER_ID)
            .authorNickname(MOCK_NICKNAME)
            .title("첫 번째 게시글")
            .content("게시글 내용입니다.")
            .postType(GuildPostType.NORMAL)
            .isPinned(false)
            .viewCount(0)
            .commentCount(0)
            .createdAt(LocalDateTime.now())
            .modifiedAt(LocalDateTime.now())
            .build();

        when(guildPostService.createPost(anyLong(), anyString(), anyString(), any(GuildPostCreateRequest.class)))
            .thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/guilds/{guildId}/posts", 1L)
                .with(user(MOCK_USER_ID))
                .header(X_USER_NICKNAME, MOCK_NICKNAME)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드게시판-01. 게시글 작성",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild Post")
                        .description("길드 게시글 작성 (공지글은 길드 마스터만 가능) (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID")
                        )
                        .requestFields(
                            fieldWithPath("title").type(JsonFieldType.STRING).description("게시글 제목"),
                            fieldWithPath("content").type(JsonFieldType.STRING).description("게시글 내용"),
                            fieldWithPath("post_type").type(JsonFieldType.STRING).description("게시글 유형 (NOTICE, NORMAL)"),
                            fieldWithPath("is_pinned").type(JsonFieldType.BOOLEAN).description("상단 고정 여부").optional()
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("게시글 정보"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("게시글 ID"),
                            fieldWithPath("value.guild_id").type(JsonFieldType.NUMBER).description("길드 ID"),
                            fieldWithPath("value.author_id").type(JsonFieldType.STRING).description("작성자 ID"),
                            fieldWithPath("value.author_nickname").type(JsonFieldType.STRING).description("작성자 닉네임").optional(),
                            fieldWithPath("value.title").type(JsonFieldType.STRING).description("제목"),
                            fieldWithPath("value.content").type(JsonFieldType.STRING).description("내용"),
                            fieldWithPath("value.post_type").type(JsonFieldType.STRING).description("게시글 유형"),
                            fieldWithPath("value.is_pinned").type(JsonFieldType.BOOLEAN).description("상단 고정 여부"),
                            fieldWithPath("value.view_count").type(JsonFieldType.NUMBER).description("조회수"),
                            fieldWithPath("value.comment_count").type(JsonFieldType.NUMBER).description("댓글 수"),
                            fieldWithPath("value.created_at").type(JsonFieldType.STRING).description("작성일시"),
                            fieldWithPath("value.modified_at").type(JsonFieldType.STRING).description("수정일시")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/guilds/{guildId}/posts : 게시글 목록 조회")
    void getPostsTest() throws Exception {
        // given
        List<GuildPostListResponse> posts = List.of(
            GuildPostListResponse.builder()
                .id(1L)
                .authorId(MOCK_USER_ID)
                .authorNickname(MOCK_NICKNAME)
                .title("첫 번째 게시글")
                .postType(GuildPostType.NORMAL)
                .isPinned(false)
                .viewCount(10)
                .commentCount(5)
                .createdAt(LocalDateTime.now())
                .build()
        );

        Page<GuildPostListResponse> page = new PageImpl<>(posts, PageRequest.of(0, 20), 1);

        when(guildPostService.getPosts(anyLong(), anyString(), any(Pageable.class)))
            .thenReturn(page);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/guilds/{guildId}/posts", 1L)
                .with(user(MOCK_USER_ID))
                .param("page", "0")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드게시판-02. 게시글 목록 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild Post")
                        .description("길드 게시글 목록 조회 (상단 고정 우선, 최신순) (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID")
                        )
                        .queryParameters(
                            parameterWithName("page").type(SimpleType.NUMBER).description("페이지 번호").optional(),
                            parameterWithName("size").type(SimpleType.NUMBER).description("페이지 크기").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/guilds/{guildId}/posts/{postId} : 게시글 상세 조회")
    void getPostTest() throws Exception {
        // given
        GuildPostResponse response = GuildPostResponse.builder()
            .id(1L)
            .guildId(1L)
            .authorId(MOCK_USER_ID)
            .authorNickname(MOCK_NICKNAME)
            .title("첫 번째 게시글")
            .content("게시글 내용입니다.")
            .postType(GuildPostType.NORMAL)
            .isPinned(false)
            .viewCount(11)
            .commentCount(5)
            .createdAt(LocalDateTime.now())
            .modifiedAt(LocalDateTime.now())
            .build();

        when(guildPostService.getPost(anyLong(), anyLong(), anyString()))
            .thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/guilds/{guildId}/posts/{postId}", 1L, 1L)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드게시판-03. 게시글 상세 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild Post")
                        .description("길드 게시글 상세 조회 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID"),
                            parameterWithName("postId").type(SimpleType.NUMBER).description("게시글 ID")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("PUT /api/v1/guilds/{guildId}/posts/{postId} : 게시글 수정")
    void updatePostTest() throws Exception {
        // given
        GuildPostUpdateRequest request = GuildPostUpdateRequest.builder()
            .title("수정된 제목")
            .content("수정된 내용입니다.")
            .build();

        GuildPostResponse response = GuildPostResponse.builder()
            .id(1L)
            .guildId(1L)
            .authorId(MOCK_USER_ID)
            .authorNickname(MOCK_NICKNAME)
            .title("수정된 제목")
            .content("수정된 내용입니다.")
            .postType(GuildPostType.NORMAL)
            .isPinned(false)
            .viewCount(10)
            .commentCount(5)
            .createdAt(LocalDateTime.now().minusDays(1))
            .modifiedAt(LocalDateTime.now())
            .build();

        when(guildPostService.updatePost(anyLong(), anyLong(), anyString(), any(GuildPostUpdateRequest.class)))
            .thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.put("/api/v1/guilds/{guildId}/posts/{postId}", 1L, 1L)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드게시판-04. 게시글 수정",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild Post")
                        .description("게시글 수정 (작성자만 가능) (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID"),
                            parameterWithName("postId").type(SimpleType.NUMBER).description("게시글 ID")
                        )
                        .requestFields(
                            fieldWithPath("title").type(JsonFieldType.STRING).description("수정할 제목").optional(),
                            fieldWithPath("content").type(JsonFieldType.STRING).description("수정할 내용").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/v1/guilds/{guildId}/posts/{postId} : 게시글 삭제")
    void deletePostTest() throws Exception {
        // given
        doNothing().when(guildPostService).deletePost(anyLong(), anyLong(), anyString());

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.delete("/api/v1/guilds/{guildId}/posts/{postId}", 1L, 1L)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드게시판-05. 게시글 삭제",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild Post")
                        .description("게시글 삭제 (작성자 또는 마스터 가능) (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID"),
                            parameterWithName("postId").type(SimpleType.NUMBER).description("게시글 ID")
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

    @Test
    @DisplayName("PATCH /api/v1/guilds/{guildId}/posts/{postId}/pin : 게시글 상단 고정 토글")
    void togglePinTest() throws Exception {
        // given
        GuildPostResponse response = GuildPostResponse.builder()
            .id(1L)
            .guildId(1L)
            .authorId(MOCK_USER_ID)
            .authorNickname(MOCK_NICKNAME)
            .title("공지글")
            .content("공지 내용입니다.")
            .postType(GuildPostType.NOTICE)
            .isPinned(true)
            .viewCount(50)
            .commentCount(10)
            .createdAt(LocalDateTime.now())
            .modifiedAt(LocalDateTime.now())
            .build();

        when(guildPostService.togglePin(anyLong(), anyLong(), anyString()))
            .thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.patch("/api/v1/guilds/{guildId}/posts/{postId}/pin", 1L, 1L)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드게시판-06. 게시글 상단 고정 토글",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild Post")
                        .description("게시글 상단 고정/해제 (마스터만 가능) (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID"),
                            parameterWithName("postId").type(SimpleType.NUMBER).description("게시글 ID")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/guilds/{guildId}/posts/{postId}/comments : 댓글 작성")
    void createCommentTest() throws Exception {
        // given
        GuildPostCommentCreateRequest request = GuildPostCommentCreateRequest.builder()
            .content("댓글 내용입니다.")
            .build();

        GuildPostCommentResponse response = GuildPostCommentResponse.builder()
            .id(1L)
            .postId(1L)
            .authorId(MOCK_USER_ID)
            .authorNickname(MOCK_NICKNAME)
            .content("댓글 내용입니다.")
            .parentId(null)
            .createdAt(LocalDateTime.now())
            .modifiedAt(LocalDateTime.now())
            .build();

        when(guildPostService.createComment(anyLong(), anyLong(), anyString(), anyString(),
            any(GuildPostCommentCreateRequest.class)))
            .thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/guilds/{guildId}/posts/{postId}/comments", 1L, 1L)
                .with(user(MOCK_USER_ID))
                .header(X_USER_NICKNAME, MOCK_NICKNAME)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드게시판-07. 댓글 작성",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild Post - Comment")
                        .description("게시글 댓글 작성 (길드원만 가능) (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID"),
                            parameterWithName("postId").type(SimpleType.NUMBER).description("게시글 ID")
                        )
                        .requestFields(
                            fieldWithPath("content").type(JsonFieldType.STRING).description("댓글 내용"),
                            fieldWithPath("parent_id").type(JsonFieldType.NUMBER).description("부모 댓글 ID (대댓글인 경우)").optional()
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("댓글 정보"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("댓글 ID"),
                            fieldWithPath("value.post_id").type(JsonFieldType.NUMBER).description("게시글 ID"),
                            fieldWithPath("value.author_id").type(JsonFieldType.STRING).description("작성자 ID"),
                            fieldWithPath("value.author_nickname").type(JsonFieldType.STRING).description("작성자 닉네임").optional(),
                            fieldWithPath("value.content").type(JsonFieldType.STRING).description("댓글 내용"),
                            fieldWithPath("value.parent_id").type(JsonFieldType.NUMBER).description("부모 댓글 ID").optional(),
                            fieldWithPath("value.replies").type(JsonFieldType.ARRAY).description("대댓글 목록").optional(),
                            fieldWithPath("value.is_deleted").type(JsonFieldType.BOOLEAN).description("삭제 여부").optional(),
                            fieldWithPath("value.created_at").type(JsonFieldType.STRING).description("작성일시"),
                            fieldWithPath("value.modified_at").type(JsonFieldType.STRING).description("수정일시")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/guilds/{guildId}/posts/{postId}/comments : 댓글 목록 조회")
    void getCommentsTest() throws Exception {
        // given
        List<GuildPostCommentResponse> responses = List.of(
            GuildPostCommentResponse.builder()
                .id(1L)
                .postId(1L)
                .authorId(MOCK_USER_ID)
                .authorNickname(MOCK_NICKNAME)
                .content("댓글 내용입니다.")
                .parentId(null)
                .createdAt(LocalDateTime.now())
                .modifiedAt(LocalDateTime.now())
                .build()
        );

        when(guildPostService.getComments(anyLong(), anyLong(), anyString()))
            .thenReturn(responses);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/guilds/{guildId}/posts/{postId}/comments", 1L, 1L)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드게시판-08. 댓글 목록 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild Post - Comment")
                        .description("게시글 댓글 목록 조회 (대댓글 포함) (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID"),
                            parameterWithName("postId").type(SimpleType.NUMBER).description("게시글 ID")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/v1/guilds/{guildId}/posts/{postId}/comments/{commentId} : 댓글 삭제")
    void deleteCommentTest() throws Exception {
        // given
        doNothing().when(guildPostService).deleteComment(anyLong(), anyLong(), anyLong(), anyString());

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.delete(
                    "/api/v1/guilds/{guildId}/posts/{postId}/comments/{commentId}", 1L, 1L, 1L)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드게시판-09. 댓글 삭제",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild Post - Comment")
                        .description("댓글 삭제 (작성자 또는 마스터 가능) (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID"),
                            parameterWithName("postId").type(SimpleType.NUMBER).description("게시글 ID"),
                            parameterWithName("commentId").type(SimpleType.NUMBER).description("댓글 ID")
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
}
