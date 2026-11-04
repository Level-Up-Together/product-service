package io.pinkspider.leveluptogethermvp.towerservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerSettingsRequest;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerSettingsResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerSetting;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerSettingRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TowerSettingService (LUT-635)")
class TowerSettingServiceTest {
    @Mock private TowerSettingRepository repository;
    @InjectMocks private TowerSettingService service;

    private static TowerSetting row(String value) {
        return TowerSetting.builder()
                .settingKey(TowerSettingService.MAX_CONCURRENT_USERS)
                .settingValue(value)
                .build();
    }

    @Test
    @DisplayName("조회: 행 없음·숫자 아님·0 이하 → 제한 없음(null), 정상 값은 그대로")
    void get() {
        when(repository.findById(TowerSettingService.MAX_CONCURRENT_USERS))
                .thenReturn(Optional.empty());
        assertThat(service.get().maxConcurrentUsers()).isNull();
        assertThat(service.getMaxConcurrentUsers()).isEmpty();

        when(repository.findById(TowerSettingService.MAX_CONCURRENT_USERS))
                .thenReturn(Optional.of(row("abc")));
        assertThat(service.getMaxConcurrentUsers()).isEmpty();
        when(repository.findById(TowerSettingService.MAX_CONCURRENT_USERS))
                .thenReturn(Optional.of(row("0")));
        assertThat(service.getMaxConcurrentUsers()).isEmpty();
        when(repository.findById(TowerSettingService.MAX_CONCURRENT_USERS))
                .thenReturn(Optional.of(row(" 300 ")));
        assertThat(service.getMaxConcurrentUsers()).contains(300);
    }

    @Test
    @DisplayName("수정: 없으면 만들고 있으면 값을 바꾼다, null 은 행 삭제(제한 없음)")
    void update() {
        when(repository.findById(TowerSettingService.MAX_CONCURRENT_USERS))
                .thenReturn(Optional.empty());
        TowerSettingsResponse created = service.update(new TowerSettingsRequest(500, null));
        ArgumentCaptor<TowerSetting> captor = ArgumentCaptor.forClass(TowerSetting.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getSettingKey()).isEqualTo("max_concurrent_users");
        assertThat(captor.getValue().getSettingValue()).isEqualTo("500");
        assertThat(created.maxConcurrentUsers()).isEqualTo(500);

        TowerSetting existing = row("500");
        when(repository.findById(TowerSettingService.MAX_CONCURRENT_USERS))
                .thenReturn(Optional.of(existing));
        assertThat(service.update(new TowerSettingsRequest(120, null)).maxConcurrentUsers())
                .isEqualTo(120);
        assertThat(existing.getSettingValue()).isEqualTo("120");

        assertThat(service.update(new TowerSettingsRequest(null, null)).maxConcurrentUsers())
                .isNull();
        verify(repository).deleteById(TowerSettingService.MAX_CONCURRENT_USERS);
        verify(repository, org.mockito.Mockito.times(2)).save(any(TowerSetting.class));
    }

    @Test
    @DisplayName("LUT-693 NPC 노출 비율: 없거나 숫자 아님·범위 밖이면 100, 저장하면 그 값 · null 은 바꾸지 않는다")
    void npcExposurePercent() {
        assertThat(service.getNpcExposurePercent()).isEqualTo(100);
        when(repository.findById(TowerSettingService.NPC_EXPOSURE_PERCENT))
                .thenReturn(Optional.of(row("abc")));
        assertThat(service.getNpcExposurePercent()).isEqualTo(100);
        when(repository.findById(TowerSettingService.NPC_EXPOSURE_PERCENT))
                .thenReturn(Optional.of(row("150")));
        assertThat(service.getNpcExposurePercent()).isEqualTo(100);
        when(repository.findById(TowerSettingService.NPC_EXPOSURE_PERCENT))
                .thenReturn(Optional.of(row("-1")));
        assertThat(service.getNpcExposurePercent()).isEqualTo(100);
        when(repository.findById(TowerSettingService.NPC_EXPOSURE_PERCENT))
                .thenReturn(Optional.of(row(" 0 ")));
        assertThat(service.getNpcExposurePercent()).isZero();
        assertThat(service.get().npcExposurePercent()).isZero();

        when(repository.findById(TowerSettingService.NPC_EXPOSURE_PERCENT))
                .thenReturn(Optional.empty());
        TowerSettingsResponse saved = service.update(new TowerSettingsRequest(null, 40));
        ArgumentCaptor<TowerSetting> captor = ArgumentCaptor.forClass(TowerSetting.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getSettingKey()).isEqualTo("npc_exposure_percent");
        assertThat(captor.getValue().getSettingValue()).isEqualTo("40");
        assertThat(saved.npcExposurePercent()).isEqualTo(40);
        assertThat(saved.maxConcurrentUsers()).isNull();

        when(repository.findById(TowerSettingService.NPC_EXPOSURE_PERCENT))
                .thenReturn(Optional.of(row("40")));
        when(repository.findById(TowerSettingService.MAX_CONCURRENT_USERS))
                .thenReturn(Optional.empty());
        assertThat(service.update(new TowerSettingsRequest(10, null)).npcExposurePercent())
                .isEqualTo(40);
    }
}
