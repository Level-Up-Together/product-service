package io.pinkspider.leveluptogethermvp.towerservice.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.towerservice.application.TowerFloorGuideService;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerFloorGuideException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorGuideAdminRequest;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorGuideAdminResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcLineDto;
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

@WebMvcTest(
        controllers = TowerFloorGuideAdminInternalController.class,
        excludeAutoConfiguration = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class
        })
@Import(ControllerTestConfig.class)
@AutoConfigureRestDocs
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("TowerFloorGuideAdminInternalController 테스트 (LUT-734)")
class TowerFloorGuideAdminInternalControllerTest {

    private static final String BODY =
            "{\"enabled\":true,\"npc_id\":3,\"lines\":[{\"text\":\"어서 와\",\"text_en\":\"Welcome\","
                + "\"text_ja\":\"ようこそ\",\"text_ar\":\"مرحبا\"}],\"show_mission_button\":true,\"mission_button_label\":\"미션"
                + " 시작하기\",\"mission_button_label_en\":\"Start a mission\",\"reshow\":true}";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private TowerFloorGuideService guideService;

    private static TowerFloorGuideAdminResponse response() {
        return new TowerFloorGuideAdminResponse(
                10L,
                true,
                3L,
                List.of(new TowerNpcLineDto("어서 와", "Welcome", "ようこそ", "مرحبا")),
                true,
                "미션 시작하기",
                "Start a mission",
                null,
                null,
                2,
                LocalDateTime.of(2026, 11, 5, 9, 0));
    }

    private static FieldDescriptor[] responseDocs() {
        return new FieldDescriptor[] {
            fieldWithPath("code").description("응답 코드"),
            fieldWithPath("message").description("응답 메시지"),
            fieldWithPath("value.floor_id").description("층 ID"),
            fieldWithPath("value.enabled").description("켜짐"),
            fieldWithPath("value.npc_id")
                    .type(JsonFieldType.NUMBER)
                    .description("안내 NPC (그 층 NPC, 없으면 null)")
                    .optional(),
            fieldWithPath("value.lines").description("안내 문장 (0~5개, 차례로 말한다)"),
            fieldWithPath("value.lines[].text").description("문장 (한국어)"),
            fieldWithPath("value.lines[].text_en").description("문장 (영어)"),
            fieldWithPath("value.lines[].text_ja").description("문장 (일본어)"),
            fieldWithPath("value.lines[].text_ar").description("문장 (아랍어)"),
            fieldWithPath("value.show_mission_button").description("마지막 문장에 미션 시작 버튼"),
            fieldWithPath("value.mission_button_label")
                    .type(JsonFieldType.STRING)
                    .description("버튼 문구 (한국어, 비우면 앱 기본 문구)")
                    .optional(),
            fieldWithPath("value.mission_button_label_en")
                    .type(JsonFieldType.STRING)
                    .description("버튼 문구 (영어)")
                    .optional(),
            fieldWithPath("value.mission_button_label_ja")
                    .type(JsonFieldType.STRING)
                    .description("버튼 문구 (일본어)")
                    .optional(),
            fieldWithPath("value.mission_button_label_ar")
                    .type(JsonFieldType.STRING)
                    .description("버튼 문구 (아랍어)")
                    .optional(),
            fieldWithPath("value.version").description("안내 버전 (저장한 적 없으면 0)"),
            fieldWithPath("value.modified_at")
                    .type(JsonFieldType.STRING)
                    .description("수정 시각")
                    .optional()
        };
    }

    @Test
    @DisplayName(
            "GET·PUT /api/internal/tower/floors/{floorId}/guide : 조회·저장 · 거부는 400 + 1506xx · 문장 6개는"
                    + " 400")
    void getAndSave() throws Exception {
        when(guideService.get(10L)).thenReturn(response());
        when(guideService.save(eq(10L), any(TowerFloorGuideAdminRequest.class)))
                .thenReturn(response());

        mockMvc.perform(
                        RestDocumentationRequestBuilders.get(
                                "/api/internal/tower/floors/{floorId}/guide", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.version").value(2))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "internal-tower-floor-guide-get",
                                ResourceSnippetParameters.builder()
                                        .tag("Tower - Admin Internal")
                                        .description("층 첫 방문 안내 조회 (LUT-734)"),
                                pathParameters(parameterWithName("floorId").description("층 ID")),
                                responseFields(responseDocs())));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.put(
                                        "/api/internal/tower/floors/{floorId}/guide", 10L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isOk())
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "internal-tower-floor-guide-save",
                                ResourceSnippetParameters.builder()
                                        .tag("Tower - Admin Internal")
                                        .description(
                                                "층 첫 방문 안내 저장 (LUT-734) — reshow=true 면 버전을 올려 다시"
                                                        + " 보여 준다. 거절 400 1506xx"),
                                pathParameters(parameterWithName("floorId").description("층 ID")),
                                requestFields(
                                        fieldWithPath("enabled").description("켜기 (필수)"),
                                        fieldWithPath("npc_id")
                                                .type(JsonFieldType.NUMBER)
                                                .description("안내 NPC — 켜면 필수, 그 층 NPC 만(150601)")
                                                .optional(),
                                        fieldWithPath("lines")
                                                .type(JsonFieldType.ARRAY)
                                                .description(
                                                        "안내 문장 0~5개 — 켜면 1개 이상(150603), 한 문장 2줄"
                                                                + " 이내(150602)")
                                                .optional(),
                                        fieldWithPath("lines[].text").description("문장 (한국어)"),
                                        fieldWithPath("lines[].text_en").description("문장 (영어)"),
                                        fieldWithPath("lines[].text_ja").description("문장 (일본어)"),
                                        fieldWithPath("lines[].text_ar").description("문장 (아랍어)"),
                                        fieldWithPath("show_mission_button")
                                                .type(JsonFieldType.BOOLEAN)
                                                .description("마지막 문장에 미션 시작 버튼 (비우면 끔)")
                                                .optional(),
                                        fieldWithPath("mission_button_label")
                                                .type(JsonFieldType.STRING)
                                                .description("버튼 문구 (한국어, 30자, 비우면 앱 기본 문구)")
                                                .optional(),
                                        fieldWithPath("mission_button_label_en")
                                                .type(JsonFieldType.STRING)
                                                .description("버튼 문구 (영어)")
                                                .optional(),
                                        fieldWithPath("mission_button_label_ja")
                                                .type(JsonFieldType.STRING)
                                                .description("버튼 문구 (일본어)")
                                                .optional(),
                                        fieldWithPath("mission_button_label_ar")
                                                .type(JsonFieldType.STRING)
                                                .description("버튼 문구 (아랍어)")
                                                .optional(),
                                        fieldWithPath("reshow")
                                                .type(JsonFieldType.BOOLEAN)
                                                .description(
                                                        "다시 보여 주기 — 버전을 올려 이미 본 유저에게도 다음 입장에 한 번 더"
                                                                + " 보인다")
                                                .optional()),
                                responseFields(responseDocs())));

        when(guideService.save(eq(11L), any(TowerFloorGuideAdminRequest.class)))
                .thenThrow(TowerFloorGuideException.npcInvalid());
        mockMvc.perform(
                        RestDocumentationRequestBuilders.put(
                                        "/api/internal/tower/floors/{floorId}/guide", 11L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(TowerFloorGuideException.NPC_INVALID));

        String line = "{\"text\":\"a\",\"text_en\":\"a\",\"text_ja\":\"a\",\"text_ar\":\"a\"}";
        String six = String.join(",", java.util.Collections.nCopies(6, line));
        mockMvc.perform(
                        RestDocumentationRequestBuilders.put(
                                        "/api/internal/tower/floors/{floorId}/guide", 10L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"enabled\":true,\"npc_id\":3,\"lines\":[" + six + "]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(
                        RestDocumentationRequestBuilders.put(
                                        "/api/internal/tower/floors/{floorId}/guide", 10L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"npc_id\":3}"))
                .andExpect(status().isBadRequest());
    }
}
