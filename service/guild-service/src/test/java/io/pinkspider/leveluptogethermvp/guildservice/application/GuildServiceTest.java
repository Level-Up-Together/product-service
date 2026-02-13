package io.pinkspider.leveluptogethermvp.guildservice.application;

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

import static io.pinkspider.global.test.TestReflectionUtils.setId;

import io.pinkspider.leveluptogethermvp.guildservice.application.GuildHelper;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildCreateRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildUpdateRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.metaservice.guildlevelconfig.application.GuildLevelConfigCacheService;
import io.pinkspider.leveluptogethermvp.metaservice.guildlevelconfig.domain.entity.GuildLevelConfig;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildMember;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildJoinType;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberRole;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberStatus;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildVisibility;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import io.pinkspider.leveluptogethermvp.metaservice.application.MissionCategoryService;
import io.pinkspider.leveluptogethermvp.metaservice.domain.dto.MissionCategoryResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.application.GamificationQueryFacadeService;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserExperience;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
class GuildServiceTest {

    @Mock
    private GuildRepository guildRepository;

    @Mock
    private GuildMemberRepository guildMemberRepository;

    @Mock
    private GuildLevelConfigCacheService guildLevelConfigCacheService;

    @Mock
    private MissionCategoryService missionCategoryService;

    @Mock
    private GuildHeadquartersService guildHeadquartersService;

    @Mock
    private GuildImageStorageService guildImageStorageService;

    @Mock
    private GamificationQueryFacadeService gamificationQueryFacadeService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private GuildHelper guildHelper;

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

            UserExperience userExperience = UserExperience.builder()
                .userId(testUserId)
                .currentLevel(20)
                .build();
            when(gamificationQueryFacadeService.getOrCreateUserExperience(testUserId)).thenReturn(userExperience);
            when(guildMemberRepository.isGuildMaster(testUserId)).thenReturn(false);
            when(missionCategoryService.getCategory(testCategoryId)).thenReturn(testCategory);
            when(guildMemberRepository.hasActiveGuildMembershipInCategory(testUserId, testCategoryId)).thenReturn(false);
            when(guildRepository.existsByNameAndIsActiveTrue("새 길드")).thenReturn(false);
            when(guildLevelConfigCacheService.getLevelConfigByLevel(1)).thenReturn(
                GuildLevelConfig.builder().level(1).maxMembers(20).build());
            when(guildRepository.save(any(Guild.class))).thenAnswer(invocation -> {
                Guild guild = invocation.getArgument(0);
                setId(guild, 1L);
                return guild;
            });
            when(guildMemberRepository.save(any(GuildMember.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(guildHelper.buildGuildResponseWithCategory(any(Guild.class), eq(1)))
                .thenAnswer(inv -> GuildResponse.from(inv.getArgument(0), inv.getArgument(1),
                    testCategory.getName(), testCategory.getIcon()));

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

            UserExperience userExperience = UserExperience.builder()
                .userId(testUserId)
                .currentLevel(20)
                .build();
            when(gamificationQueryFacadeService.getOrCreateUserExperience(testUserId)).thenReturn(userExperience);
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

            UserExperience userExperience = UserExperience.builder()
                .userId(testUserId)
                .currentLevel(20)
                .build();
            when(gamificationQueryFacadeService.getOrCreateUserExperience(testUserId)).thenReturn(userExperience);
            when(guildMemberRepository.isGuildMaster(testUserId)).thenReturn(false);
            when(missionCategoryService.getCategory(testCategoryId)).thenReturn(testCategory);
            when(guildMemberRepository.hasActiveGuildMembershipInCategory(testUserId, testCategoryId)).thenReturn(false);
            when(guildRepository.existsByNameAndIsActiveTrue("중복 길드")).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> guildService.createGuild(testUserId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 존재하는 길드명입니다");
        }

        @Test
        @DisplayName("1인 1길드 마스터 정책: 이미 다른 길드의 마스터인 사용자는 길드를 생성할 수 없다")
        void createGuild_failWhenAlreadyGuildMaster() {
            // given
            GuildCreateRequest request = GuildCreateRequest.builder()
                .name("새 길드")
                .description("새 길드 설명")
                .visibility(GuildVisibility.PUBLIC)
                .categoryId(testCategoryId)
                .build();

            UserExperience userExperience = UserExperience.builder()
                .userId(testUserId)
                .currentLevel(20)
                .build();
            when(gamificationQueryFacadeService.getOrCreateUserExperience(testUserId)).thenReturn(userExperience);
            when(guildMemberRepository.isGuildMaster(testUserId)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> guildService.createGuild(testUserId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 다른 길드의 마스터입니다");

            verify(guildRepository, never()).save(any(Guild.class));
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

            when(guildHelper.findActiveGuildById(1L)).thenReturn(testGuild);
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(5L);
            when(guildHelper.buildGuildResponseWithCategory(any(Guild.class), eq(5)))
                .thenAnswer(inv -> GuildResponse.from(inv.getArgument(0), inv.getArgument(1),
                    testCategory.getName(), testCategory.getIcon()));

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

            when(guildHelper.findActiveGuildById(1L)).thenReturn(testGuild);
            doThrow(new IllegalStateException("길드 마스터만 이 작업을 수행할 수 있습니다."))
                .when(guildHelper).validateMaster(testGuild, testUserId);

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

            when(guildHelper.findActiveGuildById(1L)).thenReturn(testGuild);
            when(guildRepository.existsByNameAndIsActiveTrue("중복 길드명")).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> guildService.updateGuild(1L, testMasterId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 존재하는 길드명입니다");
        }
    }

    @Nested
    @DisplayName("길드 해체 테스트")
    class DissolveGuildTest {

        @Test
        @DisplayName("길드 마스터가 혼자 남은 길드를 해체한다")
        void dissolveGuild_success() {
            // given
            when(guildHelper.findActiveGuildById(1L)).thenReturn(testGuild);
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
            when(guildHelper.findActiveGuildById(1L)).thenReturn(testGuild);

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

            when(guildHelper.findActiveGuildById(1L)).thenReturn(testGuild);
            when(guildMemberRepository.findByGuildIdAndStatus(1L, GuildMemberStatus.ACTIVE))
                .thenReturn(List.of(testMasterMember, otherMember));

            // when & then
            assertThatThrownBy(() -> guildService.dissolveGuild(1L, testMasterId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("모든 길드원을 내보내야 합니다");
        }
    }
}
