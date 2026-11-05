package io.pinkspider.leveluptogethermvp.towerservice.application;

import static io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.floor;
import static io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerFloorException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorAdminRequest;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorAdminResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorEffectLayerDto;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorLightDto;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloorLight;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerEffectKind;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TowerFloorAdminServiceTest {

    @Mock private TowerFloorRepository towerFloorRepository;
    @Mock private TowerFloorRuleValidator ruleValidator;

    @Mock
    private io.pinkspider.leveluptogethermvp.towerservice.presence.TowerFloorMaskCache maskCache;

    @Mock
    private io.pinkspider.leveluptogethermvp.towerservice.application.image.TowerImageStorageService
            imageStorage;

    @Mock
    private io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerNpcRepository
            towerNpcRepository;

    @Mock
    private io.pinkspider.leveluptogethermvp.towerservice.infrastructure
                    .TowerFloorPixelArtRepository
            pixelArtRepository;

    @Mock
    private io.pinkspider.leveluptogethermvp.towerservice.application.pixel.TowerPixelArtService
            pixelArtService;

    private TowerFloorAdminService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        // LUT-693: 파일 정리는 공용 부품(층·NPC 참조 둘 다 확인)
        service =
                new TowerFloorAdminService(
                        towerFloorRepository,
                        ruleValidator,
                        maskCache,
                        new TowerStoredFileCleaner(
                                imageStorage,
                                towerFloorRepository,
                                towerNpcRepository,
                                pixelArtRepository),
                        towerNpcRepository,
                        pixelArtService);
    }

    @Test
    @DisplayName("등록: 기존 층 + 새 층으로 규칙을 검증한 뒤 저장한다")
    void create() {
        TowerFloor f1 = floor(1L, 1, null, true, true);
        when(towerFloorRepository.findAllByOrderByFloorNumberAsc()).thenReturn(List.of(f1));
        when(towerFloorRepository.save(any(TowerFloor.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TowerFloorAdminResponse res = service.create(request(2, 5, false, true));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TowerFloor>> captor = ArgumentCaptor.forClass(List.class);
        verify(ruleValidator).validate(captor.capture(), any(TowerFloor.class));
        assertThat(captor.getValue()).hasSize(2);
        assertThat(res.floorNumber()).isEqualTo(2);
        assertThat(res.entryLevel()).isEqualTo(5);
        assertThat(res.instanceCapacity()).isEqualTo(30);
    }

    @Test
    @DisplayName("등록: 규칙 위반이면 저장하지 않는다")
    void createRejected() {
        when(towerFloorRepository.findAllByOrderByFloorNumberAsc()).thenReturn(List.of());
        doThrow(TowerFloorException.floor1EntryLevel())
                .when(ruleValidator)
                .validate(anyList(), any(TowerFloor.class));

        assertThatThrownBy(() -> service.create(request(1, 3, false, true)))
                .isInstanceOf(TowerFloorException.class);
        verify(towerFloorRepository, never()).save(any());
    }

    @Test
    @DisplayName("수정: 대상 층의 값을 바꾼 상태로 전체 목록을 검증한다 (즉시 반영)")
    void update() {
        TowerFloor f1 = floor(1L, 1, null, true, true);
        TowerFloor f2 = floor(2L, 2, 5, false, true);
        when(towerFloorRepository.findById(2L)).thenReturn(Optional.of(f2));
        when(towerFloorRepository.findAllByOrderByFloorNumberAsc()).thenReturn(List.of(f1, f2));

        TowerFloorAdminResponse res = service.update(2L, request(2, 7, false, false));

        verify(ruleValidator).validate(List.of(f1, f2), f2);
        assertThat(f2.getEntryLevel()).isEqualTo(7);
        assertThat(f2.isPublicFloor()).isFalse();
        assertThat(res.entryLevel()).isEqualTo(7);
        verify(maskCache).evict(f2); // LUT-631: 마스크 캐시 비움
    }

    @Test
    @DisplayName("삭제: 남는 층끼리 규칙을 검증한 뒤 지운다 (target 없음)")
    void delete() {
        TowerFloor f1 = floor(1L, 1, null, true, true);
        TowerFloor f2 = floor(2L, 2, 5, false, true);
        when(towerFloorRepository.findById(2L)).thenReturn(Optional.of(f2));
        when(towerFloorRepository.findAllByOrderByFloorNumberAsc()).thenReturn(List.of(f1, f2));

        service.delete(2L);

        verify(ruleValidator).validate(List.of(f1), null);
        verify(towerFloorRepository).delete(f2);
        verify(maskCache).evict(f2);
    }

    @Test
    @DisplayName("없는 층 → 150101")
    void notFound() {
        when(towerFloorRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(99L))
                .isInstanceOf(TowerFloorException.class)
                .extracting("code")
                .isEqualTo(TowerFloorException.NOT_FOUND);
        verify(ruleValidator, never()).validate(anyList(), isNull());
    }

    @Test
    @DisplayName("LUT-635 수정: 바뀐 마스크 URL 이 우리 저장소 것이고 다른 층이 안 쓰면 지운다, 배경(그대로)은 유지")
    void updateCleansReplacedImage() {
        TowerFloor f1 = floor(1L, 1, null, true, true);
        String oldMask = f1.getFloorMaskUrl();
        when(towerFloorRepository.findById(1L)).thenReturn(java.util.Optional.of(f1));
        when(towerFloorRepository.findAllByOrderByFloorNumberAsc()).thenReturn(List.of(f1));
        when(imageStorage.isOwned(oldMask)).thenReturn(true);
        when(towerFloorRepository.countByBackgroundImageUrlOrFloorMaskUrlOrBgmUrl(
                        oldMask, oldMask, oldMask))
                .thenReturn(0L);

        TowerFloorAdminRequest req = request(1, null, true, true);
        TowerFloorAdminRequest changed =
                new TowerFloorAdminRequest(
                        req.floorNumber(),
                        req.name(),
                        req.nameEn(),
                        req.nameJa(),
                        req.nameAr(),
                        req.entryLevel(),
                        req.guestAllowed(),
                        f1.getBackgroundImageUrl(),
                        "https://cdn/tower/mask/new.png",
                        req.entryPointX(),
                        req.entryPointY(),
                        req.bgmUrl(),
                        req.instanceCapacity(),
                        req.isPublic(),
                        null,
                        null,
                        null);
        service.update(1L, changed);

        verify(imageStorage).delete(oldMask);
        verify(imageStorage, org.mockito.Mockito.never()).delete(f1.getBackgroundImageUrl());
    }

    @Test
    @DisplayName("LUT-635 삭제: 두 이미지 중 다른 층이 쓰는 것·외부 URL 은 남기고 나머지만 지운다")
    void deleteCleansImages() {
        TowerFloor f1 = floor(1L, 1, null, true, true);
        TowerFloor f2 = floor(2L, 2, 5, false, true);
        when(towerFloorRepository.findById(2L)).thenReturn(java.util.Optional.of(f2));
        when(towerFloorRepository.findAllByOrderByFloorNumberAsc()).thenReturn(List.of(f1, f2));
        when(imageStorage.isOwned(f2.getBackgroundImageUrl())).thenReturn(true);
        when(imageStorage.isOwned(f2.getFloorMaskUrl())).thenReturn(false);
        when(towerFloorRepository.countByBackgroundImageUrlOrFloorMaskUrlOrBgmUrl(
                        f2.getBackgroundImageUrl(),
                        f2.getBackgroundImageUrl(),
                        f2.getBackgroundImageUrl()))
                .thenReturn(1L);

        service.delete(2L);

        verify(imageStorage, org.mockito.Mockito.never()).delete(any());
    }

    @Test
    @DisplayName("LUT-637 수정: 배경 음악을 바꾸면 옛 곡(우리 저장소·다른 층 미사용)을 커밋 뒤 지운다")
    void updateCleansReplacedBgm() {
        TowerFloor f1 = floor(1L, 1, null, true, true);
        String oldBgm = f1.getBgmUrl();
        when(towerFloorRepository.findById(1L)).thenReturn(java.util.Optional.of(f1));
        when(towerFloorRepository.findAllByOrderByFloorNumberAsc()).thenReturn(List.of(f1));
        when(imageStorage.isOwned(oldBgm)).thenReturn(true);
        when(towerFloorRepository.countByBackgroundImageUrlOrFloorMaskUrlOrBgmUrl(
                        oldBgm, oldBgm, oldBgm))
                .thenReturn(0L);

        TowerFloorAdminRequest req = request(1, null, true, true);
        TowerFloorAdminRequest changed =
                new TowerFloorAdminRequest(
                        req.floorNumber(),
                        req.name(),
                        req.nameEn(),
                        req.nameJa(),
                        req.nameAr(),
                        req.entryLevel(),
                        req.guestAllowed(),
                        f1.getBackgroundImageUrl(),
                        f1.getFloorMaskUrl(),
                        req.entryPointX(),
                        req.entryPointY(),
                        "https://cdn.test/tower/bgm-new.mp3",
                        req.instanceCapacity(),
                        req.isPublic(),
                        null,
                        null,
                        null);
        service.update(1L, changed);

        verify(imageStorage).delete(oldBgm);
    }

    @Test
    @DisplayName("LUT-693 삭제: 층의 NPC 이미지도 정리한다 — 다른 층 NPC 가 쓰는 이미지는 남긴다")
    void deleteCleansNpcImages() {
        TowerFloor f1 = floor(1L, 1, null, true, true);
        TowerFloor f2 = floor(2L, 2, 5, false, false);
        when(towerFloorRepository.findById(2L)).thenReturn(java.util.Optional.of(f2));
        when(towerFloorRepository.findAllByOrderByFloorNumberAsc()).thenReturn(List.of(f1, f2));
        var npcA =
                io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.npc(1L, 2L, true);
        var npcB =
                io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.npc(2L, 2L, true);
        when(towerNpcRepository.findAllByFloorId(2L)).thenReturn(List.of(npcA, npcB));
        when(imageStorage.isOwned(f2.getBackgroundImageUrl())).thenReturn(false);
        when(imageStorage.isOwned(f2.getFloorMaskUrl())).thenReturn(false);
        when(imageStorage.isOwned(npcA.getProfileImageUrl())).thenReturn(true);
        when(imageStorage.isOwned(npcB.getProfileImageUrl())).thenReturn(true);
        when(towerNpcRepository.countByProfileImageUrl(npcA.getProfileImageUrl())).thenReturn(0L);
        when(towerNpcRepository.countByProfileImageUrl(npcB.getProfileImageUrl())).thenReturn(1L);
        // LUT-733: 창 이미지도 정리 대상
        String cardImage = "https://cdn/tower/npc/card-a.png";
        npcA.updateCard(
                io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpcCard.builder()
                        .imageUrl(cardImage)
                        .build());
        npcB.updateCard(
                io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpcCard.builder()
                        .linkType(
                                io.pinkspider.leveluptogethermvp.towerservice.domain.enums
                                        .TowerNpcCardLinkType.SUBSCRIPTION)
                        .build());
        when(imageStorage.isOwned(cardImage)).thenReturn(true);
        when(towerNpcRepository.countByProfileImageUrl(cardImage)).thenReturn(0L);

        service.delete(2L);

        verify(imageStorage).delete(npcA.getProfileImageUrl());
        verify(imageStorage).delete(cardImage);
        verify(imageStorage, org.mockito.Mockito.never()).delete(npcB.getProfileImageUrl());
    }

    private static final String OLD_OCCLUSION = "https://cdn/tower/mask/occlusion-old.png";
    private static final String OLD_SNOW = "https://cdn/tower/mask/snow-old.png";

    private static TowerFloorLightDto light(int x) {
        return new TowerFloorLightDto(x, 20, 30, "#ffcc66", 80, 40, 50);
    }

    private static TowerFloorEffectLayerDto snow(String maskUrl) {
        return new TowerFloorEffectLayerDto(
                TowerEffectKind.SNOW, maskUrl, true, null, null, 100, 50, 1, 3, null);
    }

    /** 기존 요청에 LUT-725 필드만 바꿔 끼운다 */
    private static TowerFloorAdminRequest withEffects(
            TowerFloorAdminRequest r,
            String occlusion,
            List<TowerFloorLightDto> lights,
            List<TowerFloorEffectLayerDto> layers) {
        return new TowerFloorAdminRequest(
                r.floorNumber(),
                r.name(),
                r.nameEn(),
                r.nameJa(),
                r.nameAr(),
                r.entryLevel(),
                r.guestAllowed(),
                r.backgroundImageUrl(),
                r.floorMaskUrl(),
                r.entryPointX(),
                r.entryPointY(),
                r.bgmUrl(),
                r.instanceCapacity(),
                r.isPublic(),
                occlusion,
                lights,
                layers);
    }

    private static TowerFloor floorWithEffects(Long id, int number, boolean isPublic) {
        TowerFloor f = floor(id, number, null, true, isPublic);
        f.updateOcclusionMask(OLD_OCCLUSION);
        f.replaceLights(List.of(light(10).toEmbeddable()));
        f.replaceEffectLayers(List.of(snow(OLD_SNOW).toEmbeddable()));
        return f;
    }

    @Test
    @DisplayName("LUT-725 등록: 앞 가림·불빛 점·효과 레이어를 함께 저장한다")
    void createWithEffects() {
        when(towerFloorRepository.findAllByOrderByFloorNumberAsc()).thenReturn(List.of());
        when(towerFloorRepository.save(any(TowerFloor.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TowerFloorAdminResponse saved =
                service.create(
                        withEffects(
                                request(1, null, true, true),
                                "https://cdn/occlusion.png",
                                List.of(light(10), light(20)),
                                List.of(snow("https://cdn/snow.png"))));

        assertThat(saved.occlusionMaskUrl()).isEqualTo("https://cdn/occlusion.png");
        assertThat(saved.lights()).extracting(TowerFloorLightDto::x).containsExactly(10, 20);
        assertThat(saved.effectLayers())
                .extracting(TowerFloorEffectLayerDto::maskUrl)
                .containsExactly("https://cdn/snow.png");
    }

    @Test
    @DisplayName("LUT-725 수정: 효과 필드를 보내지 않으면(null) 그대로 둔다 — 구 어드민·패스스루 호환")
    void updateKeepsEffectsWhenOmitted() {
        TowerFloor f1 = floorWithEffects(1L, 1, true);
        when(towerFloorRepository.findById(1L)).thenReturn(java.util.Optional.of(f1));
        when(towerFloorRepository.findAllByOrderByFloorNumberAsc()).thenReturn(List.of(f1));

        TowerFloorAdminResponse updated = service.update(1L, request(1, null, true, true));

        assertThat(updated.occlusionMaskUrl()).isEqualTo(OLD_OCCLUSION);
        assertThat(updated.lights()).hasSize(1);
        assertThat(updated.effectLayers()).hasSize(1);
        verify(imageStorage, never()).delete(any());
    }

    @Test
    @DisplayName("LUT-725 수정: 빈 문자열·[] 이면 지우고, 안 쓰게 된 마스크(우리 저장소)는 커밋 뒤 지운다")
    void updateClearsEffects() {
        TowerFloor f1 = floorWithEffects(1L, 1, true);
        when(towerFloorRepository.findById(1L)).thenReturn(java.util.Optional.of(f1));
        when(towerFloorRepository.findAllByOrderByFloorNumberAsc()).thenReturn(List.of(f1));
        when(imageStorage.isOwned(OLD_OCCLUSION)).thenReturn(true);
        when(imageStorage.isOwned(OLD_SNOW)).thenReturn(true);

        TowerFloorAdminResponse updated =
                service.update(
                        1L, withEffects(request(1, null, true, true), "", List.of(), List.of()));

        assertThat(updated.occlusionMaskUrl()).isNull();
        assertThat(updated.lights()).isEmpty();
        assertThat(updated.effectLayers()).isEmpty();
        verify(imageStorage).delete(OLD_OCCLUSION);
        verify(imageStorage).delete(OLD_SNOW);
    }

    @Test
    @DisplayName("LUT-725 수정: 다른 층이 앞 가림·효과 마스크로 쓰는 파일은 남긴다, 계속 쓰는 마스크는 후보가 아니다")
    void updateKeepsSharedMasks() {
        TowerFloor f1 = floorWithEffects(1L, 1, true);
        when(towerFloorRepository.findById(1L)).thenReturn(java.util.Optional.of(f1));
        when(towerFloorRepository.findAllByOrderByFloorNumberAsc()).thenReturn(List.of(f1));
        when(imageStorage.isOwned(OLD_OCCLUSION)).thenReturn(true);
        when(towerFloorRepository.countByOcclusionMaskUrl(OLD_OCCLUSION)).thenReturn(1L);

        // 앞 가림은 새 파일로, 눈 레이어는 같은 마스크로 다시 보낸다
        service.update(
                1L,
                withEffects(
                        request(1, null, true, true),
                        "https://cdn/tower/mask/occlusion-new.png",
                        List.of(light(30)),
                        List.of(snow(OLD_SNOW))));

        assertThat(f1.getLights()).extracting(TowerFloorLight::getX).containsExactly(30);
        verify(imageStorage, never()).delete(any());
        verify(imageStorage, never()).isOwned(OLD_SNOW);
    }

    @Test
    @DisplayName("LUT-725 삭제: 앞 가림·효과 마스크도 정리한다 — 다른 층 효과 레이어가 쓰면 남긴다")
    void deleteCleansEffectMasks() {
        TowerFloor f1 = floor(1L, 1, null, true, true);
        TowerFloor f2 = floorWithEffects(2L, 2, false);
        when(towerFloorRepository.findById(2L)).thenReturn(java.util.Optional.of(f2));
        when(towerFloorRepository.findAllByOrderByFloorNumberAsc()).thenReturn(List.of(f1, f2));
        // 배경·바닥 마스크는 외부 URL 로 두고, 효과 마스크 두 장만 우리 저장소
        when(imageStorage.isOwned(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> List.of(OLD_OCCLUSION, OLD_SNOW).contains(inv.getArgument(0)));
        when(towerFloorRepository.countEffectLayersByMaskUrl(
                        org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> OLD_SNOW.equals(inv.getArgument(0)) ? 1L : 0L);

        service.delete(2L);

        verify(imageStorage).delete(OLD_OCCLUSION);
        verify(imageStorage, never()).delete(OLD_SNOW);
    }

    @Test
    @DisplayName("LUT-727: 등록·수정 뒤 픽셀 배경을 요청하고, 목록·조회·수정 응답에 픽셀 상태를 싣는다")
    void pixelArtHooks() {
        TowerFloor f1 = floor(1L, 1, null, true, true);
        io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloorPixelArt art =
                io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloorPixelArt
                        .create(1L, "https://cdn/old.jpg", 64, 100);
        when(towerFloorRepository.findAllByOrderByFloorNumberAsc()).thenReturn(List.of(f1));
        when(towerFloorRepository.findById(1L)).thenReturn(java.util.Optional.of(f1));
        when(pixelArtService.byFloorIds(List.of(1L))).thenReturn(java.util.Map.of(1L, art));

        assertThat(service.list().get(0).pixelArt().stale()).isTrue();
        assertThat(service.get(1L).pixelArt().colors()).isEqualTo(64);
        TowerFloorAdminResponse updated = service.update(1L, request(1, null, true, true));
        verify(pixelArtService).ensureFor(f1);
        assertThat(updated.pixelArt()).isNotNull();

        when(towerFloorRepository.save(any(TowerFloor.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        TowerFloorAdminResponse created = service.create(request(2, 5, false, false));
        // 수정 1번 + 등록 1번
        verify(pixelArtService, org.mockito.Mockito.times(2)).ensureFor(any(TowerFloor.class));
        assertThat(created.pixelArt()).isNull(); // 저장 전 ID 없음(가짜 저장)
    }

    @Test
    @DisplayName("LUT-727: 재생성은 층이 있어야 하고 설정만 넘긴다 · 삭제하면 픽셀 배경 파일도 정리한다(다른 층이 쓰면 남긴다)")
    void pixelArtRegenerateAndDelete() {
        TowerFloor f1 = floor(1L, 1, null, true, true);
        TowerFloor f2 = floor(2L, 2, 5, false, false);
        when(towerFloorRepository.findById(2L)).thenReturn(java.util.Optional.of(f2));
        io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloorPixelArt art =
                io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloorPixelArt
                        .create(2L, f2.getBackgroundImageUrl(), 64, 100);
        art.request(f2.getBackgroundImageUrl(), 32, null, java.time.LocalDateTime.now());
        when(pixelArtService.regenerate(2L, 32, null)).thenReturn(art);
        assertThat(service.regeneratePixelArt(2L, 32, null).colors()).isEqualTo(32);

        when(towerFloorRepository.findAllByOrderByFloorNumberAsc()).thenReturn(List.of(f1, f2));
        when(pixelArtService.imageUrlsOf(2L))
                .thenReturn(
                        java.util.Set.of(
                                "https://cdn/tower/pixel/a.png", "https://cdn/tower/pixel/b.png"));
        when(imageStorage.isOwned(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> inv.getArgument(0).toString().contains("/pixel/"));
        when(pixelArtRepository.countByImageUrl(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> inv.getArgument(0).toString().endsWith("b.png") ? 1L : 0L);

        service.delete(2L);

        verify(imageStorage).delete("https://cdn/tower/pixel/a.png");
        verify(imageStorage, never()).delete("https://cdn/tower/pixel/b.png");
    }
}
