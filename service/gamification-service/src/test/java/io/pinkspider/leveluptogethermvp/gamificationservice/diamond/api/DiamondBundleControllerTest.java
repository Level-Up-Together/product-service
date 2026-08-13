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

    @MockitoBean
    private io.pinkspider.leveluptogethermvp.gamificationservice.diamond.application
        .DiamondBundlePurchaseService diamondBundlePurchaseService;

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
            "/uploads/shop-items/bundle-" + id + ".png",
            "pink_" + count);
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
                            fieldWithPath("value[].image_url").type(JsonFieldType.STRING).description("상품 이미지 URL").optional(),
                            fieldWithPath("value[].store_product_id").type(JsonFieldType.STRING).description("LUT-354: 스토어 IAP 상품 ID (미설정 시 결제 불가)").optional()
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

    @Test
    @DisplayName("POST /api/v1/diamond-bundles/{id}/purchase : IAP 구매 검증·지급 (LUT-354)")
    void purchaseBundleTest() throws Exception {
        when(diamondBundlePurchaseService.purchase(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any()))
            .thenReturn(new io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto
                .DiamondBundlePurchaseResponse(1L, 100, 110, 10, 100, false));

        String requestBody = """
            {
              "platform": "ios",
              "store_product_id": "pink_100",
              "transaction_id": "tx-001",
              "receipt": "base64-receipt"
            }
            """;

        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/diamond-bundles/{bundleId}/purchase", 1L)
                .with(org.springframework.security.test.web.servlet.request
                    .SecurityMockMvcRequestPostProcessors.user("test-user-123"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("다이아-03. 핑크다이아 묶음상품 IAP 구매",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Diamond")
                        .description("LUT-354: 스토어 결제 영수증 검증 후 핑크다이아 멱등 지급 (JWT 인증 필요)")
                        .requestFields(
                            fieldWithPath("platform").type(JsonFieldType.STRING).description("결제 플랫폼 (ios|android)"),
                            fieldWithPath("store_product_id").type(JsonFieldType.STRING).description("스토어 상품 ID"),
                            fieldWithPath("transaction_id").type(JsonFieldType.STRING).description("iOS 트랜잭션 ID").optional(),
                            fieldWithPath("receipt").type(JsonFieldType.STRING).description("iOS base64 영수증").optional(),
                            fieldWithPath("purchase_token").type(JsonFieldType.STRING).description("Android purchase token").optional())
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("구매 결과"),
                            fieldWithPath("value.bundle_id").type(JsonFieldType.NUMBER).description("묶음상품 ID"),
                            fieldWithPath("value.diamond_count").type(JsonFieldType.NUMBER).description("지급된 핑크다이아 개수"),
                            fieldWithPath("value.balance").type(JsonFieldType.NUMBER).description("지급 후 총잔액 (블루+핑크)"),
                            fieldWithPath("value.blue_balance").type(JsonFieldType.NUMBER).description("블루 다이아 잔액"),
                            fieldWithPath("value.pink_balance").type(JsonFieldType.NUMBER).description("핑크다이아 잔액"),
                            fieldWithPath("value.already_processed").type(JsonFieldType.BOOLEAN)
                                .description("같은 트랜잭션 재요청 여부 (true면 지급 없이 현재 잔액)"))
                        .build()
                )
            )
        );

        resultActions
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andExpect(MockMvcResultMatchers.jsonPath("$.value.pink_balance").value(100))
            .andExpect(MockMvcResultMatchers.jsonPath("$.value.already_processed").value(false));
    }
}
