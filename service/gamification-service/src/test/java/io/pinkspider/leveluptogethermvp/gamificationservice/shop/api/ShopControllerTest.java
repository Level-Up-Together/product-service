package io.pinkspider.leveluptogethermvp.gamificationservice.shop.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
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
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.application.ShopService;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ShopItemPurchaseResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ShopItemResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ShopItemImagePosition;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ShopItemType;
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
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@WebMvcTest(controllers = ShopController.class,
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
class ShopControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    private ShopService shopService;

    private static final String MOCK_USER_ID = "test-user-123";

    private ShopItemResponse createMockShopItem(Long shopItemId, String name,
            ShopItemType type, TitleRarity rarity, int price, boolean owned) {
        return new ShopItemResponse(
            shopItemId,
            name,
            name + " (EN)",
            name + " (AR)",
            name + " (JA)",
            "아이템 설명",
            "Item description",
            "وصف العنصر",
            "アイテム説明",
            type,
            rarity,
            "/uploads/shop-items/" + shopItemId + ".png",
            ShopItemImagePosition.BACK,
            price,
            owned);
    }

    /** LUT-327: 상점 아이템 공통 응답 필드 */
    private FieldDescriptor[] shopItemFields(String prefix) {
        return new FieldDescriptor[] {
            fieldWithPath(prefix + "shop_item_id").type(JsonFieldType.NUMBER).description("상점 아이템 ID"),
            fieldWithPath(prefix + "name").type(JsonFieldType.STRING).description("아이템명"),
            fieldWithPath(prefix + "name_en").type(JsonFieldType.STRING).description("아이템명 (영어)").optional(),
            fieldWithPath(prefix + "name_ar").type(JsonFieldType.STRING).description("아이템명 (아랍어)").optional(),
            fieldWithPath(prefix + "name_ja").type(JsonFieldType.STRING).description("아이템명 (일본어)").optional(),
            fieldWithPath(prefix + "description").type(JsonFieldType.STRING).description("아이템 설명").optional(),
            fieldWithPath(prefix + "description_en").type(JsonFieldType.STRING).description("아이템 설명 (영어)").optional(),
            fieldWithPath(prefix + "description_ar").type(JsonFieldType.STRING).description("아이템 설명 (아랍어)").optional(),
            fieldWithPath(prefix + "description_ja").type(JsonFieldType.STRING).description("아이템 설명 (일본어)").optional(),
            fieldWithPath(prefix + "item_type").type(JsonFieldType.STRING).description("아이템 타입 (BASIC|FULL|HEAD|EFFECT|ETC)"),
            fieldWithPath(prefix + "rarity").type(JsonFieldType.STRING).description("희귀도 (COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, MYTHIC)"),
            fieldWithPath(prefix + "image_url").type(JsonFieldType.STRING).description("아이템 이미지 URL").optional(),
            fieldWithPath(prefix + "image_position").type(JsonFieldType.STRING).description("이미지 포지션 (FRONT|BACK)").optional(),
            fieldWithPath(prefix + "price").type(JsonFieldType.NUMBER).description("다이아 가격"),
            fieldWithPath(prefix + "is_owned").type(JsonFieldType.BOOLEAN).description("보유 여부")
        };
    }

    private FieldDescriptor[] withEnvelope(FieldDescriptor valueDescriptor,
            FieldDescriptor[] itemFields) {
        FieldDescriptor[] result = new FieldDescriptor[itemFields.length + 3];
        result[0] = fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드");
        result[1] = fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지");
        result[2] = valueDescriptor;
        System.arraycopy(itemFields, 0, result, 3, itemFields.length);
        return result;
    }

    @Test
    @DisplayName("GET /api/v1/shop-items : 판매중 아이템 목록 조회 (LUT-327)")
    void getShopItemsTest() throws Exception {
        // given — 희귀도→가격→ID 오름차순 정렬 결과
        when(shopService.getShopItems(anyString())).thenReturn(List.of(
            createMockShopItem(1L, "시작의 날개", ShopItemType.BASIC, TitleRarity.COMMON, 100, true),
            createMockShopItem(3L, "메딕의 날개", ShopItemType.FULL, TitleRarity.RARE, 300, false)));

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/shop-items")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("상점-01. 판매중 아이템 목록",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Shop")
                        .description("판매중(활성) 아이템 목록 조회 — 희귀도(일반→신화)→가격→ID 오름차순, 보유 여부 포함 (JWT 토큰 인증 필요, LUT-327)")
                        .responseFields(withEnvelope(
                            fieldWithPath("value[]").type(JsonFieldType.ARRAY).description("판매중 아이템 목록"),
                            shopItemFields("value[].")))
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/shop-items/{shopItemId}/purchase : 아이템 구매 (LUT-327)")
    void purchaseItemTest() throws Exception {
        // given
        when(shopService.purchaseItem(anyString(), anyLong()))
            .thenReturn(ShopItemPurchaseResponse.of(3L, 300, 45));

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/shop-items/{shopItemId}/purchase", 3L)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("상점-02. 아이템 구매",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Shop")
                        .description("아이템 구매 — 다이아 차감 후 인벤토리에 지급. "
                            + "실패: 120603 판매하지 않는 아이템, 120604 이미 보유, 120605 다이아 부족 "
                            + "(JWT 토큰 인증 필요, LUT-327)")
                        .pathParameters(
                            parameterWithName("shopItemId").type(SimpleType.NUMBER).description("구매할 상점 아이템 ID")
                        )
                        .responseFields(withEnvelope(
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("구매 결과"),
                            new FieldDescriptor[] {
                                fieldWithPath("value.shop_item_id").type(JsonFieldType.NUMBER).description("구매한 상점 아이템 ID"),
                                fieldWithPath("value.price").type(JsonFieldType.NUMBER).description("차감된 다이아 가격"),
                                fieldWithPath("value.balance").type(JsonFieldType.NUMBER).description("차감 후 다이아 잔액")
                            }))
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }
}
