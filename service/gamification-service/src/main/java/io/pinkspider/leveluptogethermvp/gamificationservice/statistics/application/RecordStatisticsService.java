package io.pinkspider.leveluptogethermvp.gamificationservice.statistics.application;

import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.global.policy.LevelRarityPolicy;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserExperience;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserStats;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.ExperienceHistoryRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserExperienceRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserStatsRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.statistics.domain.dto.RecordSummaryResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.statistics.domain.dto.RecordSummaryResponse.GradeReached;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-454: 전체 기간 기록 요약 — 누적 달성·스트릭은 UserStats(수행 시 유지)를, 등급 도달 이력은
 * experience_history 의 레벨 문턱 최초 통과 시각을 사용한다.
 *
 * <p>월간 리포트(미션 상세 집계)는 missionservice 의 MissionStatisticsService 가 담당 — 여기는
 * gamification_db 로컬 데이터만 조합한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
public class RecordStatisticsService {

    /** 등급별 도달 레벨 문턱 (COMMON 은 시작 등급이라 이력에 넣지 않는다) */
    private static final Map<TitleRarity, Integer> GRADE_THRESHOLDS =
            Map.of(
                    TitleRarity.UNCOMMON, 3,
                    TitleRarity.RARE, 10,
                    TitleRarity.EPIC, 200,
                    TitleRarity.LEGENDARY, 500,
                    TitleRarity.MYTHIC, 900);

    private static final List<TitleRarity> GRADE_ORDER =
            List.of(
                    TitleRarity.UNCOMMON,
                    TitleRarity.RARE,
                    TitleRarity.EPIC,
                    TitleRarity.LEGENDARY,
                    TitleRarity.MYTHIC);

    private final UserStatsRepository userStatsRepository;
    private final UserExperienceRepository userExperienceRepository;
    private final ExperienceHistoryRepository experienceHistoryRepository;

    public RecordSummaryResponse getRecordSummary(String userId) {
        UserStats stats = userStatsRepository.findByUserId(userId).orElse(null);
        int currentLevel =
                userExperienceRepository
                        .findByUserId(userId)
                        .map(UserExperience::getCurrentLevel)
                        .orElse(1);

        List<GradeReached> gradeHistory = new ArrayList<>();
        for (TitleRarity grade : GRADE_ORDER) {
            int threshold = GRADE_THRESHOLDS.get(grade);
            if (currentLevel < threshold) {
                break; // 현재 등급 미만만 조회 — 미도달 등급은 이력에 없다
            }
            LocalDateTime reachedAt =
                    experienceHistoryRepository.findFirstReachedAt(userId, threshold);
            if (reachedAt != null) {
                gradeHistory.add(new GradeReached(grade, reachedAt));
            }
        }

        return new RecordSummaryResponse(
                stats != null ? nvl(stats.getTotalMissionCompletions()) : 0,
                stats != null ? nvl(stats.getTotalGuildMissionCompletions()) : 0,
                stats != null ? nvl(stats.getCurrentStreak()) : 0,
                stats != null ? nvl(stats.getMaxStreak()) : 0,
                currentLevel,
                LevelRarityPolicy.fromLevel(currentLevel),
                gradeHistory);
    }

    private static int nvl(Integer value) {
        return value != null ? value : 0;
    }
}
