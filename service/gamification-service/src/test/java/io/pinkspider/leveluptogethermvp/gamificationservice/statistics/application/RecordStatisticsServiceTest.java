package io.pinkspider.leveluptogethermvp.gamificationservice.statistics.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserExperience;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserStats;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.ExperienceHistoryRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserExperienceRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserStatsRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.statistics.domain.dto.RecordSummaryResponse;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecordStatisticsService 테스트 (LUT-454)")
class RecordStatisticsServiceTest {

    @Mock
    private UserStatsRepository userStatsRepository;

    @Mock
    private UserExperienceRepository userExperienceRepository;

    @Mock
    private ExperienceHistoryRepository experienceHistoryRepository;

    @InjectMocks
    private RecordStatisticsService recordStatisticsService;

    private static final String USER_ID = "user-1";

    private UserExperience experience(int level) {
        return UserExperience.builder().userId(USER_ID).currentLevel(level).build();
    }

    @Test
    @DisplayName("누적 달성·스트릭·현재 등급·등급 도달 이력을 조합한다")
    void buildsSummary() {
        UserStats stats = UserStats.builder()
            .userId(USER_ID)
            .totalMissionCompletions(120)
            .totalGuildMissionCompletions(30)
            .currentStreak(5)
            .maxStreak(21)
            .build();
        when(userStatsRepository.findByUserId(USER_ID)).thenReturn(Optional.of(stats));
        when(userExperienceRepository.findByUserId(USER_ID))
            .thenReturn(Optional.of(experience(15)));
        when(experienceHistoryRepository.findFirstReachedAt(USER_ID, 3))
            .thenReturn(LocalDateTime.of(2026, 1, 10, 0, 0));
        when(experienceHistoryRepository.findFirstReachedAt(USER_ID, 10))
            .thenReturn(LocalDateTime.of(2026, 5, 2, 0, 0));

        RecordSummaryResponse response = recordStatisticsService.getRecordSummary(USER_ID);

        assertThat(response.totalMissionCompletions()).isEqualTo(120);
        assertThat(response.totalGuildMissionCompletions()).isEqualTo(30);
        assertThat(response.currentStreak()).isEqualTo(5);
        assertThat(response.maxStreak()).isEqualTo(21);
        assertThat(response.currentLevel()).isEqualTo(15);
        assertThat(response.currentGrade()).isEqualTo(TitleRarity.RARE);
        assertThat(response.gradeHistory()).hasSize(2);
        assertThat(response.gradeHistory().get(0).grade()).isEqualTo(TitleRarity.UNCOMMON);
        assertThat(response.gradeHistory().get(1).grade()).isEqualTo(TitleRarity.RARE);
        // 레벨 15는 EPIC(200) 미도달 — 문턱 조회 자체를 하지 않는다
        verify(experienceHistoryRepository, never()).findFirstReachedAt(USER_ID, 200);
    }

    @Test
    @DisplayName("기록이 전혀 없는 유저는 0/COMMON/빈 이력으로 응답한다")
    void emptyUserDefaults() {
        when(userStatsRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(userExperienceRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        RecordSummaryResponse response = recordStatisticsService.getRecordSummary(USER_ID);

        assertThat(response.totalMissionCompletions()).isZero();
        assertThat(response.maxStreak()).isZero();
        assertThat(response.currentLevel()).isEqualTo(1);
        assertThat(response.currentGrade()).isEqualTo(TitleRarity.COMMON);
        assertThat(response.gradeHistory()).isEmpty();
        verify(experienceHistoryRepository, never()).findFirstReachedAt(anyString(), anyInt());
    }

    @Test
    @DisplayName("이력 시각이 없는 문턱(레벨 조정 등)은 건너뛴다")
    void skipsThresholdWithoutHistory() {
        when(userStatsRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(userExperienceRepository.findByUserId(USER_ID))
            .thenReturn(Optional.of(experience(12)));
        when(experienceHistoryRepository.findFirstReachedAt(USER_ID, 3)).thenReturn(null);
        when(experienceHistoryRepository.findFirstReachedAt(USER_ID, 10))
            .thenReturn(LocalDateTime.of(2026, 5, 2, 0, 0));

        RecordSummaryResponse response = recordStatisticsService.getRecordSummary(USER_ID);

        assertThat(response.gradeHistory()).hasSize(1);
        assertThat(response.gradeHistory().get(0).grade()).isEqualTo(TitleRarity.RARE);
    }
}
