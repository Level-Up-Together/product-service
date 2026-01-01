package io.pinkspider.leveluptogethermvp.bffservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.bffservice.api.dto.UnifiedSearchResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildVisibility;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionCategory;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionRepository;
import io.pinkspider.leveluptogethermvp.userservice.feed.domain.entity.ActivityFeed;
import io.pinkspider.leveluptogethermvp.userservice.feed.domain.enums.ActivityType;
import io.pinkspider.leveluptogethermvp.userservice.feed.domain.enums.FeedVisibility;
import io.pinkspider.leveluptogethermvp.userservice.feed.infrastructure.ActivityFeedRepository;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class BffSearchServiceTest {

    @Mock
    private ActivityFeedRepository activityFeedRepository;

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GuildRepository guildRepository;

    @InjectMocks
    private BffSearchService bffSearchService;

    private ActivityFeed testFeed;
    private Mission testMission;
    private Users testUser;
    private Guild testGuild;
    private MissionCategory testCategory;

    @BeforeEach
    void setUp() {
        testCategory = MissionCategory.builder()
            .id(1L)
            .name("자기계발")
            .build();

        testFeed = ActivityFeed.builder()
            .id(1L)
            .userId("test-user-id")
            .userNickname("테스터")
            .activityType(ActivityType.MISSION_COMPLETED)
            .title("미션 완료!")
            .description("테스트 미션을 완료했습니다.")
            .visibility(FeedVisibility.PUBLIC)
            .build();

        testMission = Mission.builder()
            .id(1L)
            .title("테스트 미션")
            .description("테스트 미션 설명")
            .creatorId("test-user-id")
            .status(MissionStatus.OPEN)
            .visibility(MissionVisibility.PUBLIC)
            .type(MissionType.PERSONAL)
            .category(testCategory)
            .build();

        testUser = Users.builder()
            .id("test-user-id")
            .nickname("테스터")
            .email("test@example.com")
            .provider("google")
            .nicknameSet(true)
            .build();

        testGuild = Guild.builder()
            .id(1L)
            .name("테스트 길드")
            .description("테스트 길드 설명")
            .visibility(GuildVisibility.PUBLIC)
            .masterId("test-user-id")
            .categoryId(1L)
            .isActive(true)
            .build();
    }

    @Nested
    @DisplayName("통합 검색 테스트")
    class SearchTest {

        @Test
        @DisplayName("모든 타입에서 검색 결과를 정상적으로 반환한다")
        void search_success() {
            // given
            String keyword = "테스트";
            int limit = 5;

            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(testFeed));
            Page<Mission> missionPage = new PageImpl<>(List.of(testMission));
            Page<Users> userPage = new PageImpl<>(List.of(testUser));
            Page<Guild> guildPage = new PageImpl<>(List.of(testGuild));

            when(activityFeedRepository.searchByKeyword(anyString(), any(Pageable.class)))
                .thenReturn(feedPage);
            when(missionRepository.searchByKeyword(anyString(), any(Pageable.class)))
                .thenReturn(missionPage);
            when(userRepository.searchByNickname(anyString(), any(Pageable.class)))
                .thenReturn(userPage);
            when(guildRepository.searchPublicGuilds(anyString(), any(Pageable.class)))
                .thenReturn(guildPage);

            // when
            UnifiedSearchResponse response = bffSearchService.search(keyword, limit);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getFeeds()).hasSize(1);
            assertThat(response.getMissions()).hasSize(1);
            assertThat(response.getUsers()).hasSize(1);
            assertThat(response.getGuilds()).hasSize(1);
            assertThat(response.getTotalCount()).isEqualTo(4);
        }

        @Test
        @DisplayName("키워드가 2자 미만이면 빈 결과를 반환한다")
        void search_shortKeyword_returnsEmpty() {
            // given
            String keyword = "테";
            int limit = 5;

            // when
            UnifiedSearchResponse response = bffSearchService.search(keyword, limit);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getFeeds()).isEmpty();
            assertThat(response.getMissions()).isEmpty();
            assertThat(response.getUsers()).isEmpty();
            assertThat(response.getGuilds()).isEmpty();
            assertThat(response.getTotalCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("키워드가 null이면 빈 결과를 반환한다")
        void search_nullKeyword_returnsEmpty() {
            // given
            String keyword = null;
            int limit = 5;

            // when
            UnifiedSearchResponse response = bffSearchService.search(keyword, limit);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getTotalCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("검색 결과가 없으면 빈 목록을 반환한다")
        void search_noResults_returnsEmpty() {
            // given
            String keyword = "존재하지않는키워드";
            int limit = 5;

            Page<ActivityFeed> emptyFeedPage = new PageImpl<>(Collections.emptyList());
            Page<Mission> emptyMissionPage = new PageImpl<>(Collections.emptyList());
            Page<Users> emptyUserPage = new PageImpl<>(Collections.emptyList());
            Page<Guild> emptyGuildPage = new PageImpl<>(Collections.emptyList());

            when(activityFeedRepository.searchByKeyword(anyString(), any(Pageable.class)))
                .thenReturn(emptyFeedPage);
            when(missionRepository.searchByKeyword(anyString(), any(Pageable.class)))
                .thenReturn(emptyMissionPage);
            when(userRepository.searchByNickname(anyString(), any(Pageable.class)))
                .thenReturn(emptyUserPage);
            when(guildRepository.searchPublicGuilds(anyString(), any(Pageable.class)))
                .thenReturn(emptyGuildPage);

            // when
            UnifiedSearchResponse response = bffSearchService.search(keyword, limit);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getFeeds()).isEmpty();
            assertThat(response.getMissions()).isEmpty();
            assertThat(response.getUsers()).isEmpty();
            assertThat(response.getGuilds()).isEmpty();
            assertThat(response.getTotalCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("피드 검색 실패 시 빈 피드 목록을 반환한다")
        void search_feedSearchFailed_returnsEmptyFeeds() {
            // given
            String keyword = "테스트";
            int limit = 5;

            Page<Mission> missionPage = new PageImpl<>(List.of(testMission));
            Page<Users> userPage = new PageImpl<>(List.of(testUser));
            Page<Guild> guildPage = new PageImpl<>(List.of(testGuild));

            when(activityFeedRepository.searchByKeyword(anyString(), any(Pageable.class)))
                .thenThrow(new RuntimeException("피드 검색 실패"));
            when(missionRepository.searchByKeyword(anyString(), any(Pageable.class)))
                .thenReturn(missionPage);
            when(userRepository.searchByNickname(anyString(), any(Pageable.class)))
                .thenReturn(userPage);
            when(guildRepository.searchPublicGuilds(anyString(), any(Pageable.class)))
                .thenReturn(guildPage);

            // when
            UnifiedSearchResponse response = bffSearchService.search(keyword, limit);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getFeeds()).isEmpty();
            assertThat(response.getMissions()).hasSize(1);
            assertThat(response.getUsers()).hasSize(1);
            assertThat(response.getGuilds()).hasSize(1);
            assertThat(response.getTotalCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("미션 검색 실패 시 빈 미션 목록을 반환한다")
        void search_missionSearchFailed_returnsEmptyMissions() {
            // given
            String keyword = "테스트";
            int limit = 5;

            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(testFeed));
            Page<Users> userPage = new PageImpl<>(List.of(testUser));
            Page<Guild> guildPage = new PageImpl<>(List.of(testGuild));

            when(activityFeedRepository.searchByKeyword(anyString(), any(Pageable.class)))
                .thenReturn(feedPage);
            when(missionRepository.searchByKeyword(anyString(), any(Pageable.class)))
                .thenThrow(new RuntimeException("미션 검색 실패"));
            when(userRepository.searchByNickname(anyString(), any(Pageable.class)))
                .thenReturn(userPage);
            when(guildRepository.searchPublicGuilds(anyString(), any(Pageable.class)))
                .thenReturn(guildPage);

            // when
            UnifiedSearchResponse response = bffSearchService.search(keyword, limit);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getFeeds()).hasSize(1);
            assertThat(response.getMissions()).isEmpty();
            assertThat(response.getUsers()).hasSize(1);
            assertThat(response.getGuilds()).hasSize(1);
            assertThat(response.getTotalCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("사용자 검색 실패 시 빈 사용자 목록을 반환한다")
        void search_userSearchFailed_returnsEmptyUsers() {
            // given
            String keyword = "테스트";
            int limit = 5;

            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(testFeed));
            Page<Mission> missionPage = new PageImpl<>(List.of(testMission));
            Page<Guild> guildPage = new PageImpl<>(List.of(testGuild));

            when(activityFeedRepository.searchByKeyword(anyString(), any(Pageable.class)))
                .thenReturn(feedPage);
            when(missionRepository.searchByKeyword(anyString(), any(Pageable.class)))
                .thenReturn(missionPage);
            when(userRepository.searchByNickname(anyString(), any(Pageable.class)))
                .thenThrow(new RuntimeException("사용자 검색 실패"));
            when(guildRepository.searchPublicGuilds(anyString(), any(Pageable.class)))
                .thenReturn(guildPage);

            // when
            UnifiedSearchResponse response = bffSearchService.search(keyword, limit);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getFeeds()).hasSize(1);
            assertThat(response.getMissions()).hasSize(1);
            assertThat(response.getUsers()).isEmpty();
            assertThat(response.getGuilds()).hasSize(1);
            assertThat(response.getTotalCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("길드 검색 실패 시 빈 길드 목록을 반환한다")
        void search_guildSearchFailed_returnsEmptyGuilds() {
            // given
            String keyword = "테스트";
            int limit = 5;

            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(testFeed));
            Page<Mission> missionPage = new PageImpl<>(List.of(testMission));
            Page<Users> userPage = new PageImpl<>(List.of(testUser));

            when(activityFeedRepository.searchByKeyword(anyString(), any(Pageable.class)))
                .thenReturn(feedPage);
            when(missionRepository.searchByKeyword(anyString(), any(Pageable.class)))
                .thenReturn(missionPage);
            when(userRepository.searchByNickname(anyString(), any(Pageable.class)))
                .thenReturn(userPage);
            when(guildRepository.searchPublicGuilds(anyString(), any(Pageable.class)))
                .thenThrow(new RuntimeException("길드 검색 실패"));

            // when
            UnifiedSearchResponse response = bffSearchService.search(keyword, limit);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getFeeds()).hasSize(1);
            assertThat(response.getMissions()).hasSize(1);
            assertThat(response.getUsers()).hasSize(1);
            assertThat(response.getGuilds()).isEmpty();
            assertThat(response.getTotalCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("검색 결과 아이템의 필드가 올바르게 매핑된다")
        void search_itemFieldsCorrectlyMapped() {
            // given
            String keyword = "테스트";
            int limit = 5;

            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(testFeed));
            Page<Mission> missionPage = new PageImpl<>(List.of(testMission));
            Page<Users> userPage = new PageImpl<>(List.of(testUser));
            Page<Guild> guildPage = new PageImpl<>(List.of(testGuild));

            when(activityFeedRepository.searchByKeyword(anyString(), any(Pageable.class)))
                .thenReturn(feedPage);
            when(missionRepository.searchByKeyword(anyString(), any(Pageable.class)))
                .thenReturn(missionPage);
            when(userRepository.searchByNickname(anyString(), any(Pageable.class)))
                .thenReturn(userPage);
            when(guildRepository.searchPublicGuilds(anyString(), any(Pageable.class)))
                .thenReturn(guildPage);

            // when
            UnifiedSearchResponse response = bffSearchService.search(keyword, limit);

            // then
            // Feed 필드 검증
            assertThat(response.getFeeds().get(0).getId()).isEqualTo(1L);
            assertThat(response.getFeeds().get(0).getTitle()).isEqualTo("미션 완료!");
            assertThat(response.getFeeds().get(0).getUserNickname()).isEqualTo("테스터");

            // Mission 필드 검증
            assertThat(response.getMissions().get(0).getId()).isEqualTo(1L);
            assertThat(response.getMissions().get(0).getTitle()).isEqualTo("테스트 미션");
            assertThat(response.getMissions().get(0).getCategoryName()).isEqualTo("자기계발");

            // User 필드 검증
            assertThat(response.getUsers().get(0).getId()).isEqualTo("test-user-id");
            assertThat(response.getUsers().get(0).getNickname()).isEqualTo("테스터");

            // Guild 필드 검증
            assertThat(response.getGuilds().get(0).getId()).isEqualTo("1");
            assertThat(response.getGuilds().get(0).getName()).isEqualTo("테스트 길드");
        }
    }
}
