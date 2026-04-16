package io.pinkspider.leveluptogethermvp.bffservice.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.SimpleType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.leveluptogethermvp.bffservice.api.dto.GuildDetailDataResponse;
import io.pinkspider.leveluptogethermvp.bffservice.api.dto.GuildDetailDataResponse.PostPageData;
import io.pinkspider.leveluptogethermvp.bffservice.api.dto.GuildListDataResponse;
import io.pinkspider.leveluptogethermvp.bffservice.api.dto.GuildListDataResponse.FeedPageData;
import io.pinkspider.leveluptogethermvp.bffservice.api.dto.GuildListDataResponse.GuildPageData;
import io.pinkspider.leveluptogethermvp.bffservice.api.dto.HomeDataResponse;
import io.pinkspider.leveluptogethermvp.bffservice.api.dto.MissionTodayDataResponse;
import io.pinkspider.leveluptogethermvp.bffservice.api.dto.UnifiedSearchResponse;
import io.pinkspider.leveluptogethermvp.bffservice.application.BffGuildService;
import io.pinkspider.leveluptogethermvp.bffservice.application.BffHomeService;
import io.pinkspider.leveluptogethermvp.bffservice.application.BffMissionService;
import io.pinkspider.leveluptogethermvp.bffservice.application.BffSearchService;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildMemberResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildPostListResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberRole;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberStatus;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildPostType;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionExecutionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionInterval;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionSource;
import io.pinkspider.global.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.noticeservice.api.dto.NoticeResponse;
import io.pinkspider.leveluptogethermvp.noticeservice.domain.enums.NoticeType;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.ActivityFeedResponse;
import io.pinkspider.leveluptogethermvp.userservice.home.api.dto.MvpGuildResponse;
import io.pinkspider.leveluptogethermvp.userservice.home.api.dto.TodayPlayerResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
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
import org.springframework.http.MediaType;
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@WebMvcTest(controllers = BffHomeController.class,
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
class BffHomeControllerTest {

    private static final String MOCK_USER_ID = "test-user-123";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockitoBean
    private BffHomeService bffHomeService;

    @MockitoBean
    private BffGuildService bffGuildService;

    @MockitoBean
    private BffMissionService bffMissionService;

    @MockitoBean
    private BffSearchService bffSearchService;

    // ========== Mock 데이터 생성 헬퍼 메서드 ==========

    private GuildResponse createMockGuildResponse() {
        return GuildResponse.builder()
            .id(1L)
            .name("테스트 길드")
            .description("테스트 길드 설명입니다.")
            .visibility(GuildVisibility.PUBLIC)
            .joinType(io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildJoinType.OPEN)
            .masterId("user-1")
            .maxMembers(50)
            .currentMemberCount(10)
            .imageUrl("https://example.com/guild.jpg")
            .currentLevel(5)
            .currentExp(1000)
            .totalExp(5000)
            .categoryId(1L)
            .categoryName("자기계발")
            .categoryIcon("📚")
            .baseAddress("서울시 강남구")
            .baseLatitude(37.5665)
            .baseLongitude(126.978)
            .createdAt(LocalDateTime.now())
            .build();
    }

    private GuildMemberResponse createMockGuildMemberResponse(String userId, GuildMemberRole role) {
        return GuildMemberResponse.builder()
            .id(1L)
            .guildId(1L)
            .userId(userId)
            .role(role)
            .status(GuildMemberStatus.ACTIVE)
            .joinedAt(LocalDateTime.now())
            .build();
    }

    private GuildPostListResponse createMockGuildPostListResponse() {
        return GuildPostListResponse.builder()
            .id(1L)
            .guildId(1L)
            .guildName("테스트 길드")
            .authorId("user-1")
            .authorNickname("테스터")
            .title("테스트 게시글")
            .postType(GuildPostType.NORMAL)
            .isPinned(false)
            .viewCount(10)
            .commentCount(5)
            .createdAt(LocalDateTime.now())
            .build();
    }

    private TodayPlayerResponse createMockTodayPlayerResponse(int rank) {
        return TodayPlayerResponse.builder()
            .userId("user-" + rank)
            .nickname("플레이어" + rank)
            .profileImageUrl("https://example.com/profile.jpg")
            .level(10)
            .title("모험가")
            .earnedExp(1000L)
            .rank(rank)
            .build();
    }

    private MvpGuildResponse createMockMvpGuildResponse(int rank) {
        return MvpGuildResponse.builder()
            .guildId((long) rank)
            .name("MVP 길드 " + rank)
            .imageUrl("https://example.com/guild.jpg")
            .level(5)
            .memberCount(10)
            .earnedExp(5000L)
            .rank(rank)
            .build();
    }

    private NoticeResponse createMockNoticeResponse() {
        return NoticeResponse.builder()
            .id(1L)
            .title("테스트 공지사항")
            .content("테스트 공지사항 내용입니다.")
            .noticeType(NoticeType.GENERAL)
            .noticeTypeName("일반")
            .priority(1)
            .startAt(LocalDateTime.now().minusDays(1))
            .endAt(LocalDateTime.now().plusDays(30))
            .isActive(true)
            .isPopup(false)
            .createdAt(LocalDateTime.now())
            .modifiedAt(LocalDateTime.now())
            .build();
    }

    private MissionResponse createMockMissionResponse() {
        return MissionResponse.builder()
            .id(1L)
            .title("테스트 미션")
            .description("테스트 미션 설명입니다.")
            .status(MissionStatus.IN_PROGRESS)
            .visibility(MissionVisibility.PUBLIC)
            .type(MissionType.PERSONAL)
            .source(MissionSource.USER)
            .creatorId("user-1")
            .currentParticipants(5)
            .missionInterval(MissionInterval.DAILY)
            .durationDays(30)
            .expPerCompletion(100)
            .bonusExpOnFullCompletion(500)
            .categoryName("운동")
            .isPinned(false)
            .createdAt(LocalDateTime.now())
            .build();
    }

    private MissionExecutionResponse createMockMissionExecutionResponse(ExecutionStatus status) {
        return MissionExecutionResponse.builder()
            .id(1L)
            .missionId(1L)
            .missionTitle("테스트 미션")
            .participantId(1L)
            .executionDate(LocalDate.now())
            .status(status)
            .startedAt(status != ExecutionStatus.PENDING ? LocalDateTime.now() : null)
            .completedAt(status == ExecutionStatus.COMPLETED ? LocalDateTime.now() : null)
            .build();
    }

    private ActivityFeedResponse createMockActivityFeedResponse() {
        return ActivityFeedResponse.builder()
            .id(1L)
            .userId("user-1")
            .userNickname("테스터")
            .userProfileImageUrl("https://example.com/profile.jpg")
            .activityType(io.pinkspider.leveluptogethermvp.feedservice.domain.enums.ActivityType.MISSION_COMPLETED)
            .activityTypeDisplayName("미션 완료")
            .category("MISSION")
            .title("미션 완료!")
            .description("테스트 미션을 완료했습니다.")
            .visibility(io.pinkspider.leveluptogethermvp.feedservice.domain.enums.FeedVisibility.PUBLIC)
            .likeCount(5)
            .commentCount(3)
            .likedByMe(false)
            .createdAt(LocalDateTime.now())
            .build();
    }

    // ========== 테스트 케이스 ==========

    @Test
    @DisplayName("GET /api/v1/bff/home : 홈 화면 BFF 데이터 조회")
    void getHomeDataTest() throws Exception {
        // given
        HomeDataResponse mockResponse = HomeDataResponse.builder()
            .feeds(HomeDataResponse.FeedPageData.builder()
                .content(Collections.emptyList())
                .page(0)
                .size(20)
                .totalElements(0)
                .totalPages(0)
                .build())
            .rankings(List.of(createMockTodayPlayerResponse(1)))
            .mvpGuilds(List.of(createMockMvpGuildResponse(1)))
            .categories(Collections.emptyList())
            .myGuilds(List.of(createMockGuildResponse()))
            .publicGuilds(HomeDataResponse.GuildPageData.builder()
                .content(List.of(createMockGuildResponse()))
                .page(0)
                .size(5)
                .totalElements(1)
                .totalPages(1)
                .build())
            .notices(List.of(createMockNoticeResponse()))
            .build();

        when(bffHomeService.getHomeData(anyString(), any(), any(), anyInt(), anyInt(), anyInt(), any(), any()))
            .thenReturn(mockResponse);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/bff/home")
                .with(user(MOCK_USER_ID))
                .param("feedPage", "0")
                .param("feedSize", "20")
                .param("publicGuildSize", "5")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("BFF-01. 홈 화면 데이터 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("BFF")
                        .description("홈 화면에 필요한 모든 데이터를 한 번에 조회 (피드, 랭킹, 카테고리, 길드, 공지사항)")
                        .queryParameters(
                            parameterWithName("feedPage").type(SimpleType.INTEGER)
                                .description("피드 페이지 번호 (기본: 0)").optional(),
                            parameterWithName("feedSize").type(SimpleType.INTEGER)
                                .description("피드 페이지 크기 (기본: 20)").optional(),
                            parameterWithName("publicGuildSize").type(SimpleType.INTEGER)
                                .description("공개 길드 조회 개수 (기본: 5)").optional(),
                            parameterWithName("feedSearchType").type(SimpleType.STRING)
                                .description("피드 필터 타입 (ALL, FRIENDS, GUILD, MINE)").optional()
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value.feeds").type(JsonFieldType.OBJECT).description("피드 목록 (페이징)"),
                            fieldWithPath("value.feeds.content").type(JsonFieldType.ARRAY).description("피드 목록"),
                            fieldWithPath("value.feeds.page").type(JsonFieldType.NUMBER).description("현재 페이지"),
                            fieldWithPath("value.feeds.size").type(JsonFieldType.NUMBER).description("페이지 크기"),
                            fieldWithPath("value.feeds.total_elements").type(JsonFieldType.NUMBER).description("전체 요소 수"),
                            fieldWithPath("value.feeds.total_pages").type(JsonFieldType.NUMBER).description("전체 페이지 수"),
                            fieldWithPath("value.rankings[]").type(JsonFieldType.ARRAY).description("오늘의 플레이어 랭킹"),
                            fieldWithPath("value.rankings[].user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value.rankings[].nickname").type(JsonFieldType.STRING).description("닉네임"),
                            fieldWithPath("value.rankings[].profile_image_url").type(JsonFieldType.STRING).description("프로필 이미지 URL").optional(),
                            fieldWithPath("value.rankings[].level").type(JsonFieldType.NUMBER).description("레벨"),
                            fieldWithPath("value.rankings[].title").type(JsonFieldType.STRING).description("칭호 (LEFT + RIGHT 조합)").optional(),
                            fieldWithPath("value.rankings[].title_rarity").type(JsonFieldType.STRING).description("칭호 등급 - 최고 등급 (COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, MYTHIC)").optional(),
                            fieldWithPath("value.rankings[].title_color_code").type(JsonFieldType.STRING).description("칭호 색상 코드").optional(),
                            fieldWithPath("value.rankings[].left_title").type(JsonFieldType.STRING).description("왼쪽 칭호 (형용사형)").optional(),
                            fieldWithPath("value.rankings[].left_title_rarity").type(JsonFieldType.STRING).description("왼쪽 칭호 등급").optional(),
                            fieldWithPath("value.rankings[].left_title_color_code").type(JsonFieldType.STRING).description("왼쪽 칭호 색상 코드").optional(),
                            fieldWithPath("value.rankings[].right_title").type(JsonFieldType.STRING).description("오른쪽 칭호 (명사형)").optional(),
                            fieldWithPath("value.rankings[].right_title_rarity").type(JsonFieldType.STRING).description("오른쪽 칭호 등급").optional(),
                            fieldWithPath("value.rankings[].right_title_color_code").type(JsonFieldType.STRING).description("오른쪽 칭호 색상 코드").optional(),
                            fieldWithPath("value.rankings[].earned_exp").type(JsonFieldType.NUMBER).description("획득 경험치"),
                            fieldWithPath("value.rankings[].rank").type(JsonFieldType.NUMBER).description("순위"),
                            fieldWithPath("value.mvp_guilds[]").type(JsonFieldType.ARRAY).description("MVP 길드 랭킹 (금일 EXP 획득 기준 상위 5개)"),
                            fieldWithPath("value.mvp_guilds[].guild_id").type(JsonFieldType.NUMBER).description("길드 ID"),
                            fieldWithPath("value.mvp_guilds[].name").type(JsonFieldType.STRING).description("길드명"),
                            fieldWithPath("value.mvp_guilds[].image_url").type(JsonFieldType.STRING).description("길드 이미지 URL").optional(),
                            fieldWithPath("value.mvp_guilds[].level").type(JsonFieldType.NUMBER).description("길드 레벨"),
                            fieldWithPath("value.mvp_guilds[].member_count").type(JsonFieldType.NUMBER).description("멤버 수"),
                            fieldWithPath("value.mvp_guilds[].earned_exp").type(JsonFieldType.NUMBER).description("금일 획득 경험치"),
                            fieldWithPath("value.mvp_guilds[].rank").type(JsonFieldType.NUMBER).description("순위"),
                            fieldWithPath("value.categories[]").type(JsonFieldType.ARRAY).description("미션 카테고리 목록"),
                            fieldWithPath("value.my_guilds[]").type(JsonFieldType.ARRAY).description("내 길드 목록"),
                            fieldWithPath("value.my_guilds[].id").type(JsonFieldType.NUMBER).description("길드 ID"),
                            fieldWithPath("value.my_guilds[].name").type(JsonFieldType.STRING).description("길드명"),
                            fieldWithPath("value.my_guilds[].description").type(JsonFieldType.STRING).description("길드 설명").optional(),
                            fieldWithPath("value.my_guilds[].visibility").type(JsonFieldType.STRING).description("공개 설정"),
                            fieldWithPath("value.my_guilds[].join_type").type(JsonFieldType.STRING).description("가입 방식 (OPEN, APPROVAL, INVITE_ONLY)").optional(),
                            fieldWithPath("value.my_guilds[].master_id").type(JsonFieldType.STRING).description("길드장 ID"),
                            fieldWithPath("value.my_guilds[].max_members").type(JsonFieldType.NUMBER).description("최대 인원"),
                            fieldWithPath("value.my_guilds[].current_member_count").type(JsonFieldType.NUMBER).description("현재 인원"),
                            fieldWithPath("value.my_guilds[].image_url").type(JsonFieldType.STRING).description("길드 이미지 URL").optional(),
                            fieldWithPath("value.my_guilds[].current_level").type(JsonFieldType.NUMBER).description("길드 레벨"),
                            fieldWithPath("value.my_guilds[].current_exp").type(JsonFieldType.NUMBER).description("현재 경험치"),
                            fieldWithPath("value.my_guilds[].total_exp").type(JsonFieldType.NUMBER).description("누적 경험치"),
                            fieldWithPath("value.my_guilds[].category_id").type(JsonFieldType.NUMBER).description("카테고리 ID").optional(),
                            fieldWithPath("value.my_guilds[].category_name").type(JsonFieldType.STRING).description("카테고리명").optional(),
                            fieldWithPath("value.my_guilds[].category_icon").type(JsonFieldType.STRING).description("카테고리 아이콘").optional(),
                            fieldWithPath("value.my_guilds[].base_address").type(JsonFieldType.STRING).description("거점 주소").optional(),
                            fieldWithPath("value.my_guilds[].base_latitude").type(JsonFieldType.NUMBER).description("거점 위도").optional(),
                            fieldWithPath("value.my_guilds[].base_longitude").type(JsonFieldType.NUMBER).description("거점 경도").optional(),
                            fieldWithPath("value.my_guilds[].created_at").type(JsonFieldType.STRING).description("생성일시"),
                            fieldWithPath("value.my_guilds[].is_under_review").type(JsonFieldType.BOOLEAN).description("신고 처리중 여부").optional(),
                            fieldWithPath("value.my_guilds[].is_pending_join_request").type(JsonFieldType.BOOLEAN).description("가입 신청 대기중 여부").optional(),
                            fieldWithPath("value.public_guilds").type(JsonFieldType.OBJECT).description("공개 길드 목록 (페이징)"),
                            fieldWithPath("value.public_guilds.content[]").type(JsonFieldType.ARRAY).description("공개 길드 목록"),
                            fieldWithPath("value.public_guilds.content[].id").type(JsonFieldType.NUMBER).description("길드 ID"),
                            fieldWithPath("value.public_guilds.content[].name").type(JsonFieldType.STRING).description("길드명"),
                            fieldWithPath("value.public_guilds.content[].description").type(JsonFieldType.STRING).description("길드 설명").optional(),
                            fieldWithPath("value.public_guilds.content[].visibility").type(JsonFieldType.STRING).description("공개 설정"),
                            fieldWithPath("value.public_guilds.content[].join_type").type(JsonFieldType.STRING).description("가입 방식 (OPEN, APPROVAL, INVITE_ONLY)").optional(),
                            fieldWithPath("value.public_guilds.content[].master_id").type(JsonFieldType.STRING).description("길드장 ID"),
                            fieldWithPath("value.public_guilds.content[].max_members").type(JsonFieldType.NUMBER).description("최대 인원"),
                            fieldWithPath("value.public_guilds.content[].current_member_count").type(JsonFieldType.NUMBER).description("현재 인원"),
                            fieldWithPath("value.public_guilds.content[].image_url").type(JsonFieldType.STRING).description("길드 이미지 URL").optional(),
                            fieldWithPath("value.public_guilds.content[].current_level").type(JsonFieldType.NUMBER).description("길드 레벨"),
                            fieldWithPath("value.public_guilds.content[].current_exp").type(JsonFieldType.NUMBER).description("현재 경험치"),
                            fieldWithPath("value.public_guilds.content[].total_exp").type(JsonFieldType.NUMBER).description("누적 경험치"),
                            fieldWithPath("value.public_guilds.content[].category_id").type(JsonFieldType.NUMBER).description("카테고리 ID").optional(),
                            fieldWithPath("value.public_guilds.content[].category_name").type(JsonFieldType.STRING).description("카테고리명").optional(),
                            fieldWithPath("value.public_guilds.content[].category_icon").type(JsonFieldType.STRING).description("카테고리 아이콘").optional(),
                            fieldWithPath("value.public_guilds.content[].base_address").type(JsonFieldType.STRING).description("거점 주소").optional(),
                            fieldWithPath("value.public_guilds.content[].base_latitude").type(JsonFieldType.NUMBER).description("거점 위도").optional(),
                            fieldWithPath("value.public_guilds.content[].base_longitude").type(JsonFieldType.NUMBER).description("거점 경도").optional(),
                            fieldWithPath("value.public_guilds.content[].created_at").type(JsonFieldType.STRING).description("생성일시"),
                            fieldWithPath("value.public_guilds.content[].is_under_review").type(JsonFieldType.BOOLEAN).description("신고 처리중 여부").optional(),
                            fieldWithPath("value.public_guilds.content[].is_pending_join_request").type(JsonFieldType.BOOLEAN).description("가입 신청 대기중 여부").optional(),
                            fieldWithPath("value.public_guilds.page").type(JsonFieldType.NUMBER).description("현재 페이지"),
                            fieldWithPath("value.public_guilds.size").type(JsonFieldType.NUMBER).description("페이지 크기"),
                            fieldWithPath("value.public_guilds.total_elements").type(JsonFieldType.NUMBER).description("전체 요소 수"),
                            fieldWithPath("value.public_guilds.total_pages").type(JsonFieldType.NUMBER).description("전체 페이지 수"),
                            fieldWithPath("value.notices[]").type(JsonFieldType.ARRAY).description("활성 공지사항 목록"),
                            fieldWithPath("value.notices[].id").type(JsonFieldType.NUMBER).description("공지사항 ID"),
                            fieldWithPath("value.notices[].title").type(JsonFieldType.STRING).description("제목"),
                            fieldWithPath("value.notices[].content").type(JsonFieldType.STRING).description("내용"),
                            fieldWithPath("value.notices[].notice_type").type(JsonFieldType.STRING).description("공지 유형"),
                            fieldWithPath("value.notices[].notice_type_name").type(JsonFieldType.STRING).description("공지 유형명"),
                            fieldWithPath("value.notices[].priority").type(JsonFieldType.NUMBER).description("우선순위"),
                            fieldWithPath("value.notices[].start_at").type(JsonFieldType.STRING).description("시작일시"),
                            fieldWithPath("value.notices[].end_at").type(JsonFieldType.STRING).description("종료일시"),
                            fieldWithPath("value.notices[].is_active").type(JsonFieldType.BOOLEAN).description("활성 여부"),
                            fieldWithPath("value.notices[].is_popup").type(JsonFieldType.BOOLEAN).description("팝업 여부"),
                            fieldWithPath("value.notices[].created_by").type(JsonFieldType.STRING).description("생성자").optional(),
                            fieldWithPath("value.notices[].modified_by").type(JsonFieldType.STRING).description("수정자").optional(),
                            fieldWithPath("value.notices[].created_at").type(JsonFieldType.STRING).description("생성일시"),
                            fieldWithPath("value.notices[].modified_at").type(JsonFieldType.STRING).description("수정일시"),
                            fieldWithPath("value.events").type(JsonFieldType.ARRAY).description("활성 이벤트 목록 (진행중 또는 예정된 이벤트)").optional(),
                            fieldWithPath("value.current_season").type(JsonFieldType.OBJECT).description("현재 시즌 정보 (null이면 활성 시즌 없음)").optional(),
                            fieldWithPath("value.season_mvp_players").type(JsonFieldType.ARRAY).description("시즌 MVP 유저 랭킹").optional(),
                            fieldWithPath("value.season_mvp_guilds").type(JsonFieldType.ARRAY).description("시즌 MVP 길드 랭킹").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/bff/guild/{guildId} : 길드 상세 BFF 데이터 조회")
    void getGuildDetailTest() throws Exception {
        // given
        GuildDetailDataResponse mockResponse = GuildDetailDataResponse.builder()
            .guild(createMockGuildResponse())
            .members(List.of(
                createMockGuildMemberResponse("user-1", GuildMemberRole.MASTER),
                createMockGuildMemberResponse("user-2", GuildMemberRole.MEMBER)
            ))
            .posts(PostPageData.builder()
                .content(List.of(createMockGuildPostListResponse()))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .build())
            .member(true)
            .memberRole("MASTER")
            .build();

        when(bffGuildService.getGuildDetail(anyLong(), anyString(), anyInt(), anyInt()))
            .thenReturn(mockResponse);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/bff/guild/{guildId}", 1L)
                .with(user(MOCK_USER_ID))
                .param("postPage", "0")
                .param("postSize", "20")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("BFF-02. 길드 상세 데이터 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("BFF")
                        .description("길드 상세 화면에 필요한 모든 데이터를 한 번에 조회 (길드 정보, 멤버, 게시글)")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.INTEGER)
                                .description("길드 ID")
                        )
                        .queryParameters(
                            parameterWithName("postPage").type(SimpleType.INTEGER)
                                .description("게시글 페이지 번호 (기본: 0)").optional(),
                            parameterWithName("postSize").type(SimpleType.INTEGER)
                                .description("게시글 페이지 크기 (기본: 20)").optional()
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value.guild").type(JsonFieldType.OBJECT).description("길드 상세 정보"),
                            fieldWithPath("value.guild.id").type(JsonFieldType.NUMBER).description("길드 ID"),
                            fieldWithPath("value.guild.name").type(JsonFieldType.STRING).description("길드명"),
                            fieldWithPath("value.guild.description").type(JsonFieldType.STRING).description("길드 설명").optional(),
                            fieldWithPath("value.guild.visibility").type(JsonFieldType.STRING).description("공개 설정"),
                            fieldWithPath("value.guild.join_type").type(JsonFieldType.STRING).description("가입 방식 (OPEN, APPROVAL, INVITE_ONLY)").optional(),
                            fieldWithPath("value.guild.master_id").type(JsonFieldType.STRING).description("길드장 ID"),
                            fieldWithPath("value.guild.max_members").type(JsonFieldType.NUMBER).description("최대 인원"),
                            fieldWithPath("value.guild.current_member_count").type(JsonFieldType.NUMBER).description("현재 인원"),
                            fieldWithPath("value.guild.image_url").type(JsonFieldType.STRING).description("길드 이미지 URL").optional(),
                            fieldWithPath("value.guild.current_level").type(JsonFieldType.NUMBER).description("길드 레벨"),
                            fieldWithPath("value.guild.current_exp").type(JsonFieldType.NUMBER).description("현재 경험치"),
                            fieldWithPath("value.guild.total_exp").type(JsonFieldType.NUMBER).description("누적 경험치"),
                            fieldWithPath("value.guild.category_id").type(JsonFieldType.NUMBER).description("카테고리 ID").optional(),
                            fieldWithPath("value.guild.category_name").type(JsonFieldType.STRING).description("카테고리명").optional(),
                            fieldWithPath("value.guild.category_icon").type(JsonFieldType.STRING).description("카테고리 아이콘").optional(),
                            fieldWithPath("value.guild.base_address").type(JsonFieldType.STRING).description("거점 주소").optional(),
                            fieldWithPath("value.guild.base_latitude").type(JsonFieldType.NUMBER).description("거점 위도").optional(),
                            fieldWithPath("value.guild.base_longitude").type(JsonFieldType.NUMBER).description("거점 경도").optional(),
                            fieldWithPath("value.guild.created_at").type(JsonFieldType.STRING).description("생성일시"),
                            fieldWithPath("value.guild.is_under_review").type(JsonFieldType.BOOLEAN).description("신고 처리중 여부").optional(),
                            fieldWithPath("value.guild.is_pending_join_request").type(JsonFieldType.BOOLEAN).description("가입 신청 대기중 여부").optional(),
                            fieldWithPath("value.members[]").type(JsonFieldType.ARRAY).description("길드 멤버 목록"),
                            fieldWithPath("value.members[].id").type(JsonFieldType.NUMBER).description("멤버 ID"),
                            fieldWithPath("value.members[].guild_id").type(JsonFieldType.NUMBER).description("길드 ID"),
                            fieldWithPath("value.members[].user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value.members[].role").type(JsonFieldType.STRING).description("역할 (MASTER, ADMIN, MEMBER)"),
                            fieldWithPath("value.members[].status").type(JsonFieldType.STRING).description("상태"),
                            fieldWithPath("value.members[].joined_at").type(JsonFieldType.STRING).description("가입일시"),
                            fieldWithPath("value.members[].nickname").type(JsonFieldType.STRING).description("닉네임").optional(),
                            fieldWithPath("value.members[].profile_image_url").type(JsonFieldType.STRING).description("프로필 이미지 URL").optional(),
                            fieldWithPath("value.members[].user_level").type(JsonFieldType.NUMBER).description("사용자 레벨").optional(),
                            fieldWithPath("value.members[].equipped_title_name").type(JsonFieldType.STRING).description("장착된 칭호명").optional(),
                            fieldWithPath("value.members[].equipped_title_rarity").type(JsonFieldType.STRING).description("장착된 칭호 희귀도").optional(),
                            fieldWithPath("value.members[].equipped_title_color_code").type(JsonFieldType.STRING).description("장착된 칭호 색상 코드").optional(),
                            fieldWithPath("value.members[].left_title_name").type(JsonFieldType.STRING).description("왼쪽 칭호명").optional(),
                            fieldWithPath("value.members[].left_title_rarity").type(JsonFieldType.STRING).description("왼쪽 칭호 희귀도").optional(),
                            fieldWithPath("value.members[].left_title_color_code").type(JsonFieldType.STRING).description("왼쪽 칭호 색상 코드").optional(),
                            fieldWithPath("value.members[].right_title_name").type(JsonFieldType.STRING).description("오른쪽 칭호명").optional(),
                            fieldWithPath("value.members[].right_title_rarity").type(JsonFieldType.STRING).description("오른쪽 칭호 희귀도").optional(),
                            fieldWithPath("value.members[].right_title_color_code").type(JsonFieldType.STRING).description("오른쪽 칭호 색상 코드").optional(),
                            fieldWithPath("value.posts").type(JsonFieldType.OBJECT).description("게시글 목록 (페이징)"),
                            fieldWithPath("value.posts.content[]").type(JsonFieldType.ARRAY).description("게시글 목록"),
                            fieldWithPath("value.posts.content[].id").type(JsonFieldType.NUMBER).description("게시글 ID"),
                            fieldWithPath("value.posts.content[].guild_id").type(JsonFieldType.NUMBER).description("길드 ID"),
                            fieldWithPath("value.posts.content[].guild_name").type(JsonFieldType.STRING).description("길드 이름"),
                            fieldWithPath("value.posts.content[].author_id").type(JsonFieldType.STRING).description("작성자 ID"),
                            fieldWithPath("value.posts.content[].author_nickname").type(JsonFieldType.STRING).description("작성자 닉네임"),
                            fieldWithPath("value.posts.content[].title").type(JsonFieldType.STRING).description("제목"),
                            fieldWithPath("value.posts.content[].post_type").type(JsonFieldType.STRING).description("게시글 유형 (NOTICE, NORMAL)"),
                            fieldWithPath("value.posts.content[].is_pinned").type(JsonFieldType.BOOLEAN).description("상단 고정 여부"),
                            fieldWithPath("value.posts.content[].view_count").type(JsonFieldType.NUMBER).description("조회수"),
                            fieldWithPath("value.posts.content[].comment_count").type(JsonFieldType.NUMBER).description("댓글수"),
                            fieldWithPath("value.posts.content[].created_at").type(JsonFieldType.STRING).description("작성일시"),
                            fieldWithPath("value.posts.content[].is_under_review").type(JsonFieldType.BOOLEAN).description("신고 처리중 여부").optional(),
                            fieldWithPath("value.posts.page").type(JsonFieldType.NUMBER).description("현재 페이지"),
                            fieldWithPath("value.posts.size").type(JsonFieldType.NUMBER).description("페이지 크기"),
                            fieldWithPath("value.posts.total_elements").type(JsonFieldType.NUMBER).description("전체 요소 수"),
                            fieldWithPath("value.posts.total_pages").type(JsonFieldType.NUMBER).description("전체 페이지 수"),
                            fieldWithPath("value.is_member").type(JsonFieldType.BOOLEAN).description("현재 사용자의 멤버 여부"),
                            fieldWithPath("value.member_role").type(JsonFieldType.STRING).description("현재 사용자의 역할").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/bff/guild/list : 길드 목록 BFF 데이터 조회")
    void getGuildListTest() throws Exception {
        // given
        GuildListDataResponse mockResponse = GuildListDataResponse.builder()
            .myGuilds(List.of(createMockGuildResponse()))
            .recommendedGuilds(GuildPageData.builder()
                .content(List.of(createMockGuildResponse()))
                .page(0)
                .size(10)
                .totalElements(1)
                .totalPages(1)
                .build())
            .guildNotices(List.of(createMockGuildPostListResponse()))
            .guildActivityFeeds(FeedPageData.builder()
                .content(List.of(createMockActivityFeedResponse()))
                .page(0)
                .size(10)
                .totalElements(1)
                .totalPages(1)
                .build())
            .guildJoined(true)
            .build();

        when(bffGuildService.getGuildList(anyString(), anyInt(), anyInt()))
            .thenReturn(mockResponse);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/bff/guild/list")
                .with(user(MOCK_USER_ID))
                .param("recommendedGuildSize", "10")
                .param("activityFeedSize", "10")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("BFF-03. 길드 목록 데이터 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("BFF")
                        .description("길드 목록 화면에 필요한 모든 데이터를 한 번에 조회 (내 길드, 추천 길드, 공지사항, 활동 피드)")
                        .queryParameters(
                            parameterWithName("recommendedGuildSize").type(SimpleType.INTEGER)
                                .description("추천 길드 조회 개수 (기본: 10)").optional(),
                            parameterWithName("activityFeedSize").type(SimpleType.INTEGER)
                                .description("활동 피드 조회 개수 (기본: 10)").optional()
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value.my_guilds[]").type(JsonFieldType.ARRAY).description("내 길드 목록"),
                            fieldWithPath("value.my_guilds[].id").type(JsonFieldType.NUMBER).description("길드 ID"),
                            fieldWithPath("value.my_guilds[].name").type(JsonFieldType.STRING).description("길드명"),
                            fieldWithPath("value.my_guilds[].description").type(JsonFieldType.STRING).description("길드 설명").optional(),
                            fieldWithPath("value.my_guilds[].visibility").type(JsonFieldType.STRING).description("공개 설정"),
                            fieldWithPath("value.my_guilds[].join_type").type(JsonFieldType.STRING).description("가입 방식 (OPEN, APPROVAL, INVITE_ONLY)").optional(),
                            fieldWithPath("value.my_guilds[].master_id").type(JsonFieldType.STRING).description("길드장 ID"),
                            fieldWithPath("value.my_guilds[].max_members").type(JsonFieldType.NUMBER).description("최대 인원"),
                            fieldWithPath("value.my_guilds[].current_member_count").type(JsonFieldType.NUMBER).description("현재 인원"),
                            fieldWithPath("value.my_guilds[].image_url").type(JsonFieldType.STRING).description("길드 이미지 URL").optional(),
                            fieldWithPath("value.my_guilds[].current_level").type(JsonFieldType.NUMBER).description("길드 레벨"),
                            fieldWithPath("value.my_guilds[].current_exp").type(JsonFieldType.NUMBER).description("현재 경험치"),
                            fieldWithPath("value.my_guilds[].total_exp").type(JsonFieldType.NUMBER).description("누적 경험치"),
                            fieldWithPath("value.my_guilds[].category_id").type(JsonFieldType.NUMBER).description("카테고리 ID").optional(),
                            fieldWithPath("value.my_guilds[].category_name").type(JsonFieldType.STRING).description("카테고리명").optional(),
                            fieldWithPath("value.my_guilds[].category_icon").type(JsonFieldType.STRING).description("카테고리 아이콘").optional(),
                            fieldWithPath("value.my_guilds[].base_address").type(JsonFieldType.STRING).description("거점 주소").optional(),
                            fieldWithPath("value.my_guilds[].base_latitude").type(JsonFieldType.NUMBER).description("거점 위도").optional(),
                            fieldWithPath("value.my_guilds[].base_longitude").type(JsonFieldType.NUMBER).description("거점 경도").optional(),
                            fieldWithPath("value.my_guilds[].created_at").type(JsonFieldType.STRING).description("생성일시"),
                            fieldWithPath("value.my_guilds[].is_under_review").type(JsonFieldType.BOOLEAN).description("신고 처리중 여부").optional(),
                            fieldWithPath("value.my_guilds[].is_pending_join_request").type(JsonFieldType.BOOLEAN).description("가입 신청 대기중 여부").optional(),
                            fieldWithPath("value.recommended_guilds").type(JsonFieldType.OBJECT).description("추천 길드 목록 (페이징)"),
                            fieldWithPath("value.recommended_guilds.content[]").type(JsonFieldType.ARRAY).description("추천 길드 목록"),
                            fieldWithPath("value.recommended_guilds.content[].id").type(JsonFieldType.NUMBER).description("길드 ID"),
                            fieldWithPath("value.recommended_guilds.content[].name").type(JsonFieldType.STRING).description("길드명"),
                            fieldWithPath("value.recommended_guilds.content[].description").type(JsonFieldType.STRING).description("길드 설명").optional(),
                            fieldWithPath("value.recommended_guilds.content[].visibility").type(JsonFieldType.STRING).description("공개 설정"),
                            fieldWithPath("value.recommended_guilds.content[].join_type").type(JsonFieldType.STRING).description("가입 방식 (OPEN, APPROVAL, INVITE_ONLY)").optional(),
                            fieldWithPath("value.recommended_guilds.content[].master_id").type(JsonFieldType.STRING).description("길드장 ID"),
                            fieldWithPath("value.recommended_guilds.content[].max_members").type(JsonFieldType.NUMBER).description("최대 인원"),
                            fieldWithPath("value.recommended_guilds.content[].current_member_count").type(JsonFieldType.NUMBER).description("현재 인원"),
                            fieldWithPath("value.recommended_guilds.content[].image_url").type(JsonFieldType.STRING).description("길드 이미지 URL").optional(),
                            fieldWithPath("value.recommended_guilds.content[].current_level").type(JsonFieldType.NUMBER).description("길드 레벨"),
                            fieldWithPath("value.recommended_guilds.content[].current_exp").type(JsonFieldType.NUMBER).description("현재 경험치"),
                            fieldWithPath("value.recommended_guilds.content[].total_exp").type(JsonFieldType.NUMBER).description("누적 경험치"),
                            fieldWithPath("value.recommended_guilds.content[].category_id").type(JsonFieldType.NUMBER).description("카테고리 ID").optional(),
                            fieldWithPath("value.recommended_guilds.content[].category_name").type(JsonFieldType.STRING).description("카테고리명").optional(),
                            fieldWithPath("value.recommended_guilds.content[].category_icon").type(JsonFieldType.STRING).description("카테고리 아이콘").optional(),
                            fieldWithPath("value.recommended_guilds.content[].base_address").type(JsonFieldType.STRING).description("거점 주소").optional(),
                            fieldWithPath("value.recommended_guilds.content[].base_latitude").type(JsonFieldType.NUMBER).description("거점 위도").optional(),
                            fieldWithPath("value.recommended_guilds.content[].base_longitude").type(JsonFieldType.NUMBER).description("거점 경도").optional(),
                            fieldWithPath("value.recommended_guilds.content[].created_at").type(JsonFieldType.STRING).description("생성일시"),
                            fieldWithPath("value.recommended_guilds.content[].is_under_review").type(JsonFieldType.BOOLEAN).description("신고 처리중 여부").optional(),
                            fieldWithPath("value.recommended_guilds.content[].is_pending_join_request").type(JsonFieldType.BOOLEAN).description("가입 신청 대기중 여부").optional(),
                            fieldWithPath("value.recommended_guilds.page").type(JsonFieldType.NUMBER).description("현재 페이지"),
                            fieldWithPath("value.recommended_guilds.size").type(JsonFieldType.NUMBER).description("페이지 크기"),
                            fieldWithPath("value.recommended_guilds.total_elements").type(JsonFieldType.NUMBER).description("전체 요소 수"),
                            fieldWithPath("value.recommended_guilds.total_pages").type(JsonFieldType.NUMBER).description("전체 페이지 수"),
                            fieldWithPath("value.guild_notices[]").type(JsonFieldType.ARRAY).description("길드 공지사항 목록"),
                            fieldWithPath("value.guild_notices[].id").type(JsonFieldType.NUMBER).description("게시글 ID"),
                            fieldWithPath("value.guild_notices[].guild_id").type(JsonFieldType.NUMBER).description("길드 ID"),
                            fieldWithPath("value.guild_notices[].guild_name").type(JsonFieldType.STRING).description("길드 이름"),
                            fieldWithPath("value.guild_notices[].author_id").type(JsonFieldType.STRING).description("작성자 ID"),
                            fieldWithPath("value.guild_notices[].author_nickname").type(JsonFieldType.STRING).description("작성자 닉네임"),
                            fieldWithPath("value.guild_notices[].title").type(JsonFieldType.STRING).description("제목"),
                            fieldWithPath("value.guild_notices[].post_type").type(JsonFieldType.STRING).description("게시글 유형"),
                            fieldWithPath("value.guild_notices[].is_pinned").type(JsonFieldType.BOOLEAN).description("상단 고정 여부"),
                            fieldWithPath("value.guild_notices[].view_count").type(JsonFieldType.NUMBER).description("조회수"),
                            fieldWithPath("value.guild_notices[].comment_count").type(JsonFieldType.NUMBER).description("댓글수"),
                            fieldWithPath("value.guild_notices[].created_at").type(JsonFieldType.STRING).description("작성일시"),
                            fieldWithPath("value.guild_notices[].is_under_review").type(JsonFieldType.BOOLEAN).description("신고 처리중 여부").optional(),
                            fieldWithPath("value.guild_activity_feeds").type(JsonFieldType.OBJECT).description("길드 활동 피드 (페이징)"),
                            fieldWithPath("value.guild_activity_feeds.content[]").type(JsonFieldType.ARRAY).description("활동 피드 목록"),
                            fieldWithPath("value.guild_activity_feeds.content[].id").type(JsonFieldType.NUMBER).description("피드 ID"),
                            fieldWithPath("value.guild_activity_feeds.content[].user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value.guild_activity_feeds.content[].user_nickname").type(JsonFieldType.STRING).description("사용자 닉네임"),
                            fieldWithPath("value.guild_activity_feeds.content[].user_profile_image_url").type(JsonFieldType.STRING).description("프로필 이미지 URL").optional(),
                            fieldWithPath("value.guild_activity_feeds.content[].user_level").type(JsonFieldType.NUMBER).description("사용자 레벨").optional(),
                            fieldWithPath("value.guild_activity_feeds.content[].user_title").type(JsonFieldType.STRING).description("사용자 칭호").optional(),
                            fieldWithPath("value.guild_activity_feeds.content[].user_title_rarity").type(JsonFieldType.STRING).description("칭호 등급 (COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, MYTHIC)").optional(),
                            fieldWithPath("value.guild_activity_feeds.content[].user_title_color_code").type(JsonFieldType.STRING).description("칭호 색상 코드").optional(),
                            fieldWithPath("value.guild_activity_feeds.content[].user_left_title").type(JsonFieldType.STRING).description("사용자 좌측 칭호").optional(),
                            fieldWithPath("value.guild_activity_feeds.content[].user_left_title_rarity").type(JsonFieldType.STRING).description("사용자 좌측 칭호 등급").optional(),
                            fieldWithPath("value.guild_activity_feeds.content[].user_right_title").type(JsonFieldType.STRING).description("사용자 우측 칭호").optional(),
                            fieldWithPath("value.guild_activity_feeds.content[].user_right_title_rarity").type(JsonFieldType.STRING).description("사용자 우측 칭호 등급").optional(),
                            fieldWithPath("value.guild_activity_feeds.content[].activity_type").type(JsonFieldType.STRING).description("활동 유형"),
                            fieldWithPath("value.guild_activity_feeds.content[].activity_type_display_name").type(JsonFieldType.STRING).description("활동 유형 표시명"),
                            fieldWithPath("value.guild_activity_feeds.content[].category").type(JsonFieldType.STRING).description("활동 카테고리"),
                            fieldWithPath("value.guild_activity_feeds.content[].title").type(JsonFieldType.STRING).description("제목"),
                            fieldWithPath("value.guild_activity_feeds.content[].description").type(JsonFieldType.STRING).description("설명").optional(),
                            fieldWithPath("value.guild_activity_feeds.content[].reference_type").type(JsonFieldType.STRING).description("참조 유형").optional(),
                            fieldWithPath("value.guild_activity_feeds.content[].reference_id").type(JsonFieldType.NUMBER).description("참조 ID").optional(),
                            fieldWithPath("value.guild_activity_feeds.content[].reference_name").type(JsonFieldType.STRING).description("참조명").optional(),
                            fieldWithPath("value.guild_activity_feeds.content[].visibility").type(JsonFieldType.STRING).description("공개 설정"),
                            fieldWithPath("value.guild_activity_feeds.content[].guild_id").type(JsonFieldType.NUMBER).description("길드 ID").optional(),
                            fieldWithPath("value.guild_activity_feeds.content[].image_url").type(JsonFieldType.STRING).description("이미지 URL").optional(),
                            fieldWithPath("value.guild_activity_feeds.content[].icon_url").type(JsonFieldType.STRING).description("아이콘 URL").optional(),
                            fieldWithPath("value.guild_activity_feeds.content[].like_count").type(JsonFieldType.NUMBER).description("좋아요 수"),
                            fieldWithPath("value.guild_activity_feeds.content[].comment_count").type(JsonFieldType.NUMBER).description("댓글 수"),
                            fieldWithPath("value.guild_activity_feeds.content[].liked_by_me").type(JsonFieldType.BOOLEAN).description("좋아요 여부"),
                            fieldWithPath("value.guild_activity_feeds.content[].is_my_feed").type(JsonFieldType.BOOLEAN).description("내가 작성한 피드인지"),
                            fieldWithPath("value.guild_activity_feeds.content[].created_at").type(JsonFieldType.STRING).description("생성일시"),
                            fieldWithPath("value.guild_activity_feeds.content[].execution_id").type(JsonFieldType.NUMBER).description("미션 실행 ID").optional(),
                            fieldWithPath("value.guild_activity_feeds.content[].duration_minutes").type(JsonFieldType.NUMBER).description("수행 시간(분)").optional(),
                            fieldWithPath("value.guild_activity_feeds.content[].exp_earned").type(JsonFieldType.NUMBER).description("획득 경험치").optional(),
                            fieldWithPath("value.guild_activity_feeds.content[].category_id").type(JsonFieldType.NUMBER).description("카테고리 ID").optional(),
                            fieldWithPath("value.guild_activity_feeds.content[].translation").type(JsonFieldType.OBJECT).description("번역 정보").optional(),
                            fieldWithPath("value.guild_activity_feeds.content[].is_under_review").type(JsonFieldType.BOOLEAN).description("신고 처리중 여부").optional(),
                            fieldWithPath("value.guild_activity_feeds.page").type(JsonFieldType.NUMBER).description("현재 페이지"),
                            fieldWithPath("value.guild_activity_feeds.size").type(JsonFieldType.NUMBER).description("페이지 크기"),
                            fieldWithPath("value.guild_activity_feeds.total_elements").type(JsonFieldType.NUMBER).description("전체 요소 수"),
                            fieldWithPath("value.guild_activity_feeds.total_pages").type(JsonFieldType.NUMBER).description("전체 페이지 수"),
                            fieldWithPath("value.has_guild").type(JsonFieldType.BOOLEAN).description("길드 가입 여부")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/bff/mission/today : 오늘의 미션 BFF 데이터 조회")
    void getTodayMissionsTest() throws Exception {
        // given
        MissionTodayDataResponse mockResponse = MissionTodayDataResponse.builder()
            .myMissions(List.of(createMockMissionResponse()))
            .todayExecutions(List.of(
                createMockMissionExecutionResponse(ExecutionStatus.COMPLETED),
                createMockMissionExecutionResponse(ExecutionStatus.PENDING)
            ))
            .completedPinnedInstances(List.of())
            .completedCount(1)
            .inProgressCount(0)
            .pendingCount(1)
            .build();

        when(bffMissionService.getTodayMissions(anyString()))
            .thenReturn(mockResponse);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/bff/mission/today")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("BFF-04. 오늘의 미션 데이터 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("BFF")
                        .description("오늘의 미션 화면에 필요한 모든 데이터를 한 번에 조회 (내 미션, 오늘 실행 현황, 통계)")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value.my_missions[]").type(JsonFieldType.ARRAY).description("내 미션 목록"),
                            fieldWithPath("value.my_missions[].id").type(JsonFieldType.NUMBER).description("미션 ID"),
                            fieldWithPath("value.my_missions[].title").type(JsonFieldType.STRING).description("미션 제목"),
                            fieldWithPath("value.my_missions[].title_en").type(JsonFieldType.STRING).description("미션 제목 (영어)").optional(),
                            fieldWithPath("value.my_missions[].title_ar").type(JsonFieldType.STRING).description("미션 제목 (아랍어)").optional(),
                            fieldWithPath("value.my_missions[].title_ja").type(JsonFieldType.STRING).description("미션 제목 (일본어)").optional(),
                            fieldWithPath("value.my_missions[].description").type(JsonFieldType.STRING).description("미션 설명").optional(),
                            fieldWithPath("value.my_missions[].description_en").type(JsonFieldType.STRING).description("미션 설명 (영어)").optional(),
                            fieldWithPath("value.my_missions[].description_ar").type(JsonFieldType.STRING).description("미션 설명 (아랍어)").optional(),
                            fieldWithPath("value.my_missions[].description_ja").type(JsonFieldType.STRING).description("미션 설명 (일본어)").optional(),
                            fieldWithPath("value.my_missions[].status").type(JsonFieldType.STRING).description("미션 상태"),
                            fieldWithPath("value.my_missions[].visibility").type(JsonFieldType.STRING).description("공개 설정"),
                            fieldWithPath("value.my_missions[].type").type(JsonFieldType.STRING).description("미션 유형"),
                            fieldWithPath("value.my_missions[].source").type(JsonFieldType.STRING).description("미션 출처"),
                            fieldWithPath("value.my_missions[].participation_type").type(JsonFieldType.STRING).description("참여 유형").optional(),
                            fieldWithPath("value.my_missions[].is_customizable").type(JsonFieldType.BOOLEAN).description("커스터마이징 가능 여부").optional(),
                            fieldWithPath("value.my_missions[].is_pinned").type(JsonFieldType.BOOLEAN).description("고정 미션 여부").optional(),
                            fieldWithPath("value.my_missions[].execution_mode").type(JsonFieldType.STRING).description("수행 방식 (TIMED, SIMPLE)").optional(),
                            fieldWithPath("value.my_missions[].creator_id").type(JsonFieldType.STRING).description("생성자 ID"),
                            fieldWithPath("value.my_missions[].guild_id").type(JsonFieldType.STRING).description("길드 ID").optional(),
                            fieldWithPath("value.my_missions[].guild_name").type(JsonFieldType.STRING).description("길드 이름").optional(),
                            fieldWithPath("value.my_missions[].max_participants").type(JsonFieldType.NUMBER).description("최대 참여자 수").optional(),
                            fieldWithPath("value.my_missions[].current_participants").type(JsonFieldType.NUMBER).description("현재 참여자 수"),
                            fieldWithPath("value.my_missions[].start_at").type(JsonFieldType.STRING).description("시작일시").optional(),
                            fieldWithPath("value.my_missions[].end_at").type(JsonFieldType.STRING).description("종료일시").optional(),
                            fieldWithPath("value.my_missions[].mission_interval").type(JsonFieldType.STRING).description("미션 주기"),
                            fieldWithPath("value.my_missions[].duration_days").type(JsonFieldType.NUMBER).description("기간 (일)"),
                            fieldWithPath("value.my_missions[].duration_minutes").type(JsonFieldType.NUMBER).description("미션 수행 시간 (분)").optional(),
                            fieldWithPath("value.my_missions[].exp_per_completion").type(JsonFieldType.NUMBER).description("완료시 경험치"),
                            fieldWithPath("value.my_missions[].bonus_exp_on_full_completion").type(JsonFieldType.NUMBER).description("전체 완료시 보너스 경험치"),
                            fieldWithPath("value.my_missions[].target_duration_minutes").type(JsonFieldType.NUMBER).description("목표 수행 시간 (분)").optional(),
                            fieldWithPath("value.my_missions[].daily_execution_limit").type(JsonFieldType.NUMBER).description("일일 수행 제한").optional(),
                            fieldWithPath("value.my_missions[].category_id").type(JsonFieldType.NUMBER).description("카테고리 ID").optional(),
                            fieldWithPath("value.my_missions[].category_name").type(JsonFieldType.STRING).description("카테고리명").optional(),
                            fieldWithPath("value.my_missions[].created_at").type(JsonFieldType.STRING).description("생성일시"),
                            fieldWithPath("value.my_missions[].modified_at").type(JsonFieldType.STRING).description("수정일시").optional(),
                            fieldWithPath("value.my_missions[].is_under_review").type(JsonFieldType.BOOLEAN).description("신고 처리중 여부").optional(),
                            fieldWithPath("value.today_executions[]").type(JsonFieldType.ARRAY).description("오늘의 미션 실행 목록"),
                            fieldWithPath("value.today_executions[].id").type(JsonFieldType.NUMBER).description("실행 ID"),
                            fieldWithPath("value.today_executions[].participant_id").type(JsonFieldType.NUMBER).description("참여자 ID"),
                            fieldWithPath("value.today_executions[].mission_id").type(JsonFieldType.NUMBER).description("미션 ID"),
                            fieldWithPath("value.today_executions[].mission_title").type(JsonFieldType.STRING).description("미션 제목"),
                            fieldWithPath("value.today_executions[].mission_category_name").type(JsonFieldType.STRING).description("미션 카테고리명").optional(),
                            fieldWithPath("value.today_executions[].mission_type").type(JsonFieldType.STRING).description("미션 타입").optional(),
                            fieldWithPath("value.today_executions[].user_id").type(JsonFieldType.STRING).description("사용자 ID").optional(),
                            fieldWithPath("value.today_executions[].execution_date").type(JsonFieldType.STRING).description("실행 날짜"),
                            fieldWithPath("value.today_executions[].status").type(JsonFieldType.STRING).description("실행 상태 (PENDING, IN_PROGRESS, COMPLETED, SKIPPED)"),
                            fieldWithPath("value.today_executions[].started_at").type(JsonFieldType.STRING).description("시작일시").optional(),
                            fieldWithPath("value.today_executions[].completed_at").type(JsonFieldType.STRING).description("완료일시").optional(),
                            fieldWithPath("value.today_executions[].duration_minutes").type(JsonFieldType.NUMBER).description("수행 시간 (분)").optional(),
                            fieldWithPath("value.today_executions[].exp_earned").type(JsonFieldType.NUMBER).description("획득 경험치").optional(),
                            fieldWithPath("value.today_executions[].note").type(JsonFieldType.STRING).description("메모").optional(),
                            fieldWithPath("value.today_executions[].image_url").type(JsonFieldType.STRING).description("이미지 URL").optional(),
                            fieldWithPath("value.today_executions[].is_shared_to_feed").type(JsonFieldType.BOOLEAN).description("피드 공유 여부").optional(),
                            fieldWithPath("value.today_executions[].created_at").type(JsonFieldType.STRING).description("생성일시").optional(),
                            fieldWithPath("value.today_executions[].is_auto_completed").type(JsonFieldType.BOOLEAN).description("자동 완료 여부").optional(),
                            fieldWithPath("value.completed_pinned_instances[]").type(JsonFieldType.ARRAY).description("오늘 완료된 고정 미션 인스턴스 목록 (오늘 수행 기록용)").optional(),
                            fieldWithPath("value.completed_pinned_instances[].id").type(JsonFieldType.NUMBER).description("인스턴스 ID").optional(),
                            fieldWithPath("value.completed_pinned_instances[].participant_id").type(JsonFieldType.NUMBER).description("참여자 ID").optional(),
                            fieldWithPath("value.completed_pinned_instances[].mission_id").type(JsonFieldType.NUMBER).description("미션 ID").optional(),
                            fieldWithPath("value.completed_pinned_instances[].mission_title").type(JsonFieldType.STRING).description("미션 제목").optional(),
                            fieldWithPath("value.completed_pinned_instances[].mission_category_name").type(JsonFieldType.STRING).description("미션 카테고리명").optional(),
                            fieldWithPath("value.completed_pinned_instances[].mission_type").type(JsonFieldType.STRING).description("미션 타입").optional(),
                            fieldWithPath("value.completed_pinned_instances[].user_id").type(JsonFieldType.STRING).description("사용자 ID").optional(),
                            fieldWithPath("value.completed_pinned_instances[].execution_date").type(JsonFieldType.STRING).description("인스턴스 날짜").optional(),
                            fieldWithPath("value.completed_pinned_instances[].status").type(JsonFieldType.STRING).description("실행 상태 (COMPLETED)").optional(),
                            fieldWithPath("value.completed_pinned_instances[].started_at").type(JsonFieldType.STRING).description("시작일시").optional(),
                            fieldWithPath("value.completed_pinned_instances[].completed_at").type(JsonFieldType.STRING).description("완료일시").optional(),
                            fieldWithPath("value.completed_pinned_instances[].duration_minutes").type(JsonFieldType.NUMBER).description("수행 시간 (분)").optional(),
                            fieldWithPath("value.completed_pinned_instances[].exp_earned").type(JsonFieldType.NUMBER).description("획득 경험치").optional(),
                            fieldWithPath("value.completed_pinned_instances[].note").type(JsonFieldType.STRING).description("메모").optional(),
                            fieldWithPath("value.completed_pinned_instances[].image_url").type(JsonFieldType.STRING).description("이미지 URL").optional(),
                            fieldWithPath("value.completed_pinned_instances[].is_shared_to_feed").type(JsonFieldType.BOOLEAN).description("피드 공유 여부").optional(),
                            fieldWithPath("value.completed_pinned_instances[].created_at").type(JsonFieldType.STRING).description("생성일시").optional(),
                            fieldWithPath("value.completed_pinned_instances[].is_auto_completed").type(JsonFieldType.BOOLEAN).description("자동 완료 여부").optional(),
                            fieldWithPath("value.completed_count").type(JsonFieldType.NUMBER).description("오늘 완료한 미션 수 (일반 + 고정)"),
                            fieldWithPath("value.in_progress_count").type(JsonFieldType.NUMBER).description("진행 중인 미션 수"),
                            fieldWithPath("value.pending_count").type(JsonFieldType.NUMBER).description("미완료 미션 수")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/bff/home/search : 통합 검색")
    void searchTest() throws Exception {
        // given
        UnifiedSearchResponse mockResponse = UnifiedSearchResponse.builder()
            .feeds(List.of(UnifiedSearchResponse.FeedSearchItem.builder()
                .id(1L)
                .title("미션 완료!")
                .userNickname("테스터")
                .build()))
            .missions(List.of(UnifiedSearchResponse.MissionSearchItem.builder()
                .id(1L)
                .title("테스트 미션")
                .categoryName("자기계발")
                .build()))
            .users(List.of(UnifiedSearchResponse.UserSearchItem.builder()
                .id("user-1")
                .nickname("테스터")
                .profileImageUrl("https://example.com/profile.jpg")
                .build()))
            .guilds(List.of(UnifiedSearchResponse.GuildSearchItem.builder()
                .id("1")
                .name("테스트 길드")
                .memberCount(10)
                .build()))
            .totalCount(4)
            .build();

        when(bffSearchService.search(anyString(), anyInt()))
            .thenReturn(mockResponse);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/bff/search")
                .with(user(MOCK_USER_ID))
                .param("keyword", "테스트")
                .param("limit", "5")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("BFF-05. 통합 검색",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("BFF")
                        .description("피드, 미션, 사용자, 길드를 통합 검색")
                        .queryParameters(
                            parameterWithName("keyword").type(SimpleType.STRING)
                                .description("검색 키워드 (2자 이상)"),
                            parameterWithName("limit").type(SimpleType.INTEGER)
                                .description("각 타입별 최대 조회 개수 (기본: 5)").optional()
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value.feeds[]").type(JsonFieldType.ARRAY).description("피드 검색 결과"),
                            fieldWithPath("value.feeds[].id").type(JsonFieldType.NUMBER).description("피드 ID"),
                            fieldWithPath("value.feeds[].title").type(JsonFieldType.STRING).description("피드 제목"),
                            fieldWithPath("value.feeds[].description").type(JsonFieldType.STRING).description("피드 설명").optional(),
                            fieldWithPath("value.feeds[].user_nickname").type(JsonFieldType.STRING).description("작성자 닉네임"),
                            fieldWithPath("value.feeds[].image_url").type(JsonFieldType.STRING).description("피드 이미지 URL").optional(),
                            fieldWithPath("value.feeds[].created_at").type(JsonFieldType.STRING).description("생성일시").optional(),
                            fieldWithPath("value.missions[]").type(JsonFieldType.ARRAY).description("미션 검색 결과"),
                            fieldWithPath("value.missions[].id").type(JsonFieldType.NUMBER).description("미션 ID"),
                            fieldWithPath("value.missions[].title").type(JsonFieldType.STRING).description("미션 제목"),
                            fieldWithPath("value.missions[].description").type(JsonFieldType.STRING).description("미션 설명").optional(),
                            fieldWithPath("value.missions[].category_name").type(JsonFieldType.STRING).description("카테고리명").optional(),
                            fieldWithPath("value.missions[].category_id").type(JsonFieldType.NUMBER).description("카테고리 ID").optional(),
                            fieldWithPath("value.users[]").type(JsonFieldType.ARRAY).description("사용자 검색 결과"),
                            fieldWithPath("value.users[].id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value.users[].nickname").type(JsonFieldType.STRING).description("닉네임"),
                            fieldWithPath("value.users[].profile_image_url").type(JsonFieldType.STRING).description("프로필 이미지 URL").optional(),
                            fieldWithPath("value.guilds[]").type(JsonFieldType.ARRAY).description("길드 검색 결과"),
                            fieldWithPath("value.guilds[].id").type(JsonFieldType.STRING).description("길드 ID"),
                            fieldWithPath("value.guilds[].name").type(JsonFieldType.STRING).description("길드명"),
                            fieldWithPath("value.guilds[].description").type(JsonFieldType.STRING).description("길드 설명").optional(),
                            fieldWithPath("value.guilds[].image_url").type(JsonFieldType.STRING).description("길드 이미지 URL").optional(),
                            fieldWithPath("value.guilds[].member_count").type(JsonFieldType.NUMBER).description("멤버 수"),
                            fieldWithPath("value.total_count").type(JsonFieldType.NUMBER).description("전체 검색 결과 수")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }
}
