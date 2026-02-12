package io.pinkspider.leveluptogethermvp.guildservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.guildservice.application.GuildQueryFacadeService.GuildPostInfo;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildFacadeDto.GuildBasicInfo;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildFacadeDto.GuildMembershipInfo;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildFacadeDto.GuildPermissionCheck;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildFacadeDto.GuildWithMemberCount;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildMember;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildPost;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberRole;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberStatus;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildVisibility;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildExperienceHistoryRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildPostRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("GuildQueryFacadeService 테스트")
class GuildQueryFacadeServiceTest {

    @Mock
    private GuildRepository guildRepository;

    @Mock
    private GuildMemberRepository guildMemberRepository;

    @Mock
    private GuildExperienceHistoryRepository guildExpHistoryRepository;

    @Mock
    private GuildPostRepository guildPostRepository;

    @InjectMocks
    private GuildQueryFacadeService facadeService;

    // ========== 테스트 헬퍼 ==========

    private Guild createGuild(Long id, String name, String masterId) {
        Guild guild = Guild.builder()
            .name(name)
            .masterId(masterId)
            .visibility(GuildVisibility.PUBLIC)
            .imageUrl("https://img.test.com/guild/" + id + ".png")
            .currentLevel(3)
            .isActive(true)
            .categoryId(1L)
            .build();
        setEntityId(guild, id, Guild.class);
        return guild;
    }

    private GuildMember createMember(Long id, Guild guild, String userId, GuildMemberRole role) {
        GuildMember member = GuildMember.builder()
            .guild(guild)
            .userId(userId)
            .role(role)
            .status(GuildMemberStatus.ACTIVE)
            .build();
        setEntityId(member, id, GuildMember.class);
        return member;
    }

    private GuildPost createPost(Long id, Guild guild) {
        GuildPost post = GuildPost.builder()
            .guild(guild)
            .authorId("author-1")
            .build();
        setEntityId(post, id, GuildPost.class);
        return post;
    }

    private <T> void setEntityId(T entity, Long id, Class<T> clazz) {
        try {
            Field idField = clazz.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ========== 단일 길드 정보 ==========

    @Nested
    @DisplayName("guildExists")
    class GuildExistsTest {

        @Test
        @DisplayName("활성 길드가 존재하면 true 반환")
        void shouldReturnTrueWhenGuildExists() {
            when(guildRepository.existsByIdAndIsActiveTrue(1L)).thenReturn(true);

            assertThat(facadeService.guildExists(1L)).isTrue();
        }

        @Test
        @DisplayName("길드가 존재하지 않으면 false 반환")
        void shouldReturnFalseWhenGuildNotExists() {
            when(guildRepository.existsByIdAndIsActiveTrue(999L)).thenReturn(false);

            assertThat(facadeService.guildExists(999L)).isFalse();
        }
    }

    @Nested
    @DisplayName("getGuildName")
    class GetGuildNameTest {

        @Test
        @DisplayName("길드 이름 반환")
        void shouldReturnGuildName() {
            Guild guild = createGuild(1L, "테스트 길드", "master-1");
            when(guildRepository.findById(1L)).thenReturn(Optional.of(guild));

            assertThat(facadeService.getGuildName(1L)).isEqualTo("테스트 길드");
        }

        @Test
        @DisplayName("길드가 없으면 null 반환")
        void shouldReturnNullWhenGuildNotFound() {
            when(guildRepository.findById(999L)).thenReturn(Optional.empty());

            assertThat(facadeService.getGuildName(999L)).isNull();
        }
    }

    @Nested
    @DisplayName("getGuildMasterId")
    class GetGuildMasterIdTest {

        @Test
        @DisplayName("길드 마스터 ID 반환")
        void shouldReturnMasterId() {
            Guild guild = createGuild(1L, "길드", "master-123");
            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(guild));

            assertThat(facadeService.getGuildMasterId(1L)).isEqualTo("master-123");
        }

        @Test
        @DisplayName("길드가 없으면 null 반환")
        void shouldReturnNullWhenGuildNotFound() {
            when(guildRepository.findByIdAndIsActiveTrue(999L)).thenReturn(Optional.empty());

            assertThat(facadeService.getGuildMasterId(999L)).isNull();
        }
    }

    @Nested
    @DisplayName("isMaster")
    class IsMasterTest {

        @Test
        @DisplayName("마스터인 경우 true 반환")
        void shouldReturnTrueWhenUserIsMaster() {
            Guild guild = createGuild(1L, "길드", "master-1");
            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(guild));

            assertThat(facadeService.isMaster(1L, "master-1")).isTrue();
        }

        @Test
        @DisplayName("마스터가 아닌 경우 false 반환")
        void shouldReturnFalseWhenUserIsNotMaster() {
            Guild guild = createGuild(1L, "길드", "master-1");
            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(guild));

            assertThat(facadeService.isMaster(1L, "other-user")).isFalse();
        }

        @Test
        @DisplayName("길드가 없으면 false 반환")
        void shouldReturnFalseWhenGuildNotFound() {
            when(guildRepository.findByIdAndIsActiveTrue(999L)).thenReturn(Optional.empty());

            assertThat(facadeService.isMaster(999L, "any-user")).isFalse();
        }
    }

    @Nested
    @DisplayName("getGuildBasicInfo")
    class GetGuildBasicInfoTest {

        @Test
        @DisplayName("길드 기본 정보 반환")
        void shouldReturnGuildBasicInfo() {
            Guild guild = createGuild(1L, "테스트 길드", "master-1");
            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(guild));

            GuildBasicInfo result = facadeService.getGuildBasicInfo(1L);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.name()).isEqualTo("테스트 길드");
            assertThat(result.imageUrl()).isEqualTo("https://img.test.com/guild/1.png");
            assertThat(result.currentLevel()).isEqualTo(3);
        }

        @Test
        @DisplayName("길드가 없으면 null 반환")
        void shouldReturnNullWhenGuildNotFound() {
            when(guildRepository.findByIdAndIsActiveTrue(999L)).thenReturn(Optional.empty());

            assertThat(facadeService.getGuildBasicInfo(999L)).isNull();
        }
    }

    // ========== 배치 길드 정보 ==========

    @Nested
    @DisplayName("getGuildsWithMemberCounts")
    class GetGuildsWithMemberCountsTest {

        @Test
        @DisplayName("길드 목록과 멤버 수 반환")
        void shouldReturnGuildsWithMemberCounts() {
            List<Long> guildIds = List.of(1L, 2L);
            Guild guild1 = createGuild(1L, "길드1", "master-1");
            Guild guild2 = createGuild(2L, "길드2", "master-2");

            when(guildRepository.findByIdInAndIsActiveTrue(guildIds))
                .thenReturn(List.of(guild1, guild2));
            when(guildMemberRepository.countActiveMembersByGuildIds(guildIds))
                .thenReturn(Arrays.<Object[]>asList(new Object[]{1L, 5L}, new Object[]{2L, 3L}));

            List<GuildWithMemberCount> result = facadeService.getGuildsWithMemberCounts(guildIds);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).id()).isEqualTo(1L);
            assertThat(result.get(0).memberCount()).isEqualTo(5);
            assertThat(result.get(1).id()).isEqualTo(2L);
            assertThat(result.get(1).memberCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("비활성 길드는 결과에서 제외")
        void shouldFilterOutInactiveGuilds() {
            List<Long> guildIds = List.of(1L, 2L);
            Guild guild1 = createGuild(1L, "길드1", "master-1");

            when(guildRepository.findByIdInAndIsActiveTrue(guildIds))
                .thenReturn(List.of(guild1)); // guild2는 비활성
            when(guildMemberRepository.countActiveMembersByGuildIds(guildIds))
                .thenReturn(Arrays.<Object[]>asList(new Object[]{1L, 5L}));

            List<GuildWithMemberCount> result = facadeService.getGuildsWithMemberCounts(guildIds);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo(1L);
        }

        @Test
        @DisplayName("빈 목록이면 빈 결과 반환")
        void shouldReturnEmptyForEmptyInput() {
            assertThat(facadeService.getGuildsWithMemberCounts(List.of())).isEmpty();
            assertThat(facadeService.getGuildsWithMemberCounts(null)).isEmpty();
            verify(guildRepository, never()).findByIdInAndIsActiveTrue(any());
        }

        @Test
        @DisplayName("멤버 수 정보가 없는 길드는 0으로 반환")
        void shouldReturnZeroMemberCountWhenNoData() {
            List<Long> guildIds = List.of(1L);
            Guild guild1 = createGuild(1L, "길드1", "master-1");

            when(guildRepository.findByIdInAndIsActiveTrue(guildIds))
                .thenReturn(List.of(guild1));
            when(guildMemberRepository.countActiveMembersByGuildIds(guildIds))
                .thenReturn(List.of()); // 멤버 수 데이터 없음

            List<GuildWithMemberCount> result = facadeService.getGuildsWithMemberCounts(guildIds);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).memberCount()).isEqualTo(0);
        }
    }

    // ========== 멤버십 조회 ==========

    @Nested
    @DisplayName("isActiveMember")
    class IsActiveMemberTest {

        @Test
        @DisplayName("활성 멤버이면 true 반환")
        void shouldReturnTrueForActiveMember() {
            when(guildMemberRepository.isActiveMember(1L, "user-1")).thenReturn(true);

            assertThat(facadeService.isActiveMember(1L, "user-1")).isTrue();
        }

        @Test
        @DisplayName("활성 멤버가 아니면 false 반환")
        void shouldReturnFalseForNonMember() {
            when(guildMemberRepository.isActiveMember(1L, "user-1")).thenReturn(false);

            assertThat(facadeService.isActiveMember(1L, "user-1")).isFalse();
        }
    }

    @Nested
    @DisplayName("getActiveMemberUserIds")
    class GetActiveMemberUserIdsTest {

        @Test
        @DisplayName("활성 멤버 ID 목록 반환")
        void shouldReturnActiveMemberUserIds() {
            Guild guild = createGuild(1L, "길드", "master-1");
            GuildMember m1 = createMember(1L, guild, "user-1", GuildMemberRole.MASTER);
            GuildMember m2 = createMember(2L, guild, "user-2", GuildMemberRole.MEMBER);
            GuildMember m3 = createMember(3L, guild, "user-3", GuildMemberRole.MEMBER);

            when(guildMemberRepository.findActiveMembers(1L))
                .thenReturn(List.of(m1, m2, m3));

            List<String> result = facadeService.getActiveMemberUserIds(1L);

            assertThat(result).containsExactly("user-1", "user-2", "user-3");
        }

        @Test
        @DisplayName("멤버가 없으면 빈 목록 반환")
        void shouldReturnEmptyListWhenNoMembers() {
            when(guildMemberRepository.findActiveMembers(1L)).thenReturn(List.of());

            assertThat(facadeService.getActiveMemberUserIds(1L)).isEmpty();
        }
    }

    @Nested
    @DisplayName("getActiveMemberCount")
    class GetActiveMemberCountTest {

        @Test
        @DisplayName("활성 멤버 수 반환")
        void shouldReturnActiveMemberCount() {
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(5L);

            assertThat(facadeService.getActiveMemberCount(1L)).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("checkPermissions")
    class CheckPermissionsTest {

        @Test
        @DisplayName("마스터 권한 체크")
        void shouldReturnMasterPermissions() {
            Guild guild = createGuild(1L, "길드", "master-1");
            GuildMember master = createMember(1L, guild, "master-1", GuildMemberRole.MASTER);

            when(guildMemberRepository.findByGuildIdAndUserId(1L, "master-1"))
                .thenReturn(Optional.of(master));

            GuildPermissionCheck result = facadeService.checkPermissions(1L, "master-1");

            assertThat(result.isActiveMember()).isTrue();
            assertThat(result.isMaster()).isTrue();
            assertThat(result.isSubMaster()).isFalse();
            assertThat(result.isMasterOrSubMaster()).isTrue();
        }

        @Test
        @DisplayName("부마스터 권한 체크")
        void shouldReturnSubMasterPermissions() {
            Guild guild = createGuild(1L, "길드", "master-1");
            GuildMember subMaster = createMember(2L, guild, "sub-master-1", GuildMemberRole.SUB_MASTER);

            when(guildMemberRepository.findByGuildIdAndUserId(1L, "sub-master-1"))
                .thenReturn(Optional.of(subMaster));

            GuildPermissionCheck result = facadeService.checkPermissions(1L, "sub-master-1");

            assertThat(result.isActiveMember()).isTrue();
            assertThat(result.isMaster()).isFalse();
            assertThat(result.isSubMaster()).isTrue();
            assertThat(result.isMasterOrSubMaster()).isTrue();
        }

        @Test
        @DisplayName("일반 멤버 권한 체크")
        void shouldReturnMemberPermissions() {
            Guild guild = createGuild(1L, "길드", "master-1");
            GuildMember member = createMember(3L, guild, "user-1", GuildMemberRole.MEMBER);

            when(guildMemberRepository.findByGuildIdAndUserId(1L, "user-1"))
                .thenReturn(Optional.of(member));

            GuildPermissionCheck result = facadeService.checkPermissions(1L, "user-1");

            assertThat(result.isActiveMember()).isTrue();
            assertThat(result.isMaster()).isFalse();
            assertThat(result.isSubMaster()).isFalse();
            assertThat(result.isMasterOrSubMaster()).isFalse();
        }

        @Test
        @DisplayName("멤버가 아닌 경우 모두 false 반환")
        void shouldReturnAllFalseWhenNotMember() {
            when(guildMemberRepository.findByGuildIdAndUserId(1L, "unknown"))
                .thenReturn(Optional.empty());

            GuildPermissionCheck result = facadeService.checkPermissions(1L, "unknown");

            assertThat(result.isActiveMember()).isFalse();
            assertThat(result.isMaster()).isFalse();
            assertThat(result.isSubMaster()).isFalse();
        }
    }

    @Nested
    @DisplayName("getUserGuildMemberships")
    class GetUserGuildMembershipsTest {

        @Test
        @DisplayName("사용자 길드 멤버십 목록 반환")
        void shouldReturnUserGuildMemberships() {
            Guild guild1 = createGuild(1L, "길드1", "user-1");
            Guild guild2 = createGuild(2L, "길드2", "other-master");
            GuildMember m1 = createMember(1L, guild1, "user-1", GuildMemberRole.MASTER);
            GuildMember m2 = createMember(2L, guild2, "user-1", GuildMemberRole.MEMBER);

            when(guildMemberRepository.findAllActiveGuildMemberships("user-1"))
                .thenReturn(List.of(m1, m2));

            List<GuildMembershipInfo> result = facadeService.getUserGuildMemberships("user-1");

            assertThat(result).hasSize(2);

            assertThat(result.get(0).guildId()).isEqualTo(1L);
            assertThat(result.get(0).guildName()).isEqualTo("길드1");
            assertThat(result.get(0).isMaster()).isTrue();
            assertThat(result.get(0).isSubMaster()).isFalse();

            assertThat(result.get(1).guildId()).isEqualTo(2L);
            assertThat(result.get(1).guildName()).isEqualTo("길드2");
            assertThat(result.get(1).isMaster()).isFalse();
        }

        @Test
        @DisplayName("멤버십이 없으면 빈 목록 반환")
        void shouldReturnEmptyListWhenNoMemberships() {
            when(guildMemberRepository.findAllActiveGuildMemberships("user-1"))
                .thenReturn(List.of());

            assertThat(facadeService.getUserGuildMemberships("user-1")).isEmpty();
        }
    }

    @Nested
    @DisplayName("countActiveMembersByGuildIds")
    class CountActiveMembersByGuildIdsTest {

        @Test
        @DisplayName("길드별 멤버 수 맵 반환")
        void shouldReturnMemberCountMap() {
            List<Long> guildIds = List.of(1L, 2L);
            when(guildMemberRepository.countActiveMembersByGuildIds(guildIds))
                .thenReturn(Arrays.<Object[]>asList(new Object[]{1L, 5L}, new Object[]{2L, 3L}));

            Map<Long, Integer> result = facadeService.countActiveMembersByGuildIds(guildIds);

            assertThat(result).hasSize(2);
            assertThat(result.get(1L)).isEqualTo(5);
            assertThat(result.get(2L)).isEqualTo(3);
        }

        @Test
        @DisplayName("빈 목록이면 빈 맵 반환")
        void shouldReturnEmptyMapForEmptyInput() {
            assertThat(facadeService.countActiveMembersByGuildIds(List.of())).isEmpty();
            assertThat(facadeService.countActiveMembersByGuildIds(null)).isEmpty();
            verify(guildMemberRepository, never()).countActiveMembersByGuildIds(any());
        }
    }

    // ========== 경험치/랭킹 조회 ==========

    @Nested
    @DisplayName("getTopExpGuildsByPeriod")
    class GetTopExpGuildsByPeriodTest {

        @Test
        @DisplayName("기간별 상위 경험치 길드 목록 반환")
        void shouldReturnTopExpGuilds() {
            LocalDateTime start = LocalDateTime.of(2025, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2025, 1, 2, 0, 0);
            Pageable pageable = PageRequest.of(0, 3);

            List<Object[]> expected = Arrays.<Object[]>asList(
                new Object[]{1L, 1000L},
                new Object[]{2L, 800L}
            );
            when(guildExpHistoryRepository.findTopExpGuildsByPeriod(start, end, pageable))
                .thenReturn(expected);

            List<Object[]> result = facadeService.getTopExpGuildsByPeriod(start, end, pageable);

            assertThat(result).hasSize(2);
            assertThat(result.get(0)[0]).isEqualTo(1L);
            assertThat(result.get(0)[1]).isEqualTo(1000L);
        }
    }

    @Nested
    @DisplayName("sumGuildExpByPeriod")
    class SumGuildExpByPeriodTest {

        @Test
        @DisplayName("기간별 길드 경험치 합계 반환")
        void shouldReturnExpSum() {
            LocalDateTime start = LocalDateTime.of(2025, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2025, 1, 2, 0, 0);

            when(guildExpHistoryRepository.sumExpByGuildIdAndPeriod(1L, start, end))
                .thenReturn(500L);

            assertThat(facadeService.sumGuildExpByPeriod(1L, start, end)).isEqualTo(500L);
        }
    }

    @Nested
    @DisplayName("countGuildsWithMoreExp")
    class CountGuildsWithMoreExpTest {

        @Test
        @DisplayName("더 많은 경험치를 가진 길드 수 반환")
        void shouldReturnCount() {
            LocalDateTime start = LocalDateTime.of(2025, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2025, 1, 2, 0, 0);

            when(guildExpHistoryRepository.countGuildsWithMoreExpByPeriod(start, end, 500L))
                .thenReturn(3L);

            assertThat(facadeService.countGuildsWithMoreExp(start, end, 500L)).isEqualTo(3L);
        }
    }

    // ========== 게시글 관련 ==========

    @Nested
    @DisplayName("getGuildMasterIdByPostId")
    class GetGuildMasterIdByPostIdTest {

        @Test
        @DisplayName("게시글로 길드 마스터 ID 반환")
        void shouldReturnMasterIdByPostId() {
            Guild guild = createGuild(1L, "길드", "master-123");
            GuildPost post = createPost(10L, guild);

            when(guildPostRepository.findByIdAndIsDeletedFalse(10L))
                .thenReturn(Optional.of(post));

            assertThat(facadeService.getGuildMasterIdByPostId(10L)).isEqualTo("master-123");
        }

        @Test
        @DisplayName("게시글이 없으면 null 반환")
        void shouldReturnNullWhenPostNotFound() {
            when(guildPostRepository.findByIdAndIsDeletedFalse(999L))
                .thenReturn(Optional.empty());

            assertThat(facadeService.getGuildMasterIdByPostId(999L)).isNull();
        }
    }

    @Nested
    @DisplayName("getGuildInfoByPostId")
    class GetGuildInfoByPostIdTest {

        @Test
        @DisplayName("게시글로 길드 정보 반환 (guildId + masterId)")
        void shouldReturnGuildInfoByPostId() {
            Guild guild = createGuild(1L, "길드", "master-123");
            GuildPost post = createPost(10L, guild);

            when(guildPostRepository.findByIdAndIsDeletedFalse(10L))
                .thenReturn(Optional.of(post));

            GuildPostInfo result = facadeService.getGuildInfoByPostId(10L);

            assertThat(result).isNotNull();
            assertThat(result.guildId()).isEqualTo(1L);
            assertThat(result.guildMasterId()).isEqualTo("master-123");
        }

        @Test
        @DisplayName("게시글이 없으면 null 반환")
        void shouldReturnNullWhenPostNotFound() {
            when(guildPostRepository.findByIdAndIsDeletedFalse(999L))
                .thenReturn(Optional.empty());

            assertThat(facadeService.getGuildInfoByPostId(999L)).isNull();
        }
    }
}
