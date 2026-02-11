package io.pinkspider.leveluptogethermvp.missionservice.application.strategy;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionInterval;
import io.pinkspider.global.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MissionExecutionStrategyResolverTest {

    @Mock
    private MissionParticipantRepository participantRepository;

    @Mock
    private RegularMissionExecutionStrategy regularStrategy;

    @Mock
    private PinnedMissionExecutionStrategy pinnedStrategy;

    @InjectMocks
    private MissionExecutionStrategyResolver strategyResolver;

    private String testUserId;

    @BeforeEach
    void setUp() {
        testUserId = "test-user-123";
    }

    @Test
    @DisplayName("고정 미션(isPinned=true)은 PinnedMissionExecutionStrategy를 반환한다")
    void resolve_pinnedMission_returnsPinnedStrategy() {
        // given
        Long missionId = 1L;
        Mission pinnedMission = Mission.builder()
            .title("고정 미션")
            .description("매일 반복")
            .status(MissionStatus.IN_PROGRESS)
            .visibility(MissionVisibility.PRIVATE)
            .type(MissionType.PERSONAL)
            .creatorId(testUserId)
            .missionInterval(MissionInterval.DAILY)
            .isPinned(true)
            .expPerCompletion(10)
            .build();
        setId(pinnedMission, missionId);

        MissionParticipant participant = MissionParticipant.builder()
            .mission(pinnedMission)
            .userId(testUserId)
            .status(ParticipantStatus.ACCEPTED)
            .build();
        setId(participant, 1L);

        when(participantRepository.findByMissionIdAndUserId(missionId, testUserId))
            .thenReturn(Optional.of(participant));

        // when
        MissionExecutionStrategy strategy = strategyResolver.resolve(missionId, testUserId);

        // then
        assertThat(strategy).isEqualTo(pinnedStrategy);
    }

    @Test
    @DisplayName("일반 미션(isPinned=false)은 RegularMissionExecutionStrategy를 반환한다")
    void resolve_regularMission_returnsRegularStrategy() {
        // given
        Long missionId = 2L;
        Mission regularMission = Mission.builder()
            .title("일반 미션")
            .description("일반 미션")
            .status(MissionStatus.IN_PROGRESS)
            .visibility(MissionVisibility.PUBLIC)
            .type(MissionType.PERSONAL)
            .creatorId(testUserId)
            .missionInterval(MissionInterval.DAILY)
            .isPinned(false)
            .expPerCompletion(10)
            .build();
        setId(regularMission, missionId);

        MissionParticipant participant = MissionParticipant.builder()
            .mission(regularMission)
            .userId(testUserId)
            .status(ParticipantStatus.ACCEPTED)
            .build();
        setId(participant, 2L);

        when(participantRepository.findByMissionIdAndUserId(missionId, testUserId))
            .thenReturn(Optional.of(participant));

        // when
        MissionExecutionStrategy strategy = strategyResolver.resolve(missionId, testUserId);

        // then
        assertThat(strategy).isEqualTo(regularStrategy);
    }

    @Test
    @DisplayName("참여자가 없으면 RegularMissionExecutionStrategy를 반환한다")
    void resolve_noParticipant_returnsRegularStrategy() {
        // given
        Long missionId = 3L;

        when(participantRepository.findByMissionIdAndUserId(missionId, testUserId))
            .thenReturn(Optional.empty());

        // when
        MissionExecutionStrategy strategy = strategyResolver.resolve(missionId, testUserId);

        // then
        assertThat(strategy).isEqualTo(regularStrategy);
    }

    @Test
    @DisplayName("isPinned가 null이면 RegularMissionExecutionStrategy를 반환한다")
    void resolve_isPinnedNull_returnsRegularStrategy() {
        // given
        Long missionId = 4L;
        Mission missionWithNullPinned = Mission.builder()
            .title("isPinned null 미션")
            .description("테스트")
            .status(MissionStatus.IN_PROGRESS)
            .visibility(MissionVisibility.PUBLIC)
            .type(MissionType.PERSONAL)
            .creatorId(testUserId)
            .missionInterval(MissionInterval.DAILY)
            .isPinned(null)
            .expPerCompletion(10)
            .build();
        setId(missionWithNullPinned, missionId);

        MissionParticipant participant = MissionParticipant.builder()
            .mission(missionWithNullPinned)
            .userId(testUserId)
            .status(ParticipantStatus.ACCEPTED)
            .build();
        setId(participant, 4L);

        when(participantRepository.findByMissionIdAndUserId(missionId, testUserId))
            .thenReturn(Optional.of(participant));

        // when
        MissionExecutionStrategy strategy = strategyResolver.resolve(missionId, testUserId);

        // then
        assertThat(strategy).isEqualTo(regularStrategy);
    }
}
