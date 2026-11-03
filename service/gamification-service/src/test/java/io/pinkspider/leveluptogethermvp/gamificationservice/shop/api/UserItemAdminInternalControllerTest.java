package io.pinkspider.leveluptogethermvp.gamificationservice.shop.api;

import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.application.UserItemService;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.UserItemAdminResponse;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = UserItemAdminInternalController.class,
        excludeAutoConfiguration = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class
        })
@Import(ControllerTestConfig.class)
@AutoConfigureRestDocs
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("UserItemAdminInternalController 테스트 (LUT-657)")
class UserItemAdminInternalControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private UserItemService userItemService;

    @Test
    @DisplayName("GET /api/internal/users/{userId}/items : 보유 아이템 목록(착용 여부·희귀도 라벨/색)")
    void getUserItems() throws Exception {
        given(userItemService.getItemsForAdmin("user-1"))
                .willReturn(
                        List.of(
                                new UserItemAdminResponse(
                                        11L,
                                        "user-1",
                                        6L,
                                        "성장의 새싹",
                                        "Sprout",
                                        "برعم",
                                        "成長の芽",
                                        ShopItemType.HEAD,
                                        TitleRarity.RARE,
                                        "희귀",
                                        "#00F9FF",
                                        true,
                                        LocalDateTime.of(2026, 11, 2, 10, 0))));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.get(
                                        "/api/internal/users/{userId}/items", "user-1")
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value[0].shop_item_id").value(6))
                .andExpect(jsonPath("$.value[0].rarity_name").value("희귀"))
                .andExpect(jsonPath("$.value[0].is_equipped").value(true))
                .andDo(
                        document(
                                "internal-user-items-get",
                                pathParameters(parameterWithName("userId").description("유저 ID")),
                                responseFields(
                                        fieldWithPath("code").description("응답 코드"),
                                        fieldWithPath("message").description("응답 메시지"),
                                        fieldWithPath("value[].id").description("user_item ID"),
                                        fieldWithPath("value[].user_id").description("유저 ID"),
                                        fieldWithPath("value[].shop_item_id")
                                                .description("상점 아이템 ID"),
                                        fieldWithPath("value[].name").description("아이템명(ko)"),
                                        fieldWithPath("value[].name_en").description("아이템명(en)"),
                                        fieldWithPath("value[].name_ar").description("아이템명(ar)"),
                                        fieldWithPath("value[].name_ja").description("아이템명(ja)"),
                                        fieldWithPath("value[].item_type")
                                                .description("아이템 타입 (BASIC/FULL/HEAD/EFFECT/ETC)"),
                                        fieldWithPath("value[].rarity").description("희귀도 코드"),
                                        fieldWithPath("value[].rarity_name").description("희귀도 라벨"),
                                        fieldWithPath("value[].rarity_color_code")
                                                .description("희귀도 색상 코드 (COMMON 은 null)")
                                                .optional(),
                                        fieldWithPath("value[].is_equipped").description("착용 여부"),
                                        fieldWithPath("value[].acquired_at")
                                                .description("획득 일시"))));
    }
}
