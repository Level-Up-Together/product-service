package io.pinkspider.leveluptogethermvp.gamificationservice.mvphistory.application;

import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.DailyMvpCategoryStatsRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.DailyMvpHistoryRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.mvphistory.domain.dto.MvpHistoryAdminPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.mvphistory.domain.dto.MvpHistoryAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.mvphistory.domain.dto.MvpStatsAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.mvphistory.domain.dto.MvpStatsAdminResponse.CategoryPopularityDto;
import io.pinkspider.leveluptogethermvp.gamificationservice.mvphistory.domain.dto.MvpStatsAdminResponse.MvpUserStatsDto;
import io.pinkspider.leveluptogethermvp.gamificationservice.mvphistory.domain.dto.UserCategoryActivityAdminResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
public class MvpHistoryAdminInternalService {

    private final DailyMvpHistoryRepository dailyMvpHistoryRepository;
    private final DailyMvpCategoryStatsRepository dailyMvpCategoryStatsRepository;

    public List<MvpHistoryAdminResponse> getMvpHistoryByDate(LocalDate date) {
        return dailyMvpHistoryRepository.findByMvpDateOrderByMvpRankAsc(date).stream()
            .map(MvpHistoryAdminResponse::from)
            .collect(Collectors.toList());
    }

    public MvpHistoryAdminPageResponse getMvpHistoryByPeriod(
            LocalDate startDate, LocalDate endDate, int page, int size) {
        var pageResult = dailyMvpHistoryRepository.findByPeriodPaged(
            startDate, endDate, PageRequest.of(page, size));
        return MvpHistoryAdminPageResponse.from(pageResult.map(MvpHistoryAdminResponse::from));
    }

    public MvpHistoryAdminPageResponse getMvpHistoryByUser(String userId, int page, int size) {
        var pageResult = dailyMvpHistoryRepository.findByUserIdOrderByMvpDateDesc(
            userId, PageRequest.of(page, size));
        return MvpHistoryAdminPageResponse.from(pageResult.map(MvpHistoryAdminResponse::from));
    }

    public MvpStatsAdminResponse getMvpStats(LocalDate startDate, LocalDate endDate, int topUserLimit) {
        // 사용자별 MVP 통계
        List<Object[]> userStats = dailyMvpHistoryRepository.countMvpByUserAndPeriod(
            startDate, endDate, PageRequest.of(0, topUserLimit));
        List<MvpUserStatsDto> topMvpUsers = userStats.stream()
            .map(row -> new MvpUserStatsDto(
                (String) row[0],
                (String) row[1],
                ((Number) row[2]).longValue(),
                ((Number) row[3]).longValue()
            ))
            .collect(Collectors.toList());

        // 카테고리 인기도
        List<Object[]> categoryStats = dailyMvpCategoryStatsRepository.getCategoryStatsByPeriod(
            startDate, endDate);
        List<CategoryPopularityDto> categoryPopularity = categoryStats.stream()
            .map(row -> new CategoryPopularityDto(
                row[0] != null ? ((Number) row[0]).longValue() : null,
                (String) row[1],
                ((Number) row[2]).longValue(),
                ((Number) row[3]).longValue(),
                ((Number) row[4]).longValue()
            ))
            .collect(Collectors.toList());

        // 총계
        long totalMvpRecords = dailyMvpHistoryRepository.countByPeriod(startDate, endDate);
        long uniqueMvpUsers = dailyMvpHistoryRepository.countDistinctUsersByPeriod(startDate, endDate);

        return new MvpStatsAdminResponse(
            startDate, endDate, totalMvpRecords, uniqueMvpUsers, topMvpUsers, categoryPopularity
        );
    }

    public List<UserCategoryActivityAdminResponse> getUserCategoryActivity(
            String userId, LocalDate startDate, LocalDate endDate) {
        List<Object[]> stats = dailyMvpCategoryStatsRepository.getUserCategoryStatsByPeriod(
            userId, startDate, endDate);
        return stats.stream()
            .map(row -> new UserCategoryActivityAdminResponse(
                row[0] != null ? ((Number) row[0]).longValue() : null,
                (String) row[1],
                ((Number) row[2]).longValue(),
                ((Number) row[3]).longValue()
            ))
            .collect(Collectors.toList());
    }
}
