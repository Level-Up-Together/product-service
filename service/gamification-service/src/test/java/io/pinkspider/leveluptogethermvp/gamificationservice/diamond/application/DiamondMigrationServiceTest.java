package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.facade.MissionQueryFacade;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.DiamondMigrationResultResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserExperience;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.DiamondType;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.DiamondHistoryRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserExperienceRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class DiamondMigrationServiceTest {

    @Mock
    private UserExperienceRepository userExperienceRepository;

    @Mock
    private DiamondHistoryRepository diamondHistoryRepository;

    @Mock
    private DiamondService diamondService;

    @Mock
    private MissionQueryFacade missionQueryFacade;

    @InjectMocks
    private DiamondMigrationService migrationService;

    private UserExperience userExp(String userId, int level) {
        return UserExperience.builder()
            .userId(userId)
            .currentLevel(level)
            .currentExp(0)
            .totalExp(0)
            .build();
    }

    @Test
    @DisplayName("전체 유저를 순회하며 레벨 기반 + 미션북 기반 다이아를 소급 지급한다")
    void migratesLevelAndMissionBookDiamonds() {
        UserExperience user1 = userExp("user-1", 10);
        when(userExperienceRepository.findAll(any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(user1)));

        when(diamondService.awardLevelUpDiamondsAggregated("user-1", 10)).thenReturn(9);
        when(missionQueryFacade.findClearedMissionBookTemplateIds("user-1"))
            .thenReturn(Set.of(101L, 102L));
        when(diamondHistoryRepository.findAwardedSourceIds(
            eq("user-1"), eq(DiamondType.MISSION_BOOK), anySet()))
            .thenReturn(List.of(101L)); // 101 은 이미 지급됨
        when(missionQueryFacade.getMissionBookTemplateTitles(Set.of(102L)))
            .thenReturn(Map.of(102L, "아침 명상"));
        when(diamondService.awardMissionBookDiamond("user-1", 102L, "아침 명상")).thenReturn(true);

        DiamondMigrationResultResponse result = migrationService.migrate();

        assertThat(result.usersProcessed()).isEqualTo(1);
        assertThat(result.levelUpDiamondsGranted()).isEqualTo(9);
        assertThat(result.missionBookDiamondsGranted()).isEqualTo(1);
        verify(diamondService, never()).awardMissionBookDiamond(eq("user-1"), eq(101L), any());
    }

    @Test
    @DisplayName("클리어한 미션북이 없으면 미션북 지급을 건너뛴다")
    void skipsMissionBookWhenNoneCleared() {
        when(userExperienceRepository.findAll(any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(userExp("user-1", 3))));
        when(diamondService.awardLevelUpDiamondsAggregated("user-1", 3)).thenReturn(2);
        when(missionQueryFacade.findClearedMissionBookTemplateIds("user-1")).thenReturn(Set.of());

        DiamondMigrationResultResponse result = migrationService.migrate();

        assertThat(result.missionBookDiamondsGranted()).isZero();
        verify(diamondService, never()).awardMissionBookDiamond(any(), any(), any());
    }

    @Test
    @DisplayName("유저 단위 실패는 건너뛰고 나머지 유저를 계속 처리한다")
    void continuesOnPerUserFailure() {
        UserExperience bad = userExp("bad-user", 5);
        UserExperience good = userExp("good-user", 2);
        when(userExperienceRepository.findAll(any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(bad, good)));

        when(diamondService.awardLevelUpDiamondsAggregated("bad-user", 5))
            .thenThrow(new RuntimeException("boom"));
        when(diamondService.awardLevelUpDiamondsAggregated("good-user", 2)).thenReturn(1);
        when(missionQueryFacade.findClearedMissionBookTemplateIds("good-user")).thenReturn(Set.of());

        DiamondMigrationResultResponse result = migrationService.migrate();

        assertThat(result.usersProcessed()).isEqualTo(1);
        assertThat(result.levelUpDiamondsGranted()).isEqualTo(1);
    }
}
