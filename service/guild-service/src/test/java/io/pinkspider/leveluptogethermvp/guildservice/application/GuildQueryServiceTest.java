package io.pinkspider.leveluptogethermvp.guildservice.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.guildservice.application.GuildHelper;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildMemberResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildResponse;
import io.pinkspider.global.enums.ReportTargetType;
import io.pinkspider.leveluptogethermvp.adminservice.application.FeaturedContentQueryService;
import io.pinkspider.leveluptogethermvp.supportservice.report.application.ReportService;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildMember;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildJoinType;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberRole;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberStatus;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildVisibility;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildJoinRequestRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import io.pinkspider.leveluptogethermvp.userservice.profile.application.UserQueryFacadeService;
import io.pinkspider.leveluptogethermvp.gamificationservice.application.GamificationQueryFacadeService;
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

@ExtendWith(MockitoExtension.class)
class GuildQueryServiceTest {

    @Mock
    private GuildRepository guildRepository;

    @Mock
    private GuildMemberRepository guildMemberRepository;

    @Mock
    private GuildJoinRequestRepository joinRequestRepository;

    @Mock
    private FeaturedContentQueryService featuredContentQueryService;

    @Mock
    private UserQueryFacadeService userQueryFacadeService;

    @Mock
    private GamificationQueryFacadeService gamificationQueryFacadeService;

    @Mock
    private ReportService reportService;

    @Mock
    private GuildHelper guildHelper;

    @InjectMocks
    private GuildQueryService guildQueryService;

    private String testUserId;
    private String testMasterId;
    private Guild testGuild;
    private GuildMember testMasterMember;
    private Long testCategoryId;

    @BeforeEach
    void setUp() {
        testUserId = "test-user-id";
        testMasterId = "test-master-id";
        testCategoryId = 1L;

        testGuild = Guild.builder()
            .name("테스트 길드")
            .description("테스트 길드 설명")
            .visibility(GuildVisibility.PUBLIC)
            .joinType(GuildJoinType.APPROVAL_REQUIRED)
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

        // Default stub for guildHelper.buildGuildResponseWithCategory
        lenient().when(guildHelper.buildGuildResponseWithCategory(any(Guild.class), anyInt()))
            .thenAnswer(inv -> {
                Guild g = inv.getArgument(0);
                int mc = inv.getArgument(1);
                return GuildResponse.from(g, mc, null, null);
            });
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
            setId(featuredGuild, featuredGuildId);

            when(featuredContentQueryService.getActiveFeaturedGuildIds(eq(testCategoryId), any()))
                .thenReturn(List.of(featuredGuildId));
            when(guildRepository.findByIdAndIsActiveTrue(featuredGuildId))
                .thenReturn(Optional.of(featuredGuild));
            when(guildMemberRepository.countActiveMembers(featuredGuildId)).thenReturn(10L);


            // 자동 선정 길드
            when(guildRepository.findPublicGuildsByCategoryOrderByMemberCount(eq(testCategoryId), any()))
                .thenReturn(List.of(testGuild));
            when(guildMemberRepository.countActiveMembers(guildId)).thenReturn(5L);

            // when
            List<GuildResponse> result = guildQueryService.getPublicGuildsByCategory(testUserId, testCategoryId);

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
            when(featuredContentQueryService.getActiveFeaturedGuildIds(eq(testCategoryId), any()))
                .thenReturn(Collections.emptyList());
            when(guildRepository.findPublicGuildsByCategoryOrderByMemberCount(eq(testCategoryId), any()))
                .thenReturn(List.of(testGuild));
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(10L);


            // when
            List<GuildResponse> result = guildQueryService.getPublicGuildsByCategory(testUserId, testCategoryId);

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

            when(featuredContentQueryService.getActiveFeaturedGuildIds(eq(testCategoryId), any()))
                .thenReturn(List.of(guildId));
            when(guildRepository.findByIdAndIsActiveTrue(guildId))
                .thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.countActiveMembers(guildId)).thenReturn(10L);


            // 자동 선정에도 동일한 길드
            when(guildRepository.findPublicGuildsByCategoryOrderByMemberCount(eq(testCategoryId), any()))
                .thenReturn(List.of(testGuild));

            // when
            List<GuildResponse> result = guildQueryService.getPublicGuildsByCategory(testUserId, testCategoryId);

            // then
            assertThat(result).hasSize(1);  // 중복 제거됨
            assertThat(result.get(0).getId()).isEqualTo(guildId);
        }

        @Test
        @DisplayName("카테고리가 null이면 빈 목록을 반환한다")
        void getPublicGuildsByCategory_nullCategory() {
            // given
            when(featuredContentQueryService.getActiveFeaturedGuildIds(eq(null), any()))
                .thenReturn(Collections.emptyList());
            when(guildRepository.findPublicGuildsByCategoryOrderByMemberCount(eq(null), any()))
                .thenReturn(Collections.emptyList());

            // when
            List<GuildResponse> result = guildQueryService.getPublicGuildsByCategory(testUserId, null);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("최대 5개까지만 반환한다")
        void getPublicGuildsByCategory_maxFiveGuilds() {
            // given
            List<Long> manyFeaturedGuildIds = new java.util.ArrayList<>();
            for (int i = 1; i <= 6; i++) {
                Long guildId = (long) (i + 10);  // 11, 12, 13, ...
                manyFeaturedGuildIds.add(guildId);

                Guild guild = Guild.builder()
                    .name("길드 " + i)
                    .description("설명 " + i)
                    .visibility(GuildVisibility.PUBLIC)
                    .masterId("master-" + i)
                    .maxMembers(50)
                    .categoryId(testCategoryId)
                    .build();
                setId(guild, guildId);

                lenient().when(guildRepository.findByIdAndIsActiveTrue(guildId)).thenReturn(Optional.of(guild));
                lenient().when(guildMemberRepository.countActiveMembers(guildId)).thenReturn(5L);
            }

            when(featuredContentQueryService.getActiveFeaturedGuildIds(eq(testCategoryId), any()))
                .thenReturn(manyFeaturedGuildIds);


            // when
            List<GuildResponse> result = guildQueryService.getPublicGuildsByCategory(testUserId, testCategoryId);

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
            when(guildHelper.findActiveGuildById(1L)).thenReturn(testGuild);
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(5L);

            when(reportService.isUnderReview(ReportTargetType.GUILD, "1")).thenReturn(false);

            // when
            GuildResponse response = guildQueryService.getGuild(1L, testUserId);

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
            setId(privateGuild, 2L);

            when(guildHelper.findActiveGuildById(2L)).thenReturn(privateGuild);
            when(guildMemberRepository.isActiveMember(2L, testUserId)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> guildQueryService.getGuild(2L, testUserId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("비공개 길드에 접근할 수 없습니다.");
        }

        @Test
        @DisplayName("존재하지 않는 길드 조회 시 예외가 발생한다")
        void getGuild_notFound_throwsException() {
            // given
            when(guildHelper.findActiveGuildById(999L)).thenThrow(new IllegalArgumentException("길드를 찾을 수 없습니다: 999"));

            // when & then
            assertThatThrownBy(() -> guildQueryService.getGuild(999L, testUserId))
                .isInstanceOf(IllegalArgumentException.class);
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


            // when
            org.springframework.data.domain.Page<GuildResponse> result = guildQueryService.getPublicGuilds(testUserId, pageable);

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


            // when
            org.springframework.data.domain.Page<GuildResponse> result = guildQueryService.searchGuilds(testUserId, keyword, pageable);

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


            // when
            List<GuildResponse> result = guildQueryService.getMyGuilds(testUserId);

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
            List<GuildResponse> result = guildQueryService.getMyGuilds(testUserId);

            // then
            assertThat(result).isEmpty();
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

            when(guildHelper.findActiveGuildById(1L)).thenReturn(testGuild);
            lenient().when(guildMemberRepository.isActiveMember(1L, testMasterId)).thenReturn(true);
            when(guildMemberRepository.findActiveMembers(1L))
                .thenReturn(List.of(testMasterMember, member));
            when(userQueryFacadeService.getUserProfiles(anyList())).thenReturn(java.util.Map.of());

            // when
            List<GuildMemberResponse> result = guildQueryService.getGuildMembers(1L, testMasterId);

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
            setId(privateGuild, 2L);

            when(guildHelper.findActiveGuildById(2L)).thenReturn(privateGuild);
            when(guildMemberRepository.isActiveMember(2L, testUserId)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> guildQueryService.getGuildMembers(2L, testUserId))
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
            when(guildHelper.findActiveGuildById(1L)).thenReturn(testGuild);
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(5L);

            when(reportService.isUnderReview(ReportTargetType.GUILD, "1")).thenReturn(true);

            // when
            GuildResponse response = guildQueryService.getGuild(1L, testUserId);

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


            Map<String, Boolean> underReviewMap = new HashMap<>();
            underReviewMap.put("1", true);
            when(reportService.isUnderReviewBatch(eq(ReportTargetType.GUILD), anyList())).thenReturn(underReviewMap);

            // when
            org.springframework.data.domain.Page<GuildResponse> result = guildQueryService.getPublicGuilds(testUserId, pageable);

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


            Map<String, Boolean> underReviewMap = new HashMap<>();
            underReviewMap.put("1", false);
            when(reportService.isUnderReviewBatch(eq(ReportTargetType.GUILD), anyList())).thenReturn(underReviewMap);

            // when
            List<GuildResponse> result = guildQueryService.getMyGuilds(testUserId);

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


            Map<String, Boolean> underReviewMap = new HashMap<>();
            underReviewMap.put("1", true);
            when(reportService.isUnderReviewBatch(eq(ReportTargetType.GUILD), anyList())).thenReturn(underReviewMap);

            // when
            org.springframework.data.domain.Page<GuildResponse> result = guildQueryService.searchGuilds(testUserId, keyword, pageable);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getIsUnderReview()).isTrue();
            verify(reportService).isUnderReviewBatch(eq(ReportTargetType.GUILD), anyList());
        }

        @Test
        @DisplayName("카테고리별 공개 길드 조회 시 신고 처리중 상태가 일괄 조회된다")
        void getPublicGuildsByCategory_batchUnderReviewCheck() {
            // given
            when(featuredContentQueryService.getActiveFeaturedGuildIds(eq(testCategoryId), any()))
                .thenReturn(Collections.emptyList());
            when(guildRepository.findPublicGuildsByCategoryOrderByMemberCount(eq(testCategoryId), any()))
                .thenReturn(List.of(testGuild));
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(10L);


            Map<String, Boolean> underReviewMap = new HashMap<>();
            underReviewMap.put("1", true);
            when(reportService.isUnderReviewBatch(eq(ReportTargetType.GUILD), anyList())).thenReturn(underReviewMap);

            // when
            List<GuildResponse> result = guildQueryService.getPublicGuildsByCategory(testUserId, testCategoryId);

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
            List<GuildResponse> result = guildQueryService.getMyGuilds(testUserId);

            // then
            assertThat(result).isEmpty();
            verify(reportService, never()).isUnderReviewBatch(any(), anyList());
        }
    }
}
