package io.pinkspider.leveluptogethermvp.towerservice.application.visitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerVisitorException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerAdminPage;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerVisitorSanctionRequest;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerVisitorSanctionResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerVisitorSanction;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerVisitorSanctionTarget;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerVisitorSanctionType;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerVisitorSanctionRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class TowerVisitorSanctionServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 4, 12, 0);
    private final TowerVisitorSanctionRepository repository =
            mock(TowerVisitorSanctionRepository.class);
    private final AtomicLong clock = new AtomicLong(1_000_000);
    private TowerVisitorSanctionService service;

    @BeforeEach
    void setUp() {
        service =
                new TowerVisitorSanctionService(
                        repository, new TowerVisitorProperties(), clock::get, () -> NOW);
    }

    @Test
    @DisplayName("판정: 활성 목록을 30초 메모 · IP·기기 모두 없으면 조회 안 함 · 조회 실패는 제재 없음(이전 메모가 있으면 그것)")
    void isBanned() {
        when(repository.findActive(NOW))
                .thenReturn(
                        List.of(
                                TowerVisitorBasicsTest.sanction(
                                        TowerVisitorSanctionTarget.DEVICE,
                                        "dev-1",
                                        TowerVisitorSanctionType.CHAT,
                                        null)));
        assertThat(service.isBanned(TowerVisitorSanctionType.CHAT, null, null)).isFalse();
        assertThat(service.isBanned(TowerVisitorSanctionType.CHAT, "1.1.1.1", "dev-1")).isTrue();
        assertThat(service.isBanned(TowerVisitorSanctionType.ENTRY, "1.1.1.1", "dev-1")).isFalse();
        verify(repository, times(1)).findActive(NOW);

        clock.addAndGet(31_000);
        when(repository.findActive(NOW)).thenThrow(new IllegalStateException("db"));
        assertThat(service.isBanned(TowerVisitorSanctionType.CHAT, null, "dev-1")).isTrue();

        TowerVisitorSanctionService fresh =
                new TowerVisitorSanctionService(
                        repository, new TowerVisitorProperties(), clock::get, () -> NOW);
        assertThat(fresh.isBanned(TowerVisitorSanctionType.CHAT, null, "dev-1")).isFalse();
    }

    @Test
    @DisplayName("등록: 값 앞뒤 공백 제거·기간이면 끝나는 시각 · 메모를 비운다 · 빈 값은 150505 · 해제는 멱등, 없으면 150506 · 목록 페이지")
    void createLiftList() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findActive(NOW)).thenReturn(List.of());
        service.isBanned(TowerVisitorSanctionType.CHAT, "1.1.1.1", null);

        TowerVisitorSanctionResponse created =
                service.create(
                        new TowerVisitorSanctionRequest(
                                TowerVisitorSanctionTarget.IP,
                                " 1.1.1.1 ",
                                TowerVisitorSanctionType.ENTRY,
                                "도배",
                                60,
                                "admin-1"));
        ArgumentCaptor<TowerVisitorSanction> saved =
                ArgumentCaptor.forClass(TowerVisitorSanction.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getTargetValue()).isEqualTo("1.1.1.1");
        assertThat(created.expiresAt()).isEqualTo(NOW.plusMinutes(60));
        assertThat(created.active()).isTrue();
        assertThat(created.createdBy()).isEqualTo("admin-1");
        service.isBanned(TowerVisitorSanctionType.CHAT, "1.1.1.1", null);
        verify(repository, times(2)).findActive(NOW); // 등록이 메모를 비웠다

        TowerVisitorSanctionResponse permanent =
                service.create(
                        new TowerVisitorSanctionRequest(
                                TowerVisitorSanctionTarget.DEVICE,
                                "dev-1",
                                TowerVisitorSanctionType.CHAT,
                                null,
                                null,
                                null));
        assertThat(permanent.expiresAt()).isNull();
        assertThatThrownBy(
                        () ->
                                service.create(
                                        new TowerVisitorSanctionRequest(
                                                TowerVisitorSanctionTarget.IP,
                                                " ",
                                                TowerVisitorSanctionType.CHAT,
                                                null,
                                                null,
                                                null)))
                .hasFieldOrPropertyWithValue("code", TowerVisitorException.SANCTION_INVALID);

        TowerVisitorSanction existing =
                TowerVisitorBasicsTest.sanction(
                        TowerVisitorSanctionTarget.IP,
                        "1.1.1.1",
                        TowerVisitorSanctionType.CHAT,
                        null);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        assertThat(service.lift(1L).active()).isFalse();
        assertThat(existing.getLiftedAt()).isEqualTo(NOW);
        when(repository.findById(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.lift(2L))
                .hasFieldOrPropertyWithValue("code", TowerVisitorException.SANCTION_NOT_FOUND);

        when(repository.findAllByOrderByIdDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(existing), PageRequest.of(0, 100), 1));
        TowerAdminPage<TowerVisitorSanctionResponse> page = service.list(-1, 500);
        assertThat(page.content()).hasSize(1);
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(TowerVisitorSanctionService.clampSize(0)).isEqualTo(1);
    }
}
