package io.pinkspider.leveluptogethermvp.missionservice.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;

import io.pinkspider.global.test.TestReflectionUtils;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.userservice.feed.application.ActivityFeedService;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildMember;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionCreateRequest;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionTemplateResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionCategory;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionTemplate;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionInterval;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionSource;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.global.event.GuildMissionArrivedEvent;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionCategoryRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionTemplateRepository;
import io.pinkspider.leveluptogethermvp.supportservice.report.application.ReportService;
import io.pinkspider.leveluptogethermvp.supportservice.report.api.dto.ReportTargetType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

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

    @Mock
    private GuildMemberRepository guildMemberRepository;

    @Mock
    private GuildRepository guildRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ActivityFeedService activityFeedService;

    @Mock
    private MissionTemplateRepository missionTemplateRepository;

    @Mock
    private ReportService reportService;

    @Captor
    private ArgumentCaptor<GuildMissionArrivedEvent> eventCaptor;

    @InjectMocks
    private MissionService missionService;

    private static final String TEST_USER_ID = "test-user-123";
    private static final String ADMIN_USER_ID = "admin-user-456";

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
            setId(mission, missionId);
            TestReflectionUtils.setField(mission, "source", MissionSource.USER);

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
            setId(mission, missionId);
            TestReflectionUtils.setField(mission, "source", MissionSource.USER);

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
            setId(mission, missionId);
            TestReflectionUtils.setField(mission, "source", MissionSource.USER);

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
            setId(mission, missionId);
            TestReflectionUtils.setField(mission, "source", MissionSource.SYSTEM);  // 시스템 미션

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
            setId(mission, missionId);
            TestReflectionUtils.setField(mission, "source", MissionSource.SYSTEM);

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
            setId(mission, missionId);
            TestReflectionUtils.setField(mission, "source", MissionSource.USER);  // 일반 사용자 미션

            when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));

            // when & then
            assertThatThrownBy(() -> missionService.deleteMission(missionId, TEST_USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("미션 생성자 또는 길드 관리자만 이 작업을 수행할 수 있습니다.");

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
            setId(mission, missionId);
            TestReflectionUtils.setField(mission, "source", MissionSource.USER);

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
            setId(mission, missionId);
            TestReflectionUtils.setField(mission, "source", MissionSource.USER);

            when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));

            // when
            missionService.deleteMission(missionId, TEST_USER_ID);

            // then
            verify(missionRepository).delete(mission);
        }
    }

    @Nested
    @DisplayName("미션 오픈 테스트")
    class OpenMissionTest {

        private Guild createTestGuild(Long guildId) {
            Guild guild = Guild.builder()
                .name("테스트 길드")
                .description("테스트")
                .categoryId(1L)
                .masterId(TEST_USER_ID)
                .build();
            setId(guild, guildId);
            return guild;
        }

        private GuildMember createTestGuildMember(Guild guild, String userId) {
            return GuildMember.builder()
                .guild(guild)
                .userId(userId)
                .build();
        }

        @Test
        @DisplayName("길드 미션 오픈 시 길드원에게 이벤트가 발행된다")
        void openMission_guildMission_publishesEvent() {
            // given
            Long missionId = 1L;
            Long guildId = 100L;
            String member1Id = "member-1";
            String member2Id = "member-2";

            Mission mission = Mission.builder()
                .title("길드 미션")
                .description("길드 미션 설명")
                .status(MissionStatus.DRAFT)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.GUILD)
                .guildId(guildId.toString())
                .creatorId(TEST_USER_ID)
                .build();
            setId(mission, missionId);
            TestReflectionUtils.setField(mission, "source", MissionSource.USER);

            Guild guild = createTestGuild(guildId);
            List<GuildMember> guildMembers = List.of(
                createTestGuildMember(guild, TEST_USER_ID),  // 생성자
                createTestGuildMember(guild, member1Id),
                createTestGuildMember(guild, member2Id)
            );

            when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
            when(guildMemberRepository.findActiveMembers(guildId)).thenReturn(guildMembers);

            // when
            MissionResponse response = missionService.openMission(missionId, TEST_USER_ID);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(MissionStatus.OPEN);

            // 이벤트 발행 확인 (생성자 제외한 멤버 목록과 함께)
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            GuildMissionArrivedEvent event = eventCaptor.getValue();
            assertThat(event.missionId()).isEqualTo(missionId);
            assertThat(event.missionTitle()).isEqualTo("길드 미션");
            assertThat(event.memberIds()).containsExactlyInAnyOrder(member1Id, member2Id);
            assertThat(event.memberIds()).doesNotContain(TEST_USER_ID);  // 생성자 제외

            // 참여자 자동 등록은 하지 않음 (길드원이 직접 참여 신청)
            verify(missionParticipantService, never()).addGuildMemberAsParticipant(any(), anyString());
        }

        @Test
        @DisplayName("개인 미션 오픈 시 길드원 알림/등록 로직이 호출되지 않는다")
        void openMission_personalMission_noGuildNotification() {
            // given
            Long missionId = 1L;
            Mission mission = Mission.builder()
                .title("개인 미션")
                .description("개인 미션 설명")
                .status(MissionStatus.DRAFT)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .creatorId(TEST_USER_ID)
                .build();
            setId(mission, missionId);
            TestReflectionUtils.setField(mission, "source", MissionSource.USER);

            when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));

            // when
            MissionResponse response = missionService.openMission(missionId, TEST_USER_ID);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(MissionStatus.OPEN);

            // 길드 관련 로직이 호출되지 않음
            verify(guildMemberRepository, never()).findActiveMembers(anyLong());
            verify(eventPublisher, never()).publishEvent(any(GuildMissionArrivedEvent.class));
            verify(missionParticipantService, never()).addGuildMemberAsParticipant(any(), anyString());
        }

        @Test
        @DisplayName("미션 생성자가 아닌 사용자가 미션을 오픈하면 예외가 발생한다")
        void openMission_notCreator_throwsException() {
            // given
            Long missionId = 1L;
            String otherUserId = "other-user-789";
            Mission mission = Mission.builder()
                .title("테스트 미션")
                .description("설명")
                .status(MissionStatus.DRAFT)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .creatorId(otherUserId)
                .build();
            setId(mission, missionId);

            when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));

            // when & then
            assertThatThrownBy(() -> missionService.openMission(missionId, TEST_USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("미션 생성자 또는 길드 관리자만 이 작업을 수행할 수 있습니다.");
        }

        @Test
        @DisplayName("길드 미션이지만 guildId가 null이면 알림/등록 로직이 호출되지 않는다")
        void openMission_guildMissionWithNullGuildId_noNotification() {
            // given
            Long missionId = 1L;
            Mission mission = Mission.builder()
                .title("길드 미션")
                .description("설명")
                .status(MissionStatus.DRAFT)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.GUILD)
                .guildId(null)  // guildId가 null
                .creatorId(TEST_USER_ID)
                .build();
            setId(mission, missionId);

            when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));

            // when
            MissionResponse response = missionService.openMission(missionId, TEST_USER_ID);

            // then
            assertThat(response).isNotNull();
            verify(guildMemberRepository, never()).findActiveMembers(anyLong());
            verify(eventPublisher, never()).publishEvent(any(GuildMissionArrivedEvent.class));
        }
    }

    @Nested
    @DisplayName("미션 생성 테스트")
    class CreateMissionTest {

        private MissionCategory createTestCategory(Long id) {
            MissionCategory category = MissionCategory.builder()
                .name("테스트 카테고리")
                .isActive(true)
                .build();
            setId(category, id);
            return category;
        }

        @Test
        @DisplayName("개인 미션을 성공적으로 생성한다")
        void createMission_personal_success() {
            // given
            MissionCreateRequest request = MissionCreateRequest.builder()
                .title("테스트 미션")
                .description("테스트 설명")
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .missionInterval(MissionInterval.DAILY)
                .durationDays(7)
                .build();

            Mission savedMission = Mission.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .status(MissionStatus.DRAFT)
                .visibility(request.getVisibility())
                .type(request.getType())
                .source(MissionSource.USER)
                .creatorId(TEST_USER_ID)
                .build();
            setId(savedMission, 1L);

            when(missionRepository.save(any(Mission.class))).thenReturn(savedMission);
            doNothing().when(missionParticipantService).addCreatorAsParticipant(any(), anyString());

            // when
            MissionResponse response = missionService.createMission(TEST_USER_ID, request);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getTitle()).isEqualTo("테스트 미션");
            verify(missionRepository).save(any(Mission.class));
        }

        @Test
        @DisplayName("카테고리가 있는 미션을 생성한다")
        void createMission_withCategory_success() {
            // given
            Long categoryId = 1L;
            MissionCategory category = createTestCategory(categoryId);

            MissionCreateRequest request = MissionCreateRequest.builder()
                .title("카테고리 미션")
                .description("설명")
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .categoryId(categoryId)
                .missionInterval(MissionInterval.DAILY)
                .build();

            Mission savedMission = Mission.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .status(MissionStatus.DRAFT)
                .visibility(request.getVisibility())
                .type(request.getType())
                .source(MissionSource.USER)
                .creatorId(TEST_USER_ID)
                .category(category)
                .build();
            setId(savedMission, 1L);

            when(missionCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
            when(missionRepository.save(any(Mission.class))).thenReturn(savedMission);

            // when
            MissionResponse response = missionService.createMission(TEST_USER_ID, request);

            // then
            assertThat(response).isNotNull();
            verify(missionCategoryRepository).findById(categoryId);
        }

        @Test
        @DisplayName("길드 미션 생성 시 guildId가 없으면 예외가 발생한다")
        void createMission_guildMission_noGuildId_throwsException() {
            // given
            MissionCreateRequest request = MissionCreateRequest.builder()
                .title("길드 미션")
                .description("설명")
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.GUILD)
                .guildId(null)
                .build();

            // when & then
            assertThatThrownBy(() -> missionService.createMission(TEST_USER_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("길드 미션은 길드 ID가 필요합니다.");
        }

        @Test
        @DisplayName("존재하지 않는 카테고리로 미션 생성 시 예외가 발생한다")
        void createMission_invalidCategory_throwsException() {
            // given
            Long invalidCategoryId = 999L;
            MissionCreateRequest request = MissionCreateRequest.builder()
                .title("미션")
                .description("설명")
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .categoryId(invalidCategoryId)
                .build();

            when(missionCategoryRepository.findById(invalidCategoryId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> missionService.createMission(TEST_USER_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("존재하지 않는 카테고리입니다");
        }

        @Test
        @DisplayName("비활성화된 카테고리로 미션 생성 시 예외가 발생한다")
        void createMission_inactiveCategory_throwsException() {
            // given
            Long categoryId = 1L;
            MissionCategory inactiveCategory = MissionCategory.builder()
                .name("비활성 카테고리")
                .isActive(false)
                .build();
            setId(inactiveCategory, categoryId);

            MissionCreateRequest request = MissionCreateRequest.builder()
                .title("미션")
                .description("설명")
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .categoryId(categoryId)
                .build();

            when(missionCategoryRepository.findById(categoryId)).thenReturn(Optional.of(inactiveCategory));

            // when & then
            assertThatThrownBy(() -> missionService.createMission(TEST_USER_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("비활성화된 카테고리입니다.");
        }
    }

    @Nested
    @DisplayName("미션 조회 테스트")
    class GetMissionTest {

        @Test
        @DisplayName("미션을 조회한다")
        void getMission_success() {
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
            setId(mission, missionId);

            when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
            when(participantRepository.countActiveParticipants(missionId)).thenReturn(5L);

            // when
            MissionResponse response = missionService.getMission(missionId);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getTitle()).isEqualTo("테스트 미션");
            verify(participantRepository).countActiveParticipants(missionId);
        }

        @Test
        @DisplayName("존재하지 않는 미션 조회 시 예외가 발생한다")
        void getMission_notFound_throwsException() {
            // given
            Long missionId = 999L;
            when(missionRepository.findById(missionId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> missionService.getMission(missionId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("미션을 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("미션 시작 테스트")
    class StartMissionTest {

        @Test
        @DisplayName("미션을 시작한다")
        void startMission_success() {
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
            setId(mission, missionId);
            TestReflectionUtils.setField(mission, "source", MissionSource.USER);

            when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));

            // when
            MissionResponse response = missionService.startMission(missionId, TEST_USER_ID);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(MissionStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("미션 생성자가 아닌 사용자가 미션을 시작하면 예외가 발생한다")
        void startMission_notCreator_throwsException() {
            // given
            Long missionId = 1L;
            String otherUserId = "other-user";
            Mission mission = Mission.builder()
                .title("테스트 미션")
                .description("설명")
                .status(MissionStatus.OPEN)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .creatorId(otherUserId)
                .build();
            setId(mission, missionId);
            TestReflectionUtils.setField(mission, "source", MissionSource.USER);

            when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));

            // when & then
            assertThatThrownBy(() -> missionService.startMission(missionId, TEST_USER_ID))
                .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("미션 취소 테스트")
    class CancelMissionTest {

        @Test
        @DisplayName("미션을 취소한다")
        void cancelMission_success() {
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
            setId(mission, missionId);
            TestReflectionUtils.setField(mission, "source", MissionSource.USER);

            when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));

            // when
            MissionResponse response = missionService.cancelMission(missionId, TEST_USER_ID);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(MissionStatus.CANCELLED);
        }

        @Test
        @DisplayName("미션 생성자가 아닌 사용자가 미션을 취소하면 예외가 발생한다")
        void cancelMission_notCreator_throwsException() {
            // given
            Long missionId = 1L;
            String otherUserId = "other-user";
            Mission mission = Mission.builder()
                .title("테스트 미션")
                .description("설명")
                .status(MissionStatus.OPEN)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .creatorId(otherUserId)
                .build();
            setId(mission, missionId);
            TestReflectionUtils.setField(mission, "source", MissionSource.USER);

            when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));

            // when & then
            assertThatThrownBy(() -> missionService.cancelMission(missionId, TEST_USER_ID))
                .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("내 미션 조회 테스트")
    class GetMyMissionsTest {

        @Test
        @DisplayName("사용자가 참여중인 미션 목록을 조회한다")
        void getMyMissions_success() {
            // given
            Mission mission1 = Mission.builder()
                .title("미션1")
                .description("설명1")
                .status(MissionStatus.OPEN)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .creatorId(TEST_USER_ID)
                .build();
            setId(mission1, 1L);

            Mission mission2 = Mission.builder()
                .title("미션2")
                .description("설명2")
                .status(MissionStatus.IN_PROGRESS)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .creatorId(TEST_USER_ID)
                .build();
            setId(mission2, 2L);

            when(missionRepository.findByParticipantUserIdSorted(TEST_USER_ID))
                .thenReturn(List.of(mission1, mission2));

            // when
            List<MissionResponse> result = missionService.getMyMissions(TEST_USER_ID);

            // then
            assertThat(result).hasSize(2);
            verify(missionRepository).findByParticipantUserIdSorted(TEST_USER_ID);
        }

        @Test
        @DisplayName("참여중인 미션이 없으면 빈 목록을 반환한다")
        void getMyMissions_empty() {
            // given
            when(missionRepository.findByParticipantUserIdSorted(TEST_USER_ID))
                .thenReturn(List.of());

            // when
            List<MissionResponse> result = missionService.getMyMissions(TEST_USER_ID);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("공개 미션 조회 테스트")
    class GetPublicOpenMissionsTest {

        @Test
        @DisplayName("공개 모집중 미션 목록을 조회한다")
        void getPublicOpenMissions_success() {
            // given
            Mission mission = Mission.builder()
                .title("공개 미션")
                .description("설명")
                .status(MissionStatus.OPEN)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .creatorId(TEST_USER_ID)
                .build();
            setId(mission, 1L);

            org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
            org.springframework.data.domain.Page<Mission> page = new org.springframework.data.domain.PageImpl<>(List.of(mission));

            when(missionRepository.findOpenPublicMissions(pageable)).thenReturn(page);

            // when
            org.springframework.data.domain.Page<MissionResponse> result = missionService.getPublicOpenMissions(pageable);

            // then
            assertThat(result.getContent()).hasSize(1);
            verify(missionRepository).findOpenPublicMissions(pageable);
        }
    }

    @Nested
    @DisplayName("길드 미션 조회 테스트")
    class GetGuildMissionsTest {

        @Test
        @DisplayName("길드 미션 목록을 조회한다")
        void getGuildMissions_success() {
            // given
            String guildId = "100";
            Mission mission = Mission.builder()
                .title("길드 미션")
                .description("설명")
                .status(MissionStatus.OPEN)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.GUILD)
                .guildId(guildId)
                .creatorId(TEST_USER_ID)
                .build();
            setId(mission, 1L);

            List<MissionStatus> activeStatuses = List.of(
                MissionStatus.DRAFT,
                MissionStatus.OPEN,
                MissionStatus.IN_PROGRESS
            );

            when(missionRepository.findGuildMissions(guildId, activeStatuses))
                .thenReturn(List.of(mission));

            // when
            List<MissionResponse> result = missionService.getGuildMissions(guildId);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getTitle()).isEqualTo("길드 미션");
        }
    }

    @Nested
    @DisplayName("시스템 미션 조회 테스트")
    class GetSystemMissionsTest {

        @Test
        @DisplayName("시스템 미션 템플릿 목록을 조회한다")
        void getSystemMissions_success() {
            // given
            MissionTemplate template = MissionTemplate.builder()
                .title("시스템 미션")
                .description("설명")
                .visibility(MissionVisibility.PUBLIC)
                .source(MissionSource.SYSTEM)
                .build();
            setId(template, 1L);

            org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
            org.springframework.data.domain.Page<MissionTemplate> page = new org.springframework.data.domain.PageImpl<>(List.of(template));

            when(missionTemplateRepository.findPublicTemplates(MissionSource.SYSTEM, MissionVisibility.PUBLIC, pageable))
                .thenReturn(page);

            // when
            org.springframework.data.domain.Page<MissionTemplateResponse> result = missionService.getSystemMissions(pageable);

            // then
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("카테고리별 시스템 미션 템플릿을 조회한다")
        void getSystemMissionsByCategory_success() {
            // given
            Long categoryId = 1L;
            MissionTemplate template = MissionTemplate.builder()
                .title("카테고리 시스템 미션")
                .description("설명")
                .visibility(MissionVisibility.PUBLIC)
                .source(MissionSource.SYSTEM)
                .build();
            setId(template, 1L);

            org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
            org.springframework.data.domain.Page<MissionTemplate> page = new org.springframework.data.domain.PageImpl<>(List.of(template));

            when(missionTemplateRepository.findPublicTemplatesByCategory(
                MissionSource.SYSTEM, MissionVisibility.PUBLIC, categoryId, pageable))
                .thenReturn(page);

            // when
            org.springframework.data.domain.Page<MissionTemplateResponse> result = missionService.getSystemMissionsByCategory(categoryId, pageable);

            // then
            assertThat(result.getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("미션 수정 테스트")
    class UpdateMissionTest {

        @Test
        @DisplayName("DRAFT 상태의 미션을 수정한다")
        void updateMission_success() {
            // given
            Long missionId = 1L;
            Mission mission = Mission.builder()
                .title("원래 제목")
                .description("원래 설명")
                .status(MissionStatus.DRAFT)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .creatorId(TEST_USER_ID)
                .build();
            setId(mission, missionId);
            TestReflectionUtils.setField(mission, "source", MissionSource.USER);

            io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionUpdateRequest request =
                io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionUpdateRequest.builder()
                    .title("수정된 제목")
                    .description("수정된 설명")
                    .build();

            when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));

            // when
            MissionResponse response = missionService.updateMission(missionId, TEST_USER_ID, request);

            // then
            assertThat(response).isNotNull();
            assertThat(mission.getTitle()).isEqualTo("수정된 제목");
            assertThat(mission.getDescription()).isEqualTo("수정된 설명");
        }

        @Test
        @DisplayName("DRAFT 상태가 아닌 미션을 수정하면 예외가 발생한다")
        void updateMission_notDraft_throwsException() {
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
            setId(mission, missionId);
            TestReflectionUtils.setField(mission, "source", MissionSource.USER);

            io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionUpdateRequest request =
                io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionUpdateRequest.builder()
                    .title("수정된 제목")
                    .build();

            when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));

            // when & then
            assertThatThrownBy(() -> missionService.updateMission(missionId, TEST_USER_ID, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("작성중 상태의 미션만 수정할 수 있습니다.");
        }

        @Test
        @DisplayName("미션 생성자가 아닌 사용자가 수정하면 예외가 발생한다")
        void updateMission_notCreator_throwsException() {
            // given
            Long missionId = 1L;
            String otherUserId = "other-user";
            Mission mission = Mission.builder()
                .title("테스트 미션")
                .description("설명")
                .status(MissionStatus.DRAFT)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .creatorId(otherUserId)
                .build();
            setId(mission, missionId);
            TestReflectionUtils.setField(mission, "source", MissionSource.USER);

            io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionUpdateRequest request =
                io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionUpdateRequest.builder()
                    .title("수정된 제목")
                    .build();

            when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));

            // when & then
            assertThatThrownBy(() -> missionService.updateMission(missionId, TEST_USER_ID, request))
                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("카테고리를 포함하여 미션을 수정한다")
        void updateMission_withCategory_success() {
            // given
            Long missionId = 1L;
            Long categoryId = 2L;
            Mission mission = Mission.builder()
                .title("원래 제목")
                .description("원래 설명")
                .status(MissionStatus.DRAFT)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .creatorId(TEST_USER_ID)
                .build();
            setId(mission, missionId);
            TestReflectionUtils.setField(mission, "source", MissionSource.USER);

            MissionCategory category = MissionCategory.builder()
                .name("새 카테고리")
                .isActive(true)
                .build();
            setId(category, categoryId);

            io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionUpdateRequest request =
                io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionUpdateRequest.builder()
                    .categoryId(categoryId)
                    .build();

            when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
            when(missionCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));

            // when
            MissionResponse response = missionService.updateMission(missionId, TEST_USER_ID, request);

            // then
            assertThat(response).isNotNull();
            verify(missionCategoryRepository).findById(categoryId);
        }

        @Test
        @DisplayName("카테고리를 제거하여 미션을 수정한다")
        void updateMission_clearCategory_success() {
            // given
            Long missionId = 1L;
            MissionCategory existingCategory = MissionCategory.builder()
                .name("기존 카테고리")
                .isActive(true)
                .build();

            Mission mission = Mission.builder()
                .title("원래 제목")
                .description("원래 설명")
                .status(MissionStatus.DRAFT)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .creatorId(TEST_USER_ID)
                .category(existingCategory)
                .build();
            setId(mission, missionId);
            TestReflectionUtils.setField(mission, "source", MissionSource.USER);

            io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionUpdateRequest request =
                io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionUpdateRequest.builder()
                    .clearCategory(true)
                    .build();

            when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));

            // when
            MissionResponse response = missionService.updateMission(missionId, TEST_USER_ID, request);

            // then
            assertThat(response).isNotNull();
            assertThat(mission.getCategory()).isNull();
        }
    }

    @Nested
    @DisplayName("미션 완료 테스트")
    class CompleteMissionTest {

        @Test
        @DisplayName("미션을 완료한다")
        void completeMission_success() {
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
            setId(mission, missionId);
            TestReflectionUtils.setField(mission, "source", MissionSource.USER);

            when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));

            // when
            MissionResponse response = missionService.completeMission(missionId, TEST_USER_ID);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(MissionStatus.COMPLETED);
        }

        @Test
        @DisplayName("미션 생성자가 아닌 사용자가 미션을 완료하면 예외가 발생한다")
        void completeMission_notCreator_throwsException() {
            // given
            Long missionId = 1L;
            String otherUserId = "other-user";
            Mission mission = Mission.builder()
                .title("테스트 미션")
                .description("설명")
                .status(MissionStatus.IN_PROGRESS)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .creatorId(otherUserId)
                .build();
            setId(mission, missionId);
            TestReflectionUtils.setField(mission, "source", MissionSource.USER);

            when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));

            // when & then
            assertThatThrownBy(() -> missionService.completeMission(missionId, TEST_USER_ID))
                .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("길드 관리자 권한 테스트")
    class GuildAdminPermissionTest {

        private Guild createTestGuild(Long guildId) {
            Guild guild = Guild.builder()
                .name("테스트 길드")
                .description("테스트")
                .categoryId(1L)
                .masterId(TEST_USER_ID)
                .build();
            setId(guild, guildId);
            return guild;
        }

        @Test
        @DisplayName("길드 관리자가 길드 미션을 삭제할 수 있다")
        void deleteMission_guildAdmin_success() {
            // given
            Long missionId = 1L;
            Long guildId = 100L;
            String guildMasterId = "guild-master";
            Mission mission = Mission.builder()
                .title("길드 미션")
                .description("설명")
                .status(MissionStatus.OPEN)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.GUILD)
                .guildId(guildId.toString())
                .creatorId("other-creator")
                .build();
            setId(mission, missionId);
            TestReflectionUtils.setField(mission, "source", MissionSource.USER);

            Guild guild = createTestGuild(guildId);
            GuildMember masterMember = GuildMember.builder()
                .guild(guild)
                .userId(guildMasterId)
                .build();
            // 마스터 역할 설정
            TestReflectionUtils.setField(masterMember, "role", io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberRole.MASTER);
            TestReflectionUtils.setField(masterMember, "status", io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberStatus.ACTIVE);

            when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
            when(guildMemberRepository.findByGuildIdAndUserId(guildId, guildMasterId))
                .thenReturn(Optional.of(masterMember));

            // when
            missionService.deleteMission(missionId, guildMasterId);

            // then
            verify(missionRepository).delete(mission);
        }
    }

    @Nested
    @DisplayName("신고 처리중 상태 통합 테스트")
    class IsUnderReviewIntegrationTest {

        @Test
        @DisplayName("미션 상세 조회 시 신고 처리중 상태가 true로 반환된다")
        void getMission_underReview_true() {
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
            setId(mission, missionId);

            when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
            when(participantRepository.countActiveParticipants(missionId)).thenReturn(5L);
            when(reportService.isUnderReview(ReportTargetType.MISSION, "1")).thenReturn(true);

            // when
            MissionResponse response = missionService.getMission(missionId);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getIsUnderReview()).isTrue();
            verify(reportService).isUnderReview(ReportTargetType.MISSION, "1");
        }

        @Test
        @DisplayName("미션 상세 조회 시 신고 처리중 상태가 false로 반환된다")
        void getMission_underReview_false() {
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
            setId(mission, missionId);

            when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
            when(participantRepository.countActiveParticipants(missionId)).thenReturn(5L);
            when(reportService.isUnderReview(ReportTargetType.MISSION, "1")).thenReturn(false);

            // when
            MissionResponse response = missionService.getMission(missionId);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getIsUnderReview()).isFalse();
        }

        @Test
        @DisplayName("내 미션 목록 조회 시 신고 처리중 상태가 일괄 조회된다")
        void getMyMissions_batchUnderReviewCheck() {
            // given
            Mission mission1 = Mission.builder()
                .title("미션1")
                .description("설명1")
                .status(MissionStatus.OPEN)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .creatorId(TEST_USER_ID)
                .build();
            setId(mission1, 1L);

            Mission mission2 = Mission.builder()
                .title("미션2")
                .description("설명2")
                .status(MissionStatus.IN_PROGRESS)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .creatorId(TEST_USER_ID)
                .build();
            setId(mission2, 2L);

            when(missionRepository.findByParticipantUserIdSorted(TEST_USER_ID))
                .thenReturn(List.of(mission1, mission2));

            Map<String, Boolean> underReviewMap = new HashMap<>();
            underReviewMap.put("1", true);
            underReviewMap.put("2", false);
            when(reportService.isUnderReviewBatch(eq(ReportTargetType.MISSION), any())).thenReturn(underReviewMap);

            // when
            List<MissionResponse> result = missionService.getMyMissions(TEST_USER_ID);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getIsUnderReview()).isTrue();
            assertThat(result.get(1).getIsUnderReview()).isFalse();
            verify(reportService).isUnderReviewBatch(eq(ReportTargetType.MISSION), any());
        }

        @Test
        @DisplayName("공개 미션 목록 조회 시 신고 처리중 상태가 일괄 조회된다")
        void getPublicOpenMissions_batchUnderReviewCheck() {
            // given
            Mission mission = Mission.builder()
                .title("공개 미션")
                .description("설명")
                .status(MissionStatus.OPEN)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.PERSONAL)
                .creatorId(TEST_USER_ID)
                .build();
            setId(mission, 1L);

            org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
            org.springframework.data.domain.Page<Mission> page = new org.springframework.data.domain.PageImpl<>(List.of(mission));

            when(missionRepository.findOpenPublicMissions(pageable)).thenReturn(page);

            Map<String, Boolean> underReviewMap = new HashMap<>();
            underReviewMap.put("1", true);
            when(reportService.isUnderReviewBatch(eq(ReportTargetType.MISSION), any())).thenReturn(underReviewMap);

            // when
            org.springframework.data.domain.Page<MissionResponse> result = missionService.getPublicOpenMissions(pageable);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getIsUnderReview()).isTrue();
            verify(reportService).isUnderReviewBatch(eq(ReportTargetType.MISSION), any());
        }

        @Test
        @DisplayName("길드 미션 목록 조회 시 신고 처리중 상태가 일괄 조회된다")
        void getGuildMissions_batchUnderReviewCheck() {
            // given
            String guildId = "100";
            Mission mission = Mission.builder()
                .title("길드 미션")
                .description("설명")
                .status(MissionStatus.OPEN)
                .visibility(MissionVisibility.PUBLIC)
                .type(MissionType.GUILD)
                .guildId(guildId)
                .creatorId(TEST_USER_ID)
                .build();
            setId(mission, 1L);

            List<MissionStatus> activeStatuses = List.of(
                MissionStatus.DRAFT,
                MissionStatus.OPEN,
                MissionStatus.IN_PROGRESS
            );

            when(missionRepository.findGuildMissions(guildId, activeStatuses))
                .thenReturn(List.of(mission));

            Map<String, Boolean> underReviewMap = new HashMap<>();
            underReviewMap.put("1", true);
            when(reportService.isUnderReviewBatch(eq(ReportTargetType.MISSION), any())).thenReturn(underReviewMap);

            // when
            List<MissionResponse> result = missionService.getGuildMissions(guildId);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getIsUnderReview()).isTrue();
            verify(reportService).isUnderReviewBatch(eq(ReportTargetType.MISSION), any());
        }


        @Test
        @DisplayName("빈 미션 목록 조회 시 신고 상태 일괄 조회가 호출되지 않는다")
        void getMyMissions_emptyList_noReportServiceCall() {
            // given
            when(missionRepository.findByParticipantUserIdSorted(TEST_USER_ID))
                .thenReturn(List.of());

            // when
            List<MissionResponse> result = missionService.getMyMissions(TEST_USER_ID);

            // then
            assertThat(result).isEmpty();
            verify(reportService, never()).isUnderReviewBatch(any(), any());
        }
    }
}
