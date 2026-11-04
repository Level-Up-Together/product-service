package io.pinkspider.leveluptogethermvp.towerservice.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures;
import io.pinkspider.leveluptogethermvp.towerservice.application.TowerPresenceQueryService;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerEntryException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcView;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerPresenceSnapshotResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerTraceView;
import io.pinkspider.leveluptogethermvp.towerservice.presence.TowerAvatarItem;
import io.pinkspider.leveluptogethermvp.towerservice.presence.TowerPresenceView;
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
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = TowerPresenceController.class,
        excludeAutoConfiguration = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class
        })
@Import(ControllerTestConfig.class)
@AutoConfigureRestDocs
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("TowerPresenceController 테스트 (LUT-631)")
class TowerPresenceControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private TowerPresenceQueryService queryService;

    @Test
    @DisplayName("GET /api/v1/tower/instances/{instanceId}/presence : 입장 스냅샷 (방문객은 visitor_key)")
    void snapshot() throws Exception {
        when(queryService.snapshot(eq(9L), any(), eq("k-1")))
                .thenReturn(
                        new TowerPresenceSnapshotResponse(
                                9L,
                                1,
                                8L,
                                List.of(
                                        new TowerPresenceView(
                                                7L,
                                                "user-1",
                                                "DIRECT",
                                                null,
                                                "닉네임",
                                                "https://cdn/p.jpg",
                                                12,
                                                List.of(
                                                        new TowerAvatarItem(
                                                                "BASIC",
                                                                "https://cdn/wing.png",
                                                                "BACK",
                                                                null,
                                                                "RARE")),
                                                1400,
                                                1000,
                                                "MISSION",
                                                null),
                                        new TowerPresenceView(
                                                8L, null, "VISITOR", 4821, null, null, null,
                                                List.of(), 1380, 1010, "REST", null)),
                                List.of(TowerNpcView.from(TowerTestFixtures.npc(3L, 1L, true))),
                                true,
                                new TowerTraceView(
                                        4, List.of(new TowerTraceView.Point(1300, 980)))));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.get(
                                        "/api/v1/tower/instances/{instanceId}/presence", 9L)
                                .queryParam("visitor_key", "k-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.me").value(8))
                .andExpect(jsonPath("$.value.occupants[0].level").value(12))
                .andExpect(jsonPath("$.value.occupants[1].visitor_no").value(4821))
                .andExpect(jsonPath("$.value.npcs[0].name").value("탑지기"))
                .andExpect(jsonPath("$.value.npc_shown").value(true))
                .andExpect(jsonPath("$.value.traces.completed_today").value(4))
                .andDo(
                        document(
                                "tower-instance-presence",
                                pathParameters(
                                        parameterWithName("instanceId")
                                                .description("인스턴스(방) ID — 입장 응답 instance_id")),
                                queryParameters(
                                        parameterWithName("visitor_key")
                                                .description("방문객 키 (비로그인만)")
                                                .optional()),
                                responseFields(
                                        fieldWithPath("code").description("응답 코드"),
                                        fieldWithPath("message").description("응답 메시지"),
                                        fieldWithPath("value.instance_id").description("인스턴스 ID"),
                                        fieldWithPath("value.floor_number").description("층 번호"),
                                        fieldWithPath("value.me").description("요청자의 점유자 ID"),
                                        fieldWithPath("value.occupants[].occupant_id")
                                                .description("점유자 ID (이동·퇴장 이벤트의 키)"),
                                        fieldWithPath("value.occupants[].user_id")
                                                .description("유저 ID (방문객은 없음)")
                                                .optional(),
                                        fieldWithPath("value.occupants[].kind")
                                                .description("DIRECT / VISITOR / AUTO_SEATED"),
                                        fieldWithPath("value.occupants[].visitor_no")
                                                .description(
                                                        "방문객 번호 — 클라이언트가 locale 로 '방문객 1234' 조립")
                                                .optional(),
                                        fieldWithPath("value.occupants[].nickname")
                                                .description("닉네임 (방문객은 없음)")
                                                .optional(),
                                        fieldWithPath("value.occupants[].picture")
                                                .description("프로필 썸네일 URL")
                                                .optional(),
                                        fieldWithPath("value.occupants[].level")
                                                .description("레벨 (방문객은 없음, LEVEL 이벤트로 갱신)")
                                                .optional(),
                                        fieldWithPath("value.occupants[].items")
                                                .description("장착 아이템 목록 (방문객은 빈 배열)"),
                                        fieldWithPath("value.occupants[].items[].item_type")
                                                .description(
                                                        "장착 아이템 종류 (BASIC/FULL/HEAD/EFFECT/ETC)"),
                                        fieldWithPath("value.occupants[].items[].image_url")
                                                .description("아이템 이미지"),
                                        fieldWithPath("value.occupants[].items[].image_position")
                                                .description("FRONT/BACK")
                                                .optional(),
                                        fieldWithPath("value.occupants[].items[].effect_code")
                                                .description("이펙트 코드")
                                                .optional(),
                                        fieldWithPath("value.occupants[].items[].rarity")
                                                .description("희귀도")
                                                .optional(),
                                        fieldWithPath("value.occupants[].x")
                                                .description("위치 X (배경 원본 픽셀, 썸네일 맨 아래 중앙)"),
                                        fieldWithPath("value.occupants[].y").description("위치 Y"),
                                        fieldWithPath("value.occupants[].state")
                                                .description("MISSION / REST"),
                                        fieldWithPath("value.npcs")
                                                .description(
                                                        "이 층의 공개 NPC (LUT-693 — 사람"
                                                                + " 수에 넣지 않는다, 노출 그룹이"
                                                                + " 아니면 빈 배열)"),
                                        fieldWithPath("value.npcs[].npc_id").description("NPC ID"),
                                        fieldWithPath("value.npcs[].name").description("이름 (한국어)"),
                                        fieldWithPath("value.npcs[].name_en")
                                                .description("이름 (영어)"),
                                        fieldWithPath("value.npcs[].name_ja")
                                                .description("이름 (일본어)"),
                                        fieldWithPath("value.npcs[].name_ar")
                                                .description("이름 (아랍어)"),
                                        fieldWithPath("value.npcs[].profile_image_url")
                                                .description("프로필 이미지 (정사각, 원형으로 잘라" + " 그린다)"),
                                        fieldWithPath("value.npcs[].status_text")
                                                .description("상태 문구 (한국어, 없으면 표시 안 함)")
                                                .optional(),
                                        fieldWithPath("value.npcs[].status_text_en")
                                                .description("상태 문구 (영어)")
                                                .optional(),
                                        fieldWithPath("value.npcs[].status_text_ja")
                                                .description("상태 문구 (일본어)")
                                                .optional(),
                                        fieldWithPath("value.npcs[].status_text_ar")
                                                .description("상태 문구 (아랍어)")
                                                .optional(),
                                        fieldWithPath("value.npcs[].x")
                                                .description("자리 X (배경 원본 픽셀, 모든 방에 같은" + " 자리)"),
                                        fieldWithPath("value.npcs[].y").description("자리 Y"),
                                        fieldWithPath("value.npcs[].lines")
                                                .description(
                                                        "대사 목록 (순서대로, 클릭마다 다음 문장 —"
                                                                + " 순번은 기기에 기억)"),
                                        fieldWithPath("value.npcs[].lines[].text")
                                                .description("대사 (한국어)"),
                                        fieldWithPath("value.npcs[].lines[].text_en")
                                                .description("대사 (영어)"),
                                        fieldWithPath("value.npcs[].lines[].text_ja")
                                                .description("대사 (일본어)"),
                                        fieldWithPath("value.npcs[].lines[].text_ar")
                                                .description("대사 (아랍어)"),
                                        fieldWithPath("value.npcs[].auto_bubble")
                                                .description(
                                                        "자동 말풍선 (LUT-732 — 켜면 n초마다 대사를 순서대로"
                                                                + " 머리 위에, 구 서버는 없음 = 꺼짐)"),
                                        fieldWithPath("value.npcs[].bubble_interval_seconds")
                                                .description("자동 말풍선 대사 간격(초, 5~60)"),
                                        fieldWithPath("value.npc_shown")
                                                .description(
                                                        "이 요청자가 NPC 노출 그룹인가 (A/B,"
                                                                + " 분석 이벤트 파라미터용)"),
                                        fieldWithPath("value.traces")
                                                .description("오늘(KST) 이 층의 실제 흔적 (NPC" + " 제외)"),
                                        fieldWithPath("value.traces.completed_today")
                                                .description("오늘 이 층에서 미션을 완료한 사람 수 (실제" + " 수치만)"),
                                        fieldWithPath("value.traces.seats")
                                                .description(
                                                        "오늘 미션을 완료한 자리 (누구인지 비노출," + " 최근 50곳)"),
                                        fieldWithPath("value.traces.seats[].x").description("자리 X"),
                                        fieldWithPath("value.traces.seats[].y")
                                                .description("자리 Y"))));
    }

    @Test
    @DisplayName("그 방의 점유자가 아니면 403 150205")
    void notInInstance() throws Exception {
        when(queryService.snapshot(eq(10L), any(), any()))
                .thenThrow(TowerEntryException.notInInstance());
        mockMvc.perform(
                        RestDocumentationRequestBuilders.get(
                                "/api/v1/tower/instances/{instanceId}/presence", 10L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("150205"));
    }
}
