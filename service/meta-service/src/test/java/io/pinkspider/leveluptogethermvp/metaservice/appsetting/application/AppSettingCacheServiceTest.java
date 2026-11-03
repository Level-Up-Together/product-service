package io.pinkspider.leveluptogethermvp.metaservice.appsetting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.metaservice.appsetting.domain.entity.AppSetting;
import io.pinkspider.leveluptogethermvp.metaservice.appsetting.infrastructure.AppSettingRepository;
import java.util.Arrays;
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
@DisplayName("AppSettingCacheService 테스트 (LUT-708)")
class AppSettingCacheServiceTest {

    @Mock private AppSettingRepository repository;
    @InjectMocks private AppSettingCacheService service;

    private static AppSetting setting(String value) {
        return AppSetting.builder()
                .settingKey(AppSettingCacheService.STORE_REVIEW_MILESTONE_DAYS)
                .settingValue(value)
                .build();
    }

    @Test
    @DisplayName("행이 없으면 기본값 7·14·30")
    void defaultWhenMissing() {
        when(repository.findById(AppSettingCacheService.STORE_REVIEW_MILESTONE_DAYS))
                .thenReturn(Optional.empty());

        assertThat(service.getStoreReviewMilestoneDays()).containsExactly(7, 14, 30);
    }

    @Test
    @DisplayName("저장 문자열을 정렬·중복 제거해 읽고, 빈 문자열은 빈 목록(끔), 깨진 값은 기본값")
    void parsesStoredValue() {
        when(repository.findById(AppSettingCacheService.STORE_REVIEW_MILESTONE_DAYS))
                .thenReturn(Optional.of(setting(" 30, 7,7 ,14 ")))
                .thenReturn(Optional.of(setting("")))
                .thenReturn(Optional.of(setting("7,abc")))
                .thenReturn(Optional.of(setting("0,7")));

        assertThat(service.getStoreReviewMilestoneDays()).containsExactly(7, 14, 30);
        assertThat(service.getStoreReviewMilestoneDays()).isEmpty();
        assertThat(service.getStoreReviewMilestoneDays()).containsExactly(7, 14, 30);
        assertThat(service.getStoreReviewMilestoneDays()).containsExactly(7, 14, 30);
    }

    @Test
    @DisplayName("변경: 기존 행이 있으면 값만 바꾸고(정렬·중복 제거), 없으면 새 행을 만든다")
    void updateUpsert() {
        AppSetting existing = setting("7,14,30");
        when(repository.findById(AppSettingCacheService.STORE_REVIEW_MILESTONE_DAYS))
                .thenReturn(Optional.of(existing))
                .thenReturn(Optional.empty());

        assertThat(service.updateStoreReviewMilestoneDays(List.of(21, 7, 7, 14)))
                .containsExactly(7, 14, 21);
        assertThat(existing.getSettingValue()).isEqualTo("7,14,21");
        verify(repository, never()).save(any());

        assertThat(service.updateStoreReviewMilestoneDays(List.of())).isEmpty();
        ArgumentCaptor<AppSetting> captor = ArgumentCaptor.forClass(AppSetting.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getSettingKey())
                .isEqualTo(AppSettingCacheService.STORE_REVIEW_MILESTONE_DAYS);
        assertThat(captor.getValue().getSettingValue()).isEmpty();
    }

    @Test
    @DisplayName("변경 검증: 1 미만·365 초과·null 원소·11개 이상·null 목록은 080101")
    void updateValidation() {
        for (List<Integer> bad :
                List.of(
                        List.of(0, 7),
                        List.of(7, 366),
                        Arrays.asList(7, null),
                        List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11))) {
            assertThatThrownBy(() -> service.updateStoreReviewMilestoneDays(bad))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining("error.app_setting.invalid_milestones");
        }
        assertThatThrownBy(() -> service.updateStoreReviewMilestoneDays(null))
                .isInstanceOf(CustomException.class);
        verify(repository, never()).save(any());
    }
}
