package io.pinkspider.leveluptogethermvp.userservice.friend.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.headerWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.SimpleType;
import com.fasterxml.jackson.core.type.TypeReference;
import io.pinkspider.global.component.LmObjectMapper;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.userservice.friend.domain.dto.FriendRequestResponse;
import io.pinkspider.leveluptogethermvp.userservice.friend.domain.dto.FriendResponse;
import io.pinkspider.leveluptogethermvp.userservice.friend.application.FriendService;
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

@WebMvcTest(controllers = FriendController.class,
    excludeAutoConfiguration = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class
    }
)
@Import(ControllerTestConfig.class)
@AutoConfigureRestDocs
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class FriendControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    private final LmObjectMapper objectMapper = new LmObjectMapper();

    @MockitoBean
    private FriendService friendService;

    private static final String X_USER_ID = "X-User-Id";
    private static final String MOCK_USER_ID = "test-user-123";

    @Test
    @DisplayName("GET /api/v1/friends : 친구 목록 조회")
    void getFriendsTest() throws Exception {
        // given
        List<FriendResponse> friendList = MockUtil.readJsonFileToClassList(
            "fixture/friend/friendResponseList.json",
            new TypeReference<List<FriendResponse>>() {});
        Page<FriendResponse> responses = new PageImpl<>(friendList, PageRequest.of(0, 20), friendList.size());

        when(friendService.getFriends(anyString(), any()))
            .thenReturn(responses);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/friends")
                .header(X_USER_ID, MOCK_USER_ID)
                .param("page", "0")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("친구-01. 친구 목록 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Friend")
                        .description("친구 목록 조회 (페이징)")
                        .requestHeaders(
                            headerWithName(X_USER_ID).description("사용자 ID")
                        )
                        .queryParameters(
                            parameterWithName("page").type(SimpleType.NUMBER).description("페이지 번호").optional(),
                            parameterWithName("size").type(SimpleType.NUMBER).description("페이지 크기").optional()
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("페이징된 친구 목록"),
                            fieldWithPath("value.content[]").type(JsonFieldType.ARRAY).description("친구 목록"),
                            fieldWithPath("value.content[].friendship_id").type(JsonFieldType.NUMBER).description("친구관계 ID"),
                            fieldWithPath("value.content[].friend_id").type(JsonFieldType.STRING).description("친구 사용자 ID"),
                            fieldWithPath("value.content[].friend_nickname").type(JsonFieldType.STRING).description("친구 닉네임"),
                            fieldWithPath("value.content[].friend_profile_image_url").type(JsonFieldType.STRING).description("프로필 이미지 URL").optional(),
                            fieldWithPath("value.content[].friend_level").type(JsonFieldType.NUMBER).description("친구 레벨").optional(),
                            fieldWithPath("value.content[].friend_title").type(JsonFieldType.STRING).description("친구 칭호").optional(),
                            fieldWithPath("value.content[].status").type(JsonFieldType.STRING).description("친구관계 상태"),
                            fieldWithPath("value.content[].friends_since").type(JsonFieldType.STRING).description("친구가 된 일시"),
                            fieldWithPath("value.content[].is_online").type(JsonFieldType.BOOLEAN).description("온라인 여부").optional(),
                            fieldWithPath("value.pageable").type(JsonFieldType.OBJECT).description("페이징 정보"),
                            fieldWithPath("value.pageable.page_number").type(JsonFieldType.NUMBER).description("현재 페이지"),
                            fieldWithPath("value.pageable.page_size").type(JsonFieldType.NUMBER).description("페이지 크기"),
                            fieldWithPath("value.pageable.sort").type(JsonFieldType.OBJECT).description("정렬 정보"),
                            fieldWithPath("value.pageable.sort.empty").type(JsonFieldType.BOOLEAN).description("정렬 비어있음"),
                            fieldWithPath("value.pageable.sort.sorted").type(JsonFieldType.BOOLEAN).description("정렬됨"),
                            fieldWithPath("value.pageable.sort.unsorted").type(JsonFieldType.BOOLEAN).description("미정렬"),
                            fieldWithPath("value.pageable.offset").type(JsonFieldType.NUMBER).description("오프셋"),
                            fieldWithPath("value.pageable.paged").type(JsonFieldType.BOOLEAN).description("페이징"),
                            fieldWithPath("value.pageable.unpaged").type(JsonFieldType.BOOLEAN).description("비페이징"),
                            fieldWithPath("value.total_elements").type(JsonFieldType.NUMBER).description("전체 요소 수"),
                            fieldWithPath("value.total_pages").type(JsonFieldType.NUMBER).description("전체 페이지 수"),
                            fieldWithPath("value.last").type(JsonFieldType.BOOLEAN).description("마지막 페이지"),
                            fieldWithPath("value.size").type(JsonFieldType.NUMBER).description("페이지 크기"),
                            fieldWithPath("value.number").type(JsonFieldType.NUMBER).description("현재 페이지"),
                            fieldWithPath("value.sort").type(JsonFieldType.OBJECT).description("정렬 정보"),
                            fieldWithPath("value.sort.empty").type(JsonFieldType.BOOLEAN).description("정렬 비어있음"),
                            fieldWithPath("value.sort.sorted").type(JsonFieldType.BOOLEAN).description("정렬됨"),
                            fieldWithPath("value.sort.unsorted").type(JsonFieldType.BOOLEAN).description("미정렬"),
                            fieldWithPath("value.first").type(JsonFieldType.BOOLEAN).description("첫 페이지"),
                            fieldWithPath("value.number_of_elements").type(JsonFieldType.NUMBER).description("현재 요소 수"),
                            fieldWithPath("value.empty").type(JsonFieldType.BOOLEAN).description("비어있음")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/friends/request : 친구 요청 보내기")
    void sendFriendRequestTest() throws Exception {
        // given
        FriendRequestResponse response = MockUtil.readJsonFileToClass(
            "fixture/friend/friendRequestResponse.json", FriendRequestResponse.class);

        // null 체크 - fixture 로드 실패 시 직접 생성
        if (response == null) {
            response = FriendRequestResponse.builder()
                .id(1L)
                .requesterId("requester-user-123")
                .requesterNickname("요청자닉네임")
                .requesterProfileImageUrl("https://example.com/profile.jpg")
                .requesterLevel(10)
                .message("친구 신청합니다!")
                .requestedAt(java.time.LocalDateTime.of(2025, 1, 15, 10, 30))
                .build();
        }

        when(friendService.sendFriendRequest(anyString(), anyString(), nullable(String.class)))
            .thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/friends/request")
                .header(X_USER_ID, MOCK_USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"friendId\":\"target-user-456\",\"message\":\"친구 신청합니다!\"}")
        ).andDo(
            MockMvcRestDocumentationWrapper.document("친구-02. 친구 요청 보내기",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Friend")
                        .description("친구 요청 보내기")
                        .requestHeaders(
                            headerWithName(X_USER_ID).description("사용자 ID")
                        )
                        .requestFields(
                            fieldWithPath("friendId").type(JsonFieldType.STRING).description("친구 요청 대상 ID"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("친구 요청 메시지").optional()
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("친구 요청 정보"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("친구 요청 ID"),
                            fieldWithPath("value.requester_id").type(JsonFieldType.STRING).description("요청자 ID"),
                            fieldWithPath("value.requester_nickname").type(JsonFieldType.STRING).description("요청자 닉네임"),
                            fieldWithPath("value.requester_profile_image_url").type(JsonFieldType.STRING).description("프로필 이미지 URL").optional(),
                            fieldWithPath("value.requester_level").type(JsonFieldType.NUMBER).description("요청자 레벨").optional(),
                            fieldWithPath("value.message").type(JsonFieldType.STRING).description("요청 메시지").optional(),
                            fieldWithPath("value.requested_at").type(JsonFieldType.STRING).description("요청 일시")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/friends/requests/received : 받은 친구 요청 목록 조회")
    void getPendingRequestsReceivedTest() throws Exception {
        // given
        List<FriendRequestResponse> responses = MockUtil.readJsonFileToClassList(
            "fixture/friend/friendRequestResponseList.json",
            new TypeReference<List<FriendRequestResponse>>() {});

        when(friendService.getPendingRequestsReceived(anyString()))
            .thenReturn(responses);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/friends/requests/received")
                .header(X_USER_ID, MOCK_USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("친구-03. 받은 친구 요청 목록 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Friend")
                        .description("받은 친구 요청 목록 조회")
                        .requestHeaders(
                            headerWithName(X_USER_ID).description("사용자 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value[]").type(JsonFieldType.ARRAY).description("친구 요청 목록"),
                            fieldWithPath("value[].id").type(JsonFieldType.NUMBER).description("요청 ID"),
                            fieldWithPath("value[].requester_id").type(JsonFieldType.STRING).description("요청자 ID"),
                            fieldWithPath("value[].requester_nickname").type(JsonFieldType.STRING).description("요청자 닉네임"),
                            fieldWithPath("value[].requester_profile_image_url").type(JsonFieldType.STRING).description("프로필 이미지 URL").optional(),
                            fieldWithPath("value[].requester_level").type(JsonFieldType.NUMBER).description("요청자 레벨").optional(),
                            fieldWithPath("value[].message").type(JsonFieldType.STRING).description("요청 메시지").optional(),
                            fieldWithPath("value[].requested_at").type(JsonFieldType.STRING).description("요청 일시")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/friends/requests/{requestId}/accept : 친구 요청 수락")
    void acceptFriendRequestTest() throws Exception {
        // given
        Long requestId = 1L;
        FriendResponse response = MockUtil.readJsonFileToClass(
            "fixture/friend/friendResponse.json", FriendResponse.class);

        when(friendService.acceptFriendRequest(anyString(), anyLong()))
            .thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/friends/requests/{requestId}/accept", requestId)
                .header(X_USER_ID, MOCK_USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("친구-04. 친구 요청 수락",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Friend")
                        .description("친구 요청 수락")
                        .requestHeaders(
                            headerWithName(X_USER_ID).description("사용자 ID")
                        )
                        .pathParameters(
                            parameterWithName("requestId").type(SimpleType.NUMBER).description("친구 요청 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("친구 정보"),
                            fieldWithPath("value.friendship_id").type(JsonFieldType.NUMBER).description("친구관계 ID"),
                            fieldWithPath("value.friend_id").type(JsonFieldType.STRING).description("친구 ID"),
                            fieldWithPath("value.friend_nickname").type(JsonFieldType.STRING).description("친구 닉네임"),
                            fieldWithPath("value.friend_profile_image_url").type(JsonFieldType.STRING).description("프로필 이미지 URL").optional(),
                            fieldWithPath("value.friend_level").type(JsonFieldType.NUMBER).description("친구 레벨").optional(),
                            fieldWithPath("value.friend_title").type(JsonFieldType.STRING).description("친구 칭호").optional(),
                            fieldWithPath("value.status").type(JsonFieldType.STRING).description("친구관계 상태"),
                            fieldWithPath("value.friends_since").type(JsonFieldType.STRING).description("친구가 된 일시"),
                            fieldWithPath("value.is_online").type(JsonFieldType.BOOLEAN).description("온라인 여부").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/friends/requests/{requestId}/reject : 친구 요청 거절")
    void rejectFriendRequestTest() throws Exception {
        // given
        Long requestId = 1L;

        doNothing().when(friendService).rejectFriendRequest(anyString(), anyLong());

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/friends/requests/{requestId}/reject", requestId)
                .header(X_USER_ID, MOCK_USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("친구-05. 친구 요청 거절",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Friend")
                        .description("친구 요청 거절")
                        .requestHeaders(
                            headerWithName(X_USER_ID).description("사용자 ID")
                        )
                        .pathParameters(
                            parameterWithName("requestId").type(SimpleType.NUMBER).description("친구 요청 ID")
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
    @DisplayName("DELETE /api/v1/friends/{friendId} : 친구 삭제")
    void removeFriendTest() throws Exception {
        // given
        String friendId = "friend-user-123";

        doNothing().when(friendService).removeFriend(anyString(), anyString());

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.delete("/api/v1/friends/{friendId}", friendId)
                .header(X_USER_ID, MOCK_USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("친구-06. 친구 삭제",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Friend")
                        .description("친구 관계 삭제")
                        .requestHeaders(
                            headerWithName(X_USER_ID).description("사용자 ID")
                        )
                        .pathParameters(
                            parameterWithName("friendId").type(SimpleType.STRING).description("삭제할 친구 ID")
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
    @DisplayName("POST /api/v1/friends/block/{targetId} : 사용자 차단")
    void blockUserTest() throws Exception {
        // given
        String targetId = "target-user-123";

        doNothing().when(friendService).blockUser(anyString(), anyString());

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/friends/block/{targetId}", targetId)
                .header(X_USER_ID, MOCK_USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("친구-07. 사용자 차단",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Friend")
                        .description("사용자 차단")
                        .requestHeaders(
                            headerWithName(X_USER_ID).description("사용자 ID")
                        )
                        .pathParameters(
                            parameterWithName("targetId").type(SimpleType.STRING).description("차단할 사용자 ID")
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
    @DisplayName("DELETE /api/v1/friends/block/{targetId} : 사용자 차단 해제")
    void unblockUserTest() throws Exception {
        // given
        String targetId = "target-user-123";

        doNothing().when(friendService).unblockUser(anyString(), anyString());

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.delete("/api/v1/friends/block/{targetId}", targetId)
                .header(X_USER_ID, MOCK_USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("친구-08. 사용자 차단 해제",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Friend")
                        .description("사용자 차단 해제")
                        .requestHeaders(
                            headerWithName(X_USER_ID).description("사용자 ID")
                        )
                        .pathParameters(
                            parameterWithName("targetId").type(SimpleType.STRING).description("차단 해제할 사용자 ID")
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
    @DisplayName("GET /api/v1/friends/blocked : 차단 목록 조회")
    void getBlockedUsersTest() throws Exception {
        // given
        List<FriendResponse> responses = MockUtil.readJsonFileToClassList(
            "fixture/friend/friendResponseList.json",
            new TypeReference<List<FriendResponse>>() {});

        when(friendService.getBlockedUsers(anyString()))
            .thenReturn(responses);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/friends/blocked")
                .header(X_USER_ID, MOCK_USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("친구-09. 차단 목록 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Friend")
                        .description("차단한 사용자 목록 조회")
                        .requestHeaders(
                            headerWithName(X_USER_ID).description("사용자 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value[]").type(JsonFieldType.ARRAY).description("차단 목록"),
                            fieldWithPath("value[].friendship_id").type(JsonFieldType.NUMBER).description("친구관계 ID"),
                            fieldWithPath("value[].friend_id").type(JsonFieldType.STRING).description("차단된 사용자 ID"),
                            fieldWithPath("value[].friend_nickname").type(JsonFieldType.STRING).description("닉네임"),
                            fieldWithPath("value[].friend_profile_image_url").type(JsonFieldType.STRING).description("프로필 이미지 URL").optional(),
                            fieldWithPath("value[].friend_level").type(JsonFieldType.NUMBER).description("레벨").optional(),
                            fieldWithPath("value[].friend_title").type(JsonFieldType.STRING).description("칭호").optional(),
                            fieldWithPath("value[].status").type(JsonFieldType.STRING).description("상태"),
                            fieldWithPath("value[].friends_since").type(JsonFieldType.STRING).description("차단 일시"),
                            fieldWithPath("value[].is_online").type(JsonFieldType.BOOLEAN).description("온라인 여부").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }
}
