package io.pinkspider.leveluptogethermvp.gamificationservice.experience.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.gamificationservice.experience.domain.dto.CategoryMissionStatsAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.experience.domain.dto.TopExpGainerAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.ExperienceHistoryRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExperienceHistoryAdminService 테스트")
class ExperienceHistoryAdminServiceTest {

    @Mock
    private ExperienceHistoryRepository experienceHistoryRepository;

    @InjectMocks
    private ExperienceHistoryAdminService experienceHistoryAdminService;

    private final LocalDateTime startDate = LocalDateTime.of(2026, 1, 1, 0, 0);
    private final LocalDateTime endDate = LocalDateTime.of(2026, 1, 2, 0, 0);

    @Nested
    @DisplayName("getTopExpGainersByPeriod 테스트")
    class GetTopExpGainersByPeriodTest {

        @Test
        @DisplayName("기간별 경험치 상위 사용자 목록을 반환한다")
        void getTopExpGainersByPeriod_returnsTopGainers() {
            // given
            Object[] row1 = {"user-001", 500L};
            Object[] row2 = {"user-002", 300L};
            when(experienceHistoryRepository.findTopExpGainersAllByPeriod(any(), any(), any(Pageable.class)))
                .thenReturn(List.of(row1, row2));

            // when
            List<TopExpGainerAdminResponse> result =
                experienceHistoryAdminService.getTopExpGainersByPeriod(startDate, endDate, 10);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).userId()).isEqualTo("user-001");
            assertThat(result.get(0).totalExp()).isEqualTo(500L);
            assertThat(result.get(1).userId()).isEqualTo("user-002");
            assertThat(result.get(1).totalExp()).isEqualTo(300L);
            verify(experienceHistoryRepository).findTopExpGainersAllByPeriod(any(), any(), any(Pageable.class));
        }

        @Test
        @DisplayName("결과가 없으면 빈 목록을 반환한다")
        void getTopExpGainersByPeriod_returnsEmptyList_whenNoData() {
            // given
            when(experienceHistoryRepository.findTopExpGainersAllByPeriod(any(), any(), any(Pageable.class)))
                .thenReturn(List.of());

            // when
            List<TopExpGainerAdminResponse> result =
                experienceHistoryAdminService.getTopExpGainersByPeriod(startDate, endDate, 10);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getTopExpGainersByPeriodExcluding 테스트")
    class GetTopExpGainersByPeriodExcludingTest {

        @Test
        @DisplayName("특정 사용자를 제외하고 기간별 경험치 상위 사용자 목록을 반환한다")
        void getTopExpGainersByPeriodExcluding_returnsTopGainersExcludingUsers() {
            // given
            List<String> excludedUserIds = List.of("user-excluded");
            Object[] row = {"user-001", 500L};
            List<Object[]> rows = new java.util.ArrayList<>();
            rows.add(row);
            when(experienceHistoryRepository.findTopExpGainersAllByPeriodExcluding(any(), any(), anyList(), any(Pageable.class)))
                .thenReturn(rows);

            // when
            List<TopExpGainerAdminResponse> result =
                experienceHistoryAdminService.getTopExpGainersByPeriodExcluding(startDate, endDate, excludedUserIds, 10);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).userId()).isEqualTo("user-001");
            assertThat(result.get(0).totalExp()).isEqualTo(500L);
            verify(experienceHistoryRepository).findTopExpGainersAllByPeriodExcluding(any(), any(), anyList(), any(Pageable.class));
        }

        @Test
        @DisplayName("제외 목록으로 인해 결과가 없으면 빈 목록을 반환한다")
        void getTopExpGainersByPeriodExcluding_returnsEmptyList_whenAllExcluded() {
            // given
            when(experienceHistoryRepository.findTopExpGainersAllByPeriodExcluding(any(), any(), anyList(), any(Pageable.class)))
                .thenReturn(List.of());

            // when
            List<TopExpGainerAdminResponse> result =
                experienceHistoryAdminService.getTopExpGainersByPeriodExcluding(
                    startDate, endDate, List.of("user-001", "user-002"), 10);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getCategoryMissionStatsByPeriod 테스트")
    class GetCategoryMissionStatsByPeriodTest {

        @Test
        @DisplayName("기간별 카테고리 미션 통계를 반환한다")
        void getCategoryMissionStatsByPeriod_returnsCategoryStats() {
            // given
            Object[] row1 = {"운동", 100L, 2000L};
            Object[] row2 = {"공부", 50L, 1000L};
            when(experienceHistoryRepository.findCategoryMissionStatsByPeriod(any(), any()))
                .thenReturn(List.of(row1, row2));

            // when
            List<CategoryMissionStatsAdminResponse> result =
                experienceHistoryAdminService.getCategoryMissionStatsByPeriod(startDate, endDate);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).categoryName()).isEqualTo("운동");
            assertThat(result.get(0).executionCount()).isEqualTo(100L);
            assertThat(result.get(0).totalExp()).isEqualTo(2000L);
            assertThat(result.get(1).categoryName()).isEqualTo("공부");
            verify(experienceHistoryRepository).findCategoryMissionStatsByPeriod(any(), any());
        }

        @Test
        @DisplayName("데이터가 없으면 빈 목록을 반환한다")
        void getCategoryMissionStatsByPeriod_returnsEmptyList_whenNoData() {
            // given
            when(experienceHistoryRepository.findCategoryMissionStatsByPeriod(any(), any()))
                .thenReturn(List.of());

            // when
            List<CategoryMissionStatsAdminResponse> result =
                experienceHistoryAdminService.getCategoryMissionStatsByPeriod(startDate, endDate);

            // then
            assertThat(result).isEmpty();
        }
    }
}
