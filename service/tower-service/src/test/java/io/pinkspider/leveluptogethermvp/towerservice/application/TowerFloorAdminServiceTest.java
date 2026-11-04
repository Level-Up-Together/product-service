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
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
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

    @InjectMocks private TowerFloorAdminService service;

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
        when(towerFloorRepository.countByBackgroundImageUrlOrFloorMaskUrl(oldMask, oldMask))
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
                        req.isPublic());
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
        when(towerFloorRepository.countByBackgroundImageUrlOrFloorMaskUrl(
                        f2.getBackgroundImageUrl(), f2.getBackgroundImageUrl()))
                .thenReturn(1L);

        service.delete(2L);

        verify(imageStorage, org.mockito.Mockito.never()).delete(any());
    }
}
