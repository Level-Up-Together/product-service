package io.pinkspider.leveluptogethermvp.userservice.preference.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.metaservice.application.MissionCategoryService;
import io.pinkspider.leveluptogethermvp.metaservice.domain.dto.MissionCategoryResponse;
import io.pinkspider.leveluptogethermvp.userservice.preference.domain.dto.UserUiPreferenceRequest;
import io.pinkspider.leveluptogethermvp.userservice.preference.domain.dto.UserUiPreferenceResponse;
import io.pinkspider.leveluptogethermvp.userservice.preference.domain.entity.UserUiPreference;
import io.pinkspider.leveluptogethermvp.userservice.preference.infrastructure.UserUiPreferenceRepository;
import java.util.List;
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

    @Mock private UserUiPreferenceRepository preferenceRepository;

    @Mock private MissionCategoryService missionCategoryService;

    @InjectMocks private UserUiPreferenceService preferenceService;

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
        UserUiPreference preference =
                UserUiPreference.builder()
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

        UserUiPreferenceRequest request =
                UserUiPreferenceRequest.builder().missionCompletedSectionCollapsed(true).build();

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
        UserUiPreference preference =
                UserUiPreference.builder()
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
        UserUiPreference preference =
                UserUiPreference.builder()
                        .userId(TEST_USER_ID)
                        .missionCompletedSectionCollapsed(true)
                        .build();
        when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));

        UserUiPreferenceRequest request =
                UserUiPreferenceRequest.builder().missionCompletedSectionCollapsed(false).build();

        // when
        UserUiPreferenceResponse response =
                preferenceService.updatePreferences(TEST_USER_ID, request);

        // then
        assertThat(response.missionCompletedSectionCollapsed()).isFalse();
        assertThat(preference.getMissionCompletedSectionCollapsed()).isFalse();
    }

    // ===== LUT-548: 홈 피드 카테고리 필터 =====

    private static MissionCategoryResponse category(long id) {
        return MissionCategoryResponse.builder().id(id).name("c" + id).build();
    }

    @Test
    @DisplayName("저장된 설정이 없으면 홈 카테고리 필터는 빈 배열(전체)이다")
    void getPreferences_noRow_emptyCategoryIds() {
        when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());

        UserUiPreferenceResponse response = preferenceService.getPreferences(TEST_USER_ID);

        assertThat(response.homeFeedCategoryIds()).isEmpty();
    }

    @Test
    @DisplayName("저장된 홈 카테고리 필터를 순서대로 응답한다")
    void getPreferences_returnsStoredCategoryIds() {
        UserUiPreference preference =
                UserUiPreference.builder()
                        .userId(TEST_USER_ID)
                        .homeFeedCategoryIds(List.of(7L, 3L))
                        .build();
        when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));

        UserUiPreferenceResponse response = preferenceService.getPreferences(TEST_USER_ID);

        assertThat(response.homeFeedCategoryIds()).containsExactly(7L, 3L);
    }

    @Test
    @DisplayName("존재하지 않는 카테고리 ID 는 400 없이 걸러내고 나머지를 순서·중복 제거해 저장한다")
    void updatePreferences_filtersUnknownCategoryIds() {
        UserUiPreference preference = UserUiPreference.builder().userId(TEST_USER_ID).build();
        when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));
        when(missionCategoryService.getCategoriesByIds(List.of(7L, 999L, 3L)))
                .thenReturn(List.of(category(3L), category(7L)));

        UserUiPreferenceRequest request =
                UserUiPreferenceRequest.builder()
                        .homeFeedCategoryIds(List.of(7L, 999L, 7L, 3L))
                        .build();

        UserUiPreferenceResponse response =
                preferenceService.updatePreferences(TEST_USER_ID, request);

        assertThat(response.homeFeedCategoryIds()).containsExactly(7L, 3L);
        assertThat(preference.getHomeFeedCategoryIds()).containsExactly(7L, 3L);
        // 다른 필드는 건드리지 않는다
        assertThat(response.missionCompletedSectionCollapsed()).isFalse();
    }

    @Test
    @DisplayName("빈 배열은 '전체'(필터 해제)로 저장하고 카테고리 조회를 하지 않는다")
    void updatePreferences_emptyArrayClearsFilter() {
        UserUiPreference preference =
                UserUiPreference.builder()
                        .userId(TEST_USER_ID)
                        .homeFeedCategoryIds(List.of(7L, 3L))
                        .build();
        when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));

        UserUiPreferenceRequest request =
                UserUiPreferenceRequest.builder().homeFeedCategoryIds(List.of()).build();

        UserUiPreferenceResponse response =
                preferenceService.updatePreferences(TEST_USER_ID, request);

        assertThat(response.homeFeedCategoryIds()).isEmpty();
        assertThat(preference.getHomeFeedCategoryIds()).isEmpty();
        verify(missionCategoryService, never()).getCategoriesByIds(anyList());
    }

    @Test
    @DisplayName("홈 카테고리 필터가 null 이면 기존 값을 유지한다 (부분 업데이트)")
    void updatePreferences_nullCategoryIdsKeepsExisting() {
        UserUiPreference preference =
                UserUiPreference.builder()
                        .userId(TEST_USER_ID)
                        .homeFeedCategoryIds(List.of(7L))
                        .build();
        when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));

        UserUiPreferenceRequest request =
                UserUiPreferenceRequest.builder().missionCompletedSectionCollapsed(true).build();

        UserUiPreferenceResponse response =
                preferenceService.updatePreferences(TEST_USER_ID, request);

        assertThat(response.homeFeedCategoryIds()).containsExactly(7L);
        assertThat(response.missionCompletedSectionCollapsed()).isTrue();
        verify(missionCategoryService, never()).getCategoriesByIds(anyList());
    }

    @Test
    @DisplayName("카테고리 조회가 실패하면 검증 없이 요청값(중복 제거)을 저장한다 — 메타 DB 장애에 저장이 끌려가지 않음")
    void updatePreferences_categoryLookupFails_savesRequested() {
        UserUiPreference preference = UserUiPreference.builder().userId(TEST_USER_ID).build();
        when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));
        when(missionCategoryService.getCategoriesByIds(anyList()))
                .thenThrow(new RuntimeException("meta db down"));

        UserUiPreferenceRequest request =
                UserUiPreferenceRequest.builder().homeFeedCategoryIds(List.of(5L, 5L, 9L)).build();

        UserUiPreferenceResponse response =
                preferenceService.updatePreferences(TEST_USER_ID, request);

        assertThat(response.homeFeedCategoryIds()).containsExactly(5L, 9L);
    }

    @Test
    @DisplayName("모두 존재하지 않는 ID 면 빈 배열(전체)로 저장된다")
    void updatePreferences_allUnknown_becomesEmpty() {
        UserUiPreference preference =
                UserUiPreference.builder()
                        .userId(TEST_USER_ID)
                        .homeFeedCategoryIds(List.of(1L))
                        .build();
        when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));
        when(missionCategoryService.getCategoriesByIds(List.of(999L))).thenReturn(List.of());

        UserUiPreferenceRequest request =
                UserUiPreferenceRequest.builder().homeFeedCategoryIds(List.of(999L)).build();

        UserUiPreferenceResponse response =
                preferenceService.updatePreferences(TEST_USER_ID, request);

        assertThat(response.homeFeedCategoryIds()).isEmpty();
    }
}
