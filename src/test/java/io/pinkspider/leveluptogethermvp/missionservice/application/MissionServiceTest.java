package io.pinkspider.leveluptogethermvp.missionservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionSource;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionCategoryRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MissionServiceTest {

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private MissionParticipantRepository participantRepository;

    @Mock
    private MissionCategoryRepository missionCategoryRepository;

    @Mock
    private MissionParticipantService missionParticipantService;

    @InjectMocks
    private MissionService missionService;

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

    @Nested
    @DisplayName("미션 삭제 테스트")
    class DeleteMissionTest {

        @Test
        @DisplayName("미션 생성자가 DRAFT 상태의 미션을 삭제할 수 있다")
        void deleteMission_byCreator_draftStatus_success() {
            // given
            Long missionId = 1L;
            Mission mission = Mission.builder()
                .title("테스트 미션")
                .description("설명")
                .status(MissionStatus.DRAFT)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .creatorId(TEST_USER_ID)
                .build();
            setMissionId(mission, missionId);
            setMissionSource(mission, MissionSource.USER);

            when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));

            // when
            missionService.deleteMission(missionId, TEST_USER_ID);

            // then
            verify(missionRepository).delete(mission);
            verify(missionParticipantService, never()).withdrawFromMission(anyLong(), anyString());
        }

        @Test
        @DisplayName("미션 생성자가 OPEN 상태의 미션을 삭제할 수 있다")
        void deleteMission_byCreator_openStatus_success() {
            // given
            Long missionId = 1L;
            Mission mission = Mission.builder()
                .title("테스트 미션")
                .description("설명")
                .status(MissionStatus.OPEN)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .creatorId(TEST_USER_ID)
                .build();
            setMissionId(mission, missionId);
            setMissionSource(mission, MissionSource.USER);

            when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));

            // when
            missionService.deleteMission(missionId, TEST_USER_ID);

            // then
            verify(missionRepository).delete(mission);
        }

        @Test
        @DisplayName("미션 생성자가 IN_PROGRESS 상태의 미션을 삭제하면 예외가 발생한다")
        void deleteMission_byCreator_inProgressStatus_throwsException() {
            // given
            Long missionId = 1L;
            Mission mission = Mission.builder()
                .title("테스트 미션")
                .description("설명")
                .status(MissionStatus.IN_PROGRESS)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .creatorId(TEST_USER_ID)
                .build();
            setMissionId(mission, missionId);
            setMissionSource(mission, MissionSource.USER);

            when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));

            // when & then
            assertThatThrownBy(() -> missionService.deleteMission(missionId, TEST_USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("진행중인 미션은 삭제할 수 없습니다.");

            verify(missionRepository, never()).delete(any());
        }

        @Test
        @DisplayName("시스템 미션 참여자가 미션을 삭제하면 참여 철회로 처리된다")
        void deleteMission_systemMissionParticipant_withdraws() {
            // given
            Long missionId = 1L;
            Mission mission = Mission.builder()
                .title("시스템 미션")
                .description("어드민이 만든 미션")
                .status(MissionStatus.OPEN)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .creatorId(ADMIN_USER_ID)  // 어드민이 생성자
                .build();
            setMissionId(mission, missionId);
            setMissionSource(mission, MissionSource.SYSTEM);  // 시스템 미션

            when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));

            // when
            missionService.deleteMission(missionId, TEST_USER_ID);  // 일반 사용자가 삭제 요청

            // then
            verify(missionRepository, never()).delete(any());
            verify(missionParticipantService).withdrawFromMission(missionId, TEST_USER_ID);
        }

        @Test
        @DisplayName("시스템 미션의 생성자(어드민)가 삭제하면 미션 자체가 삭제된다")
        void deleteMission_systemMissionCreator_deletesMission() {
            // given
            Long missionId = 1L;
            Mission mission = Mission.builder()
                .title("시스템 미션")
                .description("어드민이 만든 미션")
                .status(MissionStatus.OPEN)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .creatorId(ADMIN_USER_ID)  // 어드민이 생성자
                .build();
            setMissionId(mission, missionId);
            setMissionSource(mission, MissionSource.SYSTEM);

            when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));

            // when
            missionService.deleteMission(missionId, ADMIN_USER_ID);  // 어드민(생성자)이 삭제 요청

            // then
            verify(missionRepository).delete(mission);
            verify(missionParticipantService, never()).withdrawFromMission(anyLong(), anyString());
        }

        @Test
        @DisplayName("일반 사용자 미션을 생성자가 아닌 사용자가 삭제하면 예외가 발생한다")
        void deleteMission_userMission_notCreator_throwsException() {
            // given
            Long missionId = 1L;
            String otherUserId = "other-user-789";
            Mission mission = Mission.builder()
                .title("다른 사용자 미션")
                .description("다른 사용자가 만든 미션")
                .status(MissionStatus.OPEN)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .creatorId(otherUserId)
                .build();
            setMissionId(mission, missionId);
            setMissionSource(mission, MissionSource.USER);  // 일반 사용자 미션

            when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));

            // when & then
            assertThatThrownBy(() -> missionService.deleteMission(missionId, TEST_USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("미션 생성자만 이 작업을 수행할 수 있습니다.");

            verify(missionRepository, never()).delete(any());
            verify(missionParticipantService, never()).withdrawFromMission(anyLong(), anyString());
        }

        @Test
        @DisplayName("존재하지 않는 미션을 삭제하면 예외가 발생한다")
        void deleteMission_notFound_throwsException() {
            // given
            Long missionId = 999L;

            when(missionRepository.findById(missionId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> missionService.deleteMission(missionId, TEST_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("미션을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("미션 생성자가 COMPLETED 상태의 미션을 삭제할 수 있다")
        void deleteMission_byCreator_completedStatus_success() {
            // given
            Long missionId = 1L;
            Mission mission = Mission.builder()
                .title("완료된 미션")
                .description("설명")
                .status(MissionStatus.COMPLETED)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .creatorId(TEST_USER_ID)
                .build();
            setMissionId(mission, missionId);
            setMissionSource(mission, MissionSource.USER);

            when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));

            // when
            missionService.deleteMission(missionId, TEST_USER_ID);

            // then
            verify(missionRepository).delete(mission);
        }

        @Test
        @DisplayName("미션 생성자가 CANCELLED 상태의 미션을 삭제할 수 있다")
        void deleteMission_byCreator_cancelledStatus_success() {
            // given
            Long missionId = 1L;
            Mission mission = Mission.builder()
                .title("취소된 미션")
                .description("설명")
                .status(MissionStatus.CANCELLED)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .creatorId(TEST_USER_ID)
                .build();
            setMissionId(mission, missionId);
            setMissionSource(mission, MissionSource.USER);

            when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));

            // when
            missionService.deleteMission(missionId, TEST_USER_ID);

            // then
            verify(missionRepository).delete(mission);
        }
    }
}
