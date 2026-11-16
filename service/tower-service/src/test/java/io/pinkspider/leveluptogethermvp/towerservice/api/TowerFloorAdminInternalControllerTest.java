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
import io.pinkspider.leveluptogethermvp.towerservice.application.TowerFloorAdminService;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerFloorException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorAdminRequest;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorAdminResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorEffectLayerDto;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorLightDto;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerPixelArtAdminDto;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerPixelBackgroundDto;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerEffectKind;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerPixelArtStatus;
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
        controllers = TowerFloorAdminInternalController.class,
        excludeAutoConfiguration = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class
        })
@Import(ControllerTestConfig.class)
@AutoConfigureRestDocs
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("TowerFloorAdminInternalController 테스트 (LUT-629)")
class TowerFloorAdminInternalControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private TowerFloorAdminService adminService;

    @MockitoBean
    private io.pinkspider.leveluptogethermvp.towerservice.application.TowerFloorImageService
            imageService;

    @MockitoBean
    private io.pinkspider.leveluptogethermvp.towerservice.application.TowerFloorAudioService
            audioService;

    private static final String BODY =
            """
{"floor_number":2,"name":"등나무 정원","name_en":"Wisteria Garden","name_ja":"藤の庭","name_ar":"حديقة الوستارية",
 "entry_level":5,"guest_allowed":false,"background_image_url":"https://cdn/bg2.jpg","floor_mask_url":"https://cdn/mask2.png",
 "entry_point_x":1200,"entry_point_y":900,"bgm_url":null,"instance_capacity":30,"is_public":true,
 "occlusion_mask_url":"https://cdn/occlusion2.png",
 "lights":[{"x":640,"y":320,"radius":60,"color":"#ffcc66","brightness":80,"flicker_intensity":40,"flicker_speed":50}],
 "effect_layers":[{"kind":"SNOW","mask_url":"https://cdn/snow2.png","enabled":true,"starts_on":"2026-12-01","ends_on":"2026-12-31",
   "amount":120,"speed":40,"size_min":1,"size_max":3}]}
""";

    private static TowerFloorAdminResponse response() {
        return new TowerFloorAdminResponse(
                2L,
                2,
                "등나무 정원",
                "Wisteria Garden",
                "藤の庭",
                "حديقة الوستارية",
                5,
                false,
                "https://cdn/bg2.jpg",
                "https://cdn/mask2.png",
                1200,
                900,
                null,
                30,
                true,
                LocalDateTime.of(2026, 11, 3, 15, 0),
                LocalDateTime.of(2026, 11, 3, 15, 0),
                "https://cdn/occlusion2.png",
                List.of(new TowerFloorLightDto(640, 320, 60, "#ffcc66", 80, 40, 50)),
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
                                null,
                                null)),
                pixelArt());
    }

    private static TowerPixelArtAdminDto pixelArt() {
        return new TowerPixelArtAdminDto(
                TowerPixelArtStatus.DONE,
                64,
                100,
                false,
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
                null,
                LocalDateTime.of(2026, 11, 3, 15, 0),
                LocalDateTime.of(2026, 11, 3, 15, 0, 4));
    }

    /** LUT-727: 픽셀아트 상태 (응답) */
    private static List<FieldDescriptor> pixelArtFields(String prefix) {
        return List.of(
                fieldWithPath(prefix + "status")
                        .description("PENDING(대기·변환 중) / DONE / FAILED(재시도 1회 뒤)"),
                fieldWithPath(prefix + "colors").description("팔레트 색 수 (기본 64)"),
                fieldWithPath(prefix + "sharpness").description("선명도 % (기본 100)"),
                fieldWithPath(prefix + "stale")
                        .description("결과가 지금 배경이 아니라 예전 배경으로 만든 것인가 (재변환 대기)"),
                fieldWithPath(prefix + "dense").description("고밀도 픽셀 (높이 576)").optional(),
                fieldWithPath(prefix + "dense.url").description("인덱스 PNG URL").optional(),
                fieldWithPath(prefix + "dense.width").description("가로 px").optional(),
                fieldWithPath(prefix + "dense.height").description("세로 px").optional(),
                fieldWithPath(prefix + "dense.version").description("내용 해시").optional(),
                fieldWithPath(prefix + "standard").description("픽셀 (높이 384)").optional(),
                fieldWithPath(prefix + "standard.url").description("인덱스 PNG URL").optional(),
                fieldWithPath(prefix + "standard.width").description("가로 px").optional(),
                fieldWithPath(prefix + "standard.height").description("세로 px").optional(),
                fieldWithPath(prefix + "standard.version").description("내용 해시").optional(),
                fieldWithPath(prefix + "error_message").description("실패 사유").optional(),
                fieldWithPath(prefix + "requested_at").description("요청 시각").optional(),
                fieldWithPath(prefix + "completed_at").description("완료·실패 시각").optional());
    }

    /** LUT-725: 앞 가림·불빛 점·효과 레이어 (요청·응답 같은 모양) */
    private static List<FieldDescriptor> effectFields(String prefix, boolean request) {
        String keep = request ? " — 보내지 않으면(null) 그대로, " : " — ";
        return List.of(
                fieldWithPath(prefix + "occlusion_mask_url")
                        .description(
                                "앞 가림 영역 마스크 PNG URL (선택, 배경과 같은 비율 — 150116)"
                                        + keep
                                        + (request ? "빈 문자열이면 지운다" : "없으면 null"))
                        .optional(),
                fieldWithPath(prefix + "lights[]")
                        .description("불빛 점 (최대 50개)" + keep + (request ? "[] 이면 모두 지운다" : "등록 순서"))
                        .optional(),
                fieldWithPath(prefix + "lights[].x").description("X (배경 원본 픽셀)"),
                fieldWithPath(prefix + "lights[].y").description("Y (배경 원본 픽셀)"),
                fieldWithPath(prefix + "lights[].radius").description("반경 4~400 (배경 원본 픽셀)"),
                fieldWithPath(prefix + "lights[].color").description("색 #rrggbb"),
                fieldWithPath(prefix + "lights[].brightness").description("밝기 0~100"),
                fieldWithPath(prefix + "lights[].flicker_intensity")
                        .description("깜빡임 세기 0~100 (0 = 깜빡이지 않음)"),
                fieldWithPath(prefix + "lights[].flicker_speed").description("깜빡임 속도 0~100"),
                fieldWithPath(prefix + "effect_layers[]")
                        .description(
                                "효과 레이어 SNOW·STARS 각 1개 (중복 150115)"
                                        + keep
                                        + (request ? "[] 이면 모두 지운다" : "꺼진 것·기간 밖 포함"))
                        .optional(),
                fieldWithPath(prefix + "effect_layers[].kind")
                        .description("SNOW(눈) / STARS(별 반짝임)"),
                fieldWithPath(prefix + "effect_layers[].mask_url")
                        .description("효과 영역 마스크 PNG URL (배경과 같은 비율 — 150116)"),
                fieldWithPath(prefix + "effect_layers[].enabled").description("켜기"),
                fieldWithPath(prefix + "effect_layers[].starts_on")
                        .description("기간 시작일 (KST, 포함, 비우면 상시)")
                        .optional(),
                fieldWithPath(prefix + "effect_layers[].ends_on")
                        .description("기간 종료일 (KST, 포함, 비우면 상시, 시작일 이후 — 150115)")
                        .optional(),
                fieldWithPath(prefix + "effect_layers[].amount")
                        .description("SNOW = 밀도(동시 눈송이 수) / STARS = 별 개수 (1~300)"),
                fieldWithPath(prefix + "effect_layers[].speed").description("낙하·반짝임 속도 1~100"),
                fieldWithPath(prefix + "effect_layers[].size_min")
                        .description("크기 최소 1~20 (화면 픽셀)"),
                fieldWithPath(prefix + "effect_layers[].size_max")
                        .description("크기 최대 1~20 (최소 이상 — 150115)"),
                fieldWithPath(prefix + "effect_layers[].spread")
                        .type(JsonFieldType.NUMBER)
                        .description(
                                "LUT-740 CLOUD 번짐(원본 px 0~200, 비우면 60) — 구름은 amount 짙기 0~100·speed"
                                        + " 흐름 0~100, 크기는 쓰지 않는다. 종류별 범위 밖 150115")
                        .optional(),
                fieldWithPath(prefix + "effect_layers[].drop_count")
                        .type(JsonFieldType.NUMBER)
                        .description(
                                "LUT-785 RAIN(유리창 빗물) 흐르는 물방울 수 0~120(비우면 46) — 물방울은 amount 맺힌"
                                        + " 물방울 밀도 1~100·speed 흐르는 속도 1~100(50 = 시안), 크기는 쓰지 않는다."
                                        + " 다른 종류는 null")
                        .optional());
    }

    private static FieldDescriptor[] floorFields(String prefix) {
        return new FieldDescriptor[] {
            fieldWithPath(prefix + "id").description("층 ID"),
            fieldWithPath(prefix + "floor_number").description("층 번호 (1부터, 유일)"),
            fieldWithPath(prefix + "name").description("층 이름(ko)"),
            fieldWithPath(prefix + "name_en").description("층 이름(en)"),
            fieldWithPath(prefix + "name_ja").description("층 이름(ja)"),
            fieldWithPath(prefix + "name_ar").description("층 이름(ar)"),
            fieldWithPath(prefix + "entry_level").description("입장 레벨 (null = 제한 없음)").optional(),
            fieldWithPath(prefix + "guest_allowed").description("비로그인 입장 허용"),
            fieldWithPath(prefix + "background_image_url").description("배경 이미지 URL"),
            fieldWithPath(prefix + "floor_mask_url").description("바닥 마스크 PNG URL (붉은색 = 이동 가능)"),
            fieldWithPath(prefix + "entry_point_x").description("입장 지점 X (배경 원본 픽셀)"),
            fieldWithPath(prefix + "entry_point_y").description("입장 지점 Y (배경 원본 픽셀)"),
            fieldWithPath(prefix + "bgm_url")
                    .description("배경 음악 URL (공개 층은 필수 — 150114, LUT-637)")
                    .optional(),
            fieldWithPath(prefix + "instance_capacity").description("인스턴스(방) 상한"),
            fieldWithPath(prefix + "is_public").description("공개 여부"),
            fieldWithPath(prefix + "created_at").description("생성 시각"),
            fieldWithPath(prefix + "modified_at").description("수정 시각")
        };
    }

    private static FieldDescriptor[] floorFieldsWithEffects(String prefix) {
        List<FieldDescriptor> all = new java.util.ArrayList<>(List.of(floorFields(prefix)));
        all.addAll(effectFields(prefix, false));
        all.add(
                fieldWithPath(prefix + "pixel_art")
                        .description("LUT-727 픽셀 배경 변환 상태 (아직 요청 전이면 null)")
                        .optional());
        all.addAll(pixelArtFields(prefix + "pixel_art."));
        return all.toArray(FieldDescriptor[]::new);
    }

    private static FieldDescriptor[] withEnvelope(FieldDescriptor[] fields) {
        FieldDescriptor[] all = new FieldDescriptor[fields.length + 2];
        all[0] = fieldWithPath("code").description("응답 코드");
        all[1] = fieldWithPath("message").description("응답 메시지");
        System.arraycopy(fields, 0, all, 2, fields.length);
        return all;
    }

    @Test
    @DisplayName("GET /api/internal/tower/floors : 층 번호 순 전체 목록")
    void list() throws Exception {
        when(adminService.list()).thenReturn(List.of(response()));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.get("/api/internal/tower/floors")
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value[0].floor_number").value(2))
                .andExpect(jsonPath("$.value[0].entry_level").value(5))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "internal-tower-floors-list",
                                ResourceSnippetParameters.builder()
                                        .tag("Tower - Admin Internal")
                                        .description("층 전체 목록 (LUT-629) — 층 번호 순, 비공개 층 포함"),
                                responseFields(withEnvelope(floorFieldsWithEffects("value[].")))));
    }

    @Test
    @DisplayName("POST /api/internal/tower/floors : 등록")
    void create() throws Exception {
        when(adminService.create(any(TowerFloorAdminRequest.class))).thenReturn(response());

        mockMvc.perform(
                        RestDocumentationRequestBuilders.post("/api/internal/tower/floors")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.id").value(2))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "internal-tower-floors-create",
                                ResourceSnippetParameters.builder()
                                        .tag("Tower - Admin Internal")
                                        .description("층 등록 (LUT-629) — 저장 규칙 7개를 어기면 400 1501xx"),
                                requestFields(
                                                fieldWithPath("floor_number")
                                                        .description("층 번호 (1 이상, 유일)"),
                                                fieldWithPath("name").description("층 이름(ko)"),
                                                fieldWithPath("name_en").description("층 이름(en)"),
                                                fieldWithPath("name_ja").description("층 이름(ja)"),
                                                fieldWithPath("name_ar").description("층 이름(ar)"),
                                                fieldWithPath("entry_level")
                                                        .description("입장 레벨 (비우면 제한 없음, 1층은 비워야 함)")
                                                        .optional(),
                                                fieldWithPath("guest_allowed")
                                                        .description("비로그인 입장 허용 (입장 레벨이 없는 층만)"),
                                                fieldWithPath("background_image_url")
                                                        .description("배경 이미지 URL"),
                                                fieldWithPath("floor_mask_url")
                                                        .description("바닥 마스크 PNG URL"),
                                                fieldWithPath("entry_point_x")
                                                        .description("입장 지점 X (마스크 안이어야 함)"),
                                                fieldWithPath("entry_point_y")
                                                        .description("입장 지점 Y"),
                                                fieldWithPath("bgm_url")
                                                        .description("배경 음악 URL")
                                                        .optional(),
                                                fieldWithPath("instance_capacity")
                                                        .description("인스턴스 상한 (비우면 30)")
                                                        .optional(),
                                                fieldWithPath("is_public").description("공개 여부"))
                                        .and(effectFields("", true)),
                                responseFields(withEnvelope(floorFieldsWithEffects("value.")))));
    }

    @Test
    @DisplayName("POST : 저장 규칙 위반은 400 + 1501xx (TowerExceptionHandler), @Valid 위반은 400")
    void createRejected() throws Exception {
        when(adminService.create(any(TowerFloorAdminRequest.class)))
                .thenThrow(TowerFloorException.entryLevelOrder());

        mockMvc.perform(
                        RestDocumentationRequestBuilders.post("/api/internal/tower/floors")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("150103"));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.post("/api/internal/tower/floors")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY.replace("\"floor_number\":2", "\"floor_number\":0")))
                .andExpect(status().isBadRequest());

        // SSRF 방어: 이미지·마스크 URL 은 https 만 (서버가 읽는 URL)
        mockMvc.perform(
                        RestDocumentationRequestBuilders.post("/api/internal/tower/floors")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        BODY.replace(
                                                "https://cdn/mask2.png",
                                                "http://10.0.1.236:10085/x.png")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/internal/tower/floors/{id} : 수정 · GET 단건·없음 404 · DELETE")
    void updateGetDelete() throws Exception {
        when(adminService.update(eq(2L), any(TowerFloorAdminRequest.class))).thenReturn(response());
        when(adminService.get(2L)).thenReturn(response());
        when(adminService.get(99L)).thenThrow(TowerFloorException.notFound());

        mockMvc.perform(
                        RestDocumentationRequestBuilders.put("/api/internal/tower/floors/{id}", 2L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.floor_number").value(2))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "internal-tower-floors-update",
                                ResourceSnippetParameters.builder()
                                        .tag("Tower - Admin Internal")
                                        .description(
                                                "층 수정 (LUT-629) — 효과·앞 가림 필드는 null 이면 그대로(LUT-725)."
                                                        + " 안 쓰게 된 우리 저장소 파일은 커밋 뒤 지운다"),
                                pathParameters(parameterWithName("id").description("층 ID")),
                                responseFields(withEnvelope(floorFieldsWithEffects("value.")))));

        // LUT-741: 단건 조회 — 어드민 층 수정 화면이 연다
        mockMvc.perform(RestDocumentationRequestBuilders.get("/api/internal/tower/floors/{id}", 2L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.floor_number").value(2))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "internal-tower-floors-get",
                                ResourceSnippetParameters.builder()
                                        .tag("Tower - Admin Internal")
                                        .description("층 단건 조회 (LUT-629) — 없으면 404 150101"),
                                pathParameters(parameterWithName("id").description("층 ID")),
                                responseFields(withEnvelope(floorFieldsWithEffects("value.")))));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.get(
                                "/api/internal/tower/floors/{id}", 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("150101"));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.delete(
                                "/api/internal/tower/floors/{id}", 2L))
                .andExpect(status().isOk())
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "internal-tower-floors-delete",
                                ResourceSnippetParameters.builder()
                                        .tag("Tower - Admin Internal")
                                        .description("층 삭제 (LUT-629) — 남는 층끼리 저장 규칙을 다시 본다"),
                                pathParameters(parameterWithName("id").description("층 ID"))));
    }

    @org.junit.jupiter.api.Test
    @DisplayName("POST /api/internal/tower/floors/images?kind=MASK : 이미지 업로드 (LUT-635)")
    void uploadImage() throws Exception {
        org.mockito.Mockito.when(
                        imageService.upload(
                                org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.eq(
                                        io.pinkspider.leveluptogethermvp.towerservice.domain.enums
                                                .TowerImageKind.MASK)))
                .thenReturn(
                        new io.pinkspider.leveluptogethermvp.towerservice.domain.dto
                                .TowerFloorImageUploadResponse(
                                io.pinkspider.leveluptogethermvp.towerservice.domain.enums
                                        .TowerImageKind.MASK,
                                "https://dev.level-up-together.com:8443/uploads/tower/mask/m.png",
                                704,
                                384));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.multipart(
                                        "/api/internal/tower/floors/images?kind=mask")
                                .file(
                                        new org.springframework.mock.web.MockMultipartFile(
                                                "file",
                                                "mask.png",
                                                "image/png",
                                                new byte[] {1, 2, 3}))
                                .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.kind").value("MASK"))
                .andExpect(jsonPath("$.value.width").value(704))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "internal-tower-floors-image-upload",
                                ResourceSnippetParameters.builder()
                                        .tag("Tower - Admin Internal")
                                        .description(
                                                "층 이미지 업로드 (LUT-635) — kind=BACKGROUND·MASK·NPC, 절대"
                                                        + " https 주소를 돌려준다. 마스크는 PNG 만(150112)"),
                                org.springframework.restdocs.request.RequestDocumentation
                                        .queryParameters(
                                                org.springframework.restdocs.request
                                                        .RequestDocumentation.parameterWithName(
                                                                "kind")
                                                        .description(
                                                                "BACKGROUND | MASK (대소문자 무관). 마스크는"
                                                                        + " PNG 만")),
                                org.springframework.restdocs.request.RequestDocumentation
                                        .requestParts(
                                                org.springframework.restdocs.request
                                                        .RequestDocumentation.partWithName("file")
                                                        .description(
                                                                "이미지 파일 (jpg·png·webp, 15MB 이하)")),
                                responseFields(
                                        fieldWithPath("code").description("응답 코드"),
                                        fieldWithPath("message").description("응답 메시지"),
                                        fieldWithPath("value").description("업로드 결과"),
                                        fieldWithPath("value.kind").description("이미지 종류"),
                                        fieldWithPath("value.url")
                                                .description(
                                                        "절대 URL — 등록/수정의"
                                                            + " background_image_url·floor_mask_url"
                                                            + " 에 그대로"),
                                        fieldWithPath("value.width")
                                                .description("가로 픽셀 (읽을 수 없으면 null)")
                                                .optional(),
                                        fieldWithPath("value.height")
                                                .description("세로 픽셀 (읽을 수 없으면 null)")
                                                .optional())));
    }

    @org.junit.jupiter.api.Test
    @DisplayName("POST /images : kind 없음·모르는 값은 400 150110, 서비스 거절(PNG 아님)은 400 150112")
    void uploadImageRejected() throws Exception {
        org.springframework.mock.web.MockMultipartFile file =
                new org.springframework.mock.web.MockMultipartFile(
                        "file", "mask.jpg", "image/jpeg", new byte[] {1});
        mockMvc.perform(
                        RestDocumentationRequestBuilders.multipart(
                                        "/api/internal/tower/floors/images")
                                .file(file)
                                .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("150110"));
        mockMvc.perform(
                        RestDocumentationRequestBuilders.multipart(
                                        "/api/internal/tower/floors/images?kind=sticker")
                                .file(file)
                                .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("150110"));

        org.mockito.Mockito.when(
                        imageService.upload(
                                org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.eq(
                                        io.pinkspider.leveluptogethermvp.towerservice.domain.enums
                                                .TowerImageKind.MASK)))
                .thenThrow(
                        io.pinkspider.leveluptogethermvp.towerservice.application.exception
                                .TowerFloorException.maskNotPng());
        mockMvc.perform(
                        RestDocumentationRequestBuilders.multipart(
                                        "/api/internal/tower/floors/images?kind=MASK")
                                .file(file)
                                .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("150112"));
    }

    @org.junit.jupiter.api.Test
    @DisplayName(
            "POST /api/internal/tower/floors/audio : 배경 음악 업로드 (LUT-637) · /images?kind=bgm 은"
                    + " 150110")
    void uploadAudio() throws Exception {
        org.springframework.mock.web.MockMultipartFile file =
                new org.springframework.mock.web.MockMultipartFile(
                        "file", "loop.mp3", "audio/mpeg", new byte[] {'I', 'D', '3', 4});
        org.mockito.Mockito.when(audioService.upload(org.mockito.ArgumentMatchers.any()))
                .thenReturn(
                        new io.pinkspider.leveluptogethermvp.towerservice.domain.dto
                                .TowerFloorAudioUploadResponse(
                                "https://dev.level-up-together.com:8443/uploads/tower/bgm/a.mp3",
                                4L,
                                "audio/mpeg"));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.multipart(
                                        "/api/internal/tower/floors/audio")
                                .file(file)
                                .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.value.url")
                                .value(
                                        "https://dev.level-up-together.com:8443/uploads/tower/bgm/a.mp3"))
                .andExpect(jsonPath("$.value.size_bytes").value(4))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "internal-tower-floors-audio-upload",
                                ResourceSnippetParameters.builder()
                                        .tag("Tower - Admin Internal")
                                        .description(
                                                "배경 음악 업로드 (LUT-637) — mp3·aac·m4a 10MB, 헤더로 진짜"
                                                        + " 오디오인지 본다(아니면 400 150113)"),
                                org.springframework.restdocs.request.RequestDocumentation
                                        .requestParts(
                                                org.springframework.restdocs.request
                                                        .RequestDocumentation.partWithName("file")
                                                        .description(
                                                                "mp3·aac·m4a, 10MB 이하 (어긋나면"
                                                                        + " 150113)")),
                                org.springframework.restdocs.payload.PayloadDocumentation
                                        .responseFields(
                                                org.springframework.restdocs.payload
                                                        .PayloadDocumentation.fieldWithPath("code")
                                                        .description("응답 코드"),
                                                org.springframework.restdocs.payload
                                                        .PayloadDocumentation.fieldWithPath(
                                                                "message")
                                                        .description("응답 메시지"),
                                                org.springframework.restdocs.payload
                                                        .PayloadDocumentation.fieldWithPath(
                                                                "value.url")
                                                        .description(
                                                                "절대 URL — 등록/수정의 bgm_url 에 그대로"),
                                                org.springframework.restdocs.payload
                                                        .PayloadDocumentation.fieldWithPath(
                                                                "value.size_bytes")
                                                        .description("파일 크기(바이트)"),
                                                org.springframework.restdocs.payload
                                                        .PayloadDocumentation.fieldWithPath(
                                                                "value.content_type")
                                                        .description("업로드 Content-Type")
                                                        .optional())));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.multipart(
                                        "/api/internal/tower/floors/images?kind=bgm")
                                .file(file)
                                .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("150110"));
        // LUT-727: 픽셀 배경은 업로드로 받지 않는다
        mockMvc.perform(
                        RestDocumentationRequestBuilders.multipart(
                                        "/api/internal/tower/floors/images?kind=pixel")
                                .file(file)
                                .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("150110"));
    }

    @Test
    @DisplayName(
            "LUT-727 POST /{id}/pixel-art : 설정을 바꿔 재생성 요청 — 바로 대기 상태, 범위 밖 400, 본문 없으면 지금 설정으로")
    void regeneratePixelArt() throws Exception {
        TowerPixelArtAdminDto pending =
                new TowerPixelArtAdminDto(
                        TowerPixelArtStatus.PENDING,
                        32,
                        120,
                        false,
                        pixelArt().dense(),
                        pixelArt().standard(),
                        null,
                        LocalDateTime.of(2026, 11, 4, 23, 0),
                        LocalDateTime.of(2026, 11, 3, 15, 0, 4));
        when(adminService.regeneratePixelArt(2L, 32, 120)).thenReturn(pending);

        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                        "/api/internal/tower/floors/{id}/pixel-art", 2L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"colors\":32,\"sharpness\":120}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.status").value("PENDING"))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "internal-tower-floors-pixel-art-regenerate",
                                ResourceSnippetParameters.builder()
                                        .tag("Tower - Admin Internal")
                                        .description(
                                                "픽셀아트 배경 재생성 (LUT-727/728) — 색 수·선명도를 바꿔 다시 만든다. 바로"
                                                        + " 대기 상태가 된다"),
                                requestFields(
                                        fieldWithPath("colors")
                                                .description("팔레트 색 수 8~256 (비우면 지금 값)")
                                                .optional(),
                                        fieldWithPath("sharpness")
                                                .description("선명도 0~200% (비우면 지금 값)")
                                                .optional()),
                                responseFields(
                                        withEnvelope(
                                                pixelArtFields("value.")
                                                        .toArray(FieldDescriptor[]::new)))));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                        "/api/internal/tower/floors/{id}/pixel-art", 2L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"colors\":4}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                "/api/internal/tower/floors/{id}/pixel-art", 2L))
                .andExpect(status().isOk());
        org.mockito.Mockito.verify(adminService).regeneratePixelArt(2L, null, null);
    }
}
