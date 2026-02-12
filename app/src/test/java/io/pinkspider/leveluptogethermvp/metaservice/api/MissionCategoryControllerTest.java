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

@WebMvcTest(controllers = MissionCategoryController.class,
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
class MissionCategoryControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockitoBean
    MissionCategoryService missionCategoryService;

    @Test
    @DisplayName("GET /api/v1/mission-categories : 활성 카테고리 목록 조회")
    void getActiveCategoriesTest() throws Exception {
        // given
        List<MissionCategoryResponse> categories = List.of(
            MissionCategoryResponse.builder()
                .id(1L)
                .name("운동")
                .description("건강과 체력을 위한 운동 관련 미션")
                .icon("\uD83C\uDFC3")
                .displayOrder(1)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .modifiedAt(LocalDateTime.now())
                .build(),
            MissionCategoryResponse.builder()
                .id(2L)
                .name("공부")
                .description("학습과 자기계발을 위한 미션")
                .icon("\uD83D\uDCDA")
                .displayOrder(2)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .modifiedAt(LocalDateTime.now())
                .build()
        );

        when(missionCategoryService.getActiveCategories()).thenReturn(categories);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/mission-categories")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("미션카테고리-01. 활성 카테고리 목록 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Mission Category")
                        .description("활성화된 미션 카테고리 목록 조회 (사용자용)")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value[]").type(JsonFieldType.ARRAY).description("카테고리 목록"),
                            fieldWithPath("value[].id").type(JsonFieldType.NUMBER).description("카테고리 ID"),
                            fieldWithPath("value[].name").type(JsonFieldType.STRING).description("카테고리 이름"),
                            fieldWithPath("value[].name_en").type(JsonFieldType.STRING).description("카테고리 이름 (영어)").optional(),
                            fieldWithPath("value[].name_ar").type(JsonFieldType.STRING).description("카테고리 이름 (아랍어)").optional(),
                            fieldWithPath("value[].description").type(JsonFieldType.STRING).description("카테고리 설명").optional(),
                            fieldWithPath("value[].description_en").type(JsonFieldType.STRING).description("카테고리 설명 (영어)").optional(),
                            fieldWithPath("value[].description_ar").type(JsonFieldType.STRING).description("카테고리 설명 (아랍어)").optional(),
                            fieldWithPath("value[].icon").type(JsonFieldType.STRING).description("카테고리 아이콘").optional(),
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

    @Test
    @DisplayName("GET /api/v1/mission-categories/{categoryId} : 카테고리 단건 조회")
    void getCategoryTest() throws Exception {
        // given
        MissionCategoryResponse category = MissionCategoryResponse.builder()
            .id(1L)
            .name("운동")
            .description("건강과 체력을 위한 운동 관련 미션")
            .icon("\uD83C\uDFC3")
            .displayOrder(1)
            .isActive(true)
            .createdAt(LocalDateTime.now())
            .modifiedAt(LocalDateTime.now())
            .build();

        when(missionCategoryService.getCategory(anyLong())).thenReturn(category);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/mission-categories/{categoryId}", 1L)
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("미션카테고리-02. 카테고리 단건 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Mission Category")
                        .description("미션 카테고리 단건 조회")
                        .pathParameters(
                            parameterWithName("categoryId").description("카테고리 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("카테고리 정보"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("카테고리 ID"),
                            fieldWithPath("value.name").type(JsonFieldType.STRING).description("카테고리 이름"),
                            fieldWithPath("value.name_en").type(JsonFieldType.STRING).description("카테고리 이름 (영어)").optional(),
                            fieldWithPath("value.name_ar").type(JsonFieldType.STRING).description("카테고리 이름 (아랍어)").optional(),
                            fieldWithPath("value.description").type(JsonFieldType.STRING).description("카테고리 설명").optional(),
                            fieldWithPath("value.description_en").type(JsonFieldType.STRING).description("카테고리 설명 (영어)").optional(),
                            fieldWithPath("value.description_ar").type(JsonFieldType.STRING).description("카테고리 설명 (아랍어)").optional(),
                            fieldWithPath("value.icon").type(JsonFieldType.STRING).description("카테고리 아이콘").optional(),
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
    @DisplayName("GET /api/v1/mission-categories/admin/all : 전체 카테고리 목록 조회 (Admin)")
    void getAllCategoriesTest() throws Exception {
        // given
        List<MissionCategoryResponse> categories = List.of(
            MissionCategoryResponse.builder()
                .id(1L)
                .name("운동")
                .description("건강과 체력을 위한 운동 관련 미션")
                .icon("\uD83C\uDFC3")
                .displayOrder(1)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .modifiedAt(LocalDateTime.now())
                .build(),
            MissionCategoryResponse.builder()
                .id(2L)
                .name("비활성카테고리")
                .description("비활성화된 카테고리")
                .icon("\u274C")
                .displayOrder(99)
                .isActive(false)
                .createdAt(LocalDateTime.now())
                .modifiedAt(LocalDateTime.now())
                .build()
        );

        when(missionCategoryService.getAllCategories()).thenReturn(categories);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/mission-categories/admin/all")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("미션카테고리-03. 전체 카테고리 목록 조회 (Admin)",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Mission Category Admin")
                        .description("전체 미션 카테고리 목록 조회 (비활성화 포함, Admin용)")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value[]").type(JsonFieldType.ARRAY).description("카테고리 목록"),
                            fieldWithPath("value[].id").type(JsonFieldType.NUMBER).description("카테고리 ID"),
                            fieldWithPath("value[].name").type(JsonFieldType.STRING).description("카테고리 이름"),
                            fieldWithPath("value[].name_en").type(JsonFieldType.STRING).description("카테고리 이름 (영어)").optional(),
                            fieldWithPath("value[].name_ar").type(JsonFieldType.STRING).description("카테고리 이름 (아랍어)").optional(),
                            fieldWithPath("value[].description").type(JsonFieldType.STRING).description("카테고리 설명").optional(),
                            fieldWithPath("value[].description_en").type(JsonFieldType.STRING).description("카테고리 설명 (영어)").optional(),
                            fieldWithPath("value[].description_ar").type(JsonFieldType.STRING).description("카테고리 설명 (아랍어)").optional(),
                            fieldWithPath("value[].icon").type(JsonFieldType.STRING).description("카테고리 아이콘").optional(),
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

    @Test
    @DisplayName("POST /api/v1/mission-categories/admin : 카테고리 생성 (Admin)")
    void createCategoryTest() throws Exception {
        // given
        MissionCategoryCreateRequest request = MissionCategoryCreateRequest.builder()
            .name("새 카테고리")
            .description("새로운 카테고리 설명")
            .icon("\uD83C\uDD95")
            .displayOrder(10)
            .build();

        MissionCategoryResponse response = MissionCategoryResponse.builder()
            .id(10L)
            .name("새 카테고리")
            .description("새로운 카테고리 설명")
            .icon("\uD83C\uDD95")
            .displayOrder(10)
            .isActive(true)
            .createdAt(LocalDateTime.now())
            .modifiedAt(LocalDateTime.now())
            .build();

        when(missionCategoryService.createCategory(any())).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/mission-categories/admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andDo(
            MockMvcRestDocumentationWrapper.document("미션카테고리-04. 카테고리 생성 (Admin)",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Mission Category Admin")
                        .description("미션 카테고리 생성 (Admin용)")
                        .requestFields(
                            fieldWithPath("name").type(JsonFieldType.STRING).description("카테고리 이름"),
                            fieldWithPath("description").type(JsonFieldType.STRING).description("카테고리 설명").optional(),
                            fieldWithPath("icon").type(JsonFieldType.STRING).description("카테고리 아이콘").optional(),
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
                            fieldWithPath("value.description").type(JsonFieldType.STRING).description("카테고리 설명").optional(),
                            fieldWithPath("value.description_en").type(JsonFieldType.STRING).description("카테고리 설명 (영어)").optional(),
                            fieldWithPath("value.description_ar").type(JsonFieldType.STRING).description("카테고리 설명 (아랍어)").optional(),
                            fieldWithPath("value.icon").type(JsonFieldType.STRING).description("카테고리 아이콘").optional(),
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
    @DisplayName("PUT /api/v1/mission-categories/admin/{categoryId} : 카테고리 수정 (Admin)")
    void updateCategoryTest() throws Exception {
        // given
        MissionCategoryUpdateRequest request = MissionCategoryUpdateRequest.builder()
            .name("수정된 카테고리")
            .description("수정된 설명")
            .icon("\u270F\uFE0F")
            .displayOrder(5)
            .isActive(true)
            .build();

        MissionCategoryResponse response = MissionCategoryResponse.builder()
            .id(1L)
            .name("수정된 카테고리")
            .description("수정된 설명")
            .icon("\u270F\uFE0F")
            .displayOrder(5)
            .isActive(true)
            .createdAt(LocalDateTime.now())
            .modifiedAt(LocalDateTime.now())
            .build();

        when(missionCategoryService.updateCategory(anyLong(), any())).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.put("/api/v1/mission-categories/admin/{categoryId}", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andDo(
            MockMvcRestDocumentationWrapper.document("미션카테고리-05. 카테고리 수정 (Admin)",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Mission Category Admin")
                        .description("미션 카테고리 수정 (Admin용)")
                        .pathParameters(
                            parameterWithName("categoryId").description("카테고리 ID")
                        )
                        .requestFields(
                            fieldWithPath("name").type(JsonFieldType.STRING).description("카테고리 이름").optional(),
                            fieldWithPath("description").type(JsonFieldType.STRING).description("카테고리 설명").optional(),
                            fieldWithPath("icon").type(JsonFieldType.STRING).description("카테고리 아이콘").optional(),
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
                            fieldWithPath("value.description").type(JsonFieldType.STRING).description("카테고리 설명").optional(),
                            fieldWithPath("value.description_en").type(JsonFieldType.STRING).description("카테고리 설명 (영어)").optional(),
                            fieldWithPath("value.description_ar").type(JsonFieldType.STRING).description("카테고리 설명 (아랍어)").optional(),
                            fieldWithPath("value.icon").type(JsonFieldType.STRING).description("카테고리 아이콘").optional(),
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
    @DisplayName("DELETE /api/v1/mission-categories/admin/{categoryId} : 카테고리 삭제 (Admin)")
    void deleteCategoryTest() throws Exception {
        // given
        doNothing().when(missionCategoryService).deleteCategory(anyLong());

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.delete("/api/v1/mission-categories/admin/{categoryId}", 1L)
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("미션카테고리-06. 카테고리 삭제 (Admin)",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Mission Category Admin")
                        .description("미션 카테고리 삭제 (Admin용)")
                        .pathParameters(
                            parameterWithName("categoryId").description("카테고리 ID")
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
    @DisplayName("POST /api/v1/mission-categories/admin/{categoryId}/deactivate : 카테고리 비활성화 (Admin)")
    void deactivateCategoryTest() throws Exception {
        // given
        MissionCategoryResponse response = MissionCategoryResponse.builder()
            .id(1L)
            .name("운동")
            .description("건강과 체력을 위한 운동 관련 미션")
            .icon("\uD83C\uDFC3")
            .displayOrder(1)
            .isActive(false)
            .createdAt(LocalDateTime.now())
            .modifiedAt(LocalDateTime.now())
            .build();

        when(missionCategoryService.deactivateCategory(anyLong())).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/mission-categories/admin/{categoryId}/deactivate", 1L)
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("미션카테고리-07. 카테고리 비활성화 (Admin)",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Mission Category Admin")
                        .description("미션 카테고리 비활성화 (Admin용) - 삭제 대신 비활성화")
                        .pathParameters(
                            parameterWithName("categoryId").description("카테고리 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("비활성화된 카테고리 정보"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("카테고리 ID"),
                            fieldWithPath("value.name").type(JsonFieldType.STRING).description("카테고리 이름"),
                            fieldWithPath("value.name_en").type(JsonFieldType.STRING).description("카테고리 이름 (영어)").optional(),
                            fieldWithPath("value.name_ar").type(JsonFieldType.STRING).description("카테고리 이름 (아랍어)").optional(),
                            fieldWithPath("value.description").type(JsonFieldType.STRING).description("카테고리 설명").optional(),
                            fieldWithPath("value.description_en").type(JsonFieldType.STRING).description("카테고리 설명 (영어)").optional(),
                            fieldWithPath("value.description_ar").type(JsonFieldType.STRING).description("카테고리 설명 (아랍어)").optional(),
                            fieldWithPath("value.icon").type(JsonFieldType.STRING).description("카테고리 아이콘").optional(),
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
