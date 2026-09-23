package io.pinkspider.leveluptogethermvp.missionservice.event.listener;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.enums.MissionStatus;
import io.pinkspider.global.event.GuildJoinedEvent;
import io.pinkspider.global.event.GuildMemberRemovedEvent;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionParticipantService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GuildMissionEventListener 테스트")
class GuildMissionEventListenerTest {

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private MissionParticipantRepository participantRepository;

    @Mock
    private MissionParticipantService participantService;

    @InjectMocks
    private GuildMissionEventListener listener;

    private static final String USER_ID = "user-1";
    private static final Long GUILD_ID = 10L;
    private static final String GUILD_NAME = "테스트 길드";

    private Mission guildMission1;
    private Mission guildMission2;

    @BeforeEach
    void setUp() {
        guildMission1 = Mission.builder()
            .title("길드 미션 1")
            .description("길드 미션 1 설명")
            .creatorId("creator-1")
            .status(MissionStatus.OPEN)
            .visibility(MissionVisibility.PRIVATE)
            .type(MissionType.GUILD)
            .guildId(String.valueOf(GUILD_ID))
            .guildName(GUILD_NAME)
            .categoryId(1L)
            .categoryName("운동")
            .expPerCompletion(50)
            .build();
        setId(guildMission1, 1L);

        guildMission2 = Mission.builder()
            .title("길드 미션 2")
            .description("길드 미션 2 설명")
            .creatorId("creator-1")
            .status(MissionStatus.IN_PROGRESS)
            .visibility(MissionVisibility.PRIVATE)
            .type(MissionType.GUILD)
            .guildId(String.valueOf(GUILD_ID))
            .guildName(GUILD_NAME)
            .categoryId(1L)
            .categoryName("운동")
            .expPerCompletion(30)
            .build();
        setId(guildMission2, 2L);
    }

    @Nested
    @DisplayName("handleGuildMemberJoined 테스트")
    class HandleGuildMemberJoinedTest {

        @Test
        @DisplayName("길드 가입 시 진행중 고정 길드 미션에 자동 참여한다 (LUT-518)")
        void enrollsInActiveGuildMissions() {
            // given
            GuildJoinedEvent event = new GuildJoinedEvent(USER_ID, GUILD_ID, GUILD_NAME);
            when(missionRepository.findActivePinnedGuildMissions(
                eq(String.valueOf(GUILD_ID)),
                eq(List.of(MissionStatus.OPEN, MissionStatus.IN_PROGRESS))
            )).thenReturn(List.of(guildMission1, guildMission2));

            // when
            listener.handleGuildMemberJoined(event);

            // then
            verify(participantService).addGuildMemberAsParticipant(guildMission1, USER_ID);
            verify(participantService).addGuildMemberAsParticipant(guildMission2, USER_ID);
        }

        @Test
        @DisplayName("활성 길드 미션이 없으면 아무 동작하지 않는다")
        void doesNothingWhenNoActiveMissions() {
            // given
            GuildJoinedEvent event = new GuildJoinedEvent(USER_ID, GUILD_ID, GUILD_NAME);
            when(missionRepository.findActivePinnedGuildMissions(any(), any())).thenReturn(List.of());

            // when
            listener.handleGuildMemberJoined(event);

            // then
            verify(participantService, never()).addGuildMemberAsParticipant(any(), any());
        }

        @Test
        @DisplayName("일부 미션 참여 실패해도 나머지 계속 처리한다")
        void continuesOnPartialFailure() {
            // given
            GuildJoinedEvent event = new GuildJoinedEvent(USER_ID, GUILD_ID, GUILD_NAME);
            when(missionRepository.findActivePinnedGuildMissions(any(), any()))
                .thenReturn(List.of(guildMission1, guildMission2));
            // 첫 번째 미션은 실패
            org.mockito.Mockito.doThrow(new RuntimeException("DB error"))
                .when(participantService).addGuildMemberAsParticipant(guildMission1, USER_ID);

            // when
            listener.handleGuildMemberJoined(event);

            // then - 두 번째 미션도 처리됨
            verify(participantService).addGuildMemberAsParticipant(guildMission2, USER_ID);
        }
    }

    @Nested
    @DisplayName("handleGuildMemberRemoved 테스트")
    class HandleGuildMemberRemovedTest {

        @Test
        @DisplayName("길드 탈퇴 시 활성 참여를 WITHDRAWN 처리한다")
        void withdrawsActiveParticipations() {
            // given
            GuildMemberRemovedEvent event = new GuildMemberRemovedEvent(USER_ID, GUILD_ID);

            MissionParticipant participant1 = MissionParticipant.builder()
                .mission(guildMission1)
                .userId(USER_ID)
                .status(ParticipantStatus.ACCEPTED)
                .build();
            setId(participant1, 100L);

            MissionParticipant participant2 = MissionParticipant.builder()
                .mission(guildMission2)
                .userId(USER_ID)
                .status(ParticipantStatus.ACCEPTED)
                .build();
            setId(participant2, 101L);

            when(participantRepository.findActiveGuildMissionParticipations(USER_ID, String.valueOf(GUILD_ID)))
                .thenReturn(List.of(participant1, participant2));

            // when
            listener.handleGuildMemberRemoved(event);

            // then
            assertThat(participant1.getStatus()).isEqualTo(ParticipantStatus.WITHDRAWN);
            assertThat(participant2.getStatus()).isEqualTo(ParticipantStatus.WITHDRAWN);
        }

        @Test
        @DisplayName("정리할 참여가 없으면 아무 동작하지 않는다")
        void doesNothingWhenNoParticipations() {
            // given
            GuildMemberRemovedEvent event = new GuildMemberRemovedEvent(USER_ID, GUILD_ID);
            when(participantRepository.findActiveGuildMissionParticipations(any(), any()))
                .thenReturn(List.of());

            // when
            listener.handleGuildMemberRemoved(event);

            // then - no exception
        }
    }
}
