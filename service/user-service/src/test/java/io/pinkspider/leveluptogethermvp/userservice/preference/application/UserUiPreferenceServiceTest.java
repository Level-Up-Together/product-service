package io.pinkspider.leveluptogethermvp.userservice.preference.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.userservice.preference.domain.dto.UserUiPreferenceRequest;
import io.pinkspider.leveluptogethermvp.userservice.preference.domain.dto.UserUiPreferenceResponse;
import io.pinkspider.leveluptogethermvp.userservice.preference.domain.entity.UserUiPreference;
import io.pinkspider.leveluptogethermvp.userservice.preference.infrastructure.UserUiPreferenceRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserUiPreferenceServiceTest {

    private static final String TEST_USER_ID = "test-user-123";

    @Mock
    private UserUiPreferenceRepository preferenceRepository;

    @InjectMocks
    private UserUiPreferenceService preferenceService;

    @Test
    @DisplayName("저장된 설정이 없으면 기본값(펼침)으로 응답하고 행을 만들지 않는다")
    void getPreferences_noRow_returnsDefaults() {
        // given
        when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());

        // when
        UserUiPreferenceResponse response = preferenceService.getPreferences(TEST_USER_ID);

        // then
        assertThat(response.missionCompletedSectionCollapsed()).isFalse();
        verify(preferenceRepository, never()).save(any());
    }

    @Test
    @DisplayName("저장된 설정이 있으면 그 값으로 응답한다")
    void getPreferences_existingRow() {
        // given
        UserUiPreference preference = UserUiPreference.builder()
            .userId(TEST_USER_ID)
            .missionCompletedSectionCollapsed(true)
            .build();
        when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));

        // when
        UserUiPreferenceResponse response = preferenceService.getPreferences(TEST_USER_ID);

        // then
        assertThat(response.missionCompletedSectionCollapsed()).isTrue();
    }

    @Test
    @DisplayName("설정이 없던 유저의 업데이트는 기본 행을 만든 뒤 값을 적용한다")
    void updatePreferences_createsRowThenApplies() {
        // given
        when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
        when(preferenceRepository.save(any(UserUiPreference.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        UserUiPreferenceRequest request = UserUiPreferenceRequest.builder()
            .missionCompletedSectionCollapsed(true)
            .build();

        // when
        UserUiPreferenceResponse response =
            preferenceService.updatePreferences(TEST_USER_ID, request);

        // then
        assertThat(response.missionCompletedSectionCollapsed()).isTrue();
        verify(preferenceRepository).save(any(UserUiPreference.class));
    }

    @Test
    @DisplayName("null 필드는 기존 값을 변경하지 않는다 (부분 업데이트)")
    void updatePreferences_nullFieldKeepsExistingValue() {
        // given
        UserUiPreference preference = UserUiPreference.builder()
            .userId(TEST_USER_ID)
            .missionCompletedSectionCollapsed(true)
            .build();
        when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));

        UserUiPreferenceRequest request = UserUiPreferenceRequest.builder().build();

        // when
        UserUiPreferenceResponse response =
            preferenceService.updatePreferences(TEST_USER_ID, request);

        // then
        assertThat(response.missionCompletedSectionCollapsed()).isTrue();
        verify(preferenceRepository, never()).save(any());
    }

    @Test
    @DisplayName("접기 해제(false)도 저장된다")
    void updatePreferences_falseIsApplied() {
        // given
        UserUiPreference preference = UserUiPreference.builder()
            .userId(TEST_USER_ID)
            .missionCompletedSectionCollapsed(true)
            .build();
        when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));

        UserUiPreferenceRequest request = UserUiPreferenceRequest.builder()
            .missionCompletedSectionCollapsed(false)
            .build();

        // when
        UserUiPreferenceResponse response =
            preferenceService.updatePreferences(TEST_USER_ID, request);

        // then
        assertThat(response.missionCompletedSectionCollapsed()).isFalse();
        assertThat(preference.getMissionCompletedSectionCollapsed()).isFalse();
    }
}
