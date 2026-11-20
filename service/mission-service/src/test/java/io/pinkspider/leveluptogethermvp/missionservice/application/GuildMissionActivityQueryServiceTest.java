package io.pinkspider.leveluptogethermvp.missionservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GuildMissionActivityQueryService — 길드 홈 '방금 길드 미션' (LUT-853)")
class GuildMissionActivityQueryServiceTest {

    @Mock private MissionExecutionRepository executionRepository;

    @Mock private DailyMissionInstanceRepository dailyMissionInstanceRepository;

    @InjectMocks private GuildMissionActivityQueryService service;

    private final LocalDateTime since = LocalDateTime.of(2026, 11, 20, 0, 10);

    @Test
    @DisplayName("일반 미션과 고정 미션의 활동을 합쳐 길드 ID 집합으로 돌려준다")
    void unionOfRegularAndPinned() {
        when(executionRepository.findGuildIdsWithActivitySince(since))
                .thenReturn(List.of("3", "7"));
        when(dailyMissionInstanceRepository.findGuildIdsWithActivitySince(
                        since, LocalDate.of(2026, 11, 19)))
                .thenReturn(List.of("7", "12"));

        Set<Long> result = service.findGuildIdsWithRecentActivity(since);

        assertThat(result).containsExactlyInAnyOrder(3L, 7L, 12L);
    }

    @Test
    @DisplayName("고정 미션은 since 의 전날 인스턴스부터 본다 — 자정을 넘겨 완료한 어제 인스턴스까지")
    void pinnedLooksFromPreviousDate() {
        when(executionRepository.findGuildIdsWithActivitySince(since)).thenReturn(List.of());
        when(dailyMissionInstanceRepository.findGuildIdsWithActivitySince(
                        since, LocalDate.of(2026, 11, 19)))
                .thenReturn(List.of());

        assertThat(service.findGuildIdsWithRecentActivity(since)).isEmpty();

        verify(dailyMissionInstanceRepository)
                .findGuildIdsWithActivitySince(since, LocalDate.of(2026, 11, 19));
    }

    @Test
    @DisplayName("guild_id 가 비었거나 숫자가 아니면 건너뛰고, 앞뒤 공백은 떼고 읽는다")
    void skipsInvalidGuildIds() {
        when(executionRepository.findGuildIdsWithActivitySince(since))
                .thenReturn(Arrays.asList(null, "", "  ", "abc", " 5 "));
        when(dailyMissionInstanceRepository.findGuildIdsWithActivitySince(
                        since, LocalDate.of(2026, 11, 19)))
                .thenReturn(List.of("9"));

        Set<Long> result = service.findGuildIdsWithRecentActivity(since);

        assertThat(result).containsExactlyInAnyOrder(5L, 9L);
    }
}
