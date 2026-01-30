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
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserTitleRepository;
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
import java.lang.reflect.Field;
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
    private UserTitleRepository userTitleRepository;

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

    private void setFeedId(ActivityFeed feed, Long id) {
        try {
            Field idField = ActivityFeed.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(feed, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setCommentId(FeedComment comment, Long id) {
        try {
            Field idField = FeedComment.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(comment, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setField(Object obj, String fieldName, Object value) {
        try {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(obj, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private CreateFeedRequest createTestFeedRequest() {
        CreateFeedRequest request = new CreateFeedRequest();
        setField(request, "activityType", ActivityType.MISSION_SHARED);
        setField(request, "title", "테스트 피드");
        setField(request, "description", "테스트 설명");
        return request;
    }

    private FeedCommentRequest createTestCommentRequest(String content) {
        FeedCommentRequest request = new FeedCommentRequest();
        setField(request, "content", content);
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
        setFeedId(feed, id);
        return feed;
    }

    private Users createTestUser(String userId) {
        Users user = Users.builder()
            .nickname("테스트유저")
            .email("test@example.com")
            .provider("GOOGLE")
            .picture("https://example.com/profile.jpg")
            .build();
        setField(user, "id", userId);
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
            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID)).thenReturn(Collections.emptyList());
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
            setCommentId(savedComment, 1L);

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
            setCommentId(comment, 1L);

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
            setCommentId(comment, commentId);

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
            setCommentId(comment, commentId);

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
            setCommentId(comment, commentId);

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
    @DisplayName("System Activity Feed 헬퍼 메서드 테스트")
    class SystemActivityFeedHelperTest {

        @Test
        @DisplayName("미션 참여 알림 피드를 생성한다")
        void notifyMissionJoined_success() {
            // given
            ActivityFeed savedFeed = createTestFeed(1L, TEST_USER_ID);
            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(activityFeedRepository.save(any(ActivityFeed.class))).thenReturn(savedFeed);

            // when
            activityFeedService.notifyMissionJoined(
                TEST_USER_ID, "테스트유저", "https://example.com/profile.jpg",
                5, 1L, "테스트 미션"
            );

            // then
            verify(activityFeedRepository).save(any(ActivityFeed.class));
        }

        @Test
        @DisplayName("레벨업 알림 피드를 생성한다")
        void notifyLevelUp_success() {
            // given
            ActivityFeed savedFeed = createTestFeed(1L, TEST_USER_ID);
            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(activityFeedRepository.save(any(ActivityFeed.class))).thenReturn(savedFeed);

            // when
            activityFeedService.notifyLevelUp(
                TEST_USER_ID, "테스트유저", "https://example.com/profile.jpg",
                10, 5000
            );

            // then
            verify(activityFeedRepository).save(any(ActivityFeed.class));
        }

        @Test
        @DisplayName("길드 가입 알림 피드를 생성한다")
        void notifyGuildJoined_success() {
            // given
            ActivityFeed savedFeed = createTestFeed(1L, TEST_USER_ID);
            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(activityFeedRepository.save(any(ActivityFeed.class))).thenReturn(savedFeed);

            // when
            activityFeedService.notifyGuildJoined(
                TEST_USER_ID, "테스트유저", "https://example.com/profile.jpg",
                5, 1L, "테스트 길드"
            );

            // then
            verify(activityFeedRepository).save(any(ActivityFeed.class));
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
            setFeedId(savedFeed, 1L);

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
    @DisplayName("추가 알림 피드 생성 테스트")
    class AdditionalNotifyTest {

        @Test
        @DisplayName("미션 완료 알림 피드를 생성한다")
        void notifyMissionCompleted_success() {
            // given
            ActivityFeed savedFeed = createTestFeed(1L, TEST_USER_ID);
            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(activityFeedRepository.save(any(ActivityFeed.class))).thenReturn(savedFeed);

            // when
            activityFeedService.notifyMissionCompleted(
                TEST_USER_ID, "테스트유저", "https://example.com/profile.jpg",
                5, 1L, "테스트 미션", 50
            );

            // then
            verify(activityFeedRepository).save(any(ActivityFeed.class));
        }

        @Test
        @DisplayName("미션 전체 완료 알림 피드를 생성한다")
        void notifyMissionFullCompleted_success() {
            // given
            ActivityFeed savedFeed = createTestFeed(1L, TEST_USER_ID);
            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(activityFeedRepository.save(any(ActivityFeed.class))).thenReturn(savedFeed);

            // when
            activityFeedService.notifyMissionFullCompleted(
                TEST_USER_ID, "테스트유저", "https://example.com/profile.jpg",
                5, 1L, "테스트 미션"
            );

            // then
            verify(activityFeedRepository).save(any(ActivityFeed.class));
        }

        @Test
        @DisplayName("업적 달성 알림 피드를 생성한다")
        void notifyAchievementUnlocked_success() {
            // given
            ActivityFeed savedFeed = createTestFeed(1L, TEST_USER_ID);
            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(activityFeedRepository.save(any(ActivityFeed.class))).thenReturn(savedFeed);

            // when
            activityFeedService.notifyAchievementUnlocked(
                TEST_USER_ID, "테스트유저", "https://example.com/profile.jpg",
                5, 1L, "첫 미션 완료", "BRONZE"
            );

            // then
            verify(activityFeedRepository).save(any(ActivityFeed.class));
        }

        @Test
        @DisplayName("칭호 획득 알림 피드를 생성한다")
        void notifyTitleAcquired_success() {
            // given
            ActivityFeed savedFeed = createTestFeed(1L, TEST_USER_ID);
            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(activityFeedRepository.save(any(ActivityFeed.class))).thenReturn(savedFeed);

            // when
            activityFeedService.notifyTitleAcquired(
                TEST_USER_ID, "테스트유저", "https://example.com/profile.jpg",
                5, 1L, "전설적인"
            );

            // then
            verify(activityFeedRepository).save(any(ActivityFeed.class));
        }

        @Test
        @DisplayName("길드 생성 알림 피드를 생성한다")
        void notifyGuildCreated_success() {
            // given
            ActivityFeed savedFeed = createTestFeed(1L, TEST_USER_ID);
            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(activityFeedRepository.save(any(ActivityFeed.class))).thenReturn(savedFeed);

            // when
            activityFeedService.notifyGuildCreated(
                TEST_USER_ID, "테스트유저", "https://example.com/profile.jpg",
                5, 1L, "테스트 길드"
            );

            // then
            verify(activityFeedRepository).save(any(ActivityFeed.class));
        }

        @Test
        @DisplayName("길드 레벨업 알림 피드를 생성한다")
        void notifyGuildLevelUp_success() {
            // given
            ActivityFeed savedFeed = createTestFeed(1L, TEST_USER_ID);
            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(activityFeedRepository.save(any(ActivityFeed.class))).thenReturn(savedFeed);

            // when
            activityFeedService.notifyGuildLevelUp(
                TEST_USER_ID, "테스트유저", "https://example.com/profile.jpg",
                5, 1L, "테스트 길드", 10
            );

            // then
            verify(activityFeedRepository).save(any(ActivityFeed.class));
        }

        @Test
        @DisplayName("친구 추가 알림 피드를 생성한다")
        void notifyFriendAdded_success() {
            // given
            ActivityFeed savedFeed = createTestFeed(1L, TEST_USER_ID);
            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(activityFeedRepository.save(any(ActivityFeed.class))).thenReturn(savedFeed);

            // when
            activityFeedService.notifyFriendAdded(
                TEST_USER_ID, "테스트유저", "https://example.com/profile.jpg",
                5, OTHER_USER_ID, "친구유저"
            );

            // then
            verify(activityFeedRepository).save(any(ActivityFeed.class));
        }

        @Test
        @DisplayName("연속 출석 알림 피드를 생성한다")
        void notifyAttendanceStreak_success() {
            // given
            ActivityFeed savedFeed = createTestFeed(1L, TEST_USER_ID);
            when(userTitleRepository.findEquippedTitlesByUserId(TEST_USER_ID)).thenReturn(Collections.emptyList());
            when(activityFeedRepository.save(any(ActivityFeed.class))).thenReturn(savedFeed);

            // when
            activityFeedService.notifyAttendanceStreak(
                TEST_USER_ID, "테스트유저", "https://example.com/profile.jpg",
                5, 7
            );

            // then
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
            setCommentId(comment, 1L);

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
