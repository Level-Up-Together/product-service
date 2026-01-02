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
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildExperienceService;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildHeadquartersService;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildService;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildCreateRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildExperienceResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildJoinRequestResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildMemberResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildUpdateRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.TransferMasterRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.JoinRequestStatus;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberRole;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberStatus;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildJoinType;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildVisibility;
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

@WebMvcTest(controllers = GuildController.class,
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
class GuildControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    private final LmObjectMapper objectMapper = new LmObjectMapper();

    @MockitoBean
    private GuildService guildService;

    @MockitoBean
    private GuildExperienceService guildExperienceService;

    @MockitoBean
    private GuildHeadquartersService guildHeadquartersService;

    private static final String MOCK_USER_ID = "test-user-123";

    private GuildResponse createMockGuildResponse() {
        return GuildResponse.builder()
            .id(1L)
            .name("테스트 길드")
            .description("테스트 길드입니다.")
            .masterId(MOCK_USER_ID)
            .visibility(GuildVisibility.PUBLIC)
            .joinType(GuildJoinType.OPEN)
            .maxMembers(50)
            .currentMemberCount(10)
            .currentLevel(5)
            .currentExp(500)
            .totalExp(1500)
            .categoryId(1L)
            .categoryName("자기계발")
            .categoryIcon("📚")
            .baseAddress("서울시 강남구")
            .baseLatitude(37.5665)
            .baseLongitude(126.978)
            .createdAt(LocalDateTime.now())
            .build();
    }

    @Test
    @DisplayName("POST /api/v1/guilds : 길드 생성")
    void createGuildTest() throws Exception {
        // given
        GuildCreateRequest request = GuildCreateRequest.builder()
            .name("테스트 길드")
            .description("테스트 길드입니다.")
            .visibility(GuildVisibility.PUBLIC)
            .categoryId(1L)
            .maxMembers(50)
            .build();

        when(guildService.createGuild(anyString(), any(GuildCreateRequest.class)))
            .thenReturn(createMockGuildResponse());

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/guilds")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드-01. 길드 생성",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild")
                        .description("새 길드 생성 (JWT 토큰 인증 필요)")
                        .requestFields(
                            fieldWithPath("name").type(JsonFieldType.STRING).description("길드 이름"),
                            fieldWithPath("description").type(JsonFieldType.STRING).description("길드 설명").optional(),
                            fieldWithPath("visibility").type(JsonFieldType.STRING).description("공개 여부 (PUBLIC, PRIVATE)"),
                            fieldWithPath("join_type").type(JsonFieldType.STRING).description("가입 방식 (OPEN, APPROVAL, INVITE_ONLY)").optional(),
                            fieldWithPath("category_id").type(JsonFieldType.NUMBER).description("카테고리 ID (필수)"),
                            fieldWithPath("max_members").type(JsonFieldType.NUMBER).description("최대 멤버 수").optional(),
                            fieldWithPath("image_url").type(JsonFieldType.STRING).description("길드 이미지 URL").optional(),
                            fieldWithPath("base_address").type(JsonFieldType.STRING).description("거점 주소").optional(),
                            fieldWithPath("base_latitude").type(JsonFieldType.NUMBER).description("거점 위도").optional(),
                            fieldWithPath("base_longitude").type(JsonFieldType.NUMBER).description("거점 경도").optional()
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("길드 정보"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("길드 ID"),
                            fieldWithPath("value.name").type(JsonFieldType.STRING).description("길드 이름"),
                            fieldWithPath("value.description").type(JsonFieldType.STRING).description("길드 설명").optional(),
                            fieldWithPath("value.master_id").type(JsonFieldType.STRING).description("마스터 ID"),
                            fieldWithPath("value.visibility").type(JsonFieldType.STRING).description("공개 여부"),
                            fieldWithPath("value.join_type").type(JsonFieldType.STRING).description("가입 방식 (OPEN, APPROVAL, INVITE_ONLY)").optional(),
                            fieldWithPath("value.max_members").type(JsonFieldType.NUMBER).description("최대 멤버 수"),
                            fieldWithPath("value.current_member_count").type(JsonFieldType.NUMBER).description("현재 멤버 수").optional(),
                            fieldWithPath("value.current_level").type(JsonFieldType.NUMBER).description("길드 레벨").optional(),
                            fieldWithPath("value.current_exp").type(JsonFieldType.NUMBER).description("현재 경험치").optional(),
                            fieldWithPath("value.total_exp").type(JsonFieldType.NUMBER).description("길드 누적 경험치").optional(),
                            fieldWithPath("value.category_id").type(JsonFieldType.NUMBER).description("카테고리 ID").optional(),
                            fieldWithPath("value.category_name").type(JsonFieldType.STRING).description("카테고리 이름").optional(),
                            fieldWithPath("value.category_icon").type(JsonFieldType.STRING).description("카테고리 아이콘").optional(),
                            fieldWithPath("value.base_address").type(JsonFieldType.STRING).description("거점 주소").optional(),
                            fieldWithPath("value.base_latitude").type(JsonFieldType.NUMBER).description("거점 위도").optional(),
                            fieldWithPath("value.base_longitude").type(JsonFieldType.NUMBER).description("거점 경도").optional(),
                            fieldWithPath("value.image_url").type(JsonFieldType.STRING).description("길드 이미지").optional(),
                            fieldWithPath("value.created_at").type(JsonFieldType.STRING).description("생성일시")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/guilds/{guildId} : 길드 조회")
    void getGuildTest() throws Exception {
        // given
        when(guildService.getGuild(anyLong(), anyString()))
            .thenReturn(createMockGuildResponse());

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/guilds/{guildId}", 1L)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드-02. 길드 상세 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild")
                        .description("길드 상세 정보 조회 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/guilds/public : 공개 길드 목록 조회")
    void getPublicGuildsTest() throws Exception {
        // given
        Page<GuildResponse> page = new PageImpl<>(
            List.of(createMockGuildResponse()), PageRequest.of(0, 20), 1);

        when(guildService.getPublicGuilds(any(Pageable.class))).thenReturn(page);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/guilds/public")
                .param("page", "0")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드-03. 공개 길드 목록 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild")
                        .description("공개 길드 목록 조회 (페이징)")
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
    @DisplayName("GET /api/v1/guilds/search : 길드 검색")
    void searchGuildsTest() throws Exception {
        // given
        Page<GuildResponse> page = new PageImpl<>(
            List.of(createMockGuildResponse()), PageRequest.of(0, 20), 1);

        when(guildService.searchGuilds(anyString(), any(Pageable.class))).thenReturn(page);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/guilds/search")
                .param("keyword", "테스트")
                .param("page", "0")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드-04. 길드 검색",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild")
                        .description("길드 이름으로 검색")
                        .queryParameters(
                            parameterWithName("keyword").type(SimpleType.STRING).description("검색 키워드"),
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
    @DisplayName("GET /api/v1/guilds/my : 내 길드 목록")
    void getMyGuildsTest() throws Exception {
        // given
        when(guildService.getMyGuilds(anyString()))
            .thenReturn(List.of(createMockGuildResponse()));

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/guilds/my")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드-05. 내 길드 목록",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild")
                        .description("내가 가입한 길드 목록 (JWT 토큰 인증 필요)")
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("PUT /api/v1/guilds/{guildId} : 길드 정보 수정")
    void updateGuildTest() throws Exception {
        // given
        GuildUpdateRequest request = GuildUpdateRequest.builder()
            .name("수정된 길드명")
            .description("수정된 설명")
            .build();

        when(guildService.updateGuild(anyLong(), anyString(), any(GuildUpdateRequest.class)))
            .thenReturn(createMockGuildResponse());

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.put("/api/v1/guilds/{guildId}", 1L)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드-06. 길드 정보 수정",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild")
                        .description("길드 정보 수정 (마스터만 가능, JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID")
                        )
                        .requestFields(
                            fieldWithPath("name").type(JsonFieldType.STRING).description("길드 이름").optional(),
                            fieldWithPath("description").type(JsonFieldType.STRING).description("길드 설명").optional(),
                            fieldWithPath("visibility").type(JsonFieldType.STRING).description("공개 여부").optional(),
                            fieldWithPath("join_type").type(JsonFieldType.STRING).description("가입 방식 (OPEN, APPROVAL, INVITE_ONLY)").optional(),
                            fieldWithPath("category_id").type(JsonFieldType.NUMBER).description("카테고리 ID").optional(),
                            fieldWithPath("max_members").type(JsonFieldType.NUMBER).description("최대 멤버 수").optional(),
                            fieldWithPath("image_url").type(JsonFieldType.STRING).description("길드 이미지 URL").optional(),
                            fieldWithPath("base_address").type(JsonFieldType.STRING).description("거점 주소").optional(),
                            fieldWithPath("base_latitude").type(JsonFieldType.NUMBER).description("거점 위도").optional(),
                            fieldWithPath("base_longitude").type(JsonFieldType.NUMBER).description("거점 경도").optional()
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("길드 정보"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("길드 ID"),
                            fieldWithPath("value.name").type(JsonFieldType.STRING).description("길드 이름"),
                            fieldWithPath("value.description").type(JsonFieldType.STRING).description("길드 설명").optional(),
                            fieldWithPath("value.master_id").type(JsonFieldType.STRING).description("마스터 ID"),
                            fieldWithPath("value.visibility").type(JsonFieldType.STRING).description("공개 여부"),
                            fieldWithPath("value.join_type").type(JsonFieldType.STRING).description("가입 방식 (OPEN, APPROVAL, INVITE_ONLY)").optional(),
                            fieldWithPath("value.max_members").type(JsonFieldType.NUMBER).description("최대 멤버 수"),
                            fieldWithPath("value.current_member_count").type(JsonFieldType.NUMBER).description("현재 멤버 수").optional(),
                            fieldWithPath("value.current_level").type(JsonFieldType.NUMBER).description("길드 레벨").optional(),
                            fieldWithPath("value.current_exp").type(JsonFieldType.NUMBER).description("현재 경험치").optional(),
                            fieldWithPath("value.total_exp").type(JsonFieldType.NUMBER).description("길드 누적 경험치").optional(),
                            fieldWithPath("value.category_id").type(JsonFieldType.NUMBER).description("카테고리 ID").optional(),
                            fieldWithPath("value.category_name").type(JsonFieldType.STRING).description("카테고리 이름").optional(),
                            fieldWithPath("value.category_icon").type(JsonFieldType.STRING).description("카테고리 아이콘").optional(),
                            fieldWithPath("value.base_address").type(JsonFieldType.STRING).description("거점 주소").optional(),
                            fieldWithPath("value.base_latitude").type(JsonFieldType.NUMBER).description("거점 위도").optional(),
                            fieldWithPath("value.base_longitude").type(JsonFieldType.NUMBER).description("거점 경도").optional(),
                            fieldWithPath("value.image_url").type(JsonFieldType.STRING).description("길드 이미지").optional(),
                            fieldWithPath("value.created_at").type(JsonFieldType.STRING).description("생성일시")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/guilds/{guildId}/members : 길드 멤버 목록")
    void getGuildMembersTest() throws Exception {
        // given
        List<GuildMemberResponse> members = List.of(
            GuildMemberResponse.builder()
                .id(1L)
                .guildId(1L)
                .userId(MOCK_USER_ID)
                .role(GuildMemberRole.MASTER)
                .status(GuildMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build(),
            GuildMemberResponse.builder()
                .id(2L)
                .guildId(1L)
                .userId("user-456")
                .role(GuildMemberRole.MEMBER)
                .status(GuildMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build()
        );

        when(guildService.getGuildMembers(anyLong(), anyString())).thenReturn(members);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/guilds/{guildId}/members", 1L)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드-07. 길드 멤버 목록",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild")
                        .description("길드 멤버 목록 조회 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value[]").type(JsonFieldType.ARRAY).description("멤버 목록"),
                            fieldWithPath("value[].id").type(JsonFieldType.NUMBER).description("멤버십 ID"),
                            fieldWithPath("value[].guild_id").type(JsonFieldType.NUMBER).description("길드 ID"),
                            fieldWithPath("value[].user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value[].role").type(JsonFieldType.STRING).description("역할 (MASTER, MANAGER, MEMBER)"),
                            fieldWithPath("value[].status").type(JsonFieldType.STRING).description("상태 (ACTIVE, INACTIVE, BANNED)"),
                            fieldWithPath("value[].joined_at").type(JsonFieldType.STRING).description("가입일시"),
                            fieldWithPath("value[].nickname").type(JsonFieldType.STRING).description("닉네임").optional(),
                            fieldWithPath("value[].profile_image_url").type(JsonFieldType.STRING).description("프로필 이미지 URL").optional(),
                            fieldWithPath("value[].user_level").type(JsonFieldType.NUMBER).description("사용자 레벨").optional(),
                            fieldWithPath("value[].equipped_title_name").type(JsonFieldType.STRING).description("장착된 칭호명").optional(),
                            fieldWithPath("value[].equipped_title_rarity").type(JsonFieldType.STRING).description("장착된 칭호 희귀도").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/guilds/{guildId}/join-requests : 가입 신청")
    void requestJoinTest() throws Exception {
        // given
        GuildJoinRequestResponse response = GuildJoinRequestResponse.builder()
            .id(1L)
            .guildId(1L)
            .requesterId(MOCK_USER_ID)
            .status(JoinRequestStatus.PENDING)
            .message("가입 신청합니다!")
            .createdAt(LocalDateTime.now())
            .build();

        when(guildService.requestJoin(anyLong(), anyString(), any())).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/guilds/{guildId}/join-requests", 1L)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"가입 신청합니다!\"}")
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드-08. 가입 신청",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild")
                        .description("공개 길드 가입 신청 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("가입 신청 정보"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("신청 ID"),
                            fieldWithPath("value.guild_id").type(JsonFieldType.NUMBER).description("길드 ID"),
                            fieldWithPath("value.guild_name").type(JsonFieldType.STRING).description("길드 이름").optional(),
                            fieldWithPath("value.requester_id").type(JsonFieldType.STRING).description("신청자 ID"),
                            fieldWithPath("value.status").type(JsonFieldType.STRING).description("신청 상태 (PENDING, APPROVED, REJECTED, CANCELLED)"),
                            fieldWithPath("value.message").type(JsonFieldType.STRING).description("신청 메시지").optional(),
                            fieldWithPath("value.reject_reason").type(JsonFieldType.STRING).description("거절 사유").optional(),
                            fieldWithPath("value.processed_by").type(JsonFieldType.STRING).description("처리자 ID").optional(),
                            fieldWithPath("value.created_at").type(JsonFieldType.STRING).description("신청일시"),
                            fieldWithPath("value.processed_at").type(JsonFieldType.STRING).description("처리일시").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/v1/guilds/{guildId}/members/me : 길드 탈퇴")
    void leaveGuildTest() throws Exception {
        // given
        doNothing().when(guildService).leaveGuild(anyLong(), anyString());

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.delete("/api/v1/guilds/{guildId}/members/me", 1L)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드-09. 길드 탈퇴",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild")
                        .description("길드 탈퇴 (마스터는 탈퇴 불가, JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID")
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
    @DisplayName("POST /api/v1/guilds/{guildId}/transfer-master : 마스터 이양")
    void transferMasterTest() throws Exception {
        // given
        doNothing().when(guildService).transferMaster(anyLong(), anyString(), anyString());

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/guilds/{guildId}/transfer-master", 1L)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"new_master_id\":\"new-master-123\"}")
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드-10. 마스터 이양",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild")
                        .description("길드 마스터 권한 이양 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID")
                        )
                        .requestFields(
                            fieldWithPath("new_master_id").type(JsonFieldType.STRING).description("새 마스터 사용자 ID")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/guilds/{guildId}/experience : 길드 경험치 정보")
    void getGuildExperienceTest() throws Exception {
        // given
        GuildExperienceResponse response = GuildExperienceResponse.builder()
            .guildId(1L)
            .guildName("테스트 길드")
            .currentLevel(5)
            .currentExp(500)
            .totalExp(1500)
            .requiredExpForNextLevel(600)
            .maxMembers(50)
            .levelTitle("성장하는 길드")
            .build();

        when(guildExperienceService.getGuildExperience(anyLong())).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/guilds/{guildId}/experience", 1L)
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드-11. 길드 경험치 정보",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild")
                        .description("길드 경험치/레벨 정보 조회")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("경험치 정보"),
                            fieldWithPath("value.guild_id").type(JsonFieldType.NUMBER).description("길드 ID"),
                            fieldWithPath("value.guild_name").type(JsonFieldType.STRING).description("길드 이름"),
                            fieldWithPath("value.current_level").type(JsonFieldType.NUMBER).description("현재 레벨"),
                            fieldWithPath("value.current_exp").type(JsonFieldType.NUMBER).description("현재 경험치"),
                            fieldWithPath("value.total_exp").type(JsonFieldType.NUMBER).description("누적 경험치"),
                            fieldWithPath("value.required_exp_for_next_level").type(JsonFieldType.NUMBER).description("다음 레벨 필요 경험치"),
                            fieldWithPath("value.max_members").type(JsonFieldType.NUMBER).description("최대 멤버 수"),
                            fieldWithPath("value.level_title").type(JsonFieldType.STRING).description("레벨 타이틀").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }
}
