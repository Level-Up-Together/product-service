package io.pinkspider.leveluptogethermvp.userservice.feed.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.global.translation.TranslationService;
import io.pinkspider.global.translation.dto.TranslationInfo;
import io.pinkspider.global.translation.enums.SupportedLocale;
import io.pinkspider.leveluptogethermvp.adminservice.domain.entity.FeaturedFeed;
import io.pinkspider.leveluptogethermvp.adminservice.infrastructure.FeaturedFeedRepository;
import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.ActivityFeed;
import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.FeedComment;
import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.FeedLike;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.ActivityType;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.FeedVisibility;
import io.pinkspider.leveluptogethermvp.feedservice.infrastructure.ActivityFeedRepository;
import io.pinkspider.leveluptogethermvp.feedservice.infrastructure.FeedCommentRepository;
import io.pinkspider.leveluptogethermvp.feedservice.infrastructure.FeedLikeRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.userservice.feed.domain.dto.UserTitleInfo;
import io.pinkspider.leveluptogethermvp.supportservice.report.application.ReportService;
import io.pinkspider.leveluptogethermvp.supportservice.report.api.dto.ReportTargetType;
import io.pinkspider.leveluptogethermvp.userservice.feed.api.dto.ActivityFeedResponse;
import io.pinkspider.leveluptogethermvp.userservice.feed.api.dto.CreateFeedRequest;
import io.pinkspider.leveluptogethermvp.userservice.feed.api.dto.FeedCommentRequest;
import io.pinkspider.leveluptogethermvp.userservice.feed.api.dto.FeedCommentResponse;
import io.pinkspider.leveluptogethermvp.userservice.feed.api.dto.FeedLikeResponse;
import io.pinkspider.leveluptogethermvp.userservice.friend.application.FriendCacheService;
import io.pinkspider.leveluptogethermvp.userservice.friend.infrastructure.FriendshipRepository;
import io.pinkspider.leveluptogethermvp.userservice.profile.application.UserProfileCacheService;
import io.pinkspider.leveluptogethermvp.userservice.profile.domain.dto.UserProfileCache;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import static io.pinkspider.global.test.TestReflectionUtils.setId;
import io.pinkspider.global.test.TestReflectionUtils;
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
class ActivityFeedServiceTest {

    @Mock
    private ActivityFeedRepository activityFeedRepository;

    @Mock
    private FeedLikeRepository feedLikeRepository;

    @Mock
    private FeedCommentRepository feedCommentRepository;

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private FriendCacheService friendCacheService;

    @Mock
    private FeaturedFeedRepository featuredFeedRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserTitleInfoHelper userTitleInfoHelper;

    @Mock
    private UserProfileCacheService userProfileCacheService;

    @Mock
    private TranslationService translationService;

    @Mock
    private ReportService reportService;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ActivityFeedService activityFeedService;

    private static final String TEST_USER_ID = "test-user-123";
    private static final String OTHER_USER_ID = "other-user-456";

    private CreateFeedRequest createTestFeedRequest() {
        CreateFeedRequest request = new CreateFeedRequest();
        TestReflectionUtils.setField(request, "activityType", ActivityType.MISSION_SHARED);
        TestReflectionUtils.setField(request, "title", "테스트 피드");
        TestReflectionUtils.setField(request, "description", "테스트 설명");
        return request;
    }

    private FeedCommentRequest createTestCommentRequest(String content) {
        FeedCommentRequest request = new FeedCommentRequest();
        TestReflectionUtils.setField(request, "content", content);
        return request;
    }

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

    private Users createTestUser(String userId) {
        Users user = Users.builder()
            .nickname("테스트유저")
            .email("test@example.com")
            .provider("GOOGLE")
            .picture("https://example.com/profile.jpg")
            .build();
        TestReflectionUtils.setField(user, "id", userId);
        return user;
    }

    @Nested
    @DisplayName("createActivityFeed 테스트")
    class CreateActivityFeedTest {

        @Test
        @DisplayName("시스템 활동 피드를 생성한다")
        void createActivityFeed_success() {
            // given
            ActivityFeed savedFeed = createTestFeed(1L, TEST_USER_ID);
            when(activityFeedRepository.save(any(ActivityFeed.class))).thenReturn(savedFeed);

            // when
            ActivityFeed result = activityFeedService.createActivityFeed(
                TEST_USER_ID, "테스트유저", "https://example.com/profile.jpg",
                5, "테스트칭호", TitleRarity.RARE, "#FFFFFF",
                ActivityType.MISSION_COMPLETED, "미션 완료", "설명",
                "MISSION", 1L, "테스트미션",
                FeedVisibility.PUBLIC, null, null, null
            );

            // then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            verify(activityFeedRepository).save(any(ActivityFeed.class));
        }
    }

    @Nested
    @DisplayName("createFeed 테스트")
    class CreateFeedTest {

        @Test
        @DisplayName("사용자가 직접 피드를 생성한다")
        void createFeed_success() {
            // given
            Users user = createTestUser(TEST_USER_ID);
            ActivityFeed savedFeed = createTestFeed(1L, TEST_USER_ID);
            CreateFeedRequest request = createTestFeedRequest();

            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
            when(userTitleInfoHelper.getUserEquippedTitleInfo(TEST_USER_ID)).thenReturn(UserTitleInfo.empty());
            when(activityFeedRepository.save(any(ActivityFeed.class))).thenReturn(savedFeed);

            // when
            ActivityFeedResponse result = activityFeedService.createFeed(TEST_USER_ID, request);

            // then
            assertThat(result).isNotNull();
            verify(userRepository).findById(TEST_USER_ID);
            verify(activityFeedRepository).save(any(ActivityFeed.class));
        }

        @Test
        @DisplayName("존재하지 않는 사용자가 피드 생성 시 예외 발생")
        void createFeed_userNotFound() {
            // given
            CreateFeedRequest request = createTestFeedRequest();

            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> activityFeedService.createFeed(TEST_USER_ID, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");
        }
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

            when(activityFeedRepository.findPublicFeedsInTimeRange(any(), any(), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());

            // when
            Page<ActivityFeedResponse> result = activityFeedService.getPublicFeeds(TEST_USER_ID, 0, 10);

            // then
            assertThat(result.getContent()).hasSize(1);
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

            when(friendCacheService.getFriendIds(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(activityFeedRepository.findByUserId(eq(TEST_USER_ID), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());

            // when
            Page<ActivityFeedResponse> result = activityFeedService.getTimelineFeeds(TEST_USER_ID, 0, 10);

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

            when(friendCacheService.getFriendIds(TEST_USER_ID)).thenReturn(friendIds);
            when(activityFeedRepository.findTimelineFeeds(eq(TEST_USER_ID), eq(friendIds), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());

            // when
            Page<ActivityFeedResponse> result = activityFeedService.getTimelineFeeds(TEST_USER_ID, 0, 10);

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
            when(friendshipRepository.areFriends(TEST_USER_ID, OTHER_USER_ID)).thenReturn(false);

            // when
            Page<ActivityFeedResponse> result = activityFeedService.getUserFeeds(OTHER_USER_ID, TEST_USER_ID, 0, 10);

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

            when(activityFeedRepository.findGuildFeeds(eq(guildId), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());

            // when
            Page<ActivityFeedResponse> result = activityFeedService.getGuildFeeds(guildId, TEST_USER_ID, 0, 10);

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
            ActivityFeedResponse result = activityFeedService.getFeed(feedId, TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("존재하지 않는 피드 조회 시 예외 발생")
        void getFeed_notFound() {
            // given
            when(activityFeedRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> activityFeedService.getFeed(999L, TEST_USER_ID))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("피드를 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("toggleLike 테스트")
    class ToggleLikeTest {

        @Test
        @DisplayName("좋아요를 추가한다")
        void toggleLike_addLike() {
            // given
            Long feedId = 1L;
            ActivityFeed feed = createTestFeed(feedId, OTHER_USER_ID);

            when(activityFeedRepository.findById(feedId)).thenReturn(Optional.of(feed));
            when(feedLikeRepository.findByFeedIdAndUserId(feedId, TEST_USER_ID)).thenReturn(Optional.empty());
            when(feedLikeRepository.save(any(FeedLike.class))).thenAnswer(i -> i.getArgument(0));
            when(activityFeedRepository.save(any(ActivityFeed.class))).thenReturn(feed);

            // when
            FeedLikeResponse result = activityFeedService.toggleLike(feedId, TEST_USER_ID);

            // then
            assertThat(result.isLiked()).isTrue();
            assertThat(result.likeCount()).isEqualTo(1);
            verify(feedLikeRepository).save(any(FeedLike.class));
        }

        @Test
        @DisplayName("좋아요를 취소한다")
        void toggleLike_removeLike() {
            // given
            Long feedId = 1L;
            ActivityFeed feed = createTestFeed(feedId, OTHER_USER_ID);
            feed.incrementLikeCount(); // 이미 좋아요 상태
            FeedLike existingLike = FeedLike.builder()
                .feed(feed)
                .userId(TEST_USER_ID)
                .build();

            when(activityFeedRepository.findById(feedId)).thenReturn(Optional.of(feed));
            when(feedLikeRepository.findByFeedIdAndUserId(feedId, TEST_USER_ID)).thenReturn(Optional.of(existingLike));
            when(activityFeedRepository.save(any(ActivityFeed.class))).thenReturn(feed);

            // when
            FeedLikeResponse result = activityFeedService.toggleLike(feedId, TEST_USER_ID);

            // then
            assertThat(result.isLiked()).isFalse();
            verify(feedLikeRepository).delete(existingLike);
        }

        @Test
        @DisplayName("자신의 피드에 좋아요하면 예외 발생")
        void toggleLike_ownFeed_throwsException() {
            // given
            Long feedId = 1L;
            ActivityFeed feed = createTestFeed(feedId, TEST_USER_ID);

            when(activityFeedRepository.findById(feedId)).thenReturn(Optional.of(feed));

            // when & then
            assertThatThrownBy(() -> activityFeedService.toggleLike(feedId, TEST_USER_ID))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("자신의 피드에는 좋아요를 할 수 없습니다");
        }
    }

    @Nested
    @DisplayName("addComment 테스트")
    class AddCommentTest {

        @Test
        @DisplayName("댓글을 추가한다")
        void addComment_success() {
            // given
            Long feedId = 1L;
            ActivityFeed feed = createTestFeed(feedId, OTHER_USER_ID);
            UserProfileCache userProfile = new UserProfileCache(
                TEST_USER_ID, "테스트유저", "https://example.com/profile.jpg",
                5, null, null, null
            );
            FeedCommentRequest request = createTestCommentRequest("테스트 댓글");

            FeedComment savedComment = FeedComment.builder()
                .feed(feed)
                .userId(TEST_USER_ID)
                .userNickname(userProfile.nickname())
                .userLevel(userProfile.level())
                .content("테스트 댓글")
                .isDeleted(false)
                .build();
            setId(savedComment, 1L);

            when(activityFeedRepository.findById(feedId)).thenReturn(Optional.of(feed));
            when(userProfileCacheService.getUserProfile(TEST_USER_ID)).thenReturn(userProfile);
            when(feedCommentRepository.save(any(FeedComment.class))).thenReturn(savedComment);
            when(activityFeedRepository.save(any(ActivityFeed.class))).thenReturn(feed);

            // when
            FeedCommentResponse result = activityFeedService.addComment(feedId, TEST_USER_ID, request);

            // then
            assertThat(result).isNotNull();
            assertThat(feed.getCommentCount()).isEqualTo(1);
            verify(feedCommentRepository).save(any(FeedComment.class));
        }

        @Test
        @DisplayName("존재하지 않는 피드에 댓글 추가 시 예외 발생")
        void addComment_feedNotFound() {
            // given
            FeedCommentRequest request = createTestCommentRequest("테스트 댓글");

            when(activityFeedRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> activityFeedService.addComment(999L, TEST_USER_ID, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("피드를 찾을 수 없습니다");
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
            when(feedCommentRepository.findByFeedId(eq(feedId), any(Pageable.class))).thenReturn(commentPage);

            // when
            Page<FeedCommentResponse> result = activityFeedService.getComments(feedId, TEST_USER_ID, 0, 10);

            // then
            assertThat(result.getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("deleteComment 테스트")
    class DeleteCommentTest {

        @Test
        @DisplayName("댓글을 삭제한다")
        void deleteComment_success() {
            // given
            Long feedId = 1L;
            Long commentId = 1L;
            ActivityFeed feed = createTestFeed(feedId, OTHER_USER_ID);
            feed.incrementCommentCount();

            FeedComment comment = FeedComment.builder()
                .feed(feed)
                .userId(TEST_USER_ID)
                .content("테스트 댓글")
                .isDeleted(false)
                .build();
            setId(comment, commentId);

            when(feedCommentRepository.findById(commentId)).thenReturn(Optional.of(comment));
            when(feedCommentRepository.save(any(FeedComment.class))).thenReturn(comment);
            when(activityFeedRepository.save(any(ActivityFeed.class))).thenReturn(feed);

            // when
            activityFeedService.deleteComment(feedId, commentId, TEST_USER_ID);

            // then
            assertThat(comment.getIsDeleted()).isTrue();
            assertThat(feed.getCommentCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("다른 사용자의 댓글 삭제 시 예외 발생")
        void deleteComment_notOwner_throwsException() {
            // given
            Long feedId = 1L;
            Long commentId = 1L;
            ActivityFeed feed = createTestFeed(feedId, OTHER_USER_ID);

            FeedComment comment = FeedComment.builder()
                .feed(feed)
                .userId(OTHER_USER_ID) // 다른 사용자의 댓글
                .content("테스트 댓글")
                .isDeleted(false)
                .build();
            setId(comment, commentId);

            when(feedCommentRepository.findById(commentId)).thenReturn(Optional.of(comment));

            // when & then
            assertThatThrownBy(() -> activityFeedService.deleteComment(feedId, commentId, TEST_USER_ID))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("본인의 댓글만 삭제할 수 있습니다");
        }

        @Test
        @DisplayName("잘못된 피드의 댓글 삭제 시 예외 발생")
        void deleteComment_wrongFeed_throwsException() {
            // given
            Long feedId = 1L;
            Long wrongFeedId = 999L;
            Long commentId = 1L;
            ActivityFeed feed = createTestFeed(feedId, OTHER_USER_ID);

            FeedComment comment = FeedComment.builder()
                .feed(feed)
                .userId(TEST_USER_ID)
                .content("테스트 댓글")
                .isDeleted(false)
                .build();
            setId(comment, commentId);

            when(feedCommentRepository.findById(commentId)).thenReturn(Optional.of(comment));

            // when & then
            assertThatThrownBy(() -> activityFeedService.deleteComment(wrongFeedId, commentId, TEST_USER_ID))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("해당 피드의 댓글이 아닙니다");
        }
    }

    @Nested
    @DisplayName("deleteFeed 테스트")
    class DeleteFeedTest {

        @Test
        @DisplayName("피드를 삭제한다")
        void deleteFeed_success() {
            // given
            Long feedId = 1L;
            ActivityFeed feed = createTestFeed(feedId, TEST_USER_ID);

            when(activityFeedRepository.findById(feedId)).thenReturn(Optional.of(feed));

            // when
            activityFeedService.deleteFeed(feedId, TEST_USER_ID);

            // then
            verify(activityFeedRepository).delete(feed);
        }

        @Test
        @DisplayName("다른 사용자의 피드 삭제 시 예외 발생")
        void deleteFeed_notOwner_throwsException() {
            // given
            Long feedId = 1L;
            ActivityFeed feed = createTestFeed(feedId, OTHER_USER_ID);

            when(activityFeedRepository.findById(feedId)).thenReturn(Optional.of(feed));

            // when & then
            assertThatThrownBy(() -> activityFeedService.deleteFeed(feedId, TEST_USER_ID))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("본인의 피드만 삭제할 수 있습니다");
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
            Page<ActivityFeedResponse> result = activityFeedService.searchFeeds(keyword, TEST_USER_ID, 0, 10);

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
            Page<ActivityFeedResponse> result = activityFeedService.getFeedsByCategory(category, TEST_USER_ID, 0, 10);

            // then
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("존재하지 않는 카테고리 조회 시 빈 결과 반환")
        void getFeedsByCategory_emptyCategory() {
            // given
            String invalidCategory = "INVALID_CATEGORY";

            // when
            Page<ActivityFeedResponse> result = activityFeedService.getFeedsByCategory(invalidCategory, TEST_USER_ID, 0, 10);

            // then
            assertThat(result.getContent()).isEmpty();
            verify(activityFeedRepository, never()).findByCategoryTypes(anyList(), any(Pageable.class));
        }
    }

    @Nested
    @DisplayName("deleteFeedsByReferenceId 테스트")
    class DeleteFeedsByReferenceIdTest {

        @Test
        @DisplayName("referenceId로 피드를 삭제한다")
        void deleteFeedsByReferenceId_success() {
            // given
            Long referenceId = 1L;
            String referenceType = "MISSION";

            when(activityFeedRepository.deleteByReferenceIdAndReferenceType(referenceId, referenceType))
                .thenReturn(3);

            // when
            int deletedCount = activityFeedService.deleteFeedsByReferenceId(referenceId, referenceType);

            // then
            assertThat(deletedCount).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("deleteFeedsByMissionId 테스트")
    class DeleteFeedsByMissionIdTest {

        @Test
        @DisplayName("missionId로 피드를 삭제한다")
        void deleteFeedsByMissionId_success() {
            // given
            Long missionId = 1L;

            when(activityFeedRepository.deleteByMissionId(missionId)).thenReturn(5);

            // when
            int deletedCount = activityFeedService.deleteFeedsByMissionId(missionId);

            // then
            assertThat(deletedCount).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("createMissionSharedFeed 테스트")
    class CreateMissionSharedFeedTest {

        @Test
        @DisplayName("미션 공유 피드를 생성한다")
        void createMissionSharedFeed_success() {
            // given
            ActivityFeed savedFeed = ActivityFeed.builder()
                .userId(TEST_USER_ID)
                .activityType(ActivityType.MISSION_SHARED)
                .visibility(FeedVisibility.PUBLIC)
                .likeCount(0)
                .commentCount(0)
                .build();
            setId(savedFeed, 1L);

            when(activityFeedRepository.save(any(ActivityFeed.class))).thenReturn(savedFeed);

            // when
            ActivityFeed result = activityFeedService.createMissionSharedFeed(
                TEST_USER_ID, "테스트유저", "https://example.com/profile.jpg",
                5, "테스트칭호", TitleRarity.RARE, "#FFFFFF",
                1L, 2L, "테스트미션", "미션 설명", 1L,
                "노트 내용", "https://example.com/image.jpg",
                30, 100
            );

            // then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            verify(activityFeedRepository).save(any(ActivityFeed.class));
        }
    }

    @Nested
    @DisplayName("deleteFeedById 테스트")
    class DeleteFeedByIdTest {

        @Test
        @DisplayName("ID로 피드를 삭제한다")
        void deleteFeedById_success() {
            // given
            Long feedId = 1L;

            // when
            activityFeedService.deleteFeedById(feedId);

            // then
            verify(activityFeedRepository).deleteById(feedId);
        }
    }

    @Nested
    @DisplayName("updateFeedImageUrl 테스트")
    class UpdateFeedImageUrlTest {

        @Test
        @DisplayName("피드 이미지 URL을 업데이트한다")
        void updateFeedImageUrl_success() {
            // given
            Long feedId = 1L;
            String newImageUrl = "https://example.com/new-image.jpg";
            ActivityFeed feed = createTestFeed(feedId, TEST_USER_ID);

            when(activityFeedRepository.findById(feedId)).thenReturn(Optional.of(feed));
            when(activityFeedRepository.save(any(ActivityFeed.class))).thenReturn(feed);

            // when
            activityFeedService.updateFeedImageUrl(feedId, newImageUrl);

            // then
            assertThat(feed.getImageUrl()).isEqualTo(newImageUrl);
            verify(activityFeedRepository).save(feed);
        }

        @Test
        @DisplayName("존재하지 않는 피드는 업데이트하지 않는다")
        void updateFeedImageUrl_feedNotFound_doesNothing() {
            // given
            Long feedId = 999L;
            String newImageUrl = "https://example.com/new-image.jpg";

            when(activityFeedRepository.findById(feedId)).thenReturn(Optional.empty());

            // when
            activityFeedService.updateFeedImageUrl(feedId, newImageUrl);

            // then
            verify(activityFeedRepository, never()).save(any(ActivityFeed.class));
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

            when(activityFeedRepository.findPublicFeedsInTimeRange(any(), any(), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = activityFeedService.getPublicFeeds(TEST_USER_ID, 0, 10, acceptLanguage);

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

            when(friendCacheService.getFriendIds(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(activityFeedRepository.findByUserId(eq(TEST_USER_ID), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = activityFeedService.getTimelineFeeds(TEST_USER_ID, 0, 10, acceptLanguage);

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
            when(friendshipRepository.areFriends(TEST_USER_ID, OTHER_USER_ID)).thenReturn(false);
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = activityFeedService.getUserFeeds(OTHER_USER_ID, TEST_USER_ID, 0, 10, acceptLanguage);

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

            when(activityFeedRepository.findGuildFeeds(eq(guildId), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = activityFeedService.getGuildFeeds(guildId, TEST_USER_ID, 0, 10, acceptLanguage);

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
            Page<ActivityFeedResponse> result = activityFeedService.getFeedsByCategory(category, TEST_USER_ID, 0, 10, acceptLanguage);

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
            Page<ActivityFeedResponse> result = activityFeedService.searchFeeds(keyword, TEST_USER_ID, 0, 10, acceptLanguage);

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
            Page<ActivityFeedResponse> result = activityFeedService.searchFeedsByCategory(keyword, category, TEST_USER_ID, 0, 10, acceptLanguage);

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
            ActivityFeedResponse result = activityFeedService.getFeed(feedId, TEST_USER_ID, acceptLanguage);

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

            when(feedCommentRepository.findByFeedId(eq(feedId), any(Pageable.class))).thenReturn(commentPage);
            when(userProfileCacheService.getUserProfile(TEST_USER_ID))
                .thenReturn(new UserProfileCache(TEST_USER_ID, "테스트유저", null, 5, null, null, null));
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<FeedCommentResponse> result = activityFeedService.getComments(feedId, TEST_USER_ID, 0, 10, acceptLanguage);

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

            when(featuredFeedRepository.findActiveFeaturedFeeds(eq(categoryId), any())).thenReturn(Collections.emptyList());
            when(activityFeedRepository.findPublicFeedsByCategoryIdInTimeRange(eq(categoryId), any(), any(), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = activityFeedService.getPublicFeedsByCategory(categoryId, TEST_USER_ID, 0, 10);

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

            FeaturedFeed featured = FeaturedFeed.builder()
                .categoryId(categoryId)
                .feedId(1L)
                .displayOrder(1)
                .build();

            when(featuredFeedRepository.findActiveFeaturedFeeds(eq(categoryId), any()))
                .thenReturn(List.of(featured));
            when(activityFeedRepository.findByIdIn(List.of(1L)))
                .thenReturn(List.of(featuredFeed));
            when(activityFeedRepository.findPublicFeedsByCategoryIdInTimeRange(eq(categoryId), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(normalFeed)));
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = activityFeedService.getPublicFeedsByCategory(categoryId, TEST_USER_ID, 0, 10);

            // then
            assertThat(result.getContent()).hasSize(2);
        }

        @Test
        @DisplayName("중복된 피드는 제거된다")
        void getPublicFeedsByCategory_removeDuplicates() {
            // given
            Long categoryId = 1L;
            ActivityFeed feed = createTestFeed(1L, TEST_USER_ID);

            FeaturedFeed featured = FeaturedFeed.builder()
                .categoryId(categoryId)
                .feedId(1L)
                .displayOrder(1)
                .build();

            when(featuredFeedRepository.findActiveFeaturedFeeds(eq(categoryId), any()))
                .thenReturn(List.of(featured));
            when(activityFeedRepository.findByIdIn(List.of(1L)))
                .thenReturn(List.of(feed));
            when(activityFeedRepository.findPublicFeedsByCategoryIdInTimeRange(eq(categoryId), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(feed))); // 동일한 피드
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = activityFeedService.getPublicFeedsByCategory(categoryId, TEST_USER_ID, 0, 10);

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

            when(featuredFeedRepository.findActiveFeaturedFeeds(eq(categoryId), any())).thenReturn(Collections.emptyList());
            when(activityFeedRepository.findPublicFeedsByCategoryIdInTimeRange(eq(categoryId), any(), any(), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = activityFeedService.getPublicFeedsByCategory(categoryId, TEST_USER_ID, 0, 10, acceptLanguage);

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
            when(friendshipRepository.areFriends(TEST_USER_ID, TEST_USER_ID)).thenReturn(false);
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = activityFeedService.getUserFeeds(TEST_USER_ID, TEST_USER_ID, 0, 10);

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
            when(friendshipRepository.areFriends(TEST_USER_ID, OTHER_USER_ID)).thenReturn(true);
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = activityFeedService.getUserFeeds(OTHER_USER_ID, TEST_USER_ID, 0, 10);

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
            when(friendshipRepository.areFriends(TEST_USER_ID, OTHER_USER_ID)).thenReturn(false);
            when(reportService.isUnderReviewBatch(any(), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = activityFeedService.getUserFeeds(OTHER_USER_ID, TEST_USER_ID, 0, 10);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0)).isNull(); // FRIENDS 가시성이므로 null
        }
    }

    @Nested
    @DisplayName("자신의 피드 댓글 이벤트 테스트")
    class CommentEventTest {

        @Test
        @DisplayName("자신의 글에 자신이 댓글을 달면 이벤트가 발행되지 않는다")
        void addComment_selfComment_noEvent() {
            // given
            Long feedId = 1L;
            ActivityFeed feed = createTestFeed(feedId, TEST_USER_ID); // 작성자가 본인
            UserProfileCache userProfile = new UserProfileCache(
                TEST_USER_ID, "테스트유저", "https://example.com/profile.jpg",
                5, null, null, null
            );
            FeedCommentRequest request = createTestCommentRequest("테스트 댓글");

            FeedComment savedComment = FeedComment.builder()
                .feed(feed)
                .userId(TEST_USER_ID)
                .userNickname(userProfile.nickname())
                .userLevel(userProfile.level())
                .content("테스트 댓글")
                .isDeleted(false)
                .build();
            setId(savedComment, 1L);

            when(activityFeedRepository.findById(feedId)).thenReturn(Optional.of(feed));
            when(userProfileCacheService.getUserProfile(TEST_USER_ID)).thenReturn(userProfile);
            when(feedCommentRepository.save(any(FeedComment.class))).thenReturn(savedComment);
            when(activityFeedRepository.save(any(ActivityFeed.class))).thenReturn(feed);

            // when
            activityFeedService.addComment(feedId, TEST_USER_ID, request);

            // then
            verify(eventPublisher, never()).publishEvent(any());
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
            ActivityFeedResponse result = activityFeedService.getFeed(feedId, TEST_USER_ID);

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
            ActivityFeedResponse result = activityFeedService.getFeed(feedId, TEST_USER_ID);

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

            when(activityFeedRepository.findPublicFeedsInTimeRange(any(), any(), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());

            Map<String, Boolean> underReviewMap = new HashMap<>();
            underReviewMap.put("1", true);
            when(reportService.isUnderReviewBatch(eq(ReportTargetType.FEED), anyList())).thenReturn(underReviewMap);

            // when
            Page<ActivityFeedResponse> result = activityFeedService.getPublicFeeds(TEST_USER_ID, 0, 10);

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

            when(friendCacheService.getFriendIds(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(activityFeedRepository.findByUserId(eq(TEST_USER_ID), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());

            Map<String, Boolean> underReviewMap = new HashMap<>();
            underReviewMap.put("1", false);
            when(reportService.isUnderReviewBatch(eq(ReportTargetType.FEED), anyList())).thenReturn(underReviewMap);

            // when
            Page<ActivityFeedResponse> result = activityFeedService.getTimelineFeeds(TEST_USER_ID, 0, 10);

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
            when(friendshipRepository.areFriends(TEST_USER_ID, OTHER_USER_ID)).thenReturn(false);

            Map<String, Boolean> underReviewMap = new HashMap<>();
            underReviewMap.put("1", true);
            when(reportService.isUnderReviewBatch(eq(ReportTargetType.FEED), anyList())).thenReturn(underReviewMap);

            // when
            Page<ActivityFeedResponse> result = activityFeedService.getUserFeeds(OTHER_USER_ID, TEST_USER_ID, 0, 10);

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

            when(activityFeedRepository.findGuildFeeds(eq(guildId), any(Pageable.class)))
                .thenReturn(feedPage);
            when(feedLikeRepository.findLikedFeedIds(eq(TEST_USER_ID), anyList()))
                .thenReturn(Collections.emptyList());

            Map<String, Boolean> underReviewMap = new HashMap<>();
            underReviewMap.put("1", true);
            when(reportService.isUnderReviewBatch(eq(ReportTargetType.FEED), anyList())).thenReturn(underReviewMap);

            // when
            Page<ActivityFeedResponse> result = activityFeedService.getGuildFeeds(guildId, TEST_USER_ID, 0, 10);

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
            Page<ActivityFeedResponse> result = activityFeedService.searchFeeds(keyword, TEST_USER_ID, 0, 10);

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

            when(activityFeedRepository.findPublicFeedsInTimeRange(any(), any(), any(Pageable.class)))
                .thenReturn(emptyPage);
            when(reportService.isUnderReviewBatch(eq(ReportTargetType.FEED), anyList()))
                .thenReturn(Collections.emptyMap());

            // when
            Page<ActivityFeedResponse> result = activityFeedService.getPublicFeeds(TEST_USER_ID, 0, 10);

            // then
            assertThat(result.getContent()).isEmpty();
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
            when(feedCommentRepository.findByFeedId(eq(feedId), any(Pageable.class))).thenReturn(commentPage);

            Map<String, Boolean> underReviewMap = new HashMap<>();
            underReviewMap.put("1", true);
            when(reportService.isUnderReviewBatch(eq(ReportTargetType.FEED_COMMENT), anyList())).thenReturn(underReviewMap);

            // when
            Page<FeedCommentResponse> result = activityFeedService.getComments(feedId, TEST_USER_ID, 0, 10);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getIsUnderReview()).isTrue();
            verify(reportService).isUnderReviewBatch(eq(ReportTargetType.FEED_COMMENT), anyList());
        }
    }
}
