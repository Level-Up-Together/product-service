package io.pinkspider.leveluptogethermvp.towerservice.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.towerservice.application.TowerNpcEntryAdminService;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerNpcException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerAdminPage;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcEntryAdminResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcEntryPurgeResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpcEntry;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerNpcEntryStatus;
import java.time.LocalDate;
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
        controllers = TowerNpcEntryAdminInternalController.class,
        excludeAutoConfiguration = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class
        })
@Import(ControllerTestConfig.class)
@AutoConfigureRestDocs
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("TowerNpcEntryAdminInternalController 테스트 (LUT-760)")
class TowerNpcEntryAdminInternalControllerTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 8, 2, 0);

    @Autowired private MockMvc mockMvc;

    @MockitoBean private TowerNpcEntryAdminService adminService;

    private static TowerNpcEntryAdminResponse response() {
        return TowerNpcEntryAdminResponse.from(
                TowerNpcEntry.builder()
                        .id(5L)
                        .npcId(3L)
                        .userId("u1")
                        .authorName("닉")
                        .body("달님 소원")
                        .email("wish@example.com")
                        .consentAt(AT)
                        .status(TowerNpcEntryStatus.VISIBLE)
                        .isSeed(false)
                        .reportCount(1)
                        .viewCount(3)
                        .build());
    }

    private static FieldDescriptor[] entryFields(String prefix) {
        return new FieldDescriptor[] {
            fieldWithPath(prefix + "id").description("글 ID"),
            fieldWithPath(prefix + "npc_id").description("NPC ID"),
            fieldWithPath(prefix + "user_id")
                    .type(JsonFieldType.STRING)
                    .description("로그인 유저 ID (방문객·시드는 null)")
                    .optional(),
            fieldWithPath(prefix + "author_name")
                    .type(JsonFieldType.STRING)
                    .description("제출 때 닉네임")
                    .optional(),
            fieldWithPath(prefix + "visitor_no")
                    .type(JsonFieldType.NUMBER)
                    .description("방문객 이름 번호 → 화면 \"방문객 4821\"")
                    .optional(),
            fieldWithPath(prefix + "visitor_tag")
                    .type(JsonFieldType.STRING)
                    .description("방문객 공개 식별자")
                    .optional(),
            fieldWithPath(prefix + "body").description("본문 (가림 적용본)"),
            fieldWithPath(prefix + "email")
                    .type(JsonFieldType.STRING)
                    .description("이메일 (email_enabled NPC 만, 파기하면 null) — 어드민 권한 필요")
                    .optional(),
            fieldWithPath(prefix + "consent_at")
                    .type(JsonFieldType.STRING)
                    .description("이메일 수집 동의 시각")
                    .optional(),
            fieldWithPath(prefix + "status").description("VISIBLE / HIDDEN"),
            fieldWithPath(prefix + "is_seed").description("운영 시드 글"),
            fieldWithPath(prefix + "report_count").description("신고 수 (3이면 자동 숨김)"),
            fieldWithPath(prefix + "view_count").description("공개 조회로 내려준 횟수"),
            fieldWithPath(prefix + "created_at")
                    .type(JsonFieldType.STRING)
                    .description("작성 시각")
                    .optional(),
            fieldWithPath(prefix + "modified_at")
                    .type(JsonFieldType.STRING)
                    .description("수정 시각")
                    .optional()
        };
    }

    private static FieldDescriptor[] withEnvelope(FieldDescriptor... fields) {
        FieldDescriptor[] all = new FieldDescriptor[fields.length + 2];
        all[0] = fieldWithPath("code").description("응답 코드");
        all[1] = fieldWithPath("message").description("응답 메시지");
        System.arraycopy(fields, 0, all, 2, fields.length);
        return all;
    }

    private static FieldDescriptor[] concat(FieldDescriptor[] a, FieldDescriptor[] b) {
        FieldDescriptor[] all = new FieldDescriptor[a.length + b.length];
        System.arraycopy(a, 0, all, 0, a.length);
        System.arraycopy(b, 0, all, a.length, b.length);
        return all;
    }

    @Test
    @DisplayName(
            "GET /api/internal/tower/npcs/{npcId}/entries : 수집함 목록(상태·신고·기간 필터) · GET /export : 전체")
    void listAndExport() throws Exception {
        when(adminService.list(
                        3L,
                        TowerNpcEntryStatus.VISIBLE,
                        true,
                        LocalDate.of(2026, 11, 1),
                        LocalDate.of(2026, 11, 8),
                        0,
                        20))
                .thenReturn(new TowerAdminPage<>(List.of(response()), 1, 1, 0, 20));
        mockMvc.perform(
                        RestDocumentationRequestBuilders.get(
                                        "/api/internal/tower/npcs/{npcId}/entries", 3L)
                                .queryParam("status", "VISIBLE")
                                .queryParam("reported", "true")
                                .queryParam("from", "2026-11-01")
                                .queryParam("to", "2026-11-08")
                                .queryParam("page", "0")
                                .queryParam("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.content[0].email").value("wish@example.com"))
                .andExpect(jsonPath("$.value.total_elements").value(1))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "internal-tower-npc-entries-list",
                                ResourceSnippetParameters.builder()
                                        .tag("Tower - Admin Internal")
                                        .description(
                                                "NPC 수집함 목록 (LUT-760) — 최신 먼저, 이메일 포함(어드민 권한·감사 로그는"
                                                        + " admin-service). NPC 없음 404 150401"),
                                pathParameters(parameterWithName("npcId").description("NPC ID")),
                                queryParameters(
                                        parameterWithName("status")
                                                .description("VISIBLE | HIDDEN (없으면 전체)")
                                                .optional(),
                                        parameterWithName("reported")
                                                .description("true = 신고 있는 글만")
                                                .optional(),
                                        parameterWithName("from")
                                                .description("작성일 시작 (KST, yyyy-MM-dd)")
                                                .optional(),
                                        parameterWithName("to")
                                                .description("작성일 끝 (KST, 포함)")
                                                .optional(),
                                        parameterWithName("page").description("0부터").optional(),
                                        parameterWithName("size")
                                                .description("1~200, 기본 20")
                                                .optional()),
                                responseFields(
                                        withEnvelope(
                                                concat(
                                                        new FieldDescriptor[] {
                                                            fieldWithPath("value.content")
                                                                    .description("글 목록"),
                                                            fieldWithPath("value.total_elements")
                                                                    .description("전체 건수"),
                                                            fieldWithPath("value.total_pages")
                                                                    .description("전체 페이지 수"),
                                                            fieldWithPath("value.page")
                                                                    .description("현재 페이지"),
                                                            fieldWithPath("value.size")
                                                                    .description("페이지 크기")
                                                        },
                                                        entryFields("value.content[]."))))));

        when(adminService.export(3L)).thenReturn(List.of(response()));
        mockMvc.perform(
                        RestDocumentationRequestBuilders.get(
                                "/api/internal/tower/npcs/{npcId}/entries/export", 3L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value[0].id").value(5))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "internal-tower-npc-entries-export",
                                ResourceSnippetParameters.builder()
                                        .tag("Tower - Admin Internal")
                                        .description(
                                                "NPC 수집함 전체 내보내기 (LUT-760) — 최신 먼저, 이메일 포함. CSV"
                                                        + " 변환·감사 로그는 admin-service"),
                                pathParameters(parameterWithName("npcId").description("NPC ID")),
                                responseFields(withEnvelope(entryFields("value[].")))));

        when(adminService.list(eq(4L), isNull(), eq(false), isNull(), isNull(), eq(0), eq(20)))
                .thenThrow(TowerNpcException.notFound());
        mockMvc.perform(
                        RestDocumentationRequestBuilders.get(
                                "/api/internal/tower/npcs/{npcId}/entries", 4L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("150401"));
    }

    @Test
    @DisplayName("PUT /{entryId}/status · POST /seed · DELETE /emails : 숨기기/다시 공개 · 시드 글 · 이메일 파기")
    void statusSeedPurge() throws Exception {
        when(adminService.updateStatus(3L, 5L, TowerNpcEntryStatus.HIDDEN)).thenReturn(response());
        mockMvc.perform(
                        RestDocumentationRequestBuilders.put(
                                        "/api/internal/tower/npcs/{npcId}/entries/{entryId}/status",
                                        3L,
                                        5L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"HIDDEN\"}"))
                .andExpect(status().isOk())
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "internal-tower-npc-entries-status",
                                ResourceSnippetParameters.builder()
                                        .tag("Tower - Admin Internal")
                                        .description(
                                                "NPC 글 숨기기 / 다시 공개 (LUT-760) — 신고로 자동 숨겨진 글도 다시 공개할"
                                                        + " 수 있다. 그 NPC 의 글이 아니면 404 150415"),
                                pathParameters(
                                        parameterWithName("npcId").description("NPC ID"),
                                        parameterWithName("entryId").description("글 ID")),
                                requestFields(
                                        fieldWithPath("status").description("VISIBLE | HIDDEN")),
                                responseFields(withEnvelope(entryFields("value.")))));

        when(adminService.addSeed(3L, "첫 쪽지")).thenReturn(response());
        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                        "/api/internal/tower/npcs/{npcId}/entries/seed", 3L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"body\":\"첫 쪽지\"}"))
                .andExpect(status().isOk())
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "internal-tower-npc-entries-seed",
                                ResourceSnippetParameters.builder()
                                        .tag("Tower - Admin Internal")
                                        .description(
                                                "NPC 시드 글 추가 (LUT-760) — 운영자가 쓴 글, 작성자 표시는 NPC 이름."
                                                        + " 길이는 설정 max_length(없으면 500), 넘으면 400"
                                                        + " 150412"),
                                pathParameters(parameterWithName("npcId").description("NPC ID")),
                                requestFields(fieldWithPath("body").description("본문 (500자)")),
                                responseFields(withEnvelope(entryFields("value.")))));
        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                        "/api/internal/tower/npcs/{npcId}/entries/seed", 3L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"body\":\" \"}"))
                .andExpect(status().isBadRequest());

        when(adminService.purgeEmails(3L)).thenReturn(new TowerNpcEntryPurgeResponse(12));
        mockMvc.perform(
                        RestDocumentationRequestBuilders.delete(
                                "/api/internal/tower/npcs/{npcId}/entries/emails", 3L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.purged_count").value(12))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "internal-tower-npc-entries-purge-emails",
                                ResourceSnippetParameters.builder()
                                        .tag("Tower - Admin Internal")
                                        .description(
                                                "NPC 수집함 이메일 일괄 파기 (LUT-760) — 본문은 남긴다. 이벤트가 끝나면"
                                                        + " 운영자가 누른다(확인 창은 어드민 화면)"),
                                pathParameters(parameterWithName("npcId").description("NPC ID")),
                                responseFields(
                                        fieldWithPath("code").description("응답 코드"),
                                        fieldWithPath("message").description("응답 메시지"),
                                        fieldWithPath("value.purged_count")
                                                .description("지운 행 수"))));
        verify(adminService).purgeEmails(3L);

        when(adminService.updateStatus(3L, 9L, TowerNpcEntryStatus.VISIBLE))
                .thenThrow(TowerNpcException.entryNotFound());
        mockMvc.perform(
                        RestDocumentationRequestBuilders.put(
                                        "/api/internal/tower/npcs/{npcId}/entries/{entryId}/status",
                                        3L,
                                        9L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"VISIBLE\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("150415"));
    }
}
