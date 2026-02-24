package io.pinkspider.leveluptogethermvp.gamificationservice.mvphistory.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.DailyMvpHistory;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.DailyMvpCategoryStatsRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.DailyMvpHistoryRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.mvphistory.domain.dto.MvpHistoryAdminPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.mvphistory.domain.dto.MvpHistoryAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.mvphistory.domain.dto.MvpStatsAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.mvphistory.domain.dto.UserCategoryActivityAdminResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class MvpHistoryAdminInternalServiceTest {

    @Mock
    private DailyMvpHistoryRepository dailyMvpHistoryRepository;

    @Mock
    private DailyMvpCategoryStatsRepository dailyMvpCategoryStatsRepository;

    @InjectMocks
    private MvpHistoryAdminInternalService mvpHistoryAdminInternalService;

    private DailyMvpHistory createMvpHistory(Long id, String userId, LocalDate date, int rank) {
        DailyMvpHistory history = DailyMvpHistory.builder()
            .mvpDate(date)
            .mvpRank(rank)
            .userId(userId)
            .nickname("테스트유저")
            .userLevel(10)
            .earnedExp(1000L)
            .topCategoryName("운동")
            .topCategoryId(1L)
            .topCategoryExp(500L)
            .build();
        setId(history, id);
        return history;
    }

    @Nested
    @DisplayName("getMvpHistoryByDate 테스트")
    class GetMvpHistoryByDateTest {

        @Test
        @DisplayName("특정 날짜의 MVP 히스토리 목록을 반환한다")
        void getMvpHistoryByDate_success() {
            // given
            LocalDate date = LocalDate.of(2025, 1, 1);
            List<DailyMvpHistory> entities = List.of(
                createMvpHistory(1L, "user-001", date, 1),
                createMvpHistory(2L, "user-002", date, 2)
            );
            when(dailyMvpHistoryRepository.findByMvpDateOrderByMvpRankAsc(date)).thenReturn(entities);

            // when
            List<MvpHistoryAdminResponse> result = mvpHistoryAdminInternalService.getMvpHistoryByDate(date);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getMvpRank()).isEqualTo(1);
            assertThat(result.get(1).getMvpRank()).isEqualTo(2);
        }

        @Test
        @DisplayName("해당 날짜에 MVP 데이터가 없으면 빈 목록을 반환한다")
        void getMvpHistoryByDate_empty() {
            // given
            LocalDate date = LocalDate.of(2025, 1, 1);
            when(dailyMvpHistoryRepository.findByMvpDateOrderByMvpRankAsc(date)).thenReturn(List.of());

            // when
            List<MvpHistoryAdminResponse> result = mvpHistoryAdminInternalService.getMvpHistoryByDate(date);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getMvpHistoryByPeriod 테스트")
    class GetMvpHistoryByPeriodTest {

        @Test
        @DisplayName("기간별 MVP 히스토리를 페이징으로 반환한다")
        void getMvpHistoryByPeriod_success() {
            // given
            LocalDate startDate = LocalDate.of(2025, 1, 1);
            LocalDate endDate = LocalDate.of(2025, 1, 31);
            DailyMvpHistory entity = createMvpHistory(1L, "user-001", startDate, 1);
            Pageable pageable = PageRequest.of(0, 20);
            Page<DailyMvpHistory> page = new PageImpl<>(List.of(entity), pageable, 1);

            when(dailyMvpHistoryRepository.findByPeriodPaged(eq(startDate), eq(endDate), any(Pageable.class)))
                .thenReturn(page);

            // when
            MvpHistoryAdminPageResponse result =
                mvpHistoryAdminInternalService.getMvpHistoryByPeriod(startDate, endDate, 0, 20);

            // then
            assertThat(result).isNotNull();
            assertThat(result.content()).hasSize(1);
            assertThat(result.totalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("기간 내 데이터가 없으면 빈 페이지를 반환한다")
        void getMvpHistoryByPeriod_empty() {
            // given
            LocalDate startDate = LocalDate.of(2025, 1, 1);
            LocalDate endDate = LocalDate.of(2025, 1, 31);
            Pageable pageable = PageRequest.of(0, 20);
            Page<DailyMvpHistory> emptyPage = new PageImpl<>(List.of(), pageable, 0);

            when(dailyMvpHistoryRepository.findByPeriodPaged(eq(startDate), eq(endDate), any(Pageable.class)))
                .thenReturn(emptyPage);

            // when
            MvpHistoryAdminPageResponse result =
                mvpHistoryAdminInternalService.getMvpHistoryByPeriod(startDate, endDate, 0, 20);

            // then
            assertThat(result.content()).isEmpty();
            assertThat(result.totalElements()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("getMvpHistoryByUser 테스트")
    class GetMvpHistoryByUserTest {

        @Test
        @DisplayName("사용자별 MVP 히스토리를 페이징으로 반환한다")
        void getMvpHistoryByUser_success() {
            // given
            String userId = "user-001";
            DailyMvpHistory entity = createMvpHistory(1L, userId, LocalDate.now(), 1);
            Pageable pageable = PageRequest.of(0, 20);
            Page<DailyMvpHistory> page = new PageImpl<>(List.of(entity), pageable, 1);

            when(dailyMvpHistoryRepository.findByUserIdOrderByMvpDateDesc(eq(userId), any(Pageable.class)))
                .thenReturn(page);

            // when
            MvpHistoryAdminPageResponse result =
                mvpHistoryAdminInternalService.getMvpHistoryByUser(userId, 0, 20);

            // then
            assertThat(result).isNotNull();
            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).getUserId()).isEqualTo(userId);
        }
    }

    @Nested
    @DisplayName("getMvpStats 테스트")
    class GetMvpStatsTest {

        @Test
        @DisplayName("기간별 MVP 통계를 반환한다")
        void getMvpStats_success() {
            // given
            LocalDate startDate = LocalDate.of(2025, 1, 1);
            LocalDate endDate = LocalDate.of(2025, 1, 31);

            Object[] userRow = new Object[]{"user-001", "테스트유저", 5L, 2L};
            Object[] categoryRow = new Object[]{1L, "운동", 5000L, 100L, 20L};

            List<Object[]> userRows = new ArrayList<>();
            userRows.add(userRow);
            List<Object[]> categoryRows = new ArrayList<>();
            categoryRows.add(categoryRow);
            when(dailyMvpHistoryRepository.countMvpByUserAndPeriod(eq(startDate), eq(endDate), any(Pageable.class)))
                .thenReturn(userRows);
            when(dailyMvpCategoryStatsRepository.getCategoryStatsByPeriod(startDate, endDate))
                .thenReturn(categoryRows);
            when(dailyMvpHistoryRepository.countByPeriod(startDate, endDate)).thenReturn(30L);
            when(dailyMvpHistoryRepository.countDistinctUsersByPeriod(startDate, endDate)).thenReturn(10L);

            // when
            MvpStatsAdminResponse result =
                mvpHistoryAdminInternalService.getMvpStats(startDate, endDate, 5);

            // then
            assertThat(result).isNotNull();
            assertThat(result.totalMvpRecords()).isEqualTo(30L);
            assertThat(result.uniqueMvpUsers()).isEqualTo(10L);
            assertThat(result.topMvpUsers()).hasSize(1);
            assertThat(result.categoryPopularity()).hasSize(1);
        }

        @Test
        @DisplayName("카테고리 ID가 null인 경우에도 통계를 정상 반환한다")
        void getMvpStats_nullCategoryId() {
            // given
            LocalDate startDate = LocalDate.of(2025, 1, 1);
            LocalDate endDate = LocalDate.of(2025, 1, 31);

            Object[] userRow = new Object[]{"user-001", "테스트유저", 3L, 1L};
            // categoryId가 null인 경우
            Object[] categoryRow = new Object[]{null, "전체", 3000L, 50L, 10L};

            List<Object[]> userRows2 = new ArrayList<>();
            userRows2.add(userRow);
            List<Object[]> categoryRows2 = new ArrayList<>();
            categoryRows2.add(categoryRow);
            when(dailyMvpHistoryRepository.countMvpByUserAndPeriod(eq(startDate), eq(endDate), any(Pageable.class)))
                .thenReturn(userRows2);
            when(dailyMvpCategoryStatsRepository.getCategoryStatsByPeriod(startDate, endDate))
                .thenReturn(categoryRows2);
            when(dailyMvpHistoryRepository.countByPeriod(startDate, endDate)).thenReturn(10L);
            when(dailyMvpHistoryRepository.countDistinctUsersByPeriod(startDate, endDate)).thenReturn(5L);

            // when
            MvpStatsAdminResponse result =
                mvpHistoryAdminInternalService.getMvpStats(startDate, endDate, 5);

            // then
            assertThat(result).isNotNull();
            assertThat(result.categoryPopularity().get(0).categoryId()).isNull();
        }

        @Test
        @DisplayName("데이터가 없으면 0 통계를 반환한다")
        void getMvpStats_emptyData() {
            // given
            LocalDate startDate = LocalDate.of(2025, 1, 1);
            LocalDate endDate = LocalDate.of(2025, 1, 31);

            when(dailyMvpHistoryRepository.countMvpByUserAndPeriod(eq(startDate), eq(endDate), any(Pageable.class)))
                .thenReturn(List.of());
            when(dailyMvpCategoryStatsRepository.getCategoryStatsByPeriod(startDate, endDate))
                .thenReturn(List.of());
            when(dailyMvpHistoryRepository.countByPeriod(startDate, endDate)).thenReturn(0L);
            when(dailyMvpHistoryRepository.countDistinctUsersByPeriod(startDate, endDate)).thenReturn(0L);

            // when
            MvpStatsAdminResponse result =
                mvpHistoryAdminInternalService.getMvpStats(startDate, endDate, 5);

            // then
            assertThat(result.totalMvpRecords()).isEqualTo(0L);
            assertThat(result.uniqueMvpUsers()).isEqualTo(0L);
            assertThat(result.topMvpUsers()).isEmpty();
            assertThat(result.categoryPopularity()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getUserCategoryActivity 테스트")
    class GetUserCategoryActivityTest {

        @Test
        @DisplayName("사용자별 카테고리 활동 통계를 반환한다")
        void getUserCategoryActivity_success() {
            // given
            String userId = "user-001";
            LocalDate startDate = LocalDate.of(2025, 1, 1);
            LocalDate endDate = LocalDate.of(2025, 1, 31);

            Object[] row1 = new Object[]{1L, "운동", 3000L, 60L};
            Object[] row2 = new Object[]{2L, "독서", 1000L, 20L};

            when(dailyMvpCategoryStatsRepository.getUserCategoryStatsByPeriod(userId, startDate, endDate))
                .thenReturn(List.of(row1, row2));

            // when
            List<UserCategoryActivityAdminResponse> result =
                mvpHistoryAdminInternalService.getUserCategoryActivity(userId, startDate, endDate);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).categoryId()).isEqualTo(1L);
            assertThat(result.get(0).categoryName()).isEqualTo("운동");
            assertThat(result.get(0).totalExp()).isEqualTo(3000L);
        }

        @Test
        @DisplayName("카테고리 ID가 null인 경우에도 정상 반환한다")
        void getUserCategoryActivity_nullCategoryId() {
            // given
            String userId = "user-001";
            LocalDate startDate = LocalDate.of(2025, 1, 1);
            LocalDate endDate = LocalDate.of(2025, 1, 31);

            Object[] row = new Object[]{null, "전체", 1000L, 20L};
            List<Object[]> rows = new ArrayList<>();
            rows.add(row);

            when(dailyMvpCategoryStatsRepository.getUserCategoryStatsByPeriod(userId, startDate, endDate))
                .thenReturn(rows);

            // when
            List<UserCategoryActivityAdminResponse> result =
                mvpHistoryAdminInternalService.getUserCategoryActivity(userId, startDate, endDate);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).categoryId()).isNull();
        }

        @Test
        @DisplayName("데이터가 없으면 빈 목록을 반환한다")
        void getUserCategoryActivity_empty() {
            // given
            String userId = "user-001";
            LocalDate startDate = LocalDate.of(2025, 1, 1);
            LocalDate endDate = LocalDate.of(2025, 1, 31);

            when(dailyMvpCategoryStatsRepository.getUserCategoryStatsByPeriod(userId, startDate, endDate))
                .thenReturn(List.of());

            // when
            List<UserCategoryActivityAdminResponse> result =
                mvpHistoryAdminInternalService.getUserCategoryActivity(userId, startDate, endDate);

            // then
            assertThat(result).isEmpty();
        }
    }
}
