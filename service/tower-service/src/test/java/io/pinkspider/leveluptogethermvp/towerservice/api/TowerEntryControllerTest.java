package io.pinkspider.leveluptogethermvp.towerservice.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.towerservice.application.TowerEntryService;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerEntryException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerEnterResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerLeaveResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerQueueStatus;
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

@WebMvcTest(
        controllers = TowerEntryController.class,
        excludeAutoConfiguration = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class
        })
@Import(ControllerTestConfig.class)
@AutoConfigureRestDocs
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("TowerEntryController 테스트 (LUT-630)")
class TowerEntryControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private TowerEntryService entryService;

    @MockitoBean private io.pinkspider.global.web.ClientIpResolver clientIpResolver;

    @MockitoBean
    private io.pinkspider.leveluptogethermvp.towerservice.application.TowerSeatService seatService;

    @Test
    @DisplayName("POST /api/v1/tower/floors/{floorNumber}/enter : 입장·배정 (인스턴스 지정 파라미터 없음)")
    void enter() throws Exception {
        when(entryService.enter(any(), eq(2), any(), any()))
                .thenReturn(
                        new TowerEnterResponse(
                                2, 10L, 77L, false, false, null, 1200, 900, 1200, 900, 12L, null,
                                false, false, null, true));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                        "/api/v1/tower/floors/{floorNumber}/enter", 2)
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.instance_id").value(10))
                .andExpect(jsonPath("$.value.resumed").value(false))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "tower-floors-enter",
                                ResourceSnippetParameters.builder()
                                        .tag("Tower")
                                        .description(
                                                "층 입장·자동 배정 (LUT-630) — 정원이 남은 방 중 사람이 가장 많은 곳. 거절:"
                                                    + " 없는 층 404 150201 · 비로그인 비허용 403 150202 · 레벨"
                                                    + " 부족 403 150203. 비로그인 허용"),
                                pathParameters(
                                        parameterWithName("floorNumber").description("층 번호")),
                                queryParameters(
                                        parameterWithName("queue_ticket")
                                                .description(
                                                        "대기표 (LUT-701 — 입장 가능 신호를 받은 뒤 다시 요청할 때만)")
                                                .optional()),
                                responseFields(
                                        fieldWithPath("code").description("응답 코드"),
                                        fieldWithPath("message").description("응답 메시지"),
                                        fieldWithPath("value.floor_number").description("입장한 층 번호"),
                                        fieldWithPath("value.instance_id")
                                                .description("배정된 인스턴스(방) ID"),
                                        fieldWithPath("value.occupant_id").description("점유자 ID"),
                                        fieldWithPath("value.resumed")
                                                .description("자리 유지 중이던 방(또는 자동 착석 자리)으로 복귀했는가"),
                                        fieldWithPath("value.mission_active")
                                                .description("미션 중(자리 유지) 여부"),
                                        fieldWithPath("value.visitor_key")
                                                .description("방문객(비로그인)에게만 — 퇴장 때 본인 확인용")
                                                .optional(),
                                        fieldWithPath("value.entry_point_x")
                                                .description("층 입장 지점 X (배경 원본 픽셀)"),
                                        fieldWithPath("value.entry_point_y")
                                                .description("층 입장 지점 Y"),
                                        fieldWithPath("value.seat_x").description("내 자리 X (입장 시점)"),
                                        fieldWithPath("value.seat_y").description("내 자리 Y"),
                                        fieldWithPath("value.population")
                                                .description("층 전체 인원 (모든 인스턴스, NPC 제외, 30초 메모)"),
                                        fieldWithPath("value.visitor_no")
                                                .description(
                                                        "방문객 이름의 네 자리 숫자 (비로그인만, 클라이언트가 locale 로"
                                                                + " '방문객 1234' 조립 — LUT-631)")
                                                .optional(),
                                        fieldWithPath("value.auto_seat_continued")
                                                .description(
                                                        "자동 착석 자리에서 직접 입장으로 이어졌는가 (자동 착석 → 직접 입장"
                                                                + " 전환율 측정, LUT-646)"),
                                        fieldWithPath("value.queued")
                                                .description(
                                                        "대기열에 섰는가 (LUT-701 — true 면 배정 없이 queue 만,"
                                                            + " 입장 가능 신호 뒤 queue_ticket 으로 다시 요청)"),
                                        fieldWithPath("value.queue")
                                                .description("대기 상태 (queued 일 때만, 아니면 null)")
                                                .optional(),
                                        fieldWithPath("value.first_visit")
                                                .type(JsonFieldType.BOOLEAN)
                                                .description(
                                                        "LUT-734 이 층의 첫 방문 안내(지금 버전)를 처음 보는 입장인가 —"
                                                            + " 서버가 유저×층으로 기억(기기를 바꿔도 다시 안 뜸). 안내가"
                                                            + " 없으면 false, 방문객·대기는 null(클라이언트가 기기에"
                                                            + " 기억)")
                                                .optional())));
    }

    @Test
    @DisplayName("POST .../enter : 탑 전체 동시 접속 상한이면 배정 대신 대기 순번 (LUT-701)")
    void enterQueued() throws Exception {
        when(entryService.enter(any(), eq(2), eq("t-1"), any()))
                .thenReturn(
                        TowerEnterResponse.queued(
                                2, new TowerQueueStatus("t-1", "WAITING", 3, 60, 2, 120L, null)));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                        "/api/v1/tower/floors/{floorNumber}/enter", 2)
                                .queryParam("queue_ticket", "t-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.queued").value(true))
                .andExpect(jsonPath("$.value.queue.position").value(3))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "tower-floors-enter-queued",
                                ResourceSnippetParameters.builder()
                                        .tag("Tower")
                                        .description(
                                                "층 입장 — 탑 전체 동시 접속 상한이면 배정 대신 대기 순번을 준다"
                                                        + " (queued=true, LUT-701)"),
                                responseFields(
                                        fieldWithPath("code").description("응답 코드"),
                                        fieldWithPath("message").description("응답 메시지"),
                                        fieldWithPath("value.floor_number").description("층 번호"),
                                        fieldWithPath("value.population")
                                                .description("층 전체 인원 (NPC 제외)"),
                                        fieldWithPath("value.queued").description("true — 대기열에 섰다"),
                                        fieldWithPath("value.instance_id")
                                                .description("대기 중엔 비어 있음")
                                                .optional(),
                                        fieldWithPath("value.occupant_id")
                                                .description("대기 중엔 비어 있음")
                                                .optional(),
                                        fieldWithPath("value.visitor_key")
                                                .description("대기 중엔 비어 있음")
                                                .optional(),
                                        fieldWithPath("value.entry_point_x")
                                                .description("대기 중엔 비어 있음")
                                                .optional(),
                                        fieldWithPath("value.entry_point_y")
                                                .description("대기 중엔 비어 있음")
                                                .optional(),
                                        fieldWithPath("value.seat_x")
                                                .description("대기 중엔 비어 있음")
                                                .optional(),
                                        fieldWithPath("value.seat_y")
                                                .description("대기 중엔 비어 있음")
                                                .optional(),
                                        fieldWithPath("value.visitor_no")
                                                .description("대기 중엔 비어 있음")
                                                .optional(),
                                        fieldWithPath("value.resumed").description("false"),
                                        fieldWithPath("value.mission_active").description("false"),
                                        fieldWithPath("value.auto_seat_continued")
                                                .description("false"),
                                        fieldWithPath("value.first_visit")
                                                .type(JsonFieldType.BOOLEAN)
                                                .description("대기 중엔 null (LUT-734)")
                                                .optional(),
                                        fieldWithPath("value.queue.ticket")
                                                .description("대기표 — 상태 조회·대기 그만하기·입장 재요청에 쓴다"),
                                        fieldWithPath("value.queue.status")
                                                .description("WAITING / ADMITTED"),
                                        fieldWithPath("value.queue.position")
                                                .description("내 순번 (1부터, 로그인 유저가 방문객보다 앞)")
                                                .optional(),
                                        fieldWithPath("value.queue.estimated_wait_seconds")
                                                .description("예상 대기 시간(초)"),
                                        fieldWithPath("value.queue.floor_number")
                                                .description("층 번호"),
                                        fieldWithPath("value.queue.population")
                                                .description("층 전체 인원"),
                                        fieldWithPath("value.queue.admit_expires_in_seconds")
                                                .description("ADMITTED 일 때 남은 입장 시간(초, 30초 창)")
                                                .optional())));
    }

    @Test
    @DisplayName(
            "비로그인 입장은 visitor_key 를 받는다 · 레벨 부족 403 150203 · 없는 층 404 150201 · 비허용 층 403 150202")
    void guestAndErrors() throws Exception {
        when(entryService.enter(any(), eq(1), any(), any()))
                .thenReturn(
                        new TowerEnterResponse(
                                1, 10L, 78L, false, false, "k-1", 1400, 1000, 1400, 1000, 3L, 4821,
                                false, false, null, null));
        when(clientIpResolver.resolve(any())).thenReturn("1.2.3.4");
        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                        "/api/v1/tower/floors/{floorNumber}/enter", 1)
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .header("User-Agent", "Mozilla/5.0")
                                .content("{\"visitor_key\":\"k-1\",\"device_id\":\"dev-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.visitor_key").value("k-1"))
                .andExpect(jsonPath("$.value.visitor_no").value(4821))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "tower-floors-enter-visitor",
                                ResourceSnippetParameters.builder()
                                        .tag("Tower")
                                        .description(
                                                "층 입장 — 방문객(비로그인)은 본문에 visitor_key·device_id 를 보낸다"
                                                        + " (LUT-705)"),
                                org.springframework.restdocs.payload.PayloadDocumentation
                                        .requestFields(
                                                fieldWithPath("visitor_key")
                                                        .description(
                                                                "이전 입장 응답의 방문객 키 (LUT-705 — 같은 세션이면"
                                                                    + " 같은 이름 번호, 층 이동은 대기열을 거치지"
                                                                    + " 않는다. 서버가 모르는 키는 무시하고 새 세션)")
                                                        .optional(),
                                                fieldWithPath("device_id")
                                                        .description("기기 식별자 (제재 단위, 최대 128자)")
                                                        .optional())));
        org.mockito.ArgumentCaptor<
                        io.pinkspider.leveluptogethermvp.towerservice.application.visitor
                                .TowerVisitorContext>
                context =
                        org.mockito.ArgumentCaptor.forClass(
                                io.pinkspider.leveluptogethermvp.towerservice.application.visitor
                                        .TowerVisitorContext.class);
        org.mockito.Mockito.verify(entryService)
                .enter(
                        org.mockito.ArgumentMatchers.isNull(),
                        eq(1),
                        org.mockito.ArgumentMatchers.isNull(),
                        context.capture());
        assertThat(context.getValue().visitorKey()).isEqualTo("k-1");
        assertThat(context.getValue().deviceId()).isEqualTo("dev-1");
        assertThat(context.getValue().ip()).isEqualTo("1.2.3.4");
        assertThat(context.getValue().userAgent()).isEqualTo("Mozilla/5.0");

        // 본문 없는(구 클라이언트) 방문객 입장도 받는다
        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                "/api/v1/tower/floors/{floorNumber}/enter", 1))
                .andExpect(status().isOk());

        when(entryService.enter(any(), eq(3), any(), any()))
                .thenThrow(TowerEntryException.levelInsufficient());
        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                "/api/v1/tower/floors/{floorNumber}/enter", 3))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("150203"));

        when(entryService.enter(any(), eq(9), any(), any()))
                .thenThrow(TowerEntryException.floorNotAvailable());
        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                "/api/v1/tower/floors/{floorNumber}/enter", 9))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("150201"));

        when(entryService.enter(any(), eq(4), any(), any()))
                .thenThrow(TowerEntryException.loginRequired());
        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                "/api/v1/tower/floors/{floorNumber}/enter", 4))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("150202"));
    }

    @Test
    @DisplayName("POST /api/v1/tower/leave : 퇴장 (방문객은 visitor_key, 로그인 유저는 본문 없이)")
    void leave() throws Exception {
        when(entryService.leave(any(), eq("k-1"))).thenReturn(new TowerLeaveResponse(true, false));
        when(entryService.leave(any(), isNull())).thenReturn(new TowerLeaveResponse(false, true));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.post("/api/v1/tower/leave")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"visitor_key\":\"k-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.left").value(true))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "tower-leave",
                                ResourceSnippetParameters.builder()
                                        .tag("Tower")
                                        .description(
                                                "탑 퇴장 (LUT-630) — 멱등, 미션 중이면 자리를 남긴다. 방문객은"
                                                        + " visitor_key. 비로그인 허용"),
                                requestFields(
                                        fieldWithPath("visitor_key")
                                                .description("방문객 키 (입장 응답, 비로그인만 — 로그인 유저는 본문 생략)")
                                                .optional()),
                                responseFields(
                                        fieldWithPath("code").description("응답 코드"),
                                        fieldWithPath("message").description("응답 메시지"),
                                        fieldWithPath("value.left").description("자리를 비웠는가"),
                                        fieldWithPath("value.seat_held")
                                                .description("미션 중이라 자리를 남겼는가 (돌아오면 같은 방으로 복귀)"))));

        mockMvc.perform(RestDocumentationRequestBuilders.post("/api/v1/tower/leave"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.seat_held").value(true));
        verify(entryService).leave(any(), isNull());
    }

    @Test
    @DisplayName("방문객 키 없는 비로그인 퇴장은 400 150204")
    void leaveWithoutKey() throws Exception {
        when(entryService.leave(any(), isNull()))
                .thenThrow(TowerEntryException.visitorKeyRequired());
        mockMvc.perform(RestDocumentationRequestBuilders.post("/api/v1/tower/leave"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("150204"));
    }

    @Test
    @DisplayName("POST /api/v1/tower/vacate : 자리 직접 비우기 — 미션은 계속, 아바타만 퇴장 (LUT-632/713)")
    void vacate() throws Exception {
        when(seatService.vacate(any())).thenReturn(true);

        mockMvc.perform(
                        RestDocumentationRequestBuilders.post("/api/v1/tower/vacate")
                                .with(user("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.left").value(true))
                .andExpect(jsonPath("$.value.seat_held").value(false))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "tower-vacate",
                                ResourceSnippetParameters.builder()
                                        .tag("Tower")
                                        .description(
                                                "자리 직접 비우기 (LUT-632) — 미션은 계속, 자동 착석 자리 포함. 로그인"
                                                        + " 필요"),
                                responseFields(
                                        fieldWithPath("code").description("응답 코드"),
                                        fieldWithPath("message").description("응답 메시지"),
                                        fieldWithPath("value.left")
                                                .description("자리를 비웠는가 (탑에 없었으면 false)"),
                                        fieldWithPath("value.seat_held")
                                                .description("항상 false — 미션 중이어도 자리를 남기지 않는다"))));
    }

    @Test
    @DisplayName("GET /api/v1/tower/me : 내 자리 (LUT-713 — 미션 진행 화면 카드)")
    void mySeat() throws Exception {
        when(entryService.mySeat("user-1"))
                .thenReturn(
                        new io.pinkspider.leveluptogethermvp.towerservice.domain.dto
                                .TowerMySeatResponse(true, 3, true, true));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.get("/api/v1/tower/me")
                                .with(user("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.floor_number").value(3))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "tower-me",
                                ResourceSnippetParameters.builder()
                                        .tag("Tower")
                                        .description(
                                                "내 자리 (LUT-713) — 미션 진행 화면 탑 카드가 서버 값으로 그린다. 로그인"
                                                        + " 필요"),
                                responseFields(
                                        fieldWithPath("code").description("응답 코드"),
                                        fieldWithPath("message").description("응답 메시지"),
                                        fieldWithPath("value.seated")
                                                .description(
                                                        "탑에 자리가 있는가 (false = 자동 착석을 껐거나 자리를 비웠다)"),
                                        fieldWithPath("value.floor_number")
                                                .description("자리가 있는 층")
                                                .optional(),
                                        fieldWithPath("value.auto_seated")
                                                .description(
                                                        "자동 착석 자리인가 (true = 들어가면 그 자리에서 직접 입장,"
                                                                + " false = 직접 입장한 자리로 복귀)")
                                                .optional(),
                                        fieldWithPath("value.mission_active")
                                                .description("미션 중 표시인가")
                                                .optional())));
    }
}
