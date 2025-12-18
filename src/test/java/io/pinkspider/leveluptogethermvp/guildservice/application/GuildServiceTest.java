package io.pinkspider.leveluptogethermvp.guildservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildCreateRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildJoinRequestDto;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildJoinRequestResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildMemberResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildJoinRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildMember;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberRole;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberStatus;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildVisibility;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.JoinRequestStatus;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildJoinRequestRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

@ExtendWith(MockitoExtension.class)
class GuildServiceTest {

    @Mock
    private GuildRepository guildRepository;

    @Mock
    private GuildMemberRepository guildMemberRepository;

    @Mock
    private GuildJoinRequestRepository joinRequestRepository;

    @Mock
    private ApplicationContext applicationContext;

    @InjectMocks
    private GuildService guildService;

    private String testUserId;
    private String testMasterId;
    private Guild testGuild;
    private GuildMember testMasterMember;

    @BeforeEach
    void setUp() {
        testUserId = "test-user-id";
        testMasterId = "test-master-id";

        testGuild = Guild.builder()
            .name("테스트 길드")
            .description("테스트 길드 설명")
            .visibility(GuildVisibility.PUBLIC)
            .masterId(testMasterId)
            .maxMembers(50)
            .build();
        setGuildId(testGuild, 1L);

        testMasterMember = GuildMember.builder()
            .guild(testGuild)
            .userId(testMasterId)
            .role(GuildMemberRole.MASTER)
            .status(GuildMemberStatus.ACTIVE)
            .joinedAt(LocalDateTime.now())
            .build();
    }

    private void setGuildId(Guild guild, Long id) {
        try {
            java.lang.reflect.Field idField = Guild.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(guild, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setJoinRequestId(GuildJoinRequest request, Long id) {
        try {
            java.lang.reflect.Field idField = GuildJoinRequest.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(request, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("길드 생성 테스트")
    class CreateGuildTest {

        @Test
        @DisplayName("정상적으로 길드를 생성한다")
        void createGuild_success() {
            // given
            GuildCreateRequest request = GuildCreateRequest.builder()
                .name("새 길드")
                .description("새 길드 설명")
                .visibility(GuildVisibility.PUBLIC)
                .maxMembers(30)
                .build();

            when(guildMemberRepository.hasActiveGuildMembership(testUserId)).thenReturn(false);
            when(guildRepository.existsByNameAndIsActiveTrue("새 길드")).thenReturn(false);
            when(guildRepository.save(any(Guild.class))).thenAnswer(invocation -> {
                Guild guild = invocation.getArgument(0);
                setGuildId(guild, 1L);
                return guild;
            });
            when(guildMemberRepository.save(any(GuildMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            GuildResponse response = guildService.createGuild(testUserId, request);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getName()).isEqualTo("새 길드");
            assertThat(response.getMasterId()).isEqualTo(testUserId);
            verify(guildRepository).save(any(Guild.class));
            verify(guildMemberRepository).save(any(GuildMember.class));
        }

        @Test
        @DisplayName("1인 1길드 정책: 이미 다른 길드에 가입된 사용자는 길드를 생성할 수 없다")
        void createGuild_failWhenAlreadyInGuild() {
            // given
            GuildCreateRequest request = GuildCreateRequest.builder()
                .name("새 길드")
                .description("새 길드 설명")
                .visibility(GuildVisibility.PUBLIC)
                .build();

            when(guildMemberRepository.hasActiveGuildMembership(testUserId)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> guildService.createGuild(testUserId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 다른 길드에 가입되어 있습니다");

            verify(guildRepository, never()).save(any(Guild.class));
        }

        @Test
        @DisplayName("중복된 길드명으로 생성 시 예외 발생")
        void createGuild_failWhenDuplicateName() {
            // given
            GuildCreateRequest request = GuildCreateRequest.builder()
                .name("중복 길드")
                .description("설명")
                .visibility(GuildVisibility.PUBLIC)
                .build();

            when(guildMemberRepository.hasActiveGuildMembership(testUserId)).thenReturn(false);
            when(guildRepository.existsByNameAndIsActiveTrue("중복 길드")).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> guildService.createGuild(testUserId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 존재하는 길드명입니다");
        }
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

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.hasActiveGuildMembership(testUserId)).thenReturn(false);
            when(guildMemberRepository.isActiveMember(1L, testUserId)).thenReturn(false);
            when(joinRequestRepository.existsByGuildIdAndRequesterIdAndStatus(1L, testUserId, JoinRequestStatus.PENDING)).thenReturn(false);
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(10L);
            when(joinRequestRepository.save(any(GuildJoinRequest.class))).thenAnswer(invocation -> {
                GuildJoinRequest request = invocation.getArgument(0);
                setJoinRequestId(request, 1L);
                return request;
            });

            // when
            GuildJoinRequestResponse response = guildService.requestJoin(1L, testUserId, joinRequest);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(JoinRequestStatus.PENDING);
            verify(joinRequestRepository).save(any(GuildJoinRequest.class));
        }

        @Test
        @DisplayName("1인 1길드 정책: 이미 다른 길드에 가입된 사용자는 가입 신청할 수 없다")
        void requestJoin_failWhenAlreadyInGuild() {
            // given
            GuildJoinRequestDto joinRequest = GuildJoinRequestDto.builder()
                .message("가입 희망합니다")
                .build();

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.hasActiveGuildMembership(testUserId)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> guildService.requestJoin(1L, testUserId, joinRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 다른 길드에 가입되어 있습니다");

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
                .build();
            setGuildId(privateGuild, 2L);

            GuildJoinRequestDto joinRequest = GuildJoinRequestDto.builder()
                .message("가입 희망합니다")
                .build();

            when(guildRepository.findByIdAndIsActiveTrue(2L)).thenReturn(Optional.of(privateGuild));

            // when & then
            assertThatThrownBy(() -> guildService.requestJoin(2L, testUserId, joinRequest))
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

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.hasActiveGuildMembership(testUserId)).thenReturn(false);
            when(guildMemberRepository.isActiveMember(1L, testUserId)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> guildService.requestJoin(1L, testUserId, joinRequest))
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

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.hasActiveGuildMembership(testUserId)).thenReturn(false);
            when(guildMemberRepository.isActiveMember(1L, testUserId)).thenReturn(false);
            when(joinRequestRepository.existsByGuildIdAndRequesterIdAndStatus(1L, testUserId, JoinRequestStatus.PENDING)).thenReturn(false);
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(50L); // maxMembers = 50

            // when & then
            assertThatThrownBy(() -> guildService.requestJoin(1L, testUserId, joinRequest))
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
            setJoinRequestId(joinRequest, 1L);

            when(joinRequestRepository.findById(1L)).thenReturn(Optional.of(joinRequest));
            when(guildMemberRepository.hasActiveGuildMembership(testUserId)).thenReturn(false);
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(10L);
            when(guildMemberRepository.save(any(GuildMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            GuildMemberResponse response = guildService.approveJoinRequest(1L, testMasterId);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getUserId()).isEqualTo(testUserId);
            assertThat(response.getRole()).isEqualTo(GuildMemberRole.MEMBER);
            verify(guildMemberRepository).save(any(GuildMember.class));
        }

        @Test
        @DisplayName("1인 1길드 정책: 대기 중 다른 길드에 가입한 경우 자동 거절된다")
        void approveJoinRequest_autoRejectWhenAlreadyInOtherGuild() {
            // given
            GuildJoinRequest joinRequest = GuildJoinRequest.builder()
                .guild(testGuild)
                .requesterId(testUserId)
                .message("가입 희망합니다")
                .build();
            setJoinRequestId(joinRequest, 1L);

            when(joinRequestRepository.findById(1L)).thenReturn(Optional.of(joinRequest));
            when(guildMemberRepository.hasActiveGuildMembership(testUserId)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> guildService.approveJoinRequest(1L, testMasterId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("신청자가 이미 다른 길드에 가입되어 있어 자동 거절되었습니다");

            assertThat(joinRequest.getStatus()).isEqualTo(JoinRequestStatus.REJECTED);
            verify(guildMemberRepository, never()).save(any(GuildMember.class));
        }

        @Test
        @DisplayName("길드 마스터만 가입 신청을 승인할 수 있다")
        void approveJoinRequest_failWhenNotMaster() {
            // given
            GuildJoinRequest joinRequest = GuildJoinRequest.builder()
                .guild(testGuild)
                .requesterId(testUserId)
                .message("가입 희망합니다")
                .build();
            setJoinRequestId(joinRequest, 1L);

            String nonMasterId = "non-master-id";

            when(joinRequestRepository.findById(1L)).thenReturn(Optional.of(joinRequest));

            // when & then
            assertThatThrownBy(() -> guildService.approveJoinRequest(1L, nonMasterId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("길드 마스터만 이 작업을 수행할 수 있습니다");
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
            setJoinRequestId(joinRequest, 1L);
            joinRequest.reject(testMasterId, "거절");

            when(joinRequestRepository.findById(1L)).thenReturn(Optional.of(joinRequest));

            // when & then
            assertThatThrownBy(() -> guildService.approveJoinRequest(1L, testMasterId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 처리된 가입 신청입니다");
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

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testUserId)).thenReturn(Optional.of(member));

            // when
            guildService.leaveGuild(1L, testUserId);

            // then
            assertThat(member.getStatus()).isEqualTo(GuildMemberStatus.LEFT);
        }

        @Test
        @DisplayName("길드 마스터는 탈퇴할 수 없다")
        void leaveGuild_failWhenMaster() {
            // given
            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));

            // when & then
            assertThatThrownBy(() -> guildService.leaveGuild(1L, testMasterId))
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

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, newMasterId)).thenReturn(Optional.of(newMasterMember));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testMasterId)).thenReturn(Optional.of(testMasterMember));

            // when
            guildService.transferMaster(1L, testMasterId, newMasterId);

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

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));

            // when & then
            assertThatThrownBy(() -> guildService.transferMaster(1L, nonMasterId, newMasterId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("길드 마스터만 이 작업을 수행할 수 있습니다");
        }
    }
}
