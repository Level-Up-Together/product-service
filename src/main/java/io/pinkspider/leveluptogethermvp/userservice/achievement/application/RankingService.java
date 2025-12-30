package io.pinkspider.leveluptogethermvp.userservice.achievement.application;

import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.dto.LevelRankingResponse;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.dto.RankingResponse;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.entity.UserStats;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.entity.UserTitle;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.enums.TitlePosition;
import io.pinkspider.leveluptogethermvp.userservice.achievement.infrastructure.UserStatsRepository;
import io.pinkspider.leveluptogethermvp.userservice.achievement.infrastructure.UserTitleRepository;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.UserExperience;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.ExperienceHistoryRepository;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserExperienceRepository;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import java.util.ArrayList;
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
    private final UserRepository userRepository;
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

        String equippedTitleName = getCombinedEquippedTitleName(userId);

        return RankingResponse.from(stats, rank, null, userLevel, equippedTitleName);
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

            // 장착된 칭호 조회 (LEFT + RIGHT 조합)
            String equippedTitleName = getCombinedEquippedTitleName(stats.getUserId());

            responses.add(RankingResponse.from(stats, startRank++, null, userLevel, equippedTitleName));
        }

        return new PageImpl<>(responses, pageable, statsPage.getTotalElements());
    }

    /**
     * 내 레벨 랭킹 조회
     * (레벨 기준, 동일 레벨 시 총 경험치 기준)
     */
    public LevelRankingResponse getMyLevelRanking(String userId) {
        long totalUsers = userExperienceRepository.countTotalUsers();

        UserExperience userExp = userExperienceRepository.findByUserId(userId)
            .orElse(null);

        if (userExp == null) {
            return LevelRankingResponse.defaultResponse(userId, totalUsers);
        }

        long rank = userExperienceRepository.calculateLevelRank(
            userExp.getCurrentLevel(),
            userExp.getTotalExp()
        );

        return LevelRankingResponse.from(userExp, rank, totalUsers);
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

        // 사용자 정보 일괄 조회
        Map<String, Users> userMap = userRepository.findAllByIdIn(userIds).stream()
            .collect(Collectors.toMap(Users::getId, u -> u));

        // 장착된 칭호 일괄 조회 (LEFT + RIGHT 조합)
        Map<String, String> titleMap = userIds.stream()
            .collect(Collectors.toMap(
                id -> id,
                id -> getCombinedEquippedTitleName(id)
            ));

        List<LevelRankingResponse> responses = new ArrayList<>();
        long startRank = pageable.getOffset() + 1;

        for (UserExperience exp : expPage.getContent()) {
            Users user = userMap.get(exp.getUserId());
            String nickname = user != null ? user.getDisplayName() : null;
            String profileImageUrl = user != null ? user.getPicture() : null;
            String equippedTitle = titleMap.get(exp.getUserId());

            responses.add(LevelRankingResponse.from(
                exp, startRank++, totalUsers, nickname, profileImageUrl, equippedTitle
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

        // 사용자 정보 일괄 조회
        Map<String, Users> userMap = userRepository.findAllByIdIn(userIds).stream()
            .collect(Collectors.toMap(Users::getId, u -> u));

        // 사용자 경험치 정보 일괄 조회
        Map<String, UserExperience> expMap = userIds.stream()
            .collect(Collectors.toMap(
                id -> id,
                id -> userExperienceRepository.findByUserId(id).orElse(null)
            ));

        // 장착된 칭호 일괄 조회 (LEFT + RIGHT 조합)
        Map<String, String> titleMap = userIds.stream()
            .collect(Collectors.toMap(
                id -> id,
                id -> getCombinedEquippedTitleName(id)
            ));

        List<LevelRankingResponse> responses = new ArrayList<>();
        long startRank = pageable.getOffset() + 1;

        for (Object[] row : categoryRanking.getContent()) {
            String odayUserId = (String) row[0];
            Long categoryExp = ((Number) row[1]).longValue();

            Users user = userMap.get(odayUserId);
            UserExperience userExp = expMap.get(odayUserId);

            String nickname = user != null ? user.getDisplayName() : null;
            String profileImageUrl = user != null ? user.getPicture() : null;
            String equippedTitle = titleMap.get(odayUserId);

            // 카테고리별 랭킹은 카테고리 내 경험치를 기준으로 함
            if (userExp != null) {
                responses.add(LevelRankingResponse.builder()
                    .rank(startRank++)
                    .userId(odayUserId)
                    .nickname(nickname)
                    .profileImageUrl(profileImageUrl)
                    .equippedTitle(equippedTitle)
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
                    .equippedTitle(equippedTitle)
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
     * 사용자의 장착된 칭호 조합 이름 조회 (LEFT + RIGHT)
     * 예: "용감한 전사", "전설적인 챔피언"
     */
    private String getCombinedEquippedTitleName(String userId) {
        List<UserTitle> equippedTitles = userTitleRepository.findEquippedTitlesByUserId(userId);
        if (equippedTitles.isEmpty()) {
            return null;
        }

        String leftTitle = equippedTitles.stream()
            .filter(ut -> ut.getEquippedPosition() == TitlePosition.LEFT)
            .findFirst()
            .map(ut -> ut.getTitle().getDisplayName())
            .orElse(null);

        String rightTitle = equippedTitles.stream()
            .filter(ut -> ut.getEquippedPosition() == TitlePosition.RIGHT)
            .findFirst()
            .map(ut -> ut.getTitle().getDisplayName())
            .orElse(null);

        if (leftTitle == null && rightTitle == null) {
            return null;
        }
        if (leftTitle == null) {
            return rightTitle;
        }
        if (rightTitle == null) {
            return leftTitle;
        }
        return leftTitle + " " + rightTitle;
    }
}
