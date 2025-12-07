package io.pinkspider.leveluptogethermvp.userservice.achievement.application;

import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.dto.RankingResponse;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.entity.UserStats;
import io.pinkspider.leveluptogethermvp.userservice.achievement.infrastructure.UserStatsRepository;
import io.pinkspider.leveluptogethermvp.userservice.achievement.infrastructure.UserTitleRepository;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserExperienceRepository;
import java.util.ArrayList;
import java.util.List;
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

        String equippedTitleName = userTitleRepository.findEquippedByUserId(userId)
            .map(ut -> ut.getTitle().getDisplayName())
            .orElse(null);

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

            // 장착된 칭호 조회
            String equippedTitleName = userTitleRepository.findEquippedByUserId(stats.getUserId())
                .map(ut -> ut.getTitle().getDisplayName())
                .orElse(null);

            responses.add(RankingResponse.from(stats, startRank++, null, userLevel, equippedTitleName));
        }

        return new PageImpl<>(responses, pageable, statsPage.getTotalElements());
    }
}
