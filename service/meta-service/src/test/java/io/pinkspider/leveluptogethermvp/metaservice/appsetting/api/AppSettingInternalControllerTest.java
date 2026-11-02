package io.pinkspider.leveluptogethermvp.metaservice.appsetting.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.metaservice.appsetting.application.AppSettingCacheService;
import io.pinkspider.leveluptogethermvp.metaservice.appsetting.domain.dto.StoreReviewMilestonesRequest;
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@WebMvcTest(
        controllers = AppSettingInternalController.class,
        excludeAutoConfiguration = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class
        })
@Import(ControllerTestConfig.class)
@AutoConfigureRestDocs
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("AppSettingInternalController 테스트 (LUT-708)")
class AppSettingInternalControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean AppSettingCacheService appSettingCacheService;

    @Test
    @DisplayName("GET /api/internal/app-settings/store-review-milestones : 스토어 평가 마일스톤 조회")
    void getMilestones() throws Exception {
        when(appSettingCacheService.getStoreReviewMilestoneDays()).thenReturn(List.of(7, 14, 30));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.get(
                                "/api/internal/app-settings/store-review-milestones"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.value.milestone_days[0]").value(7))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "내부API-서비스설정-01. 스토어 평가 마일스톤 조회",
                                preprocessRequest(prettyPrint()),
                                preprocessResponse(prettyPrint()),
                                resource(
                                        ResourceSnippetParameters.builder()
                                                .tag("App Setting Internal")
                                                .description(
                                                        "스토어 인앱 평가 팝업을 요청할 연속 출석 일수 목록 (Admin"
                                                                + " Backend 전용)")
                                                .responseFields(
                                                        fieldWithPath("code")
                                                                .type(JsonFieldType.STRING)
                                                                .description("응답 코드"),
                                                        fieldWithPath("message")
                                                                .type(JsonFieldType.STRING)
                                                                .description("응답 메시지"),
                                                        fieldWithPath("value.milestone_days")
                                                                .type(JsonFieldType.ARRAY)
                                                                .description(
                                                                        "연속 출석 일수 목록(오름차순). 빈 목록 ="
                                                                                + " 끔"))
                                                .build())));
    }

    @Test
    @DisplayName("PUT /api/internal/app-settings/store-review-milestones : 스토어 평가 마일스톤 변경")
    void updateMilestones() throws Exception {
        when(appSettingCacheService.updateStoreReviewMilestoneDays(any()))
                .thenReturn(List.of(7, 14, 21, 28));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.put(
                                        "/api/internal/app-settings/store-review-milestones")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new StoreReviewMilestonesRequest(
                                                        List.of(28, 7, 14, 21)))))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.value.milestone_days[3]").value(28))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "내부API-서비스설정-02. 스토어 평가 마일스톤 변경",
                                preprocessRequest(prettyPrint()),
                                preprocessResponse(prettyPrint()),
                                resource(
                                        ResourceSnippetParameters.builder()
                                                .tag("App Setting Internal")
                                                .description(
                                                        "마일스톤 변경 — 1~365, 최대 10개, 정렬·중복 제거해 저장, 캐시"
                                                                + " evict (Admin Backend 전용)")
                                                .requestFields(
                                                        fieldWithPath("milestone_days")
                                                                .type(JsonFieldType.ARRAY)
                                                                .description(
                                                                        "연속 출석 일수 목록. 빈 목록 = 끔"))
                                                .responseFields(
                                                        fieldWithPath("code")
                                                                .type(JsonFieldType.STRING)
                                                                .description("응답 코드"),
                                                        fieldWithPath("message")
                                                                .type(JsonFieldType.STRING)
                                                                .description("응답 메시지"),
                                                        fieldWithPath("value.milestone_days")
                                                                .type(JsonFieldType.ARRAY)
                                                                .description("저장된 목록(오름차순)"))
                                                .build())));
    }
}
