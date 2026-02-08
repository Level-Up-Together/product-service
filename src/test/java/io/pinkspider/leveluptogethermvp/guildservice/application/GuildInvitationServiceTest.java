package io.pinkspider.leveluptogethermvp.guildservice.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.event.GuildInvitationEvent;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildInvitationResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildInvitation;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildMember;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildInvitationStatus;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberRole;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberStatus;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildVisibility;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildInvitationRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionCategoryService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionCategoryResponse;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class GuildInvitationServiceTest {

    @Mock
    private GuildInvitationRepository invitationRepository;

    @Mock
    private GuildRepository guildRepository;

    @Mock
    private GuildMemberRepository guildMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MissionCategoryService missionCategoryService;

    @Mock
    private GuildChatService guildChatService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private GuildInvitationService invitationService;

    private String testMasterId;
    private String testInviterId;
    private String testInviteeId;
    private Guild testPrivateGuild;
    private Guild testPublicGuild;
    private GuildMember testMasterMember;
    private GuildMember testSubMasterMember;
    private Users testInviter;
    private Users testInvitee;
    private Long testCategoryId;
    private MissionCategoryResponse testCategory;

    @BeforeEach
    void setUp() {
        testMasterId = "test-master-id";
        testInviterId = "test-inviter-id";
        testInviteeId = "test-invitee-id";
        testCategoryId = 1L;

        testCategory = MissionCategoryResponse.builder()
            .id(testCategoryId)
            .name("테스트 카테고리")
            .icon("📚")
            .isActive(true)
            .build();

        testPrivateGuild = Guild.builder()
            .name("비공개 길드")
            .description("비공개 길드 설명")
            .visibility(GuildVisibility.PRIVATE)
            .masterId(testMasterId)
            .maxMembers(50)
            .categoryId(testCategoryId)
            .isActive(true)
            .build();
        setId(testPrivateGuild, 1L);

        testPublicGuild = Guild.builder()
            .name("공개 길드")
            .description("공개 길드 설명")
            .visibility(GuildVisibility.PUBLIC)
            .masterId(testMasterId)
            .maxMembers(50)
            .categoryId(testCategoryId)
            .isActive(true)
            .build();
        setId(testPublicGuild, 2L);

        testMasterMember = GuildMember.builder()
            .guild(testPrivateGuild)
            .userId(testMasterId)
            .role(GuildMemberRole.MASTER)
            .status(GuildMemberStatus.ACTIVE)
            .joinedAt(LocalDateTime.now())
            .build();
        setId(testMasterMember, 1L);

        testSubMasterMember = GuildMember.builder()
            .guild(testPrivateGuild)
            .userId(testInviterId)
            .role(GuildMemberRole.SUB_MASTER)
            .status(GuildMemberStatus.ACTIVE)
            .joinedAt(LocalDateTime.now())
            .build();
        setId(testSubMasterMember, 2L);

        testInviter = Users.builder()
            .id(testInviterId)
            .nickname("초대자")
            .email("inviter@test.com")
            .provider("google")
            .build();

        testInvitee = Users.builder()
            .id(testInviteeId)
            .nickname("초대받는사람")
            .email("invitee@test.com")
            .provider("google")
            .build();
    }

    @Nested
    @DisplayName("sendInvitation 테스트")
    class SendInvitationTest {

        @Test
        @DisplayName("마스터가 비공개 길드에 초대를 발송한다")
        void sendInvitation_byMaster_success() {
            // given
            String message = "우리 길드에 함께하시겠어요?";

            when(guildRepository.findById(1L)).thenReturn(Optional.of(testPrivateGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testMasterId))
                .thenReturn(Optional.of(testMasterMember));
            when(userRepository.findById(testInviteeId)).thenReturn(Optional.of(testInvitee));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testInviteeId))
                .thenReturn(Optional.empty());
            when(guildMemberRepository.hasActiveGuildMembershipInCategory(testInviteeId, testCategoryId))
                .thenReturn(false);
            when(invitationRepository.existsByGuildIdAndInviteeIdAndStatus(1L, testInviteeId, GuildInvitationStatus.PENDING))
                .thenReturn(false);
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(10L);
            when(invitationRepository.save(any(GuildInvitation.class))).thenAnswer(invocation -> {
                GuildInvitation invitation = invocation.getArgument(0);
                setId(invitation, 1L);
                return invitation;
            });
            when(userRepository.findById(testMasterId)).thenReturn(Optional.of(
                Users.builder().id(testMasterId).nickname("마스터").email("master@test.com").provider("google").build()
            ));

            // when
            GuildInvitationResponse response = invitationService.sendInvitation(1L, testMasterId, testInviteeId, message);

            // then
            assertThat(response).isNotNull();
            assertThat(response.guildId()).isEqualTo(1L);
            assertThat(response.inviterId()).isEqualTo(testMasterId);
            assertThat(response.inviteeId()).isEqualTo(testInviteeId);
            assertThat(response.message()).isEqualTo(message);
            assertThat(response.status()).isEqualTo(GuildInvitationStatus.PENDING);

            verify(invitationRepository).save(any(GuildInvitation.class));
            verify(eventPublisher).publishEvent(any(GuildInvitationEvent.class));
        }

        @Test
        @DisplayName("부마스터가 비공개 길드에 초대를 발송한다")
        void sendInvitation_bySubMaster_success() {
            // given
            String message = "함께 활동해요!";

            when(guildRepository.findById(1L)).thenReturn(Optional.of(testPrivateGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testInviterId))
                .thenReturn(Optional.of(testSubMasterMember));
            when(userRepository.findById(testInviteeId)).thenReturn(Optional.of(testInvitee));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testInviteeId))
                .thenReturn(Optional.empty());
            when(guildMemberRepository.hasActiveGuildMembershipInCategory(testInviteeId, testCategoryId))
                .thenReturn(false);
            when(invitationRepository.existsByGuildIdAndInviteeIdAndStatus(1L, testInviteeId, GuildInvitationStatus.PENDING))
                .thenReturn(false);
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(10L);
            when(invitationRepository.save(any(GuildInvitation.class))).thenAnswer(invocation -> {
                GuildInvitation invitation = invocation.getArgument(0);
                setId(invitation, 1L);
                return invitation;
            });
            when(userRepository.findById(testInviterId)).thenReturn(Optional.of(testInviter));

            // when
            GuildInvitationResponse response = invitationService.sendInvitation(1L, testInviterId, testInviteeId, message);

            // then
            assertThat(response).isNotNull();
            assertThat(response.inviterNickname()).isEqualTo("초대자");
            verify(invitationRepository).save(any(GuildInvitation.class));
        }

        @Test
        @DisplayName("공개 길드에서는 초대 기능을 사용할 수 없다")
        void sendInvitation_publicGuild_throwsException() {
            // given
            when(guildRepository.findById(2L)).thenReturn(Optional.of(testPublicGuild));

            // when & then
            assertThatThrownBy(() -> invitationService.sendInvitation(2L, testMasterId, testInviteeId, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("공개 길드에서는 초대 기능을 사용할 수 없습니다");

            verify(invitationRepository, never()).save(any(GuildInvitation.class));
        }

        @Test
        @DisplayName("일반 멤버는 초대를 발송할 수 없다")
        void sendInvitation_byNormalMember_throwsException() {
            // given
            String normalMemberId = "normal-member-id";
            GuildMember normalMember = GuildMember.builder()
                .guild(testPrivateGuild)
                .userId(normalMemberId)
                .role(GuildMemberRole.MEMBER)
                .status(GuildMemberStatus.ACTIVE)
                .build();

            when(guildRepository.findById(1L)).thenReturn(Optional.of(testPrivateGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, normalMemberId))
                .thenReturn(Optional.of(normalMember));

            // when & then
            assertThatThrownBy(() -> invitationService.sendInvitation(1L, normalMemberId, testInviteeId, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("길드 마스터 또는 부마스터만");
        }

        @Test
        @DisplayName("존재하지 않는 유저를 초대할 수 없다")
        void sendInvitation_nonExistentUser_throwsException() {
            // given
            when(guildRepository.findById(1L)).thenReturn(Optional.of(testPrivateGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testMasterId))
                .thenReturn(Optional.of(testMasterMember));
            when(userRepository.findById(testInviteeId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> invitationService.sendInvitation(1L, testMasterId, testInviteeId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("초대 대상자를 찾을 수 없습니다");
        }

        @Test
        @DisplayName("이미 길드 멤버인 유저는 초대할 수 없다")
        void sendInvitation_alreadyMember_throwsException() {
            // given
            GuildMember existingMember = GuildMember.builder()
                .guild(testPrivateGuild)
                .userId(testInviteeId)
                .role(GuildMemberRole.MEMBER)
                .status(GuildMemberStatus.ACTIVE)
                .build();

            when(guildRepository.findById(1L)).thenReturn(Optional.of(testPrivateGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testMasterId))
                .thenReturn(Optional.of(testMasterMember));
            when(userRepository.findById(testInviteeId)).thenReturn(Optional.of(testInvitee));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testInviteeId))
                .thenReturn(Optional.of(existingMember));

            // when & then
            assertThatThrownBy(() -> invitationService.sendInvitation(1L, testMasterId, testInviteeId, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 길드 멤버입니다");
        }

        @Test
        @DisplayName("같은 카테고리의 다른 길드에 가입된 유저는 초대할 수 없다")
        void sendInvitation_alreadyInOtherGuild_throwsException() {
            // given
            when(guildRepository.findById(1L)).thenReturn(Optional.of(testPrivateGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testMasterId))
                .thenReturn(Optional.of(testMasterMember));
            when(userRepository.findById(testInviteeId)).thenReturn(Optional.of(testInvitee));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testInviteeId))
                .thenReturn(Optional.empty());
            when(guildMemberRepository.hasActiveGuildMembershipInCategory(testInviteeId, testCategoryId))
                .thenReturn(true);
            when(missionCategoryService.getCategory(testCategoryId)).thenReturn(testCategory);

            // when & then
            assertThatThrownBy(() -> invitationService.sendInvitation(1L, testMasterId, testInviteeId, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("카테고리");
        }

        @Test
        @DisplayName("이미 대기 중인 초대가 있으면 초대할 수 없다")
        void sendInvitation_alreadyPendingInvitation_throwsException() {
            // given
            when(guildRepository.findById(1L)).thenReturn(Optional.of(testPrivateGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testMasterId))
                .thenReturn(Optional.of(testMasterMember));
            when(userRepository.findById(testInviteeId)).thenReturn(Optional.of(testInvitee));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testInviteeId))
                .thenReturn(Optional.empty());
            when(guildMemberRepository.hasActiveGuildMembershipInCategory(testInviteeId, testCategoryId))
                .thenReturn(false);
            when(invitationRepository.existsByGuildIdAndInviteeIdAndStatus(1L, testInviteeId, GuildInvitationStatus.PENDING))
                .thenReturn(true);

            // when & then
            assertThatThrownBy(() -> invitationService.sendInvitation(1L, testMasterId, testInviteeId, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 대기 중인 초대가 있습니다");
        }

        @Test
        @DisplayName("길드 정원이 가득 찬 경우 초대할 수 없다")
        void sendInvitation_guildFull_throwsException() {
            // given
            when(guildRepository.findById(1L)).thenReturn(Optional.of(testPrivateGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testMasterId))
                .thenReturn(Optional.of(testMasterMember));
            when(userRepository.findById(testInviteeId)).thenReturn(Optional.of(testInvitee));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testInviteeId))
                .thenReturn(Optional.empty());
            when(guildMemberRepository.hasActiveGuildMembershipInCategory(testInviteeId, testCategoryId))
                .thenReturn(false);
            when(invitationRepository.existsByGuildIdAndInviteeIdAndStatus(1L, testInviteeId, GuildInvitationStatus.PENDING))
                .thenReturn(false);
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(50L); // maxMembers = 50

            // when & then
            assertThatThrownBy(() -> invitationService.sendInvitation(1L, testMasterId, testInviteeId, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("길드 인원이 가득 찼습니다");
        }

        @Test
        @DisplayName("비활성화된 길드에는 초대할 수 없다")
        void sendInvitation_inactiveGuild_throwsException() {
            // given
            Guild inactiveGuild = Guild.builder()
                .name("비활성 길드")
                .visibility(GuildVisibility.PRIVATE)
                .masterId(testMasterId)
                .categoryId(testCategoryId)
                .isActive(false)
                .build();
            setId(inactiveGuild, 3L);

            when(guildRepository.findById(3L)).thenReturn(Optional.of(inactiveGuild));

            // when & then
            assertThatThrownBy(() -> invitationService.sendInvitation(3L, testMasterId, testInviteeId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("길드를 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("acceptInvitation 테스트")
    class AcceptInvitationTest {

        @Test
        @DisplayName("초대를 수락하고 길드에 가입한다 (신규 가입)")
        void acceptInvitation_newMember_success() {
            // given
            GuildInvitation invitation = GuildInvitation.create(testPrivateGuild, testMasterId, testInviteeId, "초대 메시지");
            setId(invitation, 1L);

            when(invitationRepository.findByIdWithGuild(1L)).thenReturn(Optional.of(invitation));
            when(guildMemberRepository.hasActiveGuildMembershipInCategory(testInviteeId, testCategoryId))
                .thenReturn(false);
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testInviteeId))
                .thenReturn(Optional.empty());
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(10L);
            when(guildMemberRepository.save(any(GuildMember.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(userRepository.findById(testInviteeId)).thenReturn(Optional.of(testInvitee));
            when(userRepository.findById(testMasterId)).thenReturn(Optional.of(
                Users.builder().id(testMasterId).nickname("마스터").email("master@test.com").provider("google").build()
            ));

            // when
            GuildInvitationResponse response = invitationService.acceptInvitation(1L, testInviteeId);

            // then
            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(GuildInvitationStatus.ACCEPTED);
            assertThat(invitation.getStatus()).isEqualTo(GuildInvitationStatus.ACCEPTED);

            verify(guildMemberRepository).save(any(GuildMember.class));
            verify(guildChatService).notifyMemberJoin(eq(1L), eq("초대받는사람"));
        }

        @Test
        @DisplayName("초대를 수락하고 길드에 재가입한다 (탈퇴 후)")
        void acceptInvitation_rejoinAfterLeave_success() {
            // given
            GuildInvitation invitation = GuildInvitation.create(testPrivateGuild, testMasterId, testInviteeId, "재초대");
            setId(invitation, 1L);

            GuildMember leftMember = GuildMember.builder()
                .guild(testPrivateGuild)
                .userId(testInviteeId)
                .role(GuildMemberRole.MEMBER)
                .status(GuildMemberStatus.LEFT)
                .joinedAt(LocalDateTime.now().minusDays(10))
                .leftAt(LocalDateTime.now().minusDays(1))
                .build();

            when(invitationRepository.findByIdWithGuild(1L)).thenReturn(Optional.of(invitation));
            when(guildMemberRepository.hasActiveGuildMembershipInCategory(testInviteeId, testCategoryId))
                .thenReturn(false);
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testInviteeId))
                .thenReturn(Optional.of(leftMember));
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(10L);
            when(userRepository.findById(testInviteeId)).thenReturn(Optional.of(testInvitee));
            when(userRepository.findById(testMasterId)).thenReturn(Optional.of(
                Users.builder().id(testMasterId).nickname("마스터").email("master@test.com").provider("google").build()
            ));

            // when
            GuildInvitationResponse response = invitationService.acceptInvitation(1L, testInviteeId);

            // then
            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(GuildInvitationStatus.ACCEPTED);
            assertThat(leftMember.getStatus()).isEqualTo(GuildMemberStatus.ACTIVE);
            assertThat(leftMember.getRole()).isEqualTo(GuildMemberRole.MEMBER);
            assertThat(leftMember.getLeftAt()).isNull();

            verify(guildMemberRepository, never()).save(any(GuildMember.class));
            verify(guildChatService).notifyMemberJoin(eq(1L), eq("초대받는사람"));
        }

        @Test
        @DisplayName("다른 사람에게 온 초대는 수락할 수 없다")
        void acceptInvitation_notForMe_throwsException() {
            // given
            GuildInvitation invitation = GuildInvitation.create(testPrivateGuild, testMasterId, testInviteeId, null);
            setId(invitation, 1L);

            when(invitationRepository.findByIdWithGuild(1L)).thenReturn(Optional.of(invitation));

            // when & then
            assertThatThrownBy(() -> invitationService.acceptInvitation(1L, "other-user-id"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("본인에게 온 초대만 수락할 수 있습니다");
        }

        @Test
        @DisplayName("이미 처리된 초대는 수락할 수 없다")
        void acceptInvitation_alreadyProcessed_throwsException() {
            // given
            GuildInvitation invitation = GuildInvitation.create(testPrivateGuild, testMasterId, testInviteeId, null);
            setId(invitation, 1L);
            invitation.accept();

            when(invitationRepository.findByIdWithGuild(1L)).thenReturn(Optional.of(invitation));

            // when & then
            assertThatThrownBy(() -> invitationService.acceptInvitation(1L, testInviteeId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 처리된 초대입니다");
        }

        @Test
        @DisplayName("만료된 초대는 수락할 수 없다")
        void acceptInvitation_expired_throwsException() {
            // given
            GuildInvitation invitation = GuildInvitation.builder()
                .guild(testPrivateGuild)
                .inviterId(testMasterId)
                .inviteeId(testInviteeId)
                .status(GuildInvitationStatus.PENDING)
                .expiresAt(LocalDateTime.now().minusDays(1)) // 만료됨
                .build();
            setId(invitation, 1L);

            when(invitationRepository.findByIdWithGuild(1L)).thenReturn(Optional.of(invitation));

            // when & then
            assertThatThrownBy(() -> invitationService.acceptInvitation(1L, testInviteeId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("초대가 만료되었습니다");
        }

        @Test
        @DisplayName("비활성화된 길드의 초대는 수락할 수 없다")
        void acceptInvitation_inactiveGuild_throwsException() {
            // given
            Guild inactiveGuild = Guild.builder()
                .name("비활성 길드")
                .visibility(GuildVisibility.PRIVATE)
                .masterId(testMasterId)
                .categoryId(testCategoryId)
                .isActive(false)
                .build();
            setId(inactiveGuild, 3L);

            GuildInvitation invitation = GuildInvitation.create(inactiveGuild, testMasterId, testInviteeId, null);
            setId(invitation, 1L);

            when(invitationRepository.findByIdWithGuild(1L)).thenReturn(Optional.of(invitation));

            // when & then
            assertThatThrownBy(() -> invitationService.acceptInvitation(1L, testInviteeId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("길드가 비활성화되었습니다");
        }

        @Test
        @DisplayName("같은 카테고리의 다른 길드에 이미 가입된 경우 초대를 수락할 수 없다")
        void acceptInvitation_alreadyInOtherGuild_throwsException() {
            // given
            GuildInvitation invitation = GuildInvitation.create(testPrivateGuild, testMasterId, testInviteeId, null);
            setId(invitation, 1L);

            when(invitationRepository.findByIdWithGuild(1L)).thenReturn(Optional.of(invitation));
            when(guildMemberRepository.hasActiveGuildMembershipInCategory(testInviteeId, testCategoryId))
                .thenReturn(true);
            when(missionCategoryService.getCategory(testCategoryId)).thenReturn(testCategory);

            // when & then
            assertThatThrownBy(() -> invitationService.acceptInvitation(1L, testInviteeId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("카테고리");
        }

        @Test
        @DisplayName("길드 정원이 가득 찬 경우 초대를 수락할 수 없다")
        void acceptInvitation_guildFull_throwsException() {
            // given
            GuildInvitation invitation = GuildInvitation.create(testPrivateGuild, testMasterId, testInviteeId, null);
            setId(invitation, 1L);

            when(invitationRepository.findByIdWithGuild(1L)).thenReturn(Optional.of(invitation));
            when(guildMemberRepository.hasActiveGuildMembershipInCategory(testInviteeId, testCategoryId))
                .thenReturn(false);
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testInviteeId))
                .thenReturn(Optional.empty());
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(50L); // maxMembers = 50

            // when & then
            assertThatThrownBy(() -> invitationService.acceptInvitation(1L, testInviteeId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("길드 인원이 가득 찼습니다");
        }
    }

    @Nested
    @DisplayName("rejectInvitation 테스트")
    class RejectInvitationTest {

        @Test
        @DisplayName("초대를 거절한다")
        void rejectInvitation_success() {
            // given
            GuildInvitation invitation = GuildInvitation.create(testPrivateGuild, testMasterId, testInviteeId, null);
            setId(invitation, 1L);

            when(invitationRepository.findById(1L)).thenReturn(Optional.of(invitation));

            // when
            invitationService.rejectInvitation(1L, testInviteeId);

            // then
            assertThat(invitation.getStatus()).isEqualTo(GuildInvitationStatus.REJECTED);
            assertThat(invitation.getProcessedAt()).isNotNull();
        }

        @Test
        @DisplayName("다른 사람에게 온 초대는 거절할 수 없다")
        void rejectInvitation_notForMe_throwsException() {
            // given
            GuildInvitation invitation = GuildInvitation.create(testPrivateGuild, testMasterId, testInviteeId, null);
            setId(invitation, 1L);

            when(invitationRepository.findById(1L)).thenReturn(Optional.of(invitation));

            // when & then
            assertThatThrownBy(() -> invitationService.rejectInvitation(1L, "other-user-id"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("본인에게 온 초대만 거절할 수 있습니다");
        }

        @Test
        @DisplayName("이미 처리된 초대는 거절할 수 없다")
        void rejectInvitation_alreadyProcessed_throwsException() {
            // given
            GuildInvitation invitation = GuildInvitation.create(testPrivateGuild, testMasterId, testInviteeId, null);
            setId(invitation, 1L);
            invitation.cancel();

            when(invitationRepository.findById(1L)).thenReturn(Optional.of(invitation));

            // when & then
            assertThatThrownBy(() -> invitationService.rejectInvitation(1L, testInviteeId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 처리된 초대입니다");
        }

        @Test
        @DisplayName("존재하지 않는 초대는 거절할 수 없다")
        void rejectInvitation_notFound_throwsException() {
            // given
            when(invitationRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> invitationService.rejectInvitation(999L, testInviteeId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("초대를 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("cancelInvitation 테스트")
    class CancelInvitationTest {

        @Test
        @DisplayName("마스터가 초대를 취소한다")
        void cancelInvitation_byMaster_success() {
            // given
            GuildInvitation invitation = GuildInvitation.create(testPrivateGuild, testMasterId, testInviteeId, null);
            setId(invitation, 1L);

            when(invitationRepository.findByIdWithGuild(1L)).thenReturn(Optional.of(invitation));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testMasterId))
                .thenReturn(Optional.of(testMasterMember));

            // when
            invitationService.cancelInvitation(1L, testMasterId);

            // then
            assertThat(invitation.getStatus()).isEqualTo(GuildInvitationStatus.CANCELLED);
            assertThat(invitation.getProcessedAt()).isNotNull();
        }

        @Test
        @DisplayName("부마스터가 초대를 취소한다")
        void cancelInvitation_bySubMaster_success() {
            // given
            GuildInvitation invitation = GuildInvitation.create(testPrivateGuild, testInviterId, testInviteeId, null);
            setId(invitation, 1L);

            when(invitationRepository.findByIdWithGuild(1L)).thenReturn(Optional.of(invitation));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testInviterId))
                .thenReturn(Optional.of(testSubMasterMember));

            // when
            invitationService.cancelInvitation(1L, testInviterId);

            // then
            assertThat(invitation.getStatus()).isEqualTo(GuildInvitationStatus.CANCELLED);
        }

        @Test
        @DisplayName("일반 멤버는 초대를 취소할 수 없다")
        void cancelInvitation_byNormalMember_throwsException() {
            // given
            String normalMemberId = "normal-member-id";
            GuildMember normalMember = GuildMember.builder()
                .guild(testPrivateGuild)
                .userId(normalMemberId)
                .role(GuildMemberRole.MEMBER)
                .status(GuildMemberStatus.ACTIVE)
                .build();

            GuildInvitation invitation = GuildInvitation.create(testPrivateGuild, testMasterId, testInviteeId, null);
            setId(invitation, 1L);

            when(invitationRepository.findByIdWithGuild(1L)).thenReturn(Optional.of(invitation));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, normalMemberId))
                .thenReturn(Optional.of(normalMember));

            // when & then
            assertThatThrownBy(() -> invitationService.cancelInvitation(1L, normalMemberId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("길드 마스터 또는 부마스터만");
        }

        @Test
        @DisplayName("이미 처리된 초대는 취소할 수 없다")
        void cancelInvitation_alreadyProcessed_throwsException() {
            // given
            GuildInvitation invitation = GuildInvitation.create(testPrivateGuild, testMasterId, testInviteeId, null);
            setId(invitation, 1L);
            invitation.accept();

            when(invitationRepository.findByIdWithGuild(1L)).thenReturn(Optional.of(invitation));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testMasterId))
                .thenReturn(Optional.of(testMasterMember));

            // when & then
            assertThatThrownBy(() -> invitationService.cancelInvitation(1L, testMasterId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 처리된 초대입니다");
        }

        @Test
        @DisplayName("존재하지 않는 초대는 취소할 수 없다")
        void cancelInvitation_notFound_throwsException() {
            // given
            when(invitationRepository.findByIdWithGuild(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> invitationService.cancelInvitation(999L, testMasterId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("초대를 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("getMyPendingInvitations 테스트")
    class GetMyPendingInvitationsTest {

        @Test
        @DisplayName("내 대기 중인 초대 목록을 조회한다")
        void getMyPendingInvitations_success() {
            // given
            GuildInvitation invitation1 = GuildInvitation.create(testPrivateGuild, testMasterId, testInviteeId, "초대1");
            setId(invitation1, 1L);

            Guild anotherGuild = Guild.builder()
                .name("또다른 길드")
                .visibility(GuildVisibility.PRIVATE)
                .masterId("another-master")
                .categoryId(2L)
                .build();
            setId(anotherGuild, 2L);

            GuildInvitation invitation2 = GuildInvitation.create(anotherGuild, "another-master", testInviteeId, "초대2");
            setId(invitation2, 2L);

            when(invitationRepository.findByInviteeIdAndStatusWithGuild(testInviteeId, GuildInvitationStatus.PENDING))
                .thenReturn(List.of(invitation1, invitation2));
            when(userRepository.findAllByIdIn(List.of(testMasterId, "another-master")))
                .thenReturn(List.of(
                    Users.builder().id(testMasterId).nickname("마스터").email("master@test.com").provider("google").build(),
                    Users.builder().id("another-master").nickname("다른마스터").email("other@test.com").provider("google").build()
                ));
            when(userRepository.findById(testInviteeId)).thenReturn(Optional.of(testInvitee));

            // when
            List<GuildInvitationResponse> result = invitationService.getMyPendingInvitations(testInviteeId);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).inviteeNickname()).isEqualTo("초대받는사람");
            assertThat(result.get(0).status()).isEqualTo(GuildInvitationStatus.PENDING);
        }

        @Test
        @DisplayName("만료된 초대는 목록에서 제외된다")
        void getMyPendingInvitations_excludesExpired() {
            // given
            GuildInvitation validInvitation = GuildInvitation.create(testPrivateGuild, testMasterId, testInviteeId, "유효");
            setId(validInvitation, 1L);

            GuildInvitation expiredInvitation = GuildInvitation.builder()
                .guild(testPrivateGuild)
                .inviterId(testMasterId)
                .inviteeId(testInviteeId)
                .message("만료됨")
                .status(GuildInvitationStatus.PENDING)
                .expiresAt(LocalDateTime.now().minusDays(1)) // 만료됨
                .build();
            setId(expiredInvitation, 2L);

            when(invitationRepository.findByInviteeIdAndStatusWithGuild(testInviteeId, GuildInvitationStatus.PENDING))
                .thenReturn(List.of(validInvitation, expiredInvitation));
            when(userRepository.findAllByIdIn(List.of(testMasterId)))
                .thenReturn(List.of(
                    Users.builder().id(testMasterId).nickname("마스터").email("master@test.com").provider("google").build()
                ));
            when(userRepository.findById(testInviteeId)).thenReturn(Optional.of(testInvitee));

            // when
            List<GuildInvitationResponse> result = invitationService.getMyPendingInvitations(testInviteeId);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).message()).isEqualTo("유효");
        }

        @Test
        @DisplayName("대기 중인 초대가 없으면 빈 목록을 반환한다")
        void getMyPendingInvitations_empty() {
            // given
            when(invitationRepository.findByInviteeIdAndStatusWithGuild(testInviteeId, GuildInvitationStatus.PENDING))
                .thenReturn(Collections.emptyList());

            // when
            List<GuildInvitationResponse> result = invitationService.getMyPendingInvitations(testInviteeId);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getGuildPendingInvitations 테스트")
    class GetGuildPendingInvitationsTest {

        @Test
        @DisplayName("마스터가 길드의 대기 중인 초대 목록을 조회한다")
        void getGuildPendingInvitations_byMaster_success() {
            // given
            GuildInvitation invitation = GuildInvitation.create(testPrivateGuild, testMasterId, testInviteeId, "초대");
            setId(invitation, 1L);

            when(guildMemberRepository.findByGuildIdAndUserId(1L, testMasterId))
                .thenReturn(Optional.of(testMasterMember));
            when(invitationRepository.findByGuildIdAndStatus(1L, GuildInvitationStatus.PENDING))
                .thenReturn(List.of(invitation));
            when(userRepository.findAllByIdIn(List.of(testMasterId, testInviteeId)))
                .thenReturn(List.of(
                    Users.builder().id(testMasterId).nickname("마스터").email("master@test.com").provider("google").build(),
                    testInvitee
                ));

            // when
            List<GuildInvitationResponse> result = invitationService.getGuildPendingInvitations(1L, testMasterId);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).inviterNickname()).isEqualTo("마스터");
            assertThat(result.get(0).inviteeNickname()).isEqualTo("초대받는사람");
        }

        @Test
        @DisplayName("부마스터가 길드의 대기 중인 초대 목록을 조회한다")
        void getGuildPendingInvitations_bySubMaster_success() {
            // given
            GuildInvitation invitation = GuildInvitation.create(testPrivateGuild, testInviterId, testInviteeId, "초대");
            setId(invitation, 1L);

            when(guildMemberRepository.findByGuildIdAndUserId(1L, testInviterId))
                .thenReturn(Optional.of(testSubMasterMember));
            when(invitationRepository.findByGuildIdAndStatus(1L, GuildInvitationStatus.PENDING))
                .thenReturn(List.of(invitation));
            when(userRepository.findAllByIdIn(List.of(testInviterId, testInviteeId)))
                .thenReturn(List.of(testInviter, testInvitee));

            // when
            List<GuildInvitationResponse> result = invitationService.getGuildPendingInvitations(1L, testInviterId);

            // then
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("일반 멤버는 길드의 초대 목록을 조회할 수 없다")
        void getGuildPendingInvitations_byNormalMember_throwsException() {
            // given
            String normalMemberId = "normal-member-id";
            GuildMember normalMember = GuildMember.builder()
                .guild(testPrivateGuild)
                .userId(normalMemberId)
                .role(GuildMemberRole.MEMBER)
                .status(GuildMemberStatus.ACTIVE)
                .build();

            when(guildMemberRepository.findByGuildIdAndUserId(1L, normalMemberId))
                .thenReturn(Optional.of(normalMember));

            // when & then
            assertThatThrownBy(() -> invitationService.getGuildPendingInvitations(1L, normalMemberId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("길드 마스터 또는 부마스터만");
        }

        @Test
        @DisplayName("길드 멤버가 아닌 사람은 초대 목록을 조회할 수 없다")
        void getGuildPendingInvitations_notMember_throwsException() {
            // given
            when(guildMemberRepository.findByGuildIdAndUserId(1L, "non-member"))
                .thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> invitationService.getGuildPendingInvitations(1L, "non-member"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("길드 멤버가 아닙니다");
        }
    }

    @Nested
    @DisplayName("validateMasterOrSubMaster 테스트")
    class ValidateMasterOrSubMasterTest {

        @Test
        @DisplayName("비활성 멤버는 권한이 없다")
        void validateMasterOrSubMaster_inactiveMember_throwsException() {
            // given
            GuildMember inactiveMember = GuildMember.builder()
                .guild(testPrivateGuild)
                .userId(testInviterId)
                .role(GuildMemberRole.SUB_MASTER)
                .status(GuildMemberStatus.LEFT)
                .build();

            GuildInvitation invitation = GuildInvitation.create(testPrivateGuild, testInviterId, testInviteeId, null);
            setId(invitation, 1L);

            when(invitationRepository.findByIdWithGuild(1L)).thenReturn(Optional.of(invitation));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testInviterId))
                .thenReturn(Optional.of(inactiveMember));

            // when & then
            assertThatThrownBy(() -> invitationService.cancelInvitation(1L, testInviterId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("길드 멤버가 아닙니다");
        }
    }
}
