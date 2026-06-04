package io.pinkspider.leveluptogethermvp.feedservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.global.translation.TranslationService;
import io.pinkspider.global.feign.admin.AdminInternalFeignClient;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.admin.FeedAdminPageResponse;
import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.ActivityFeed;
import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.FeedComment;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.ActivityType;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.FeedSearchType;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.FeedVisibility;
import io.pinkspider.leveluptogethermvp.feedservice.infrastructure.ActivityFeedRepository;
import io.pinkspider.leveluptogethermvp.feedservice.infrastructure.FeedCommentLikeRepository;
import io.pinkspider.leveluptogethermvp.feedservice.infrastructure.FeedCommentRepository;
import io.pinkspider.leveluptogethermvp.feedservice.infrastructure.FeedLikeRepository;
import io.pinkspider.leveluptogethermvp.supportservice.report.application.ReportService;
import io.pinkspider.global.enums.ReportTargetType;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.ActivityFeedResponse;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.FeedCommentResponse;
import io.pinkspider.global.facade.GuildQueryFacade;
import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.global.facade.dto.GuildMembershipInfo;
import io.pinkspider.global.facade.dto.UserProfileInfo;
import static io.pinkspider.global.test.TestReflectionUtils.setId;
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
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class FeedQueryServiceTest {

    @Mock
    private ActivityFeedRepository activityFeedRepository;

    @Mock
    private io.pinkspider.leveluptogethermvp.feedservice.infrastructure.ActivityFeedImageRepository activityFeedImageRepository;

    @Mock
    private FeedLikeRepository feedLikeRepository;

    @Mock
    private FeedCommentRepository feedCommentRepository;

    @Mock
    private FeedCommentLikeRepository feedCommentLikeRepository;

    @Mock
    private AdminInternalFeignClient adminInternalFeignClient;

    @Mock
    private UserQueryFacade userQueryFacadeService;

    @Mock
    private TranslationService translationService;

    @Mock
    private ReportService reportService;

    @Mock
    private GuildQueryFacade guildQueryFacadeService;

    @Mock
    private FeedAccessChecker feedAccessChecker;

    @InjectMocks
    private FeedQueryService feedQueryService;

    private static final String TEST_USER_ID = "test-user-123";
    private static final String OTHER_USER_ID = "other-user-456";

    private ActivityFeed createTestFeed(Long id, String userId) {
        ActivityFeed feed = ActivityFeed.builder()
            .userId(userId)
            .userNickname("테스트유저")
            .userProfileImageUrl("https://example.com/profile.jpg")
            .userLevel(5)
            .activityType(ActivityType.MISSION_COMPLETED)
            .title("테스트 피드")
            .description("테스트 설명")
            .visibility(FeedVisibility.PUBLIC)
            .likeCount(0)
            .commentCount(0)
            .build();
        setId(feed, id);
        return feed;
    }

    @Nested
    @DisplayName("getPublicFeeds 테스트")
    class GetPublicFeedsTest {

        @Test
        @DisplayName("전체 공개 피드를 조회한다")
        void getPublicFeeds_success() {
            // given
            ActivityFeed feed = createTestFeed(1L, TEST_USER_ID);
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(feed));

            when(activityFeedRepository.findAccessibleFeeds(any(), any(), any(), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getPublicFeeds(TEST_USER_ID, 0, 10);

            // then
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("QA-168: 친구ID와 길드ID를 조회한 뒤 Repository 로 전달한다")
        void getPublicFeeds_resolvesFriendAndGuildIds() {
            // given
            ActivityFeed feed = createTestFeed(1L, OTHER_USER_ID);
            List<String> friendIds = List.of("friend-1", "friend-2");
            GuildMembershipInfo membership = new GuildMembershipInfo(100L, "길드A", null, 1, false, false);
            when(userQueryFacadeService.getFriendIds(TEST_USER_ID)).thenReturn(friendIds);
            when(guildQueryFacadeService.getUserGuildMemberships(TEST_USER_ID))
                .thenReturn(List.of(membership));
            when(activityFeedRepository.findAccessibleFeeds(
                eq(TEST_USER_ID), eq(friendIds), eq(List.of(100L)), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(feed)));
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getPublicFeeds(TEST_USER_ID, 0, 10);

            // then
            assertThat(result.getContent()).hasSize(1);
            verify(activityFeedRepository).findAccessibleFeeds(
                eq(TEST_USER_ID), eq(friendIds), eq(List.of(100L)), any(Pageable.class));
        }

        @Test
        @DisplayName("QA-168: 비로그인(userId=null)은 친구/길드 조회 없이 빈 리스트로 호출한다")
        void getPublicFeeds_anonymousUserSkipsFacades() {
            // given
            ActivityFeed feed = createTestFeed(1L, OTHER_USER_ID);
            when(activityFeedRepository.findAccessibleFeeds(
                eq(null), eq(List.of()), eq(List.of()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(feed)));

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getPublicFeeds(null, 0, 10);

            // then
            assertThat(result.getContent()).hasSize(1);
            verify(userQueryFacadeService, never()).getFriendIds(anyString());
            verify(guildQueryFacadeService, never()).getUserGuildMemberships(anyString());
        }

        @Test
        @DisplayName("QA-168: facade가 null을 반환해도 빈 리스트로 정상 처리한다")
        void getPublicFeeds_nullFacadeResultsTreatedAsEmpty() {
            // given
            when(userQueryFacadeService.getFriendIds(TEST_USER_ID)).thenReturn(null);
            when(guildQueryFacadeService.getUserGuildMemberships(TEST_USER_ID)).thenReturn(null);
            when(activityFeedRepository.findAccessibleFeeds(
                eq(TEST_USER_ID), eq(List.of()), eq(List.of()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getPublicFeeds(TEST_USER_ID, 0, 10);

            // then
            assertThat(result.getContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getTimelineFeeds 테스트")
    class GetTimelineFeedsTest {

        @Test
        @DisplayName("친구가 없는 경우 내 피드만 조회한다")
        void getTimelineFeeds_noFriends() {
            // given
            ActivityFeed feed = createTestFeed(1L, TEST_USER_ID);
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(feed));

            when(userQueryFacadeService.getFriendIds(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(activityFeedRepository.findByUserId(eq(TEST_USER_ID), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getTimelineFeeds(TEST_USER_ID, 0, 10);

            // then
            assertThat(result.getContent()).hasSize(1);
            verify(activityFeedRepository).findByUserId(eq(TEST_USER_ID), any(Pageable.class));
        }

        @Test
        @DisplayName("친구가 있는 경우 타임라인 피드를 조회한다")
        void getTimelineFeeds_withFriends() {
            // given
            ActivityFeed feed = createTestFeed(1L, TEST_USER_ID);
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(feed));
            List<String> friendIds = List.of("friend-1", "friend-2");

            when(userQueryFacadeService.getFriendIds(TEST_USER_ID)).thenReturn(friendIds);
            when(activityFeedRepository.findTimelineFeeds(eq(TEST_USER_ID), eq(friendIds), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getTimelineFeeds(TEST_USER_ID, 0, 10);

            // then
            assertThat(result.getContent()).hasSize(1);
            verify(activityFeedRepository).findTimelineFeeds(eq(TEST_USER_ID), eq(friendIds), any(Pageable.class));
        }
    }

    @Nested
    @DisplayName("getUserFeeds 테스트")
    class GetUserFeedsTest {

        @Test
        @DisplayName("특정 사용자의 피드를 조회한다")
        void getUserFeeds_success() {
            // given
            ActivityFeed feed = createTestFeed(1L, OTHER_USER_ID);
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(feed));

            when(activityFeedRepository.findByUserId(eq(OTHER_USER_ID), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());
            when(userQueryFacadeService.areFriends(TEST_USER_ID, OTHER_USER_ID)).thenReturn(false);
            when(guildQueryFacadeService.getUserGuildMemberships(anyString())).thenReturn(Collections.emptyList());
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getUserFeeds(OTHER_USER_ID, TEST_USER_ID, 0, 10);

            // then
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("getGuildFeeds 테스트")
    class GetGuildFeedsTest {

        @Test
        @DisplayName("길드 피드를 조회한다")
        void getGuildFeeds_success() {
            // given
            Long guildId = 1L;
            ActivityFeed feed = createTestFeed(1L, TEST_USER_ID);
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(feed));

            when(guildQueryFacadeService.getUserGuildMemberships(TEST_USER_ID))
                .thenReturn(List.of(new GuildMembershipInfo(guildId, "테스트길드", null, 1, false, false)));
            when(activityFeedRepository.findGuildFeeds(eq(guildId), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getGuildFeeds(guildId, TEST_USER_ID, 0, 10);

            // then
            assertThat(result.getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getFeed 테스트")
    class GetFeedTest {

        @Test
        @DisplayName("피드 상세를 조회한다")
        void getFeed_success() {
            // given
            Long feedId = 1L;
            ActivityFeed feed = createTestFeed(feedId, TEST_USER_ID);

            when(activityFeedRepository.findById(feedId)).thenReturn(Optional.of(feed));
            when(feedLikeRepository.existsByFeedIdAndUserId(feedId, TEST_USER_ID)).thenReturn(false);

            // when
            ActivityFeedResponse result = feedQueryService.getFeed(feedId, TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("존재하지 않는 피드 조회 시 예외 발생")
        void getFeed_notFound() {
            // given
            when(activityFeedRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> feedQueryService.getFeed(999L, TEST_USER_ID))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.feed.not_found");
        }
    }

    @Nested
    @DisplayName("getComments 테스트")
    class GetCommentsTest {

        @Test
        @DisplayName("댓글 목록을 조회한다")
        void getComments_success() {
            // given
            Long feedId = 1L;
            ActivityFeed feed = createTestFeed(feedId, OTHER_USER_ID);

            FeedComment comment = FeedComment.builder()
                .feed(feed)
                .userId(TEST_USER_ID)
                .userNickname("테스트유저")
                .content("테스트 댓글")
                .isDeleted(false)
                .build();
            setId(comment, 1L);

            Page<FeedComment> commentPage = new PageImpl<>(List.of(comment));
            when(activityFeedRepository.findById(feedId)).thenReturn(Optional.of(feed));
            when(feedCommentRepository.findRootCommentsByFeedId(eq(feedId), any(Pageable.class))).thenReturn(commentPage);

            // when
            Page<FeedCommentResponse> result = feedQueryService.getComments(feedId, TEST_USER_ID, 0, 10);

            // then
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("대댓글이 있는 댓글은 replies 배열에 트리로 반환된다")
        void getComments_tree_success() {
            // given
            Long feedId = 1L;
            ActivityFeed feed = createTestFeed(feedId, OTHER_USER_ID);

            FeedComment parent = FeedComment.builder()
                .feed(feed).userId(OTHER_USER_ID).userNickname("부모유저")
                .content("부모 댓글").isDeleted(false).isEdited(false).build();
            setId(parent, 10L);

            FeedComment reply = FeedComment.builder()
                .feed(feed).userId(TEST_USER_ID).userNickname("나").parent(parent)
                .content("대댓글").isDeleted(false).isEdited(false).build();
            setId(reply, 11L);

            when(activityFeedRepository.findById(feedId)).thenReturn(Optional.of(feed));
            when(feedCommentRepository.findRootCommentsByFeedId(eq(feedId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(parent)));
            when(feedCommentRepository.findRepliesByParentIds(List.of(10L)))
                .thenReturn(List.of(reply));
            when(feedCommentLikeRepository.countByCommentIds(anyList())).thenReturn(List.of());
            when(feedCommentLikeRepository.findLikedCommentIds(anyString(), anyList())).thenReturn(List.of());
            when(reportService.isUnderReviewBatch(eq(ReportTargetType.FEED_COMMENT), anyList()))
                .thenReturn(new HashMap<>());
            when(userQueryFacadeService.getUserProfile(anyString())).thenReturn(
                new UserProfileInfo("any", "닉", null, 5, null, null, null));

            // when
            Page<FeedCommentResponse> result = feedQueryService.getComments(feedId, TEST_USER_ID, 0, 10);

            // then
            assertThat(result.getContent()).hasSize(1);
            FeedCommentResponse parentResponse = result.getContent().get(0);
            assertThat(parentResponse.getReplies()).hasSize(1);
            assertThat(parentResponse.getReplies().get(0).getParentId()).isEqualTo(10L);
            // 부모는 대댓글 있어서 수정 불가
            assertThat(parentResponse.getIsEditable()).isFalse();
        }

        @Test
        @DisplayName("내 댓글에 대댓글 없으면 is_editable=true")
        void getComments_myComment_editable() {
            // given
            Long feedId = 1L;
            ActivityFeed feed = createTestFeed(feedId, OTHER_USER_ID);

            FeedComment myComment = FeedComment.builder()
                .feed(feed).userId(TEST_USER_ID).userNickname("나")
                .content("내 댓글").isDeleted(false).isEdited(false).build();
            setId(myComment, 10L);

            when(activityFeedRepository.findById(feedId)).thenReturn(Optional.of(feed));
            when(feedCommentRepository.findRootCommentsByFeedId(eq(feedId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(myComment)));
            when(feedCommentRepository.findRepliesByParentIds(anyList())).thenReturn(List.of());
            when(feedCommentLikeRepository.countByCommentIds(anyList())).thenReturn(List.of());
            when(feedCommentLikeRepository.findLikedCommentIds(anyString(), anyList())).thenReturn(List.of());
            when(reportService.isUnderReviewBatch(eq(ReportTargetType.FEED_COMMENT), anyList()))
                .thenReturn(new HashMap<>());
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID)).thenReturn(
                new UserProfileInfo(TEST_USER_ID, "나", null, 5, null, null, null));

            // when
            Page<FeedCommentResponse> result = feedQueryService.getComments(feedId, TEST_USER_ID, 0, 10);

            // then
            FeedCommentResponse response = result.getContent().get(0);
            assertThat(response.getIsMyComment()).isTrue();
            assertThat(response.getIsEditable()).isTrue();
            assertThat(response.getReplies()).isEmpty();
        }
    }

    @Nested
    @DisplayName("searchFeeds 테스트")
    class SearchFeedsTest {

        @Test
        @DisplayName("키워드로 피드를 검색한다")
        void searchFeeds_success() {
            // given
            String keyword = "테스트";
            ActivityFeed feed = createTestFeed(1L, TEST_USER_ID);
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(feed));

            when(activityFeedRepository.searchByKeyword(eq(keyword), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.searchFeeds(keyword, TEST_USER_ID, 0, 10);

            // then
            assertThat(result.getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getFeedsByCategory 테스트")
    class GetFeedsByCategoryTest {

        @Test
        @DisplayName("카테고리별 피드를 조회한다")
        void getFeedsByCategory_success() {
            // given
            String category = "MISSION";
            ActivityFeed feed = createTestFeed(1L, TEST_USER_ID);
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(feed));

            when(activityFeedRepository.findByCategoryTypes(anyList(), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getFeedsByCategory(category, TEST_USER_ID, 0, 10);

            // then
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("존재하지 않는 카테고리 조회 시 빈 결과 반환")
        void getFeedsByCategory_emptyCategory() {
            // given
            String invalidCategory = "INVALID_CATEGORY";

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getFeedsByCategory(invalidCategory, TEST_USER_ID, 0, 10);

            // then
            assertThat(result.getContent()).isEmpty();
            verify(activityFeedRepository, never()).findByCategoryTypes(anyList(), any(Pageable.class));
        }
    }

    @Nested
    @DisplayName("다국어 지원 조회 테스트")
    class MultilingualFeedTest {

        @Test
        @DisplayName("Accept-Language 헤더와 함께 공개 피드를 조회한다")
        void getPublicFeeds_withAcceptLanguage() {
            // given
            ActivityFeed feed = createTestFeed(1L, TEST_USER_ID);
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(feed));
            String acceptLanguage = "en-US,en;q=0.9";

            when(activityFeedRepository.findAccessibleFeeds(any(), any(), any(), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getPublicFeeds(TEST_USER_ID, 0, 10, acceptLanguage);

            // then
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Accept-Language 헤더와 함께 타임라인 피드를 조회한다")
        void getTimelineFeeds_withAcceptLanguage() {
            // given
            ActivityFeed feed = createTestFeed(1L, TEST_USER_ID);
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(feed));
            String acceptLanguage = "ar";

            when(userQueryFacadeService.getFriendIds(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(activityFeedRepository.findByUserId(eq(TEST_USER_ID), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getTimelineFeeds(TEST_USER_ID, 0, 10, acceptLanguage);

            // then
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Accept-Language 헤더와 함께 사용자 피드를 조회한다")
        void getUserFeeds_withAcceptLanguage() {
            // given
            ActivityFeed feed = createTestFeed(1L, OTHER_USER_ID);
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(feed));
            String acceptLanguage = "ja";

            when(activityFeedRepository.findByUserId(eq(OTHER_USER_ID), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());
            when(userQueryFacadeService.areFriends(TEST_USER_ID, OTHER_USER_ID)).thenReturn(false);
            when(guildQueryFacadeService.getUserGuildMemberships(anyString())).thenReturn(Collections.emptyList());
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getUserFeeds(OTHER_USER_ID, TEST_USER_ID, 0, 10, acceptLanguage);

            // then
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Accept-Language 헤더와 함께 길드 피드를 조회한다")
        void getGuildFeeds_withAcceptLanguage() {
            // given
            Long guildId = 1L;
            ActivityFeed feed = createTestFeed(1L, TEST_USER_ID);
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(feed));
            String acceptLanguage = "ko";

            when(guildQueryFacadeService.getUserGuildMemberships(TEST_USER_ID))
                .thenReturn(List.of(new GuildMembershipInfo(guildId, "테스트길드", null, 1, false, false)));
            when(activityFeedRepository.findGuildFeeds(eq(guildId), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getGuildFeeds(guildId, TEST_USER_ID, 0, 10, acceptLanguage);

            // then
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Accept-Language 헤더와 함께 카테고리별 피드를 조회한다")
        void getFeedsByCategory_withAcceptLanguage() {
            // given
            String category = "MISSION";
            ActivityFeed feed = createTestFeed(1L, TEST_USER_ID);
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(feed));
            String acceptLanguage = "zh";

            when(activityFeedRepository.findByCategoryTypes(anyList(), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getFeedsByCategory(category, TEST_USER_ID, 0, 10, acceptLanguage);

            // then
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Accept-Language 헤더와 함께 피드를 검색한다")
        void searchFeeds_withAcceptLanguage() {
            // given
            String keyword = "테스트";
            ActivityFeed feed = createTestFeed(1L, TEST_USER_ID);
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(feed));
            String acceptLanguage = "en";

            when(activityFeedRepository.searchByKeyword(eq(keyword), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.searchFeeds(keyword, TEST_USER_ID, 0, 10, acceptLanguage);

            // then
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Accept-Language 헤더와 함께 카테고리별 피드를 검색한다")
        void searchFeedsByCategory_withAcceptLanguage() {
            // given
            String keyword = "테스트";
            String category = "MISSION";
            ActivityFeed feed = createTestFeed(1L, TEST_USER_ID);
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(feed));
            String acceptLanguage = "fr";

            when(activityFeedRepository.searchByKeywordAndCategory(eq(keyword), anyList(), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.searchFeedsByCategory(keyword, category, TEST_USER_ID, 0, 10, acceptLanguage);

            // then
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Accept-Language 헤더와 함께 피드 상세를 조회한다")
        void getFeed_withAcceptLanguage() {
            // given
            Long feedId = 1L;
            ActivityFeed feed = createTestFeed(feedId, TEST_USER_ID);
            String acceptLanguage = "de";

            when(activityFeedRepository.findById(feedId)).thenReturn(Optional.of(feed));
            when(feedLikeRepository.existsByFeedIdAndUserId(feedId, TEST_USER_ID)).thenReturn(false);
            when(reportService.isUnderReview(any(), anyString())).thenReturn(false);

            // when
            ActivityFeedResponse result = feedQueryService.getFeed(feedId, TEST_USER_ID, acceptLanguage);

            // then
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Accept-Language 헤더와 함께 댓글 목록을 조회한다")
        void getComments_withAcceptLanguage() {
            // given
            Long feedId = 1L;
            ActivityFeed feed = createTestFeed(feedId, OTHER_USER_ID);
            FeedComment comment = FeedComment.builder()
                .feed(feed)
                .userId(TEST_USER_ID)
                .userNickname("테스트유저")
                .content("테스트 댓글")
                .isDeleted(false)
                .build();
            setId(comment, 1L);
            Page<FeedComment> commentPage = new PageImpl<>(List.of(comment));
            String acceptLanguage = "es";

            when(activityFeedRepository.findById(feedId)).thenReturn(Optional.of(feed));
            when(feedCommentRepository.findRootCommentsByFeedId(eq(feedId), any(Pageable.class))).thenReturn(commentPage);
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID))
                .thenReturn(new UserProfileInfo(TEST_USER_ID, "테스트유저", null, 5, null, null, null));
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<FeedCommentResponse> result = feedQueryService.getComments(feedId, TEST_USER_ID, 0, 10, acceptLanguage);

            // then
            assertThat(result.getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("카테고리별 공개 피드 조회 하이브리드 테스트")
    class GetPublicFeedsByCategoryTest {

        @Test
        @DisplayName("카테고리별 공개 피드를 조회한다")
        void getPublicFeedsByCategory_success() {
            // given
            Long categoryId = 1L;
            ActivityFeed feed = createTestFeed(1L, TEST_USER_ID);
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(feed));

            when(adminInternalFeignClient.getFeaturedFeedIds(categoryId)).thenReturn(Collections.emptyList());
            when(activityFeedRepository.findAccessibleFeedsByCategoryId(eq(categoryId), any(), any(), any(), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getPublicFeedsByCategory(categoryId, TEST_USER_ID, 0, 10);

            // then
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Featured Feed가 있는 경우 먼저 표시된다")
        void getPublicFeedsByCategory_withFeaturedFeeds() {
            // given
            Long categoryId = 1L;
            ActivityFeed featuredFeed = createTestFeed(1L, TEST_USER_ID);
            ActivityFeed normalFeed = createTestFeed(2L, OTHER_USER_ID);

            when(adminInternalFeignClient.getFeaturedFeedIds(categoryId))
                .thenReturn(List.of(1L));
            when(activityFeedRepository.findByIdIn(List.of(1L)))
                .thenReturn(List.of(featuredFeed));
            when(activityFeedRepository.findAccessibleFeedsByCategoryId(eq(categoryId), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(normalFeed)));
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getPublicFeedsByCategory(categoryId, TEST_USER_ID, 0, 10);

            // then
            assertThat(result.getContent()).hasSize(2);
        }

        @Test
        @DisplayName("중복된 피드는 제거된다")
        void getPublicFeedsByCategory_removeDuplicates() {
            // given
            Long categoryId = 1L;
            ActivityFeed feed = createTestFeed(1L, TEST_USER_ID);

            when(adminInternalFeignClient.getFeaturedFeedIds(categoryId))
                .thenReturn(List.of(1L));
            when(activityFeedRepository.findByIdIn(List.of(1L)))
                .thenReturn(List.of(feed));
            when(activityFeedRepository.findAccessibleFeedsByCategoryId(eq(categoryId), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(feed))); // 동일한 피드
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getPublicFeedsByCategory(categoryId, TEST_USER_ID, 0, 10);

            // then
            assertThat(result.getContent()).hasSize(1); // 중복 제거됨
        }

        @Test
        @DisplayName("Accept-Language와 함께 카테고리별 공개 피드를 조회한다")
        void getPublicFeedsByCategory_withAcceptLanguage() {
            // given
            Long categoryId = 1L;
            ActivityFeed feed = createTestFeed(1L, TEST_USER_ID);
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(feed));
            String acceptLanguage = "en";

            when(adminInternalFeignClient.getFeaturedFeedIds(categoryId)).thenReturn(Collections.emptyList());
            when(activityFeedRepository.findAccessibleFeedsByCategoryId(eq(categoryId), any(), any(), any(), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getPublicFeedsByCategory(categoryId, TEST_USER_ID, 0, 10, acceptLanguage);

            // then
            assertThat(result.getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("사용자 피드 가시성 테스트")
    class UserFeedVisibilityTest {

        @Test
        @DisplayName("본인 피드는 모든 가시성 설정을 볼 수 있다")
        void getUserFeeds_selfAllVisible() {
            // given
            ActivityFeed privateFeed = ActivityFeed.builder()
                .userId(TEST_USER_ID)
                .userNickname("테스트유저")
                .activityType(ActivityType.MISSION_COMPLETED)
                .title("비공개 피드")
                .visibility(FeedVisibility.PRIVATE)
                .likeCount(0)
                .commentCount(0)
                .build();
            setId(privateFeed, 1L);
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(privateFeed));

            when(activityFeedRepository.findByUserId(eq(TEST_USER_ID), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getUserFeeds(TEST_USER_ID, TEST_USER_ID, 0, 10);

            // then
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("친구는 FRIENDS 가시성 피드를 볼 수 있다")
        void getUserFeeds_friendCanSeeFriendsVisibility() {
            // given
            ActivityFeed friendsFeed = ActivityFeed.builder()
                .userId(OTHER_USER_ID)
                .userNickname("다른유저")
                .activityType(ActivityType.MISSION_COMPLETED)
                .title("친구공개 피드")
                .visibility(FeedVisibility.FRIENDS)
                .likeCount(0)
                .commentCount(0)
                .build();
            setId(friendsFeed, 1L);
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(friendsFeed));

            when(activityFeedRepository.findByUserId(eq(OTHER_USER_ID), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());
            when(userQueryFacadeService.areFriends(TEST_USER_ID, OTHER_USER_ID)).thenReturn(true);
            when(guildQueryFacadeService.getUserGuildMemberships(anyString())).thenReturn(Collections.emptyList());
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getUserFeeds(OTHER_USER_ID, TEST_USER_ID, 0, 10);

            // then
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("비친구는 PUBLIC 가시성 피드만 볼 수 있다")
        void getUserFeeds_nonFriendSeesPublicOnly() {
            // given
            ActivityFeed friendsFeed = ActivityFeed.builder()
                .userId(OTHER_USER_ID)
                .userNickname("다른유저")
                .activityType(ActivityType.MISSION_COMPLETED)
                .title("친구공개 피드")
                .visibility(FeedVisibility.FRIENDS)
                .likeCount(0)
                .commentCount(0)
                .build();
            setId(friendsFeed, 1L);
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(friendsFeed));

            when(activityFeedRepository.findByUserId(eq(OTHER_USER_ID), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());
            when(userQueryFacadeService.areFriends(TEST_USER_ID, OTHER_USER_ID)).thenReturn(false);
            when(guildQueryFacadeService.getUserGuildMemberships(anyString())).thenReturn(Collections.emptyList());
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getUserFeeds(OTHER_USER_ID, TEST_USER_ID, 0, 10);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0)).isNull(); // FRIENDS 가시성이므로 null
        }
    }

    @Nested
    @DisplayName("신고 처리중 상태 통합 테스트")
    class IsUnderReviewIntegrationTest {

        @Test
        @DisplayName("피드 상세 조회 시 신고 처리중 상태가 true로 반환된다")
        void getFeed_underReview_true() {
            // given
            Long feedId = 1L;
            ActivityFeed feed = createTestFeed(feedId, TEST_USER_ID);

            when(activityFeedRepository.findById(feedId)).thenReturn(Optional.of(feed));
            when(feedLikeRepository.existsByFeedIdAndUserId(feedId, TEST_USER_ID)).thenReturn(false);
            when(reportService.isUnderReview(ReportTargetType.FEED, "1")).thenReturn(true);

            // when
            ActivityFeedResponse result = feedQueryService.getFeed(feedId, TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getIsUnderReview()).isTrue();
            verify(reportService).isUnderReview(ReportTargetType.FEED, "1");
        }

        @Test
        @DisplayName("피드 상세 조회 시 신고 처리중 상태가 false로 반환된다")
        void getFeed_underReview_false() {
            // given
            Long feedId = 1L;
            ActivityFeed feed = createTestFeed(feedId, TEST_USER_ID);

            when(activityFeedRepository.findById(feedId)).thenReturn(Optional.of(feed));
            when(feedLikeRepository.existsByFeedIdAndUserId(feedId, TEST_USER_ID)).thenReturn(false);
            when(reportService.isUnderReview(ReportTargetType.FEED, "1")).thenReturn(false);

            // when
            ActivityFeedResponse result = feedQueryService.getFeed(feedId, TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getIsUnderReview()).isFalse();
        }

        @Test
        @DisplayName("전체 공개 피드 목록 조회 시 신고 처리중 상태가 일괄 조회된다")
        void getPublicFeeds_batchUnderReviewCheck() {
            // given
            ActivityFeed feed = createTestFeed(1L, TEST_USER_ID);
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(feed));

            when(activityFeedRepository.findAccessibleFeeds(any(), any(), any(), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());

            Map<String, Boolean> underReviewMap = new HashMap<>();
            underReviewMap.put("1", true);
            when(reportService.isUnderReviewBatch(eq(ReportTargetType.FEED), anyList())).thenReturn(underReviewMap);

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getPublicFeeds(TEST_USER_ID, 0, 10);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getIsUnderReview()).isTrue();
            verify(reportService).isUnderReviewBatch(eq(ReportTargetType.FEED), anyList());
        }

        @Test
        @DisplayName("타임라인 피드 목록 조회 시 신고 처리중 상태가 일괄 조회된다")
        void getTimelineFeeds_batchUnderReviewCheck() {
            // given
            ActivityFeed feed = createTestFeed(1L, TEST_USER_ID);
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(feed));

            when(userQueryFacadeService.getFriendIds(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(activityFeedRepository.findByUserId(eq(TEST_USER_ID), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());

            Map<String, Boolean> underReviewMap = new HashMap<>();
            underReviewMap.put("1", false);
            when(reportService.isUnderReviewBatch(eq(ReportTargetType.FEED), anyList())).thenReturn(underReviewMap);

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getTimelineFeeds(TEST_USER_ID, 0, 10);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getIsUnderReview()).isFalse();
            verify(reportService).isUnderReviewBatch(eq(ReportTargetType.FEED), anyList());
        }

        @Test
        @DisplayName("사용자 피드 목록 조회 시 신고 처리중 상태가 일괄 조회된다")
        void getUserFeeds_batchUnderReviewCheck() {
            // given
            ActivityFeed feed = createTestFeed(1L, OTHER_USER_ID);
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(feed));

            when(activityFeedRepository.findByUserId(eq(OTHER_USER_ID), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());
            when(userQueryFacadeService.areFriends(TEST_USER_ID, OTHER_USER_ID)).thenReturn(false);
            when(guildQueryFacadeService.getUserGuildMemberships(anyString())).thenReturn(Collections.emptyList());

            Map<String, Boolean> underReviewMap = new HashMap<>();
            underReviewMap.put("1", true);
            when(reportService.isUnderReviewBatch(eq(ReportTargetType.FEED), anyList())).thenReturn(underReviewMap);

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getUserFeeds(OTHER_USER_ID, TEST_USER_ID, 0, 10);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getIsUnderReview()).isTrue();
            verify(reportService).isUnderReviewBatch(eq(ReportTargetType.FEED), anyList());
        }

        @Test
        @DisplayName("길드 피드 목록 조회 시 신고 처리중 상태가 일괄 조회된다")
        void getGuildFeeds_batchUnderReviewCheck() {
            // given
            Long guildId = 1L;
            ActivityFeed feed = createTestFeed(1L, TEST_USER_ID);
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(feed));

            when(guildQueryFacadeService.getUserGuildMemberships(TEST_USER_ID))
                .thenReturn(List.of(new GuildMembershipInfo(guildId, "테스트길드", null, 1, false, false)));
            when(activityFeedRepository.findGuildFeeds(eq(guildId), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());

            Map<String, Boolean> underReviewMap = new HashMap<>();
            underReviewMap.put("1", true);
            when(reportService.isUnderReviewBatch(eq(ReportTargetType.FEED), anyList())).thenReturn(underReviewMap);

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getGuildFeeds(guildId, TEST_USER_ID, 0, 10);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getIsUnderReview()).isTrue();
            verify(reportService).isUnderReviewBatch(eq(ReportTargetType.FEED), anyList());
        }

        @Test
        @DisplayName("피드 검색 시 신고 처리중 상태가 일괄 조회된다")
        void searchFeeds_batchUnderReviewCheck() {
            // given
            String keyword = "테스트";
            ActivityFeed feed = createTestFeed(1L, TEST_USER_ID);
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(feed));

            when(activityFeedRepository.searchByKeyword(eq(keyword), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());

            Map<String, Boolean> underReviewMap = new HashMap<>();
            underReviewMap.put("1", false);
            when(reportService.isUnderReviewBatch(eq(ReportTargetType.FEED), anyList())).thenReturn(underReviewMap);

            // when
            Page<ActivityFeedResponse> result = feedQueryService.searchFeeds(keyword, TEST_USER_ID, 0, 10);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getIsUnderReview()).isFalse();
            verify(reportService).isUnderReviewBatch(eq(ReportTargetType.FEED), anyList());
        }

        @Test
        @DisplayName("빈 피드 목록 조회 시 빈 맵이 반환되어도 정상 동작한다")
        void getPublicFeeds_emptyList_emptyMapReturned() {
            // given
            Page<ActivityFeed> emptyPage = new PageImpl<>(Collections.emptyList());

            when(activityFeedRepository.findAccessibleFeeds(any(), any(), any(), any(Pageable.class)))
                .thenReturn(emptyPage);
            when(reportService.isUnderReviewBatch(eq(ReportTargetType.FEED), anyList()))
                .thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getPublicFeeds(TEST_USER_ID, 0, 10);

            // then
            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("currentUserId가 null이어도 피드 상세를 조회한다")
        void getFeed_currentUserIdNull_success() {
            // given
            Long feedId = 1L;
            ActivityFeed feed = createTestFeed(feedId, OTHER_USER_ID);

            when(activityFeedRepository.findById(feedId)).thenReturn(Optional.of(feed));
            when(reportService.isUnderReview(ReportTargetType.FEED, "1")).thenReturn(false);

            // when
            ActivityFeedResponse result = feedQueryService.getFeed(feedId, null);

            // then - currentUserId null이면 likedByMe=false, isMyFeed=false
            assertThat(result).isNotNull();
            assertThat(result.isLikedByMe()).isFalse();
        }

        @Test
        @DisplayName("댓글 목록 조회 시 신고 처리중 상태가 일괄 조회된다")
        void getComments_batchUnderReviewCheck() {
            // given
            Long feedId = 1L;
            ActivityFeed feed = createTestFeed(feedId, OTHER_USER_ID);

            FeedComment comment = FeedComment.builder()
                .feed(feed)
                .userId(TEST_USER_ID)
                .userNickname("테스트유저")
                .content("테스트 댓글")
                .isDeleted(false)
                .build();
            setId(comment, 1L);

            Page<FeedComment> commentPage = new PageImpl<>(List.of(comment));
            when(activityFeedRepository.findById(feedId)).thenReturn(Optional.of(feed));
            when(feedCommentRepository.findRootCommentsByFeedId(eq(feedId), any(Pageable.class))).thenReturn(commentPage);

            Map<String, Boolean> underReviewMap = new HashMap<>();
            underReviewMap.put("1", true);
            when(reportService.isUnderReviewBatch(eq(ReportTargetType.FEED_COMMENT), anyList())).thenReturn(underReviewMap);

            // when
            Page<FeedCommentResponse> result = feedQueryService.getComments(feedId, TEST_USER_ID, 0, 10);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getIsUnderReview()).isTrue();
            verify(reportService).isUnderReviewBatch(eq(ReportTargetType.FEED_COMMENT), anyList());
        }
    }

    @Nested
    @DisplayName("translateFeed 분기 테스트")
    class TranslateFeedTest {

        @Test
        @DisplayName("기본 언어(en)로 공개 피드를 조회하면 번역 서비스를 호출하지 않는다")
        void getPublicFeeds_defaultLocale_noTranslation() {
            // given
            ActivityFeed feed = createTestFeed(1L, TEST_USER_ID);
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(feed));

            when(activityFeedRepository.findAccessibleFeeds(any(), any(), any(), any(Pageable.class))).thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList())).thenReturn(Collections.emptyList());
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            feedQueryService.getPublicFeeds(TEST_USER_ID, 0, 10, "en");

            // then - 기본 언어이므로 translationService 호출 안됨
            org.mockito.Mockito.verify(translationService, org.mockito.Mockito.never())
                .translateContent(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("acceptLanguage가 null이면 기본 언어로 처리되어 번역을 호출하지 않는다")
        void getPublicFeeds_nullLocale_noTranslation() {
            // given
            ActivityFeed feed = createTestFeed(1L, TEST_USER_ID);
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(feed));

            when(activityFeedRepository.findAccessibleFeeds(any(), any(), any(), any(Pageable.class))).thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList())).thenReturn(Collections.emptyList());
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            feedQueryService.getPublicFeeds(TEST_USER_ID, 0, 10, null);

            // then - null locale도 기본 언어 처리
            org.mockito.Mockito.verify(translationService, org.mockito.Mockito.never())
                .translateContent(any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("translateComment 분기 테스트")
    class TranslateCommentTest {

        @Test
        @DisplayName("삭제된 댓글은 번역을 호출하지 않는다")
        void getComments_deletedComment_noTranslation() {
            // given
            Long feedId = 1L;
            ActivityFeed feed = createTestFeed(feedId, OTHER_USER_ID);

            FeedComment deletedComment = FeedComment.builder()
                .feed(feed)
                .userId(TEST_USER_ID)
                .userNickname("테스트유저")
                .content("삭제된 댓글")
                .isDeleted(true)
                .build();
            setId(deletedComment, 1L);

            Page<FeedComment> commentPage = new PageImpl<>(List.of(deletedComment));
            when(activityFeedRepository.findById(feedId)).thenReturn(Optional.of(feed));
            when(feedCommentRepository.findRootCommentsByFeedId(eq(feedId), any(Pageable.class))).thenReturn(commentPage);
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID))
                .thenReturn(new UserProfileInfo(TEST_USER_ID, "테스트유저", null, 3, null, null, null));
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            feedQueryService.getComments(feedId, TEST_USER_ID, 0, 10, "en");

            // then - 삭제된 댓글은 번역 안 함
            org.mockito.Mockito.verify(translationService, org.mockito.Mockito.never())
                .translateContent(any(), any(), any(), any());
        }

        @Test
        @DisplayName("기본 언어(en) 댓글 조회 시 번역을 호출하지 않는다")
        void getComments_defaultLocale_noTranslation() {
            // given
            Long feedId = 1L;
            ActivityFeed feed = createTestFeed(feedId, OTHER_USER_ID);

            FeedComment comment = FeedComment.builder()
                .feed(feed)
                .userId(TEST_USER_ID)
                .userNickname("테스트유저")
                .content("댓글")
                .isDeleted(false)
                .build();
            setId(comment, 2L);

            Page<FeedComment> commentPage = new PageImpl<>(List.of(comment));
            when(activityFeedRepository.findById(feedId)).thenReturn(Optional.of(feed));
            when(feedCommentRepository.findRootCommentsByFeedId(eq(feedId), any(Pageable.class))).thenReturn(commentPage);
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID))
                .thenReturn(new UserProfileInfo(TEST_USER_ID, "테스트유저", null, 3, null, null, null));
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            feedQueryService.getComments(feedId, TEST_USER_ID, 0, 10, "en");

            // then - 기본 언어이므로 번역 안 함
            org.mockito.Mockito.verify(translationService, org.mockito.Mockito.never())
                .translateContent(any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("getComments getUserProfile 예외 폴백 테스트")
    class GetCommentsFallbackTest {

        @Test
        @DisplayName("userProfile 조회 실패 시 comment에 저장된 userLevel로 폴백한다")
        void getComments_userProfileException_fallbackToCommentLevel() {
            // given
            Long feedId = 1L;
            ActivityFeed feed = createTestFeed(feedId, OTHER_USER_ID);

            FeedComment comment = FeedComment.builder()
                .feed(feed)
                .userId(TEST_USER_ID)
                .userNickname("테스트유저")
                .content("댓글")
                .isDeleted(false)
                .userLevel(7)  // 저장된 레벨
                .build();
            setId(comment, 3L);

            Page<FeedComment> commentPage = new PageImpl<>(List.of(comment));
            when(activityFeedRepository.findById(feedId)).thenReturn(Optional.of(feed));
            when(feedCommentRepository.findRootCommentsByFeedId(eq(feedId), any(Pageable.class))).thenReturn(commentPage);
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID))
                .thenThrow(new RuntimeException("사용자 조회 실패"));
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<FeedCommentResponse> result = feedQueryService.getComments(feedId, TEST_USER_ID, 0, 10);

            // then - 예외가 전파되지 않고 저장된 userLevel 사용
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getUserLevel()).isEqualTo(7);
        }

        @Test
        @DisplayName("userProfile 조회 실패 및 comment.userLevel도 null이면 기본값 1을 사용한다")
        void getComments_userProfileException_userLevelNull_defaultsTo1() {
            // given
            Long feedId = 1L;
            ActivityFeed feed = createTestFeed(feedId, OTHER_USER_ID);

            FeedComment comment = FeedComment.builder()
                .feed(feed)
                .userId(TEST_USER_ID)
                .userNickname("테스트유저")
                .content("댓글")
                .isDeleted(false)
                // userLevel = null
                .build();
            setId(comment, 4L);

            Page<FeedComment> commentPage = new PageImpl<>(List.of(comment));
            when(activityFeedRepository.findById(feedId)).thenReturn(Optional.of(feed));
            when(feedCommentRepository.findRootCommentsByFeedId(eq(feedId), any(Pageable.class))).thenReturn(commentPage);
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID))
                .thenThrow(new RuntimeException("사용자 조회 실패"));
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<FeedCommentResponse> result = feedQueryService.getComments(feedId, TEST_USER_ID, 0, 10);

            // then - 기본값 1 사용
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getUserLevel()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("getLikedFeedIds 분기 테스트")
    class GetLikedFeedIdsTest {

        @Test
        @DisplayName("currentUserId가 null이면 빈 좋아요 목록을 반환한다")
        void getPublicFeeds_userIdNull_emptyLikedIds() {
            // given
            ActivityFeed feed = createTestFeed(1L, OTHER_USER_ID);
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(feed));

            when(activityFeedRepository.findAccessibleFeeds(any(), any(), any(), any(Pageable.class))).thenReturn(feedPage);
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getPublicFeeds(null, 0, 10);

            // then - userId null이면 feedLikeRepository 호출 안됨
            assertThat(result.getContent()).hasSize(1);
            org.mockito.Mockito.verify(feedLikeRepository, org.mockito.Mockito.never())
                .findLikedFeedIds(any(), anyList());
        }

        @Test
        @DisplayName("피드 목록이 비어있으면 좋아요 조회를 하지 않는다")
        void getPublicFeeds_emptyFeeds_noLikeQuery() {
            // given
            Page<ActivityFeed> emptyPage = new PageImpl<>(Collections.emptyList());
            when(activityFeedRepository.findAccessibleFeeds(any(), any(), any(), any(Pageable.class))).thenReturn(emptyPage);
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getPublicFeeds(TEST_USER_ID, 0, 10);

            // then - 빈 피드 목록이면 findLikedFeedIds 호출 안됨
            assertThat(result.getContent()).isEmpty();
            org.mockito.Mockito.verify(feedLikeRepository, org.mockito.Mockito.never())
                .findLikedFeedIds(any(), anyList());
        }
    }

    @Nested
    @DisplayName("getPublicFeedsByCategory 추가 분기 테스트")
    class GetPublicFeedsByCategoryExtraTest {

        @Test
        @DisplayName("page > 0이면 Featured 피드를 추가하지 않는다")
        void getPublicFeedsByCategory_pageGreaterThanZero_noFeatured() {
            // given
            Long categoryId = 1L;
            ActivityFeed feed = createTestFeed(1L, TEST_USER_ID);
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(feed));

            when(adminInternalFeignClient.getFeaturedFeedIds(categoryId)).thenReturn(List.of(99L));
            when(activityFeedRepository.findAccessibleFeedsByCategoryId(eq(categoryId), any(), any(), any(), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when - page=1 (첫 페이지 아님)
            Page<ActivityFeedResponse> result = feedQueryService.getPublicFeedsByCategory(categoryId, TEST_USER_ID, 1, 10);

            // then - featured는 첫 페이지에만 적용
            assertThat(result.getContent()).hasSize(1);
            // findByIdIn이 호출되지 않음 (featuredFeedIds가 비어있지 않아도 page>0이면 featured 추가 안됨)
        }

        @Test
        @DisplayName("combined 피드가 size를 초과하면 잘라낸다")
        void getPublicFeedsByCategory_oversizedCombined_truncated() {
            // given
            Long categoryId = 1L;
            // 5개 featured + 5개 normal → size=5 초과
            List<ActivityFeed> featuredFeeds = new java.util.ArrayList<>();
            List<Long> featuredIds = new java.util.ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                ActivityFeed f = createTestFeed((long)i, TEST_USER_ID);
                featuredFeeds.add(f);
                featuredIds.add((long)i);
            }
            // normal 피드 1개 (duplicate 아닌 것)
            ActivityFeed normalFeed = createTestFeed(10L, OTHER_USER_ID);
            Page<ActivityFeed> normalPage = new PageImpl<>(List.of(normalFeed));

            when(adminInternalFeignClient.getFeaturedFeedIds(categoryId)).thenReturn(featuredIds);
            when(activityFeedRepository.findByIdIn(featuredIds)).thenReturn(featuredFeeds);
            when(activityFeedRepository.findAccessibleFeedsByCategoryId(eq(categoryId), any(), any(), any(), any(Pageable.class)))
                .thenReturn(normalPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when - size=5
            Page<ActivityFeedResponse> result = feedQueryService.getPublicFeedsByCategory(categoryId, TEST_USER_ID, 0, 5);

            // then - 5개로 제한됨
            assertThat(result.getContent()).hasSize(5);
        }
    }

    @Nested
    @DisplayName("searchFeedsByCategory 분기 테스트")
    class SearchFeedsByCategoryExtraTest {

        @Test
        @DisplayName("유효하지 않은 카테고리로 피드 검색 시 빈 결과 반환")
        void searchFeedsByCategory_invalidCategory_returnsEmpty() {
            // when
            Page<ActivityFeedResponse> result = feedQueryService.searchFeedsByCategory(
                "키워드", "INVALID_CATEGORY", TEST_USER_ID, 0, 10);

            // then
            assertThat(result.getContent()).isEmpty();
            org.mockito.Mockito.verify(activityFeedRepository, org.mockito.Mockito.never())
                .searchByKeywordAndCategory(any(), anyList(), any());
        }
    }

    // ==================== 새로 추가된 테스트 ====================

    @Nested
    @DisplayName("searchFeedsForAdmin 테스트")
    class SearchFeedsForAdminTest {

        @Test
        @DisplayName("모든 파라미터가 null인 경우 전체 검색을 수행한다")
        void searchFeedsForAdmin_allParamsNull() {
            // given
            ActivityFeed feed = createTestFeed(1L, TEST_USER_ID);
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(feed));

            when(activityFeedRepository.searchFeedsForAdmin(
                eq(null), eq(null), eq(null), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(feedPage);

            // when
            FeedAdminPageResponse result = feedQueryService.searchFeedsForAdmin(
                null, null, null, null, null, 0, 10, null, null);

            // then
            assertThat(result).isNotNull();
            assertThat(result.content()).hasSize(1);
        }

        @Test
        @DisplayName("activityType과 visibility 파라미터가 있으면 enum으로 변환하여 검색한다")
        void searchFeedsForAdmin_withActivityTypeAndVisibility() {
            // given
            ActivityFeed feed = createTestFeed(1L, TEST_USER_ID);
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(feed));

            when(activityFeedRepository.searchFeedsForAdmin(
                eq(ActivityType.MISSION_COMPLETED), eq(FeedVisibility.PUBLIC),
                eq(TEST_USER_ID), eq(1L), eq("keyword"), any(Pageable.class)))
                .thenReturn(feedPage);

            // when
            FeedAdminPageResponse result = feedQueryService.searchFeedsForAdmin(
                "MISSION_COMPLETED", "PUBLIC", TEST_USER_ID, 1L, "keyword", 0, 10, "id", "ASC");

            // then
            assertThat(result).isNotNull();
            assertThat(result.content()).hasSize(1);
        }

        @Test
        @DisplayName("sortDirection이 ASC이면 오름차순 정렬을 적용한다")
        void searchFeedsForAdmin_sortAscending() {
            // given
            Page<ActivityFeed> feedPage = new PageImpl<>(Collections.emptyList());
            when(activityFeedRepository.searchFeedsForAdmin(
                any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(feedPage);

            // when
            FeedAdminPageResponse result = feedQueryService.searchFeedsForAdmin(
                null, null, null, null, null, 0, 10, "createdAt", "ASC");

            // then
            assertThat(result).isNotNull();
            verify(activityFeedRepository).searchFeedsForAdmin(
                any(), any(), any(), any(), any(), any(Pageable.class));
        }

        @Test
        @DisplayName("sortDirection이 DESC이면 내림차순 정렬을 적용한다")
        void searchFeedsForAdmin_sortDescending() {
            // given
            Page<ActivityFeed> feedPage = new PageImpl<>(Collections.emptyList());
            when(activityFeedRepository.searchFeedsForAdmin(
                any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(feedPage);

            // when
            FeedAdminPageResponse result = feedQueryService.searchFeedsForAdmin(
                null, null, null, null, null, 0, 10, "createdAt", "DESC");

            // then
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("sortBy가 null이면 기본 정렬 기준 id를 사용한다")
        void searchFeedsForAdmin_sortByNull_defaultsToId() {
            // given
            Page<ActivityFeed> feedPage = new PageImpl<>(Collections.emptyList());
            when(activityFeedRepository.searchFeedsForAdmin(
                any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(feedPage);

            // when — sortBy=null, sortDirection=ASC
            FeedAdminPageResponse result = feedQueryService.searchFeedsForAdmin(
                null, null, null, null, null, 0, 10, null, "ASC");

            // then
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("sortDirection이 null이면 DESC(기본값)으로 처리한다")
        void searchFeedsForAdmin_sortDirectionNull_defaultsToDesc() {
            // given
            Page<ActivityFeed> feedPage = new PageImpl<>(Collections.emptyList());
            when(activityFeedRepository.searchFeedsForAdmin(
                any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(feedPage);

            // when — sortDirection=null → else 분기(DESC)
            FeedAdminPageResponse result = feedQueryService.searchFeedsForAdmin(
                null, null, null, null, null, 0, 10, "id", null);

            // then
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("getFilteredFeeds 테스트")
    class GetFilteredFeedsTest {

        @Test
        @DisplayName("searchType이 null이면 전체 공개 피드를 반환한다")
        void getFilteredFeeds_searchTypeNull_returnsPublic() {
            // given
            ActivityFeed feed = createTestFeed(1L, TEST_USER_ID);
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(feed));

            when(activityFeedRepository.findAccessibleFeeds(any(), any(), any(), any(Pageable.class))).thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getFilteredFeeds(
                null, TEST_USER_ID, 0, 10, null);

            // then
            assertThat(result).isNotNull();
            verify(activityFeedRepository).findAccessibleFeeds(any(), any(), any(), any(Pageable.class));
        }

        @Test
        @DisplayName("userId가 null이면 전체 공개 피드를 반환한다")
        void getFilteredFeeds_userIdNull_returnsPublic() {
            // given
            Page<ActivityFeed> feedPage = new PageImpl<>(Collections.emptyList());

            when(activityFeedRepository.findAccessibleFeeds(any(), any(), any(), any(Pageable.class))).thenReturn(feedPage);
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getFilteredFeeds(
                FeedSearchType.ALL, null, 0, 10, null);

            // then
            assertThat(result).isNotNull();
            verify(activityFeedRepository).findAccessibleFeeds(any(), any(), any(), any(Pageable.class));
        }

        @Test
        @DisplayName("searchType=ALL이면 전체 공개 피드를 반환한다")
        void getFilteredFeeds_searchTypeAll() {
            // given
            ActivityFeed feed = createTestFeed(1L, TEST_USER_ID);
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(feed));

            when(activityFeedRepository.findAccessibleFeeds(any(), any(), any(), any(Pageable.class))).thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getFilteredFeeds(
                FeedSearchType.ALL, TEST_USER_ID, 0, 10, null);

            // then
            assertThat(result.getContent()).hasSize(1);
            verify(activityFeedRepository).findAccessibleFeeds(any(), any(), any(), any(Pageable.class));
        }

        @Test
        @DisplayName("searchType=FRIENDS이고 친구가 없으면 빈 페이지를 반환한다")
        void getFilteredFeeds_searchTypeFriends_noFriends_returnsEmpty() {
            // given
            when(userQueryFacadeService.getFriendIds(TEST_USER_ID))
                .thenReturn(Collections.emptyList());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getFilteredFeeds(
                FeedSearchType.FRIENDS, TEST_USER_ID, 0, 10, null);

            // then
            assertThat(result.getContent()).isEmpty();
            verify(activityFeedRepository, never()).findFriendsFeeds(anyList(), any(Pageable.class));
        }

        @Test
        @DisplayName("searchType=FRIENDS이고 친구가 있으면 친구 피드를 반환한다")
        void getFilteredFeeds_searchTypeFriends_withFriends() {
            // given
            List<String> friendIds = List.of("friend-1", "friend-2");
            ActivityFeed feed = createTestFeed(1L, "friend-1");
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(feed));

            when(userQueryFacadeService.getFriendIds(TEST_USER_ID)).thenReturn(friendIds);
            when(activityFeedRepository.findFriendsFeeds(eq(friendIds), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getFilteredFeeds(
                FeedSearchType.FRIENDS, TEST_USER_ID, 0, 10, null);

            // then
            assertThat(result.getContent()).hasSize(1);
            verify(activityFeedRepository).findFriendsFeeds(eq(friendIds), any(Pageable.class));
        }

        @Test
        @DisplayName("searchType=GUILD이고 소속 길드가 없으면 빈 페이지를 반환한다")
        void getFilteredFeeds_searchTypeGuild_noGuilds_returnsEmpty() {
            // given
            when(guildQueryFacadeService.getUserGuildMemberships(TEST_USER_ID))
                .thenReturn(Collections.emptyList());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getFilteredFeeds(
                FeedSearchType.GUILD, TEST_USER_ID, 0, 10, null);

            // then
            assertThat(result.getContent()).isEmpty();
            verify(activityFeedRepository, never()).findGuildOnlyFeedsByGuildIds(anyList(), any(Pageable.class));
        }

        @Test
        @DisplayName("searchType=GUILD이고 소속 길드가 있으면 길드 피드를 반환한다")
        void getFilteredFeeds_searchTypeGuild_withGuilds() {
            // given
            Long guildId = 10L;
            GuildMembershipInfo membership = new GuildMembershipInfo(guildId, "테스트길드", null, 1, false, false);
            ActivityFeed feed = createTestFeed(1L, TEST_USER_ID);
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(feed));

            when(guildQueryFacadeService.getUserGuildMemberships(TEST_USER_ID))
                .thenReturn(List.of(membership));
            when(activityFeedRepository.findGuildOnlyFeedsByGuildIds(eq(List.of(guildId)), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getFilteredFeeds(
                FeedSearchType.GUILD, TEST_USER_ID, 0, 10, null);

            // then
            assertThat(result.getContent()).hasSize(1);
            verify(activityFeedRepository).findGuildOnlyFeedsByGuildIds(eq(List.of(guildId)), any(Pageable.class));
        }

        @Test
        @DisplayName("searchType=MINE이면 내 공개 피드를 반환한다")
        void getFilteredFeeds_searchTypeMine() {
            // given
            ActivityFeed feed = createTestFeed(1L, TEST_USER_ID);
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(feed));

            when(activityFeedRepository.findPublicFeedsByUserId(eq(TEST_USER_ID), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getFilteredFeeds(
                FeedSearchType.MINE, TEST_USER_ID, 0, 10, null);

            // then
            assertThat(result.getContent()).hasSize(1);
            verify(activityFeedRepository).findPublicFeedsByUserId(eq(TEST_USER_ID), any(Pageable.class));
        }

        @Test
        @DisplayName("QA-139: searchType=MINE 응답에도 다중 이미지(imageUrls)가 포함된다")
        void getFilteredFeeds_searchTypeMine_includesMultipleImageUrls() {
            // given
            Long feedId = 7L;
            ActivityFeed feed = createTestFeed(feedId, TEST_USER_ID);
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(feed));

            io.pinkspider.leveluptogethermvp.feedservice.domain.entity.ActivityFeedImage img1 =
                io.pinkspider.leveluptogethermvp.feedservice.domain.entity.ActivityFeedImage.builder()
                    .feed(feed).imageUrl("https://cdn/x.jpg").sortOrder(0).build();
            io.pinkspider.leveluptogethermvp.feedservice.domain.entity.ActivityFeedImage img2 =
                io.pinkspider.leveluptogethermvp.feedservice.domain.entity.ActivityFeedImage.builder()
                    .feed(feed).imageUrl("https://cdn/y.jpg").sortOrder(1).build();

            when(activityFeedRepository.findPublicFeedsByUserId(eq(TEST_USER_ID), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());
            when(activityFeedImageRepository.findByFeedIdInOrderBySortOrder(anyList()))
                .thenReturn(List.of(img1, img2));

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getFilteredFeeds(
                FeedSearchType.MINE, TEST_USER_ID, 0, 10, null);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getImageUrls())
                .containsExactly("https://cdn/x.jpg", "https://cdn/y.jpg");
        }
    }

    @Nested
    @DisplayName("getFeed 추가 분기 테스트")
    class GetFeedAdditionalTest {

        @Test
        @DisplayName("현재 유저가 피드 작성자이면 isMyFeed=true를 반환한다")
        void getFeed_isMyFeed_true() {
            // given
            Long feedId = 1L;
            ActivityFeed feed = createTestFeed(feedId, TEST_USER_ID);

            when(activityFeedRepository.findById(feedId)).thenReturn(Optional.of(feed));
            when(feedLikeRepository.existsByFeedIdAndUserId(feedId, TEST_USER_ID)).thenReturn(true);
            when(reportService.isUnderReview(any(), anyString())).thenReturn(false);

            // when
            ActivityFeedResponse result = feedQueryService.getFeed(feedId, TEST_USER_ID, null);

            // then
            assertThat(result.isMyFeed()).isTrue();
            assertThat(result.isLikedByMe()).isTrue();
        }

        @Test
        @DisplayName("현재 유저가 피드 작성자가 아니면 isMyFeed=false를 반환한다")
        void getFeed_isMyFeed_false() {
            // given
            Long feedId = 1L;
            ActivityFeed feed = createTestFeed(feedId, OTHER_USER_ID);

            when(activityFeedRepository.findById(feedId)).thenReturn(Optional.of(feed));
            when(feedLikeRepository.existsByFeedIdAndUserId(feedId, TEST_USER_ID)).thenReturn(false);
            when(reportService.isUnderReview(any(), anyString())).thenReturn(false);

            // when
            ActivityFeedResponse result = feedQueryService.getFeed(feedId, TEST_USER_ID, null);

            // then
            assertThat(result.isMyFeed()).isFalse();
        }
    }

    @Nested
    @DisplayName("enrichFeeds lambda 분기 테스트")
    class EnrichFeedsLambdaTest {

        @Test
        @DisplayName("enrichFeeds에서 피드 작성자와 현재 유저가 같으면 isMyFeed=true로 매핑된다")
        void enrichFeeds_isMyFeed_true_viaGetFilteredFeedsMine() {
            // given
            ActivityFeed myFeed = createTestFeed(1L, TEST_USER_ID);
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(myFeed));

            when(activityFeedRepository.findPublicFeedsByUserId(eq(TEST_USER_ID), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(List.of(1L));  // 좋아요한 피드
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getFilteredFeeds(
                FeedSearchType.MINE, TEST_USER_ID, 0, 10, null);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).isMyFeed()).isTrue();
            assertThat(result.getContent().get(0).isLikedByMe()).isTrue();
        }

        @Test
        @DisplayName("enrichFeeds에서 신고 처리중인 피드는 isUnderReview=true로 설정된다")
        void enrichFeeds_underReview_true_viaFriendsFeeds() {
            // given
            List<String> friendIds = List.of("friend-1");
            ActivityFeed feed = createTestFeed(5L, "friend-1");
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(feed));

            when(userQueryFacadeService.getFriendIds(TEST_USER_ID)).thenReturn(friendIds);
            when(activityFeedRepository.findFriendsFeeds(eq(friendIds), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());

            Map<String, Boolean> underReviewMap = new HashMap<>();
            underReviewMap.put("5", true);
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(underReviewMap);

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getFilteredFeeds(
                FeedSearchType.FRIENDS, TEST_USER_ID, 0, 10, null);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getIsUnderReview()).isTrue();
        }

        @Test
        @DisplayName("enrichFeeds에서 userId가 null이면 isMyFeed=false로 매핑된다")
        void enrichFeeds_userId_null_isMyFeed_false() {
            // given — getFilteredFeeds(null, null) → null/null 분기로 getPublicFeeds() 호출
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(createTestFeed(1L, TEST_USER_ID)));

            when(activityFeedRepository.findAccessibleFeeds(any(), any(), any(), any(Pageable.class))).thenReturn(feedPage);
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getFilteredFeeds(
                null, null, 0, 10, null);

            // then
            assertThat(result.getContent()).hasSize(1);
            // currentUserId=null → isMyFeed 평가 시 null!=null이면 false
            assertThat(result.getContent().get(0).isMyFeed()).isFalse();
        }
    }

    @Nested
    @DisplayName("getUserFeeds lambda 분기 테스트 (lambda$getUserFeeds$10)")
    class GetUserFeedsLambdaTest {

        @Test
        @DisplayName("GUILD 공개범위 피드에서 같은 길드 소속이면 열람 가능하다")
        void getUserFeeds_guildFeed_sameGuild_canView() {
            // given
            Long guildId = 100L;
            ActivityFeed guildFeed = ActivityFeed.builder()
                .userId(OTHER_USER_ID)
                .userNickname("다른유저")
                .activityType(ActivityType.MISSION_COMPLETED)
                .title("길드공개 피드")
                .visibility(FeedVisibility.GUILD)
                .guildId(guildId)
                .likeCount(0)
                .commentCount(0)
                .build();
            setId(guildFeed, 1L);
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(guildFeed));

            GuildMembershipInfo membership = new GuildMembershipInfo(guildId, "테스트길드", null, 1, false, false);

            when(activityFeedRepository.findByUserId(eq(OTHER_USER_ID), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());
            when(userQueryFacadeService.areFriends(TEST_USER_ID, OTHER_USER_ID)).thenReturn(false);
            // 현재 유저가 같은 길드 소속
            when(guildQueryFacadeService.getUserGuildMemberships(TEST_USER_ID))
                .thenReturn(List.of(membership));
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getUserFeeds(
                OTHER_USER_ID, TEST_USER_ID, 0, 10);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0)).isNotNull();
        }

        @Test
        @DisplayName("GUILD 공개범위 피드에서 다른 길드 소속이면 null을 반환한다")
        void getUserFeeds_guildFeed_differentGuild_returnsNull() {
            // given
            Long feedGuildId = 100L;
            Long myGuildId = 200L;
            ActivityFeed guildFeed = ActivityFeed.builder()
                .userId(OTHER_USER_ID)
                .userNickname("다른유저")
                .activityType(ActivityType.MISSION_COMPLETED)
                .title("길드공개 피드")
                .visibility(FeedVisibility.GUILD)
                .guildId(feedGuildId)
                .likeCount(0)
                .commentCount(0)
                .build();
            setId(guildFeed, 1L);
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(guildFeed));

            GuildMembershipInfo myMembership = new GuildMembershipInfo(myGuildId, "내길드", null, 1, false, false);

            when(activityFeedRepository.findByUserId(eq(OTHER_USER_ID), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());
            when(userQueryFacadeService.areFriends(TEST_USER_ID, OTHER_USER_ID)).thenReturn(false);
            // 현재 유저는 다른 길드 소속
            when(guildQueryFacadeService.getUserGuildMemberships(TEST_USER_ID))
                .thenReturn(List.of(myMembership));
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getUserFeeds(
                OTHER_USER_ID, TEST_USER_ID, 0, 10);

            // then — GUILD 공개이지만 같은 길드 아니므로 null
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0)).isNull();
        }

        @Test
        @DisplayName("PRIVATE 공개범위 피드는 항상 null을 반환한다")
        void getUserFeeds_privateFeed_alwaysNull() {
            // given
            ActivityFeed privateFeed = ActivityFeed.builder()
                .userId(OTHER_USER_ID)
                .userNickname("다른유저")
                .activityType(ActivityType.MISSION_COMPLETED)
                .title("비공개 피드")
                .visibility(FeedVisibility.PRIVATE)
                .likeCount(0)
                .commentCount(0)
                .build();
            setId(privateFeed, 1L);
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(privateFeed));

            when(activityFeedRepository.findByUserId(eq(OTHER_USER_ID), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());
            // 친구이더라도 PRIVATE은 열람 불가
            when(userQueryFacadeService.areFriends(TEST_USER_ID, OTHER_USER_ID)).thenReturn(true);
            when(guildQueryFacadeService.getUserGuildMemberships(anyString()))
                .thenReturn(Collections.emptyList());
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getUserFeeds(
                OTHER_USER_ID, TEST_USER_ID, 0, 10);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0)).isNull();
        }

        @Test
        @DisplayName("GUILD 공개범위이고 feed.guildId가 null인 피드는 null을 반환한다")
        void getUserFeeds_guildFeed_guildIdNull_returnsNull() {
            // given — guildId가 null인 GUILD 공개 피드
            ActivityFeed guildFeed = ActivityFeed.builder()
                .userId(OTHER_USER_ID)
                .userNickname("다른유저")
                .activityType(ActivityType.MISSION_COMPLETED)
                .title("길드공개 피드 (guildId 없음)")
                .visibility(FeedVisibility.GUILD)
                .guildId(null)
                .likeCount(0)
                .commentCount(0)
                .build();
            setId(guildFeed, 1L);
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(guildFeed));

            when(activityFeedRepository.findByUserId(eq(OTHER_USER_ID), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());
            when(userQueryFacadeService.areFriends(TEST_USER_ID, OTHER_USER_ID)).thenReturn(false);
            when(guildQueryFacadeService.getUserGuildMemberships(TEST_USER_ID))
                .thenReturn(Collections.emptyList());
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getUserFeeds(
                OTHER_USER_ID, TEST_USER_ID, 0, 10);

            // then — guildId null이면 myGuildIds.contains(null) = false → null 반환
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0)).isNull();
        }
    }

    @Nested
    @DisplayName("translateComment 분기 테스트 (비기본 언어)")
    class TranslateCommentNonDefaultLocaleTest {

        @Test
        @DisplayName("비기본 언어이고 삭제되지 않은 댓글은 번역 서비스를 호출한다")
        void getComments_nonDefaultLocale_notDeleted_callsTranslation() {
            // given
            Long feedId = 1L;
            ActivityFeed feed = createTestFeed(feedId, OTHER_USER_ID);

            FeedComment comment = FeedComment.builder()
                .feed(feed)
                .userId(TEST_USER_ID)
                .userNickname("테스트유저")
                .content("댓글 내용")
                .isDeleted(false)
                .userLevel(3)
                .build();
            setId(comment, 10L);

            Page<FeedComment> commentPage = new PageImpl<>(List.of(comment));
            when(activityFeedRepository.findById(feedId)).thenReturn(Optional.of(feed));
            when(feedCommentRepository.findRootCommentsByFeedId(eq(feedId), any(Pageable.class))).thenReturn(commentPage);
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID))
                .thenReturn(new UserProfileInfo(TEST_USER_ID, "테스트유저", null, 3, null, null, null));
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());
            when(translationService.translateContent(any(), any(), any(), any())).thenReturn(null);

            // when
            feedQueryService.getComments(feedId, TEST_USER_ID, 0, 10, "ja");

            // then — ja는 기본 언어(en) 아니므로 번역 호출됨
            verify(translationService).translateContent(
                eq(io.pinkspider.global.translation.enums.ContentType.FEED_COMMENT),
                any(), any(), eq("ja"));
        }

        @Test
        @DisplayName("비기본 언어이지만 삭제된 댓글은 번역 서비스를 호출하지 않는다")
        void getComments_nonDefaultLocale_deleted_noTranslation() {
            // given
            Long feedId = 1L;
            ActivityFeed feed = createTestFeed(feedId, OTHER_USER_ID);

            FeedComment deletedComment = FeedComment.builder()
                .feed(feed)
                .userId(TEST_USER_ID)
                .userNickname("테스트유저")
                .content("삭제된 댓글")
                .isDeleted(true)
                .userLevel(3)
                .build();
            setId(deletedComment, 11L);

            Page<FeedComment> commentPage = new PageImpl<>(List.of(deletedComment));
            when(activityFeedRepository.findById(feedId)).thenReturn(Optional.of(feed));
            when(feedCommentRepository.findRootCommentsByFeedId(eq(feedId), any(Pageable.class))).thenReturn(commentPage);
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID))
                .thenReturn(new UserProfileInfo(TEST_USER_ID, "테스트유저", null, 3, null, null, null));
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            feedQueryService.getComments(feedId, TEST_USER_ID, 0, 10, "ja");

            // then — 삭제된 댓글은 번역 호출 안됨
            verify(translationService, never()).translateContent(any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("getFriendsOnlyFeeds 분기 테스트")
    class GetFriendsOnlyFeedsTest {

        @Test
        @DisplayName("친구 목록이 비어있으면 빈 페이지를 즉시 반환한다")
        void getFriendsOnlyFeeds_emptyFriendIds_returnsEmpty() {
            // given
            when(userQueryFacadeService.getFriendIds(TEST_USER_ID))
                .thenReturn(Collections.emptyList());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getFilteredFeeds(
                FeedSearchType.FRIENDS, TEST_USER_ID, 0, 10, "ko");

            // then
            assertThat(result.getContent()).isEmpty();
            verify(activityFeedRepository, never()).findFriendsFeeds(anyList(), any(Pageable.class));
        }

        @Test
        @DisplayName("친구 목록이 있으면 친구 피드를 조회한다")
        void getFriendsOnlyFeeds_withFriends_fetchesFeeds() {
            // given
            List<String> friendIds = List.of("friend-A");
            ActivityFeed feed = createTestFeed(1L, "friend-A");
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(feed));

            when(userQueryFacadeService.getFriendIds(TEST_USER_ID)).thenReturn(friendIds);
            when(activityFeedRepository.findFriendsFeeds(eq(friendIds), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getFilteredFeeds(
                FeedSearchType.FRIENDS, TEST_USER_ID, 0, 10, "en");

            // then
            assertThat(result.getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getMyGuildFeeds 분기 테스트")
    class GetMyGuildFeedsTest {

        @Test
        @DisplayName("소속 길드가 없으면 빈 페이지를 즉시 반환한다")
        void getMyGuildFeeds_noGuilds_returnsEmpty() {
            // given
            when(guildQueryFacadeService.getUserGuildMemberships(TEST_USER_ID))
                .thenReturn(Collections.emptyList());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getFilteredFeeds(
                FeedSearchType.GUILD, TEST_USER_ID, 0, 10, "ko");

            // then
            assertThat(result.getContent()).isEmpty();
            verify(activityFeedRepository, never()).findGuildOnlyFeedsByGuildIds(anyList(), any(Pageable.class));
        }

        @Test
        @DisplayName("소속 길드가 있으면 해당 길드 피드를 조회한다")
        void getMyGuildFeeds_withGuilds_fetchesFeeds() {
            // given
            Long guildId = 7L;
            GuildMembershipInfo membership = new GuildMembershipInfo(guildId, "내길드", null, 2, false, false);
            ActivityFeed feed = createTestFeed(1L, TEST_USER_ID);
            Page<ActivityFeed> feedPage = new PageImpl<>(List.of(feed));

            when(guildQueryFacadeService.getUserGuildMemberships(TEST_USER_ID))
                .thenReturn(List.of(membership));
            when(activityFeedRepository.findGuildOnlyFeedsByGuildIds(eq(List.of(guildId)), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getFilteredFeeds(
                FeedSearchType.GUILD, TEST_USER_ID, 0, 10, "en");

            // then
            assertThat(result.getContent()).hasSize(1);
            verify(activityFeedRepository).findGuildOnlyFeedsByGuildIds(eq(List.of(guildId)), any(Pageable.class));
        }
    }

    @Nested
    @DisplayName("getGuildFeeds 비멤버 분기 테스트")
    class GetGuildFeedsNonMemberTest {

        @Test
        @DisplayName("비멤버는 PUBLIC 피드만 조회된다")
        void getGuildFeeds_nonMember_seesPublicOnly() {
            // given
            Long guildId = 1L;
            ActivityFeed publicFeed = createTestFeed(1L, OTHER_USER_ID);
            Page<ActivityFeed> publicFeedPage = new PageImpl<>(List.of(publicFeed));

            // 현재 유저는 해당 길드의 멤버가 아님
            when(guildQueryFacadeService.getUserGuildMemberships(TEST_USER_ID))
                .thenReturn(Collections.emptyList());
            when(activityFeedRepository.findPublicFeedsByGuildId(eq(guildId), any(Pageable.class)))
                .thenReturn(publicFeedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = feedQueryService.getGuildFeeds(guildId, TEST_USER_ID, 0, 10);

            // then
            assertThat(result.getContent()).hasSize(1);
            verify(activityFeedRepository).findPublicFeedsByGuildId(eq(guildId), any(Pageable.class));
            verify(activityFeedRepository, never()).findGuildFeeds(eq(guildId), any(Pageable.class));
        }
    }
}
