package io.pinkspider.leveluptogethermvp.metaservice.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.metaservice.application.MissionCategoryService;
import io.pinkspider.leveluptogethermvp.metaservice.domain.dto.MissionCategoryCreateRequest;
import io.pinkspider.leveluptogethermvp.metaservice.domain.dto.MissionCategoryResponse;
import io.pinkspider.leveluptogethermvp.metaservice.domain.dto.MissionCategoryUpdateRequest;
import io.pinkspider.leveluptogethermvp.metaservice.domain.entity.MissionCategory;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
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

@WebMvcTest(controllers = MissionCategoryInternalController.class,
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
class MissionCategoryInternalControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockitoBean
    MissionCategoryService missionCategoryService;

    private MissionCategoryResponse buildCategoryResponse(Long id, String name, boolean isActive) {
        return MissionCategoryResponse.builder()
            .id(id)
            .name(name)
            .description(name + " 설명")
            .icon("icon_" + name.toLowerCase())
            .displayOrder(id.intValue())
            .isActive(isActive)
            .createdAt(LocalDateTime.now())
            .modifiedAt(LocalDateTime.now())
            .build();
    }

    @Nested
    @DisplayName("GET /api/internal/mission-categories 검색 테스트")
    class SearchCategoriesTest {

        @Test
        @DisplayName("GET /api/internal/mission-categories : 키워드로 카테고리를 검색한다")
        void searchCategories_withKeyword() throws Exception {
            // given
            MissionCategoryResponse response = buildCategoryResponse(1L, "운동", true);
            PageImpl<MissionCategoryResponse> page = new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1);

            when(missionCategoryService.searchCategories(any(), any())).thenReturn(page);

            // when
            ResultActions resultActions = mockMvc.perform(
                RestDocumentationRequestBuilders.get("/api/internal/mission-categories")
                    .param("keyword", "운동")
                    .param("page", "0")
                    .param("size", "20")
                    .contentType(MediaType.APPLICATION_JSON)
            ).andDo(
                MockMvcRestDocumentationWrapper.document("내부API-미션카테고리-01. 카테고리 검색",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Mission Category Internal")
                            .description("카테고리 검색 (페이징 + 키워드)")
                            .queryParameters(
                                parameterWithName("keyword").description("검색 키워드 (선택)").optional(),
                                parameterWithName("page").description("페이지 번호 (기본값 0)").optional(),
                                parameterWithName("size").description("페이지 크기 (기본값 20)").optional()
                            )
                            .responseFields(
                                fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("value").type(JsonFieldType.OBJECT).description("페이지 응답"),
                                fieldWithPath("value.content[]").type(JsonFieldType.ARRAY).description("카테고리 목록"),
                                fieldWithPath("value.content[].id").type(JsonFieldType.NUMBER).description("카테고리 ID"),
                                fieldWithPath("value.content[].name").type(JsonFieldType.STRING).description("카테고리 이름"),
                                fieldWithPath("value.content[].name_en").type(JsonFieldType.STRING).description("카테고리 이름 (영어)").optional(),
                                fieldWithPath("value.content[].name_ar").type(JsonFieldType.STRING).description("카테고리 이름 (아랍어)").optional(),
                                fieldWithPath("value.content[].name_ja").type(JsonFieldType.STRING).description("카테고리 이름 (일본어)").optional(),
                                fieldWithPath("value.content[].description").type(JsonFieldType.STRING).description("카테고리 설명").optional(),
                                fieldWithPath("value.content[].description_en").type(JsonFieldType.STRING).description("카테고리 설명 (영어)").optional(),
                                fieldWithPath("value.content[].description_ar").type(JsonFieldType.STRING).description("카테고리 설명 (아랍어)").optional(),
                                fieldWithPath("value.content[].description_ja").type(JsonFieldType.STRING).description("카테고리 설명 (일본어)").optional(),
                                fieldWithPath("value.content[].icon").type(JsonFieldType.STRING).description("아이콘").optional(),
                                fieldWithPath("value.content[].display_order").type(JsonFieldType.NUMBER).description("표시 순서").optional(),
                                fieldWithPath("value.content[].is_active").type(JsonFieldType.BOOLEAN).description("활성화 여부"),
                                fieldWithPath("value.content[].created_at").type(JsonFieldType.STRING).description("생성일시").optional(),
                                fieldWithPath("value.content[].modified_at").type(JsonFieldType.STRING).description("수정일시").optional(),
                                fieldWithPath("value.page").type(JsonFieldType.NUMBER).description("현재 페이지"),
                                fieldWithPath("value.size").type(JsonFieldType.NUMBER).description("페이지 크기"),
                                fieldWithPath("value.total_elements").type(JsonFieldType.NUMBER).description("총 요소 수"),
                                fieldWithPath("value.total_pages").type(JsonFieldType.NUMBER).description("총 페이지 수"),
                                fieldWithPath("value.first").type(JsonFieldType.BOOLEAN).description("첫 번째 페이지 여부"),
                                fieldWithPath("value.last").type(JsonFieldType.BOOLEAN).description("마지막 페이지 여부")
                            )
                            .build()
                    )
                )
            );

            // then
            resultActions.andExpect(MockMvcResultMatchers.status().isOk());
        }

        @Test
        @DisplayName("GET /api/internal/mission-categories : 키워드 없이 전체 카테고리를 검색한다")
        void searchCategories_withoutKeyword() throws Exception {
            // given
            MissionCategoryResponse r1 = buildCategoryResponse(1L, "운동", true);
            MissionCategoryResponse r2 = buildCategoryResponse(2L, "독서", false);
            PageImpl<MissionCategoryResponse> page = new PageImpl<>(List.of(r1, r2), PageRequest.of(0, 20), 2);

            when(missionCategoryService.searchCategories(any(), any())).thenReturn(page);

            // when
            ResultActions resultActions = mockMvc.perform(
                RestDocumentationRequestBuilders.get("/api/internal/mission-categories")
                    .contentType(MediaType.APPLICATION_JSON)
            );

            // then
            resultActions.andExpect(MockMvcResultMatchers.status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api/internal/mission-categories/all 테스트")
    class GetAllCategoriesTest {

        @Test
        @DisplayName("GET /api/internal/mission-categories/all : 비활성화 포함 전체 카테고리를 조회한다")
        void getAllCategories_success() throws Exception {
            // given
            List<MissionCategoryResponse> categories = List.of(
                buildCategoryResponse(1L, "운동", true),
                buildCategoryResponse(2L, "독서", false)
            );

            when(missionCategoryService.getAllCategories()).thenReturn(categories);

            // when
            ResultActions resultActions = mockMvc.perform(
                RestDocumentationRequestBuilders.get("/api/internal/mission-categories/all")
                    .contentType(MediaType.APPLICATION_JSON)
            ).andDo(
                MockMvcRestDocumentationWrapper.document("내부API-미션카테고리-02. 전체 카테고리 조회",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Mission Category Internal")
                            .description("전체 카테고리 목록 조회 (비활성화 포함)")
                            .responseFields(
                                fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("value[]").type(JsonFieldType.ARRAY).description("카테고리 목록"),
                                fieldWithPath("value[].id").type(JsonFieldType.NUMBER).description("카테고리 ID"),
                                fieldWithPath("value[].name").type(JsonFieldType.STRING).description("카테고리 이름"),
                                fieldWithPath("value[].name_en").type(JsonFieldType.STRING).description("카테고리 이름 (영어)").optional(),
                                fieldWithPath("value[].name_ar").type(JsonFieldType.STRING).description("카테고리 이름 (아랍어)").optional(),
                                fieldWithPath("value[].name_ja").type(JsonFieldType.STRING).description("카테고리 이름 (일본어)").optional(),
                                fieldWithPath("value[].description").type(JsonFieldType.STRING).description("카테고리 설명").optional(),
                                fieldWithPath("value[].description_en").type(JsonFieldType.STRING).description("카테고리 설명 (영어)").optional(),
                                fieldWithPath("value[].description_ar").type(JsonFieldType.STRING).description("카테고리 설명 (아랍어)").optional(),
                                fieldWithPath("value[].description_ja").type(JsonFieldType.STRING).description("카테고리 설명 (일본어)").optional(),
                                fieldWithPath("value[].icon").type(JsonFieldType.STRING).description("아이콘").optional(),
                                fieldWithPath("value[].display_order").type(JsonFieldType.NUMBER).description("표시 순서").optional(),
                                fieldWithPath("value[].is_active").type(JsonFieldType.BOOLEAN).description("활성화 여부"),
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
    }

    @Nested
    @DisplayName("GET /api/internal/mission-categories/active 테스트")
    class GetActiveCategoriesTest {

        @Test
        @DisplayName("GET /api/internal/mission-categories/active : 활성화된 카테고리만 조회한다")
        void getActiveCategories_success() throws Exception {
            // given
            List<MissionCategoryResponse> categories = List.of(
                buildCategoryResponse(1L, "운동", true)
            );

            when(missionCategoryService.getActiveCategories()).thenReturn(categories);

            // when
            ResultActions resultActions = mockMvc.perform(
                RestDocumentationRequestBuilders.get("/api/internal/mission-categories/active")
                    .contentType(MediaType.APPLICATION_JSON)
            ).andDo(
                MockMvcRestDocumentationWrapper.document("내부API-미션카테고리-03. 활성 카테고리 조회",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Mission Category Internal")
                            .description("활성화된 카테고리만 조회")
                            .responseFields(
                                fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("value[]").type(JsonFieldType.ARRAY).description("카테고리 목록"),
                                fieldWithPath("value[].id").type(JsonFieldType.NUMBER).description("카테고리 ID"),
                                fieldWithPath("value[].name").type(JsonFieldType.STRING).description("카테고리 이름"),
                                fieldWithPath("value[].name_en").type(JsonFieldType.STRING).description("카테고리 이름 (영어)").optional(),
                                fieldWithPath("value[].name_ar").type(JsonFieldType.STRING).description("카테고리 이름 (아랍어)").optional(),
                                fieldWithPath("value[].name_ja").type(JsonFieldType.STRING).description("카테고리 이름 (일본어)").optional(),
                                fieldWithPath("value[].description").type(JsonFieldType.STRING).description("카테고리 설명").optional(),
                                fieldWithPath("value[].description_en").type(JsonFieldType.STRING).description("카테고리 설명 (영어)").optional(),
                                fieldWithPath("value[].description_ar").type(JsonFieldType.STRING).description("카테고리 설명 (아랍어)").optional(),
                                fieldWithPath("value[].description_ja").type(JsonFieldType.STRING).description("카테고리 설명 (일본어)").optional(),
                                fieldWithPath("value[].icon").type(JsonFieldType.STRING).description("아이콘").optional(),
                                fieldWithPath("value[].display_order").type(JsonFieldType.NUMBER).description("표시 순서").optional(),
                                fieldWithPath("value[].is_active").type(JsonFieldType.BOOLEAN).description("활성화 여부"),
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
    }

    @Nested
    @DisplayName("GET /api/internal/mission-categories/{id} 테스트")
    class GetCategoryTest {

        @Test
        @DisplayName("GET /api/internal/mission-categories/{id} : 카테고리 단건 조회한다")
        void getCategory_success() throws Exception {
            // given
            MissionCategoryResponse response = buildCategoryResponse(1L, "운동", true);
            when(missionCategoryService.getCategory(anyLong())).thenReturn(response);

            // when
            ResultActions resultActions = mockMvc.perform(
                RestDocumentationRequestBuilders.get("/api/internal/mission-categories/{id}", 1L)
                    .contentType(MediaType.APPLICATION_JSON)
            ).andDo(
                MockMvcRestDocumentationWrapper.document("내부API-미션카테고리-04. 카테고리 단건 조회",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Mission Category Internal")
                            .description("카테고리 단건 조회")
                            .pathParameters(
                                parameterWithName("id").description("카테고리 ID")
                            )
                            .responseFields(
                                fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("value").type(JsonFieldType.OBJECT).description("카테고리 정보"),
                                fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("카테고리 ID"),
                                fieldWithPath("value.name").type(JsonFieldType.STRING).description("카테고리 이름"),
                                fieldWithPath("value.name_en").type(JsonFieldType.STRING).description("카테고리 이름 (영어)").optional(),
                                fieldWithPath("value.name_ar").type(JsonFieldType.STRING).description("카테고리 이름 (아랍어)").optional(),
                                fieldWithPath("value.name_ja").type(JsonFieldType.STRING).description("카테고리 이름 (일본어)").optional(),
                                fieldWithPath("value.description").type(JsonFieldType.STRING).description("카테고리 설명").optional(),
                                fieldWithPath("value.description_en").type(JsonFieldType.STRING).description("카테고리 설명 (영어)").optional(),
                                fieldWithPath("value.description_ar").type(JsonFieldType.STRING).description("카테고리 설명 (아랍어)").optional(),
                                fieldWithPath("value.description_ja").type(JsonFieldType.STRING).description("카테고리 설명 (일본어)").optional(),
                                fieldWithPath("value.icon").type(JsonFieldType.STRING).description("아이콘").optional(),
                                fieldWithPath("value.display_order").type(JsonFieldType.NUMBER).description("표시 순서").optional(),
                                fieldWithPath("value.is_active").type(JsonFieldType.BOOLEAN).description("활성화 여부"),
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

    @Nested
    @DisplayName("GET /api/internal/mission-categories/by-name/{name} 테스트")
    class GetCategoryByNameTest {

        @Test
        @DisplayName("GET /api/internal/mission-categories/by-name/{name} : 이름으로 카테고리를 조회한다")
        void getCategoryByName_success() throws Exception {
            // given
            MissionCategory category = MissionCategory.builder()
                .name("운동")
                .description("운동 설명")
                .icon("icon_exercise")
                .displayOrder(1)
                .isActive(true)
                .build();

            when(missionCategoryService.findByName("운동")).thenReturn(category);

            // when
            ResultActions resultActions = mockMvc.perform(
                RestDocumentationRequestBuilders.get("/api/internal/mission-categories/by-name/{name}", "운동")
                    .contentType(MediaType.APPLICATION_JSON)
            ).andDo(
                MockMvcRestDocumentationWrapper.document("내부API-미션카테고리-05. 이름으로 카테고리 조회",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Mission Category Internal")
                            .description("카테고리 이름으로 조회")
                            .pathParameters(
                                parameterWithName("name").description("카테고리 이름")
                            )
                            .responseFields(
                                fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("value").type(JsonFieldType.OBJECT).description("카테고리 정보"),
                                fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("카테고리 ID").optional(),
                                fieldWithPath("value.name").type(JsonFieldType.STRING).description("카테고리 이름"),
                                fieldWithPath("value.name_en").type(JsonFieldType.STRING).description("카테고리 이름 (영어)").optional(),
                                fieldWithPath("value.name_ar").type(JsonFieldType.STRING).description("카테고리 이름 (아랍어)").optional(),
                                fieldWithPath("value.name_ja").type(JsonFieldType.STRING).description("카테고리 이름 (일본어)").optional(),
                                fieldWithPath("value.description").type(JsonFieldType.STRING).description("카테고리 설명").optional(),
                                fieldWithPath("value.description_en").type(JsonFieldType.STRING).description("카테고리 설명 (영어)").optional(),
                                fieldWithPath("value.description_ar").type(JsonFieldType.STRING).description("카테고리 설명 (아랍어)").optional(),
                                fieldWithPath("value.description_ja").type(JsonFieldType.STRING).description("카테고리 설명 (일본어)").optional(),
                                fieldWithPath("value.icon").type(JsonFieldType.STRING).description("아이콘").optional(),
                                fieldWithPath("value.display_order").type(JsonFieldType.NUMBER).description("표시 순서").optional(),
                                fieldWithPath("value.is_active").type(JsonFieldType.BOOLEAN).description("활성화 여부"),
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

        @Test
        @DisplayName("GET /api/internal/mission-categories/by-name/{name} : 존재하지 않는 이름이면 CustomException이 발생한다")
        void getCategoryByName_notFound_throwsException() {
            // given
            when(missionCategoryService.findByName("없는카테고리")).thenReturn(null);

            // when & then — 테스트 환경에서 RestExceptionHandler가 없어 ServletException으로 전파됨
            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                mockMvc.perform(
                    RestDocumentationRequestBuilders.get("/api/internal/mission-categories/by-name/{name}", "없는카테고리")
                        .contentType(MediaType.APPLICATION_JSON)
                )
            ).hasCauseInstanceOf(io.pinkspider.global.exception.CustomException.class);
        }
    }

    @Nested
    @DisplayName("GET /api/internal/mission-categories/by-ids 테스트")
    class GetCategoriesByIdsTest {

        @Test
        @DisplayName("GET /api/internal/mission-categories/by-ids : ID 목록으로 카테고리를 배치 조회한다")
        void getCategoriesByIds_success() throws Exception {
            // given
            List<MissionCategoryResponse> responses = List.of(
                buildCategoryResponse(1L, "운동", true),
                buildCategoryResponse(2L, "독서", true)
            );

            when(missionCategoryService.getCategoriesByIds(any())).thenReturn(responses);

            // when
            ResultActions resultActions = mockMvc.perform(
                RestDocumentationRequestBuilders.get("/api/internal/mission-categories/by-ids")
                    .param("ids", "1", "2")
                    .contentType(MediaType.APPLICATION_JSON)
            ).andDo(
                MockMvcRestDocumentationWrapper.document("내부API-미션카테고리-06. ID 배치 조회",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Mission Category Internal")
                            .description("카테고리 ID 목록으로 배치 조회")
                            .queryParameters(
                                parameterWithName("ids").description("카테고리 ID 목록")
                            )
                            .responseFields(
                                fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("value[]").type(JsonFieldType.ARRAY).description("카테고리 목록"),
                                fieldWithPath("value[].id").type(JsonFieldType.NUMBER).description("카테고리 ID"),
                                fieldWithPath("value[].name").type(JsonFieldType.STRING).description("카테고리 이름"),
                                fieldWithPath("value[].name_en").type(JsonFieldType.STRING).description("카테고리 이름 (영어)").optional(),
                                fieldWithPath("value[].name_ar").type(JsonFieldType.STRING).description("카테고리 이름 (아랍어)").optional(),
                                fieldWithPath("value[].name_ja").type(JsonFieldType.STRING).description("카테고리 이름 (일본어)").optional(),
                                fieldWithPath("value[].description").type(JsonFieldType.STRING).description("카테고리 설명").optional(),
                                fieldWithPath("value[].description_en").type(JsonFieldType.STRING).description("카테고리 설명 (영어)").optional(),
                                fieldWithPath("value[].description_ar").type(JsonFieldType.STRING).description("카테고리 설명 (아랍어)").optional(),
                                fieldWithPath("value[].description_ja").type(JsonFieldType.STRING).description("카테고리 설명 (일본어)").optional(),
                                fieldWithPath("value[].icon").type(JsonFieldType.STRING).description("아이콘").optional(),
                                fieldWithPath("value[].display_order").type(JsonFieldType.NUMBER).description("표시 순서").optional(),
                                fieldWithPath("value[].is_active").type(JsonFieldType.BOOLEAN).description("활성화 여부"),
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
    }

    @Nested
    @DisplayName("POST /api/internal/mission-categories 테스트")
    class CreateCategoryTest {

        @Test
        @DisplayName("POST /api/internal/mission-categories : 카테고리를 생성한다")
        void createCategory_success() throws Exception {
            // given
            MissionCategoryCreateRequest request = MissionCategoryCreateRequest.builder()
                .name("새 카테고리")
                .description("새로운 카테고리 설명")
                .icon("icon_new")
                .displayOrder(10)
                .build();

            MissionCategoryResponse response = buildCategoryResponse(10L, "새 카테고리", true);

            when(missionCategoryService.createCategory(any())).thenReturn(response);

            // when
            ResultActions resultActions = mockMvc.perform(
                RestDocumentationRequestBuilders.post("/api/internal/mission-categories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            ).andDo(
                MockMvcRestDocumentationWrapper.document("내부API-미션카테고리-07. 카테고리 생성",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Mission Category Internal")
                            .description("카테고리 생성 (Admin Backend 전용)")
                            .requestFields(
                                fieldWithPath("name").type(JsonFieldType.STRING).description("카테고리 이름"),
                                fieldWithPath("name_en").type(JsonFieldType.STRING).description("카테고리 이름 (영어)").optional(),
                                fieldWithPath("name_ar").type(JsonFieldType.STRING).description("카테고리 이름 (아랍어)").optional(),
                                fieldWithPath("name_ja").type(JsonFieldType.STRING).description("카테고리 이름 (일본어)").optional(),
                                fieldWithPath("description").type(JsonFieldType.STRING).description("카테고리 설명").optional(),
                                fieldWithPath("description_en").type(JsonFieldType.STRING).description("카테고리 설명 (영어)").optional(),
                                fieldWithPath("description_ar").type(JsonFieldType.STRING).description("카테고리 설명 (아랍어)").optional(),
                                fieldWithPath("description_ja").type(JsonFieldType.STRING).description("카테고리 설명 (일본어)").optional(),
                                fieldWithPath("icon").type(JsonFieldType.STRING).description("아이콘").optional(),
                                fieldWithPath("display_order").type(JsonFieldType.NUMBER).description("표시 순서").optional()
                            )
                            .responseFields(
                                fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("value").type(JsonFieldType.OBJECT).description("생성된 카테고리 정보"),
                                fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("카테고리 ID"),
                                fieldWithPath("value.name").type(JsonFieldType.STRING).description("카테고리 이름"),
                                fieldWithPath("value.name_en").type(JsonFieldType.STRING).description("카테고리 이름 (영어)").optional(),
                                fieldWithPath("value.name_ar").type(JsonFieldType.STRING).description("카테고리 이름 (아랍어)").optional(),
                                fieldWithPath("value.name_ja").type(JsonFieldType.STRING).description("카테고리 이름 (일본어)").optional(),
                                fieldWithPath("value.description").type(JsonFieldType.STRING).description("카테고리 설명").optional(),
                                fieldWithPath("value.description_en").type(JsonFieldType.STRING).description("카테고리 설명 (영어)").optional(),
                                fieldWithPath("value.description_ar").type(JsonFieldType.STRING).description("카테고리 설명 (아랍어)").optional(),
                                fieldWithPath("value.description_ja").type(JsonFieldType.STRING).description("카테고리 설명 (일본어)").optional(),
                                fieldWithPath("value.icon").type(JsonFieldType.STRING).description("아이콘").optional(),
                                fieldWithPath("value.display_order").type(JsonFieldType.NUMBER).description("표시 순서").optional(),
                                fieldWithPath("value.is_active").type(JsonFieldType.BOOLEAN).description("활성화 여부"),
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

    @Nested
    @DisplayName("PUT /api/internal/mission-categories/{id} 테스트")
    class UpdateCategoryTest {

        @Test
        @DisplayName("PUT /api/internal/mission-categories/{id} : 카테고리를 수정한다")
        void updateCategory_success() throws Exception {
            // given
            MissionCategoryUpdateRequest request = MissionCategoryUpdateRequest.builder()
                .name("수정된 카테고리")
                .description("수정된 설명")
                .isActive(true)
                .build();

            MissionCategoryResponse response = buildCategoryResponse(1L, "수정된 카테고리", true);

            when(missionCategoryService.updateCategory(anyLong(), any())).thenReturn(response);

            // when
            ResultActions resultActions = mockMvc.perform(
                RestDocumentationRequestBuilders.put("/api/internal/mission-categories/{id}", 1L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            ).andDo(
                MockMvcRestDocumentationWrapper.document("내부API-미션카테고리-08. 카테고리 수정",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Mission Category Internal")
                            .description("카테고리 수정 (Admin Backend 전용)")
                            .pathParameters(
                                parameterWithName("id").description("카테고리 ID")
                            )
                            .requestFields(
                                fieldWithPath("name").type(JsonFieldType.STRING).description("카테고리 이름").optional(),
                                fieldWithPath("name_en").type(JsonFieldType.STRING).description("카테고리 이름 (영어)").optional(),
                                fieldWithPath("name_ar").type(JsonFieldType.STRING).description("카테고리 이름 (아랍어)").optional(),
                                fieldWithPath("name_ja").type(JsonFieldType.STRING).description("카테고리 이름 (일본어)").optional(),
                                fieldWithPath("description").type(JsonFieldType.STRING).description("카테고리 설명").optional(),
                                fieldWithPath("description_en").type(JsonFieldType.STRING).description("카테고리 설명 (영어)").optional(),
                                fieldWithPath("description_ar").type(JsonFieldType.STRING).description("카테고리 설명 (아랍어)").optional(),
                                fieldWithPath("description_ja").type(JsonFieldType.STRING).description("카테고리 설명 (일본어)").optional(),
                                fieldWithPath("icon").type(JsonFieldType.STRING).description("아이콘").optional(),
                                fieldWithPath("display_order").type(JsonFieldType.NUMBER).description("표시 순서").optional(),
                                fieldWithPath("is_active").type(JsonFieldType.BOOLEAN).description("활성화 여부").optional()
                            )
                            .responseFields(
                                fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("value").type(JsonFieldType.OBJECT).description("수정된 카테고리 정보"),
                                fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("카테고리 ID"),
                                fieldWithPath("value.name").type(JsonFieldType.STRING).description("카테고리 이름"),
                                fieldWithPath("value.name_en").type(JsonFieldType.STRING).description("카테고리 이름 (영어)").optional(),
                                fieldWithPath("value.name_ar").type(JsonFieldType.STRING).description("카테고리 이름 (아랍어)").optional(),
                                fieldWithPath("value.name_ja").type(JsonFieldType.STRING).description("카테고리 이름 (일본어)").optional(),
                                fieldWithPath("value.description").type(JsonFieldType.STRING).description("카테고리 설명").optional(),
                                fieldWithPath("value.description_en").type(JsonFieldType.STRING).description("카테고리 설명 (영어)").optional(),
                                fieldWithPath("value.description_ar").type(JsonFieldType.STRING).description("카테고리 설명 (아랍어)").optional(),
                                fieldWithPath("value.description_ja").type(JsonFieldType.STRING).description("카테고리 설명 (일본어)").optional(),
                                fieldWithPath("value.icon").type(JsonFieldType.STRING).description("아이콘").optional(),
                                fieldWithPath("value.display_order").type(JsonFieldType.NUMBER).description("표시 순서").optional(),
                                fieldWithPath("value.is_active").type(JsonFieldType.BOOLEAN).description("활성화 여부"),
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

    @Nested
    @DisplayName("DELETE /api/internal/mission-categories/{id} 테스트")
    class DeleteCategoryTest {

        @Test
        @DisplayName("DELETE /api/internal/mission-categories/{id} : 카테고리를 삭제한다")
        void deleteCategory_success() throws Exception {
            // given
            doNothing().when(missionCategoryService).deleteCategory(anyLong());

            // when
            ResultActions resultActions = mockMvc.perform(
                RestDocumentationRequestBuilders.delete("/api/internal/mission-categories/{id}", 1L)
                    .contentType(MediaType.APPLICATION_JSON)
            ).andDo(
                MockMvcRestDocumentationWrapper.document("내부API-미션카테고리-09. 카테고리 삭제",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Mission Category Internal")
                            .description("카테고리 삭제 (Admin Backend 전용)")
                            .pathParameters(
                                parameterWithName("id").description("카테고리 ID")
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

    @Nested
    @DisplayName("POST /api/internal/mission-categories/{id}/toggle-active 테스트")
    class ToggleActiveTest {

        @Test
        @DisplayName("POST /api/internal/mission-categories/{id}/toggle-active : 카테고리 활성화 상태를 토글한다")
        void toggleActive_success() throws Exception {
            // given
            MissionCategoryResponse response = buildCategoryResponse(1L, "운동", false);

            when(missionCategoryService.toggleActive(anyLong())).thenReturn(response);

            // when
            ResultActions resultActions = mockMvc.perform(
                RestDocumentationRequestBuilders.post("/api/internal/mission-categories/{id}/toggle-active", 1L)
                    .contentType(MediaType.APPLICATION_JSON)
            ).andDo(
                MockMvcRestDocumentationWrapper.document("내부API-미션카테고리-10. 카테고리 활성화 토글",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Mission Category Internal")
                            .description("카테고리 활성화 상태 토글 (Admin Backend 전용)")
                            .pathParameters(
                                parameterWithName("id").description("카테고리 ID")
                            )
                            .responseFields(
                                fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("value").type(JsonFieldType.OBJECT).description("토글된 카테고리 정보"),
                                fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("카테고리 ID"),
                                fieldWithPath("value.name").type(JsonFieldType.STRING).description("카테고리 이름"),
                                fieldWithPath("value.name_en").type(JsonFieldType.STRING).description("카테고리 이름 (영어)").optional(),
                                fieldWithPath("value.name_ar").type(JsonFieldType.STRING).description("카테고리 이름 (아랍어)").optional(),
                                fieldWithPath("value.name_ja").type(JsonFieldType.STRING).description("카테고리 이름 (일본어)").optional(),
                                fieldWithPath("value.description").type(JsonFieldType.STRING).description("카테고리 설명").optional(),
                                fieldWithPath("value.description_en").type(JsonFieldType.STRING).description("카테고리 설명 (영어)").optional(),
                                fieldWithPath("value.description_ar").type(JsonFieldType.STRING).description("카테고리 설명 (아랍어)").optional(),
                                fieldWithPath("value.description_ja").type(JsonFieldType.STRING).description("카테고리 설명 (일본어)").optional(),
                                fieldWithPath("value.icon").type(JsonFieldType.STRING).description("아이콘").optional(),
                                fieldWithPath("value.display_order").type(JsonFieldType.NUMBER).description("표시 순서").optional(),
                                fieldWithPath("value.is_active").type(JsonFieldType.BOOLEAN).description("활성화 여부"),
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
}
