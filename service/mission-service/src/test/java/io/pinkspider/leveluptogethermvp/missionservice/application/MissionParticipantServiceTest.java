package io.pinkspider.leveluptogethermvp.missionservice.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;

import io.pinkspider.global.test.TestReflectionUtils;
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
import io.pinkspider.global.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionRepository;
import java.time.LocalDateTime;
import java.util.List;
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

    private Mission createOpenPublicMission(Long id) {
        Mission mission = Mission.builder()
            .title("테스트 미션")
            .description("설명")
            .status(MissionStatus.OPEN)
            .visibility(MissionVisibility.PUBLIC)
            .type(MissionType.PERSONAL)
            .creatorId(ADMIN_USER_ID)
            .build();
        setId(mission, id);
        TestReflectionUtils.setField(mission, "source", MissionSource.SYSTEM);
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

            when(missionRepository.findByIdAndIsDeletedFalse(missionId)).thenReturn(Optional.of(mission));
            when(participantRepository.existsActiveParticipation(missionId, TEST_USER_ID)).thenReturn(false);
            when(participantRepository.findByMissionIdAndUserId(missionId, TEST_USER_ID)).thenReturn(Optional.empty());
            when(participantRepository.save(any(MissionParticipant.class)))
                .thenAnswer(invocation -> {
                    MissionParticipant saved = invocation.getArgument(0);
                    setId(saved, 1L);
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

            when(missionRepository.findByIdAndIsDeletedFalse(missionId)).thenReturn(Optional.of(mission));
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
            setId(withdrawnParticipant, 1L);

            // existsActiveParticipation은 WITHDRAWN 상태를 제외하므로 false 반환
            when(missionRepository.findByIdAndIsDeletedFalse(missionId)).thenReturn(Optional.of(mission));
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
            setId(mission, missionId);

            when(missionRepository.findByIdAndIsDeletedFalse(missionId)).thenReturn(Optional.of(mission));

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
            setId(mission, missionId);

            when(missionRepository.findByIdAndIsDeletedFalse(missionId)).thenReturn(Optional.of(mission));
            when(participantRepository.existsActiveParticipation(missionId, TEST_USER_ID)).thenReturn(false);
            when(participantRepository.findByMissionIdAndUserId(missionId, TEST_USER_ID)).thenReturn(Optional.empty());
            when(participantRepository.save(any(MissionParticipant.class)))
                .thenAnswer(invocation -> {
                    MissionParticipant saved = invocation.getArgument(0);
                    setId(saved, 1L);
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
            setId(mission, missionId);

            when(missionRepository.findByIdAndIsDeletedFalse(missionId)).thenReturn(Optional.of(mission));
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
            setId(participant, 1L);

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

    @Nested
    @DisplayName("참여자 승인 테스트")
    class AcceptParticipantTest {

        @Test
        @DisplayName("미션 생성자가 대기 중인 참여자를 승인한다")
        void acceptParticipant_success() {
            // given
            Long missionId = 1L;
            Long participantId = 1L;
            Mission mission = Mission.builder()
                .title("비공개 미션")
                .description("설명")
                .status(MissionStatus.OPEN)
                .visibility(MissionVisibility.PRIVATE)
                .type(MissionType.PERSONAL)
                .creatorId(ADMIN_USER_ID)
                .build();
            setId(mission, missionId);
            TestReflectionUtils.setField(mission, "source", MissionSource.USER);

            MissionParticipant participant = MissionParticipant.builder()
                .mission(mission)
                .userId(TEST_USER_ID)
                .status(ParticipantStatus.PENDING)
                .progress(0)
                .joinedAt(LocalDateTime.now())
                .build();
            setId(participant, participantId);

            when(missionRepository.findByIdAndIsDeletedFalse(missionId)).thenReturn(Optional.of(mission));
            when(participantRepository.findById(participantId)).thenReturn(Optional.of(participant));

            // when
            MissionParticipantResponse response = missionParticipantService.acceptParticipant(missionId, participantId, ADMIN_USER_ID);

            // then
            assertThat(response.getStatus()).isEqualTo(ParticipantStatus.ACCEPTED);
            verify(missionExecutionService).generateExecutionsForParticipant(participant);
        }

        @Test
        @DisplayName("미션 생성자가 아닌 사용자가 승인하면 예외가 발생한다")
        void acceptParticipant_notOwner_throwsException() {
            // given
            Long missionId = 1L;
            Long participantId = 1L;
            Mission mission = Mission.builder()
                .title("비공개 미션")
                .description("설명")
                .status(MissionStatus.OPEN)
                .visibility(MissionVisibility.PRIVATE)
                .type(MissionType.PERSONAL)
                .creatorId(ADMIN_USER_ID)
                .build();
            setId(mission, missionId);
            TestReflectionUtils.setField(mission, "source", MissionSource.USER);

            when(missionRepository.findByIdAndIsDeletedFalse(missionId)).thenReturn(Optional.of(mission));

            // when & then
            assertThatThrownBy(() -> missionParticipantService.acceptParticipant(missionId, participantId, TEST_USER_ID))
                .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("참여자 목록 조회 테스트")
    class GetMissionParticipantsTest {

        @Test
        @DisplayName("미션의 참여자 목록을 조회한다")
        void getMissionParticipants_success() {
            // given
            Long missionId = 1L;
            Mission mission = createOpenPublicMission(missionId);

            MissionParticipant participant1 = MissionParticipant.builder()
                .mission(mission)
                .userId(TEST_USER_ID)
                .status(ParticipantStatus.ACCEPTED)
                .progress(50)
                .joinedAt(LocalDateTime.now())
                .build();
            setId(participant1, 1L);

            MissionParticipant participant2 = MissionParticipant.builder()
                .mission(mission)
                .userId("user-2")
                .status(ParticipantStatus.ACCEPTED)
                .progress(30)
                .joinedAt(LocalDateTime.now())
                .build();
            setId(participant2, 2L);

            when(participantRepository.findByMissionId(missionId))
                .thenReturn(List.of(participant1, participant2));

            // when
            List<MissionParticipantResponse> response = missionParticipantService.getMissionParticipants(missionId);

            // then
            assertThat(response).hasSize(2);
        }

        @Test
        @DisplayName("참여자가 없으면 빈 목록을 반환한다")
        void getMissionParticipants_empty() {
            // given
            Long missionId = 1L;

            when(participantRepository.findByMissionId(missionId)).thenReturn(List.of());

            // when
            List<MissionParticipantResponse> response = missionParticipantService.getMissionParticipants(missionId);

            // then
            assertThat(response).isEmpty();
        }
    }

    @Nested
    @DisplayName("참여 여부 확인 테스트")
    class IsParticipatingTest {

        @Test
        @DisplayName("참여 중이면 true를 반환한다")
        void isParticipating_true() {
            // given
            Long missionId = 1L;

            when(participantRepository.existsActiveParticipation(missionId, TEST_USER_ID)).thenReturn(true);

            // when
            boolean result = missionParticipantService.isParticipating(missionId, TEST_USER_ID);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("참여하지 않으면 false를 반환한다")
        void isParticipating_false() {
            // given
            Long missionId = 1L;

            when(participantRepository.existsActiveParticipation(missionId, TEST_USER_ID)).thenReturn(false);

            // when
            boolean result = missionParticipantService.isParticipating(missionId, TEST_USER_ID);

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("생성자 참여 테스트")
    class AddCreatorAsParticipantTest {

        @Test
        @DisplayName("미션 생성자를 참여자로 추가한다")
        void addCreatorAsParticipant_success() {
            // given
            Long missionId = 1L;
            Mission mission = createOpenPublicMission(missionId);

            when(participantRepository.save(any(MissionParticipant.class)))
                .thenAnswer(invocation -> {
                    MissionParticipant saved = invocation.getArgument(0);
                    setId(saved, 1L);
                    return saved;
                });

            // when
            missionParticipantService.addCreatorAsParticipant(mission, TEST_USER_ID);

            // then
            verify(participantRepository).save(any(MissionParticipant.class));
            verify(missionExecutionService).generateExecutionsForParticipant(any(MissionParticipant.class));
        }
    }

    @Nested
    @DisplayName("길드 멤버 참여 테스트")
    class AddGuildMemberAsParticipantTest {

        @Test
        @DisplayName("길드 멤버를 참여자로 추가한다")
        void addGuildMemberAsParticipant_success() {
            // given
            Long missionId = 1L;
            Mission mission = createOpenPublicMission(missionId);

            when(participantRepository.save(any(MissionParticipant.class)))
                .thenAnswer(invocation -> {
                    MissionParticipant saved = invocation.getArgument(0);
                    setId(saved, 1L);
                    return saved;
                });

            // when
            missionParticipantService.addGuildMemberAsParticipant(mission, TEST_USER_ID);

            // then
            verify(participantRepository).save(any(MissionParticipant.class));
            verify(missionExecutionService).generateExecutionsForParticipant(any(MissionParticipant.class));
        }
    }

    @Nested
    @DisplayName("진행 상태 변경 테스트")
    class ProgressStatusTest {

        @Test
        @DisplayName("참여자의 진행 상태를 IN_PROGRESS로 변경한다")
        void startProgress_success() {
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
            setId(participant, 1L);

            when(participantRepository.findByMissionIdAndUserId(missionId, TEST_USER_ID))
                .thenReturn(Optional.of(participant));

            // when
            MissionParticipantResponse response = missionParticipantService.startProgress(missionId, TEST_USER_ID);

            // then
            assertThat(response.getStatus()).isEqualTo(ParticipantStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("참여자의 진행률을 업데이트한다")
        void updateProgress_success() {
            // given
            Long missionId = 1L;
            Mission mission = createOpenPublicMission(missionId);
            int newProgress = 50;

            MissionParticipant participant = MissionParticipant.builder()
                .mission(mission)
                .userId(TEST_USER_ID)
                .status(ParticipantStatus.IN_PROGRESS)
                .progress(0)
                .joinedAt(LocalDateTime.now())
                .build();
            setId(participant, 1L);

            when(participantRepository.findByMissionIdAndUserId(missionId, TEST_USER_ID))
                .thenReturn(Optional.of(participant));

            // when
            MissionParticipantResponse response = missionParticipantService.updateProgress(missionId, TEST_USER_ID, newProgress);

            // then
            assertThat(response.getProgress()).isEqualTo(newProgress);
        }

        @Test
        @DisplayName("참여자를 완료 상태로 변경한다")
        void completeParticipant_success() {
            // given
            Long missionId = 1L;
            Mission mission = createOpenPublicMission(missionId);

            MissionParticipant participant = MissionParticipant.builder()
                .mission(mission)
                .userId(TEST_USER_ID)
                .status(ParticipantStatus.IN_PROGRESS)
                .progress(100)
                .joinedAt(LocalDateTime.now())
                .build();
            setId(participant, 1L);

            when(participantRepository.findByMissionIdAndUserId(missionId, TEST_USER_ID))
                .thenReturn(Optional.of(participant));

            // when
            MissionParticipantResponse response = missionParticipantService.completeParticipant(missionId, TEST_USER_ID);

            // then
            assertThat(response.getStatus()).isEqualTo(ParticipantStatus.COMPLETED);
        }
    }

    @Nested
    @DisplayName("내 참여 조회 테스트")
    class GetMyParticipationsTest {

        @Test
        @DisplayName("내 참여 목록을 조회한다")
        void getMyParticipations_success() {
            // given
            Mission mission1 = createOpenPublicMission(1L);
            Mission mission2 = createOpenPublicMission(2L);

            MissionParticipant participant1 = MissionParticipant.builder()
                .mission(mission1)
                .userId(TEST_USER_ID)
                .status(ParticipantStatus.ACCEPTED)
                .progress(30)
                .joinedAt(LocalDateTime.now())
                .build();
            setId(participant1, 1L);

            MissionParticipant participant2 = MissionParticipant.builder()
                .mission(mission2)
                .userId(TEST_USER_ID)
                .status(ParticipantStatus.IN_PROGRESS)
                .progress(60)
                .joinedAt(LocalDateTime.now())
                .build();
            setId(participant2, 2L);

            when(participantRepository.findByUserIdWithMission(TEST_USER_ID))
                .thenReturn(List.of(participant1, participant2));

            // when
            List<MissionParticipantResponse> responses = missionParticipantService.getMyParticipations(TEST_USER_ID);

            // then
            assertThat(responses).hasSize(2);
        }

        @Test
        @DisplayName("참여한 미션이 없으면 빈 목록을 반환한다")
        void getMyParticipations_empty() {
            // given
            when(participantRepository.findByUserIdWithMission(TEST_USER_ID)).thenReturn(List.of());

            // when
            List<MissionParticipantResponse> responses = missionParticipantService.getMyParticipations(TEST_USER_ID);

            // then
            assertThat(responses).isEmpty();
        }

        @Test
        @DisplayName("특정 미션에 대한 내 참여 정보를 조회한다")
        void getMyParticipation_success() {
            // given
            Long missionId = 1L;
            Mission mission = createOpenPublicMission(missionId);

            MissionParticipant participant = MissionParticipant.builder()
                .mission(mission)
                .userId(TEST_USER_ID)
                .status(ParticipantStatus.ACCEPTED)
                .progress(50)
                .joinedAt(LocalDateTime.now())
                .build();
            setId(participant, 1L);

            when(participantRepository.findByMissionIdAndUserId(missionId, TEST_USER_ID))
                .thenReturn(Optional.of(participant));

            // when
            MissionParticipantResponse response = missionParticipantService.getMyParticipation(missionId, TEST_USER_ID);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getProgress()).isEqualTo(50);
        }

        @Test
        @DisplayName("참여하지 않은 미션 정보를 조회하면 예외가 발생한다")
        void getMyParticipation_notFound_throwsException() {
            // given
            Long missionId = 1L;

            when(participantRepository.findByMissionIdAndUserId(missionId, TEST_USER_ID))
                .thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> missionParticipantService.getMyParticipation(missionId, TEST_USER_ID))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

}
