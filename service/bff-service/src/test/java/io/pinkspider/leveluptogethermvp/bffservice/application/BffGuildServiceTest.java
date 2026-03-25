package io.pinkspider.leveluptogethermvp.bffservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.bffservice.api.dto.GuildDetailDataResponse;
import io.pinkspider.leveluptogethermvp.bffservice.api.dto.GuildListDataResponse;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildPostService;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildQueryService;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildMemberResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildPostListResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberRole;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberStatus;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildPostType;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildVisibility;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.ActivityFeedResponse;
import io.pinkspider.leveluptogethermvp.feedservice.application.FeedQueryService;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.ActivityType;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.FeedVisibility;
import java.time.LocalDateTime;
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
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class BffGuildServiceTest {

    @Mock
    private GuildQueryService guildQueryService;

    @Mock
    private GuildPostService guildPostService;

    @Mock
    private FeedQueryService feedQueryService;

    @InjectMocks
    private BffGuildService bffGuildService;

    private String testUserId;
    private GuildResponse testGuildResponse;
    private GuildMemberResponse testMemberResponse;
    private GuildPostListResponse testPostResponse;
    private ActivityFeedResponse testFeedResponse;

    @BeforeEach
    void setUp() {
        testUserId = "test-user-id";

        testGuildResponse = GuildResponse.builder()
            .id(1L)
            .name("테스트 길드")
            .description("테스트 길드 설명")
            .visibility(GuildVisibility.PUBLIC)
            .masterId(testUserId)
            .maxMembers(50)
            .currentMemberCount(10)
            .currentLevel(1)
            .currentExp(100)
            .totalExp(100)
            .categoryId(1L)
            .categoryName("자기계발")
            .categoryIcon("📚")
            .createdAt(LocalDateTime.now())
            .build();

        testMemberResponse = GuildMemberResponse.builder()
            .id(1L)
            .guildId(1L)
            .userId(testUserId)
            .role(GuildMemberRole.MASTER)
            .status(GuildMemberStatus.ACTIVE)
            .joinedAt(LocalDateTime.now())
            .build();

        testPostResponse = GuildPostListResponse.builder()
            .id(1L)
            .authorId(testUserId)
            .authorNickname("테스터")
            .title("테스트 게시글")
            .postType(GuildPostType.NORMAL)
            .isPinned(false)
            .viewCount(10)
            .commentCount(5)
            .createdAt(LocalDateTime.now())
            .build();

        testFeedResponse = ActivityFeedResponse.builder()
            .id(1L)
            .userId(testUserId)
            .userNickname("테스터")
            .activityType(ActivityType.MISSION_COMPLETED)
            .activityTypeDisplayName("미션 완료")
            .category("MISSION")
            .title("미션 완료!")
            .description("테스트 미션을 완료했습니다.")
            .visibility(FeedVisibility.PUBLIC)
            .likeCount(0)
            .commentCount(0)
            .likedByMe(false)
            .createdAt(LocalDateTime.now())
            .build();
    }

    @Nested
    @DisplayName("길드 상세 데이터 조회 테스트")
    class GetGuildDetailTest {

        @Test
        @DisplayName("길드 상세 데이터를 조회한다")
        void getGuildDetail_success() {
            // given
            Page<GuildPostListResponse> postPage = new PageImpl<>(
                List.of(testPostResponse), PageRequest.of(0, 20), 1
            );

            when(guildQueryService.getGuild(1L, testUserId)).thenReturn(testGuildResponse);
            when(guildQueryService.getGuildMembers(1L, testUserId)).thenReturn(List.of(testMemberResponse));
            when(guildPostService.getPosts(anyLong(), anyString(), any(), any())).thenReturn(postPage);

            // when
            GuildDetailDataResponse response = bffGuildService.getGuildDetail(1L, testUserId, 0, 20);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getGuild()).isNotNull();
            assertThat(response.getGuild().getId()).isEqualTo(1L);
            assertThat(response.getMembers()).hasSize(1);
            assertThat(response.getPosts().getContent()).hasSize(1);
            assertThat(response.isMember()).isTrue();
            assertThat(response.getMemberRole()).isEqualTo("MASTER");
        }

        @Test
        @DisplayName("멤버가 아닌 경우 isMember가 false")
        void getGuildDetail_notMember() {
            // given
            String otherUserId = "other-user-id";
            Page<GuildPostListResponse> postPage = new PageImpl<>(
                List.of(testPostResponse), PageRequest.of(0, 20), 1
            );

            when(guildQueryService.getGuild(1L, otherUserId)).thenReturn(testGuildResponse);
            when(guildQueryService.getGuildMembers(1L, otherUserId)).thenReturn(List.of(testMemberResponse));
            when(guildPostService.getPosts(anyLong(), anyString(), any(), any())).thenReturn(postPage);

            // when
            GuildDetailDataResponse response = bffGuildService.getGuildDetail(1L, otherUserId, 0, 20);

            // then
            assertThat(response).isNotNull();
            assertThat(response.isMember()).isFalse();
            assertThat(response.getMemberRole()).isNull();
        }

        @Test
        @DisplayName("길드 조회 실패 시 guild가 null")
        void getGuildDetail_guildFetchFailed() {
            // given
            when(guildQueryService.getGuild(1L, testUserId)).thenThrow(new RuntimeException("조회 실패"));
            when(guildQueryService.getGuildMembers(1L, testUserId)).thenReturn(List.of(testMemberResponse));
            when(guildPostService.getPosts(anyLong(), anyString(), any(), any())).thenReturn(Page.empty());

            // when
            GuildDetailDataResponse response = bffGuildService.getGuildDetail(1L, testUserId, 0, 20);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getGuild()).isNull();
        }
    }

    @Nested
    @DisplayName("길드 목록 데이터 조회 테스트")
    class GetGuildListTest {

        @Test
        @DisplayName("길드에 가입된 사용자의 목록 데이터를 조회한다")
        void getGuildList_withGuild_success() {
            // given
            Page<GuildResponse> guildPage = new PageImpl<>(
                List.of(testGuildResponse), PageRequest.of(0, 10), 1
            );
            Page<ActivityFeedResponse> feedPage = new PageImpl<>(
                List.of(testFeedResponse), PageRequest.of(0, 10), 1
            );
            GuildPostListResponse noticePost = GuildPostListResponse.builder()
                .id(2L)
                .authorId(testUserId)
                .authorNickname("테스터")
                .title("공지사항")
                .postType(GuildPostType.NOTICE)
                .isPinned(true)
                .createdAt(LocalDateTime.now())
                .build();

            when(guildQueryService.getMyGuilds(testUserId)).thenReturn(List.of(testGuildResponse));
            when(guildQueryService.getPublicGuilds(any(), any())).thenReturn(guildPage);
            when(guildPostService.getNotices(1L, testUserId, null)).thenReturn(List.of(noticePost));
            when(feedQueryService.getGuildFeeds(anyLong(), anyString(), anyInt(), anyInt())).thenReturn(feedPage);

            // when
            GuildListDataResponse response = bffGuildService.getGuildList(testUserId, 10, 10);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getMyGuilds()).hasSize(1);
            assertThat(response.isGuildJoined()).isTrue();
            assertThat(response.getRecommendedGuilds().getContent()).hasSize(1);
            assertThat(response.getGuildNotices()).hasSize(1);
        }

        @Test
        @DisplayName("길드에 가입되지 않은 사용자의 목록 데이터를 조회한다")
        void getGuildList_withoutGuild_success() {
            // given
            Page<GuildResponse> guildPage = new PageImpl<>(
                List.of(testGuildResponse), PageRequest.of(0, 10), 1
            );

            when(guildQueryService.getMyGuilds(testUserId)).thenReturn(Collections.emptyList());
            when(guildQueryService.getPublicGuilds(any(), any())).thenReturn(guildPage);

            // when
            GuildListDataResponse response = bffGuildService.getGuildList(testUserId, 10, 10);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getMyGuilds()).isEmpty();
            assertThat(response.isGuildJoined()).isFalse();
            assertThat(response.getGuildNotices()).isEmpty();
            assertThat(response.getGuildActivityFeeds().getContent()).isEmpty();
        }

        @Test
        @DisplayName("여러 길드에 가입된 경우 모든 길드의 공지를 통합 조회한다")
        void getGuildList_multipleGuilds_mergedNotices() {
            // given
            GuildResponse secondGuild = GuildResponse.builder()
                .id(2L)
                .name("두번째 길드")
                .categoryId(2L)
                .createdAt(LocalDateTime.now())
                .build();

            GuildPostListResponse notice1 = GuildPostListResponse.builder()
                .id(1L)
                .title("첫번째 길드 공지")
                .postType(GuildPostType.NOTICE)
                .createdAt(LocalDateTime.now().minusHours(1))
                .build();

            GuildPostListResponse notice2 = GuildPostListResponse.builder()
                .id(2L)
                .title("두번째 길드 공지")
                .postType(GuildPostType.NOTICE)
                .createdAt(LocalDateTime.now())
                .build();

            Page<GuildResponse> guildPage = new PageImpl<>(Collections.emptyList());
            Page<ActivityFeedResponse> feedPage = new PageImpl<>(Collections.emptyList());

            when(guildQueryService.getMyGuilds(testUserId)).thenReturn(List.of(testGuildResponse, secondGuild));
            when(guildQueryService.getPublicGuilds(any(), any())).thenReturn(guildPage);
            when(guildPostService.getNotices(1L, testUserId, null)).thenReturn(List.of(notice1));
            when(guildPostService.getNotices(2L, testUserId, null)).thenReturn(List.of(notice2));
            when(feedQueryService.getGuildFeeds(anyLong(), anyString(), anyInt(), anyInt())).thenReturn(feedPage);

            // when
            GuildListDataResponse response = bffGuildService.getGuildList(testUserId, 10, 10);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getMyGuilds()).hasSize(2);
            assertThat(response.getGuildNotices()).hasSize(2);
            // 최신순 정렬 확인
            assertThat(response.getGuildNotices().get(0).getTitle()).isEqualTo("두번째 길드 공지");
        }

        @Test
        @DisplayName("내 길드 조회 실패 시 빈 목록 반환")
        void getGuildList_myGuildsFetchFailed() {
            // given
            when(guildQueryService.getMyGuilds(testUserId)).thenThrow(new RuntimeException("조회 실패"));
            when(guildQueryService.getPublicGuilds(any(), any())).thenReturn(Page.empty());

            // when
            GuildListDataResponse response = bffGuildService.getGuildList(testUserId, 10, 10);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getMyGuilds()).isEmpty();
            assertThat(response.isGuildJoined()).isFalse();
        }
    }
}
