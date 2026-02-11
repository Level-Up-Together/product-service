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
import io.pinkspider.leveluptogethermvp.userservice.profile.application.UserProfileCacheService;
import io.pinkspider.leveluptogethermvp.userservice.profile.domain.dto.UserProfileCache;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final UserProfileCacheService userProfileCacheService;
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

        return RankingResponse.from(stats, rank, null, userLevel, titleInfo.name(), titleInfo.rarity(), titleInfo.colorCode());
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
        List<RankingResponse> responses = new ArrayList<>();
        long startRank = pageable.getOffset() + 1;

        for (UserStats stats : statsPage.getContent()) {
            // 유저 레벨 조회
            Integer userLevel = userExperienceRepository.findByUserId(stats.getUserId())
                .map(exp -> exp.getCurrentLevel())
                .orElse(1);

            // 장착된 칭호 조회 (LEFT + RIGHT 조합 및 등급)
            TitleInfo titleInfo = getCombinedEquippedTitleInfo(stats.getUserId());

            responses.add(RankingResponse.from(stats, startRank++, null, userLevel,
                titleInfo.name(), titleInfo.rarity(), titleInfo.colorCode()));
        }

        return new PageImpl<>(responses, pageable, statsPage.getTotalElements());
    }

    /**
     * 내 레벨 랭킹 조회
     * (레벨 기준, 동일 레벨 시 총 경험치 기준)
     */
    public LevelRankingResponse getMyLevelRanking(String userId) {
        long totalUsers = userExperienceRepository.countTotalUsers();

        // 사용자 정보 조회 (닉네임, 프로필 이미지)
        UserProfileCache profile = userProfileCacheService.getUserProfile(userId);
        String nickname = profile.nickname();
        String profileImageUrl = profile.picture();

        // 장착된 칭호 조회 (이름, 등급, 색상 코드)
        TitleInfo titleInfo = getCombinedEquippedTitleInfo(userId);

        UserExperience userExp = userExperienceRepository.findByUserId(userId)
            .orElse(null);

        if (userExp == null) {
            return LevelRankingResponse.defaultResponse(userId, totalUsers, nickname, profileImageUrl,
                titleInfo.name(), titleInfo.rarity(), titleInfo.colorCode());
        }

        long rank = userExperienceRepository.calculateLevelRank(
            userExp.getCurrentLevel(),
            userExp.getTotalExp()
        );

        return LevelRankingResponse.from(userExp, rank, totalUsers, nickname, profileImageUrl,
            titleInfo.name(), titleInfo.rarity(), titleInfo.colorCode());
    }

    /**
     * 전체 레벨 랭킹 조회
     * (레벨 내림차순, 동일 레벨 시 총 경험치 내림차순)
     */
    public Page<LevelRankingResponse> getLevelRanking(Pageable pageable) {
        long totalUsers = userExperienceRepository.countTotalUsers();
        Page<UserExperience> expPage = userExperienceRepository.findAllByOrderByCurrentLevelDescTotalExpDesc(pageable);

        // 사용자 ID 목록 추출
        List<String> userIds = expPage.getContent().stream()
            .map(UserExperience::getUserId)
            .collect(Collectors.toList());

        // 사용자 정보 일괄 조회 (캐시)
        Map<String, UserProfileCache> profileMap = userProfileCacheService.getUserProfiles(userIds);

        // 장착된 칭호 일괄 조회 (LEFT + RIGHT 조합, 등급, 색상 코드 포함)
        Map<String, TitleInfo> titleInfoMap = new HashMap<>();
        for (String id : userIds) {
            titleInfoMap.put(id, getCombinedEquippedTitleInfo(id));
        }

        List<LevelRankingResponse> responses = new ArrayList<>();
        long startRank = pageable.getOffset() + 1;

        for (UserExperience exp : expPage.getContent()) {
            UserProfileCache profile = profileMap.get(exp.getUserId());
            String nickname = profile != null ? profile.nickname() : null;
            String profileImageUrl = profile != null ? profile.picture() : null;
            TitleInfo titleInfo = titleInfoMap.get(exp.getUserId());

            responses.add(LevelRankingResponse.from(
                exp, startRank++, totalUsers, nickname, profileImageUrl,
                titleInfo.name(), titleInfo.rarity(), titleInfo.colorCode()
            ));
        }

        return new PageImpl<>(responses, pageable, expPage.getTotalElements());
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

        // 카테고리별 경험치 랭킹 조회
        Page<Object[]> categoryRanking = experienceHistoryRepository.findUserExpRankingByCategory(category, pageable);

        // 사용자 ID 목록 추출
        List<String> userIds = categoryRanking.getContent().stream()
            .map(row -> (String) row[0])
            .collect(Collectors.toList());

        // 사용자 정보 일괄 조회 (캐시)
        Map<String, UserProfileCache> profileMap = userProfileCacheService.getUserProfiles(userIds);

        // 사용자 경험치 정보 일괄 조회
        // Collectors.toMap()은 null 값을 허용하지 않으므로 HashMap 직접 사용
        Map<String, UserExperience> expMap = new HashMap<>();
        for (String id : userIds) {
            expMap.put(id, userExperienceRepository.findByUserId(id).orElse(null));
        }

        // 장착된 칭호 일괄 조회 (LEFT + RIGHT 조합, 등급, 색상 코드 포함)
        Map<String, TitleInfo> titleInfoMap = new HashMap<>();
        for (String id : userIds) {
            titleInfoMap.put(id, getCombinedEquippedTitleInfo(id));
        }

        List<LevelRankingResponse> responses = new ArrayList<>();
        long startRank = pageable.getOffset() + 1;

        for (Object[] row : categoryRanking.getContent()) {
            String odayUserId = (String) row[0];
            Long categoryExp = ((Number) row[1]).longValue();

            UserProfileCache profile = profileMap.get(odayUserId);
            UserExperience userExp = expMap.get(odayUserId);
            TitleInfo titleInfo = titleInfoMap.get(odayUserId);

            String nickname = profile != null ? profile.nickname() : null;
            String profileImageUrl = profile != null ? profile.picture() : null;

            // 카테고리별 랭킹은 카테고리 내 경험치를 기준으로 함
            if (userExp != null) {
                responses.add(LevelRankingResponse.builder()
                    .rank(startRank++)
                    .userId(odayUserId)
                    .nickname(nickname)
                    .profileImageUrl(profileImageUrl)
                    .equippedTitle(titleInfo.name())
                    .equippedTitleRarity(titleInfo.rarity())
                    .equippedTitleColorCode(titleInfo.colorCode())
                    .currentLevel(userExp.getCurrentLevel())
                    .currentExp(userExp.getCurrentExp())
                    .totalExp(categoryExp.intValue()) // 카테고리 내 총 경험치
                    .totalUsers(totalUsersInCategory)
                    .percentile(calculatePercentile(startRank - 1, totalUsersInCategory))
                    .build());
            } else {
                responses.add(LevelRankingResponse.builder()
                    .rank(startRank++)
                    .userId(odayUserId)
                    .nickname(nickname)
                    .profileImageUrl(profileImageUrl)
                    .equippedTitle(titleInfo.name())
                    .equippedTitleRarity(titleInfo.rarity())
                    .equippedTitleColorCode(titleInfo.colorCode())
                    .currentLevel(1)
                    .currentExp(0)
                    .totalExp(categoryExp.intValue())
                    .totalUsers(totalUsersInCategory)
                    .percentile(calculatePercentile(startRank - 1, totalUsersInCategory))
                    .build());
            }
        }

        return new PageImpl<>(responses, pageable, categoryRanking.getTotalElements());
    }

    private double calculatePercentile(long rank, long totalUsers) {
        if (totalUsers == 0) return 100.0;
        return Math.round((double) rank / totalUsers * 1000) / 10.0;
    }

    /**
     * 칭호 정보 (이름, 등급, 색상 코드)를 담는 레코드
     */
    public record TitleInfo(String name, TitleRarity rarity, String colorCode) {}

    /**
     * 사용자의 장착된 칭호 조합 정보 조회 (LEFT + RIGHT)
     * 예: "용감한 전사", 최고 등급, 색상 코드
     */
    private TitleInfo getCombinedEquippedTitleInfo(String userId) {
        List<UserTitle> equippedTitles = userTitleRepository.findEquippedTitlesByUserId(userId);
        if (equippedTitles.isEmpty()) {
            return new TitleInfo(null, null, null);
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

        // 두 칭호 중 더 높은 등급과 색상 코드 사용
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

        return new TitleInfo(combinedTitle, highestRarity, colorCode);
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
