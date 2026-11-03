package io.pinkspider.leveluptogethermvp.missionservice.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/** LUT-539: 다음 고정 미션 인스턴스 insert 를 별도 트랜잭션(REQUIRES_NEW)에서 수행하는 전용 빈. */
@ExtendWith(MockitoExtension.class)
class DailyMissionInstanceCreatorTest {

    @Mock private DailyMissionInstanceRepository instanceRepository;
    @Mock private MissionParticipantRepository participantRepository;

    @InjectMocks private DailyMissionInstanceCreator creator;

    private MissionParticipant participant;
    private final LocalDate date = LocalDate.of(2026, 10, 13);

    @BeforeEach
    void setUp() {
        Mission mission =
                Mission.builder()
                        .title("매일 운동")
                        .creatorId("user-1")
                        .status(MissionStatus.IN_PROGRESS)
                        .visibility(MissionVisibility.PRIVATE)
                        .type(MissionType.PERSONAL)
                        .isPinned(true)
                        .targetDurationMinutes(30)
                        .build();
        setId(mission, 1L);
        participant =
                MissionParticipant.builder()
                        .mission(mission)
                        .userId("user-1")
                        .status(ParticipantStatus.IN_PROGRESS)
                        .progress(0)
                        .build();
        setId(participant, 7L);
    }

    @Test
    @DisplayName("참여자를 id 로 내부 트랜잭션에서 로드해 지정 sequence 의 인스턴스를 flush 하며 생성하고 id 를 돌려준다")
    void create_loadsParticipantAndFlushes() {
        when(participantRepository.findById(7L)).thenReturn(Optional.of(participant));
        when(instanceRepository.saveAndFlush(any(DailyMissionInstance.class)))
                .thenAnswer(
                        inv -> {
                            DailyMissionInstance i = inv.getArgument(0);
                            setId(i, 99L);
                            return i;
                        });

        Long id = creator.create(7L, date, 2);

        assertThat(id).isEqualTo(99L);
        ArgumentCaptor<DailyMissionInstance> captor =
                ArgumentCaptor.forClass(DailyMissionInstance.class);
        verify(instanceRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getSequenceNumber()).isEqualTo(2);
        assertThat(captor.getValue().getInstanceDate()).isEqualTo(date);
        assertThat(captor.getValue().getTargetDurationMinutes()).isEqualTo(30);
    }

    @Test
    @DisplayName("sequence 충돌 예외는 삼키지 않고 호출자(스텝)에게 전파한다 — 스텝이 재조회·재시도")
    void create_propagatesDuplicateKey() {
        when(participantRepository.findById(7L)).thenReturn(Optional.of(participant));
        when(instanceRepository.saveAndFlush(any(DailyMissionInstance.class)))
                .thenThrow(new DataIntegrityViolationException("uk sequence"));

        assertThatThrownBy(() -> creator.create(7L, date, 2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("참여자가 없으면 IllegalStateException")
    void create_participantMissing_throws() {
        when(participantRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> creator.create(7L, date, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("7");
    }
}
