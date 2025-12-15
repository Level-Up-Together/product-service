package io.pinkspider.leveluptogethermvp.userservice.feed.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import io.pinkspider.leveluptogethermvp.userservice.feed.api.dto.ActivityFeedResponse;
import io.pinkspider.leveluptogethermvp.userservice.feed.api.dto.CreateFeedRequest;
import io.pinkspider.leveluptogethermvp.userservice.feed.api.dto.FeedCommentRequest;
import io.pinkspider.leveluptogethermvp.userservice.feed.api.dto.FeedCommentResponse;
import io.pinkspider.leveluptogethermvp.userservice.feed.application.ActivityFeedService;
import io.pinkspider.util.MockUtil;
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
import org.springframework.http.MediaType;
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@WebMvcTest(controllers = ActivityFeedController.class,
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
class ActivityFeedControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    private final LmObjectMapper objectMapper = new LmObjectMapper();

    @MockitoBean
    private ActivityFeedService activityFeedService;

    private static final String MOCK_USER_ID = "test-user-123";

    @Test
    @DisplayName("GET /api/v1/feeds/public : 전체 공개 피드 조회")
    void getPublicFeedsTest() throws Exception {
        // given
        ActivityFeedResponse feedResponse = MockUtil.readJsonFileToClass(
            "fixture/feed/activityFeedResponse.json", ActivityFeedResponse.class);
        Page<ActivityFeedResponse> responses = new PageImpl<>(
            List.of(feedResponse), PageRequest.of(0, 20), 1);

        when(activityFeedService.getPublicFeeds(anyString(), anyInt(), anyInt()))
            .thenReturn(responses);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/feeds/public")
                .with(user(MOCK_USER_ID))
                .param("page", "0")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("피드-01. 전체 공개 피드 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Activity Feed")
                        .description("전체 공개 피드 조회 (JWT 토큰 인증 필요)")
                        .queryParameters(
                            parameterWithName("page").type(SimpleType.NUMBER).description("페이지 번호 (0부터 시작)").optional(),
                            parameterWithName("size").type(SimpleType.NUMBER).description("페이지 크기").optional()
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("페이징된 피드 목록"),
                            fieldWithPath("value.content[]").type(JsonFieldType.ARRAY).description("피드 목록"),
                            fieldWithPath("value.content[].id").type(JsonFieldType.NUMBER).description("피드 ID"),
                            fieldWithPath("value.content[].user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value.content[].user_nickname").type(JsonFieldType.STRING).description("사용자 닉네임"),
                            fieldWithPath("value.content[].user_profile_image_url").type(JsonFieldType.STRING).description("사용자 프로필 이미지").optional(),
                            fieldWithPath("value.content[].activity_type").type(JsonFieldType.STRING).description("활동 타입"),
                            fieldWithPath("value.content[].activity_type_display_name").type(JsonFieldType.STRING).description("활동 타입 표시명"),
                            fieldWithPath("value.content[].category").type(JsonFieldType.STRING).description("카테고리"),
                            fieldWithPath("value.content[].title").type(JsonFieldType.STRING).description("피드 제목"),
                            fieldWithPath("value.content[].description").type(JsonFieldType.STRING).description("피드 설명").optional(),
                            fieldWithPath("value.content[].reference_type").type(JsonFieldType.STRING).description("참조 타입").optional(),
                            fieldWithPath("value.content[].reference_id").type(JsonFieldType.NUMBER).description("참조 ID").optional(),
                            fieldWithPath("value.content[].reference_name").type(JsonFieldType.STRING).description("참조 이름").optional(),
                            fieldWithPath("value.content[].visibility").type(JsonFieldType.STRING).description("공개 범위"),
                            fieldWithPath("value.content[].guild_id").type(JsonFieldType.NUMBER).description("길드 ID").optional(),
                            fieldWithPath("value.content[].image_url").type(JsonFieldType.STRING).description("이미지 URL").optional(),
                            fieldWithPath("value.content[].icon_url").type(JsonFieldType.STRING).description("아이콘 URL").optional(),
                            fieldWithPath("value.content[].like_count").type(JsonFieldType.NUMBER).description("좋아요 수"),
                            fieldWithPath("value.content[].comment_count").type(JsonFieldType.NUMBER).description("댓글 수"),
                            fieldWithPath("value.content[].liked_by_me").type(JsonFieldType.BOOLEAN).description("내가 좋아요 했는지"),
                            fieldWithPath("value.content[].created_at").type(JsonFieldType.STRING).description("생성 일시"),
                            fieldWithPath("value.pageable").type(JsonFieldType.OBJECT).description("페이징 정보"),
                            fieldWithPath("value.pageable.page_number").type(JsonFieldType.NUMBER).description("현재 페이지 번호"),
                            fieldWithPath("value.pageable.page_size").type(JsonFieldType.NUMBER).description("페이지 크기"),
                            fieldWithPath("value.pageable.sort").type(JsonFieldType.OBJECT).description("정렬 정보"),
                            fieldWithPath("value.pageable.sort.empty").type(JsonFieldType.BOOLEAN).description("정렬 비어있음 여부"),
                            fieldWithPath("value.pageable.sort.sorted").type(JsonFieldType.BOOLEAN).description("정렬 여부"),
                            fieldWithPath("value.pageable.sort.unsorted").type(JsonFieldType.BOOLEAN).description("미정렬 여부"),
                            fieldWithPath("value.pageable.offset").type(JsonFieldType.NUMBER).description("오프셋"),
                            fieldWithPath("value.pageable.paged").type(JsonFieldType.BOOLEAN).description("페이징 여부"),
                            fieldWithPath("value.pageable.unpaged").type(JsonFieldType.BOOLEAN).description("비페이징 여부"),
                            fieldWithPath("value.total_elements").type(JsonFieldType.NUMBER).description("전체 요소 수"),
                            fieldWithPath("value.total_pages").type(JsonFieldType.NUMBER).description("전체 페이지 수"),
                            fieldWithPath("value.last").type(JsonFieldType.BOOLEAN).description("마지막 페이지 여부"),
                            fieldWithPath("value.size").type(JsonFieldType.NUMBER).description("페이지 크기"),
                            fieldWithPath("value.number").type(JsonFieldType.NUMBER).description("현재 페이지 번호"),
                            fieldWithPath("value.sort").type(JsonFieldType.OBJECT).description("정렬 정보"),
                            fieldWithPath("value.sort.empty").type(JsonFieldType.BOOLEAN).description("정렬 비어있음 여부"),
                            fieldWithPath("value.sort.sorted").type(JsonFieldType.BOOLEAN).description("정렬 여부"),
                            fieldWithPath("value.sort.unsorted").type(JsonFieldType.BOOLEAN).description("미정렬 여부"),
                            fieldWithPath("value.first").type(JsonFieldType.BOOLEAN).description("첫 페이지 여부"),
                            fieldWithPath("value.number_of_elements").type(JsonFieldType.NUMBER).description("현재 페이지 요소 수"),
                            fieldWithPath("value.empty").type(JsonFieldType.BOOLEAN).description("비어있음 여부")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/feeds/timeline : 타임라인 피드 조회")
    void getTimelineFeedsTest() throws Exception {
        // given
        ActivityFeedResponse feedResponse = MockUtil.readJsonFileToClass(
            "fixture/feed/activityFeedResponse.json", ActivityFeedResponse.class);
        Page<ActivityFeedResponse> responses = new PageImpl<>(
            List.of(feedResponse), PageRequest.of(0, 20), 1);

        when(activityFeedService.getTimelineFeeds(anyString(), anyInt(), anyInt()))
            .thenReturn(responses);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/feeds/timeline")
                .with(user(MOCK_USER_ID))
                .param("page", "0")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("피드-02. 타임라인 피드 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Activity Feed")
                        .description("내 피드 + 친구 피드 타임라인 조회 (JWT 토큰 인증 필요)")
                        .queryParameters(
                            parameterWithName("page").type(SimpleType.NUMBER).description("페이지 번호").optional(),
                            parameterWithName("size").type(SimpleType.NUMBER).description("페이지 크기").optional()
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("페이징된 피드 목록"),
                            fieldWithPath("value.content[]").type(JsonFieldType.ARRAY).description("피드 목록"),
                            fieldWithPath("value.content[].id").type(JsonFieldType.NUMBER).description("피드 ID"),
                            fieldWithPath("value.content[].user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value.content[].user_nickname").type(JsonFieldType.STRING).description("사용자 닉네임"),
                            fieldWithPath("value.content[].user_profile_image_url").type(JsonFieldType.STRING).description("사용자 프로필 이미지").optional(),
                            fieldWithPath("value.content[].activity_type").type(JsonFieldType.STRING).description("활동 타입"),
                            fieldWithPath("value.content[].activity_type_display_name").type(JsonFieldType.STRING).description("활동 타입 표시명"),
                            fieldWithPath("value.content[].category").type(JsonFieldType.STRING).description("카테고리"),
                            fieldWithPath("value.content[].title").type(JsonFieldType.STRING).description("피드 제목"),
                            fieldWithPath("value.content[].description").type(JsonFieldType.STRING).description("피드 설명").optional(),
                            fieldWithPath("value.content[].reference_type").type(JsonFieldType.STRING).description("참조 타입").optional(),
                            fieldWithPath("value.content[].reference_id").type(JsonFieldType.NUMBER).description("참조 ID").optional(),
                            fieldWithPath("value.content[].reference_name").type(JsonFieldType.STRING).description("참조 이름").optional(),
                            fieldWithPath("value.content[].visibility").type(JsonFieldType.STRING).description("공개 범위"),
                            fieldWithPath("value.content[].guild_id").type(JsonFieldType.NUMBER).description("길드 ID").optional(),
                            fieldWithPath("value.content[].image_url").type(JsonFieldType.STRING).description("이미지 URL").optional(),
                            fieldWithPath("value.content[].icon_url").type(JsonFieldType.STRING).description("아이콘 URL").optional(),
                            fieldWithPath("value.content[].like_count").type(JsonFieldType.NUMBER).description("좋아요 수"),
                            fieldWithPath("value.content[].comment_count").type(JsonFieldType.NUMBER).description("댓글 수"),
                            fieldWithPath("value.content[].liked_by_me").type(JsonFieldType.BOOLEAN).description("내가 좋아요 했는지"),
                            fieldWithPath("value.content[].created_at").type(JsonFieldType.STRING).description("생성 일시"),
                            fieldWithPath("value.pageable").type(JsonFieldType.OBJECT).description("페이징 정보"),
                            fieldWithPath("value.pageable.page_number").type(JsonFieldType.NUMBER).description("현재 페이지 번호"),
                            fieldWithPath("value.pageable.page_size").type(JsonFieldType.NUMBER).description("페이지 크기"),
                            fieldWithPath("value.pageable.sort").type(JsonFieldType.OBJECT).description("정렬 정보"),
                            fieldWithPath("value.pageable.sort.empty").type(JsonFieldType.BOOLEAN).description("정렬 비어있음 여부"),
                            fieldWithPath("value.pageable.sort.sorted").type(JsonFieldType.BOOLEAN).description("정렬 여부"),
                            fieldWithPath("value.pageable.sort.unsorted").type(JsonFieldType.BOOLEAN).description("미정렬 여부"),
                            fieldWithPath("value.pageable.offset").type(JsonFieldType.NUMBER).description("오프셋"),
                            fieldWithPath("value.pageable.paged").type(JsonFieldType.BOOLEAN).description("페이징 여부"),
                            fieldWithPath("value.pageable.unpaged").type(JsonFieldType.BOOLEAN).description("비페이징 여부"),
                            fieldWithPath("value.total_elements").type(JsonFieldType.NUMBER).description("전체 요소 수"),
                            fieldWithPath("value.total_pages").type(JsonFieldType.NUMBER).description("전체 페이지 수"),
                            fieldWithPath("value.last").type(JsonFieldType.BOOLEAN).description("마지막 페이지 여부"),
                            fieldWithPath("value.size").type(JsonFieldType.NUMBER).description("페이지 크기"),
                            fieldWithPath("value.number").type(JsonFieldType.NUMBER).description("현재 페이지 번호"),
                            fieldWithPath("value.sort").type(JsonFieldType.OBJECT).description("정렬 정보"),
                            fieldWithPath("value.sort.empty").type(JsonFieldType.BOOLEAN).description("정렬 비어있음 여부"),
                            fieldWithPath("value.sort.sorted").type(JsonFieldType.BOOLEAN).description("정렬 여부"),
                            fieldWithPath("value.sort.unsorted").type(JsonFieldType.BOOLEAN).description("미정렬 여부"),
                            fieldWithPath("value.first").type(JsonFieldType.BOOLEAN).description("첫 페이지 여부"),
                            fieldWithPath("value.number_of_elements").type(JsonFieldType.NUMBER).description("현재 페이지 요소 수"),
                            fieldWithPath("value.empty").type(JsonFieldType.BOOLEAN).description("비어있음 여부")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/feeds/{feedId} : 피드 상세 조회")
    void getFeedTest() throws Exception {
        // given
        Long feedId = 1L;
        ActivityFeedResponse response = MockUtil.readJsonFileToClass(
            "fixture/feed/activityFeedResponse.json", ActivityFeedResponse.class);

        when(activityFeedService.getFeed(anyLong(), anyString()))
            .thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/feeds/{feedId}", feedId)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("피드-03. 피드 상세 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Activity Feed")
                        .description("피드 상세 조회 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("feedId").type(SimpleType.NUMBER).description("피드 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("피드 정보"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("피드 ID"),
                            fieldWithPath("value.user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value.user_nickname").type(JsonFieldType.STRING).description("사용자 닉네임"),
                            fieldWithPath("value.user_profile_image_url").type(JsonFieldType.STRING).description("사용자 프로필 이미지").optional(),
                            fieldWithPath("value.activity_type").type(JsonFieldType.STRING).description("활동 타입"),
                            fieldWithPath("value.activity_type_display_name").type(JsonFieldType.STRING).description("활동 타입 표시명"),
                            fieldWithPath("value.category").type(JsonFieldType.STRING).description("카테고리"),
                            fieldWithPath("value.title").type(JsonFieldType.STRING).description("피드 제목"),
                            fieldWithPath("value.description").type(JsonFieldType.STRING).description("피드 설명").optional(),
                            fieldWithPath("value.reference_type").type(JsonFieldType.STRING).description("참조 타입").optional(),
                            fieldWithPath("value.reference_id").type(JsonFieldType.NUMBER).description("참조 ID").optional(),
                            fieldWithPath("value.reference_name").type(JsonFieldType.STRING).description("참조 이름").optional(),
                            fieldWithPath("value.visibility").type(JsonFieldType.STRING).description("공개 범위"),
                            fieldWithPath("value.guild_id").type(JsonFieldType.NUMBER).description("길드 ID").optional(),
                            fieldWithPath("value.image_url").type(JsonFieldType.STRING).description("이미지 URL").optional(),
                            fieldWithPath("value.icon_url").type(JsonFieldType.STRING).description("아이콘 URL").optional(),
                            fieldWithPath("value.like_count").type(JsonFieldType.NUMBER).description("좋아요 수"),
                            fieldWithPath("value.comment_count").type(JsonFieldType.NUMBER).description("댓글 수"),
                            fieldWithPath("value.liked_by_me").type(JsonFieldType.BOOLEAN).description("내가 좋아요 했는지"),
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
    @DisplayName("POST /api/v1/feeds/{feedId}/like : 좋아요 토글")
    void toggleLikeTest() throws Exception {
        // given
        Long feedId = 1L;

        when(activityFeedService.toggleLike(anyLong(), anyString()))
            .thenReturn(true);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/feeds/{feedId}/like", feedId)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("피드-04. 좋아요 토글",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Activity Feed")
                        .description("피드 좋아요 토글 (좋아요/좋아요 취소) (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("feedId").type(SimpleType.NUMBER).description("피드 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("좋아요 결과"),
                            fieldWithPath("value.liked").type(JsonFieldType.BOOLEAN).description("좋아요 상태 (true: 좋아요, false: 좋아요 취소)")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/feeds/{feedId}/comments : 댓글 목록 조회")
    void getCommentsTest() throws Exception {
        // given
        Long feedId = 1L;
        FeedCommentResponse commentResponse = MockUtil.readJsonFileToClass(
            "fixture/feed/feedCommentResponse.json", FeedCommentResponse.class);
        Page<FeedCommentResponse> responses = new PageImpl<>(
            List.of(commentResponse), PageRequest.of(0, 20), 1);

        when(activityFeedService.getComments(anyLong(), anyInt(), anyInt()))
            .thenReturn(responses);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/feeds/{feedId}/comments", feedId)
                .param("page", "0")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("피드-05. 댓글 목록 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Activity Feed")
                        .description("피드 댓글 목록 조회 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("feedId").type(SimpleType.NUMBER).description("피드 ID")
                        )
                        .queryParameters(
                            parameterWithName("page").type(SimpleType.NUMBER).description("페이지 번호").optional(),
                            parameterWithName("size").type(SimpleType.NUMBER).description("페이지 크기").optional()
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("페이징된 댓글 목록"),
                            fieldWithPath("value.content[]").type(JsonFieldType.ARRAY).description("댓글 목록"),
                            fieldWithPath("value.content[].id").type(JsonFieldType.NUMBER).description("댓글 ID"),
                            fieldWithPath("value.content[].feed_id").type(JsonFieldType.NUMBER).description("피드 ID"),
                            fieldWithPath("value.content[].user_id").type(JsonFieldType.STRING).description("작성자 ID"),
                            fieldWithPath("value.content[].user_nickname").type(JsonFieldType.STRING).description("작성자 닉네임"),
                            fieldWithPath("value.content[].content").type(JsonFieldType.STRING).description("댓글 내용"),
                            fieldWithPath("value.content[].deleted").type(JsonFieldType.BOOLEAN).description("삭제 여부"),
                            fieldWithPath("value.content[].created_at").type(JsonFieldType.STRING).description("작성 일시"),
                            fieldWithPath("value.pageable").type(JsonFieldType.OBJECT).description("페이징 정보"),
                            fieldWithPath("value.pageable.page_number").type(JsonFieldType.NUMBER).description("현재 페이지 번호"),
                            fieldWithPath("value.pageable.page_size").type(JsonFieldType.NUMBER).description("페이지 크기"),
                            fieldWithPath("value.pageable.sort").type(JsonFieldType.OBJECT).description("정렬 정보"),
                            fieldWithPath("value.pageable.sort.empty").type(JsonFieldType.BOOLEAN).description("정렬 비어있음 여부"),
                            fieldWithPath("value.pageable.sort.sorted").type(JsonFieldType.BOOLEAN).description("정렬 여부"),
                            fieldWithPath("value.pageable.sort.unsorted").type(JsonFieldType.BOOLEAN).description("미정렬 여부"),
                            fieldWithPath("value.pageable.offset").type(JsonFieldType.NUMBER).description("오프셋"),
                            fieldWithPath("value.pageable.paged").type(JsonFieldType.BOOLEAN).description("페이징 여부"),
                            fieldWithPath("value.pageable.unpaged").type(JsonFieldType.BOOLEAN).description("비페이징 여부"),
                            fieldWithPath("value.total_elements").type(JsonFieldType.NUMBER).description("전체 요소 수"),
                            fieldWithPath("value.total_pages").type(JsonFieldType.NUMBER).description("전체 페이지 수"),
                            fieldWithPath("value.last").type(JsonFieldType.BOOLEAN).description("마지막 페이지 여부"),
                            fieldWithPath("value.size").type(JsonFieldType.NUMBER).description("페이지 크기"),
                            fieldWithPath("value.number").type(JsonFieldType.NUMBER).description("현재 페이지 번호"),
                            fieldWithPath("value.sort").type(JsonFieldType.OBJECT).description("정렬 정보"),
                            fieldWithPath("value.sort.empty").type(JsonFieldType.BOOLEAN).description("정렬 비어있음 여부"),
                            fieldWithPath("value.sort.sorted").type(JsonFieldType.BOOLEAN).description("정렬 여부"),
                            fieldWithPath("value.sort.unsorted").type(JsonFieldType.BOOLEAN).description("미정렬 여부"),
                            fieldWithPath("value.first").type(JsonFieldType.BOOLEAN).description("첫 페이지 여부"),
                            fieldWithPath("value.number_of_elements").type(JsonFieldType.NUMBER).description("현재 페이지 요소 수"),
                            fieldWithPath("value.empty").type(JsonFieldType.BOOLEAN).description("비어있음 여부")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/feeds/{feedId}/comments : 댓글 작성")
    void addCommentTest() throws Exception {
        // given
        Long feedId = 1L;
        FeedCommentResponse response = MockUtil.readJsonFileToClass(
            "fixture/feed/feedCommentResponse.json", FeedCommentResponse.class);

        when(activityFeedService.addComment(anyLong(), anyString(), anyString(), any(FeedCommentRequest.class)))
            .thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/feeds/{feedId}/comments", feedId)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"축하합니다!\"}")
        ).andDo(
            MockMvcRestDocumentationWrapper.document("피드-06. 댓글 작성",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Activity Feed")
                        .description("피드에 댓글 작성 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("feedId").type(SimpleType.NUMBER).description("피드 ID")
                        )
                        .requestFields(
                            fieldWithPath("content").type(JsonFieldType.STRING).description("댓글 내용")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("댓글 정보"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("댓글 ID"),
                            fieldWithPath("value.feed_id").type(JsonFieldType.NUMBER).description("피드 ID"),
                            fieldWithPath("value.user_id").type(JsonFieldType.STRING).description("작성자 ID"),
                            fieldWithPath("value.user_nickname").type(JsonFieldType.STRING).description("작성자 닉네임"),
                            fieldWithPath("value.content").type(JsonFieldType.STRING).description("댓글 내용"),
                            fieldWithPath("value.deleted").type(JsonFieldType.BOOLEAN).description("삭제 여부"),
                            fieldWithPath("value.created_at").type(JsonFieldType.STRING).description("작성 일시")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/v1/feeds/{feedId}/comments/{commentId} : 댓글 삭제")
    void deleteCommentTest() throws Exception {
        // given
        Long feedId = 1L;
        Long commentId = 1L;

        doNothing().when(activityFeedService).deleteComment(anyLong(), anyLong(), anyString());

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.delete("/api/v1/feeds/{feedId}/comments/{commentId}", feedId, commentId)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("피드-07. 댓글 삭제",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Activity Feed")
                        .description("피드 댓글 삭제 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("feedId").type(SimpleType.NUMBER).description("피드 ID"),
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

    @Test
    @DisplayName("DELETE /api/v1/feeds/{feedId} : 피드 삭제")
    void deleteFeedTest() throws Exception {
        // given
        Long feedId = 1L;

        doNothing().when(activityFeedService).deleteFeed(anyLong(), anyString());

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.delete("/api/v1/feeds/{feedId}", feedId)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("피드-08. 피드 삭제",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Activity Feed")
                        .description("자신의 피드 삭제 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("feedId").type(SimpleType.NUMBER).description("피드 ID")
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
