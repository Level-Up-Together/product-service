package io.pinkspider.leveluptogethermvp.guildservice.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.guildservice.application.GuildHelper;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildJoinRequestDto;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildJoinRequestResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildMemberResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildJoinRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildMember;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildJoinType;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberRole;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberStatus;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildVisibility;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.JoinRequestStatus;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildJoinRequestRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberRepository;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import io.pinkspider.leveluptogethermvp.metaservice.application.MissionCategoryService;
import io.pinkspider.leveluptogethermvp.metaservice.domain.dto.MissionCategoryResponse;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class GuildMemberServiceTest {

    @Mock
    private GuildMemberRepository guildMemberRepository;

    @Mock
    private GuildJoinRequestRepository joinRequestRepository;

    @Mock
    private MissionCategoryService missionCategoryService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private io.pinkspider.leveluptogethermvp.userservice.achievement.application.TitleService titleService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private GuildHelper guildHelper;

    @InjectMocks
    private GuildMemberService guildMemberService;

    private String testUserId;
    private String testMasterId;
    private Guild testGuild;
    private GuildMember testMasterMember;
    private Long testCategoryId;
    private MissionCategoryResponse testCategory;

    @BeforeEach
    void setUp() {
        testUserId = "test-user-id";
        testMasterId = "test-master-id";
        testCategoryId = 1L;

        testCategory = MissionCategoryResponse.builder()
            .id(testCategoryId)
            .name("테스트 카테고리")
            .icon("📚")
            .isActive(true)
            .build();

        testGuild = Guild.builder()
            .name("테스트 길드")
            .description("테스트 길드 설명")
            .visibility(GuildVisibility.PUBLIC)
            .joinType(GuildJoinType.APPROVAL_REQUIRED)  // 승인 필요 길드로 설정
            .masterId(testMasterId)
            .maxMembers(50)
            .categoryId(testCategoryId)
            .build();
        setId(testGuild, 1L);

        testMasterMember = GuildMember.builder()
            .guild(testGuild)
            .userId(testMasterId)
            .role(GuildMemberRole.MASTER)
            .status(GuildMemberStatus.ACTIVE)
            .joinedAt(LocalDateTime.now())
            .build();
    }

    @Nested
    @DisplayName("길드 가입 신청 테스트")
    class RequestJoinTest {

        @Test
        @DisplayName("정상적으로 가입 신청을 한다")
        void requestJoin_success() {
            // given
            GuildJoinRequestDto joinRequest = GuildJoinRequestDto.builder()
                .message("가입 희망합니다")
                .build();

            when(guildHelper.findActiveGuildById(1L)).thenReturn(testGuild);
            when(guildMemberRepository.hasActiveGuildMembershipInCategory(testUserId, testCategoryId)).thenReturn(false);
            when(guildMemberRepository.isActiveMember(1L, testUserId)).thenReturn(false);
            when(joinRequestRepository.existsByGuildIdAndRequesterIdAndStatus(1L, testUserId, JoinRequestStatus.PENDING)).thenReturn(false);
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(10L);
            when(joinRequestRepository.save(any(GuildJoinRequest.class))).thenAnswer(invocation -> {
                GuildJoinRequest request = invocation.getArgument(0);
                setId(request, 1L);
                return request;
            });

            // when
            GuildJoinRequestResponse response = guildMemberService.requestJoin(1L, testUserId, joinRequest);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(JoinRequestStatus.PENDING);
            verify(joinRequestRepository).save(any(GuildJoinRequest.class));
        }

        @Test
        @DisplayName("카테고리별 1인 1길드 정책: 동일 카테고리의 다른 길드에 가입된 사용자는 가입 신청할 수 없다")
        void requestJoin_failWhenAlreadyInGuildOfSameCategory() {
            // given
            GuildJoinRequestDto joinRequest = GuildJoinRequestDto.builder()
                .message("가입 희망합니다")
                .build();

            when(guildHelper.findActiveGuildById(1L)).thenReturn(testGuild);
            when(guildMemberRepository.hasActiveGuildMembershipInCategory(testUserId, testCategoryId)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> guildMemberService.requestJoin(1L, testUserId, joinRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("카테고리");

            verify(joinRequestRepository, never()).save(any(GuildJoinRequest.class));
        }

        @Test
        @DisplayName("비공개 길드에는 가입 신청할 수 없다")
        void requestJoin_failWhenPrivateGuild() {
            // given
            Guild privateGuild = Guild.builder()
                .name("비공개 길드")
                .description("비공개")
                .visibility(GuildVisibility.PRIVATE)
                .masterId(testMasterId)
                .maxMembers(50)
                .categoryId(testCategoryId)
                .build();
            setId(privateGuild, 2L);

            GuildJoinRequestDto joinRequest = GuildJoinRequestDto.builder()
                .message("가입 희망합니다")
                .build();

            when(guildHelper.findActiveGuildById(2L)).thenReturn(privateGuild);

            // when & then
            assertThatThrownBy(() -> guildMemberService.requestJoin(2L, testUserId, joinRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("비공개 길드는 초대를 통해서만 가입할 수 있습니다");
        }

        @Test
        @DisplayName("이미 길드 멤버인 경우 가입 신청할 수 없다")
        void requestJoin_failWhenAlreadyMember() {
            // given
            GuildJoinRequestDto joinRequest = GuildJoinRequestDto.builder()
                .message("가입 희망합니다")
                .build();

            when(guildHelper.findActiveGuildById(1L)).thenReturn(testGuild);
            when(guildMemberRepository.hasActiveGuildMembershipInCategory(testUserId, testCategoryId)).thenReturn(false);
            when(guildMemberRepository.isActiveMember(1L, testUserId)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> guildMemberService.requestJoin(1L, testUserId, joinRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 길드 멤버입니다");
        }

        @Test
        @DisplayName("길드 인원이 가득 찬 경우 가입 신청할 수 없다")
        void requestJoin_failWhenGuildFull() {
            // given
            GuildJoinRequestDto joinRequest = GuildJoinRequestDto.builder()
                .message("가입 희망합니다")
                .build();

            when(guildHelper.findActiveGuildById(1L)).thenReturn(testGuild);
            when(guildMemberRepository.hasActiveGuildMembershipInCategory(testUserId, testCategoryId)).thenReturn(false);
            when(guildMemberRepository.isActiveMember(1L, testUserId)).thenReturn(false);
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(50L); // maxMembers = 50

            // when & then
            assertThatThrownBy(() -> guildMemberService.requestJoin(1L, testUserId, joinRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("길드 인원이 가득 찼습니다");
        }
    }

    @Nested
    @DisplayName("가입 신청 승인 테스트")
    class ApproveJoinRequestTest {

        @Test
        @DisplayName("정상적으로 가입 신청을 승인한다")
        void approveJoinRequest_success() {
            // given
            GuildJoinRequest joinRequest = GuildJoinRequest.builder()
                .guild(testGuild)
                .requesterId(testUserId)
                .message("가입 희망합니다")
                .build();
            setId(joinRequest, 1L);

            when(joinRequestRepository.findById(1L)).thenReturn(Optional.of(joinRequest));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testMasterId)).thenReturn(Optional.of(testMasterMember));
            when(guildMemberRepository.hasActiveGuildMembershipInCategory(testUserId, testCategoryId)).thenReturn(false);
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(10L);
            when(guildMemberRepository.save(any(GuildMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            GuildMemberResponse response = guildMemberService.approveJoinRequest(1L, testMasterId);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getUserId()).isEqualTo(testUserId);
            assertThat(response.getRole()).isEqualTo(GuildMemberRole.MEMBER);
            verify(guildMemberRepository).save(any(GuildMember.class));
        }

        @Test
        @DisplayName("카테고리별 1인 1길드 정책: 대기 중 동일 카테고리의 다른 길드에 가입한 경우 자동 거절된다")
        void approveJoinRequest_autoRejectWhenAlreadyInOtherGuildOfSameCategory() {
            // given
            GuildJoinRequest joinRequest = GuildJoinRequest.builder()
                .guild(testGuild)
                .requesterId(testUserId)
                .message("가입 희망합니다")
                .build();
            setId(joinRequest, 1L);

            when(joinRequestRepository.findById(1L)).thenReturn(Optional.of(joinRequest));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testMasterId)).thenReturn(Optional.of(testMasterMember));
            when(guildMemberRepository.hasActiveGuildMembershipInCategory(testUserId, testCategoryId)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> guildMemberService.approveJoinRequest(1L, testMasterId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("카테고리");

            assertThat(joinRequest.getStatus()).isEqualTo(JoinRequestStatus.REJECTED);
            verify(guildMemberRepository, never()).save(any(GuildMember.class));
        }

        @Test
        @DisplayName("길드 마스터 또는 부길드마스터만 가입 신청을 승인할 수 있다")
        void approveJoinRequest_failWhenNotMasterOrSubMaster() {
            // given
            GuildJoinRequest joinRequest = GuildJoinRequest.builder()
                .guild(testGuild)
                .requesterId(testUserId)
                .message("가입 희망합니다")
                .build();
            setId(joinRequest, 1L);

            String regularMemberId = "regular-member-id";
            GuildMember regularMember = GuildMember.builder()
                .guild(testGuild)
                .userId(regularMemberId)
                .role(GuildMemberRole.MEMBER)
                .status(GuildMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build();

            when(joinRequestRepository.findById(1L)).thenReturn(Optional.of(joinRequest));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, regularMemberId)).thenReturn(Optional.of(regularMember));

            // when & then
            assertThatThrownBy(() -> guildMemberService.approveJoinRequest(1L, regularMemberId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("길드 마스터 또는 부길드마스터만 이 작업을 수행할 수 있습니다");
        }

        @Test
        @DisplayName("이미 처리된 가입 신청은 승인할 수 없다")
        void approveJoinRequest_failWhenAlreadyProcessed() {
            // given
            GuildJoinRequest joinRequest = GuildJoinRequest.builder()
                .guild(testGuild)
                .requesterId(testUserId)
                .message("가입 희망합니다")
                .build();
            setId(joinRequest, 1L);
            joinRequest.reject(testMasterId, "거절");

            when(joinRequestRepository.findById(1L)).thenReturn(Optional.of(joinRequest));

            // when & then
            assertThatThrownBy(() -> guildMemberService.approveJoinRequest(1L, testMasterId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 처리된 가입 신청입니다");
        }
    }

    @Nested
    @DisplayName("부길드마스터 가입 승인 테스트")
    class ApproveJoinRequestBySubMasterTest {

        @Test
        @DisplayName("부길드마스터도 가입 신청을 승인할 수 있다")
        void approveJoinRequest_bySubMaster_success() {
            // given
            String subMasterId = "sub-master-id";
            GuildMember subMasterMember = GuildMember.builder()
                .guild(testGuild)
                .userId(subMasterId)
                .role(GuildMemberRole.SUB_MASTER)
                .status(GuildMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build();

            GuildJoinRequest joinRequest = GuildJoinRequest.builder()
                .guild(testGuild)
                .requesterId(testUserId)
                .message("가입 희망합니다")
                .build();
            setId(joinRequest, 1L);

            when(joinRequestRepository.findById(1L)).thenReturn(Optional.of(joinRequest));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, subMasterId)).thenReturn(Optional.of(subMasterMember));
            when(guildMemberRepository.hasActiveGuildMembershipInCategory(testUserId, testCategoryId)).thenReturn(false);
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(10L);
            when(guildMemberRepository.save(any(GuildMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            GuildMemberResponse response = guildMemberService.approveJoinRequest(1L, subMasterId);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getUserId()).isEqualTo(testUserId);
            assertThat(response.getRole()).isEqualTo(GuildMemberRole.MEMBER);
            verify(guildMemberRepository).save(any(GuildMember.class));
        }
    }

    @Nested
    @DisplayName("가입 신청 거절 테스트")
    class RejectJoinRequestTest {

        @Test
        @DisplayName("가입 신청을 거절한다")
        void rejectJoinRequest_success() {
            // given
            GuildJoinRequest joinRequest = GuildJoinRequest.builder()
                .guild(testGuild)
                .requesterId(testUserId)
                .status(JoinRequestStatus.PENDING)
                .build();
            setId(joinRequest, 1L);

            when(joinRequestRepository.findById(1L)).thenReturn(Optional.of(joinRequest));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testMasterId))
                .thenReturn(Optional.of(testMasterMember));

            // when
            GuildJoinRequestResponse response = guildMemberService.rejectJoinRequest(1L, testMasterId, "테스트 거절 사유");

            // then
            assertThat(response).isNotNull();
            assertThat(joinRequest.getStatus()).isEqualTo(JoinRequestStatus.REJECTED);
        }

        @Test
        @DisplayName("이미 처리된 가입 신청을 거절하면 예외가 발생한다")
        void rejectJoinRequest_alreadyProcessed_throwsException() {
            // given
            GuildJoinRequest joinRequest = GuildJoinRequest.builder()
                .guild(testGuild)
                .requesterId(testUserId)
                .status(JoinRequestStatus.APPROVED)
                .build();
            setId(joinRequest, 1L);

            when(joinRequestRepository.findById(1L)).thenReturn(Optional.of(joinRequest));

            // when & then
            assertThatThrownBy(() -> guildMemberService.rejectJoinRequest(1L, testMasterId, "거절 사유"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이미 처리된 가입 신청입니다.");
        }
    }

    @Nested
    @DisplayName("길드 탈퇴 테스트")
    class LeaveGuildTest {

        @Test
        @DisplayName("정상적으로 길드를 탈퇴한다")
        void leaveGuild_success() {
            // given
            GuildMember member = GuildMember.builder()
                .guild(testGuild)
                .userId(testUserId)
                .role(GuildMemberRole.MEMBER)
                .status(GuildMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build();

            when(guildHelper.findActiveGuildById(1L)).thenReturn(testGuild);
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testUserId)).thenReturn(Optional.of(member));

            // when
            guildMemberService.leaveGuild(1L, testUserId);

            // then
            assertThat(member.getStatus()).isEqualTo(GuildMemberStatus.LEFT);
        }

        @Test
        @DisplayName("길드 마스터는 탈퇴할 수 없다")
        void leaveGuild_failWhenMaster() {
            // given
            when(guildHelper.findActiveGuildById(1L)).thenReturn(testGuild);

            // when & then
            assertThatThrownBy(() -> guildMemberService.leaveGuild(1L, testMasterId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("길드 마스터는 탈퇴할 수 없습니다");
        }
    }

    @Nested
    @DisplayName("길드 마스터 이전 테스트")
    class TransferMasterTest {

        @Test
        @DisplayName("정상적으로 길드 마스터를 이전한다")
        void transferMaster_success() {
            // given
            String newMasterId = "new-master-id";

            GuildMember newMasterMember = GuildMember.builder()
                .guild(testGuild)
                .userId(newMasterId)
                .role(GuildMemberRole.MEMBER)
                .status(GuildMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build();

            when(guildHelper.findActiveGuildById(1L)).thenReturn(testGuild);
            when(guildMemberRepository.findByGuildIdAndUserId(1L, newMasterId)).thenReturn(Optional.of(newMasterMember));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testMasterId)).thenReturn(Optional.of(testMasterMember));

            // when
            guildMemberService.transferMaster(1L, testMasterId, newMasterId);

            // then
            assertThat(testGuild.getMasterId()).isEqualTo(newMasterId);
            assertThat(newMasterMember.getRole()).isEqualTo(GuildMemberRole.MASTER);
            assertThat(testMasterMember.getRole()).isEqualTo(GuildMemberRole.MEMBER);
        }

        @Test
        @DisplayName("길드 마스터만 마스터 권한을 이전할 수 있다")
        void transferMaster_failWhenNotMaster() {
            // given
            String nonMasterId = "non-master-id";
            String newMasterId = "new-master-id";

            when(guildHelper.findActiveGuildById(1L)).thenReturn(testGuild);
            doThrow(new IllegalStateException("길드 마스터만 이 작업을 수행할 수 있습니다."))
                .when(guildHelper).validateMaster(testGuild, nonMasterId);

            // when & then
            assertThatThrownBy(() -> guildMemberService.transferMaster(1L, nonMasterId, newMasterId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("길드 마스터만 이 작업을 수행할 수 있습니다");
        }
    }

    @Nested
    @DisplayName("부길드마스터 승격 테스트")
    class PromoteToSubMasterTest {

        @Test
        @DisplayName("길드 마스터가 멤버를 부길드마스터로 승격시킨다")
        void promoteToSubMaster_success() {
            // given
            GuildMember targetMember = GuildMember.builder()
                .guild(testGuild)
                .userId(testUserId)
                .role(GuildMemberRole.MEMBER)
                .status(GuildMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build();

            when(guildHelper.findActiveGuildById(1L)).thenReturn(testGuild);
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testUserId)).thenReturn(Optional.of(targetMember));
            when(userRepository.findById(testUserId)).thenReturn(Optional.empty());

            // when
            GuildMemberResponse response = guildMemberService.promoteToSubMaster(1L, testMasterId, testUserId);

            // then
            assertThat(targetMember.getRole()).isEqualTo(GuildMemberRole.SUB_MASTER);
            assertThat(response.getRole()).isEqualTo(GuildMemberRole.SUB_MASTER);
        }

        @Test
        @DisplayName("길드 마스터가 아닌 사람은 승격시킬 수 없다")
        void promoteToSubMaster_failWhenNotMaster() {
            // given
            String nonMasterId = "non-master-id";
            when(guildHelper.findActiveGuildById(1L)).thenReturn(testGuild);
            doThrow(new IllegalStateException("길드 마스터만 이 작업을 수행할 수 있습니다."))
                .when(guildHelper).validateMaster(testGuild, nonMasterId);

            // when & then
            assertThatThrownBy(() -> guildMemberService.promoteToSubMaster(1L, nonMasterId, testUserId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("길드 마스터만");
        }

        @Test
        @DisplayName("이미 부길드마스터인 멤버는 승격할 수 없다")
        void promoteToSubMaster_failWhenAlreadySubMaster() {
            // given
            GuildMember subMasterMember = GuildMember.builder()
                .guild(testGuild)
                .userId(testUserId)
                .role(GuildMemberRole.SUB_MASTER)
                .status(GuildMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build();

            when(guildHelper.findActiveGuildById(1L)).thenReturn(testGuild);
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testUserId)).thenReturn(Optional.of(subMasterMember));

            // when & then
            assertThatThrownBy(() -> guildMemberService.promoteToSubMaster(1L, testMasterId, testUserId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 부길드마스터입니다");
        }
    }

    @Nested
    @DisplayName("부길드마스터 강등 테스트")
    class DemoteFromSubMasterTest {

        @Test
        @DisplayName("길드 마스터가 부길드마스터를 일반 멤버로 강등시킨다")
        void demoteFromSubMaster_success() {
            // given
            GuildMember subMasterMember = GuildMember.builder()
                .guild(testGuild)
                .userId(testUserId)
                .role(GuildMemberRole.SUB_MASTER)
                .status(GuildMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build();

            when(guildHelper.findActiveGuildById(1L)).thenReturn(testGuild);
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testUserId)).thenReturn(Optional.of(subMasterMember));
            when(userRepository.findById(testUserId)).thenReturn(Optional.empty());

            // when
            GuildMemberResponse response = guildMemberService.demoteFromSubMaster(1L, testMasterId, testUserId);

            // then
            assertThat(subMasterMember.getRole()).isEqualTo(GuildMemberRole.MEMBER);
            assertThat(response.getRole()).isEqualTo(GuildMemberRole.MEMBER);
        }

        @Test
        @DisplayName("부길드마스터가 아닌 멤버는 강등할 수 없다")
        void demoteFromSubMaster_failWhenNotSubMaster() {
            // given
            GuildMember normalMember = GuildMember.builder()
                .guild(testGuild)
                .userId(testUserId)
                .role(GuildMemberRole.MEMBER)
                .status(GuildMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build();

            when(guildHelper.findActiveGuildById(1L)).thenReturn(testGuild);
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testUserId)).thenReturn(Optional.of(normalMember));

            // when & then
            assertThatThrownBy(() -> guildMemberService.demoteFromSubMaster(1L, testMasterId, testUserId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("부길드마스터만 강등할 수 있습니다");
        }
    }

    @Nested
    @DisplayName("멤버 추방 테스트")
    class KickMemberTest {

        @Test
        @DisplayName("길드 마스터가 일반 멤버를 추방한다")
        void kickMember_byMaster_success() {
            // given
            GuildMember targetMember = GuildMember.builder()
                .guild(testGuild)
                .userId(testUserId)
                .role(GuildMemberRole.MEMBER)
                .status(GuildMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build();

            when(guildHelper.findActiveGuildById(1L)).thenReturn(testGuild);
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testMasterId)).thenReturn(Optional.of(testMasterMember));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testUserId)).thenReturn(Optional.of(targetMember));

            // when
            guildMemberService.kickMember(1L, testMasterId, testUserId);

            // then
            assertThat(targetMember.getStatus()).isEqualTo(GuildMemberStatus.KICKED);
        }

        @Test
        @DisplayName("부길드마스터가 일반 멤버를 추방한다")
        void kickMember_bySubMaster_success() {
            // given
            String subMasterId = "sub-master-id";
            GuildMember subMasterMember = GuildMember.builder()
                .guild(testGuild)
                .userId(subMasterId)
                .role(GuildMemberRole.SUB_MASTER)
                .status(GuildMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build();

            GuildMember targetMember = GuildMember.builder()
                .guild(testGuild)
                .userId(testUserId)
                .role(GuildMemberRole.MEMBER)
                .status(GuildMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build();

            when(guildHelper.findActiveGuildById(1L)).thenReturn(testGuild);
            when(guildMemberRepository.findByGuildIdAndUserId(1L, subMasterId)).thenReturn(Optional.of(subMasterMember));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testUserId)).thenReturn(Optional.of(targetMember));

            // when
            guildMemberService.kickMember(1L, subMasterId, testUserId);

            // then
            assertThat(targetMember.getStatus()).isEqualTo(GuildMemberStatus.KICKED);
        }

        @Test
        @DisplayName("부길드마스터는 다른 부길드마스터를 추방할 수 없다")
        void kickMember_subMasterCannotKickSubMaster() {
            // given
            String subMasterId1 = "sub-master-id-1";
            String subMasterId2 = "sub-master-id-2";
            GuildMember subMasterMember1 = GuildMember.builder()
                .guild(testGuild)
                .userId(subMasterId1)
                .role(GuildMemberRole.SUB_MASTER)
                .status(GuildMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build();

            GuildMember subMasterMember2 = GuildMember.builder()
                .guild(testGuild)
                .userId(subMasterId2)
                .role(GuildMemberRole.SUB_MASTER)
                .status(GuildMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build();

            when(guildHelper.findActiveGuildById(1L)).thenReturn(testGuild);
            when(guildMemberRepository.findByGuildIdAndUserId(1L, subMasterId1)).thenReturn(Optional.of(subMasterMember1));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, subMasterId2)).thenReturn(Optional.of(subMasterMember2));

            // when & then
            assertThatThrownBy(() -> guildMemberService.kickMember(1L, subMasterId1, subMasterId2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("부길드마스터는 다른 부길드마스터나 길드 마스터를 추방할 수 없습니다");
        }

        @Test
        @DisplayName("일반 멤버는 추방 권한이 없다")
        void kickMember_memberCannotKick() {
            // given
            String memberId = "member-id";
            GuildMember normalMember = GuildMember.builder()
                .guild(testGuild)
                .userId(memberId)
                .role(GuildMemberRole.MEMBER)
                .status(GuildMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build();

            when(guildHelper.findActiveGuildById(1L)).thenReturn(testGuild);
            when(guildMemberRepository.findByGuildIdAndUserId(1L, memberId)).thenReturn(Optional.of(normalMember));

            // when & then
            assertThatThrownBy(() -> guildMemberService.kickMember(1L, memberId, testUserId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("길드 마스터 또는 부길드마스터만 멤버를 추방할 수 있습니다");
        }

        @Test
        @DisplayName("자기 자신을 추방할 수 없다")
        void kickMember_cannotKickSelf() {
            // given
            when(guildHelper.findActiveGuildById(1L)).thenReturn(testGuild);

            // when & then
            assertThatThrownBy(() -> guildMemberService.kickMember(1L, testMasterId, testMasterId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("자기 자신을 추방할 수 없습니다");
        }
    }

    @Nested
    @DisplayName("가입 신청 대기 목록 조회 테스트")
    class GetPendingJoinRequestsTest {

        @Test
        @DisplayName("가입 신청 대기 목록을 조회한다")
        void getPendingJoinRequests_success() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            GuildJoinRequest joinRequest = GuildJoinRequest.builder()
                .guild(testGuild)
                .requesterId(testUserId)
                .message("가입 희망합니다")
                .status(JoinRequestStatus.PENDING)
                .build();
            setId(joinRequest, 1L);

            when(guildHelper.findActiveGuildById(1L)).thenReturn(testGuild);
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testMasterId))
                .thenReturn(Optional.of(testMasterMember));
            when(joinRequestRepository.findPendingRequests(eq(1L), any(Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(joinRequest)));

            // when
            org.springframework.data.domain.Page<GuildJoinRequestResponse> result =
                guildMemberService.getPendingJoinRequests(1L, testMasterId, pageable);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getStatus()).isEqualTo(JoinRequestStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("길드 재가입 테스트")
    class RejoinGuildTest {

        @Test
        @DisplayName("OPEN 길드에서 탈퇴 후 재가입하면 기존 멤버십이 재활성화된다")
        void requestJoin_openGuild_rejoinAfterLeave_success() {
            // given
            Guild openGuild = Guild.builder()
                .name("오픈 길드")
                .description("오픈 길드 설명")
                .visibility(GuildVisibility.PUBLIC)
                .joinType(GuildJoinType.OPEN)
                .masterId(testMasterId)
                .maxMembers(50)
                .categoryId(testCategoryId)
                .build();
            setId(openGuild, 1L);

            GuildMember leftMember = GuildMember.builder()
                .guild(openGuild)
                .userId(testUserId)
                .role(GuildMemberRole.MEMBER)
                .status(GuildMemberStatus.LEFT)
                .joinedAt(LocalDateTime.now().minusDays(10))
                .leftAt(LocalDateTime.now().minusDays(1))
                .build();
            setId(leftMember, 1L);

            GuildJoinRequestDto joinRequest = GuildJoinRequestDto.builder().build();

            when(guildHelper.findActiveGuildById(1L)).thenReturn(openGuild);
            when(guildMemberRepository.hasActiveGuildMembershipInCategory(testUserId, testCategoryId)).thenReturn(false);
            when(guildMemberRepository.isActiveMember(1L, testUserId)).thenReturn(false);
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(10L);
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testUserId)).thenReturn(Optional.of(leftMember));

            // when
            GuildJoinRequestResponse response = guildMemberService.requestJoin(1L, testUserId, joinRequest);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(JoinRequestStatus.APPROVED);
            assertThat(response.getIsMember()).isTrue();
            assertThat(response.getCurrentMemberCount()).isNotNull();
            assertThat(response.getGuildName()).isEqualTo("오픈 길드");
            assertThat(leftMember.getStatus()).isEqualTo(GuildMemberStatus.ACTIVE);
            assertThat(leftMember.getRole()).isEqualTo(GuildMemberRole.MEMBER);
            assertThat(leftMember.getLeftAt()).isNull();
            // save가 호출되지 않음 (기존 레코드 업데이트)
            verify(guildMemberRepository, never()).save(any(GuildMember.class));
        }

        @Test
        @DisplayName("OPEN 길드에서 추방 후 재가입하면 기존 멤버십이 재활성화된다")
        void requestJoin_openGuild_rejoinAfterKick_success() {
            // given
            Guild openGuild = Guild.builder()
                .name("오픈 길드")
                .description("오픈 길드 설명")
                .visibility(GuildVisibility.PUBLIC)
                .joinType(GuildJoinType.OPEN)
                .masterId(testMasterId)
                .maxMembers(50)
                .categoryId(testCategoryId)
                .build();
            setId(openGuild, 1L);

            GuildMember kickedMember = GuildMember.builder()
                .guild(openGuild)
                .userId(testUserId)
                .role(GuildMemberRole.MEMBER)
                .status(GuildMemberStatus.KICKED)
                .joinedAt(LocalDateTime.now().minusDays(10))
                .leftAt(LocalDateTime.now().minusDays(1))
                .build();
            setId(kickedMember, 1L);

            GuildJoinRequestDto joinRequest = GuildJoinRequestDto.builder().build();

            when(guildHelper.findActiveGuildById(1L)).thenReturn(openGuild);
            when(guildMemberRepository.hasActiveGuildMembershipInCategory(testUserId, testCategoryId)).thenReturn(false);
            when(guildMemberRepository.isActiveMember(1L, testUserId)).thenReturn(false);
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(10L);
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testUserId)).thenReturn(Optional.of(kickedMember));

            // when
            GuildJoinRequestResponse response = guildMemberService.requestJoin(1L, testUserId, joinRequest);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(JoinRequestStatus.APPROVED);
            assertThat(response.getIsMember()).isTrue();
            assertThat(response.getCurrentMemberCount()).isNotNull();
            assertThat(kickedMember.getStatus()).isEqualTo(GuildMemberStatus.ACTIVE);
            assertThat(kickedMember.getRole()).isEqualTo(GuildMemberRole.MEMBER);
            assertThat(kickedMember.getLeftAt()).isNull();
        }

        @Test
        @DisplayName("APPROVAL_REQUIRED 길드에서 탈퇴 후 재가입 승인 시 기존 멤버십이 재활성화된다")
        void approveJoinRequest_rejoinAfterLeave_success() {
            // given
            GuildMember leftMember = GuildMember.builder()
                .guild(testGuild)
                .userId(testUserId)
                .role(GuildMemberRole.MEMBER)
                .status(GuildMemberStatus.LEFT)
                .joinedAt(LocalDateTime.now().minusDays(10))
                .leftAt(LocalDateTime.now().minusDays(1))
                .build();
            setId(leftMember, 1L);

            GuildJoinRequest joinRequest = GuildJoinRequest.builder()
                .guild(testGuild)
                .requesterId(testUserId)
                .message("재가입 희망합니다")
                .build();
            setId(joinRequest, 1L);

            when(joinRequestRepository.findById(1L)).thenReturn(Optional.of(joinRequest));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testMasterId)).thenReturn(Optional.of(testMasterMember));
            when(guildMemberRepository.hasActiveGuildMembershipInCategory(testUserId, testCategoryId)).thenReturn(false);
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(10L);
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testUserId)).thenReturn(Optional.of(leftMember));

            // when
            GuildMemberResponse response = guildMemberService.approveJoinRequest(1L, testMasterId);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getUserId()).isEqualTo(testUserId);
            assertThat(response.getRole()).isEqualTo(GuildMemberRole.MEMBER);
            assertThat(leftMember.getStatus()).isEqualTo(GuildMemberStatus.ACTIVE);
            assertThat(leftMember.getLeftAt()).isNull();
            // save가 호출되지 않음 (기존 레코드 업데이트)
            verify(guildMemberRepository, never()).save(any(GuildMember.class));
        }

        @Test
        @DisplayName("신규 가입자에 대한 OPEN 길드 가입은 새 멤버를 생성한다")
        void requestJoin_openGuild_newMember_createsMembership() {
            // given
            Guild openGuild = Guild.builder()
                .name("오픈 길드")
                .description("오픈 길드 설명")
                .visibility(GuildVisibility.PUBLIC)
                .joinType(GuildJoinType.OPEN)
                .masterId(testMasterId)
                .maxMembers(50)
                .categoryId(testCategoryId)
                .build();
            setId(openGuild, 1L);

            GuildJoinRequestDto joinRequest = GuildJoinRequestDto.builder().build();

            when(guildHelper.findActiveGuildById(1L)).thenReturn(openGuild);
            when(guildMemberRepository.hasActiveGuildMembershipInCategory(testUserId, testCategoryId)).thenReturn(false);
            when(guildMemberRepository.isActiveMember(1L, testUserId)).thenReturn(false);
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(10L);
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testUserId)).thenReturn(Optional.empty());
            when(guildMemberRepository.save(any(GuildMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            GuildJoinRequestResponse response = guildMemberService.requestJoin(1L, testUserId, joinRequest);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(JoinRequestStatus.APPROVED);
            assertThat(response.getIsMember()).isTrue();
            assertThat(response.getCurrentMemberCount()).isNotNull();
            assertThat(response.getGuildName()).isEqualTo("오픈 길드");
            verify(guildMemberRepository).save(any(GuildMember.class));
        }

        @Test
        @DisplayName("신규 가입자에 대한 APPROVAL_REQUIRED 길드 승인은 새 멤버를 생성한다")
        void approveJoinRequest_newMember_createsMembership() {
            // given
            GuildJoinRequest joinRequest = GuildJoinRequest.builder()
                .guild(testGuild)
                .requesterId(testUserId)
                .message("가입 희망합니다")
                .build();
            setId(joinRequest, 1L);

            when(joinRequestRepository.findById(1L)).thenReturn(Optional.of(joinRequest));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testMasterId)).thenReturn(Optional.of(testMasterMember));
            when(guildMemberRepository.hasActiveGuildMembershipInCategory(testUserId, testCategoryId)).thenReturn(false);
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(10L);
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testUserId)).thenReturn(Optional.empty());
            when(guildMemberRepository.save(any(GuildMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            GuildMemberResponse response = guildMemberService.approveJoinRequest(1L, testMasterId);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getUserId()).isEqualTo(testUserId);
            assertThat(response.getRole()).isEqualTo(GuildMemberRole.MEMBER);
            verify(guildMemberRepository).save(any(GuildMember.class));
        }
    }
}
