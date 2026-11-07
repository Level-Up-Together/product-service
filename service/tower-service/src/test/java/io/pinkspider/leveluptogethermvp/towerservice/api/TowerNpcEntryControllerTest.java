package io.pinkspider.leveluptogethermvp.towerservice.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.towerservice.application.TowerNpcEntryService;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerNpcException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcEntryRandomResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcEntryReportResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcEntryRequest;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcEntryView;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
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
        controllers = TowerNpcEntryController.class,
        excludeAutoConfiguration = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class
        })
@Import(ControllerTestConfig.class)
@AutoConfigureRestDocs
@ActiveProfiles("test")
@DisplayName("TowerNpcEntryController 테스트 (LUT-760)")
class TowerNpcEntryControllerTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 8, 2, 0);

    @Autowired private MockMvc mockMvc;

    @MockitoBean private TowerNpcEntryService entryService;
    @MockitoBean private io.pinkspider.global.web.ClientIpResolver clientIpResolver;

    private static FieldDescriptor[] entryFields(String prefix) {
        return new FieldDescriptor[] {
            fieldWithPath(prefix + "entry_id").description("글 ID"),
            fieldWithPath(prefix + "body").description("본문 (욕설 가림 적용본)"),
            fieldWithPath(prefix + "author_name")
                    .type(JsonFieldType.STRING)
                    .description("로그인 유저의 닉네임(제출 때 값). 방문객·시드는 null")
                    .optional(),
            fieldWithPath(prefix + "visitor_no")
                    .type(JsonFieldType.NUMBER)
                    .description("방문객 이름 번호 — 클라이언트가 locale 로 \"방문객 4821\" 조립")
                    .optional(),
            fieldWithPath(prefix + "visitor_tag")
                    .type(JsonFieldType.STRING)
                    .description("방문객 공개 식별자(숨기기 기준, LUT-705)")
                    .optional(),
            fieldWithPath(prefix + "user_id")
                    .type(JsonFieldType.STRING)
                    .description("작성 유저 ID (차단 유저 글을 클라이언트가 뺄 때)")
                    .optional(),
            fieldWithPath(prefix + "is_seed").description("운영 시드 글 — 작성자 표시는 NPC 이름"),
            fieldWithPath(prefix + "created_at")
                    .type(JsonFieldType.STRING)
                    .description("작성 시각 (UTC)")
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

    @Test
    @DisplayName(
            "POST /api/v1/tower/npcs/{npcId}/entries : 글 남기기(로그인·방문객) · 거절 150411/150414/150410")
    void submit() throws Exception {
        when(clientIpResolver.resolve(any())).thenReturn("9.9.9.9");
        when(entryService.submit(
                        eq(3L), eq("user-1"), any(TowerNpcEntryRequest.class), eq("9.9.9.9")))
                .thenReturn(
                        new TowerNpcEntryView(77L, "트리야 안녕", "닉", null, null, "user-1", false, AT));
        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                        "/api/v1/tower/npcs/{npcId}/entries", 3L)
                                .with(user("user-1"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"body\":\"트리야"
                                            + " 안녕\",\"email\":\"wish@example.com\",\"consent\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.entry_id").value(77))
                .andExpect(jsonPath("$.value.author_name").value("닉"))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "tower-npc-entry-submit",
                                ResourceSnippetParameters.builder()
                                        .tag("Tower")
                                        .description(
                                                "NPC 에 글 남기기 (LUT-760) — 비로그인 허용(방문객은 visitor_key,"
                                                    + " 150204). 입력을 받지 않는 NPC 400 150410 · 로그인 전용"
                                                    + " NPC 에 방문객 403 150411 · 본문 비었거나 글자 수 초과 400"
                                                    + " 150412 · 이메일 형식·동의 400 150413 · 같은 NPC 하루"
                                                    + " 1회 초과 429 150414 · HIGH 금칙어 400 150305. 응답은"
                                                    + " 가림 적용된 저장본 — 바로 내 화면에 보여 준다"),
                                pathParameters(parameterWithName("npcId").description("NPC ID")),
                                requestFields(
                                        fieldWithPath("body").description("본문 (설정 max_length 이내)"),
                                        fieldWithPath("email")
                                                .type(JsonFieldType.STRING)
                                                .description("이메일 — email_enabled 인 NPC 만 (아니면 무시)")
                                                .optional(),
                                        fieldWithPath("consent")
                                                .type(JsonFieldType.BOOLEAN)
                                                .description("이메일 수집 동의 — email_enabled 면 true 필수")
                                                .optional(),
                                        fieldWithPath("visitor_key")
                                                .type(JsonFieldType.STRING)
                                                .description("방문객 키 (비로그인 필수)")
                                                .optional()),
                                responseFields(withEnvelope(entryFields("value.")))));

        when(entryService.submit(eq(4L), isNull(), any(TowerNpcEntryRequest.class), eq("9.9.9.9")))
                .thenThrow(TowerNpcException.entryLoginRequired());
        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                        "/api/v1/tower/npcs/{npcId}/entries", 4L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"body\":\"소원\",\"visitor_key\":\"k-1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("150411"));

        when(entryService.submit(
                        eq(5L), eq("user-1"), any(TowerNpcEntryRequest.class), eq("9.9.9.9")))
                .thenThrow(TowerNpcException.entryDailyLimit());
        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                        "/api/v1/tower/npcs/{npcId}/entries", 5L)
                                .with(user("user-1"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"body\":\"또\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("150414"));

        when(entryService.submit(
                        eq(6L), eq("user-1"), any(TowerNpcEntryRequest.class), eq("9.9.9.9")))
                .thenThrow(TowerNpcException.formDisabled());
        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                        "/api/v1/tower/npcs/{npcId}/entries", 6L)
                                .with(user("user-1"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"body\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("150410"));
    }

    @Test
    @DisplayName(
            "GET /api/v1/tower/npcs/{npcId}/entries/random : 덜 보여 준 글부터 최대 50개 (exclude 로 더 받기)")
    void random() throws Exception {
        when(entryService.random(3L, 50, List.of(1L, 2L)))
                .thenReturn(
                        new TowerNpcEntryRandomResponse(
                                List.of(
                                        new TowerNpcEntryView(
                                                78L,
                                                "소원",
                                                null,
                                                4821,
                                                "abcd1234abcd1234",
                                                null,
                                                false,
                                                AT),
                                        new TowerNpcEntryView(
                                                1L, "첫 쪽지", null, null, null, null, true, AT)),
                                128L));
        mockMvc.perform(
                        RestDocumentationRequestBuilders.get(
                                        "/api/v1/tower/npcs/{npcId}/entries/random", 3L)
                                .queryParam("limit", "50")
                                .queryParam("exclude", "1,2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.entries[0].visitor_no").value(4821))
                .andExpect(jsonPath("$.value.entries[1].is_seed").value(true))
                .andExpect(jsonPath("$.value.total").value(128))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "tower-npc-entries-random",
                                ResourceSnippetParameters.builder()
                                        .tag("Tower")
                                        .description(
                                                "NPC 공개 글 조회 (LUT-760) — 비로그인 허용. 공개(숨김 아님) 글을 덜 보여"
                                                    + " 준 글부터(노출 수 오름차순, 같은 수는 무작위) 최대 50개, 내려준 글은"
                                                    + " 노출 수 +1. 공개 방식 NONE·설정 없음이면 빈 목록. 이메일은 절대"
                                                    + " 내리지 않는다. NPC 없음 404 150401"),
                                pathParameters(parameterWithName("npcId").description("NPC ID")),
                                queryParameters(
                                        parameterWithName("limit")
                                                .description("최대 개수 (1~50, 기본 50)")
                                                .optional(),
                                        parameterWithName("exclude")
                                                .description("이미 받은 글 ID (쉼표 구분) — 다 보면 넘겨 더 받는다")
                                                .optional()),
                                responseFields(
                                        withEnvelope(
                                                concat(
                                                        new FieldDescriptor[] {
                                                            fieldWithPath("value.entries")
                                                                    .description("글 목록 (덜 보여 준 순)"),
                                                            fieldWithPath("value.total")
                                                                    .description(
                                                                            "공개 글 전체 수 (시드 포함)")
                                                        },
                                                        entryFields("value.entries[]."))))));
    }

    @Test
    @DisplayName(
            "POST /api/v1/tower/npcs/entries/{entryId}/reports : 신고(로그인·방문객) · 없는 글 404 150415")
    void report() throws Exception {
        when(entryService.report(78L, null, "k-1"))
                .thenReturn(new TowerNpcEntryReportResponse(false, true));
        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                        "/api/v1/tower/npcs/entries/{entryId}/reports", 78L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"visitor_key\":\"k-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.duplicate").value(false))
                .andExpect(jsonPath("$.value.hidden").value(true))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "tower-npc-entry-report",
                                ResourceSnippetParameters.builder()
                                        .tag("Tower")
                                        .description(
                                                "NPC 글 신고 (LUT-760) — 로그인 유저·방문객 모두, 같은 사람의 재신고는"
                                                        + " duplicate. 3건이면 자동 숨김(hidden). 없는 글 404"
                                                        + " 150415, 방문객 키 없음 150204"),
                                pathParameters(parameterWithName("entryId").description("글 ID")),
                                requestFields(
                                        fieldWithPath("visitor_key")
                                                .type(JsonFieldType.STRING)
                                                .description("방문객 키 (비로그인 필수, 로그인은 본문 생략 가능)")
                                                .optional()),
                                responseFields(
                                        fieldWithPath("code").description("응답 코드"),
                                        fieldWithPath("message").description("응답 메시지"),
                                        fieldWithPath("value.duplicate")
                                                .description("같은 사람이 이미 신고해 세지 않았는가"),
                                        fieldWithPath("value.hidden")
                                                .description("지금 숨김 상태인가 (신고 3건 자동)"))));

        when(entryService.report(79L, "user-1", null))
                .thenReturn(new TowerNpcEntryReportResponse(true, false));
        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                        "/api/v1/tower/npcs/entries/{entryId}/reports", 79L)
                                .with(user("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.duplicate").value(true));

        when(entryService.report(99L, "user-1", null)).thenThrow(TowerNpcException.entryNotFound());
        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                        "/api/v1/tower/npcs/entries/{entryId}/reports", 99L)
                                .with(user("user-1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("150415"));
    }

    private static FieldDescriptor[] concat(FieldDescriptor[] a, FieldDescriptor[] b) {
        FieldDescriptor[] all = new FieldDescriptor[a.length + b.length];
        System.arraycopy(a, 0, all, 0, a.length);
        System.arraycopy(b, 0, all, a.length, b.length);
        return all;
    }
}
