package io.pinkspider.leveluptogethermvp.gamificationservice.application;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.DailyMvpCategoryStats;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.DailyMvpHistory;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserExperience;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserTitle;
import io.pinkspider.global.enums.TitlePosition;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.DailyMvpCategoryStatsRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.DailyMvpHistoryRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.ExperienceHistoryRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserExperienceRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserTitleRepository;
import io.pinkspider.leveluptogethermvp.metaservice.application.MissionCategoryService;
import io.pinkspider.leveluptogethermvp.metaservice.domain.dto.MissionCategoryResponse;
import io.pinkspider.leveluptogethermvp.userservice.profile.application.UserProfileCacheService;
import io.pinkspider.leveluptogethermvp.userservice.profile.domain.dto.UserProfileCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
public class DailyMvpHistoryService {

    private final DailyMvpHistoryRepository historyRepository;
    private final DailyMvpCategoryStatsRepository categoryStatsRepository;
    private final ExperienceHistoryRepository experienceHistoryRepository;
    private final UserProfileCacheService userProfileCacheService;
    private final UserExperienceRepository userExperienceRepository;
    private final UserTitleRepository userTitleRepository;
    private final MissionCategoryService missionCategoryService;

    private static final int MVP_COUNT = 5;

    /**
     * 특정 날짜의 MVP 데이터를 캡처하여 저장
     */
    @Transactional(transactionManager = "gamificationTransactionManager")
    public void captureAndSaveDailyMvp(LocalDate targetDate) {
        // 이미 존재하는 경우 스킵 (중복 방지)
        // 기존 레코드 수를 확인하여 완전히 저장된 경우만 스킵
        long existingCount = historyRepository.countByMvpDate(targetDate);
        if (existingCount >= MVP_COUNT) {
            log.info("이미 저장된 MVP 히스토리가 있습니다: date={}, count={}", targetDate, existingCount);
            return;
        }

        // 부분적으로 저장된 경우 기존 데이터 삭제 후 재저장
        if (existingCount > 0) {
            log.warn("부분적으로 저장된 MVP 히스토리 발견, 삭제 후 재저장: date={}, existingCount={}", targetDate, existingCount);
            historyRepository.deleteByMvpDate(targetDate);
            categoryStatsRepository.deleteByStatsDate(targetDate);
        }

        LocalDateTime startDate = targetDate.atStartOfDay();
        LocalDateTime endDate = targetDate.atTime(LocalTime.MAX);

        // 1. 상위 5명의 MVP 조회
        List<Object[]> topGainers = experienceHistoryRepository.findTopExpGainersByPeriod(
            startDate, endDate, PageRequest.of(0, MVP_COUNT));

        if (topGainers.isEmpty()) {
            log.info("해당 날짜에 MVP 데이터가 없습니다: date={}", targetDate);
            return;
        }

        // 2. 사용자 ID 추출
        List<String> userIds = topGainers.stream()
            .map(row -> (String) row[0])
            .toList();

        // 3. 배치 조회: 사용자 프로필 (캐시)
        Map<String, UserProfileCache> profileMap = userProfileCacheService.getUserProfiles(userIds);

        // 4. 배치 조회: 레벨 정보
        Map<String, Integer> levelMap = userExperienceRepository.findByUserIdIn(userIds).stream()
            .collect(Collectors.toMap(UserExperience::getUserId, UserExperience::getCurrentLevel));

        // 5. 배치 조회: 장착된 칭호
        Map<String, List<UserTitle>> titleMap = userTitleRepository.findEquippedTitlesByUserIdIn(userIds).stream()
            .collect(Collectors.groupingBy(UserTitle::getUserId));

        // 6. 각 사용자별 카테고리 통계 조회
        Map<String, List<Object[]>> categoryStatsMap = new HashMap<>();
        for (String userId : userIds) {
            List<Object[]> categoryStats = experienceHistoryRepository
                .findUserCategoryExpByPeriod(userId, startDate, endDate);
            categoryStatsMap.put(userId, categoryStats);
        }

        // 7. 카테고리 이름 -> ID 매핑 조회
        Map<String, Long> categoryNameToIdMap = missionCategoryService.getActiveCategories().stream()
            .collect(Collectors.toMap(
                MissionCategoryResponse::getName,
                MissionCategoryResponse::getId,
                (existing, replacement) -> existing
            ));

        // 8. MVP 히스토리 저장
        int rank = 1;
        for (Object[] row : topGainers) {
            String odayUserId = (String) row[0];
            Long earnedExp = ((Number) row[1]).longValue();

            UserProfileCache profile = profileMap.get(odayUserId);
            Integer level = levelMap.getOrDefault(odayUserId, 1);
            TitleInfo titleInfo = buildTitleInfo(titleMap.get(odayUserId));

            // 최다 활동 카테고리 추출
            CategoryInfo topCategory = getTopCategory(categoryStatsMap.get(odayUserId), categoryNameToIdMap);

            DailyMvpHistory history = DailyMvpHistory.builder()
                .mvpDate(targetDate)
                .mvpRank(rank++)
                .userId(odayUserId)
                .nickname(profile != null ? profile.nickname() : null)
                .picture(profile != null ? profile.picture() : null)
                .userLevel(level)
                .earnedExp(earnedExp)
                .topCategoryName(topCategory.name())
                .topCategoryId(topCategory.id())
                .topCategoryExp(topCategory.exp())
                .titleName(titleInfo.name())
                .titleRarity(titleInfo.rarity())
                .build();

            historyRepository.save(history);
        }

        // 9. 카테고리 통계 저장 (Top 5 사용자의 카테고리별 활동)
        saveCategoryStats(targetDate, categoryStatsMap, categoryNameToIdMap);

        log.info("MVP 히스토리 저장 완료: date={}, count={}", targetDate, topGainers.size());
    }

    /**
     * 수동으로 특정 날짜의 MVP 데이터 재처리
     */
    @Transactional(transactionManager = "gamificationTransactionManager")
    public void reprocessDailyMvp(LocalDate targetDate) {
        log.info("MVP 히스토리 재처리 시작: date={}", targetDate);

        // 기존 데이터 삭제
        historyRepository.deleteByMvpDate(targetDate);
        categoryStatsRepository.deleteByStatsDate(targetDate);

        // 재처리
        captureAndSaveDailyMvpInternal(targetDate);

        log.info("MVP 히스토리 재처리 완료: date={}", targetDate);
    }

    /**
     * 내부용 저장 메서드 (존재 체크 없이)
     */
    private void captureAndSaveDailyMvpInternal(LocalDate targetDate) {
        LocalDateTime startDate = targetDate.atStartOfDay();
        LocalDateTime endDate = targetDate.atTime(LocalTime.MAX);

        List<Object[]> topGainers = experienceHistoryRepository.findTopExpGainersByPeriod(
            startDate, endDate, PageRequest.of(0, MVP_COUNT));

        if (topGainers.isEmpty()) {
            log.info("해당 날짜에 MVP 데이터가 없습니다: date={}", targetDate);
            return;
        }

        List<String> userIds = topGainers.stream()
            .map(row -> (String) row[0])
            .toList();

        Map<String, UserProfileCache> profileMap = userProfileCacheService.getUserProfiles(userIds);

        Map<String, Integer> levelMap = userExperienceRepository.findByUserIdIn(userIds).stream()
            .collect(Collectors.toMap(UserExperience::getUserId, UserExperience::getCurrentLevel));

        Map<String, List<UserTitle>> titleMap = userTitleRepository.findEquippedTitlesByUserIdIn(userIds).stream()
            .collect(Collectors.groupingBy(UserTitle::getUserId));

        Map<String, List<Object[]>> categoryStatsMap = new HashMap<>();
        for (String userId : userIds) {
            List<Object[]> categoryStats = experienceHistoryRepository
                .findUserCategoryExpByPeriod(userId, startDate, endDate);
            categoryStatsMap.put(userId, categoryStats);
        }

        Map<String, Long> categoryNameToIdMap = missionCategoryService.getActiveCategories().stream()
            .collect(Collectors.toMap(
                MissionCategoryResponse::getName,
                MissionCategoryResponse::getId,
                (existing, replacement) -> existing
            ));

        int rank = 1;
        for (Object[] row : topGainers) {
            String odayUserId = (String) row[0];
            Long earnedExp = ((Number) row[1]).longValue();

            UserProfileCache profile = profileMap.get(odayUserId);
            Integer level = levelMap.getOrDefault(odayUserId, 1);
            TitleInfo titleInfo = buildTitleInfo(titleMap.get(odayUserId));
            CategoryInfo topCategory = getTopCategory(categoryStatsMap.get(odayUserId), categoryNameToIdMap);

            DailyMvpHistory history = DailyMvpHistory.builder()
                .mvpDate(targetDate)
                .mvpRank(rank++)
                .userId(odayUserId)
                .nickname(profile != null ? profile.nickname() : null)
                .picture(profile != null ? profile.picture() : null)
                .userLevel(level)
                .earnedExp(earnedExp)
                .topCategoryName(topCategory.name())
                .topCategoryId(topCategory.id())
                .topCategoryExp(topCategory.exp())
                .titleName(titleInfo.name())
                .titleRarity(titleInfo.rarity())
                .build();

            historyRepository.save(history);
        }

        saveCategoryStats(targetDate, categoryStatsMap, categoryNameToIdMap);
    }

    private void saveCategoryStats(LocalDate targetDate, Map<String, List<Object[]>> categoryStatsMap,
                                   Map<String, Long> categoryNameToIdMap) {
        for (Map.Entry<String, List<Object[]>> entry : categoryStatsMap.entrySet()) {
            String odayUserId = entry.getKey();
            List<Object[]> stats = entry.getValue();

            if (stats == null) continue;

            for (Object[] stat : stats) {
                String categoryName = (String) stat[0];
                Long categoryExp = ((Number) stat[2]).longValue();
                Integer activityCount = ((Number) stat[3]).intValue();

                // 카테고리 ID 조회
                Long categoryId = categoryNameToIdMap.get(categoryName);

                if (categoryId != null && categoryExp > 0) {
                    DailyMvpCategoryStats categoryStat = DailyMvpCategoryStats.builder()
                        .statsDate(targetDate)
                        .userId(odayUserId)
                        .categoryId(categoryId)
                        .categoryName(categoryName)
                        .earnedExp(categoryExp)
                        .activityCount(activityCount)
                        .build();

                    categoryStatsRepository.save(categoryStat);
                }
            }
        }
    }

    private TitleInfo buildTitleInfo(List<UserTitle> titles) {
        if (titles == null || titles.isEmpty()) {
            return new TitleInfo(null, null);
        }

        // LEFT + RIGHT 조합
        String leftTitle = titles.stream()
            .filter(t -> t.getEquippedPosition() == TitlePosition.LEFT)
            .findFirst()
            .map(t -> t.getTitle().getName())
            .orElse(null);

        String rightTitle = titles.stream()
            .filter(t -> t.getEquippedPosition() == TitlePosition.RIGHT)
            .findFirst()
            .map(t -> t.getTitle().getName())
            .orElse(null);

        TitleRarity highestRarity = titles.stream()
            .map(t -> t.getTitle().getRarity())
            .filter(Objects::nonNull)
            .max((r1, r2) -> Integer.compare(r1.ordinal(), r2.ordinal()))
            .orElse(null);

        String combinedName = Stream.of(leftTitle, rightTitle)
            .filter(Objects::nonNull)
            .collect(Collectors.joining(" "));

        return new TitleInfo(combinedName.isEmpty() ? null : combinedName, highestRarity);
    }

    private CategoryInfo getTopCategory(List<Object[]> categoryStats, Map<String, Long> categoryNameToIdMap) {
        if (categoryStats == null || categoryStats.isEmpty()) {
            return new CategoryInfo(null, null, 0L);
        }
        // 이미 경험치 순으로 정렬된 상태이므로 첫 번째 항목 반환
        Object[] top = categoryStats.get(0);
        String categoryName = (String) top[0];
        Long categoryId = categoryNameToIdMap.get(categoryName);
        Long exp = ((Number) top[2]).longValue();

        return new CategoryInfo(categoryName, categoryId, exp);
    }

    private record TitleInfo(String name, TitleRarity rarity) {}
    private record CategoryInfo(String name, Long id, Long exp) {}
}
