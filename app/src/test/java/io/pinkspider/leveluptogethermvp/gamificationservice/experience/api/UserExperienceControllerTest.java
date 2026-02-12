package io.pinkspider.leveluptogethermvp.gamificationservice.experience.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.metaservice.userlevelconfig.domain.entity.UserLevelConfig;
import io.pinkspider.leveluptogethermvp.gamificationservice.experience.application.UserExperienceService;
import io.pinkspider.leveluptogethermvp.gamificationservice.experience.domain.dto.UserExperienceResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.ExperienceHistory;
import io.pinkspider.global.enums.ExpSourceType;
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

@WebMvcTest(controllers = UserExperienceController.class,
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
class UserExperienceControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    private UserExperienceService userExperienceService;

    private static final String MOCK_USER_ID = "test-user-123";

    @Test
    @DisplayName("GET /api/v1/users/experience/me : 내 경험치 정보 조회")
    void getMyExperienceTest() throws Exception {
        // given
        UserExperienceResponse response = UserExperienceResponse.builder()
            .id(1L)
            .userId(MOCK_USER_ID)
            .currentLevel(5)
            .currentExp(250)
            .totalExp(1250)
            .nextLevelRequiredExp(300)
            .expToNextLevel(50)
            .progressToNextLevel(83.33)
            .createdAt(LocalDateTime.now())
            .modifiedAt(LocalDateTime.now())
            .build();

        when(userExperienceService.getUserExperience(anyString())).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/users/experience/me")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("경험치-01. 내 경험치 정보 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("User Experience")
                        .description("내 경험치/레벨 정보 조회 (JWT 토큰 인증 필요)")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("경험치 정보"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("경험치 ID"),
                            fieldWithPath("value.user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value.current_level").type(JsonFieldType.NUMBER).description("현재 레벨"),
                            fieldWithPath("value.current_exp").type(JsonFieldType.NUMBER).description("현재 경험치"),
                            fieldWithPath("value.total_exp").type(JsonFieldType.NUMBER).description("누적 경험치"),
                            fieldWithPath("value.next_level_required_exp").type(JsonFieldType.NUMBER).description("다음 레벨 필요 경험치"),
                            fieldWithPath("value.exp_to_next_level").type(JsonFieldType.NUMBER).description("다음 레벨까지 남은 경험치"),
                            fieldWithPath("value.progress_to_next_level").type(JsonFieldType.NUMBER).description("다음 레벨 진행률 (%)"),
                            fieldWithPath("value.created_at").type(JsonFieldType.STRING).description("생성일시"),
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
    @DisplayName("GET /api/v1/users/experience/me/history : 내 경험치 획득 이력 조회")
    void getMyExperienceHistoryTest() throws Exception {
        // given
        ExperienceHistory history1 = ExperienceHistory.builder()
            .id(1L)
            .userId(MOCK_USER_ID)
            .sourceType(ExpSourceType.MISSION_EXECUTION)
            .sourceId(1L)
            .expAmount(50)
            .description("미션 완료")
            .build();
        history1.setCreatedAt(LocalDateTime.now());

        ExperienceHistory history2 = ExperienceHistory.builder()
            .id(2L)
            .userId(MOCK_USER_ID)
            .sourceType(ExpSourceType.BONUS)
            .sourceId(1L)
            .expAmount(10)
            .description("출석 보상")
            .build();
        history2.setCreatedAt(LocalDateTime.now());

        Page<ExperienceHistory> page = new PageImpl<>(
            List.of(history1, history2), PageRequest.of(0, 20), 2);

        when(userExperienceService.getExperienceHistory(anyString(), any(Pageable.class)))
            .thenReturn(page);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/users/experience/me/history")
                .with(user(MOCK_USER_ID))
                .param("page", "0")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("경험치-02. 내 경험치 획득 이력 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("User Experience")
                        .description("내 경험치 획득 이력 조회 (JWT 토큰 인증 필요)")
                        .queryParameters(
                            parameterWithName("page").type(SimpleType.NUMBER).description("페이지 번호 (0부터 시작)").optional(),
                            parameterWithName("size").type(SimpleType.NUMBER).description("페이지 크기").optional()
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("페이징된 경험치 이력"),
                            fieldWithPath("value.content[]").type(JsonFieldType.ARRAY).description("경험치 이력 목록"),
                            fieldWithPath("value.content[].id").type(JsonFieldType.NUMBER).description("이력 ID"),
                            fieldWithPath("value.content[].user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value.content[].source_type").type(JsonFieldType.STRING).description("획득 유형 (MISSION_EXECUTION, ACHIEVEMENT, BONUS 등)"),
                            fieldWithPath("value.content[].source_id").type(JsonFieldType.NUMBER).description("출처 ID").optional(),
                            fieldWithPath("value.content[].exp_amount").type(JsonFieldType.NUMBER).description("획득 경험치"),
                            fieldWithPath("value.content[].description").type(JsonFieldType.STRING).description("설명").optional(),
                            fieldWithPath("value.content[].category_name").type(JsonFieldType.STRING).description("카테고리명").optional(),
                            fieldWithPath("value.content[].level_before").type(JsonFieldType.NUMBER).description("획득 전 레벨").optional(),
                            fieldWithPath("value.content[].level_after").type(JsonFieldType.NUMBER).description("획득 후 레벨").optional(),
                            fieldWithPath("value.content[].created_at").type(JsonFieldType.STRING).description("획득 일시"),
                            fieldWithPath("value.content[].modified_at").type(JsonFieldType.STRING).description("수정 일시").optional(),
                            fieldWithPath("value.pageable").type(JsonFieldType.OBJECT).description("페이징 정보").optional(),
                            fieldWithPath("value.pageable.page_number").type(JsonFieldType.NUMBER).description("페이지 번호").optional(),
                            fieldWithPath("value.pageable.page_size").type(JsonFieldType.NUMBER).description("페이지 크기").optional(),
                            fieldWithPath("value.pageable.sort").type(JsonFieldType.OBJECT).description("정렬 정보").optional(),
                            fieldWithPath("value.pageable.sort.empty").type(JsonFieldType.BOOLEAN).description("정렬 비어있음").optional(),
                            fieldWithPath("value.pageable.sort.sorted").type(JsonFieldType.BOOLEAN).description("정렬됨").optional(),
                            fieldWithPath("value.pageable.sort.unsorted").type(JsonFieldType.BOOLEAN).description("미정렬").optional(),
                            fieldWithPath("value.pageable.offset").type(JsonFieldType.NUMBER).description("오프셋").optional(),
                            fieldWithPath("value.pageable.paged").type(JsonFieldType.BOOLEAN).description("페이징 여부").optional(),
                            fieldWithPath("value.pageable.unpaged").type(JsonFieldType.BOOLEAN).description("비페이징 여부").optional(),
                            fieldWithPath("value.total_elements").type(JsonFieldType.NUMBER).description("전체 요소 수").optional(),
                            fieldWithPath("value.total_pages").type(JsonFieldType.NUMBER).description("전체 페이지 수").optional(),
                            fieldWithPath("value.last").type(JsonFieldType.BOOLEAN).description("마지막 페이지 여부").optional(),
                            fieldWithPath("value.size").type(JsonFieldType.NUMBER).description("페이지 크기").optional(),
                            fieldWithPath("value.number").type(JsonFieldType.NUMBER).description("현재 페이지 번호").optional(),
                            fieldWithPath("value.sort").type(JsonFieldType.OBJECT).description("정렬 정보").optional(),
                            fieldWithPath("value.sort.empty").type(JsonFieldType.BOOLEAN).description("정렬 비어있음").optional(),
                            fieldWithPath("value.sort.sorted").type(JsonFieldType.BOOLEAN).description("정렬됨").optional(),
                            fieldWithPath("value.sort.unsorted").type(JsonFieldType.BOOLEAN).description("미정렬").optional(),
                            fieldWithPath("value.first").type(JsonFieldType.BOOLEAN).description("첫 페이지 여부").optional(),
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
    @DisplayName("GET /api/v1/users/experience/levels : 레벨별 필요 경험치 설정 조회")
    void getLevelConfigsTest() throws Exception {
        // given
        List<UserLevelConfig> configs = List.of(
            UserLevelConfig.builder().id(1L).level(1).requiredExp(100).cumulativeExp(0).build(),
            UserLevelConfig.builder().id(2L).level(2).requiredExp(200).cumulativeExp(100).build(),
            UserLevelConfig.builder().id(3L).level(3).requiredExp(300).cumulativeExp(300).build()
        );

        when(userExperienceService.getAllLevelConfigs()).thenReturn(configs);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/users/experience/levels")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("경험치-03. 레벨별 필요 경험치 설정 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("User Experience")
                        .description("레벨별 필요 경험치 설정 조회")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value[]").type(JsonFieldType.ARRAY).description("레벨 설정 목록"),
                            fieldWithPath("value[].id").type(JsonFieldType.NUMBER).description("설정 ID"),
                            fieldWithPath("value[].level").type(JsonFieldType.NUMBER).description("레벨"),
                            fieldWithPath("value[].required_exp").type(JsonFieldType.NUMBER).description("해당 레벨 필요 경험치"),
                            fieldWithPath("value[].cumulative_exp").type(JsonFieldType.NUMBER).description("누적 경험치").optional(),
                            fieldWithPath("value[].created_at").type(JsonFieldType.STRING).description("생성일시").optional(),
                            fieldWithPath("value[].modified_at").type(JsonFieldType.STRING).description("수정일시").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/users/experience/levels : 레벨 설정 생성/수정")
    void createOrUpdateLevelConfigTest() throws Exception {
        // given
        UserLevelConfig config = UserLevelConfig.builder()
            .id(1L)
            .level(10)
            .requiredExp(1000)
            .cumulativeExp(4500)
            .build();

        when(userExperienceService.createOrUpdateLevelConfig(anyInt(), anyInt(), any()))
            .thenReturn(config);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/users/experience/levels")
                .queryParam("level", "10")
                .queryParam("requiredExp", "1000")
                .queryParam("cumulativeExp", "4500")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("경험치-04. 레벨 설정 생성/수정",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("User Experience")
                        .description("레벨 설정 생성 또는 수정 (Admin용)")
                        .queryParameters(
                            parameterWithName("level").type(SimpleType.INTEGER).description("레벨"),
                            parameterWithName("requiredExp").type(SimpleType.INTEGER).description("필요 경험치"),
                            parameterWithName("cumulativeExp").type(SimpleType.INTEGER).description("누적 경험치").optional()
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("레벨 설정"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("설정 ID"),
                            fieldWithPath("value.level").type(JsonFieldType.NUMBER).description("레벨"),
                            fieldWithPath("value.required_exp").type(JsonFieldType.NUMBER).description("필요 경험치"),
                            fieldWithPath("value.cumulative_exp").type(JsonFieldType.NUMBER).description("누적 경험치").optional(),
                            fieldWithPath("value.created_at").type(JsonFieldType.STRING).description("생성일시").optional(),
                            fieldWithPath("value.modified_at").type(JsonFieldType.STRING).description("수정일시").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }
}
