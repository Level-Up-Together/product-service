package io.pinkspider.leveluptogethermvp.missionservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionParticipantResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionSource;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MissionParticipantServiceTest {

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private MissionParticipantRepository participantRepository;

    @Mock
    private MissionExecutionService missionExecutionService;

    @InjectMocks
    private MissionParticipantService missionParticipantService;

    private static final String TEST_USER_ID = "test-user-123";
    private static final String ADMIN_USER_ID = "admin-user-456";

    private void setMissionId(Mission mission, Long id) {
        try {
            java.lang.reflect.Field idField = Mission.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(mission, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setMissionSource(Mission mission, MissionSource source) {
        try {
            java.lang.reflect.Field sourceField = Mission.class.getDeclaredField("source");
            sourceField.setAccessible(true);
            sourceField.set(mission, source);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setParticipantId(MissionParticipant participant, Long id) {
        try {
            java.lang.reflect.Field idField = MissionParticipant.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(participant, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Mission createOpenPublicMission(Long id) {
        Mission mission = Mission.builder()
            .title("테스트 미션")
            .description("설명")
            .status(MissionStatus.OPEN)
            .visibility(MissionVisibility.PUBLIC)
            .type(MissionType.PERSONAL)
            .creatorId(ADMIN_USER_ID)
            .build();
        setMissionId(mission, id);
        setMissionSource(mission, MissionSource.SYSTEM);
        return mission;
    }

    @Nested
    @DisplayName("미션 참여 테스트")
    class JoinMissionTest {

        @Test
        @DisplayName("공개 미션에 참여하면 ACCEPTED 상태로 등록된다")
        void joinMission_publicMission_acceptedStatus() {
            // given
            Long missionId = 1L;
            Mission mission = createOpenPublicMission(missionId);

            when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
            when(participantRepository.existsActiveParticipation(missionId, TEST_USER_ID)).thenReturn(false);
            when(participantRepository.findByMissionIdAndUserId(missionId, TEST_USER_ID)).thenReturn(Optional.empty());
            when(participantRepository.save(any(MissionParticipant.class)))
                .thenAnswer(invocation -> {
                    MissionParticipant saved = invocation.getArgument(0);
                    setParticipantId(saved, 1L);
                    return saved;
                });

            // when
            MissionParticipantResponse response = missionParticipantService.joinMission(missionId, TEST_USER_ID);

            // then
            assertThat(response.getStatus()).isEqualTo(ParticipantStatus.ACCEPTED);
            verify(missionExecutionService).generateExecutionsForParticipant(any(MissionParticipant.class));
        }

        @Test
        @DisplayName("이미 참여 중인 미션에 다시 참여하면 예외가 발생한다")
        void joinMission_alreadyParticipating_throwsException() {
            // given
            Long missionId = 1L;
            Mission mission = createOpenPublicMission(missionId);

            when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
            when(participantRepository.existsActiveParticipation(missionId, TEST_USER_ID)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> missionParticipantService.joinMission(missionId, TEST_USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이미 참여한 미션입니다.");

            verify(participantRepository, never()).save(any());
        }

        @Test
        @DisplayName("철회한 미션에 다시 참여할 수 있다")
        void joinMission_afterWithdrawal_canRejoin() {
            // given
            Long missionId = 1L;
            Mission mission = createOpenPublicMission(missionId);

            // 이전에 참여했다가 탈퇴한 참여자 레코드
            MissionParticipant withdrawnParticipant = MissionParticipant.builder()
                .mission(mission)
                .userId(TEST_USER_ID)
                .status(ParticipantStatus.WITHDRAWN)
                .progress(0)
                .joinedAt(LocalDateTime.now().minusDays(7))
                .build();
            setParticipantId(withdrawnParticipant, 1L);

            // existsActiveParticipation은 WITHDRAWN 상태를 제외하므로 false 반환
            when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
            when(participantRepository.existsActiveParticipation(missionId, TEST_USER_ID)).thenReturn(false);
            when(participantRepository.findByMissionIdAndUserId(missionId, TEST_USER_ID))
                .thenReturn(Optional.of(withdrawnParticipant));
            when(participantRepository.save(any(MissionParticipant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            MissionParticipantResponse response = missionParticipantService.joinMission(missionId, TEST_USER_ID);

            // then
            assertThat(response.getStatus()).isEqualTo(ParticipantStatus.ACCEPTED);
            assertThat(response.getId()).isEqualTo(1L);  // 기존 ID 유지

            ArgumentCaptor<MissionParticipant> captor = ArgumentCaptor.forClass(MissionParticipant.class);
            verify(participantRepository).save(captor.capture());

            MissionParticipant savedParticipant = captor.getValue();
            assertThat(savedParticipant.getUserId()).isEqualTo(TEST_USER_ID);
            assertThat(savedParticipant.getStatus()).isEqualTo(ParticipantStatus.ACCEPTED);
            assertThat(savedParticipant.getProgress()).isEqualTo(0);  // 진행률 리셋
        }

        @Test
        @DisplayName("모집 중이 아닌 미션에 참여하면 예외가 발생한다")
        void joinMission_notOpenMission_throwsException() {
            // given
            Long missionId = 1L;
            Mission mission = Mission.builder()
                .title("진행중 미션")
                .description("설명")
                .status(MissionStatus.IN_PROGRESS)  // OPEN이 아님
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .creatorId(ADMIN_USER_ID)
                .build();
            setMissionId(mission, missionId);

            when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));

            // when & then
            assertThatThrownBy(() -> missionParticipantService.joinMission(missionId, TEST_USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("모집중인 미션에만 참여할 수 있습니다.");

            verify(participantRepository, never()).save(any());
        }

        @Test
        @DisplayName("비공개 미션에 참여하면 PENDING 상태로 등록된다")
        void joinMission_privateMission_pendingStatus() {
            // given
            Long missionId = 1L;
            Mission mission = Mission.builder()
                .title("비공개 미션")
                .description("설명")
                .status(MissionStatus.OPEN)
                .visibility(MissionVisibility.PRIVATE)
                .type(MissionType.PERSONAL)
                .creatorId(ADMIN_USER_ID)
                .build();
            setMissionId(mission, missionId);

            when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
            when(participantRepository.existsActiveParticipation(missionId, TEST_USER_ID)).thenReturn(false);
            when(participantRepository.findByMissionIdAndUserId(missionId, TEST_USER_ID)).thenReturn(Optional.empty());
            when(participantRepository.save(any(MissionParticipant.class)))
                .thenAnswer(invocation -> {
                    MissionParticipant saved = invocation.getArgument(0);
                    setParticipantId(saved, 1L);
                    return saved;
                });

            // when
            MissionParticipantResponse response = missionParticipantService.joinMission(missionId, TEST_USER_ID);

            // then
            assertThat(response.getStatus()).isEqualTo(ParticipantStatus.PENDING);
            // PENDING 상태에서는 실행 스케줄 생성하지 않음
            verify(missionExecutionService, never()).generateExecutionsForParticipant(any(MissionParticipant.class));
        }

        @Test
        @DisplayName("참여 인원이 초과된 미션에 참여하면 예외가 발생한다")
        void joinMission_maxParticipantsExceeded_throwsException() {
            // given
            Long missionId = 1L;
            Mission mission = Mission.builder()
                .title("인원 제한 미션")
                .description("설명")
                .status(MissionStatus.OPEN)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .creatorId(ADMIN_USER_ID)
                .maxParticipants(10)
                .build();
            setMissionId(mission, missionId);

            when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
            when(participantRepository.existsActiveParticipation(missionId, TEST_USER_ID)).thenReturn(false);
            when(participantRepository.countActiveParticipants(missionId)).thenReturn(10L);  // 이미 10명 참여

            // when & then
            assertThatThrownBy(() -> missionParticipantService.joinMission(missionId, TEST_USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("참여 인원이 초과되었습니다.");

            verify(participantRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("미션 철회 테스트")
    class WithdrawFromMissionTest {

        @Test
        @DisplayName("참여 중인 미션에서 철회할 수 있다")
        void withdrawFromMission_success() {
            // given
            Long missionId = 1L;
            Mission mission = createOpenPublicMission(missionId);

            MissionParticipant participant = MissionParticipant.builder()
                .mission(mission)
                .userId(TEST_USER_ID)
                .status(ParticipantStatus.ACCEPTED)
                .progress(0)
                .joinedAt(LocalDateTime.now())
                .build();
            setParticipantId(participant, 1L);

            when(participantRepository.findByMissionIdAndUserId(missionId, TEST_USER_ID))
                .thenReturn(Optional.of(participant));

            // when
            MissionParticipantResponse response = missionParticipantService.withdrawFromMission(missionId, TEST_USER_ID);

            // then
            assertThat(response.getStatus()).isEqualTo(ParticipantStatus.WITHDRAWN);
        }

        @Test
        @DisplayName("참여하지 않은 미션에서 철회하면 예외가 발생한다")
        void withdrawFromMission_notParticipating_throwsException() {
            // given
            Long missionId = 1L;

            when(participantRepository.findByMissionIdAndUserId(missionId, TEST_USER_ID))
                .thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> missionParticipantService.withdrawFromMission(missionId, TEST_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("미션 참여 정보를 찾을 수 없습니다.");
        }
    }
}
