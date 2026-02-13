package io.pinkspider.leveluptogethermvp.gamificationservice.experience.application;

import io.pinkspider.leveluptogethermvp.gamificationservice.experience.domain.dto.CategoryMissionStatsAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.experience.domain.dto.TopExpGainerAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.ExperienceHistoryRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
public class ExperienceHistoryAdminService {

    private final ExperienceHistoryRepository experienceHistoryRepository;

    public List<TopExpGainerAdminResponse> getTopExpGainersByPeriod(
            LocalDateTime startDate, LocalDateTime endDate, int limit) {
        return experienceHistoryRepository.findTopExpGainersAllByPeriod(
                startDate, endDate, PageRequest.of(0, limit))
            .stream()
            .map(row -> new TopExpGainerAdminResponse(
                (String) row[0],
                ((Number) row[1]).longValue()
            ))
            .collect(Collectors.toList());
    }

    public List<TopExpGainerAdminResponse> getTopExpGainersByPeriodExcluding(
            LocalDateTime startDate, LocalDateTime endDate,
            List<String> excludedUserIds, int limit) {
        return experienceHistoryRepository.findTopExpGainersAllByPeriodExcluding(
                startDate, endDate, excludedUserIds, PageRequest.of(0, limit))
            .stream()
            .map(row -> new TopExpGainerAdminResponse(
                (String) row[0],
                ((Number) row[1]).longValue()
            ))
            .collect(Collectors.toList());
    }

    public List<CategoryMissionStatsAdminResponse> getCategoryMissionStatsByPeriod(
            LocalDateTime startDate, LocalDateTime endDate) {
        return experienceHistoryRepository.findCategoryMissionStatsByPeriod(startDate, endDate)
            .stream()
            .map(row -> new CategoryMissionStatsAdminResponse(
                (String) row[0],
                ((Number) row[1]).longValue(),
                ((Number) row[2]).longValue()
            ))
            .collect(Collectors.toList());
    }
}
