package io.pinkspider.leveluptogethermvp.towerservice.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.towerservice.application.TowerFloorQueryService;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorEffectLayerDto;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorGuideView;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorLightDto;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorListResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcLineDto;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerPixelBackgroundDto;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerEffectKind;
import java.time.LocalDate;
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

@WebMvcTest(
        controllers = TowerFloorController.class,
        excludeAutoConfiguration = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class
        })
@Import(ControllerTestConfig.class)
@AutoConfigureRestDocs
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("TowerFloorController 테스트 (LUT-629)")
class TowerFloorControllerTest {

    private static final String USER_ID = "user-1";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private TowerFloorQueryService queryService;

    private static TowerFloorListResponse sample() {
        return new TowerFloorListResponse(
                List.of(
                        new TowerFloorResponse(
                                1L,
                                1,
                                "여행자의 쉼터",
                                "Travelers' Rest",
                                "旅人の休息所",
                                "استراحة المسافرين",
                                null,
                                false,
                                true,
                                "https://cdn/bg1.jpg",
                                "https://cdn/mask1.png",
                                1400,
                                1000,
                                "https://cdn/bgm1.mp3",
                                30,
                                12L,
                                "https://cdn/occlusion1.png",
                                List.of(
                                        new TowerFloorLightDto(
                                                640, 320, 60, "#ffcc66", 80, 40, 50)),
                                List.of(
                                        new TowerFloorEffectLayerDto(
                                                TowerEffectKind.SNOW,
                                                "https://cdn/snow2.png",
                                                true,
                                                LocalDate.of(2026, 12, 1),
                                                LocalDate.of(2026, 12, 31),
                                                120,
                                                40,
                                                1,
                                                3,
                                                null)),
                                new TowerPixelBackgroundDto(
                                        "https://cdn/tower/pixel/9f2c41aa0b7d3e11.png",
                                        1056,
                                        576,
                                        "9f2c41aa0b7d3e11"),
                                new TowerPixelBackgroundDto(
                                        "https://cdn/tower/pixel/1b4e7c9d2f0a6e38.png",
                                        704,
                                        384,
                                        "1b4e7c9d2f0a6e38"),
                                new TowerFloorGuideView(
                                        3L,
                                        2,
                                        List.of(
                                                new TowerNpcLineDto(
                                                        "어서 와, 처음이지?",
                                                        "Welcome, first time?",
                                                        "ようこそ、初めて？",
                                                        "أهلًا، أول مرة؟"),
                                                new TowerNpcLineDto(
                                                        "미션을 시작하면 자리에 앉아요",
                                                        "Start a mission to take a seat",
                                                        "ミッションを始めると席に着くよ",
                                                        "ابدأ مهمة لتجلس")),
                                        true,
                                        "미션 시작하기",
                                        "Start a mission",
                                        null,
                                        null)),
                        new TowerFloorResponse(
                                2L,
                                2,
                                "등나무 정원",
                                "Wisteria Garden",
                                "藤の庭",
                                "حديقة الوستارية",
                                5,
                                true,
                                false,
                                "https://cdn/bg2.jpg",
                                "https://cdn/mask2.png",
                                1200,
                                900,
                                null,
                                30,
                                0L,
                                null,
                                List.of(),
                                List.of(),
                                null,
                                null,
                                null)),
                1);
    }

    @Test
    @DisplayName("GET /api/v1/tower/floors : 공개 층 목록 + 잠금 + 기본 층 (REST Docs)")
    void floorsForUser() throws Exception {
        // 필터를 끈 슬라이스에서는 @CurrentUser 가 주입되지 않는다(다른 컨트롤러 테스트와 같음) — 유저 전달은 리졸버 테스트 몫
        when(queryService.listForUser(any())).thenReturn(sample());

        mockMvc.perform(
                        RestDocumentationRequestBuilders.get("/api/v1/tower/floors")
                                .with(user(USER_ID))
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.default_floor_number").value(1))
                .andExpect(jsonPath("$.value.floors[1].locked").value(true))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "tower-floors-list",
                                ResourceSnippetParameters.builder()
                                        .tag("Tower")
                                        .description(
                                                "공개 층 목록 (LUT-629) — 층 번호 순, 잠금은 서버 판정, 기본 층·층 인원·첫"
                                                        + " 방문 안내·효과 포함. 비로그인 허용"),
                                responseFields(
                                        fieldWithPath("code").description("응답 코드"),
                                        fieldWithPath("message").description("응답 메시지"),
                                        fieldWithPath("value.default_floor_number")
                                                .description(
                                                        "기본 입장 층 — 마지막으로 머문 층(입장 가능하면), 아니면 입장 가능한"
                                                                + " 가장 낮은 층, 없으면 null")
                                                .optional(),
                                        fieldWithPath("value.floors[].id").description("층 ID"),
                                        fieldWithPath("value.floors[].floor_number")
                                                .description("층 번호"),
                                        fieldWithPath("value.floors[].name")
                                                .description("층 이름(ko)"),
                                        fieldWithPath("value.floors[].name_en")
                                                .description("층 이름(en)"),
                                        fieldWithPath("value.floors[].name_ja")
                                                .description("층 이름(ja)"),
                                        fieldWithPath("value.floors[].name_ar")
                                                .description("층 이름(ar)"),
                                        fieldWithPath("value.floors[].entry_level")
                                                .description("입장 레벨 (null = 제한 없음)")
                                                .optional(),
                                        fieldWithPath("value.floors[].locked")
                                                .description(
                                                        "잠금 — 로그인 유저는 레벨 부족, 방문객은 비로그인 비허용 층 (서버"
                                                                + " 판정)"),
                                        fieldWithPath("value.floors[].guest_allowed")
                                                .description("비로그인 입장 허용"),
                                        fieldWithPath("value.floors[].background_image_url")
                                                .description("배경 이미지 URL"),
                                        fieldWithPath("value.floors[].floor_mask_url")
                                                .description("바닥 마스크 PNG URL"),
                                        fieldWithPath("value.floors[].entry_point_x")
                                                .description("입장 지점 X (배경 원본 픽셀)"),
                                        fieldWithPath("value.floors[].entry_point_y")
                                                .description("입장 지점 Y"),
                                        fieldWithPath("value.floors[].bgm_url")
                                                .description("배경 음악 URL")
                                                .optional(),
                                        fieldWithPath("value.floors[].instance_capacity")
                                                .description("인스턴스(방) 상한"),
                                        fieldWithPath("value.floors[].population")
                                                .description(
                                                        "층 전체 인원 (모든 인스턴스 합, 방문객·자리 유지·자동 착석 포함,"
                                                                + " NPC 제외, 30초 메모 — LUT-630)"),
                                        fieldWithPath("value.floors[].occlusion_mask_url")
                                                .description(
                                                        "앞 가림 영역 마스크 PNG URL (LUT-725, 없으면 null) —"
                                                            + " 칠한 덩어리의 아래쪽 가장자리를 기준선으로 캐릭터 앞·뒤를"
                                                            + " 정한다")
                                                .optional(),
                                        fieldWithPath("value.floors[].lights[]")
                                                .description("불빛 점 (LUT-725, 최대 50개)"),
                                        fieldWithPath("value.floors[].lights[].x")
                                                .description("X (배경 원본 픽셀)"),
                                        fieldWithPath("value.floors[].lights[].y")
                                                .description("Y (배경 원본 픽셀)"),
                                        fieldWithPath("value.floors[].lights[].radius")
                                                .description("반경 (배경 원본 픽셀)"),
                                        fieldWithPath("value.floors[].lights[].color")
                                                .description("색 #rrggbb"),
                                        fieldWithPath("value.floors[].lights[].brightness")
                                                .description("밝기 0~100"),
                                        fieldWithPath("value.floors[].lights[].flicker_intensity")
                                                .description("깜빡임 세기 0~100 (0 = 깜빡이지 않음)"),
                                        fieldWithPath("value.floors[].lights[].flicker_speed")
                                                .description("깜빡임 속도 0~100"),
                                        fieldWithPath("value.floors[].effect_layers[]")
                                                .description(
                                                        "오늘(KST) 켜진 효과 레이어만 (LUT-725 — 꺼졌거나 기간 밖은"
                                                                + " 빠진다)"),
                                        fieldWithPath("value.floors[].effect_layers[].kind")
                                                .description("SNOW(눈) / STARS(별 반짝임)"),
                                        fieldWithPath("value.floors[].effect_layers[].mask_url")
                                                .description("효과 영역 마스크 PNG URL (칠한 곳에서만 그린다)"),
                                        fieldWithPath("value.floors[].effect_layers[].enabled")
                                                .description("켜기 (응답에는 항상 true)"),
                                        fieldWithPath("value.floors[].effect_layers[].starts_on")
                                                .description("기간 시작일 (KST, 포함, null = 상시)")
                                                .optional(),
                                        fieldWithPath("value.floors[].effect_layers[].ends_on")
                                                .description("기간 종료일 (KST, 포함, null = 상시)")
                                                .optional(),
                                        fieldWithPath("value.floors[].effect_layers[].amount")
                                                .description("SNOW = 밀도(동시 눈송이 수) / STARS = 별 개수"),
                                        fieldWithPath("value.floors[].effect_layers[].speed")
                                                .description("낙하·반짝임 속도 1~100"),
                                        fieldWithPath("value.floors[].effect_layers[].size_min")
                                                .description("크기 최소 (화면 픽셀 1~20)"),
                                        fieldWithPath("value.floors[].effect_layers[].size_max")
                                                .description("크기 최대 (화면 픽셀 1~20)"),
                                        fieldWithPath("value.floors[].effect_layers[].spread")
                                                .type(JsonFieldType.NUMBER)
                                                .description(
                                                        "LUT-740 구름(CLOUD) 번짐 — 칠한 영역 바깥으로 옅게 퍼지는"
                                                            + " 거리(원본 px 0~200), 눈·별은 null. 구름은"
                                                            + " amount = 짙기 0~100, speed = 흐름 속도"
                                                            + " 0~100")
                                                .optional(),
                                        fieldWithPath("value.floors[].pixel_dense")
                                                .description(
                                                        "고밀도 픽셀 배경 (LUT-727, 높이 576·입장 기본) — 변환"
                                                            + " 전·실패면 null(원본 background_image_url"
                                                            + " 로 보여 준다)")
                                                .optional(),
                                        fieldWithPath("value.floors[].pixel_dense.url")
                                                .description("64색 인덱스 PNG URL (보간 없이 확대)")
                                                .optional(),
                                        fieldWithPath("value.floors[].pixel_dense.width")
                                                .description("가로 px (원본 비율)")
                                                .optional(),
                                        fieldWithPath("value.floors[].pixel_dense.height")
                                                .description("세로 px (576 고정)")
                                                .optional(),
                                        fieldWithPath("value.floors[].pixel_dense.version")
                                                .description(
                                                        "이미지 버전(내용 해시) — 프론트 효과 맵의 버전과 다르면 효과만"
                                                                + " 끈다")
                                                .optional(),
                                        fieldWithPath("value.floors[].pixel_standard")
                                                .description("픽셀 배경 (높이 384) — 없으면 null")
                                                .optional(),
                                        fieldWithPath("value.floors[].pixel_standard.url")
                                                .description("64색 인덱스 PNG URL")
                                                .optional(),
                                        fieldWithPath("value.floors[].pixel_standard.width")
                                                .description("가로 px")
                                                .optional(),
                                        fieldWithPath("value.floors[].pixel_standard.height")
                                                .description("세로 px (384 고정)")
                                                .optional(),
                                        fieldWithPath("value.floors[].pixel_standard.version")
                                                .description("이미지 버전(내용 해시)")
                                                .optional(),
                                        fieldWithPath("value.floors[].guide")
                                                .type(JsonFieldType.OBJECT)
                                                .description(
                                                        "LUT-734 첫 방문 안내 — 꺼져 있거나 안내 NPC 가 비공개·없으면"
                                                            + " null. 언제 보일지는 입장 응답 first_visit")
                                                .optional(),
                                        fieldWithPath("value.floors[].guide.npc_id")
                                                .type(JsonFieldType.NUMBER)
                                                .description("안내 NPC (입장 스냅샷 npcs[] 중 하나)")
                                                .optional(),
                                        fieldWithPath("value.floors[].guide.version")
                                                .type(JsonFieldType.NUMBER)
                                                .description("안내 버전 (다시 보여 주기로 저장하면 오른다)")
                                                .optional(),
                                        fieldWithPath("value.floors[].guide.lines[].text")
                                                .type(JsonFieldType.STRING)
                                                .description("안내 문장 (한국어, 1~5개, 차례로 말한다)")
                                                .optional(),
                                        fieldWithPath("value.floors[].guide.lines[].text_en")
                                                .type(JsonFieldType.STRING)
                                                .description("안내 문장 (영어)")
                                                .optional(),
                                        fieldWithPath("value.floors[].guide.lines[].text_ja")
                                                .type(JsonFieldType.STRING)
                                                .description("안내 문장 (일본어)")
                                                .optional(),
                                        fieldWithPath("value.floors[].guide.lines[].text_ar")
                                                .type(JsonFieldType.STRING)
                                                .description("안내 문장 (아랍어)")
                                                .optional(),
                                        fieldWithPath("value.floors[].guide.show_mission_button")
                                                .type(JsonFieldType.BOOLEAN)
                                                .description("마지막 문장에 미션 시작 버튼")
                                                .optional(),
                                        fieldWithPath("value.floors[].guide.mission_button_label")
                                                .type(JsonFieldType.STRING)
                                                .description("버튼 문구 (한국어, 비우면 앱 기본 문구)")
                                                .optional(),
                                        fieldWithPath(
                                                        "value.floors[].guide.mission_button_label_en")
                                                .type(JsonFieldType.STRING)
                                                .description("버튼 문구 (영어)")
                                                .optional(),
                                        fieldWithPath(
                                                        "value.floors[].guide.mission_button_label_ja")
                                                .type(JsonFieldType.STRING)
                                                .description("버튼 문구 (일본어)")
                                                .optional(),
                                        fieldWithPath(
                                                        "value.floors[].guide.mission_button_label_ar")
                                                .type(JsonFieldType.STRING)
                                                .description("버튼 문구 (아랍어)")
                                                .optional())));
    }

    @Test
    @DisplayName("비로그인도 호출할 수 있다 — userId 없이 서비스에 넘긴다")
    void floorsForGuest() throws Exception {
        when(queryService.listForUser(null))
                .thenReturn(new TowerFloorListResponse(List.of(), null));

        mockMvc.perform(RestDocumentationRequestBuilders.get("/api/v1/tower/floors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.floors").isEmpty());

        verify(queryService).listForUser(isNull());
    }
}
