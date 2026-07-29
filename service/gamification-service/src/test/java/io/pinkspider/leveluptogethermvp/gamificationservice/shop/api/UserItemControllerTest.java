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
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.application.UserItemService;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.UserItemResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ShopItemImagePosition;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ShopItemType;
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
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@WebMvcTest(controllers = UserItemController.class,
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
class UserItemControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    private UserItemService userItemService;

    private static final String MOCK_USER_ID = "test-user-123";

    private UserItemResponse createMockUserItem(Long userItemId, Long shopItemId, String name,
            ShopItemType type, boolean equipped) {
        return new UserItemResponse(
            userItemId,
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
            TitleRarity.RARE,
            "/uploads/shop-items/" + shopItemId + ".png",
            ShopItemImagePosition.BACK,
            equipped,
            LocalDateTime.of(2026, 7, 1, 0, 0));
    }

    /** LUT-296: 인벤토리 아이템 공통 응답 필드 */
    private FieldDescriptor[] userItemFields(String prefix) {
        return new FieldDescriptor[] {
            fieldWithPath(prefix + "user_item_id").type(JsonFieldType.NUMBER).description("보유 아이템 ID"),
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
            fieldWithPath(prefix + "is_equipped").type(JsonFieldType.BOOLEAN).description("장착 여부 (타입당 최대 1개)"),
            fieldWithPath(prefix + "acquired_at").type(JsonFieldType.STRING).description("획득 일시").optional()
        };
    }

    private FieldDescriptor[] withEnvelope(FieldDescriptor listDescriptor,
            FieldDescriptor[] itemFields) {
        FieldDescriptor[] result = new FieldDescriptor[itemFields.length + 3];
        result[0] = fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드");
        result[1] = fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지");
        result[2] = listDescriptor;
        System.arraycopy(itemFields, 0, result, 3, itemFields.length);
        return result;
    }

    @Test
    @DisplayName("GET /api/v1/user-items : 내 보유 아이템 목록 조회 (LUT-296)")
    void getMyItemsTest() throws Exception {
        // given — 장착중 1개 + 미장착 1개
        when(userItemService.getMyItems(anyString())).thenReturn(List.of(
            createMockUserItem(1L, 2L, "레벨업 사용 설명서", ShopItemType.ETC, false),
            createMockUserItem(2L, 3L, "메딕의 날개", ShopItemType.EFFECT, true)));

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/user-items")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("아이템 인벤토리-01. 내 보유 아이템 목록",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("UserItem")
                        .description("내 보유 아이템 목록 조회 — 기본 아이템(ID:2) 미보유 시 지급 후 반환 (JWT 토큰 인증 필요, LUT-296)")
                        .responseFields(withEnvelope(
                            fieldWithPath("value[]").type(JsonFieldType.ARRAY).description("보유 아이템 목록"),
                            userItemFields("value[].")))
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/user-items/{shopItemId}/equip : 아이템 장착 (LUT-296)")
    void equipItemTest() throws Exception {
        // given
        when(userItemService.equipItem(anyString(), anyLong()))
            .thenReturn(createMockUserItem(2L, 3L, "메딕의 날개", ShopItemType.EFFECT, true));

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/user-items/{shopItemId}/equip", 3L)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("아이템 인벤토리-02. 아이템 장착",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("UserItem")
                        .description("아이템 장착 — 같은 타입의 기존 장착 아이템은 해제된다 (타입당 1개, JWT 토큰 인증 필요, LUT-296)")
                        .pathParameters(
                            parameterWithName("shopItemId").type(SimpleType.NUMBER).description("장착할 상점 아이템 ID")
                        )
                        .responseFields(withEnvelope(
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("장착된 아이템"),
                            userItemFields("value.")))
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }
}
