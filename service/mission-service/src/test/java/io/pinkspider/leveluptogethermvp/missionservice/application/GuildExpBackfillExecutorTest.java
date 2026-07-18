package io.pinkspider.leveluptogethermvp.missionservice.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.enums.GuildExpSourceType;
import io.pinkspider.global.facade.GuildQueryFacade;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.global.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GuildExpBackfillExecutor 테스트 (LUT-236)")
class GuildExpBackfillExecutorTest {

    @Mock
    private MissionExecutionRepository executionRepository;

    @Mock
    private DailyMissionInstanceRepository instanceRepository;

    @Mock
    private GuildQueryFacade guildQueryFacade;

    @InjectMocks
    private GuildExpBackfillExecutor executor;

    private static final String USER_ID = "user-1";

    private MissionExecution guildExecution(int expEarned, boolean alreadyGranted) {
        Mission mission = Mission.builder()
            .title("길드 미션")
            .creatorId(USER_ID)
            .status(MissionStatus.IN_PROGRESS)
            .type(MissionType.GUILD)
            .guildId("123")
            .build();
        setId(mission, 99L);
        MissionParticipant participant = MissionParticipant.builder()
            .mission(mission)
            .userId(USER_ID)
            .status(ParticipantStatus.COMPLETED)
            .build();
        MissionExecution execution = MissionExecution.builder()
            .participant(participant)
            .status(ExecutionStatus.COMPLETED)
            .isAutoCompleted(true)
            .guildExpGranted(alreadyGranted)
            .expEarned(expEarned)
            .build();
        setId(execution, 1L);
        return execution;
    }

    @Test
    @DisplayName("미지급 자동종료 길드 미션에 길드 경험치를 소급하고 마커를 세팅한다")
    void grantsAndMarks() {
        MissionExecution execution = guildExecution(120, false);
        when(executionRepository.findById(1L)).thenReturn(Optional.of(execution));
        when(guildQueryFacade.guildExists(123L)).thenReturn(true);

        int granted = executor.grantForExecution(1L);

        assertThat(granted).isEqualTo(120);
        assertThat(execution.getGuildExpGranted()).isTrue();
        verify(guildQueryFacade).addGuildExperience(
            eq(123L), eq(120), eq(GuildExpSourceType.GUILD_MISSION_EXECUTION),
            eq(99L), eq(USER_ID), anyString());
    }

    @Test
    @DisplayName("이미 지급된 건은 건너뛴다 (멱등)")
    void skipsAlreadyGranted() {
        MissionExecution execution = guildExecution(120, true);
        when(executionRepository.findById(1L)).thenReturn(Optional.of(execution));

        int granted = executor.grantForExecution(1L);

        assertThat(granted).isEqualTo(0);
        verify(guildQueryFacade, never())
            .addGuildExperience(any(), anyInt(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("expEarned가 0 이하면 지급 없이 마커만 세팅한다")
    void zeroExp_marksOnly() {
        MissionExecution execution = guildExecution(0, false);
        when(executionRepository.findById(1L)).thenReturn(Optional.of(execution));

        int granted = executor.grantForExecution(1L);

        assertThat(granted).isEqualTo(0);
        assertThat(execution.getGuildExpGranted()).isTrue();
        verify(guildQueryFacade, never())
            .addGuildExperience(any(), anyInt(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("존재하지 않는 실행은 0을 반환한다")
    void missingExecution_returnsZero() {
        when(executionRepository.findById(1L)).thenReturn(Optional.empty());

        assertThat(executor.grantForExecution(1L)).isEqualTo(0);
        verify(guildQueryFacade, never())
            .addGuildExperience(any(), anyInt(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("비활성/해체 길드는 실패가 아니라 스킵하고 마커를 세팅한다 (재실행 대상 제외)")
    void inactiveGuild_skipsAndMarks() {
        MissionExecution execution = guildExecution(120, false);
        when(executionRepository.findById(1L)).thenReturn(Optional.of(execution));
        when(guildQueryFacade.guildExists(123L)).thenReturn(false); // 해체된 길드

        int granted = executor.grantForExecution(1L);

        assertThat(granted).isEqualTo(0);
        assertThat(execution.getGuildExpGranted()).isTrue(); // 마커 세팅 → 재실행 시 재시도/failed 집계 안 됨
        verify(guildQueryFacade, never())
            .addGuildExperience(any(), anyInt(), any(), any(), any(), any());
    }

    private DailyMissionInstance guildInstance(Integer expEarned, boolean alreadyGranted) {
        Mission mission = Mission.builder()
            .title("김부장미션")
            .creatorId(USER_ID)
            .status(MissionStatus.IN_PROGRESS)
            .type(MissionType.GUILD)
            .guildId("6")
            .isPinned(true)
            .build();
        setId(mission, 467L);
        MissionParticipant participant = MissionParticipant.builder()
            .mission(mission)
            .userId(USER_ID)
            .status(ParticipantStatus.IN_PROGRESS)
            .build();
        DailyMissionInstance instance = DailyMissionInstance.builder()
            .participant(participant)
            .instanceDate(LocalDate.now())
            .sequenceNumber(1)
            .missionTitle("김부장미션")
            .status(ExecutionStatus.COMPLETED)
            .isAutoCompleted(true)
            .guildExpGranted(alreadyGranted)
            .expEarned(expEarned)
            .build();
        setId(instance, 3978L);
        return instance;
    }

    @Test
    @DisplayName("고정 길드 미션 인스턴스에 길드 경험치를 소급하고 마커를 세팅한다")
    void grantForInstance_grantsAndMarks() {
        DailyMissionInstance instance = guildInstance(120, false);
        when(instanceRepository.findById(3978L)).thenReturn(Optional.of(instance));
        when(guildQueryFacade.guildExists(6L)).thenReturn(true);

        int granted = executor.grantForInstance(3978L);

        assertThat(granted).isEqualTo(120);
        assertThat(instance.getGuildExpGranted()).isTrue();
        verify(guildQueryFacade).addGuildExperience(
            eq(6L), eq(120), eq(GuildExpSourceType.GUILD_MISSION_EXECUTION),
            eq(467L), eq(USER_ID), anyString());
    }

    @Test
    @DisplayName("비활성/해체 길드 인스턴스는 실패가 아니라 스킵하고 마커를 세팅한다")
    void grantForInstance_inactiveGuild_skipsAndMarks() {
        DailyMissionInstance instance = guildInstance(120, false);
        when(instanceRepository.findById(3978L)).thenReturn(Optional.of(instance));
        when(guildQueryFacade.guildExists(6L)).thenReturn(false); // 해체된 길드

        int granted = executor.grantForInstance(3978L);

        assertThat(granted).isEqualTo(0);
        assertThat(instance.getGuildExpGranted()).isTrue();
        verify(guildQueryFacade, never())
            .addGuildExperience(any(), anyInt(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("이미 지급된 인스턴스는 건너뛴다 (멱등)")
    void grantForInstance_skipsAlreadyGranted() {
        DailyMissionInstance instance = guildInstance(120, true);
        when(instanceRepository.findById(3978L)).thenReturn(Optional.of(instance));

        int granted = executor.grantForInstance(3978L);

        assertThat(granted).isEqualTo(0);
        verify(guildQueryFacade, never())
            .addGuildExperience(any(), anyInt(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("expEarned가 null/0이면 지급 없이 마커만 세팅한다")
    void grantForInstance_zeroExp_marksOnly() {
        DailyMissionInstance instance = guildInstance(0, false);
        when(instanceRepository.findById(3978L)).thenReturn(Optional.of(instance));

        int granted = executor.grantForInstance(3978L);

        assertThat(granted).isEqualTo(0);
        assertThat(instance.getGuildExpGranted()).isTrue();
        verify(guildQueryFacade, never())
            .addGuildExperience(any(), anyInt(), any(), any(), any(), any());
    }
}
