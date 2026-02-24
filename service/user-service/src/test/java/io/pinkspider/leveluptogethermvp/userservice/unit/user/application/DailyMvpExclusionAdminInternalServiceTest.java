package io.pinkspider.leveluptogethermvp.userservice.unit.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin.DailyMvpExclusionAdminRequest;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin.DailyMvpExclusionAdminResponse;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.DailyMvpExclusion;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.DailyMvpExclusionRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DailyMvpExclusionAdminInternalServiceTest {

    @Mock
    private DailyMvpExclusionRepository dailyMvpExclusionRepository;

    @InjectMocks
    private DailyMvpExclusionAdminInternalService service;

    private static final LocalDate TEST_DATE = LocalDate.of(2026, 1, 15);

    @Nested
    @DisplayName("getExclusionsByDate 테스트")
    class GetExclusionsByDateTest {

        @Test
        @DisplayName("날짜별 제외 목록을 반환한다")
        void returnsExclusionsByDate() {
            // given
            DailyMvpExclusion exclusion = DailyMvpExclusion.builder()
                .mvpDate(TEST_DATE)
                .userId("user-1")
                .reason("테스트")
                .adminId(1L)
                .build();
            when(dailyMvpExclusionRepository.findAllByMvpDateOrderByCreatedAtDesc(TEST_DATE))
                .thenReturn(List.of(exclusion));

            // when
            List<DailyMvpExclusionAdminResponse> result = service.getExclusionsByDate(TEST_DATE);

            // then
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("빈 목록을 반환한다")
        void returnsEmptyList() {
            when(dailyMvpExclusionRepository.findAllByMvpDateOrderByCreatedAtDesc(TEST_DATE))
                .thenReturn(List.of());

            List<DailyMvpExclusionAdminResponse> result = service.getExclusionsByDate(TEST_DATE);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("addExclusion 테스트")
    class AddExclusionTest {

        @Test
        @DisplayName("제외 항목을 추가한다")
        void addsExclusion() {
            // given
            DailyMvpExclusionAdminRequest request = new DailyMvpExclusionAdminRequest(
                TEST_DATE, "user-1", "테스트 제외", 1L);
            when(dailyMvpExclusionRepository.existsByMvpDateAndUserId(TEST_DATE, "user-1"))
                .thenReturn(false);
            DailyMvpExclusion saved = DailyMvpExclusion.builder()
                .mvpDate(TEST_DATE)
                .userId("user-1")
                .reason("테스트 제외")
                .adminId(1L)
                .build();
            when(dailyMvpExclusionRepository.save(any())).thenReturn(saved);

            // when
            DailyMvpExclusionAdminResponse result = service.addExclusion(request);

            // then
            assertThat(result).isNotNull();
            verify(dailyMvpExclusionRepository).save(any());
        }

        @Test
        @DisplayName("이미 제외된 사용자는 예외를 발생시킨다")
        void throwsWhenAlreadyExcluded() {
            DailyMvpExclusionAdminRequest request = new DailyMvpExclusionAdminRequest(
                TEST_DATE, "user-1", "중복", 1L);
            when(dailyMvpExclusionRepository.existsByMvpDateAndUserId(TEST_DATE, "user-1"))
                .thenReturn(true);

            assertThatThrownBy(() -> service.addExclusion(request))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("removeExclusion 테스트")
    class RemoveExclusionTest {

        @Test
        @DisplayName("제외 항목을 삭제한다")
        void removesExclusion() {
            when(dailyMvpExclusionRepository.existsByMvpDateAndUserId(TEST_DATE, "user-1"))
                .thenReturn(true);

            service.removeExclusion(TEST_DATE, "user-1");

            verify(dailyMvpExclusionRepository).deleteByMvpDateAndUserId(TEST_DATE, "user-1");
        }

        @Test
        @DisplayName("존재하지 않는 제외 항목은 예외를 발생시킨다")
        void throwsWhenNotFound() {
            when(dailyMvpExclusionRepository.existsByMvpDateAndUserId(TEST_DATE, "user-1"))
                .thenReturn(false);

            assertThatThrownBy(() -> service.removeExclusion(TEST_DATE, "user-1"))
                .isInstanceOf(CustomException.class);
        }
    }
}
