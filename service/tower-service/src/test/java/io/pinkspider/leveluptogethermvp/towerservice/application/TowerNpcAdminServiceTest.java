package io.pinkspider.leveluptogethermvp.towerservice.application;

import static io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.floor;
import static io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.npc;
import static io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.npcRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.facade.GamificationQueryFacade;
import io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerFloorException;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerNpcException;
import io.pinkspider.leveluptogethermvp.towerservice.application.image.TowerImageStorageService;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcAdminRequest;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcAdminResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcCardDto;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcCardLinkDto;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcFormDto;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcLineDto;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcMotionDto;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcMotionFrameDto;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcView;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpc;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpcForm;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerNpcCardLinkType;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerNpcFormBodyType;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerNpcFormDisplayMode;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerNpcMotionPlayMode;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerNpcFormRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerNpcRepository;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TowerNpcAdminServiceTest {

    private static final String IMG = "https://cdn.test/tower/npc/new.png";

    @Mock private TowerFloorRepository floorRepository;
    @Mock private TowerNpcRepository npcRepository;
    @Mock private TowerNpcFormRepository formRepository;
    @Mock private FloorMaskChecker maskChecker;
    @Mock private TowerStoredFileCleaner fileCleaner;
    @Mock private GamificationQueryFacade gamificationQueryFacade;
    @Mock private TowerImageStorageService imageStorage;

    @InjectMocks private TowerNpcAdminService service;

    private final TowerFloor floor1 = floor(10L, 1, null, true, true);

    @Test
    @DisplayName("등록: 배경 안 자리·2줄 이내 대사면 저장(LUT-746 — 바닥 마스크는 보지 않는다), 공백 상태 문구는 비운다 · 목록은 층별")
    void create() throws IOException {
        when(floorRepository.findById(10L)).thenReturn(Optional.of(floor1));
        when(maskChecker.isInsideImage(floor1.getBackgroundImageUrl(), 300, 400)).thenReturn(true);
        when(npcRepository.save(any(TowerNpc.class))).thenAnswer(inv -> inv.getArgument(0));

        TowerNpcAdminResponse res = service.create(10L, npcRequest(IMG, 300, 400, "가".repeat(160)));
        verify(maskChecker, never()).isWalkable(any(), any(), anyInt(), anyInt());

        ArgumentCaptor<TowerNpc> saved = ArgumentCaptor.forClass(TowerNpc.class);
        verify(npcRepository).save(saved.capture());
        assertThat(saved.getValue().getFloorId()).isEqualTo(10L);
        assertThat(saved.getValue().getStatusText()).isEqualTo("조는 중");
        assertThat(saved.getValue().getStatusTextEn()).isNull(); // " " → null
        assertThat(saved.getValue().getSortOrder()).isZero();
        assertThat(res.lines()).hasSize(1);
        assertThat(res.lines().get(0).text()).hasSize(160); // LUT-751: 잘리지 않고 그대로
        assertThat(res.isPublic()).isTrue();

        when(npcRepository.findAllByFloorIdOrderBySortOrderAscIdAsc(10L))
                .thenReturn(List.of(npc(1L, 10L, true), npc(2L, 10L, false)));
        assertThat(service.list(10L)).extracting(TowerNpcAdminResponse::id).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("LUT-836: 층당 인원 제한이 없다 — 이미 NPC 가 여러 명 있는 층에도 저장되고, 몇 명인지 세지 않는다")
    void createHasNoPerFloorLimit() throws IOException {
        when(floorRepository.findById(10L)).thenReturn(Optional.of(floor1));
        when(maskChecker.isInsideImage(floor1.getBackgroundImageUrl(), 300, 400)).thenReturn(true);
        when(npcRepository.save(any(TowerNpc.class))).thenAnswer(inv -> inv.getArgument(0));

        for (int i = 0; i < 5; i++) {
            assertThat(service.create(10L, npcRequest(IMG, 300, 400, "안녕")).lines()).hasSize(1);
        }

        verify(npcRepository, org.mockito.Mockito.times(5)).save(any(TowerNpc.class));
    }

    @Test
    @DisplayName("거부: 층 없음 404 150101 · 배경 밖 150403 · 배경 못 읽음 150109")
    void createRejected() throws IOException {
        when(floorRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(99L, npcRequest(IMG, 1, 1, "안녕")))
                .isInstanceOf(TowerFloorException.class)
                .extracting("code")
                .isEqualTo(TowerFloorException.NOT_FOUND);
        assertThatThrownBy(() -> service.list(99L)).isInstanceOf(TowerFloorException.class);

        when(floorRepository.findById(10L)).thenReturn(Optional.of(floor1));
        when(maskChecker.isInsideImage(anyString(), anyInt(), anyInt())).thenReturn(false);
        assertThatThrownBy(() -> service.create(10L, npcRequest(IMG, 1, 1, "안녕")))
                .isInstanceOf(TowerNpcException.class)
                .extracting("code")
                .isEqualTo(TowerNpcException.SEAT_OUTSIDE_BACKGROUND);

        when(maskChecker.isInsideImage(anyString(), anyInt(), anyInt()))
                .thenThrow(new IOException("down"));
        assertThatThrownBy(() -> service.create(10L, npcRequest(IMG, 1, 1, "안녕")))
                .isInstanceOf(TowerFloorException.class)
                .extracting("code")
                .isEqualTo(TowerFloorException.MASK_UNREADABLE);
        verify(npcRepository, never()).save(any());
    }

    @Test
    @DisplayName("LUT-751: 대사 한 문장이 160자를 넘으면 어느 언어든 150404 — 160자는 저장된다(예전 상한 한글 40자를 넘어도)")
    void lineTooLong() {
        when(floorRepository.findById(10L)).thenReturn(Optional.of(floor1));
        assertThatThrownBy(() -> service.create(10L, npcRequest(IMG, 1, 1, "가".repeat(161))))
                .isInstanceOf(TowerNpcException.class)
                .extracting("code")
                .isEqualTo(TowerNpcException.LINE_TOO_LONG);
        for (int i = 1; i <= 3; i++) {
            String[] texts = {"안녕", "Welcome", "ようこそ", "مرحبا"};
            texts[i] = i == 2 ? "あ".repeat(161) : "a".repeat(161);
            TowerNpcAdminRequest base = npcRequest(IMG, 1, 1, "안녕");
            TowerNpcAdminRequest request =
                    new TowerNpcAdminRequest(
                            base.name(),
                            base.nameEn(),
                            base.nameJa(),
                            base.nameAr(),
                            IMG,
                            null,
                            null,
                            null,
                            null,
                            1,
                            1,
                            List.of(new TowerNpcLineDto(texts[0], texts[1], texts[2], texts[3])),
                            false,
                            2,
                            null,
                            null,
                            null,
                            null,
                            null);
            assertThatThrownBy(() -> service.create(10L, request))
                    .isInstanceOf(TowerNpcException.class);
        }
    }

    @Test
    @DisplayName("마스크 검사가 꺼져 있으면(local) 자리 검사를 건너뛴다")
    void maskCheckDisabled() {
        ReflectionTestUtils.setField(service, "maskCheckEnabled", false);
        when(floorRepository.findById(10L)).thenReturn(Optional.of(floor1));
        when(npcRepository.save(any(TowerNpc.class))).thenAnswer(inv -> inv.getArgument(0));
        service.create(10L, npcRequest(IMG, 9999, 9999, "안녕"));
        verify(npcRepository).save(any(TowerNpc.class));
    }

    @Test
    @DisplayName("수정: 값·대사를 바꾸고 옛 이미지를 정리 대상으로 · 다른 층의 NPC 는 404 150401 · 삭제도 이미지 정리")
    void updateAndDelete() throws IOException {
        TowerNpc existing = npc(5L, 10L, false);
        String oldImage = existing.getProfileImageUrl();
        when(floorRepository.findById(10L)).thenReturn(Optional.of(floor1));
        when(npcRepository.findByIdAndFloorId(5L, 10L)).thenReturn(Optional.of(existing));
        when(maskChecker.isInsideImage(anyString(), anyInt(), anyInt())).thenReturn(true);

        TowerNpcAdminResponse res = service.update(10L, 5L, npcRequest(IMG, 50, 60, "새 대사"));

        assertThat(res.profileImageUrl()).isEqualTo(IMG);
        assertThat(existing.getSeatX()).isEqualTo(50);
        assertThat(existing.getLines()).hasSize(1);
        assertThat(existing.getLines().get(0).getText()).isEqualTo("새 대사");
        assertThat(existing.isPublicNpc()).isTrue();
        verify(fileCleaner).scheduleCleanup(Set.of(oldImage), Set.of(IMG));

        when(npcRepository.findByIdAndFloorId(6L, 10L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(10L, 6L, npcRequest(IMG, 1, 1, "x")))
                .isInstanceOf(TowerNpcException.class)
                .extracting("code")
                .isEqualTo(TowerNpcException.NOT_FOUND);
        assertThatThrownBy(() -> service.delete(10L, 6L)).isInstanceOf(TowerNpcException.class);

        service.delete(10L, 5L);
        verify(npcRepository).delete(existing);
        verify(fileCleaner).scheduleCleanup(Set.of(IMG), Set.of());
    }

    @Test
    @DisplayName("LUT-732 자동 말풍선: 등록 때 비우면 꺼짐·10초, 넘기면 그 값 · 수정 때 비우면 그대로(구 어드민 호환)")
    void autoBubble() throws IOException {
        when(floorRepository.findById(10L)).thenReturn(Optional.of(floor1));
        when(maskChecker.isInsideImage(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(npcRepository.save(any(TowerNpc.class))).thenAnswer(inv -> inv.getArgument(0));

        TowerNpcAdminResponse plain = service.create(10L, npcRequest(IMG, 1, 1, "안녕"));
        assertThat(plain.autoBubble()).isFalse();
        assertThat(plain.bubbleIntervalSeconds()).isEqualTo(10);

        TowerNpcAdminRequest base = npcRequest(IMG, 1, 1, "안녕");
        TowerNpcAdminRequest withBubble = withAutoBubble(base, true, 8);
        TowerNpcAdminResponse bubbly = service.create(10L, withBubble);
        assertThat(bubbly.autoBubble()).isTrue();
        assertThat(bubbly.bubbleIntervalSeconds()).isEqualTo(8);

        TowerNpc existing = npc(5L, 10L, true);
        existing.updateAutoBubble(true, 15);
        when(npcRepository.findByIdAndFloorId(5L, 10L)).thenReturn(Optional.of(existing));
        service.update(10L, 5L, base);
        assertThat(existing.isAutoBubble()).isTrue();
        assertThat(existing.getBubbleIntervalSeconds()).isEqualTo(15);
        service.update(10L, 5L, withAutoBubble(base, false, null));
        assertThat(existing.isAutoBubble()).isFalse();
        assertThat(existing.getBubbleIntervalSeconds()).isEqualTo(15);
        assertThat(TowerNpcView.from(existing).bubbleIntervalSeconds()).isEqualTo(15);
        assertThat(TowerNpcView.from(existing).autoBubble()).isFalse();
    }

    private static TowerNpcAdminRequest withAutoBubble(
            TowerNpcAdminRequest b, Boolean enabled, Integer interval) {
        return new TowerNpcAdminRequest(
                b.name(),
                b.nameEn(),
                b.nameJa(),
                b.nameAr(),
                b.profileImageUrl(),
                b.statusText(),
                b.statusTextEn(),
                b.statusTextJa(),
                b.statusTextAr(),
                b.seatX(),
                b.seatY(),
                b.lines(),
                b.isPublic(),
                b.sortOrder(),
                enabled,
                interval,
                b.card(),
                b.form(),
                b.motion());
    }

    private static TowerNpcAdminRequest withCard(TowerNpcAdminRequest b, TowerNpcCardDto card) {
        return new TowerNpcAdminRequest(
                b.name(),
                b.nameEn(),
                b.nameJa(),
                b.nameAr(),
                b.profileImageUrl(),
                b.statusText(),
                b.statusTextEn(),
                b.statusTextJa(),
                b.statusTextAr(),
                b.seatX(),
                b.seatY(),
                b.lines(),
                b.isPublic(),
                b.sortOrder(),
                b.autoBubble(),
                b.bubbleIntervalSeconds(),
                card,
                b.form(),
                b.motion());
    }

    /** LUT-760 */
    private static TowerNpcAdminRequest withForm(TowerNpcAdminRequest b, TowerNpcFormDto form) {
        return new TowerNpcAdminRequest(
                b.name(),
                b.nameEn(),
                b.nameJa(),
                b.nameAr(),
                b.profileImageUrl(),
                b.statusText(),
                b.statusTextEn(),
                b.statusTextJa(),
                b.statusTextAr(),
                b.seatX(),
                b.seatY(),
                b.lines(),
                b.isPublic(),
                b.sortOrder(),
                b.autoBubble(),
                b.bubbleIntervalSeconds(),
                b.card(),
                form,
                b.motion());
    }

    /** LUT-864 */
    private static TowerNpcAdminRequest withMotion(
            TowerNpcAdminRequest b, TowerNpcMotionDto motion) {
        return new TowerNpcAdminRequest(
                b.name(),
                b.nameEn(),
                b.nameJa(),
                b.nameAr(),
                b.profileImageUrl(),
                b.statusText(),
                b.statusTextEn(),
                b.statusTextJa(),
                b.statusTextAr(),
                b.seatX(),
                b.seatY(),
                b.lines(),
                b.isPublic(),
                b.sortOrder(),
                b.autoBubble(),
                b.bubbleIntervalSeconds(),
                b.card(),
                b.form(),
                motion);
    }

    private static final String SHEET = "https://cdn.test/tower/npc-motion/cat.webp";

    /** 256px 칸 16장(4×4) — 탑지기 고양이 idle 과 같은 모양 */
    private static List<TowerNpcMotionFrameDto> frames16() {
        List<TowerNpcMotionFrameDto> frames = new java.util.ArrayList<>();
        for (int i = 0; i < 16; i++) {
            frames.add(new TowerNpcMotionFrameDto((i % 4) * 256, (i / 4) * 256, 256, 256, 128));
        }
        return frames;
    }

    private static TowerNpcMotionDto motion(
            String sheet,
            List<TowerNpcMotionFrameDto> frames,
            Integer footX,
            Integer footY,
            Integer contentTop) {
        return new TowerNpcMotionDto(
                sheet, frames, footX, footY, contentTop, null, null, null, null);
    }

    @Test
    @DisplayName("LUT-864 움직이는 그림: 등록 — 비운 키·뒤집기·재생 방식·쉬는 시간은 64·false·OCCASIONAL·6, 응답·스냅샷에 같은 값")
    void motionCreate() throws IOException {
        when(floorRepository.findById(10L)).thenReturn(Optional.of(floor1));
        when(maskChecker.isInsideImage(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(npcRepository.save(any(TowerNpc.class))).thenAnswer(inv -> inv.getArgument(0));
        when(imageStorage.isOwned(SHEET)).thenReturn(true);

        TowerNpcAdminResponse res =
                service.create(
                        10L,
                        withMotion(
                                npcRequest(IMG, 1, 1, "안녕"),
                                motion(" " + SHEET + " ", frames16(), 116, 243, 55)));

        ArgumentCaptor<TowerNpc> saved = ArgumentCaptor.forClass(TowerNpc.class);
        verify(npcRepository).save(saved.capture());
        assertThat(saved.getValue().getMotion().getSheetUrl()).isEqualTo(SHEET);
        assertThat(saved.getValue().getMotion().getFrames())
                .startsWith("[{\"x\":0,\"y\":0,\"w\":256,\"h\":256,\"duration_ms\":128}");
        TowerNpcMotionDto out = res.motion();
        assertThat(out.sheetUrl()).isEqualTo(SHEET);
        assertThat(out.frames()).hasSize(16).containsExactlyElementsOf(frames16());
        assertThat(out.footX()).isEqualTo(116);
        assertThat(out.footY()).isEqualTo(243);
        assertThat(out.contentTop()).isEqualTo(55);
        assertThat(out.height()).isEqualTo(TowerNpcMotionDto.DEFAULT_HEIGHT).isEqualTo(64);
        assertThat(out.flip()).isFalse();
        assertThat(out.playMode()).isEqualTo(TowerNpcMotionPlayMode.OCCASIONAL);
        assertThat(out.intervalSeconds()).isEqualTo(6);
        assertThat(TowerNpcView.from(saved.getValue()).motion()).isEqualTo(out);

        TowerNpcAdminResponse loop =
                service.create(
                        10L,
                        withMotion(
                                npcRequest(IMG, 1, 1, "안녕"),
                                new TowerNpcMotionDto(
                                        SHEET,
                                        frames16().subList(0, 1),
                                        0,
                                        256,
                                        0,
                                        200,
                                        true,
                                        TowerNpcMotionPlayMode.LOOP,
                                        60)));
        assertThat(loop.motion())
                .extracting(
                        TowerNpcMotionDto::height,
                        TowerNpcMotionDto::flip,
                        TowerNpcMotionDto::playMode,
                        TowerNpcMotionDto::intervalSeconds)
                .containsExactly(200, true, TowerNpcMotionPlayMode.LOOP, 60);

        // 움직이는 그림 없이 등록 = null, 시트 주소가 빈 묶음도 없음
        assertThat(service.create(10L, npcRequest(IMG, 1, 1, "안녕")).motion()).isNull();
        assertThat(
                        service.create(
                                        10L,
                                        withMotion(
                                                npcRequest(IMG, 1, 1, "안녕"),
                                                motion("", null, null, null, null)))
                                .motion())
                .isNull();
    }

    @Test
    @DisplayName("LUT-864 움직이는 그림: 장 없음·칸이 시트 밖·발/그림 맨 위가 칸 밖은 400 150416, 업로드하지 않은 시트는 400 150417")
    void motionInvalid() throws IOException {
        when(floorRepository.findById(10L)).thenReturn(Optional.of(floor1));
        when(maskChecker.isInsideImage(anyString(), anyInt(), anyInt())).thenReturn(true);
        List<TowerNpcMotionFrameDto> outside =
                List.of(new TowerNpcMotionFrameDto(3900, 0, 256, 256, 128));
        List<TowerNpcMotionFrameDto> below =
                List.of(new TowerNpcMotionFrameDto(0, 3900, 256, 256, 128));
        for (TowerNpcMotionDto bad :
                List.of(
                        motion(SHEET, null, 116, 243, 55),
                        motion(SHEET, List.of(), 116, 243, 55),
                        motion(SHEET, outside, 116, 243, 55),
                        motion(SHEET, below, 116, 243, 55),
                        motion(SHEET, frames16(), null, 243, 55),
                        motion(SHEET, frames16(), 116, null, 55),
                        motion(SHEET, frames16(), 116, 243, null),
                        motion(SHEET, frames16(), 257, 243, 55),
                        motion(SHEET, frames16(), 116, 257, 55),
                        motion(SHEET, frames16(), 116, 55, 55),
                        motion(SHEET, frames16(), 116, 54, 55))) {
            assertThatThrownBy(
                            () -> service.create(10L, withMotion(npcRequest(IMG, 1, 1, "안녕"), bad)))
                    .isInstanceOf(TowerNpcException.class)
                    .extracting("code")
                    .isEqualTo(TowerNpcException.MOTION_INVALID);
        }
        when(imageStorage.isOwned("https://other.cdn/cat.webp")).thenReturn(false);
        assertThatThrownBy(
                        () ->
                                service.create(
                                        10L,
                                        withMotion(
                                                npcRequest(IMG, 1, 1, "안녕"),
                                                motion(
                                                        "https://other.cdn/cat.webp",
                                                        frames16(),
                                                        116,
                                                        243,
                                                        55))))
                .isInstanceOf(TowerNpcException.class)
                .extracting("code")
                .isEqualTo(TowerNpcException.MOTION_SHEET_NOT_UPLOADED);
        verify(npcRepository, never()).save(any(TowerNpc.class));
    }

    @Test
    @DisplayName("LUT-864 움직이는 그림: 수정 — 없으면 그대로(구 어드민), 바꾸면 옛 시트 정리, 시트 주소를 비우면 지우고 정리 · 삭제도 시트 정리")
    void motionUpdateAndDelete() throws IOException {
        String newSheet = "https://cdn.test/tower/npc-motion/cat2.webp";
        TowerNpc existing = npc(5L, 10L, true);
        String image = existing.getProfileImageUrl();
        when(floorRepository.findById(10L)).thenReturn(Optional.of(floor1));
        when(npcRepository.findByIdAndFloorId(5L, 10L)).thenReturn(Optional.of(existing));
        when(maskChecker.isInsideImage(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(imageStorage.isOwned(anyString())).thenReturn(true);
        TowerNpcAdminRequest base = npcRequest(image, 1, 1, "안녕");

        service.update(10L, 5L, withMotion(base, motion(SHEET, frames16(), 116, 243, 55)));
        assertThat(existing.getMotion().getSheetUrl()).isEqualTo(SHEET);

        service.update(10L, 5L, base);
        assertThat(existing.getMotion().getSheetUrl()).isEqualTo(SHEET);
        verify(fileCleaner).scheduleCleanup(Set.of(image), Set.of(image, SHEET));
        verify(fileCleaner).scheduleCleanup(Set.of(image, SHEET), Set.of(image, SHEET));

        service.update(10L, 5L, withMotion(base, motion(newSheet, frames16(), 116, 243, 55)));
        assertThat(existing.getMotion().getSheetUrl()).isEqualTo(newSheet);
        verify(fileCleaner).scheduleCleanup(Set.of(image, SHEET), Set.of(image, newSheet));

        service.update(10L, 5L, withMotion(base, motion(" ", null, null, null, null)));
        assertThat(existing.getMotion()).isNull();
        assertThat(TowerNpcView.from(existing).motion()).isNull();
        verify(fileCleaner).scheduleCleanup(Set.of(image, newSheet), Set.of(image));

        existing.updateMotion(motion(SHEET, frames16(), 116, 243, 55).toEntityOrNull());
        service.delete(10L, 5L);
        verify(fileCleaner).scheduleCleanup(Set.of(image, SHEET), Set.of());
    }

    private static final String CARD_IMG = "https://cdn.test/tower/npc/card.png";

    private static TowerNpcCardDto card(
            String image, TowerNpcCardLinkType type, Long targetId, String path) {
        return new TowerNpcCardDto(
                image,
                type == null ? null : new TowerNpcCardLinkDto(type, targetId, path),
                " 상점 가기 ",
                "Go to shop",
                null,
                " ",
                null,
                null);
    }

    @Test
    @DisplayName(
            "LUT-733 창 링크 저장 검증: 판매 중 아이템·있는 이벤트만(150405) · 대상 ID 없음·앱 경로 아님 150406 · 기간 역전 150407")
    void cardValidation() throws IOException {
        when(floorRepository.findById(10L)).thenReturn(Optional.of(floor1));
        when(maskChecker.isInsideImage(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(npcRepository.save(any(TowerNpc.class))).thenAnswer(inv -> inv.getArgument(0));
        TowerNpcAdminRequest base = npcRequest(IMG, 1, 1, "안녕");
        when(gamificationQueryFacade.findOnSaleShopItemIds(List.of(15L))).thenReturn(Set.of(15L));
        when(gamificationQueryFacade.findOnSaleShopItemIds(List.of(16L))).thenReturn(Set.of());
        when(gamificationQueryFacade.findExistingEventIds(List.of(3L))).thenReturn(Set.of(3L));
        when(gamificationQueryFacade.findExistingEventIds(List.of(4L))).thenReturn(Set.of());

        TowerNpcAdminResponse shop =
                service.create(
                        10L,
                        withCard(base, card(CARD_IMG, TowerNpcCardLinkType.SHOP_ITEM, 15L, "/x")));
        assertThat(shop.card().imageUrl()).isEqualTo(CARD_IMG);
        assertThat(shop.card().link().type()).isEqualTo(TowerNpcCardLinkType.SHOP_ITEM);
        assertThat(shop.card().link().targetId()).isEqualTo(15L);
        assertThat(shop.card().link().path()).isNull(); // 종류에 맞지 않는 값은 버린다
        assertThat(shop.card().linkLabel()).isEqualTo("상점 가기");
        assertThat(shop.card().linkLabelAr()).isNull();
        assertThat(
                        service.create(
                                        10L,
                                        withCard(
                                                base,
                                                card(null, TowerNpcCardLinkType.EVENT, 3L, null)))
                                .card()
                                .imageUrl())
                .isNull();
        assertThat(
                        service.create(
                                        10L,
                                        withCard(
                                                base,
                                                card(
                                                        null,
                                                        TowerNpcCardLinkType.PATH,
                                                        9L,
                                                        " /shop/subscription ")))
                                .card()
                                .link())
                .satisfies(
                        link -> {
                            assertThat(link.path()).isEqualTo("/shop/subscription");
                            assertThat(link.targetId()).isNull();
                        });
        assertThat(
                        service.create(
                                        10L,
                                        withCard(
                                                base,
                                                card(
                                                        null,
                                                        TowerNpcCardLinkType.SUBSCRIPTION,
                                                        null,
                                                        null)))
                                .card()
                                .link()
                                .type())
                .isEqualTo(TowerNpcCardLinkType.SUBSCRIPTION);
        // 이미지만(링크 없음) — 버튼 문구는 버린다
        TowerNpcAdminResponse imageOnly =
                service.create(10L, withCard(base, card(CARD_IMG, null, null, null)));
        assertThat(imageOnly.card().link()).isNull();
        assertThat(imageOnly.card().linkLabel()).isNull();
        // 이미지도 링크도 없으면 창 묶음 없음
        assertThat(service.create(10L, withCard(base, card(" ", null, null, null))).card())
                .isNull();

        assertCode(
                withCard(base, card(null, TowerNpcCardLinkType.SHOP_ITEM, 16L, null)),
                TowerNpcException.CARD_LINK_TARGET_MISSING);
        assertCode(
                withCard(base, card(null, TowerNpcCardLinkType.EVENT, 4L, null)),
                TowerNpcException.CARD_LINK_TARGET_MISSING);
        assertCode(
                withCard(base, card(null, TowerNpcCardLinkType.SHOP_ITEM, null, null)),
                TowerNpcException.CARD_LINK_INVALID);
        assertCode(
                withCard(base, card(null, TowerNpcCardLinkType.EVENT, 0L, null)),
                TowerNpcException.CARD_LINK_INVALID);
        for (String bad : new String[] {"shop", "//evil.com", "https://x", "/a b", null}) {
            assertCode(
                    withCard(base, card(null, TowerNpcCardLinkType.PATH, null, bad)),
                    TowerNpcException.CARD_LINK_INVALID);
        }
        TowerNpcCardDto reversed =
                new TowerNpcCardDto(
                        CARD_IMG,
                        null,
                        null,
                        null,
                        null,
                        null,
                        java.time.LocalDate.of(2026, 12, 2),
                        java.time.LocalDate.of(2026, 12, 1));
        assertCode(withCard(base, reversed), TowerNpcException.CARD_PERIOD_INVALID);
    }

    private void assertCode(TowerNpcAdminRequest request, String code) {
        assertThatThrownBy(() -> service.create(10L, request))
                .isInstanceOf(TowerNpcException.class)
                .extracting("code")
                .isEqualTo(code);
    }

    @Test
    @DisplayName(
            "LUT-733 수정: card 없음 = 그대로(구 어드민) · 있으면 통째로 바꿈 · 빈 묶음 = 지움 · 바뀐 창 이미지는 정리 · 삭제도 창 이미지"
                    + " 정리")
    void cardUpdate() throws IOException {
        TowerNpc existing = npc(5L, 10L, true);
        existing.updateCard(
                io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpcCard.builder()
                        .imageUrl(CARD_IMG)
                        .linkType(TowerNpcCardLinkType.SUBSCRIPTION)
                        .build());
        String profile = existing.getProfileImageUrl();
        when(floorRepository.findById(10L)).thenReturn(Optional.of(floor1));
        when(npcRepository.findByIdAndFloorId(5L, 10L)).thenReturn(Optional.of(existing));
        when(maskChecker.isInsideImage(anyString(), anyInt(), anyInt())).thenReturn(true);
        TowerNpcAdminRequest base = npcRequest(IMG, 1, 1, "안녕");

        service.update(10L, 5L, base);
        assertThat(existing.getCard().getImageUrl()).isEqualTo(CARD_IMG);
        verify(fileCleaner).scheduleCleanup(Set.of(profile, CARD_IMG), Set.of(IMG, CARD_IMG));

        String newCard = "https://cdn.test/tower/npc/card2.png";
        service.update(10L, 5L, withCard(base, card(newCard, null, null, null)));
        assertThat(existing.getCard().getImageUrl()).isEqualTo(newCard);
        assertThat(existing.getCard().hasLink()).isFalse();
        verify(fileCleaner).scheduleCleanup(Set.of(IMG, CARD_IMG), Set.of(IMG, newCard));

        service.update(10L, 5L, withCard(base, card(null, null, null, null)));
        assertThat(existing.getCard()).isNull();
        verify(fileCleaner).scheduleCleanup(Set.of(IMG, newCard), Set.of(IMG));

        existing.updateCard(
                io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpcCard.builder()
                        .imageUrl(CARD_IMG)
                        .build());
        service.delete(10L, 5L);
        verify(fileCleaner).scheduleCleanup(Set.of(IMG, CARD_IMG), Set.of());
    }

    // ---------- LUT-760: 입력 받기 설정 ----------

    /** 요청 DTO — 나머지는 고정, 자주 바꾸는 값만 인자 */
    private static TowerNpcFormDto form(
            boolean enabled,
            String labelJa,
            boolean email,
            String consentKo,
            TowerNpcFormDisplayMode mode,
            String mask) {
        return new TowerNpcFormDto(
                enabled,
                TowerNpcFormBodyType.INPUT,
                "한마디",
                null,
                null,
                null,
                100,
                "저장",
                "Save",
                labelJa,
                "حفظ",
                false,
                email,
                consentKo,
                email ? "I agree" : null,
                email ? "同意" : null,
                email ? "أوافق" : null,
                mode,
                mask);
    }

    private static final String TREE = "https://cdn.test/tower/mask/tree.png";
    private static final String SKY = "https://cdn.test/tower/mask/sky.png";

    @Test
    @DisplayName("LUT-760 등록: form 이 있으면 NPC 저장 뒤 설정도 저장(이메일 받기면 로그인 강제), 없으면 설정 없음")
    void createWithForm() throws IOException {
        when(floorRepository.findById(10L)).thenReturn(Optional.of(floor1));
        when(maskChecker.isInsideImage(floor1.getBackgroundImageUrl(), 300, 400)).thenReturn(true);
        when(maskChecker.sameAspectRatio(TREE, floor1.getBackgroundImageUrl())).thenReturn(true);
        when(npcRepository.save(any(TowerNpc.class)))
                .thenAnswer(
                        inv -> {
                            TowerNpc n = inv.getArgument(0);
                            ReflectionTestUtils.setField(n, "id", 7L);
                            return n;
                        });
        when(formRepository.save(any(TowerNpcForm.class))).thenAnswer(inv -> inv.getArgument(0));

        TowerNpcAdminResponse res =
                service.create(
                        10L,
                        withForm(
                                npcRequest(IMG, 300, 400, "안녕"),
                                TowerTestFixtures.npcFormDto(true)));

        ArgumentCaptor<TowerNpcForm> saved = ArgumentCaptor.forClass(TowerNpcForm.class);
        verify(formRepository).save(saved.capture());
        assertThat(saved.getValue().getNpcId()).isEqualTo(7L);
        assertThat(saved.getValue().isEnabled()).isTrue();
        assertThat(saved.getValue().isEmailEnabled()).isTrue();
        assertThat(saved.getValue().isLoginRequired()).isTrue(); // 요청은 false 였지만 이메일 받기라 강제
        assertThat(saved.getValue().getBodyType()).isEqualTo(TowerNpcFormBodyType.TEXTAREA);
        assertThat(saved.getValue().getMaxLength()).isEqualTo(200);
        assertThat(res.form()).isNotNull();
        assertThat(res.form().loginRequired()).isTrue();
        assertThat(res.form().displayMode()).isEqualTo(TowerNpcFormDisplayMode.FLOAT);

        assertThat(service.create(10L, npcRequest(IMG, 300, 400, "안녕")).form()).isNull();
        verify(formRepository, times(1)).save(any());
    }

    @Test
    @DisplayName(
            "LUT-760 설정 검증: 켜진 설정의 버튼 문구·동의 문구·공개 마스크 누락 150408, 마스크 비율 불일치 150409, 못 읽음 150109 ·"
                    + " 꺼진 설정·NONE 은 마스크 검사 없음")
    void formValidation() throws IOException {
        when(floorRepository.findById(10L)).thenReturn(Optional.of(floor1));
        when(maskChecker.isInsideImage(floor1.getBackgroundImageUrl(), 1, 1)).thenReturn(true);
        TowerNpcAdminRequest base = npcRequest(IMG, 1, 1, "안녕");

        for (TowerNpcFormDto invalid :
                List.of(
                        form(true, " ", false, null, TowerNpcFormDisplayMode.NONE, null),
                        form(true, "保存", true, null, TowerNpcFormDisplayMode.NONE, null),
                        form(true, "保存", false, null, TowerNpcFormDisplayMode.TAG, null))) {
            assertThatThrownBy(() -> service.create(10L, withForm(base, invalid)))
                    .isInstanceOf(TowerNpcException.class)
                    .extracting("code")
                    .isEqualTo(TowerNpcException.FORM_INVALID);
        }

        when(maskChecker.sameAspectRatio(TREE, floor1.getBackgroundImageUrl())).thenReturn(false);
        assertThatThrownBy(
                        () ->
                                service.create(
                                        10L,
                                        withForm(
                                                base,
                                                form(
                                                        true,
                                                        "保存",
                                                        false,
                                                        null,
                                                        TowerNpcFormDisplayMode.TAG,
                                                        TREE))))
                .isInstanceOf(TowerNpcException.class)
                .extracting("code")
                .isEqualTo(TowerNpcException.FORM_MASK_ASPECT_MISMATCH);

        when(maskChecker.sameAspectRatio(SKY, floor1.getBackgroundImageUrl()))
                .thenThrow(new IOException("timeout"));
        assertThatThrownBy(
                        () ->
                                service.create(
                                        10L,
                                        withForm(
                                                base,
                                                form(
                                                        true,
                                                        "保存",
                                                        false,
                                                        null,
                                                        TowerNpcFormDisplayMode.FLOAT,
                                                        SKY))))
                .extracting("code")
                .isEqualTo("150109");
        verify(npcRepository, never()).save(any());

        // 꺼진 설정은 값만 저장, NONE 은 마스크를 보지 않는다
        when(npcRepository.save(any(TowerNpc.class))).thenAnswer(inv -> inv.getArgument(0));
        when(formRepository.save(any(TowerNpcForm.class))).thenAnswer(inv -> inv.getArgument(0));
        service.create(
                10L,
                withForm(base, form(false, " ", true, null, TowerNpcFormDisplayMode.TAG, null)));
        service.create(
                10L,
                withForm(base, form(true, "保存", false, null, TowerNpcFormDisplayMode.NONE, null)));
        verify(formRepository, times(2)).save(any());
        verify(maskChecker, times(2)).sameAspectRatio(any(), any());
    }

    @Test
    @DisplayName("LUT-760 수정: form 없으면 그대로, 있으면 기존 행 갱신(없으면 생성), 바뀐 공개 마스크는 정리 후보·새 마스크는 유지 목록")
    void updateForm() throws IOException {
        TowerNpc existing = TowerTestFixtures.npc(5L, 10L, true);
        when(floorRepository.findById(10L)).thenReturn(Optional.of(floor1));
        when(npcRepository.findByIdAndFloorId(5L, 10L)).thenReturn(Optional.of(existing));
        when(maskChecker.isInsideImage(floor1.getBackgroundImageUrl(), 300, 400)).thenReturn(true);
        TowerNpcForm form = TowerTestFixtures.npcForm(5L);
        when(formRepository.findByNpcId(5L)).thenReturn(Optional.of(form));
        TowerNpcAdminRequest base = npcRequest(IMG, 300, 400, "안녕");

        TowerNpcAdminResponse kept = service.update(10L, 5L, base);
        assertThat(kept.form()).isNotNull();
        assertThat(kept.form().displayMaskUrl()).isEqualTo(TREE);
        verify(formRepository, never()).save(any());

        when(maskChecker.sameAspectRatio(SKY, floor1.getBackgroundImageUrl())).thenReturn(true);
        TowerNpcAdminResponse updated =
                service.update(
                        10L,
                        5L,
                        withForm(
                                base,
                                form(true, "保存", false, null, TowerNpcFormDisplayMode.FLOAT, SKY)));
        assertThat(form.getDisplayMode()).isEqualTo(TowerNpcFormDisplayMode.FLOAT);
        assertThat(updated.form().displayMaskUrl()).isEqualTo(SKY);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<String>> previous = ArgumentCaptor.forClass(Set.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<String>> stillUsed = ArgumentCaptor.forClass(Set.class);
        verify(fileCleaner, times(2)).scheduleCleanup(previous.capture(), stillUsed.capture());
        assertThat(previous.getAllValues().get(1)).contains(TREE);
        assertThat(stillUsed.getAllValues().get(1)).contains(SKY).doesNotContain(TREE);

        TowerNpc other = TowerTestFixtures.npc(6L, 10L, true);
        when(npcRepository.findByIdAndFloorId(6L, 10L)).thenReturn(Optional.of(other));
        when(formRepository.findByNpcId(6L)).thenReturn(Optional.empty());
        when(formRepository.save(any(TowerNpcForm.class))).thenAnswer(inv -> inv.getArgument(0));
        TowerNpcAdminResponse created =
                service.update(
                        10L,
                        6L,
                        withForm(
                                base,
                                form(true, "保存", false, null, TowerNpcFormDisplayMode.NONE, null)));
        assertThat(created.form()).isNotNull();
        assertThat(created.form().formEnabled()).isTrue();
        verify(formRepository).save(any());
    }

    @Test
    @DisplayName("LUT-760 삭제: 공개 마스크도 정리 후보 · 목록은 NPC 마다 설정을 붙인다(없으면 null)")
    void deleteAndListWithForm() {
        TowerNpc existing = TowerTestFixtures.npc(5L, 10L, true);
        when(npcRepository.findByIdAndFloorId(5L, 10L)).thenReturn(Optional.of(existing));
        when(formRepository.findByNpcId(5L)).thenReturn(Optional.of(TowerTestFixtures.npcForm(5L)));
        service.delete(10L, 5L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<String>> previous = ArgumentCaptor.forClass(Set.class);
        verify(fileCleaner).scheduleCleanup(previous.capture(), eq(Set.of()));
        assertThat(previous.getValue()).contains(existing.getProfileImageUrl(), TREE);

        when(floorRepository.findById(10L)).thenReturn(Optional.of(floor1));
        when(npcRepository.findAllByFloorIdOrderBySortOrderAscIdAsc(10L))
                .thenReturn(
                        List.of(
                                TowerTestFixtures.npc(1L, 10L, true),
                                TowerTestFixtures.npc(2L, 10L, true)));
        when(formRepository.findAllByNpcIdIn(List.of(1L, 2L)))
                .thenReturn(List.of(TowerTestFixtures.npcForm(2L)));
        List<TowerNpcAdminResponse> list = service.list(10L);
        assertThat(list.get(0).form()).isNull();
        assertThat(list.get(1).form()).isNotNull();
        assertThat(list.get(1).form().submitLabel()).isEqualTo("저장");
    }
}
