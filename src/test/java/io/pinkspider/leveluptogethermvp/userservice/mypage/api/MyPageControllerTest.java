package io.pinkspider.leveluptogethermvp.userservice.mypage.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.mock.web.MockMultipartFile;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.userservice.mypage.application.MyPageService;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.MyPageResponse;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.MyPageResponse.EquippedTitleInfo;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.MyPageResponse.ExperienceInfo;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.MyPageResponse.ProfileInfo;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.MyPageResponse.UserInfo;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.ProfileUpdateRequest;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.PublicProfileResponse;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.TitleChangeRequest;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.TitleChangeResponse;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.UserTitleListResponse;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.UserTitleListResponse.UserTitleItem;
import io.pinkspider.leveluptogethermvp.userservice.mypage.presentation.MyPageController;
import java.time.LocalDate;
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
import org.springframework.http.MediaType;
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@WebMvcTest(controllers = MyPageController.class,
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
class MyPageControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MyPageService myPageService;

    private static final String MOCK_USER_ID = "test-user-123";

    @Test
    @DisplayName("GET /api/v1/mypage : MyPage 전체 데이터 조회")
    void getMyPageTest() throws Exception {
        // given
        EquippedTitleInfo leftTitle = EquippedTitleInfo.builder()
            .userTitleId(1L)
            .titleId(1L)
            .name("초보 모험가")
            .displayName("[초보] 초보 모험가")
            .rarity("COMMON")
            .colorCode("#808080")
            .iconUrl("https://example.com/title/common.png")
            .build();

        EquippedTitleInfo rightTitle = EquippedTitleInfo.builder()
            .userTitleId(2L)
            .titleId(2L)
            .name("미션 마스터")
            .displayName("[미션] 미션 마스터")
            .rarity("RARE")
            .colorCode("#0000FF")
            .iconUrl("https://example.com/title/rare.png")
            .build();

        ProfileInfo profileInfo = ProfileInfo.builder()
            .userId(MOCK_USER_ID)
            .nickname("테스트유저")
            .profileImageUrl("https://example.com/profile.jpg")
            .bio("안녕하세요! 반갑습니다.")
            .leftTitle(leftTitle)
            .rightTitle(rightTitle)
            .followerCount(10)
            .followingCount(10)
            .build();

        ExperienceInfo experienceInfo = ExperienceInfo.builder()
            .currentLevel(5)
            .currentExp(250)
            .totalExp(1250)
            .nextLevelRequiredExp(300)
            .expPercentage(83.33)
            .expForPercentage(250)
            .build();

        UserInfo userInfo = UserInfo.builder()
            .startDate(LocalDate.of(2024, 1, 1))
            .daysSinceJoined(365L)
            .clearedMissionsCount(50)
            .clearedMissionBooksCount(5)
            .rankingPercentile(15.5)
            .acquiredTitlesCount(10)
            .rankingPoints(1500L)
            .build();

        MyPageResponse response = MyPageResponse.builder()
            .profile(profileInfo)
            .experience(experienceInfo)
            .userInfo(userInfo)
            .build();

        when(myPageService.getMyPage(anyString())).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/mypage")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("마이페이지-01. MyPage 전체 데이터 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("MyPage")
                        .description("MyPage 화면에 필요한 전체 데이터 조회 (프로필, 경험치, 유저 정보) (JWT 토큰 인증 필요)")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("MyPage 데이터"),

                            // Profile Info
                            fieldWithPath("value.profile").type(JsonFieldType.OBJECT).description("프로필 정보"),
                            fieldWithPath("value.profile.user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value.profile.nickname").type(JsonFieldType.STRING).description("닉네임"),
                            fieldWithPath("value.profile.profile_image_url").type(JsonFieldType.STRING).description("프로필 이미지 URL").optional(),
                            fieldWithPath("value.profile.bio").type(JsonFieldType.STRING).description("자기소개").optional(),
                            fieldWithPath("value.profile.left_title").type(JsonFieldType.OBJECT).description("좌측 장착 칭호").optional(),
                            fieldWithPath("value.profile.left_title.user_title_id").type(JsonFieldType.NUMBER).description("사용자 칭호 ID").optional(),
                            fieldWithPath("value.profile.left_title.title_id").type(JsonFieldType.NUMBER).description("칭호 ID").optional(),
                            fieldWithPath("value.profile.left_title.name").type(JsonFieldType.STRING).description("칭호 이름").optional(),
                            fieldWithPath("value.profile.left_title.name_en").type(JsonFieldType.STRING).description("칭호 이름 (영어)").optional(),
                            fieldWithPath("value.profile.left_title.name_ar").type(JsonFieldType.STRING).description("칭호 이름 (아랍어)").optional(),
                            fieldWithPath("value.profile.left_title.display_name").type(JsonFieldType.STRING).description("표시 이름").optional(),
                            fieldWithPath("value.profile.left_title.rarity").type(JsonFieldType.STRING).description("희귀도").optional(),
                            fieldWithPath("value.profile.left_title.color_code").type(JsonFieldType.STRING).description("색상 코드").optional(),
                            fieldWithPath("value.profile.left_title.icon_url").type(JsonFieldType.STRING).description("아이콘 URL").optional(),
                            fieldWithPath("value.profile.right_title").type(JsonFieldType.OBJECT).description("우측 장착 칭호").optional(),
                            fieldWithPath("value.profile.right_title.user_title_id").type(JsonFieldType.NUMBER).description("사용자 칭호 ID").optional(),
                            fieldWithPath("value.profile.right_title.title_id").type(JsonFieldType.NUMBER).description("칭호 ID").optional(),
                            fieldWithPath("value.profile.right_title.name").type(JsonFieldType.STRING).description("칭호 이름").optional(),
                            fieldWithPath("value.profile.right_title.name_en").type(JsonFieldType.STRING).description("칭호 이름 (영어)").optional(),
                            fieldWithPath("value.profile.right_title.name_ar").type(JsonFieldType.STRING).description("칭호 이름 (아랍어)").optional(),
                            fieldWithPath("value.profile.right_title.display_name").type(JsonFieldType.STRING).description("표시 이름").optional(),
                            fieldWithPath("value.profile.right_title.rarity").type(JsonFieldType.STRING).description("희귀도").optional(),
                            fieldWithPath("value.profile.right_title.color_code").type(JsonFieldType.STRING).description("색상 코드").optional(),
                            fieldWithPath("value.profile.right_title.icon_url").type(JsonFieldType.STRING).description("아이콘 URL").optional(),
                            fieldWithPath("value.profile.follower_count").type(JsonFieldType.NUMBER).description("팔로워 수"),
                            fieldWithPath("value.profile.following_count").type(JsonFieldType.NUMBER).description("팔로잉 수"),

                            // Experience Info
                            fieldWithPath("value.experience").type(JsonFieldType.OBJECT).description("경험치 정보"),
                            fieldWithPath("value.experience.current_level").type(JsonFieldType.NUMBER).description("현재 레벨"),
                            fieldWithPath("value.experience.current_exp").type(JsonFieldType.NUMBER).description("현재 경험치"),
                            fieldWithPath("value.experience.total_exp").type(JsonFieldType.NUMBER).description("누적 경험치"),
                            fieldWithPath("value.experience.next_level_required_exp").type(JsonFieldType.NUMBER).description("다음 레벨 필요 경험치"),
                            fieldWithPath("value.experience.exp_percentage").type(JsonFieldType.NUMBER).description("경험치 퍼센트"),
                            fieldWithPath("value.experience.exp_for_percentage").type(JsonFieldType.NUMBER).description("퍼센트 계산에 사용된 경험치"),

                            // User Info
                            fieldWithPath("value.user_info").type(JsonFieldType.OBJECT).description("유저 정보/통계"),
                            fieldWithPath("value.user_info.start_date").type(JsonFieldType.STRING).description("가입일"),
                            fieldWithPath("value.user_info.days_since_joined").type(JsonFieldType.NUMBER).description("가입 후 일수"),
                            fieldWithPath("value.user_info.cleared_missions_count").type(JsonFieldType.NUMBER).description("완료한 미션 수"),
                            fieldWithPath("value.user_info.cleared_mission_books_count").type(JsonFieldType.NUMBER).description("완료한 미션북 수"),
                            fieldWithPath("value.user_info.ranking_percentile").type(JsonFieldType.NUMBER).description("랭킹 퍼센타일 (상위 X%)"),
                            fieldWithPath("value.user_info.acquired_titles_count").type(JsonFieldType.NUMBER).description("획득한 칭호 수"),
                            fieldWithPath("value.user_info.ranking_points").type(JsonFieldType.NUMBER).description("랭킹 포인트")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("PUT /api/v1/mypage/profile : 프로필 이미지 변경")
    void updateProfileTest() throws Exception {
        // given
        ProfileUpdateRequest request = ProfileUpdateRequest.builder()
            .profileImageUrl("https://example.com/new-profile.jpg")
            .build();

        ProfileInfo response = ProfileInfo.builder()
            .userId(MOCK_USER_ID)
            .nickname("테스트유저")
            .profileImageUrl("https://example.com/new-profile.jpg")
            .bio("안녕하세요!")
            .followerCount(10)
            .followingCount(10)
            .build();

        when(myPageService.updateProfileImage(anyString(), any(ProfileUpdateRequest.class)))
            .thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.put("/api/v1/mypage/profile")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andDo(
            MockMvcRestDocumentationWrapper.document("마이페이지-02. 프로필 이미지 변경",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("MyPage")
                        .description("프로필 이미지 변경 (JWT 토큰 인증 필요)")
                        .requestFields(
                            fieldWithPath("profile_image_url").type(JsonFieldType.STRING).description("새 프로필 이미지 URL")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("업데이트된 프로필 정보"),
                            fieldWithPath("value.user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value.nickname").type(JsonFieldType.STRING).description("닉네임"),
                            fieldWithPath("value.profile_image_url").type(JsonFieldType.STRING).description("프로필 이미지 URL"),
                            fieldWithPath("value.bio").type(JsonFieldType.STRING).description("자기소개").optional(),
                            fieldWithPath("value.left_title").type(JsonFieldType.OBJECT).description("좌측 장착 칭호").optional(),
                            fieldWithPath("value.right_title").type(JsonFieldType.OBJECT).description("우측 장착 칭호").optional(),
                            fieldWithPath("value.follower_count").type(JsonFieldType.NUMBER).description("팔로워 수"),
                            fieldWithPath("value.following_count").type(JsonFieldType.NUMBER).description("팔로잉 수")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/mypage/profile/image : 프로필 이미지 업로드")
    void uploadProfileImageTest() throws Exception {
        // given
        MockMultipartFile imageFile = new MockMultipartFile(
            "image",
            "profile.jpg",
            MediaType.IMAGE_JPEG_VALUE,
            "test image content".getBytes()
        );

        ProfileInfo response = ProfileInfo.builder()
            .userId(MOCK_USER_ID)
            .nickname("테스트유저")
            .profileImageUrl("/uploads/profile/" + MOCK_USER_ID + "/uuid-generated.jpg")
            .bio("안녕하세요!")
            .followerCount(10)
            .followingCount(10)
            .build();

        when(myPageService.uploadProfileImage(anyString(), any(MultipartFile.class)))
            .thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.multipart("/api/v1/mypage/profile/image")
                .file(imageFile)
                .with(user(MOCK_USER_ID))
        ).andDo(
            MockMvcRestDocumentationWrapper.document("마이페이지-05. 프로필 이미지 업로드",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("MyPage")
                        .description("프로필 이미지 파일 업로드 (multipart/form-data) (JWT 토큰 인증 필요)")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("업데이트된 프로필 정보"),
                            fieldWithPath("value.user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value.nickname").type(JsonFieldType.STRING).description("닉네임"),
                            fieldWithPath("value.profile_image_url").type(JsonFieldType.STRING).description("프로필 이미지 URL"),
                            fieldWithPath("value.bio").type(JsonFieldType.STRING).description("자기소개").optional(),
                            fieldWithPath("value.left_title").type(JsonFieldType.OBJECT).description("좌측 장착 칭호").optional(),
                            fieldWithPath("value.right_title").type(JsonFieldType.OBJECT).description("우측 장착 칭호").optional(),
                            fieldWithPath("value.follower_count").type(JsonFieldType.NUMBER).description("팔로워 수"),
                            fieldWithPath("value.following_count").type(JsonFieldType.NUMBER).description("팔로잉 수")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/mypage/titles : 보유 칭호 목록 조회")
    void getUserTitlesTest() throws Exception {
        // given
        UserTitleItem title1 = UserTitleItem.builder()
            .userTitleId(1L)
            .titleId(1L)
            .name("용감한")
            .displayName("용감한")
            .description("두려움 없는")
            .rarity("UNCOMMON")
            .positionType("LEFT")
            .colorCode("#98FB98")
            .iconUrl("https://example.com/title/left/brave.png")
            .isEquipped(true)
            .equippedPosition("LEFT")
            .acquiredAt(LocalDateTime.now().minusDays(30))
            .build();

        UserTitleItem title2 = UserTitleItem.builder()
            .userTitleId(2L)
            .titleId(2L)
            .name("전사")
            .displayName("전사")
            .description("강인한 의지의 전사")
            .rarity("UNCOMMON")
            .positionType("RIGHT")
            .colorCode("#7CFC00")
            .iconUrl("https://example.com/title/right/warrior.png")
            .isEquipped(true)
            .equippedPosition("RIGHT")
            .acquiredAt(LocalDateTime.now().minusDays(10))
            .build();

        UserTitleItem title3 = UserTitleItem.builder()
            .userTitleId(3L)
            .titleId(3L)
            .name("모험가")
            .displayName("모험가")
            .description("모험을 시작한 자")
            .rarity("COMMON")
            .positionType("RIGHT")
            .colorCode("#C0C0C0")
            .iconUrl("https://example.com/title/right/adventurer.png")
            .isEquipped(false)
            .equippedPosition(null)
            .acquiredAt(LocalDateTime.now().minusDays(5))
            .build();

        UserTitleListResponse response = UserTitleListResponse.builder()
            .totalCount(3)
            .titles(List.of(title1, title2, title3))
            .equippedLeftId(1L)
            .equippedRightId(2L)
            .build();

        when(myPageService.getUserTitles(anyString())).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/mypage/titles")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("마이페이지-03. 보유 칭호 목록 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("MyPage")
                        .description("사용자가 보유한 칭호 목록 조회 (JWT 토큰 인증 필요)")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("칭호 목록 정보"),
                            fieldWithPath("value.total_count").type(JsonFieldType.NUMBER).description("총 보유 칭호 수"),
                            fieldWithPath("value.equipped_left_id").type(JsonFieldType.NUMBER).description("좌측 장착 칭호 ID").optional(),
                            fieldWithPath("value.equipped_right_id").type(JsonFieldType.NUMBER).description("우측 장착 칭호 ID").optional(),
                            fieldWithPath("value.titles[]").type(JsonFieldType.ARRAY).description("칭호 목록"),
                            fieldWithPath("value.titles[].user_title_id").type(JsonFieldType.NUMBER).description("사용자 칭호 ID"),
                            fieldWithPath("value.titles[].title_id").type(JsonFieldType.NUMBER).description("칭호 ID"),
                            fieldWithPath("value.titles[].name").type(JsonFieldType.STRING).description("칭호 이름"),
                            fieldWithPath("value.titles[].name_en").type(JsonFieldType.STRING).description("칭호 이름 (영어)").optional(),
                            fieldWithPath("value.titles[].name_ar").type(JsonFieldType.STRING).description("칭호 이름 (아랍어)").optional(),
                            fieldWithPath("value.titles[].display_name").type(JsonFieldType.STRING).description("표시 이름"),
                            fieldWithPath("value.titles[].description").type(JsonFieldType.STRING).description("칭호 설명").optional(),
                            fieldWithPath("value.titles[].description_en").type(JsonFieldType.STRING).description("칭호 설명 (영어)").optional(),
                            fieldWithPath("value.titles[].description_ar").type(JsonFieldType.STRING).description("칭호 설명 (아랍어)").optional(),
                            fieldWithPath("value.titles[].rarity").type(JsonFieldType.STRING).description("희귀도 (COMMON, UNCOMMON, RARE, EPIC, LEGENDARY)"),
                            fieldWithPath("value.titles[].position_type").type(JsonFieldType.STRING).description("칭호 위치 타입 (LEFT: 형용사, RIGHT: 명사)"),
                            fieldWithPath("value.titles[].color_code").type(JsonFieldType.STRING).description("색상 코드").optional(),
                            fieldWithPath("value.titles[].icon_url").type(JsonFieldType.STRING).description("아이콘 URL").optional(),
                            fieldWithPath("value.titles[].is_equipped").type(JsonFieldType.BOOLEAN).description("장착 여부"),
                            fieldWithPath("value.titles[].equipped_position").type(JsonFieldType.STRING).description("장착 위치 (LEFT, RIGHT)").optional(),
                            fieldWithPath("value.titles[].acquired_at").type(JsonFieldType.STRING).description("획득 일시")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("PUT /api/v1/mypage/titles : 칭호 변경")
    void changeTitlesTest() throws Exception {
        // given
        TitleChangeRequest request = TitleChangeRequest.builder()
            .leftUserTitleId(1L)
            .rightUserTitleId(3L)
            .build();

        EquippedTitleInfo leftTitle = EquippedTitleInfo.builder()
            .userTitleId(1L)
            .titleId(1L)
            .name("초보 모험가")
            .displayName("[초보] 초보 모험가")
            .rarity("COMMON")
            .colorCode("#808080")
            .iconUrl("https://example.com/title/common.png")
            .build();

        EquippedTitleInfo rightTitle = EquippedTitleInfo.builder()
            .userTitleId(3L)
            .titleId(3L)
            .name("출석왕")
            .displayName("[출석] 출석왕")
            .rarity("UNCOMMON")
            .colorCode("#00FF00")
            .iconUrl("https://example.com/title/uncommon.png")
            .build();

        TitleChangeResponse response = TitleChangeResponse.builder()
            .message("칭호가 변경되었습니다.")
            .leftTitle(leftTitle)
            .rightTitle(rightTitle)
            .build();

        when(myPageService.changeTitles(anyString(), any(TitleChangeRequest.class)))
            .thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.put("/api/v1/mypage/titles")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andDo(
            MockMvcRestDocumentationWrapper.document("마이페이지-04. 칭호 변경",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("MyPage")
                        .description("좌측/우측 칭호 동시 변경 (둘 다 필수 선택) (JWT 토큰 인증 필요)")
                        .requestFields(
                            fieldWithPath("left_user_title_id").type(JsonFieldType.NUMBER).description("좌측 장착할 사용자 칭호 ID (필수)"),
                            fieldWithPath("right_user_title_id").type(JsonFieldType.NUMBER).description("우측 장착할 사용자 칭호 ID (필수)")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("변경 결과"),
                            fieldWithPath("value.message").type(JsonFieldType.STRING).description("결과 메시지"),
                            fieldWithPath("value.left_title").type(JsonFieldType.OBJECT).description("좌측 장착 칭호"),
                            fieldWithPath("value.left_title.user_title_id").type(JsonFieldType.NUMBER).description("사용자 칭호 ID"),
                            fieldWithPath("value.left_title.title_id").type(JsonFieldType.NUMBER).description("칭호 ID"),
                            fieldWithPath("value.left_title.name").type(JsonFieldType.STRING).description("칭호 이름"),
                            fieldWithPath("value.left_title.name_en").type(JsonFieldType.STRING).description("칭호 이름 (영어)").optional(),
                            fieldWithPath("value.left_title.name_ar").type(JsonFieldType.STRING).description("칭호 이름 (아랍어)").optional(),
                            fieldWithPath("value.left_title.display_name").type(JsonFieldType.STRING).description("표시 이름"),
                            fieldWithPath("value.left_title.rarity").type(JsonFieldType.STRING).description("희귀도"),
                            fieldWithPath("value.left_title.color_code").type(JsonFieldType.STRING).description("색상 코드").optional(),
                            fieldWithPath("value.left_title.icon_url").type(JsonFieldType.STRING).description("아이콘 URL").optional(),
                            fieldWithPath("value.right_title").type(JsonFieldType.OBJECT).description("우측 장착 칭호"),
                            fieldWithPath("value.right_title.user_title_id").type(JsonFieldType.NUMBER).description("사용자 칭호 ID"),
                            fieldWithPath("value.right_title.title_id").type(JsonFieldType.NUMBER).description("칭호 ID"),
                            fieldWithPath("value.right_title.name").type(JsonFieldType.STRING).description("칭호 이름"),
                            fieldWithPath("value.right_title.name_en").type(JsonFieldType.STRING).description("칭호 이름 (영어)").optional(),
                            fieldWithPath("value.right_title.name_ar").type(JsonFieldType.STRING).description("칭호 이름 (아랍어)").optional(),
                            fieldWithPath("value.right_title.display_name").type(JsonFieldType.STRING).description("표시 이름"),
                            fieldWithPath("value.right_title.rarity").type(JsonFieldType.STRING).description("희귀도"),
                            fieldWithPath("value.right_title.color_code").type(JsonFieldType.STRING).description("색상 코드").optional(),
                            fieldWithPath("value.right_title.icon_url").type(JsonFieldType.STRING).description("아이콘 URL").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/mypage/profile/{userId} : 공개 프로필 조회 - 본인")
    void getPublicProfileOwnerTest() throws Exception {
        // given
        String targetUserId = MOCK_USER_ID;

        PublicProfileResponse.GuildInfo guild1 = PublicProfileResponse.GuildInfo.builder()
            .guildId(1L)
            .name("테스트 길드")
            .imageUrl("https://example.com/guild.jpg")
            .level(3)
            .memberCount(8)
            .build();

        PublicProfileResponse response = PublicProfileResponse.builder()
            .userId(targetUserId)
            .nickname("테스트유저")
            .profileImageUrl("https://example.com/profile.jpg")
            .bio("안녕하세요! 반갑습니다.")
            .level(5)
            .startDate(LocalDate.of(2024, 1, 1))
            .daysSinceJoined(365L)
            .clearedMissionsCount(50)
            .acquiredTitlesCount(10)
            .guilds(List.of(guild1))
            .isOwner(true)  // 본인이므로 true
            .friendshipStatus(null)  // 본인 조회시 친구 상태 불필요
            .friendRequestId(null)
            .build();

        when(myPageService.getPublicProfile(targetUserId, MOCK_USER_ID)).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/mypage/profile/{userId}", targetUserId)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("마이페이지-06. 공개 프로필 조회 (본인)",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("MyPage")
                        .description("공개 프로필 조회 - 본인 프로필 조회 시 is_owner: true")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("공개 프로필 데이터"),
                            fieldWithPath("value.user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value.nickname").type(JsonFieldType.STRING).description("닉네임"),
                            fieldWithPath("value.profile_image_url").type(JsonFieldType.STRING).description("프로필 이미지 URL").optional(),
                            fieldWithPath("value.bio").type(JsonFieldType.STRING).description("자기소개").optional(),
                            fieldWithPath("value.left_title").type(JsonFieldType.OBJECT).description("좌측 장착 칭호").optional(),
                            fieldWithPath("value.right_title").type(JsonFieldType.OBJECT).description("우측 장착 칭호").optional(),
                            fieldWithPath("value.level").type(JsonFieldType.NUMBER).description("레벨"),
                            fieldWithPath("value.start_date").type(JsonFieldType.STRING).description("가입일"),
                            fieldWithPath("value.days_since_joined").type(JsonFieldType.NUMBER).description("가입 후 일수"),
                            fieldWithPath("value.cleared_missions_count").type(JsonFieldType.NUMBER).description("완료한 미션 수"),
                            fieldWithPath("value.acquired_titles_count").type(JsonFieldType.NUMBER).description("획득한 칭호 수"),
                            fieldWithPath("value.guilds").type(JsonFieldType.ARRAY).description("소속 길드 목록").optional(),
                            fieldWithPath("value.guilds[].guild_id").type(JsonFieldType.NUMBER).description("길드 ID").optional(),
                            fieldWithPath("value.guilds[].name").type(JsonFieldType.STRING).description("길드 이름").optional(),
                            fieldWithPath("value.guilds[].image_url").type(JsonFieldType.STRING).description("길드 이미지 URL").optional(),
                            fieldWithPath("value.guilds[].level").type(JsonFieldType.NUMBER).description("길드 레벨").optional(),
                            fieldWithPath("value.guilds[].member_count").type(JsonFieldType.NUMBER).description("길드 멤버 수").optional(),
                            fieldWithPath("value.is_owner").type(JsonFieldType.BOOLEAN).description("본인 여부 (true: 본인, false: 타인)"),
                            fieldWithPath("value.friendship_status").type(JsonFieldType.STRING).description("친구 관계 상태 (본인 조회 시 null)").optional(),
                            fieldWithPath("value.friend_request_id").type(JsonFieldType.NUMBER).description("친구 요청 ID (본인 조회 시 null)").optional(),
                            fieldWithPath("value.is_under_review").type(JsonFieldType.BOOLEAN).description("신고 처리중 여부").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andExpect(MockMvcResultMatchers.jsonPath("$.value.is_owner").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/mypage/profile/{userId} : 공개 프로필 조회 - 타인 (친구 아님)")
    void getPublicProfileOtherTest() throws Exception {
        // given
        String targetUserId = "other-user-456";

        PublicProfileResponse.GuildInfo guild1 = PublicProfileResponse.GuildInfo.builder()
            .guildId(2L)
            .name("다른 길드")
            .imageUrl("https://example.com/other-guild.jpg")
            .level(5)
            .memberCount(10)
            .build();

        PublicProfileResponse response = PublicProfileResponse.builder()
            .userId(targetUserId)
            .nickname("다른유저")
            .profileImageUrl("https://example.com/other-profile.jpg")
            .bio("다른 사람의 자기소개")
            .level(10)
            .startDate(LocalDate.of(2023, 6, 1))
            .daysSinceJoined(500L)
            .clearedMissionsCount(100)
            .acquiredTitlesCount(20)
            .guilds(List.of(guild1))
            .isOwner(false)  // 타인이므로 false
            .friendshipStatus("NONE")  // 친구 관계 없음
            .friendRequestId(null)
            .build();

        when(myPageService.getPublicProfile(targetUserId, MOCK_USER_ID)).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/mypage/profile/{userId}", targetUserId)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("마이페이지-07. 공개 프로필 조회 (타인-친구아님)",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("MyPage")
                        .description("공개 프로필 조회 - 타인 프로필, 친구 관계 없음 (friendshipStatus: NONE)")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("공개 프로필 데이터"),
                            fieldWithPath("value.user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value.nickname").type(JsonFieldType.STRING).description("닉네임"),
                            fieldWithPath("value.profile_image_url").type(JsonFieldType.STRING).description("프로필 이미지 URL").optional(),
                            fieldWithPath("value.bio").type(JsonFieldType.STRING).description("자기소개").optional(),
                            fieldWithPath("value.left_title").type(JsonFieldType.OBJECT).description("좌측 장착 칭호").optional(),
                            fieldWithPath("value.right_title").type(JsonFieldType.OBJECT).description("우측 장착 칭호").optional(),
                            fieldWithPath("value.level").type(JsonFieldType.NUMBER).description("레벨"),
                            fieldWithPath("value.start_date").type(JsonFieldType.STRING).description("가입일"),
                            fieldWithPath("value.days_since_joined").type(JsonFieldType.NUMBER).description("가입 후 일수"),
                            fieldWithPath("value.cleared_missions_count").type(JsonFieldType.NUMBER).description("완료한 미션 수"),
                            fieldWithPath("value.acquired_titles_count").type(JsonFieldType.NUMBER).description("획득한 칭호 수"),
                            fieldWithPath("value.guilds").type(JsonFieldType.ARRAY).description("소속 길드 목록").optional(),
                            fieldWithPath("value.guilds[].guild_id").type(JsonFieldType.NUMBER).description("길드 ID").optional(),
                            fieldWithPath("value.guilds[].name").type(JsonFieldType.STRING).description("길드 이름").optional(),
                            fieldWithPath("value.guilds[].image_url").type(JsonFieldType.STRING).description("길드 이미지 URL").optional(),
                            fieldWithPath("value.guilds[].level").type(JsonFieldType.NUMBER).description("길드 레벨").optional(),
                            fieldWithPath("value.guilds[].member_count").type(JsonFieldType.NUMBER).description("길드 멤버 수").optional(),
                            fieldWithPath("value.is_owner").type(JsonFieldType.BOOLEAN).description("본인 여부 (true: 본인, false: 타인)"),
                            fieldWithPath("value.friendship_status").type(JsonFieldType.STRING).description("친구 관계 상태 (NONE, PENDING_SENT, PENDING_RECEIVED, ACCEPTED)").optional(),
                            fieldWithPath("value.friend_request_id").type(JsonFieldType.NUMBER).description("친구 요청 ID (PENDING_RECEIVED일 때만 존재)").optional(),
                            fieldWithPath("value.is_under_review").type(JsonFieldType.BOOLEAN).description("신고 처리중 여부").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andExpect(MockMvcResultMatchers.jsonPath("$.value.is_owner").value(false))
            .andExpect(MockMvcResultMatchers.jsonPath("$.value.friendship_status").value("NONE"));
    }

    @Test
    @DisplayName("GET /api/v1/mypage/profile/{userId} : 공개 프로필 조회 - 친구 요청 받음")
    void getPublicProfilePendingReceivedTest() throws Exception {
        // given
        String targetUserId = "requester-user-789";

        PublicProfileResponse response = PublicProfileResponse.builder()
            .userId(targetUserId)
            .nickname("요청한유저")
            .profileImageUrl("https://example.com/requester-profile.jpg")
            .bio("팔로우 신청을 보낸 사용자")
            .level(8)
            .startDate(LocalDate.of(2024, 3, 1))
            .daysSinceJoined(300L)
            .clearedMissionsCount(75)
            .acquiredTitlesCount(15)
            .guilds(List.of())
            .isOwner(false)
            .friendshipStatus("PENDING_RECEIVED")  // 상대방이 나에게 친구 요청을 보냄
            .friendRequestId(123L)  // 수락/거절에 사용할 요청 ID
            .build();

        when(myPageService.getPublicProfile(targetUserId, MOCK_USER_ID)).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/mypage/profile/{userId}", targetUserId)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("마이페이지-08. 공개 프로필 조회 (친구요청받음)",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("MyPage")
                        .description("공개 프로필 조회 - 상대방에게 친구 요청을 받은 상태 (friendshipStatus: PENDING_RECEIVED)")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("공개 프로필 데이터"),
                            fieldWithPath("value.user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value.nickname").type(JsonFieldType.STRING).description("닉네임"),
                            fieldWithPath("value.profile_image_url").type(JsonFieldType.STRING).description("프로필 이미지 URL").optional(),
                            fieldWithPath("value.bio").type(JsonFieldType.STRING).description("자기소개").optional(),
                            fieldWithPath("value.left_title").type(JsonFieldType.OBJECT).description("좌측 장착 칭호").optional(),
                            fieldWithPath("value.right_title").type(JsonFieldType.OBJECT).description("우측 장착 칭호").optional(),
                            fieldWithPath("value.level").type(JsonFieldType.NUMBER).description("레벨"),
                            fieldWithPath("value.start_date").type(JsonFieldType.STRING).description("가입일"),
                            fieldWithPath("value.days_since_joined").type(JsonFieldType.NUMBER).description("가입 후 일수"),
                            fieldWithPath("value.cleared_missions_count").type(JsonFieldType.NUMBER).description("완료한 미션 수"),
                            fieldWithPath("value.acquired_titles_count").type(JsonFieldType.NUMBER).description("획득한 칭호 수"),
                            fieldWithPath("value.guilds").type(JsonFieldType.ARRAY).description("소속 길드 목록").optional(),
                            fieldWithPath("value.is_owner").type(JsonFieldType.BOOLEAN).description("본인 여부"),
                            fieldWithPath("value.friendship_status").type(JsonFieldType.STRING).description("친구 관계 상태 (PENDING_RECEIVED)"),
                            fieldWithPath("value.friend_request_id").type(JsonFieldType.NUMBER).description("친구 요청 ID (수락/거절에 사용)"),
                            fieldWithPath("value.is_under_review").type(JsonFieldType.BOOLEAN).description("신고 처리중 여부").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andExpect(MockMvcResultMatchers.jsonPath("$.value.friendship_status").value("PENDING_RECEIVED"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.value.friend_request_id").value(123));
    }

    @Test
    @DisplayName("GET /api/v1/mypage/profile/{userId} : 공개 프로필 조회 - 이미 친구")
    void getPublicProfileAcceptedTest() throws Exception {
        // given
        String targetUserId = "friend-user-999";

        PublicProfileResponse response = PublicProfileResponse.builder()
            .userId(targetUserId)
            .nickname("친구유저")
            .profileImageUrl("https://example.com/friend-profile.jpg")
            .bio("이미 친구인 사용자")
            .level(12)
            .startDate(LocalDate.of(2023, 1, 1))
            .daysSinceJoined(730L)
            .clearedMissionsCount(200)
            .acquiredTitlesCount(30)
            .guilds(List.of())
            .isOwner(false)
            .friendshipStatus("ACCEPTED")  // 이미 친구
            .friendRequestId(null)
            .build();

        when(myPageService.getPublicProfile(targetUserId, MOCK_USER_ID)).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/mypage/profile/{userId}", targetUserId)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("마이페이지-09. 공개 프로필 조회 (친구)",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("MyPage")
                        .description("공개 프로필 조회 - 이미 친구 상태 (friendshipStatus: ACCEPTED)")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("공개 프로필 데이터"),
                            fieldWithPath("value.user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value.nickname").type(JsonFieldType.STRING).description("닉네임"),
                            fieldWithPath("value.profile_image_url").type(JsonFieldType.STRING).description("프로필 이미지 URL").optional(),
                            fieldWithPath("value.bio").type(JsonFieldType.STRING).description("자기소개").optional(),
                            fieldWithPath("value.left_title").type(JsonFieldType.OBJECT).description("좌측 장착 칭호").optional(),
                            fieldWithPath("value.right_title").type(JsonFieldType.OBJECT).description("우측 장착 칭호").optional(),
                            fieldWithPath("value.level").type(JsonFieldType.NUMBER).description("레벨"),
                            fieldWithPath("value.start_date").type(JsonFieldType.STRING).description("가입일"),
                            fieldWithPath("value.days_since_joined").type(JsonFieldType.NUMBER).description("가입 후 일수"),
                            fieldWithPath("value.cleared_missions_count").type(JsonFieldType.NUMBER).description("완료한 미션 수"),
                            fieldWithPath("value.acquired_titles_count").type(JsonFieldType.NUMBER).description("획득한 칭호 수"),
                            fieldWithPath("value.guilds").type(JsonFieldType.ARRAY).description("소속 길드 목록").optional(),
                            fieldWithPath("value.is_owner").type(JsonFieldType.BOOLEAN).description("본인 여부"),
                            fieldWithPath("value.friendship_status").type(JsonFieldType.STRING).description("친구 관계 상태 (ACCEPTED)"),
                            fieldWithPath("value.friend_request_id").type(JsonFieldType.NUMBER).description("친구 요청 ID").optional(),
                            fieldWithPath("value.is_under_review").type(JsonFieldType.BOOLEAN).description("신고 처리중 여부").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andExpect(MockMvcResultMatchers.jsonPath("$.value.friendship_status").value("ACCEPTED"));
    }
}
