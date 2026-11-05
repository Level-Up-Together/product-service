package io.pinkspider.leveluptogethermvp.towerservice.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
import io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures;
import io.pinkspider.leveluptogethermvp.towerservice.application.TowerNpcAdminService;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerNpcException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcAdminRequest;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcAdminResponse;
import java.util.ArrayList;
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
        controllers = TowerNpcAdminInternalController.class,
        excludeAutoConfiguration = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class
        })
@Import(ControllerTestConfig.class)
@AutoConfigureRestDocs
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("TowerNpcAdminInternalController 테스트 (LUT-693)")
class TowerNpcAdminInternalControllerTest {

    private static final String BODY =
            "{\"name\":\"탑지기\",\"name_en\":\"Keeper\",\"name_ja\":\"塔守\",\"name_ar\":\"الحارس\",\"profile_image_url\":\"https://cdn/npc.png\",\"status_text\":\"조는"
                + " 중\","
                + "\"status_text_en\":\"Dozing\",\"status_text_ja\":null,\"status_text_ar\":null,\"seat_x\":120,\"seat_y\":220,\"lines\":[{\"text\":\"어서"
                + " 와\",\"text_en\":\"Welcome\","
                + "\"text_ja\":\"ようこそ\",\"text_ar\":\"مرحبا\"}],\"is_public\":true,\"sort_order\":0,"
                + "\"auto_bubble\":true,\"bubble_interval_seconds\":8,"
                + "\"card\":{\"image_url\":\"https://cdn/card.png\",\"link\":{\"type\":\"SHOP_ITEM\",\"target_id\":15},\"link_label\":\"상점"
                + " 가기\",\"starts_on\":\"2026-11-01\",\"ends_on\":\"2026-11-30\"}}";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private TowerNpcAdminService npcService;

    private static TowerNpcAdminResponse response() {
        var npc = TowerTestFixtures.npc(3L, 10L, true);
        npc.updateCard(TowerTestFixtures.npcCard());
        return TowerNpcAdminResponse.from(npc);
    }

    /** LUT-733: 창 이미지·이동 링크 문서 필드 */
    static FieldDescriptor[] cardFields(String prefix) {
        return new FieldDescriptor[] {
            fieldWithPath(prefix + "card")
                    .type(JsonFieldType.OBJECT)
                    .description("LUT-733 NPC 창 이미지·이동 링크 (없으면 null)")
                    .optional(),
            fieldWithPath(prefix + "card.image_url")
                    .type(JsonFieldType.STRING)
                    .description("창 이미지 (가로 2 : 세로 1 권장, 이미지 업로드 kind=NPC)")
                    .optional(),
            fieldWithPath(prefix + "card.link")
                    .type(JsonFieldType.OBJECT)
                    .description("이동 링크 (없으면 null)")
                    .optional(),
            fieldWithPath(prefix + "card.link.type")
                    .type(JsonFieldType.STRING)
                    .description("SHOP_ITEM(판매 중 아이템) · EVENT · SUBSCRIPTION · PATH(앱 경로)")
                    .optional(),
            fieldWithPath(prefix + "card.link.target_id")
                    .type(JsonFieldType.NUMBER)
                    .description("SHOP_ITEM·EVENT 의 대상 ID")
                    .optional(),
            fieldWithPath(prefix + "card.link.path")
                    .type(JsonFieldType.STRING)
                    .description("PATH 의 앱 경로 (/ 로 시작, // · :// · 공백 금지)")
                    .optional(),
            fieldWithPath(prefix + "card.link_label")
                    .type(JsonFieldType.STRING)
                    .description("버튼 문구 (한국어, 비우면 기본 문구 \"바로 가기\")")
                    .optional(),
            fieldWithPath(prefix + "card.link_label_en")
                    .type(JsonFieldType.STRING)
                    .description("버튼 문구 (영어)")
                    .optional(),
            fieldWithPath(prefix + "card.link_label_ja")
                    .type(JsonFieldType.STRING)
                    .description("버튼 문구 (일본어)")
                    .optional(),
            fieldWithPath(prefix + "card.link_label_ar")
                    .type(JsonFieldType.STRING)
                    .description("버튼 문구 (아랍어)")
                    .optional(),
            fieldWithPath(prefix + "card.starts_on")
                    .type(JsonFieldType.STRING)
                    .description("노출 시작일 (KST, 비우면 상시)")
                    .optional(),
            fieldWithPath(prefix + "card.ends_on")
                    .type(JsonFieldType.STRING)
                    .description("노출 종료일 (KST, 포함, 비우면 상시)")
                    .optional()
        };
    }

    private static FieldDescriptor[] npcFields(String prefix) {
        return new FieldDescriptor[] {
            fieldWithPath(prefix + "id").description("NPC ID"),
            fieldWithPath(prefix + "floor_id").description("층 ID"),
            fieldWithPath(prefix + "name").description("이름 (한국어, 30자)"),
            fieldWithPath(prefix + "name_en").description("이름 (영어)"),
            fieldWithPath(prefix + "name_ja").description("이름 (일본어)"),
            fieldWithPath(prefix + "name_ar").description("이름 (아랍어)"),
            fieldWithPath(prefix + "profile_image_url")
                    .description("프로필 이미지 (정사각, 이미지 업로드 kind=NPC)"),
            fieldWithPath(prefix + "status_text").description("상태 문구 (한국어, 선택)").optional(),
            fieldWithPath(prefix + "status_text_en").description("상태 문구 (영어)").optional(),
            fieldWithPath(prefix + "status_text_ja").description("상태 문구 (일본어)").optional(),
            fieldWithPath(prefix + "status_text_ar").description("상태 문구 (아랍어)").optional(),
            fieldWithPath(prefix + "seat_x").description("자리 X (배경 원본 픽셀, 마스크 안)"),
            fieldWithPath(prefix + "seat_y").description("자리 Y"),
            fieldWithPath(prefix + "is_public").description("공개 (비공개는 탑에 안 나타난다 — 검수용)"),
            fieldWithPath(prefix + "sort_order").description("층 안 정렬 (작은 값 먼저)"),
            fieldWithPath(prefix + "lines").description("대사 목록 (1개 이상, 순서대로)"),
            fieldWithPath(prefix + "lines[].text").description("대사 (한국어, 2줄 이내 = 한글 약 40자)"),
            fieldWithPath(prefix + "lines[].text_en").description("대사 (영어, 약 80자)"),
            fieldWithPath(prefix + "lines[].text_ja").description("대사 (일본어)"),
            fieldWithPath(prefix + "lines[].text_ar").description("대사 (아랍어)"),
            fieldWithPath(prefix + "created_at").description("등록 시각").optional(),
            fieldWithPath(prefix + "modified_at").description("수정 시각").optional(),
            fieldWithPath(prefix + "auto_bubble").description("자동 말풍선 사용 (LUT-732)"),
            fieldWithPath(prefix + "bubble_interval_seconds").description("자동 말풍선 대사 간격(초, 5~60)")
        };
    }

    private static FieldDescriptor[] npcFieldsWithCard(String prefix) {
        List<FieldDescriptor> all = new ArrayList<>(List.of(npcFields(prefix)));
        all.addAll(List.of(cardFields(prefix)));
        return all.toArray(new FieldDescriptor[0]);
    }

    private static FieldDescriptor[] withEnvelope(FieldDescriptor... fields) {
        List<FieldDescriptor> all = new ArrayList<>();
        all.add(fieldWithPath("code").description("응답 코드"));
        all.add(fieldWithPath("message").description("응답 메시지"));
        all.addAll(List.of(fields));
        return all.toArray(new FieldDescriptor[0]);
    }

    private static FieldDescriptor[] requestNpcFields() {
        return new FieldDescriptor[] {
            fieldWithPath("name").description("이름 (한국어, 필수, 30자)"),
            fieldWithPath("name_en").description("이름 (영어, 필수)"),
            fieldWithPath("name_ja").description("이름 (일본어, 필수)"),
            fieldWithPath("name_ar").description("이름 (아랍어, 필수)"),
            fieldWithPath("profile_image_url").description("프로필 이미지 URL (https)"),
            fieldWithPath("status_text").description("상태 문구 (선택, 비우면 표시 안 함)").optional(),
            fieldWithPath("status_text_en").description("상태 문구 (영어)").optional(),
            fieldWithPath("status_text_ja").description("상태 문구 (일본어)").optional(),
            fieldWithPath("status_text_ar").description("상태 문구 (아랍어)").optional(),
            fieldWithPath("seat_x")
                    .description("자리 X (배경 원본 픽셀) — 배경 그림 밖이면 150403, 이동 불가 영역도 된다(LUT-746)"),
            fieldWithPath("seat_y").description("자리 Y"),
            fieldWithPath("lines").description("대사 목록 (1~50개)"),
            fieldWithPath("lines[].text").description("대사 (한국어) — 2줄을 넘으면 150404"),
            fieldWithPath("lines[].text_en").description("대사 (영어)"),
            fieldWithPath("lines[].text_ja").description("대사 (일본어)"),
            fieldWithPath("lines[].text_ar").description("대사 (아랍어)"),
            fieldWithPath("is_public").description("공개"),
            fieldWithPath("sort_order").description("정렬 (비우면 0)").optional(),
            fieldWithPath("auto_bubble")
                    .description("LUT-732 자동 말풍선 — 등록 때 비우면 꺼짐, 수정 때 비우면 그대로")
                    .optional(),
            fieldWithPath("bubble_interval_seconds")
                    .description("대사 간격(초) 5~60 — 등록 때 비우면 10, 수정 때 비우면 그대로, 범위 밖 400")
                    .optional(),
            fieldWithPath("card")
                    .type(JsonFieldType.OBJECT)
                    .description(
                            "LUT-733 창 이미지·이동 링크 — 등록 때 비우면 없음, 수정 때 비우면 그대로, 있으면 통째로 바꾼다(이미지·링크가 다"
                                    + " 비면 지움). 대상 없음 150405 · 링크 형식 150406 · 기간 역전 150407")
                    .optional(),
            fieldWithPath("card.image_url")
                    .type(JsonFieldType.STRING)
                    .description("창 이미지 URL (https)")
                    .optional(),
            fieldWithPath("card.link").type(JsonFieldType.OBJECT).description("이동 링크").optional(),
            fieldWithPath("card.link.type")
                    .type(JsonFieldType.STRING)
                    .description("SHOP_ITEM · EVENT · SUBSCRIPTION · PATH")
                    .optional(),
            fieldWithPath("card.link.target_id")
                    .type(JsonFieldType.NUMBER)
                    .description("SHOP_ITEM(판매 중)·EVENT 대상 ID")
                    .optional(),
            fieldWithPath("card.link.path")
                    .type(JsonFieldType.STRING)
                    .description("PATH 앱 경로 (/ 로 시작)")
                    .optional(),
            fieldWithPath("card.link_label")
                    .type(JsonFieldType.STRING)
                    .description("버튼 문구 (한국어, 30자)")
                    .optional(),
            fieldWithPath("card.link_label_en")
                    .type(JsonFieldType.STRING)
                    .description("버튼 문구 (영어)")
                    .optional(),
            fieldWithPath("card.link_label_ja")
                    .type(JsonFieldType.STRING)
                    .description("버튼 문구 (일본어)")
                    .optional(),
            fieldWithPath("card.link_label_ar")
                    .type(JsonFieldType.STRING)
                    .description("버튼 문구 (아랍어)")
                    .optional(),
            fieldWithPath("card.starts_on")
                    .type(JsonFieldType.STRING)
                    .description("노출 시작일 (KST)")
                    .optional(),
            fieldWithPath("card.ends_on")
                    .type(JsonFieldType.STRING)
                    .description("노출 종료일 (KST, 포함)")
                    .optional()
        };
    }

    @Test
    @DisplayName("GET·POST /api/internal/tower/floors/{floorId}/npcs : 층별 목록·등록")
    void listAndCreate() throws Exception {
        when(npcService.list(10L)).thenReturn(List.of(response()));
        when(npcService.create(eq(10L), any(TowerNpcAdminRequest.class))).thenReturn(response());

        mockMvc.perform(
                        RestDocumentationRequestBuilders.get(
                                "/api/internal/tower/floors/{floorId}/npcs", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value[0].name").value("탑지기"))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "internal-tower-npcs-list",
                                ResourceSnippetParameters.builder()
                                        .tag("Tower - Admin Internal")
                                        .description("층 NPC 목록 (LUT-693)"),
                                pathParameters(parameterWithName("floorId").description("층 ID")),
                                responseFields(withEnvelope(npcFieldsWithCard("value[].")))));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                        "/api/internal/tower/floors/{floorId}/npcs", 10L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.lines[0].text").value("어서 와"))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "internal-tower-npcs-create",
                                ResourceSnippetParameters.builder()
                                        .tag("Tower - Admin Internal")
                                        .description(
                                                "층 NPC 등록 (LUT-693) — 층당 최대 3명(150402), 자동"
                                                        + " 말풍선(LUT-732)·창 이미지·이동 링크(LUT-733)"),
                                pathParameters(parameterWithName("floorId").description("층 ID")),
                                requestFields(requestNpcFields()),
                                responseFields(withEnvelope(npcFieldsWithCard("value.")))));
    }

    @Test
    @DisplayName("PUT·DELETE : 수정·삭제 · 거부는 400 + 1504xx, 없음 404 · @Valid 위반(대사 없음·http) 400")
    void updateDeleteRejected() throws Exception {
        when(npcService.update(eq(10L), eq(3L), any(TowerNpcAdminRequest.class)))
                .thenReturn(response());
        mockMvc.perform(
                        RestDocumentationRequestBuilders.put(
                                        "/api/internal/tower/floors/{floorId}/npcs/{npcId}",
                                        10L,
                                        3L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isOk())
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "internal-tower-npcs-update",
                                ResourceSnippetParameters.builder()
                                        .tag("Tower - Admin Internal")
                                        .description(
                                                "층 NPC 수정 (LUT-693) — 자동 말풍선 값·card 가 없으면 그대로 둔다"),
                                pathParameters(
                                        parameterWithName("floorId").description("층 ID"),
                                        parameterWithName("npcId").description("NPC ID")),
                                responseFields(withEnvelope(npcFieldsWithCard("value.")))));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.delete(
                                "/api/internal/tower/floors/{floorId}/npcs/{npcId}", 10L, 3L))
                .andExpect(status().isOk())
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "internal-tower-npcs-delete",
                                ResourceSnippetParameters.builder()
                                        .tag("Tower - Admin Internal")
                                        .description("층 NPC 삭제 (LUT-693)"),
                                pathParameters(
                                        parameterWithName("floorId").description("층 ID"),
                                        parameterWithName("npcId").description("NPC ID"))));
        verify(npcService).delete(10L, 3L);

        when(npcService.create(eq(10L), any(TowerNpcAdminRequest.class)))
                .thenThrow(TowerNpcException.limitExceeded());
        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                        "/api/internal/tower/floors/{floorId}/npcs", 10L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("150402"));

        when(npcService.list(11L)).thenThrow(TowerNpcException.notFound());
        mockMvc.perform(
                        RestDocumentationRequestBuilders.get(
                                "/api/internal/tower/floors/{floorId}/npcs", 11L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("150401"));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                        "/api/internal/tower/floors/{floorId}/npcs", 10L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY.replaceAll("\"lines\":\\[.*?\\]", "\"lines\":[]")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                        "/api/internal/tower/floors/{floorId}/npcs", 10L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY.replace("https://cdn/npc.png", "http://x/npc.png")))
                .andExpect(status().isBadRequest());
        // LUT-732: 자동 말풍선 간격은 5~60초
        for (String interval : new String[] {"4", "61"}) {
            mockMvc.perform(
                            RestDocumentationRequestBuilders.post(
                                            "/api/internal/tower/floors/{floorId}/npcs", 10L)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            BODY.replace(
                                                    "\"bubble_interval_seconds\":8",
                                                    "\"bubble_interval_seconds\":" + interval)))
                    .andExpect(status().isBadRequest());
        }
    }
}
