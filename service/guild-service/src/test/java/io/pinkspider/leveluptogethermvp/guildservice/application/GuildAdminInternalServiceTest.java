package io.pinkspider.leveluptogethermvp.guildservice.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.global.facade.dto.UserProfileInfo;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.admin.GuildAdminPageResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.admin.GuildAdminResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.admin.GuildMemberAdminResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.admin.GuildStatisticsAdminResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildMember;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberStatus;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildVisibility;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import io.pinkspider.leveluptogethermvp.metaservice.application.MissionCategoryService;
import io.pinkspider.leveluptogethermvp.metaservice.domain.dto.MissionCategoryResponse;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class GuildAdminInternalServiceTest {

    @Mock
    private GuildRepository guildRepository;

    @Mock
    private GuildMemberRepository guildMemberRepository;

    @Mock
    private MissionCategoryService missionCategoryService;

    @Mock
    private UserQueryFacade userQueryFacadeService;

    @InjectMocks
    private GuildAdminInternalService service;

    private Guild createTestGuild(Long id) {
        Guild guild = Guild.builder()
            .name("테스트 길드")
            .description("설명")
            .visibility(GuildVisibility.PUBLIC)
            .masterId("master-1")
            .categoryId(1L)
            .isActive(true)
            .build();
        setId(guild, id);
        return guild;
    }

    @Nested
    @DisplayName("searchGuilds 테스트")
    class SearchGuildsTest {

        @Test
        @DisplayName("키워드로 길드를 검색한다")
        void searchByKeyword() {
            // given
            Guild guild = createTestGuild(1L);
            Page<Guild> page = new PageImpl<>(List.of(guild));
            Pageable pageable = PageRequest.of(0, 10);
            when(guildRepository.searchGuildsForAdmin(any(), anyBoolean(), anyList(), any(), any(), any()))
                .thenReturn(page);
            when(missionCategoryService.getAllCategories()).thenReturn(List.of());
            when(guildMemberRepository.countActiveMembersByGuildIds(anyList()))
                .thenReturn(Collections.singletonList(new Object[]{1L, 5L}));
            when(userQueryFacadeService.getUserProfiles(anyList()))
                .thenReturn(Map.of("master-1", new UserProfileInfo("master-1", "마스터", null, null, null, null, null)));

            // when
            GuildAdminPageResponse result = service.searchGuilds("테스트", null, null, null, pageable);

            // then
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("visibility 필터로 검색한다")
        void searchByVisibility() {
            // given
            Page<Guild> page = new PageImpl<>(List.of());
            Pageable pageable = PageRequest.of(0, 10);
            when(guildRepository.searchGuildsForAdmin(any(), anyBoolean(), anyList(), any(), eq(GuildVisibility.PRIVATE), any()))
                .thenReturn(page);
            when(missionCategoryService.getAllCategories()).thenReturn(List.of());

            // when
            GuildAdminPageResponse result = service.searchGuilds(null, null, null, "PRIVATE", pageable);

            // then
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("categoryIds가 있으면 멀티 카테고리 필터로 검색한다")
        void searchByCategoryIds() {
            // given
            Page<Guild> page = new PageImpl<>(List.of());
            Pageable pageable = PageRequest.of(0, 10);
            when(guildRepository.searchGuildsForAdmin(any(), eq(true), eq(List.of(1L, 2L)), any(), any(), any()))
                .thenReturn(page);
            when(missionCategoryService.getAllCategories()).thenReturn(List.of());

            // when
            GuildAdminPageResponse result =
                service.searchGuilds(null, 9L, List.of(1L, 2L), null, null, pageable);

            // then
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("categoryIds가 비어있고 categoryId가 있으면 단일 카테고리 필터를 사용한다")
        void searchByEmptyCategoryIdsFallsBackToCategoryId() {
            // given
            Page<Guild> page = new PageImpl<>(List.of());
            Pageable pageable = PageRequest.of(0, 10);
            when(guildRepository.searchGuildsForAdmin(any(), eq(true), eq(List.of(7L)), any(), any(), any()))
                .thenReturn(page);
            when(missionCategoryService.getAllCategories()).thenReturn(List.of());

            // when
            GuildAdminPageResponse result =
                service.searchGuilds(null, 7L, List.of(), null, null, pageable);

            // then
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("categoryIds가 null이고 categoryId가 있으면 단일 카테고리 필터를 사용한다")
        void searchByNullCategoryIdsWithCategoryId() {
            // given
            Page<Guild> page = new PageImpl<>(List.of());
            Pageable pageable = PageRequest.of(0, 10);
            when(guildRepository.searchGuildsForAdmin(any(), eq(true), eq(List.of(3L)), any(), any(), any()))
                .thenReturn(page);
            when(missionCategoryService.getAllCategories()).thenReturn(List.of());

            // when
            GuildAdminPageResponse result = service.searchGuilds(null, 3L, null, null, null, pageable);

            // then
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("카테고리 맵에 있으면 카테고리 이름·아이콘을 채운다")
        void fillsCategoryNameAndIcon() {
            // given
            Guild guild = createTestGuild(1L);
            Page<Guild> page = new PageImpl<>(List.of(guild));
            Pageable pageable = PageRequest.of(0, 10);
            when(guildRepository.searchGuildsForAdmin(any(), anyBoolean(), anyList(), any(), any(), any()))
                .thenReturn(page);
            when(missionCategoryService.getAllCategories())
                .thenReturn(List.of(MissionCategoryResponse.builder().id(1L).name("운동").icon("dumbbell").build()));
            when(guildMemberRepository.countActiveMembersByGuildIds(anyList())).thenReturn(List.of());
            when(userQueryFacadeService.getUserProfiles(anyList())).thenReturn(Map.of());

            // when
            GuildAdminPageResponse result = service.searchGuilds(null, null, null, null, pageable);

            // then
            assertThat(result.content().get(0).categoryName()).isEqualTo("운동");
            assertThat(result.content().get(0).categoryIcon()).isEqualTo("dumbbell");
            assertThat(result.content().get(0).currentMemberCount()).isZero();
        }

        @Test
        @DisplayName("마스터가 없는 길드는 마스터 닉네임 조회를 건너뛴다")
        void skipsMasterNicknameWhenMasterIdNull() {
            // given
            Guild guild = Guild.builder()
                .name("주인 없는 길드")
                .visibility(GuildVisibility.PUBLIC)
                .masterId(null)
                .categoryId(1L)
                .build();
            setId(guild, 5L);
            Page<Guild> page = new PageImpl<>(List.of(guild));
            Pageable pageable = PageRequest.of(0, 10);
            when(guildRepository.searchGuildsForAdmin(any(), anyBoolean(), anyList(), any(), any(), any()))
                .thenReturn(page);
            when(missionCategoryService.getAllCategories()).thenReturn(List.of());
            when(guildMemberRepository.countActiveMembersByGuildIds(anyList())).thenReturn(List.of());

            // when
            GuildAdminPageResponse result = service.searchGuilds(null, null, null, null, pageable);

            // then
            assertThat(result.content().get(0).masterNickname()).isNull();
            verify(userQueryFacadeService, never()).getUserProfiles(anyList());
        }

        @Test
        @DisplayName("마스터 프로필이 null이면 닉네임 맵에서 제외한다")
        void excludesNullProfileFromMasterNicknameMap() {
            // given
            Guild guild = createTestGuild(1L);
            Page<Guild> page = new PageImpl<>(List.of(guild));
            Pageable pageable = PageRequest.of(0, 10);
            when(guildRepository.searchGuildsForAdmin(any(), anyBoolean(), anyList(), any(), any(), any()))
                .thenReturn(page);
            when(missionCategoryService.getAllCategories()).thenReturn(List.of());
            when(guildMemberRepository.countActiveMembersByGuildIds(anyList())).thenReturn(List.of());
            Map<String, UserProfileInfo> profiles = new HashMap<>();
            profiles.put("master-1", null);
            when(userQueryFacadeService.getUserProfiles(anyList())).thenReturn(profiles);

            // when
            GuildAdminPageResponse result = service.searchGuilds(null, null, null, null, pageable);

            // then
            assertThat(result.content().get(0).masterNickname()).isNull();
        }

        @Test
        @DisplayName("카테고리 조회 실패 시 빈 카테고리 맵으로 진행한다")
        void proceedsWhenCategoryLookupFails() {
            // given
            Guild guild = createTestGuild(1L);
            Page<Guild> page = new PageImpl<>(List.of(guild));
            Pageable pageable = PageRequest.of(0, 10);
            when(guildRepository.searchGuildsForAdmin(any(), anyBoolean(), anyList(), any(), any(), any()))
                .thenReturn(page);
            when(missionCategoryService.getAllCategories()).thenThrow(new RuntimeException("meta down"));
            when(guildMemberRepository.countActiveMembersByGuildIds(anyList())).thenReturn(List.of());
            when(userQueryFacadeService.getUserProfiles(anyList())).thenReturn(Map.of());

            // when
            GuildAdminPageResponse result = service.searchGuilds(null, null, null, null, pageable);

            // then
            assertThat(result.content().get(0).categoryName()).isNull();
        }
    }

    @Nested
    @DisplayName("getGuild 테스트")
    class GetGuildTest {

        @Test
        @DisplayName("길드를 조회한다")
        void getGuild() {
            // given
            Guild guild = createTestGuild(1L);
            when(guildRepository.findById(1L)).thenReturn(Optional.of(guild));
            when(missionCategoryService.getCategory(1L))
                .thenReturn(MissionCategoryResponse.builder().id(1L).name("카테고리").icon("icon").build());
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(5L);
            when(userQueryFacadeService.getUserProfile("master-1"))
                .thenReturn(new UserProfileInfo("master-1", "마스터", null, null, null, null, null));

            // when
            GuildAdminResponse result = service.getGuild(1L);

            // then
            assertThat(result.name()).isEqualTo("테스트 길드");
        }

        @Test
        @DisplayName("존재하지 않는 길드는 예외를 발생시킨다")
        void throwsWhenNotFound() {
            when(guildRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getGuild(999L))
                .isInstanceOf(CustomException.class);
        }

        @Test
        @DisplayName("카테고리 조회 실패·마스터 없음이면 카테고리·닉네임이 null이다")
        void nullCategoryAndNullMaster() {
            // given
            Guild guild = Guild.builder()
                .name("주인 없는 길드")
                .visibility(GuildVisibility.PUBLIC)
                .masterId(null)
                .categoryId(1L)
                .build();
            setId(guild, 1L);
            when(guildRepository.findById(1L)).thenReturn(Optional.of(guild));
            when(missionCategoryService.getCategory(1L)).thenThrow(new RuntimeException("meta down"));
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(0L);

            // when
            GuildAdminResponse result = service.getGuild(1L);

            // then
            assertThat(result.categoryName()).isNull();
            assertThat(result.categoryIcon()).isNull();
            assertThat(result.masterNickname()).isNull();
            verify(userQueryFacadeService, never()).getUserProfile(any());
        }

        @Test
        @DisplayName("마스터 프로필이 null이면 닉네임이 null이다")
        void nullMasterProfile() {
            // given
            Guild guild = createTestGuild(1L);
            when(guildRepository.findById(1L)).thenReturn(Optional.of(guild));
            when(missionCategoryService.getCategory(1L)).thenReturn(null);
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(0L);
            when(userQueryFacadeService.getUserProfile("master-1")).thenReturn(null);

            // when
            GuildAdminResponse result = service.getGuild(1L);

            // then
            assertThat(result.masterNickname()).isNull();
        }
    }

    @Nested
    @DisplayName("getStatistics 테스트")
    class GetStatisticsTest {

        @Test
        @DisplayName("통계 정보를 조회한다")
        void getStatistics() {
            // given
            when(guildRepository.count()).thenReturn(100L);
            when(guildRepository.countByIsActiveTrue()).thenReturn(80L);
            when(guildRepository.countByIsActiveFalse()).thenReturn(20L);
            when(guildRepository.countByVisibility(GuildVisibility.PUBLIC)).thenReturn(60L);
            when(guildRepository.countByVisibility(GuildVisibility.PRIVATE)).thenReturn(40L);
            when(guildRepository.countByCreatedAtAfter(any())).thenReturn(5L);
            when(missionCategoryService.getAllCategories()).thenReturn(List.of());
            when(guildRepository.countGuildsByCategory()).thenReturn(List.of());
            when(guildRepository.countDailyNewGuilds(any(), any())).thenReturn(List.of());

            // when
            GuildStatisticsAdminResponse result = service.getStatistics();

            // then
            assertThat(result.totalGuilds()).isEqualTo(100L);
            assertThat(result.activeGuilds()).isEqualTo(80L);
            assertThat(result.inactiveGuilds()).isEqualTo(20L);
        }

        @Test
        @DisplayName("카테고리별 통계는 알려진 카테고리 이름, 미지 카테고리는 Unknown으로 집계한다")
        void aggregatesByCategoryName() {
            // given
            when(guildRepository.count()).thenReturn(3L);
            when(guildRepository.countByIsActiveTrue()).thenReturn(3L);
            when(guildRepository.countByIsActiveFalse()).thenReturn(0L);
            when(guildRepository.countByVisibility(any())).thenReturn(1L);
            when(guildRepository.countByCreatedAtAfter(any())).thenReturn(1L);
            when(missionCategoryService.getAllCategories())
                .thenReturn(List.of(MissionCategoryResponse.builder().id(1L).name("운동").build()));
            List<Object[]> categoryStats = List.of(new Object[]{1L, 2L}, new Object[]{99L, 1L});
            when(guildRepository.countGuildsByCategory()).thenReturn(categoryStats);
            List<Object[]> dailyStats =
                Collections.singletonList(new Object[]{java.sql.Date.valueOf("2026-01-01"), 2L});
            when(guildRepository.countDailyNewGuilds(any(), any())).thenReturn(dailyStats);

            // when
            GuildStatisticsAdminResponse result = service.getStatistics();

            // then
            assertThat(result.guildsByCategory()).containsEntry("운동", 2L).containsEntry("Unknown", 1L);
            assertThat(result.dailyNewGuilds()).hasSize(1);
            assertThat(result.dailyNewGuilds().get(0).count()).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("getGuildMembers 테스트")
    class GetGuildMembersTest {

        @Test
        @DisplayName("길드 멤버 목록을 조회한다")
        void getGuildMembers() {
            // given
            Guild guild = createTestGuild(1L);
            when(guildRepository.existsById(1L)).thenReturn(true);
            GuildMember member = GuildMember.builder()
                .guild(guild)
                .userId("user-1")
                .status(GuildMemberStatus.ACTIVE)
                .build();
            setId(member, 1L);
            when(guildMemberRepository.findByGuildIdAndStatus(1L, GuildMemberStatus.ACTIVE))
                .thenReturn(List.of(member));
            when(userQueryFacadeService.getUserProfiles(anyList()))
                .thenReturn(Map.of("user-1", new UserProfileInfo("user-1", "유저", "img.png", null, null, null, null)));

            // when
            List<GuildMemberAdminResponse> result = service.getGuildMembers(1L);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).userId()).isEqualTo("user-1");
        }

        @Test
        @DisplayName("존재하지 않는 길드는 예외를 발생시킨다")
        void throwsWhenGuildNotFound() {
            when(guildRepository.existsById(999L)).thenReturn(false);

            assertThatThrownBy(() -> service.getGuildMembers(999L))
                .isInstanceOf(CustomException.class);
        }

        @Test
        @DisplayName("프로필이 없는 멤버는 닉네임·이미지가 null이다")
        void nullProfileMember() {
            // given
            Guild guild = createTestGuild(1L);
            when(guildRepository.existsById(1L)).thenReturn(true);
            GuildMember member = GuildMember.builder()
                .guild(guild)
                .userId("user-x")
                .status(GuildMemberStatus.ACTIVE)
                .build();
            setId(member, 1L);
            when(guildMemberRepository.findByGuildIdAndStatus(1L, GuildMemberStatus.ACTIVE))
                .thenReturn(List.of(member));
            when(userQueryFacadeService.getUserProfiles(anyList())).thenReturn(Map.of());

            // when
            List<GuildMemberAdminResponse> result = service.getGuildMembers(1L);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).userNickname()).isNull();
            assertThat(result.get(0).userProfileImage()).isNull();
        }
    }

    @Nested
    @DisplayName("toggleActive 테스트")
    class ToggleActiveTest {

        @Test
        @DisplayName("길드 활성 상태를 토글한다")
        void toggleActive() {
            // given
            Guild guild = createTestGuild(1L);
            when(guildRepository.findById(1L)).thenReturn(Optional.of(guild));
            when(guildRepository.save(any(Guild.class))).thenReturn(guild);
            when(missionCategoryService.getCategory(1L)).thenReturn(null);
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(3L);
            when(userQueryFacadeService.getUserProfile("master-1")).thenReturn(null);

            // when
            GuildAdminResponse result = service.toggleActive(1L);

            // then
            assertThat(result).isNotNull();
            verify(guildRepository).save(any(Guild.class));
        }

        @Test
        @DisplayName("비활성 길드를 토글하면 활성이 되고 카테고리·닉네임을 채운다")
        void toggleInactiveToActiveWithCategory() {
            // given
            Guild guild = createTestGuild(1L);
            guild.setIsActive(false);
            when(guildRepository.findById(1L)).thenReturn(Optional.of(guild));
            when(guildRepository.save(any(Guild.class))).thenReturn(guild);
            when(missionCategoryService.getCategory(1L))
                .thenReturn(MissionCategoryResponse.builder().id(1L).name("운동").icon("dumbbell").build());
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(3L);
            when(userQueryFacadeService.getUserProfile("master-1"))
                .thenReturn(new UserProfileInfo("master-1", "마스터", null, null, null, null, null));

            // when
            GuildAdminResponse result = service.toggleActive(1L);

            // then
            assertThat(result.isActive()).isTrue();
            assertThat(result.categoryName()).isEqualTo("운동");
            assertThat(result.categoryIcon()).isEqualTo("dumbbell");
            assertThat(result.masterNickname()).isEqualTo("마스터");
        }

        @Test
        @DisplayName("존재하지 않는 길드를 토글하면 예외를 발생시킨다")
        void throwsWhenNotFound() {
            when(guildRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.toggleActive(999L))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("banFromReport 테스트")
    class BanFromReportTest {

        @Test
        @DisplayName("신고 처리로 길드를 차단한다")
        void bansGuild() {
            Guild guild = createTestGuild(1L);
            when(guildRepository.findById(1L)).thenReturn(Optional.of(guild));

            service.banFromReport(1L, "부적절");

            assertThat(guild.getIsBanned()).isTrue();
            verify(guildRepository).save(guild);
        }

        @Test
        @DisplayName("존재하지 않는 길드 차단은 예외를 발생시킨다")
        void throwsWhenNotFound() {
            when(guildRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.banFromReport(999L, "x"))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("getGuildNamesByIds 테스트")
    class GetGuildNamesByIdsTest {

        @Test
        @DisplayName("길드 이름 맵을 반환한다")
        void returnsGuildNames() {
            Guild guild1 = createTestGuild(1L);
            Guild guild2 = createTestGuild(2L);
            guild2.setName("두번째 길드");
            when(guildRepository.findAllById(anyList())).thenReturn(List.of(guild1, guild2));

            Map<Long, String> result = service.getGuildNamesByIds(List.of(1L, 2L));

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("빈 목록이면 빈 맵을 반환한다")
        void returnsEmptyForEmptyInput() {
            Map<Long, String> result = service.getGuildNamesByIds(List.of());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null이면 빈 맵을 반환한다")
        void returnsEmptyForNull() {
            Map<Long, String> result = service.getGuildNamesByIds(null);
            assertThat(result).isEmpty();
        }
    }
}
