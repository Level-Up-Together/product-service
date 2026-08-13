package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.application.DiamondBundleService;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.DiamondBundleResponse;
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

@WebMvcTest(controllers = DiamondBundleController.class,
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
class DiamondBundleControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    private DiamondBundleService diamondBundleService;

    private DiamondBundleResponse createBundle(Long id, String name, int count) {
        return new DiamondBundleResponse(
            id,
            name,
            name + " (EN)",
            name + " (AR)",
            name + " (JA)",
            "상품 설명",
            "Bundle description",
            "وصف الحزمة",
            "商品説明",
            count,
            "/uploads/shop-items/bundle-" + id + ".png");
    }

    @Test
    @DisplayName("GET /api/v1/diamond-bundles : 판매중 핑크다이아 묶음상품 목록 조회 (LUT-356)")
    void getActiveBundlesTest() throws Exception {
        when(diamondBundleService.getActiveBundles()).thenReturn(List.of(
            createBundle(1L, "핑크다이아 100개", 100),
            createBundle(2L, "핑크다이아 550개", 550)));

        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/diamond-bundles")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("다이아-02. 핑크다이아 묶음상품 목록 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Diamond")
                        .description("판매중 핑크다이아 묶음상품 목록 조회 — 다이아 개수 오름차순, 비로그인 허용 (LUT-356)")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.ARRAY).description("묶음상품 목록"),
                            fieldWithPath("value[].id").type(JsonFieldType.NUMBER).description("묶음상품 ID"),
                            fieldWithPath("value[].name").type(JsonFieldType.STRING).description("상품명"),
                            fieldWithPath("value[].name_en").type(JsonFieldType.STRING).description("상품명 (영어)").optional(),
                            fieldWithPath("value[].name_ar").type(JsonFieldType.STRING).description("상품명 (아랍어)").optional(),
                            fieldWithPath("value[].name_ja").type(JsonFieldType.STRING).description("상품명 (일본어)").optional(),
                            fieldWithPath("value[].description").type(JsonFieldType.STRING).description("상품 설명").optional(),
                            fieldWithPath("value[].description_en").type(JsonFieldType.STRING).description("상품 설명 (영어)").optional(),
                            fieldWithPath("value[].description_ar").type(JsonFieldType.STRING).description("상품 설명 (아랍어)").optional(),
                            fieldWithPath("value[].description_ja").type(JsonFieldType.STRING).description("상품 설명 (일본어)").optional(),
                            fieldWithPath("value[].diamond_count").type(JsonFieldType.NUMBER).description("핑크다이아 개수"),
                            fieldWithPath("value[].image_url").type(JsonFieldType.STRING).description("상품 이미지 URL").optional()
                        )
                        .build()
                )
            )
        );

        resultActions
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andExpect(MockMvcResultMatchers.jsonPath("$.value.length()").value(2))
            .andExpect(MockMvcResultMatchers.jsonPath("$.value[0].diamond_count").value(100))
            .andExpect(MockMvcResultMatchers.jsonPath("$.value[1].diamond_count").value(550));
    }

    @Test
    @DisplayName("판매중 상품이 없으면 빈 배열을 반환한다")
    void getActiveBundles_empty() throws Exception {
        when(diamondBundleService.getActiveBundles()).thenReturn(List.of());

        mockMvc.perform(RestDocumentationRequestBuilders.get("/api/v1/diamond-bundles"))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andExpect(MockMvcResultMatchers.jsonPath("$.value.length()").value(0));
    }
}
