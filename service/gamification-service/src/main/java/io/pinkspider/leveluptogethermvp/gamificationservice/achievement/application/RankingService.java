package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application;

import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.LevelRankingResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.RankingResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserStats;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserTitle;
import io.pinkspider.global.enums.TitlePosition;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserStatsRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserTitleRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserExperience;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.ExperienceHistoryRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserExperienceRepository;
import io.pinkspider.global.facade.MissionQueryFacade;
import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.global.facade.dto.InProgressMissionDto;
import io.pinkspider.global.facade.dto.UserProfileInfo;
import io.pinkspider.leveluptogethermvp.metaservice.application.MissionCategoryService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingService {

    private final UserStatsRepository userStatsRepository;
    private final UserTitleRepository userTitleRepository;
    private final UserExperienceRepository userExperienceRepository;
    private final UserQueryFacade userQueryFacadeService;
    private final ExperienceHistoryRepository experienceHistoryRepository;
    // LUT-275: 랭킹 목록의 "현재 진행중인 미션" 표시용
    private final MissionQueryFacade missionQueryFacade;
    private final MissionCategoryService missionCategoryService;

    // 종합 랭킹 (랭킹 포인트 기준)
    public Page<RankingResponse> getOverallRanking(Pageable pageable) {
        return getOverallRanking(pageable, null);
    }

    public Page<RankingResponse> getOverallRanking(Pageable pageable, String locale) {
        Page<UserStats> statsPage = userStatsRepository.findAllByOrderByRankingPointsDesc(pageable);
        return convertToRankingResponse(statsPage, pageable, locale);
    }

    // 미션 완료 랭킹
    public Page<RankingResponse> getMissionCompletionRanking(Pageable pageable) {
        return getMissionCompletionRanking(pageable, null);
    }

    public Page<RankingResponse> getMissionCompletionRanking(Pageable pageable, String locale) {
        Page<UserStats> statsPage = userStatsRepository.findAllByOrderByTotalMissionCompletionsDesc(pageable);
        return convertToRankingResponse(statsPage, pageable, locale);
    }

    // 연속 활동 랭킹
    public Page<RankingResponse> getStreakRanking(Pageable pageable) {
        return getStreakRanking(pageable, null);
    }

    public Page<RankingResponse> getStreakRanking(Pageable pageable, String locale) {
        Page<UserStats> statsPage = userStatsRepository.findAllByOrderByMaxStreakDesc(pageable);
        return convertToRankingResponse(statsPage, pageable, locale);
    }

    // 업적 달성 랭킹
    public Page<RankingResponse> getAchievementRanking(Pageable pageable) {
        return getAchievementRanking(pageable, null);
    }

    public Page<RankingResponse> getAchievementRanking(Pageable pageable, String locale) {
        Page<UserStats> statsPage = userStatsRepository.findAllByOrderByTotalAchievementsCompletedDesc(pageable);
        return convertToRankingResponse(statsPage, pageable, locale);
    }

    // 내 랭킹 조회
    public RankingResponse getMyRanking(String userId) {
        return getMyRanking(userId, null);
    }

    public RankingResponse getMyRanking(String userId, String locale) {
        UserStats stats = userStatsRepository.findByUserId(userId)
            .orElse(null);

        if (stats == null) {
            return RankingResponse.builder()
                .rank(0L)
                .userId(userId)
                .rankingPoints(0L)
                .totalMissionCompletions(0)
                .maxStreak(0)
                .totalAchievementsCompleted(0)
                .build();
        }

        Long rank = userStatsRepository.findUserRank(userId);

        // 유저 추가 정보 조회
        Integer userLevel = userExperienceRepository.findByUserId(userId)
            .map(exp -> exp.getCurrentLevel())
            .orElse(1);

        TitleInfo titleInfo = getCombinedEquippedTitleInfo(userId, locale);

        return RankingResponse.from(stats, rank, null, userLevel, titleInfo.name(), titleInfo.rarity(), titleInfo.colorCode(),
            titleInfo.leftTitle(), titleInfo.leftRarity(), titleInfo.rightTitle(), titleInfo.rightRarity());
    }

    // 주변 랭킹 조회 (내 위아래 N명)
    public List<RankingResponse> getNearbyRanking(String userId, int range) {
        Long myRank = userStatsRepository.findUserRank(userId);
        if (myRank == null || myRank == 0) {
            return List.of();
        }

        int startRank = Math.max(1, myRank.intValue() - range);
        int size = range * 2 + 1;

        Page<UserStats> statsPage = userStatsRepository.findAllByOrderByRankingPointsDesc(
            Pageable.ofSize(size).withPage((startRank - 1) / size)
        );

        List<RankingResponse> result = new ArrayList<>();
        long currentRank = startRank;
        for (UserStats stats : statsPage.getContent()) {
            result.add(RankingResponse.from(stats, currentRank++));
        }

        return result;
    }

    private Page<RankingResponse> convertToRankingResponse(Page<UserStats> statsPage, Pageable pageable,
                                                            String locale) {
        // 탈퇴 사용자 필터링
        List<String> userIds = statsPage.getContent().stream()
            .map(UserStats::getUserId)
            .collect(Collectors.toList());
        Set<String> activeUserIds = new HashSet<>(userQueryFacadeService.getActiveUserIds(userIds));

        List<RankingResponse> responses = new ArrayList<>();
        long startRank = pageable.getOffset() + 1;

        for (UserStats stats : statsPage.getContent()) {
            if (!activeUserIds.contains(stats.getUserId())) {
                continue;
            }

            // 유저 레벨 조회
            Integer userLevel = userExperienceRepository.findByUserId(stats.getUserId())
                .map(exp -> exp.getCurrentLevel())
                .orElse(1);

            // 장착된 칭호 조회 (LEFT + RIGHT 조합 및 등급)
            TitleInfo titleInfo = getCombinedEquippedTitleInfo(stats.getUserId(), locale);

            responses.add(RankingResponse.from(stats, startRank++, null, userLevel,
                titleInfo.name(), titleInfo.rarity(), titleInfo.colorCode(),
                titleInfo.leftTitle(), titleInfo.leftRarity(), titleInfo.rightTitle(), titleInfo.rightRarity()));
        }

        return new PageImpl<>(responses, pageable, statsPage.getTotalElements());
    }

    /**
     * 내 레벨 랭킹 조회
     * (레벨 기준, 동일 레벨 시 총 경험치 기준)
     */
    public LevelRankingResponse getMyLevelRanking(String userId) {
        return getMyLevelRanking(userId, null);
    }

    public LevelRankingResponse getMyLevelRanking(String userId, String locale) {
        // 활성 사용자만 대상으로 랭킹 계산
        List<String> allUserIds = userExperienceRepository.findAll().stream()
            .map(UserExperience::getUserId)
            .collect(Collectors.toList());
        List<String> activeUserIds = userQueryFacadeService.getActiveUserIds(allUserIds);
        long totalUsers = activeUserIds.size();

        // 사용자 정보 조회 (닉네임, 프로필 이미지)
        UserProfileInfo profile = userQueryFacadeService.getUserProfile(userId);
        String nickname = profile.nickname();
        String profileImageUrl = profile.picture();

        // 장착된 칭호 조회 (이름, 등급, 색상 코드)
        TitleInfo titleInfo = getCombinedEquippedTitleInfo(userId, locale);

        UserExperience userExp = userExperienceRepository.findByUserId(userId)
            .orElse(null);

        if (userExp == null) {
            return LevelRankingResponse.defaultResponse(userId, totalUsers, nickname, profileImageUrl,
                titleInfo.name(), titleInfo.rarity(), titleInfo.colorCode(),
                titleInfo.leftTitle(), titleInfo.leftRarity(), titleInfo.rightTitle(), titleInfo.rightRarity());
        }

        long rank = userExperienceRepository.calculateLevelRankAmongActiveUsers(
            userExp.getCurrentLevel(),
            userExp.getTotalExp(),
            activeUserIds
        );

        return LevelRankingResponse.from(userExp, rank, totalUsers, nickname, profileImageUrl,
            titleInfo.name(), titleInfo.rarity(), titleInfo.colorCode(),
            titleInfo.leftTitle(), titleInfo.leftRarity(), titleInfo.rightTitle(), titleInfo.rightRarity());
    }

    /**
     * 전체 레벨 랭킹 조회
     * (레벨 내림차순, 동일 레벨 시 총 경험치 내림차순)
     */
    public Page<LevelRankingResponse> getLevelRanking(Pageable pageable) {
        return getLevelRanking(pageable, null);
    }

    public Page<LevelRankingResponse> getLevelRanking(Pageable pageable, String locale) {
        return getLevelRanking(pageable, locale, null);
    }

    public Page<LevelRankingResponse> getLevelRanking(Pageable pageable, String locale,
                                                       String viewerUserId) {
        // QA-206: 목록 순위를 내 랭킹(getMyLevelRanking: COUNT(나보다 위)+1)과 동일한 의미로 맞춘다.
        // 전체를 정렬해 로드 → 탈퇴자 제외 → 동점 공동순위(RANK) 부여 → 활성 기준 페이징.
        List<UserExperience> sorted =
            userExperienceRepository.findAllByOrderByCurrentLevelDescTotalExpDesc();

        List<String> allUserIds = sorted.stream()
            .map(UserExperience::getUserId)
            .collect(Collectors.toList());
        Set<String> activeUserIds = new HashSet<>(userQueryFacadeService.getActiveUserIds(allUserIds));

        List<UserExperience> active = sorted.stream()
            .filter(exp -> activeUserIds.contains(exp.getUserId()))
            .collect(Collectors.toList());
        long totalUsers = active.size();

        // 동점 공동순위: 직전 항목과 (레벨, 총경험치)가 동일하면 같은 순위
        long[] ranks = assignCompetitionRanks(active.size(), i ->
            Objects.equals(active.get(i).getCurrentLevel(), active.get(i - 1).getCurrentLevel())
                && Objects.equals(active.get(i).getTotalExp(), active.get(i - 1).getTotalExp()));

        // 활성 유저 기준 페이지 슬라이스 (탈퇴자에 의한 offset 오염 방지)
        int from = (int) Math.min(pageable.getOffset(), active.size());
        int to = (int) Math.min((long) from + pageable.getPageSize(), (long) active.size());
        List<UserExperience> slice = active.subList(from, to);

        List<String> sliceIds = slice.stream()
            .map(UserExperience::getUserId)
            .collect(Collectors.toList());
        Map<String, UserProfileInfo> profileMap = userQueryFacadeService.getUserProfiles(sliceIds);

        List<LevelRankingResponse> responses = new ArrayList<>();
        for (int i = 0; i < slice.size(); i++) {
            UserExperience exp = slice.get(i);
            UserProfileInfo profile = profileMap.get(exp.getUserId());
            String nickname = profile != null ? profile.nickname() : null;
            String profileImageUrl = profile != null ? profile.picture() : null;
            TitleInfo titleInfo = getCombinedEquippedTitleInfo(exp.getUserId(), locale);

            responses.add(LevelRankingResponse.from(
                exp, ranks[from + i], totalUsers, nickname, profileImageUrl,
                titleInfo.name(), titleInfo.rarity(), titleInfo.colorCode(),
                titleInfo.leftTitle(), titleInfo.leftRarity(), titleInfo.rightTitle(),
                titleInfo.rightRarity()));
        }

        enrichInProgressMissions(responses, viewerUserId, locale);
        return new PageImpl<>(responses, pageable, totalUsers);
    }

    /**
     * QA-206: 정렬된 목록에 동점 공동순위(경쟁 순위)를 부여한다. 동점은 같은 순위를 갖고
     * 다음 그룹은 그룹 시작 위치(1-based)로 점프한다 — 내 랭킹의 {@code COUNT(나보다 위)+1}과 동일한 의미.
     */
    private static long[] assignCompetitionRanks(int size, IntPredicate tiedWithPrevious) {
        long[] ranks = new long[size];
        long rank = 0;
        for (int i = 0; i < size; i++) {
            if (i == 0 || !tiedWithPrevious.test(i)) {
                rank = i + 1L;
            }
            ranks[i] = rank;
        }
        return ranks;
    }

    /**
     * 카테고리별 레벨 랭킹 조회
     * (해당 카테고리 미션에서 획득한 경험치 기준)
     */
    public Page<LevelRankingResponse> getLevelRankingByCategory(String category, Pageable pageable) {
        return getLevelRankingByCategory(category, pageable, null);
    }

    public Page<LevelRankingResponse> getLevelRankingByCategory(String category, Pageable pageable,
                                                                String locale) {
        return getLevelRankingByCategory(category, pageable, locale, null);
    }

    public Page<LevelRankingResponse> getLevelRankingByCategory(String category, Pageable pageable,
                                                                String locale, String viewerUserId) {
        log.info("카테고리별 레벨 랭킹 조회 요청: category={}", category);

        // 카테고리별 전체 사용자 수
        long totalUsersInCategory = experienceHistoryRepository.countUsersByCategory(category);

        if (totalUsersInCategory == 0) {
            log.info("해당 카테고리에 경험치 기록이 없습니다: category={}", category);
            return Page.empty(pageable);
        }

        // QA-206: 카테고리 목록도 내 랭킹과 동일 기준으로 — 전체 로드 → 탈퇴자 제외 → 동점 공동순위.
        List<Object[]> activeRows = activeCategoryRanking(category);
        long totalUsers = activeRows.size();

        long[] ranks = assignCompetitionRanks(activeRows.size(), i ->
            categoryExpOf(activeRows.get(i)) == categoryExpOf(activeRows.get(i - 1)));

        int from = (int) Math.min(pageable.getOffset(), activeRows.size());
        int to = (int) Math.min((long) from + pageable.getPageSize(), (long) activeRows.size());
        List<Object[]> slice = activeRows.subList(from, to);

        List<String> sliceIds = slice.stream()
            .map(row -> (String) row[0])
            .collect(Collectors.toList());
        Map<String, UserProfileInfo> profileMap = userQueryFacadeService.getUserProfiles(sliceIds);

        List<LevelRankingResponse> responses = new ArrayList<>();
        for (int i = 0; i < slice.size(); i++) {
            String userId = (String) slice.get(i)[0];
            long categoryExp = categoryExpOf(slice.get(i));
            long rank = ranks[from + i];

            UserProfileInfo profile = profileMap.get(userId);
            UserExperience userExp = userExperienceRepository.findByUserId(userId).orElse(null);
            TitleInfo titleInfo = getCombinedEquippedTitleInfo(userId, locale);

            responses.add(LevelRankingResponse.builder()
                .rank(rank)
                .userId(userId)
                .nickname(profile != null ? profile.nickname() : null)
                .profileImageUrl(profile != null ? profile.picture() : null)
                .equippedTitle(titleInfo.name())
                .equippedTitleRarity(titleInfo.rarity())
                .equippedTitleColorCode(titleInfo.colorCode())
                .leftTitle(titleInfo.leftTitle())
                .leftTitleRarity(titleInfo.leftRarity())
                .rightTitle(titleInfo.rightTitle())
                .rightTitleRarity(titleInfo.rightRarity())
                .currentLevel(userExp != null ? userExp.getCurrentLevel() : 1)
                .currentExp(userExp != null ? userExp.getCurrentExp() : 0)
                .totalExp((int) categoryExp) // 카테고리 내 총 경험치
                .totalUsers(totalUsers)
                .percentile(calculatePercentile(rank, totalUsers))
                .build());
        }

        enrichInProgressMissions(responses, viewerUserId, locale);
        return new PageImpl<>(responses, pageable, totalUsers);
    }

    /**
     * LUT-297: 실시간 랭킹 — 진행중 미션이 있는 유저를 오래 진행한 순(started_at 오름차순)으로 조회한다.
     * 순위는 경과시간 순번이며, 미션 공개범위 마스킹은 LUT-275 목록 규칙과 동일하다.
     */
    public Page<LevelRankingResponse> getRealtimeRanking(Pageable pageable, String locale,
                                                          String viewerUserId) {
        Map<String, InProgressMissionDto> missions = missionQueryFacade.findAllInProgressMissions();
        if (missions.isEmpty()) {
            return Page.empty(pageable);
        }
        Set<String> activeUserIds = new HashSet<>(userQueryFacadeService.getActiveUserIds(
            new ArrayList<>(missions.keySet())));

        List<Map.Entry<String, InProgressMissionDto>> sorted = missions.entrySet().stream()
            .filter(e -> activeUserIds.contains(e.getKey()))
            .sorted(Comparator.comparing(e -> e.getValue().startedAt(),
                Comparator.nullsLast(Comparator.naturalOrder())))
            .collect(Collectors.toList());
        long totalUsers = sorted.size();

        int from = (int) Math.min(pageable.getOffset(), sorted.size());
        int to = (int) Math.min((long) from + pageable.getPageSize(), (long) sorted.size());
        List<Map.Entry<String, InProgressMissionDto>> slice = sorted.subList(from, to);

        List<String> sliceIds = slice.stream().map(Map.Entry::getKey).collect(Collectors.toList());
        Map<String, UserProfileInfo> profileMap = userQueryFacadeService.getUserProfiles(sliceIds);

        List<LevelRankingResponse> responses = new ArrayList<>();
        for (int i = 0; i < slice.size(); i++) {
            String userId = slice.get(i).getKey();
            long rank = from + i + 1L;
            UserProfileInfo profile = profileMap.get(userId);
            UserExperience userExp = userExperienceRepository.findByUserId(userId).orElse(null);
            TitleInfo titleInfo = getCombinedEquippedTitleInfo(userId, locale);

            LevelRankingResponse response = LevelRankingResponse.builder()
                .rank(rank)
                .userId(userId)
                .nickname(profile != null ? profile.nickname() : null)
                .profileImageUrl(profile != null ? profile.picture() : null)
                .equippedTitle(titleInfo.name())
                .equippedTitleRarity(titleInfo.rarity())
                .equippedTitleColorCode(titleInfo.colorCode())
                .leftTitle(titleInfo.leftTitle())
                .leftTitleRarity(titleInfo.leftRarity())
                .rightTitle(titleInfo.rightTitle())
                .rightTitleRarity(titleInfo.rightRarity())
                .currentLevel(userExp != null ? userExp.getCurrentLevel() : 1)
                .currentExp(userExp != null ? userExp.getCurrentExp() : 0)
                .totalExp(userExp != null ? userExp.getTotalExp() : 0)
                .totalUsers(totalUsers)
                .percentile(calculatePercentile(rank, totalUsers))
                .build();
            response.setInProgressMission(
                toInProgressMissionInfo(slice.get(i).getValue(), userId, viewerUserId, locale));
            responses.add(response);
        }
        return new PageImpl<>(responses, pageable, totalUsers);
    }

    /** LUT-297: 주간 레벨 랭킹 — 이번주(타임존 기준 월요일 시작) 획득 경험치 순 */
    public Page<LevelRankingResponse> getWeeklyLevelRanking(Pageable pageable, String locale,
                                                             String viewerUserId, String timezone) {
        ZoneId zone = resolveZone(timezone);
        LocalDate weekStart = LocalDate.now(zone).with(DayOfWeek.MONDAY);
        return getPeriodLevelRanking(toUtc(weekStart, zone), toUtc(weekStart.plusWeeks(1), zone),
            pageable, locale, viewerUserId);
    }

    /** LUT-297: 월간 레벨 랭킹 — 이번달(타임존 기준 1일 시작) 획득 경험치 순 */
    public Page<LevelRankingResponse> getMonthlyLevelRanking(Pageable pageable, String locale,
                                                              String viewerUserId, String timezone) {
        ZoneId zone = resolveZone(timezone);
        LocalDate monthStart = LocalDate.now(zone).withDayOfMonth(1);
        return getPeriodLevelRanking(toUtc(monthStart, zone), toUtc(monthStart.plusMonths(1), zone),
            pageable, locale, viewerUserId);
    }

    /** LUT-316: 주간 내 랭킹 — 주간 목록(getWeeklyLevelRanking)과 동일 기준의 내 순위 */
    public LevelRankingResponse getMyWeeklyLevelRanking(String userId, String locale,
                                                         String timezone) {
        ZoneId zone = resolveZone(timezone);
        LocalDate weekStart = LocalDate.now(zone).with(DayOfWeek.MONDAY);
        return getMyPeriodLevelRanking(userId, toUtc(weekStart, zone),
            toUtc(weekStart.plusWeeks(1), zone), locale);
    }

    /** LUT-316: 월간 내 랭킹 — 월간 목록(getMonthlyLevelRanking)과 동일 기준의 내 순위 */
    public LevelRankingResponse getMyMonthlyLevelRanking(String userId, String locale,
                                                          String timezone) {
        ZoneId zone = resolveZone(timezone);
        LocalDate monthStart = LocalDate.now(zone).withDayOfMonth(1);
        return getMyPeriodLevelRanking(userId, toUtc(monthStart, zone),
            toUtc(monthStart.plusMonths(1), zone), locale);
    }

    /**
     * LUT-316: 기간 획득 경험치 기준 내 랭킹. 목록(getPeriodLevelRanking)과 동일 모수(활성 유저)와
     * 공동순위 규칙(COUNT(나보다 위)+1)을 적용하고, 기간 내 기록이 없으면 카테고리 내 랭킹(QA-206)과
     * 동일하게 최하위(전체 활성 + 1)로 표기한다.
     */
    private LevelRankingResponse getMyPeriodLevelRanking(String userId, LocalDateTime startUtc,
                                                          LocalDateTime endUtc, String locale) {
        UserProfileInfo profile = userQueryFacadeService.getUserProfile(userId);
        TitleInfo titleInfo = getCombinedEquippedTitleInfo(userId, locale);
        UserExperience userExp = userExperienceRepository.findByUserId(userId).orElse(null);

        List<Object[]> allRows =
            experienceHistoryRepository.findUserExpRankingByPeriod(startUtc, endUtc);
        Set<String> activeUserIds = new HashSet<>(userQueryFacadeService.getActiveUserIds(
            allRows.stream().map(row -> (String) row[0]).collect(Collectors.toList())));
        List<Object[]> active = allRows.stream()
            .filter(row -> activeUserIds.contains((String) row[0]))
            .collect(Collectors.toList());
        long totalUsers = active.size();

        Long myPeriodExp = active.stream()
            .filter(row -> userId.equals((String) row[0]))
            .map(RankingService::categoryExpOf)
            .findFirst()
            .orElse(null);

        long rank = myPeriodExp == null
            ? totalUsers + 1
            : active.stream().filter(row -> categoryExpOf(row) > myPeriodExp).count() + 1;

        return LevelRankingResponse.builder()
            .rank(rank)
            .userId(userId)
            .nickname(profile != null ? profile.nickname() : null)
            .profileImageUrl(profile != null ? profile.picture() : null)
            .equippedTitle(titleInfo.name())
            .equippedTitleRarity(titleInfo.rarity())
            .equippedTitleColorCode(titleInfo.colorCode())
            .leftTitle(titleInfo.leftTitle())
            .leftTitleRarity(titleInfo.leftRarity())
            .rightTitle(titleInfo.rightTitle())
            .rightTitleRarity(titleInfo.rightRarity())
            .currentLevel(userExp != null ? userExp.getCurrentLevel() : 1)
            .currentExp(userExp != null ? userExp.getCurrentExp() : 0)
            .totalExp(userExp != null ? userExp.getTotalExp() : 0)
            .periodExp(myPeriodExp != null ? myPeriodExp : 0L)
            .totalUsers(totalUsers)
            .percentile(myPeriodExp == null ? 100.0 : calculatePercentile(rank, totalUsers))
            .build();
    }

    /**
     * LUT-297: 기간 획득 경험치 랭킹 공통 로직. QA-206 공동순위 규칙(활성 유저만, 동점 동일 순위)을 준용하며
     * period_exp 에 정렬 기준(기간 획득 경험치), total_exp 에 우측 표기용 누적 총 경험치를 담는다.
     */
    private Page<LevelRankingResponse> getPeriodLevelRanking(LocalDateTime startUtc,
                                                              LocalDateTime endUtc, Pageable pageable,
                                                              String locale, String viewerUserId) {
        List<Object[]> allRows =
            experienceHistoryRepository.findUserExpRankingByPeriod(startUtc, endUtc);
        if (allRows.isEmpty()) {
            return Page.empty(pageable);
        }
        Set<String> activeUserIds = new HashSet<>(userQueryFacadeService.getActiveUserIds(
            allRows.stream().map(row -> (String) row[0]).collect(Collectors.toList())));
        List<Object[]> active = allRows.stream()
            .filter(row -> activeUserIds.contains((String) row[0]))
            .collect(Collectors.toList());
        long totalUsers = active.size();

        long[] ranks = assignCompetitionRanks(active.size(), i ->
            categoryExpOf(active.get(i)) == categoryExpOf(active.get(i - 1)));

        int from = (int) Math.min(pageable.getOffset(), active.size());
        int to = (int) Math.min((long) from + pageable.getPageSize(), (long) active.size());
        List<Object[]> slice = active.subList(from, to);

        List<String> sliceIds = slice.stream()
            .map(row -> (String) row[0])
            .collect(Collectors.toList());
        Map<String, UserProfileInfo> profileMap = userQueryFacadeService.getUserProfiles(sliceIds);

        List<LevelRankingResponse> responses = new ArrayList<>();
        for (int i = 0; i < slice.size(); i++) {
            String userId = (String) slice.get(i)[0];
            long periodExp = categoryExpOf(slice.get(i));
            long rank = ranks[from + i];

            UserProfileInfo profile = profileMap.get(userId);
            UserExperience userExp = userExperienceRepository.findByUserId(userId).orElse(null);
            TitleInfo titleInfo = getCombinedEquippedTitleInfo(userId, locale);

            responses.add(LevelRankingResponse.builder()
                .rank(rank)
                .userId(userId)
                .nickname(profile != null ? profile.nickname() : null)
                .profileImageUrl(profile != null ? profile.picture() : null)
                .equippedTitle(titleInfo.name())
                .equippedTitleRarity(titleInfo.rarity())
                .equippedTitleColorCode(titleInfo.colorCode())
                .leftTitle(titleInfo.leftTitle())
                .leftTitleRarity(titleInfo.leftRarity())
                .rightTitle(titleInfo.rightTitle())
                .rightTitleRarity(titleInfo.rightRarity())
                .currentLevel(userExp != null ? userExp.getCurrentLevel() : 1)
                .currentExp(userExp != null ? userExp.getCurrentExp() : 0)
                .totalExp(userExp != null ? userExp.getTotalExp() : 0)
                .periodExp(periodExp)
                .totalUsers(totalUsers)
                .percentile(calculatePercentile(rank, totalUsers))
                .build());
        }
        enrichInProgressMissions(responses, viewerUserId, locale);
        return new PageImpl<>(responses, pageable, totalUsers);
    }

    /** 잘못된 타임존 문자열은 기본값(Asia/Seoul)으로 폴백 — 무인증 공개 API의 클라이언트 헤더 방어 */
    private static ZoneId resolveZone(String timezone) {
        try {
            return ZoneId.of(timezone != null ? timezone : "Asia/Seoul");
        } catch (Exception e) {
            return ZoneId.of("Asia/Seoul");
        }
    }

    /** 타임존 기준 날짜의 자정을 UTC LocalDateTime 으로 변환 (created_at 은 UTC 저장) */
    private static LocalDateTime toUtc(LocalDate date, ZoneId zone) {
        return date.atStartOfDay(zone).withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime();
    }

    /**
     * LUT-275: 랭킹 목록에 각 유저의 실시간 진행중 미션을 채운다.
     *
     * <p>랭킹은 불특정 다수에게 노출되는 화면이므로 PUBLIC 미션(과 본인 행)만 상세를 노출하고,
     * 그 외 공개범위는 프로필(LUT-257)과 동일하게 미션 정보를 null 마스킹 + is_visible=false 로
     * 내린다 (프론트는 "비공개 미션 진행중" 표시 가능). 조회 실패 시 필드 없이 기존 응답을 유지한다.
     */
    private void enrichInProgressMissions(List<LevelRankingResponse> responses, String viewerUserId,
                                           String locale) {
        if (responses == null || responses.isEmpty()) {
            return;
        }
        try {
            List<String> userIds = responses.stream()
                .map(LevelRankingResponse::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            Map<String, InProgressMissionDto> missions =
                missionQueryFacade.findInProgressMissions(userIds);
            for (LevelRankingResponse response : responses) {
                InProgressMissionDto m = missions.get(response.getUserId());
                if (m == null) {
                    continue;
                }
                response.setInProgressMission(
                    toInProgressMissionInfo(m, response.getUserId(), viewerUserId, locale));
            }
        } catch (Exception e) {
            log.warn("랭킹 진행중 미션 조회 실패 - 필드 생략: {}", e.getMessage());
        }
    }

    /** LUT-275/LUT-297 공통: 공개범위 마스킹(PUBLIC 또는 본인만 노출)을 적용한 진행중 미션 정보 생성 */
    private LevelRankingResponse.InProgressMissionInfo toInProgressMissionInfo(
            InProgressMissionDto m, String ownerUserId, String viewerUserId, String locale) {
        boolean visible = "PUBLIC".equals(m.visibility())
            || (viewerUserId != null && viewerUserId.equals(ownerUserId));
        return LevelRankingResponse.InProgressMissionInfo.builder()
            .missionId(visible ? m.missionId() : null)
            .categoryId(visible ? m.categoryId() : null)
            .categoryName(visible
                ? localizeMissionCategoryName(m.categoryId(), m.categoryName(), locale)
                : null)
            .title(visible ? m.title() : null)
            .visibility(m.visibility())
            .isVisible(visible)
            .startedAt(m.startedAt())
            .build();
    }

    private String localizeMissionCategoryName(Long categoryId, String fallbackName, String locale) {
        if (categoryId == null || locale == null) {
            return fallbackName;
        }
        try {
            var category = missionCategoryService.getCategory(categoryId);
            return category != null ? category.getLocalizedName(locale) : fallbackName;
        } catch (Exception e) {
            return fallbackName;
        }
    }

    /**
     * QA-206: 카테고리별 내 랭킹 (목록과 동일 기준 — 활성 유저 중 공동순위).
     * 카테고리를 선택하면 목록은 카테고리 순위인데 내 랭킹은 전체였던 불일치를 해소한다.
     */
    public LevelRankingResponse getMyLevelRankingByCategory(String userId, String category) {
        return getMyLevelRankingByCategory(userId, category, null);
    }

    public LevelRankingResponse getMyLevelRankingByCategory(String userId, String category, String locale) {
        UserProfileInfo profile = userQueryFacadeService.getUserProfile(userId);
        TitleInfo titleInfo = getCombinedEquippedTitleInfo(userId, locale);
        UserExperience userExp = userExperienceRepository.findByUserId(userId).orElse(null);

        List<Object[]> activeRows = activeCategoryRanking(category);
        long totalUsers = activeRows.size();

        Long myCategoryExp = activeRows.stream()
            .filter(row -> userId.equals((String) row[0]))
            .map(RankingService::categoryExpOf)
            .findFirst()
            .orElse(null);

        // 해당 카테고리 경험치 기록이 없으면 최하위(전체 활성 + 1)로 표기
        long rank = myCategoryExp == null
            ? totalUsers + 1
            : activeRows.stream().filter(row -> categoryExpOf(row) > myCategoryExp).count() + 1;

        return LevelRankingResponse.builder()
            .rank(rank)
            .userId(userId)
            .nickname(profile != null ? profile.nickname() : null)
            .profileImageUrl(profile != null ? profile.picture() : null)
            .equippedTitle(titleInfo.name())
            .equippedTitleRarity(titleInfo.rarity())
            .equippedTitleColorCode(titleInfo.colorCode())
            .leftTitle(titleInfo.leftTitle())
            .leftTitleRarity(titleInfo.leftRarity())
            .rightTitle(titleInfo.rightTitle())
            .rightTitleRarity(titleInfo.rightRarity())
            .currentLevel(userExp != null ? userExp.getCurrentLevel() : 1)
            .currentExp(userExp != null ? userExp.getCurrentExp() : 0)
            .totalExp(myCategoryExp != null ? myCategoryExp.intValue() : 0)
            .totalUsers(totalUsers)
            .percentile(myCategoryExp == null ? 100.0 : calculatePercentile(rank, totalUsers))
            .build();
    }

    /** 카테고리 랭킹 행 {userId, categoryExp} 에서 카테고리 경험치를 추출한다. */
    private static long categoryExpOf(Object[] row) {
        return ((Number) row[1]).longValue();
    }

    /** 활성 유저만, 카테고리 경험치 내림차순으로 정렬된 {userId, categoryExp} 목록. */
    private List<Object[]> activeCategoryRanking(String category) {
        List<Object[]> allRows = experienceHistoryRepository
            .findUserExpRankingByCategory(category, Pageable.unpaged())
            .getContent();
        Set<String> activeUserIds = new HashSet<>(userQueryFacadeService.getActiveUserIds(
            allRows.stream().map(row -> (String) row[0]).collect(Collectors.toList())));
        return allRows.stream()
            .filter(row -> activeUserIds.contains((String) row[0]))
            .collect(Collectors.toList());
    }

    private double calculatePercentile(long rank, long totalUsers) {
        if (totalUsers == 0) return 100.0;
        return Math.round((double) rank / totalUsers * 1000) / 10.0;
    }

    /**
     * 칭호 정보 (이름, 등급, 색상 코드, 좌/우 개별 정보)를 담는 레코드
     */
    public record TitleInfo(String name, TitleRarity rarity, String colorCode,
                            String leftTitle, TitleRarity leftRarity,
                            String rightTitle, TitleRarity rightRarity) {}

    /**
     * 사용자의 장착된 칭호 조합 정보 조회 (LEFT + RIGHT)
     * 예: "용감한 전사", 최고 등급, 색상 코드
     */
    private TitleInfo getCombinedEquippedTitleInfo(String userId) {
        return getCombinedEquippedTitleInfo(userId, null);
    }

    /** LUT-255: locale에 맞는 칭호명으로 조합 */
    private TitleInfo getCombinedEquippedTitleInfo(String userId, String locale) {
        List<UserTitle> equippedTitles = userTitleRepository.findEquippedTitlesByUserId(userId);
        if (equippedTitles.isEmpty()) {
            return new TitleInfo(null, null, null, null, null, null, null);
        }

        UserTitle leftUserTitle = equippedTitles.stream()
            .filter(ut -> ut.getEquippedPosition() == TitlePosition.LEFT)
            .findFirst()
            .orElse(null);

        UserTitle rightUserTitle = equippedTitles.stream()
            .filter(ut -> ut.getEquippedPosition() == TitlePosition.RIGHT)
            .findFirst()
            .orElse(null);

        String leftTitle = leftUserTitle != null ? leftUserTitle.getTitle().getLocalizedName(locale) : null;
        String rightTitle = rightUserTitle != null ? rightUserTitle.getTitle().getLocalizedName(locale) : null;

        // 조합된 칭호 이름
        String combinedTitle;
        if (leftTitle == null && rightTitle == null) {
            combinedTitle = null;
        } else if (leftTitle == null) {
            combinedTitle = rightTitle;
        } else if (rightTitle == null) {
            combinedTitle = leftTitle;
        } else {
            combinedTitle = leftTitle + " " + rightTitle;
        }

        // 좌/우 개별 등급
        TitleRarity leftRarity = leftUserTitle != null ? leftUserTitle.getTitle().getRarity() : null;
        TitleRarity rightRarity = rightUserTitle != null ? rightUserTitle.getTitle().getRarity() : null;
        TitleRarity highestRarity = getHighestRarity(leftRarity, rightRarity);

        // 가장 높은 등급의 색상 코드 선택
        String colorCode = null;
        if (highestRarity != null) {
            if (leftRarity == highestRarity && leftUserTitle != null) {
                colorCode = leftUserTitle.getTitle().getColorCode();
            } else if (rightUserTitle != null) {
                colorCode = rightUserTitle.getTitle().getColorCode();
            }
        }

        return new TitleInfo(combinedTitle, highestRarity, colorCode,
            leftTitle, leftRarity, rightTitle, rightRarity);
    }

    /**
     * 기존 호환용 - 칭호 이름만 조회
     */
    private String getCombinedEquippedTitleName(String userId) {
        return getCombinedEquippedTitleInfo(userId).name();
    }

    /**
     * 두 등급 중 더 높은 등급 반환
     */
    private TitleRarity getHighestRarity(TitleRarity r1, TitleRarity r2) {
        if (r1 == null) return r2;
        if (r2 == null) return r1;
        return r1.ordinal() > r2.ordinal() ? r1 : r2;
    }
}
