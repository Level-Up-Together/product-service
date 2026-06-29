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
import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.global.facade.dto.UserProfileInfo;
import java.util.ArrayList;
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

    // 종합 랭킹 (랭킹 포인트 기준)
    public Page<RankingResponse> getOverallRanking(Pageable pageable) {
        Page<UserStats> statsPage = userStatsRepository.findAllByOrderByRankingPointsDesc(pageable);
        return convertToRankingResponse(statsPage, pageable);
    }

    // 미션 완료 랭킹
    public Page<RankingResponse> getMissionCompletionRanking(Pageable pageable) {
        Page<UserStats> statsPage = userStatsRepository.findAllByOrderByTotalMissionCompletionsDesc(pageable);
        return convertToRankingResponse(statsPage, pageable);
    }

    // 연속 활동 랭킹
    public Page<RankingResponse> getStreakRanking(Pageable pageable) {
        Page<UserStats> statsPage = userStatsRepository.findAllByOrderByMaxStreakDesc(pageable);
        return convertToRankingResponse(statsPage, pageable);
    }

    // 업적 달성 랭킹
    public Page<RankingResponse> getAchievementRanking(Pageable pageable) {
        Page<UserStats> statsPage = userStatsRepository.findAllByOrderByTotalAchievementsCompletedDesc(pageable);
        return convertToRankingResponse(statsPage, pageable);
    }

    // 내 랭킹 조회
    public RankingResponse getMyRanking(String userId) {
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

        TitleInfo titleInfo = getCombinedEquippedTitleInfo(userId);

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

    private Page<RankingResponse> convertToRankingResponse(Page<UserStats> statsPage, Pageable pageable) {
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
            TitleInfo titleInfo = getCombinedEquippedTitleInfo(stats.getUserId());

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
        TitleInfo titleInfo = getCombinedEquippedTitleInfo(userId);

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
            TitleInfo titleInfo = getCombinedEquippedTitleInfo(exp.getUserId());

            responses.add(LevelRankingResponse.from(
                exp, ranks[from + i], totalUsers, nickname, profileImageUrl,
                titleInfo.name(), titleInfo.rarity(), titleInfo.colorCode(),
                titleInfo.leftTitle(), titleInfo.leftRarity(), titleInfo.rightTitle(),
                titleInfo.rightRarity()));
        }

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
            TitleInfo titleInfo = getCombinedEquippedTitleInfo(userId);

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

        return new PageImpl<>(responses, pageable, totalUsers);
    }

    /**
     * QA-206: 카테고리별 내 랭킹 (목록과 동일 기준 — 활성 유저 중 공동순위).
     * 카테고리를 선택하면 목록은 카테고리 순위인데 내 랭킹은 전체였던 불일치를 해소한다.
     */
    public LevelRankingResponse getMyLevelRankingByCategory(String userId, String category) {
        UserProfileInfo profile = userQueryFacadeService.getUserProfile(userId);
        TitleInfo titleInfo = getCombinedEquippedTitleInfo(userId);
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

        String leftTitle = leftUserTitle != null ? leftUserTitle.getTitle().getDisplayName() : null;
        String rightTitle = rightUserTitle != null ? rightUserTitle.getTitle().getDisplayName() : null;

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
