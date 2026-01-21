package io.pinkspider.leveluptogethermvp.guildservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildCreateRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildJoinRequestDto;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildUpdateRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildJoinRequestResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildMemberResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildResponse;
import io.pinkspider.leveluptogethermvp.supportservice.report.application.ReportService;
import io.pinkspider.leveluptogethermvp.supportservice.report.api.dto.ReportTargetType;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildJoinRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildLevelConfig;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildMember;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildJoinType;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberRole;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberStatus;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildVisibility;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.JoinRequestStatus;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildJoinRequestRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildLevelConfigRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import io.pinkspider.leveluptogethermvp.adminservice.domain.entity.FeaturedGuild;
import io.pinkspider.leveluptogethermvp.adminservice.infrastructure.FeaturedGuildRepository;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionCategoryService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionCategoryResponse;
import io.pinkspider.leveluptogethermvp.profanity.application.ProfanityValidationService;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private GuildLevelConfigRepository levelConfigRepository;

    @Mock
    private ProfanityValidationService profanityValidationService;

    @Mock
    private MissionCategoryService missionCategoryService;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private GuildHeadquartersService guildHeadquartersService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FeaturedGuildRepository featuredGuildRepository;

    @Mock
    private io.pinkspider.leveluptogethermvp.userservice.achievement.application.TitleService titleService;

    @Mock
    private GuildImageStorageService guildImageStorageService;

    @Mock
    private GuildChatService guildChatService;

    @Mock
    private ReportService reportService;

    @InjectMocks
    private GuildService guildService;

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
                .categoryId(testCategoryId)
                .build();

            when(missionCategoryService.getCategory(testCategoryId)).thenReturn(testCategory);
            when(guildMemberRepository.hasActiveGuildMembershipInCategory(testUserId, testCategoryId)).thenReturn(false);
            when(guildRepository.existsByNameAndIsActiveTrue("새 길드")).thenReturn(false);
            when(levelConfigRepository.findByLevel(1)).thenReturn(Optional.of(
                GuildLevelConfig.builder().level(1).maxMembers(20).build()));
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
            assertThat(response.getCategoryId()).isEqualTo(testCategoryId);
            verify(guildRepository).save(any(Guild.class));
            verify(guildMemberRepository).save(any(GuildMember.class));
        }

        @Test
        @DisplayName("카테고리별 1인 1길드 정책: 동일 카테고리의 다른 길드에 가입된 사용자는 길드를 생성할 수 없다")
        void createGuild_failWhenAlreadyInGuildOfSameCategory() {
            // given
            GuildCreateRequest request = GuildCreateRequest.builder()
                .name("새 길드")
                .description("새 길드 설명")
                .visibility(GuildVisibility.PUBLIC)
                .categoryId(testCategoryId)
                .build();

            when(missionCategoryService.getCategory(testCategoryId)).thenReturn(testCategory);
            when(guildMemberRepository.hasActiveGuildMembershipInCategory(testUserId, testCategoryId)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> guildService.createGuild(testUserId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("카테고리");

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
                .categoryId(testCategoryId)
                .build();

            when(missionCategoryService.getCategory(testCategoryId)).thenReturn(testCategory);
            when(guildMemberRepository.hasActiveGuildMembershipInCategory(testUserId, testCategoryId)).thenReturn(false);
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
            when(guildMemberRepository.hasActiveGuildMembershipInCategory(testUserId, testCategoryId)).thenReturn(false);
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
        @DisplayName("카테고리별 1인 1길드 정책: 동일 카테고리의 다른 길드에 가입된 사용자는 가입 신청할 수 없다")
        void requestJoin_failWhenAlreadyInGuildOfSameCategory() {
            // given
            GuildJoinRequestDto joinRequest = GuildJoinRequestDto.builder()
                .message("가입 희망합니다")
                .build();

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.hasActiveGuildMembershipInCategory(testUserId, testCategoryId)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> guildService.requestJoin(1L, testUserId, joinRequest))
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
            when(guildMemberRepository.hasActiveGuildMembershipInCategory(testUserId, testCategoryId)).thenReturn(false);
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
            when(guildMemberRepository.hasActiveGuildMembershipInCategory(testUserId, testCategoryId)).thenReturn(false);
            when(guildMemberRepository.isActiveMember(1L, testUserId)).thenReturn(false);
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
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testMasterId)).thenReturn(Optional.of(testMasterMember));
            when(guildMemberRepository.hasActiveGuildMembershipInCategory(testUserId, testCategoryId)).thenReturn(false);
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
        @DisplayName("카테고리별 1인 1길드 정책: 대기 중 동일 카테고리의 다른 길드에 가입한 경우 자동 거절된다")
        void approveJoinRequest_autoRejectWhenAlreadyInOtherGuildOfSameCategory() {
            // given
            GuildJoinRequest joinRequest = GuildJoinRequest.builder()
                .guild(testGuild)
                .requesterId(testUserId)
                .message("가입 희망합니다")
                .build();
            setJoinRequestId(joinRequest, 1L);

            when(joinRequestRepository.findById(1L)).thenReturn(Optional.of(joinRequest));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testMasterId)).thenReturn(Optional.of(testMasterMember));
            when(guildMemberRepository.hasActiveGuildMembershipInCategory(testUserId, testCategoryId)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> guildService.approveJoinRequest(1L, testMasterId))
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
            setJoinRequestId(joinRequest, 1L);

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
            assertThatThrownBy(() -> guildService.approveJoinRequest(1L, regularMemberId))
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

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testUserId)).thenReturn(Optional.of(targetMember));
            when(userRepository.findById(testUserId)).thenReturn(Optional.empty());

            // when
            GuildMemberResponse response = guildService.promoteToSubMaster(1L, testMasterId, testUserId);

            // then
            assertThat(targetMember.getRole()).isEqualTo(GuildMemberRole.SUB_MASTER);
            assertThat(response.getRole()).isEqualTo(GuildMemberRole.SUB_MASTER);
        }

        @Test
        @DisplayName("길드 마스터가 아닌 사람은 승격시킬 수 없다")
        void promoteToSubMaster_failWhenNotMaster() {
            // given
            String nonMasterId = "non-master-id";
            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));

            // when & then
            assertThatThrownBy(() -> guildService.promoteToSubMaster(1L, nonMasterId, testUserId))
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

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testUserId)).thenReturn(Optional.of(subMasterMember));

            // when & then
            assertThatThrownBy(() -> guildService.promoteToSubMaster(1L, testMasterId, testUserId))
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

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testUserId)).thenReturn(Optional.of(subMasterMember));
            when(userRepository.findById(testUserId)).thenReturn(Optional.empty());

            // when
            GuildMemberResponse response = guildService.demoteFromSubMaster(1L, testMasterId, testUserId);

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

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testUserId)).thenReturn(Optional.of(normalMember));

            // when & then
            assertThatThrownBy(() -> guildService.demoteFromSubMaster(1L, testMasterId, testUserId))
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

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testMasterId)).thenReturn(Optional.of(testMasterMember));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testUserId)).thenReturn(Optional.of(targetMember));

            // when
            guildService.kickMember(1L, testMasterId, testUserId);

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

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, subMasterId)).thenReturn(Optional.of(subMasterMember));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testUserId)).thenReturn(Optional.of(targetMember));

            // when
            guildService.kickMember(1L, subMasterId, testUserId);

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

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, subMasterId1)).thenReturn(Optional.of(subMasterMember1));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, subMasterId2)).thenReturn(Optional.of(subMasterMember2));

            // when & then
            assertThatThrownBy(() -> guildService.kickMember(1L, subMasterId1, subMasterId2))
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

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, memberId)).thenReturn(Optional.of(normalMember));

            // when & then
            assertThatThrownBy(() -> guildService.kickMember(1L, memberId, testUserId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("길드 마스터 또는 부길드마스터만 멤버를 추방할 수 있습니다");
        }

        @Test
        @DisplayName("자기 자신을 추방할 수 없다")
        void kickMember_cannotKickSelf() {
            // given
            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));

            // when & then
            assertThatThrownBy(() -> guildService.kickMember(1L, testMasterId, testMasterId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("자기 자신을 추방할 수 없습니다");
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
            setJoinRequestId(joinRequest, 1L);

            when(joinRequestRepository.findById(1L)).thenReturn(Optional.of(joinRequest));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, subMasterId)).thenReturn(Optional.of(subMasterMember));
            when(guildMemberRepository.hasActiveGuildMembershipInCategory(testUserId, testCategoryId)).thenReturn(false);
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(10L);
            when(guildMemberRepository.save(any(GuildMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            GuildMemberResponse response = guildService.approveJoinRequest(1L, subMasterId);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getUserId()).isEqualTo(testUserId);
            assertThat(response.getRole()).isEqualTo(GuildMemberRole.MEMBER);
            verify(guildMemberRepository).save(any(GuildMember.class));
        }
    }

    @Nested
    @DisplayName("카테고리별 공개 길드 조회 테스트")
    class GetPublicGuildsByCategoryTest {

        @Test
        @DisplayName("Featured 길드 우선 표시 후 자동 선정 길드를 조회한다")
        void getPublicGuildsByCategory_hybridSelection() {
            // given
            Long guildId = 1L;
            Long featuredGuildId = 2L;
            Guild featuredGuild = Guild.builder()
                .name("추천 길드")
                .description("추천 길드 설명")
                .visibility(GuildVisibility.PUBLIC)
                .masterId("featured-master")
                .maxMembers(50)
                .categoryId(testCategoryId)
                .build();
            setGuildId(featuredGuild, featuredGuildId);

            FeaturedGuild fg = FeaturedGuild.builder()
                .categoryId(testCategoryId)
                .guildId(featuredGuildId)
                .displayOrder(1)
                .isActive(true)
                .build();

            when(featuredGuildRepository.findActiveFeaturedGuilds(eq(testCategoryId), any()))
                .thenReturn(List.of(fg));
            when(guildRepository.findByIdAndIsActiveTrue(featuredGuildId))
                .thenReturn(Optional.of(featuredGuild));
            when(guildMemberRepository.countActiveMembers(featuredGuildId)).thenReturn(10L);
            when(missionCategoryService.getCategory(testCategoryId)).thenReturn(testCategory);

            // 자동 선정 길드
            when(guildRepository.findPublicGuildsByCategoryOrderByMemberCount(eq(testCategoryId), any()))
                .thenReturn(List.of(testGuild));
            when(guildMemberRepository.countActiveMembers(guildId)).thenReturn(5L);

            // when
            List<GuildResponse> result = guildService.getPublicGuildsByCategory(testCategoryId);

            // then
            assertThat(result).hasSize(2);
            // Featured 길드가 먼저
            assertThat(result.get(0).getId()).isEqualTo(featuredGuildId);
            assertThat(result.get(0).getName()).isEqualTo("추천 길드");
            // 자동 선정 길드가 그 다음
            assertThat(result.get(1).getId()).isEqualTo(guildId);
        }

        @Test
        @DisplayName("Featured 길드가 없으면 자동 선정만 조회한다")
        void getPublicGuildsByCategory_onlyAutoSelection() {
            // given
            when(featuredGuildRepository.findActiveFeaturedGuilds(eq(testCategoryId), any()))
                .thenReturn(Collections.emptyList());
            when(guildRepository.findPublicGuildsByCategoryOrderByMemberCount(eq(testCategoryId), any()))
                .thenReturn(List.of(testGuild));
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(10L);
            when(missionCategoryService.getCategory(testCategoryId)).thenReturn(testCategory);

            // when
            List<GuildResponse> result = guildService.getPublicGuildsByCategory(testCategoryId);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(1L);
            assertThat(result.get(0).getName()).isEqualTo("테스트 길드");
        }

        @Test
        @DisplayName("중복된 길드는 제외된다")
        void getPublicGuildsByCategory_noDuplicates() {
            // given
            Long guildId = 1L;
            FeaturedGuild fg = FeaturedGuild.builder()
                .categoryId(testCategoryId)
                .guildId(guildId)  // 자동 선정과 동일한 길드
                .displayOrder(1)
                .isActive(true)
                .build();

            when(featuredGuildRepository.findActiveFeaturedGuilds(eq(testCategoryId), any()))
                .thenReturn(List.of(fg));
            when(guildRepository.findByIdAndIsActiveTrue(guildId))
                .thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.countActiveMembers(guildId)).thenReturn(10L);
            when(missionCategoryService.getCategory(testCategoryId)).thenReturn(testCategory);

            // 자동 선정에도 동일한 길드
            when(guildRepository.findPublicGuildsByCategoryOrderByMemberCount(eq(testCategoryId), any()))
                .thenReturn(List.of(testGuild));

            // when
            List<GuildResponse> result = guildService.getPublicGuildsByCategory(testCategoryId);

            // then
            assertThat(result).hasSize(1);  // 중복 제거됨
            assertThat(result.get(0).getId()).isEqualTo(guildId);
        }

        @Test
        @DisplayName("카테고리가 null이면 빈 목록을 반환한다")
        void getPublicGuildsByCategory_nullCategory() {
            // given
            when(featuredGuildRepository.findActiveFeaturedGuilds(eq(null), any()))
                .thenReturn(Collections.emptyList());
            when(guildRepository.findPublicGuildsByCategoryOrderByMemberCount(eq(null), any()))
                .thenReturn(Collections.emptyList());

            // when
            List<GuildResponse> result = guildService.getPublicGuildsByCategory(null);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("최대 5개까지만 반환한다")
        void getPublicGuildsByCategory_maxFiveGuilds() {
            // given
            List<FeaturedGuild> manyFeaturedGuilds = new java.util.ArrayList<>();
            for (int i = 1; i <= 6; i++) {
                Long guildId = (long) (i + 10);  // 11, 12, 13, ...
                FeaturedGuild fg = FeaturedGuild.builder()
                    .categoryId(testCategoryId)
                    .guildId(guildId)
                    .displayOrder(i)
                    .isActive(true)
                    .build();
                manyFeaturedGuilds.add(fg);

                Guild guild = Guild.builder()
                    .name("길드 " + i)
                    .description("설명 " + i)
                    .visibility(GuildVisibility.PUBLIC)
                    .masterId("master-" + i)
                    .maxMembers(50)
                    .categoryId(testCategoryId)
                    .build();
                setGuildId(guild, guildId);

                lenient().when(guildRepository.findByIdAndIsActiveTrue(guildId)).thenReturn(Optional.of(guild));
                lenient().when(guildMemberRepository.countActiveMembers(guildId)).thenReturn(5L);
            }

            when(featuredGuildRepository.findActiveFeaturedGuilds(eq(testCategoryId), any()))
                .thenReturn(manyFeaturedGuilds);
            lenient().when(missionCategoryService.getCategory(testCategoryId)).thenReturn(testCategory);

            // when
            List<GuildResponse> result = guildService.getPublicGuildsByCategory(testCategoryId);

            // then
            assertThat(result).hasSize(5);  // 최대 5개
        }
    }

    @Nested
    @DisplayName("길드 조회 테스트")
    class GetGuildTest {

        @Test
        @DisplayName("공개 길드를 조회한다")
        void getGuild_publicGuild_success() {
            // given
            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(5L);
            lenient().when(missionCategoryService.getCategory(testCategoryId)).thenReturn(testCategory);
            when(reportService.isUnderReview(ReportTargetType.GUILD, "1")).thenReturn(false);

            // when
            GuildResponse response = guildService.getGuild(1L, testUserId);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getName()).isEqualTo("테스트 길드");
            assertThat(response.getIsUnderReview()).isFalse();
            verify(reportService).isUnderReview(ReportTargetType.GUILD, "1");
        }

        @Test
        @DisplayName("비공개 길드에 멤버가 아닌 사용자가 접근하면 예외가 발생한다")
        void getGuild_privateGuild_notMember_throwsException() {
            // given
            Guild privateGuild = Guild.builder()
                .name("비공개 길드")
                .description("설명")
                .visibility(GuildVisibility.PRIVATE)
                .masterId(testMasterId)
                .maxMembers(50)
                .categoryId(testCategoryId)
                .build();
            setGuildId(privateGuild, 2L);

            when(guildRepository.findByIdAndIsActiveTrue(2L)).thenReturn(Optional.of(privateGuild));
            when(guildMemberRepository.isActiveMember(2L, testUserId)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> guildService.getGuild(2L, testUserId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("비공개 길드에 접근할 수 없습니다.");
        }

        @Test
        @DisplayName("존재하지 않는 길드 조회 시 예외가 발생한다")
        void getGuild_notFound_throwsException() {
            // given
            when(guildRepository.findByIdAndIsActiveTrue(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> guildService.getGuild(999L, testUserId))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("길드 수정 테스트")
    class UpdateGuildTest {

        @Test
        @DisplayName("길드 마스터가 길드를 수정한다")
        void updateGuild_success() {
            // given
            GuildUpdateRequest request = GuildUpdateRequest.builder()
                .description("수정된 설명")
                .build();

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(5L);
            lenient().when(missionCategoryService.getCategory(testCategoryId)).thenReturn(testCategory);

            // when
            GuildResponse response = guildService.updateGuild(1L, testMasterId, request);

            // then
            assertThat(response).isNotNull();
            assertThat(testGuild.getDescription()).isEqualTo("수정된 설명");
        }

        @Test
        @DisplayName("길드 마스터가 아닌 사용자가 수정하면 예외가 발생한다")
        void updateGuild_notMaster_throwsException() {
            // given
            GuildUpdateRequest request = GuildUpdateRequest.builder()
                .description("수정된 설명")
                .build();

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));

            // when & then
            assertThatThrownBy(() -> guildService.updateGuild(1L, testUserId, request))
                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("중복 길드명으로 수정 시 예외가 발생한다")
        void updateGuild_duplicateName_throwsException() {
            // given
            GuildUpdateRequest request = GuildUpdateRequest.builder()
                .name("중복 길드명")
                .build();

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildRepository.existsByNameAndIsActiveTrue("중복 길드명")).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> guildService.updateGuild(1L, testMasterId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 존재하는 길드명입니다");
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
            setJoinRequestId(joinRequest, 1L);

            when(joinRequestRepository.findById(1L)).thenReturn(Optional.of(joinRequest));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testMasterId))
                .thenReturn(Optional.of(testMasterMember));

            // when
            GuildJoinRequestResponse response = guildService.rejectJoinRequest(1L, testMasterId, "테스트 거절 사유");

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
            setJoinRequestId(joinRequest, 1L);

            when(joinRequestRepository.findById(1L)).thenReturn(Optional.of(joinRequest));

            // when & then
            assertThatThrownBy(() -> guildService.rejectJoinRequest(1L, testMasterId, "거절 사유"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이미 처리된 가입 신청입니다.");
        }
    }

    @Nested
    @DisplayName("길드 해체 테스트")
    class DissolveGuildTest {

        @Test
        @DisplayName("길드 마스터가 혼자 남은 길드를 해체한다")
        void dissolveGuild_success() {
            // given
            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndStatus(1L, GuildMemberStatus.ACTIVE))
                .thenReturn(List.of(testMasterMember));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testMasterId))
                .thenReturn(Optional.of(testMasterMember));

            // when
            guildService.dissolveGuild(1L, testMasterId);

            // then
            assertThat(testGuild.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("길드 마스터가 아닌 사용자가 해체하면 예외가 발생한다")
        void dissolveGuild_notMaster_throwsException() {
            // given
            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));

            // when & then
            assertThatThrownBy(() -> guildService.dissolveGuild(1L, testUserId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("길드 마스터만 길드를 해체할 수 있습니다.");
        }

        @Test
        @DisplayName("다른 멤버가 있으면 길드를 해체할 수 없다")
        void dissolveGuild_hasOtherMembers_throwsException() {
            // given
            GuildMember otherMember = GuildMember.builder()
                .guild(testGuild)
                .userId(testUserId)
                .role(GuildMemberRole.MEMBER)
                .status(GuildMemberStatus.ACTIVE)
                .build();

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndStatus(1L, GuildMemberStatus.ACTIVE))
                .thenReturn(List.of(testMasterMember, otherMember));

            // when & then
            assertThatThrownBy(() -> guildService.dissolveGuild(1L, testMasterId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("모든 길드원을 내보내야 합니다");
        }
    }

    @Nested
    @DisplayName("공개 길드 목록 조회 테스트")
    class GetPublicGuildsTest {

        @Test
        @DisplayName("공개 길드 목록을 조회한다")
        void getPublicGuilds_success() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            when(guildRepository.findPublicGuilds(any(Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(testGuild)));
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(10L);
            lenient().when(missionCategoryService.getCategory(testCategoryId)).thenReturn(testCategory);

            // when
            org.springframework.data.domain.Page<GuildResponse> result = guildService.getPublicGuilds(pageable);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("테스트 길드");
        }
    }

    @Nested
    @DisplayName("길드 검색 테스트")
    class SearchGuildsTest {

        @Test
        @DisplayName("키워드로 길드를 검색한다")
        void searchGuilds_success() {
            // given
            String keyword = "테스트";
            Pageable pageable = PageRequest.of(0, 10);
            when(guildRepository.searchPublicGuilds(eq(keyword), any(Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(testGuild)));
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(10L);
            lenient().when(missionCategoryService.getCategory(testCategoryId)).thenReturn(testCategory);

            // when
            org.springframework.data.domain.Page<GuildResponse> result = guildService.searchGuilds(keyword, pageable);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("테스트 길드");
        }
    }

    @Nested
    @DisplayName("내 길드 목록 조회 테스트")
    class GetMyGuildsTest {

        @Test
        @DisplayName("내가 속한 길드 목록을 조회한다")
        void getMyGuilds_success() {
            // given
            GuildMember myMembership = GuildMember.builder()
                .guild(testGuild)
                .userId(testUserId)
                .role(GuildMemberRole.MEMBER)
                .status(GuildMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build();

            when(guildMemberRepository.findActiveGuildsByUserId(testUserId))
                .thenReturn(List.of(myMembership));
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(5L);
            lenient().when(missionCategoryService.getCategory(testCategoryId)).thenReturn(testCategory);

            // when
            List<GuildResponse> result = guildService.getMyGuilds(testUserId);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("테스트 길드");
        }

        @Test
        @DisplayName("가입한 길드가 없으면 빈 목록을 반환한다")
        void getMyGuilds_empty() {
            // given
            when(guildMemberRepository.findActiveGuildsByUserId(testUserId))
                .thenReturn(Collections.emptyList());

            // when
            List<GuildResponse> result = guildService.getMyGuilds(testUserId);

            // then
            assertThat(result).isEmpty();
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
            setJoinRequestId(joinRequest, 1L);

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testMasterId))
                .thenReturn(Optional.of(testMasterMember));
            when(joinRequestRepository.findPendingRequests(eq(1L), any(Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(joinRequest)));

            // when
            org.springframework.data.domain.Page<GuildJoinRequestResponse> result =
                guildService.getPendingJoinRequests(1L, testMasterId, pageable);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getStatus()).isEqualTo(JoinRequestStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("길드 멤버 목록 조회 테스트")
    class GetGuildMembersTest {

        @Test
        @DisplayName("길드 멤버 목록을 조회한다")
        void getGuildMembers_success() {
            // given
            GuildMember member = GuildMember.builder()
                .guild(testGuild)
                .userId(testUserId)
                .role(GuildMemberRole.MEMBER)
                .status(GuildMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build();

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            lenient().when(guildMemberRepository.isActiveMember(1L, testMasterId)).thenReturn(true);
            when(guildMemberRepository.findActiveMembers(1L))
                .thenReturn(List.of(testMasterMember, member));
            when(userRepository.findAllByIdIn(anyList())).thenReturn(Collections.emptyList());

            // when
            List<GuildMemberResponse> result = guildService.getGuildMembers(1L, testMasterId);

            // then
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("비공개 길드의 멤버가 아닌 사용자는 멤버 목록을 조회할 수 없다")
        void getGuildMembers_notMember_throwsException() {
            // given
            Guild privateGuild = Guild.builder()
                .name("비공개 길드")
                .description("설명")
                .visibility(GuildVisibility.PRIVATE)
                .masterId(testMasterId)
                .maxMembers(50)
                .categoryId(testCategoryId)
                .build();
            setGuildId(privateGuild, 2L);

            when(guildRepository.findByIdAndIsActiveTrue(2L)).thenReturn(Optional.of(privateGuild));
            when(guildMemberRepository.isActiveMember(2L, testUserId)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> guildService.getGuildMembers(2L, testUserId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("비공개 길드의 멤버 목록을 조회할 수 없습니다.");
        }
    }

    @Nested
    @DisplayName("신고 처리중 상태 통합 테스트")
    class IsUnderReviewIntegrationTest {

        @Test
        @DisplayName("길드 상세 조회 시 신고 처리중 상태가 true로 반환된다")
        void getGuild_underReview_true() {
            // given
            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(5L);
            lenient().when(missionCategoryService.getCategory(testCategoryId)).thenReturn(testCategory);
            when(reportService.isUnderReview(ReportTargetType.GUILD, "1")).thenReturn(true);

            // when
            GuildResponse response = guildService.getGuild(1L, testUserId);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getIsUnderReview()).isTrue();
            verify(reportService).isUnderReview(ReportTargetType.GUILD, "1");
        }

        @Test
        @DisplayName("공개 길드 목록 조회 시 신고 처리중 상태가 일괄 조회된다")
        void getPublicGuilds_batchUnderReviewCheck() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            when(guildRepository.findPublicGuilds(any(Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(testGuild)));
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(10L);
            lenient().when(missionCategoryService.getCategory(testCategoryId)).thenReturn(testCategory);

            Map<String, Boolean> underReviewMap = new HashMap<>();
            underReviewMap.put("1", true);
            when(reportService.isUnderReviewBatch(eq(ReportTargetType.GUILD), anyList())).thenReturn(underReviewMap);

            // when
            org.springframework.data.domain.Page<GuildResponse> result = guildService.getPublicGuilds(pageable);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getIsUnderReview()).isTrue();
            verify(reportService).isUnderReviewBatch(eq(ReportTargetType.GUILD), anyList());
        }

        @Test
        @DisplayName("내 길드 목록 조회 시 신고 처리중 상태가 일괄 조회된다")
        void getMyGuilds_batchUnderReviewCheck() {
            // given
            GuildMember myMembership = GuildMember.builder()
                .guild(testGuild)
                .userId(testUserId)
                .role(GuildMemberRole.MEMBER)
                .status(GuildMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build();

            when(guildMemberRepository.findActiveGuildsByUserId(testUserId))
                .thenReturn(List.of(myMembership));
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(5L);
            lenient().when(missionCategoryService.getCategory(testCategoryId)).thenReturn(testCategory);

            Map<String, Boolean> underReviewMap = new HashMap<>();
            underReviewMap.put("1", false);
            when(reportService.isUnderReviewBatch(eq(ReportTargetType.GUILD), anyList())).thenReturn(underReviewMap);

            // when
            List<GuildResponse> result = guildService.getMyGuilds(testUserId);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getIsUnderReview()).isFalse();
            verify(reportService).isUnderReviewBatch(eq(ReportTargetType.GUILD), anyList());
        }

        @Test
        @DisplayName("길드 검색 시 신고 처리중 상태가 일괄 조회된다")
        void searchGuilds_batchUnderReviewCheck() {
            // given
            String keyword = "테스트";
            Pageable pageable = PageRequest.of(0, 10);
            when(guildRepository.searchPublicGuilds(eq(keyword), any(Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(testGuild)));
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(10L);
            lenient().when(missionCategoryService.getCategory(testCategoryId)).thenReturn(testCategory);

            Map<String, Boolean> underReviewMap = new HashMap<>();
            underReviewMap.put("1", true);
            when(reportService.isUnderReviewBatch(eq(ReportTargetType.GUILD), anyList())).thenReturn(underReviewMap);

            // when
            org.springframework.data.domain.Page<GuildResponse> result = guildService.searchGuilds(keyword, pageable);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getIsUnderReview()).isTrue();
            verify(reportService).isUnderReviewBatch(eq(ReportTargetType.GUILD), anyList());
        }

        @Test
        @DisplayName("카테고리별 공개 길드 조회 시 신고 처리중 상태가 일괄 조회된다")
        void getPublicGuildsByCategory_batchUnderReviewCheck() {
            // given
            when(featuredGuildRepository.findActiveFeaturedGuilds(eq(testCategoryId), any()))
                .thenReturn(Collections.emptyList());
            when(guildRepository.findPublicGuildsByCategoryOrderByMemberCount(eq(testCategoryId), any()))
                .thenReturn(List.of(testGuild));
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(10L);
            when(missionCategoryService.getCategory(testCategoryId)).thenReturn(testCategory);

            Map<String, Boolean> underReviewMap = new HashMap<>();
            underReviewMap.put("1", true);
            when(reportService.isUnderReviewBatch(eq(ReportTargetType.GUILD), anyList())).thenReturn(underReviewMap);

            // when
            List<GuildResponse> result = guildService.getPublicGuildsByCategory(testCategoryId);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getIsUnderReview()).isTrue();
            verify(reportService).isUnderReviewBatch(eq(ReportTargetType.GUILD), anyList());
        }

        @Test
        @DisplayName("빈 길드 목록 조회 시 신고 상태 일괄 조회가 호출되지 않는다")
        void getMyGuilds_emptyList_noReportServiceCall() {
            // given
            when(guildMemberRepository.findActiveGuildsByUserId(testUserId))
                .thenReturn(Collections.emptyList());

            // when
            List<GuildResponse> result = guildService.getMyGuilds(testUserId);

            // then
            assertThat(result).isEmpty();
            verify(reportService, never()).isUnderReviewBatch(any(), anyList());
        }
    }
}
