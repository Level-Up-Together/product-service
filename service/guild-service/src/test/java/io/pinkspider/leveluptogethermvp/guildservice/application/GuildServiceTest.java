package io.pinkspider.leveluptogethermvp.guildservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyDouble;
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
import io.pinkspider.global.facade.GamificationQueryFacade;
import io.pinkspider.global.facade.dto.UserExperienceDto;
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
import org.springframework.mock.web.MockMultipartFile;

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
    private GamificationQueryFacade gamificationQueryFacadeService;

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

            UserExperienceDto userExperience = new UserExperienceDto(null, testUserId, 20, 0, 0, null, null, null);
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

            UserExperienceDto userExperience = new UserExperienceDto(null, testUserId, 20, 0, 0, null, null, null);
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

            UserExperienceDto userExperience = new UserExperienceDto(null, testUserId, 20, 0, 0, null, null, null);
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

            UserExperienceDto userExperience = new UserExperienceDto(null, testUserId, 20, 0, 0, null, null, null);
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
    @DisplayName("길드 생성 추가 테스트")
    class CreateGuildAdditionalTest {

        @Test
        @DisplayName("레벨 20 미만인 사용자는 길드를 생성할 수 없다")
        void createGuild_failWhenLevelTooLow() {
            // given
            GuildCreateRequest request = GuildCreateRequest.builder()
                .name("새 길드")
                .description("설명")
                .visibility(GuildVisibility.PUBLIC)
                .categoryId(testCategoryId)
                .build();

            UserExperienceDto userExperience = new UserExperienceDto(null, testUserId, 19, 0, 0, null, null, null);
            when(gamificationQueryFacadeService.getOrCreateUserExperience(testUserId)).thenReturn(userExperience);

            // when & then
            assertThatThrownBy(() -> guildService.createGuild(testUserId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("레벨 20 이상부터");
        }

        @Test
        @DisplayName("비활성 카테고리로 길드 생성 시 예외가 발생한다")
        void createGuild_failWhenCategoryInactive() {
            // given
            GuildCreateRequest request = GuildCreateRequest.builder()
                .name("새 길드")
                .description("설명")
                .visibility(GuildVisibility.PUBLIC)
                .categoryId(testCategoryId)
                .build();

            MissionCategoryResponse inactiveCategory = MissionCategoryResponse.builder()
                .id(testCategoryId)
                .name("비활성 카테고리")
                .isActive(false)
                .build();

            UserExperienceDto userExperience = new UserExperienceDto(null, testUserId, 20, 0, 0, null, null, null);
            when(gamificationQueryFacadeService.getOrCreateUserExperience(testUserId)).thenReturn(userExperience);
            when(guildMemberRepository.isGuildMaster(testUserId)).thenReturn(false);
            when(missionCategoryService.getCategory(testCategoryId)).thenReturn(inactiveCategory);

            // when & then
            assertThatThrownBy(() -> guildService.createGuild(testUserId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("유효하지 않은 카테고리");
        }

        @Test
        @DisplayName("카테고리가 null이면 예외가 발생한다")
        void createGuild_failWhenCategoryNull() {
            // given
            GuildCreateRequest request = GuildCreateRequest.builder()
                .name("새 길드")
                .description("설명")
                .visibility(GuildVisibility.PUBLIC)
                .categoryId(testCategoryId)
                .build();

            UserExperienceDto userExperience = new UserExperienceDto(null, testUserId, 20, 0, 0, null, null, null);
            when(gamificationQueryFacadeService.getOrCreateUserExperience(testUserId)).thenReturn(userExperience);
            when(guildMemberRepository.isGuildMaster(testUserId)).thenReturn(false);
            when(missionCategoryService.getCategory(testCategoryId)).thenReturn(null);

            // when & then
            assertThatThrownBy(() -> guildService.createGuild(testUserId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("유효하지 않은 카테고리");
        }

        @Test
        @DisplayName("joinType이 null이면 OPEN으로 기본 설정하여 길드를 생성한다")
        void createGuild_nullJoinType_defaultsToOpen() {
            // given
            GuildCreateRequest request = GuildCreateRequest.builder()
                .name("새 길드")
                .description("새 길드 설명")
                .visibility(GuildVisibility.PUBLIC)
                .categoryId(testCategoryId)
                .joinType(null) // null → OPEN 기본값
                .build();

            UserExperienceDto userExperience = new UserExperienceDto(null, testUserId, 20, 0, 0, null, null, null);
            when(gamificationQueryFacadeService.getOrCreateUserExperience(testUserId)).thenReturn(userExperience);
            when(guildMemberRepository.isGuildMaster(testUserId)).thenReturn(false);
            when(missionCategoryService.getCategory(testCategoryId)).thenReturn(testCategory);
            when(guildMemberRepository.hasActiveGuildMembershipInCategory(testUserId, testCategoryId)).thenReturn(false);
            when(guildRepository.existsByNameAndIsActiveTrue("새 길드")).thenReturn(false);
            when(guildLevelConfigCacheService.getLevelConfigByLevel(1)).thenReturn(null); // null → 기본값 10
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
            assertThat(response.getJoinType()).isEqualTo(GuildJoinType.OPEN);
        }

        @Test
        @DisplayName("maxMembers가 null이면 level1Config의 maxMembers를 사용한다")
        void createGuild_nullMaxMembers_usesLevel1Config() {
            // given
            GuildCreateRequest request = GuildCreateRequest.builder()
                .name("새 길드")
                .description("설명")
                .visibility(GuildVisibility.PUBLIC)
                .categoryId(testCategoryId)
                .maxMembers(null) // null → level1 config 사용
                .build();

            UserExperienceDto userExperience = new UserExperienceDto(null, testUserId, 20, 0, 0, null, null, null);
            when(gamificationQueryFacadeService.getOrCreateUserExperience(testUserId)).thenReturn(userExperience);
            when(guildMemberRepository.isGuildMaster(testUserId)).thenReturn(false);
            when(missionCategoryService.getCategory(testCategoryId)).thenReturn(testCategory);
            when(guildMemberRepository.hasActiveGuildMembershipInCategory(testUserId, testCategoryId)).thenReturn(false);
            when(guildRepository.existsByNameAndIsActiveTrue("새 길드")).thenReturn(false);
            when(guildLevelConfigCacheService.getLevelConfigByLevel(1))
                .thenReturn(GuildLevelConfig.builder().level(1).maxMembers(25).build());
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
            assertThat(response.getMaxMembers()).isEqualTo(25);
        }

        @Test
        @DisplayName("거점 위치가 설정되면 거리 검증을 수행한다")
        void createGuild_withBaseLocation_validatesDistance() {
            // given
            GuildCreateRequest request = GuildCreateRequest.builder()
                .name("새 길드")
                .description("설명")
                .visibility(GuildVisibility.PUBLIC)
                .categoryId(testCategoryId)
                .baseLatitude(37.5665)
                .baseLongitude(126.9780)
                .build();

            UserExperienceDto userExperience = new UserExperienceDto(null, testUserId, 20, 0, 0, null, null, null);
            when(gamificationQueryFacadeService.getOrCreateUserExperience(testUserId)).thenReturn(userExperience);
            when(guildMemberRepository.isGuildMaster(testUserId)).thenReturn(false);
            when(missionCategoryService.getCategory(testCategoryId)).thenReturn(testCategory);
            when(guildMemberRepository.hasActiveGuildMembershipInCategory(testUserId, testCategoryId)).thenReturn(false);
            when(guildRepository.existsByNameAndIsActiveTrue("새 길드")).thenReturn(false);
            when(guildLevelConfigCacheService.getLevelConfigByLevel(1))
                .thenReturn(GuildLevelConfig.builder().level(1).maxMembers(20).build());
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
            verify(guildHeadquartersService).validateAndThrowIfInvalid(null, 37.5665, 126.9780);
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

        @Test
        @DisplayName("같은 이름으로 수정 시 중복 검사를 하지 않는다")
        void updateGuild_sameName_doesNotCheckDuplicate() {
            // given
            GuildUpdateRequest request = GuildUpdateRequest.builder()
                .name("테스트 길드")  // 기존과 동일한 이름
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
            verify(guildRepository, never()).existsByNameAndIsActiveTrue(anyString());
        }

        @Test
        @DisplayName("위도와 경도 모두 있을 때 거점 위치 변경 및 검증")
        void updateGuild_withBothCoordinates_validatesAndUpdates() {
            // given
            GuildUpdateRequest request = GuildUpdateRequest.builder()
                .baseLatitude(37.5665)
                .baseLongitude(126.9780)
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
            verify(guildHeadquartersService).validateAndThrowIfInvalid(1L, 37.5665, 126.9780);
            assertThat(testGuild.getBaseLatitude()).isEqualTo(37.5665);
            assertThat(testGuild.getBaseLongitude()).isEqualTo(126.9780);
        }

        @Test
        @DisplayName("위도만 있을 때 위도만 업데이트된다")
        void updateGuild_withLatitudeOnly_updatesLatitude() {
            // given
            GuildUpdateRequest request = GuildUpdateRequest.builder()
                .baseLatitude(37.5665)
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
            verify(guildHeadquartersService, never()).validateAndThrowIfInvalid(anyLong(), anyDouble(), anyDouble());
            assertThat(testGuild.getBaseLatitude()).isEqualTo(37.5665);
        }

        @Test
        @DisplayName("경도만 있을 때 경도만 업데이트된다")
        void updateGuild_withLongitudeOnly_updatesLongitude() {
            // given
            GuildUpdateRequest request = GuildUpdateRequest.builder()
                .baseLongitude(126.9780)
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
            verify(guildHeadquartersService, never()).validateAndThrowIfInvalid(anyLong(), anyDouble(), anyDouble());
            assertThat(testGuild.getBaseLongitude()).isEqualTo(126.9780);
        }

        @Test
        @DisplayName("visibility, joinType, maxMembers, imageUrl, baseAddress를 모두 수정한다")
        void updateGuild_allFields_success() {
            // given
            GuildUpdateRequest request = GuildUpdateRequest.builder()
                .visibility(GuildVisibility.PRIVATE)
                .joinType(GuildJoinType.APPROVAL_REQUIRED)
                .maxMembers(100)
                .imageUrl("https://new-image.com/img.png")
                .baseAddress("서울시 강남구")
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
            assertThat(testGuild.getVisibility()).isEqualTo(GuildVisibility.PRIVATE);
            assertThat(testGuild.getJoinType()).isEqualTo(GuildJoinType.APPROVAL_REQUIRED);
            assertThat(testGuild.getMaxMembers()).isEqualTo(100);
            assertThat(testGuild.getImageUrl()).isEqualTo("https://new-image.com/img.png");
            assertThat(testGuild.getBaseAddress()).isEqualTo("서울시 강남구");
        }
    }

    @Nested
    @DisplayName("길드 이미지 업로드 테스트")
    class UploadGuildImageTest {

        @Test
        @DisplayName("기존 이미지가 있으면 삭제 후 새 이미지로 업로드한다")
        void uploadGuildImage_withExistingImage_deletesOldAndStoresNew() {
            // given
            org.springframework.mock.web.MockMultipartFile imageFile =
                new org.springframework.mock.web.MockMultipartFile(
                    "file", "test.jpg", "image/jpeg", "test-content".getBytes());

            testGuild.setImageUrl("https://old-image.com/old.jpg");

            when(guildHelper.findActiveGuildById(1L)).thenReturn(testGuild);
            when(guildImageStorageService.isValidImage(imageFile)).thenReturn(true);
            when(guildImageStorageService.store(imageFile, 1L)).thenReturn("https://new-image.com/new.jpg");
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(5L);
            when(guildHelper.buildGuildResponseWithCategory(any(Guild.class), eq(5)))
                .thenAnswer(inv -> GuildResponse.from(inv.getArgument(0), inv.getArgument(1),
                    testCategory.getName(), testCategory.getIcon()));

            // when
            GuildResponse response = guildService.uploadGuildImage(1L, testMasterId, imageFile);

            // then
            assertThat(response).isNotNull();
            verify(guildImageStorageService).delete("https://old-image.com/old.jpg");
            verify(guildImageStorageService).store(imageFile, 1L);
            assertThat(testGuild.getImageUrl()).isEqualTo("https://new-image.com/new.jpg");
        }

        @Test
        @DisplayName("기존 이미지가 없으면 삭제 없이 새 이미지를 저장한다")
        void uploadGuildImage_withNoExistingImage_storesNewImage() {
            // given
            org.springframework.mock.web.MockMultipartFile imageFile =
                new org.springframework.mock.web.MockMultipartFile(
                    "file", "test.jpg", "image/jpeg", "test-content".getBytes());

            // testGuild.imageUrl is null by default

            when(guildHelper.findActiveGuildById(1L)).thenReturn(testGuild);
            when(guildImageStorageService.isValidImage(imageFile)).thenReturn(true);
            when(guildImageStorageService.store(imageFile, 1L)).thenReturn("https://new-image.com/new.jpg");
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(5L);
            when(guildHelper.buildGuildResponseWithCategory(any(Guild.class), eq(5)))
                .thenAnswer(inv -> GuildResponse.from(inv.getArgument(0), inv.getArgument(1),
                    testCategory.getName(), testCategory.getIcon()));

            // when
            GuildResponse response = guildService.uploadGuildImage(1L, testMasterId, imageFile);

            // then
            assertThat(response).isNotNull();
            verify(guildImageStorageService, never()).delete(anyString());
            verify(guildImageStorageService).store(imageFile, 1L);
        }

        @Test
        @DisplayName("유효하지 않은 이미지 파일이면 예외가 발생한다")
        void uploadGuildImage_invalidFile_throwsException() {
            // given
            org.springframework.mock.web.MockMultipartFile imageFile =
                new org.springframework.mock.web.MockMultipartFile(
                    "file", "test.exe", "application/octet-stream", "test-content".getBytes());

            when(guildHelper.findActiveGuildById(1L)).thenReturn(testGuild);
            when(guildImageStorageService.isValidImage(imageFile)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> guildService.uploadGuildImage(1L, testMasterId, imageFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("유효하지 않은 이미지 파일");
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

        @Test
        @DisplayName("마스터 멤버 정보를 찾을 수 없으면 예외가 발생한다")
        void dissolveGuild_masterMemberNotFound_throwsException() {
            // given
            when(guildHelper.findActiveGuildById(1L)).thenReturn(testGuild);
            when(guildMemberRepository.findByGuildIdAndStatus(1L, GuildMemberStatus.ACTIVE))
                .thenReturn(List.of(testMasterMember));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, testMasterId))
                .thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> guildService.dissolveGuild(1L, testMasterId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("길드 멤버 정보를 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("길드 생성 - 거점 위치 검증 실패 테스트")
    class CreateGuildBaseLocationValidationFailTest {

        @Test
        @DisplayName("거점 위치 검증 실패 시 예외가 발생한다")
        void createGuild_invalidBaseLocation_throwsException() {
            // given
            GuildCreateRequest request = GuildCreateRequest.builder()
                .name("새 길드")
                .description("설명")
                .visibility(GuildVisibility.PUBLIC)
                .categoryId(testCategoryId)
                .baseLatitude(37.5665)
                .baseLongitude(126.9780)
                .build();

            UserExperienceDto userExperience = new UserExperienceDto(null, testUserId, 20, 0, 0, null, null, null);
            when(gamificationQueryFacadeService.getOrCreateUserExperience(testUserId)).thenReturn(userExperience);
            when(guildMemberRepository.isGuildMaster(testUserId)).thenReturn(false);
            when(missionCategoryService.getCategory(testCategoryId)).thenReturn(testCategory);
            when(guildMemberRepository.hasActiveGuildMembershipInCategory(testUserId, testCategoryId)).thenReturn(false);
            when(guildRepository.existsByNameAndIsActiveTrue("새 길드")).thenReturn(false);
            doThrow(new IllegalStateException("다른 길드와의 거리가 너무 가깝습니다."))
                .when(guildHeadquartersService).validateAndThrowIfInvalid(null, 37.5665, 126.9780);

            // when & then
            assertThatThrownBy(() -> guildService.createGuild(testUserId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("다른 길드와의 거리가 너무 가깝습니다");
        }
    }

    @Nested
    @DisplayName("길드 수정 추가 테스트")
    class UpdateGuildAdditionalTest {

        @Test
        @DisplayName("이름이 null이면 이름을 변경하지 않는다")
        void updateGuild_nullName_doesNotChangeName() {
            // given
            GuildUpdateRequest request = GuildUpdateRequest.builder()
                .name(null)
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
            assertThat(testGuild.getName()).isEqualTo("테스트 길드");
            verify(guildRepository, never()).existsByNameAndIsActiveTrue(anyString());
        }

        @Test
        @DisplayName("새 이름이 기존과 다를 때 중복 검사 후 이름을 변경한다")
        void updateGuild_differentName_checksDuplicateAndUpdates() {
            // given
            GuildUpdateRequest request = GuildUpdateRequest.builder()
                .name("변경된 길드명")
                .build();

            when(guildHelper.findActiveGuildById(1L)).thenReturn(testGuild);
            when(guildRepository.existsByNameAndIsActiveTrue("변경된 길드명")).thenReturn(false);
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(5L);
            when(guildHelper.buildGuildResponseWithCategory(any(Guild.class), eq(5)))
                .thenAnswer(inv -> GuildResponse.from(inv.getArgument(0), inv.getArgument(1),
                    testCategory.getName(), testCategory.getIcon()));

            // when
            GuildResponse response = guildService.updateGuild(1L, testMasterId, request);

            // then
            assertThat(response).isNotNull();
            assertThat(testGuild.getName()).isEqualTo("변경된 길드명");
            verify(guildRepository).existsByNameAndIsActiveTrue("변경된 길드명");
        }
    }
}
