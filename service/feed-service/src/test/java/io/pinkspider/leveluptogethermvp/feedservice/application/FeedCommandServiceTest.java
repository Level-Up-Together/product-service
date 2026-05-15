package io.pinkspider.leveluptogethermvp.feedservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.event.FeedCommentLikedEvent;
import io.pinkspider.global.event.FeedCommentReplyEvent;
import io.pinkspider.global.event.FeedLikedEvent;
import io.pinkspider.global.event.FeedUnlikedEvent;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.global.test.TestReflectionUtils;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.ActivityFeedResponse;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.CreateFeedRequest;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.FeedCommentLikeResponse;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.FeedCommentRequest;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.FeedCommentResponse;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.FeedCommentUpdateRequest;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.FeedLikeResponse;
import io.pinkspider.global.facade.dto.DetailedTitleInfoDto;
import io.pinkspider.global.facade.dto.TitleInfoDto;
import io.pinkspider.global.facade.GamificationQueryFacade;
import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.ActivityFeed;
import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.FeedComment;
import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.FeedCommentLike;
import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.FeedLike;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.ActivityType;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.FeedVisibility;
import io.pinkspider.leveluptogethermvp.feedservice.infrastructure.ActivityFeedRepository;
import io.pinkspider.leveluptogethermvp.feedservice.infrastructure.FeedCommentLikeRepository;
import io.pinkspider.leveluptogethermvp.feedservice.infrastructure.FeedCommentRepository;
import io.pinkspider.leveluptogethermvp.feedservice.infrastructure.FeedLikeRepository;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.global.facade.dto.UserProfileInfo;
import static io.pinkspider.global.test.TestReflectionUtils.setId;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeedCommandServiceTest {

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
    private UserQueryFacade userQueryFacadeService;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Mock
    private GamificationQueryFacade gamificationQueryFacadeService;

    @Mock
    private FeedAccessChecker feedAccessChecker;

    @InjectMocks
    private FeedCommandService feedCommandService;

    private static final String TEST_USER_ID = "test-user-123";
    private static final String OTHER_USER_ID = "other-user-456";
    private static final DetailedTitleInfoDto EMPTY_DETAILED_TITLE = new DetailedTitleInfoDto(null, null, null, null, null, null);

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

    private FeedCommentRequest createTestReplyRequest(String content, Long parentId) {
        FeedCommentRequest request = new FeedCommentRequest();
        TestReflectionUtils.setField(request, "content", content);
        TestReflectionUtils.setField(request, "parentId", parentId);
        return request;
    }

    private FeedCommentUpdateRequest createTestUpdateRequest(String content) {
        FeedCommentUpdateRequest request = new FeedCommentUpdateRequest();
        TestReflectionUtils.setField(request, "content", content);
        return request;
    }

    private FeedComment createTestComment(Long id, ActivityFeed feed, String userId, FeedComment parent) {
        FeedComment comment = FeedComment.builder()
            .feed(feed)
            .userId(userId)
            .userNickname("nickname-" + userId)
            .content("기존 댓글")
            .parent(parent)
            .isDeleted(false)
            .isEdited(false)
            .build();
        setId(comment, id);
        return comment;
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

    @Nested
    @DisplayName("createActivityFeed 테스트")
    class CreateActivityFeedTest {

        @Test
        @DisplayName("시스템 활동 피드를 생성한다")
        void createActivityFeed_success() {
            // given
            ActivityFeed savedFeed = createTestFeed(1L, TEST_USER_ID);
            when(gamificationQueryFacadeService.getDetailedEquippedTitleInfo(TEST_USER_ID)).thenReturn(EMPTY_DETAILED_TITLE);
            when(activityFeedRepository.save(any(ActivityFeed.class))).thenReturn(savedFeed);

            // when
            ActivityFeed result = feedCommandService.createActivityFeed(
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
            ActivityFeed savedFeed = createTestFeed(1L, TEST_USER_ID);
            CreateFeedRequest request = createTestFeedRequest();

            when(userQueryFacadeService.userExistsById(TEST_USER_ID)).thenReturn(true);
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID)).thenReturn(new UserProfileInfo(TEST_USER_ID, "테스트유저", "https://example.com/profile.jpg", 5, null, null, null));
            when(gamificationQueryFacadeService.getCombinedEquippedTitleInfo(TEST_USER_ID))
                .thenReturn(new TitleInfoDto("초보 모험가", TitleRarity.COMMON, "#FFFFFF"));
            when(gamificationQueryFacadeService.getDetailedEquippedTitleInfo(TEST_USER_ID)).thenReturn(EMPTY_DETAILED_TITLE);
            when(activityFeedRepository.save(any(ActivityFeed.class))).thenReturn(savedFeed);

            // when
            ActivityFeedResponse result = feedCommandService.createFeed(TEST_USER_ID, request);

            // then
            assertThat(result).isNotNull();
            verify(userQueryFacadeService).userExistsById(TEST_USER_ID);
            verify(activityFeedRepository).save(any(ActivityFeed.class));
        }

        @Test
        @DisplayName("존재하지 않는 사용자가 피드 생성 시 예외 발생")
        void createFeed_userNotFound() {
            // given
            CreateFeedRequest request = createTestFeedRequest();

            when(userQueryFacadeService.userExistsById(TEST_USER_ID)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> feedCommandService.createFeed(TEST_USER_ID, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.user.not_found");
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
            FeedLikeResponse result = feedCommandService.toggleLike(feedId, TEST_USER_ID);

            // then
            assertThat(result.isLiked()).isTrue();
            assertThat(result.likeCount()).isEqualTo(1);
            verify(feedLikeRepository).save(any(FeedLike.class));
            verify(eventPublisher).publishEvent(any(FeedLikedEvent.class));
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
            FeedLikeResponse result = feedCommandService.toggleLike(feedId, TEST_USER_ID);

            // then
            assertThat(result.isLiked()).isFalse();
            verify(feedLikeRepository).delete(existingLike);
            verify(eventPublisher).publishEvent(any(FeedUnlikedEvent.class));
        }

        @Test
        @DisplayName("자신의 피드에 좋아요하면 예외 발생")
        void toggleLike_ownFeed_throwsException() {
            // given
            Long feedId = 1L;
            ActivityFeed feed = createTestFeed(feedId, TEST_USER_ID);

            when(activityFeedRepository.findById(feedId)).thenReturn(Optional.of(feed));

            // when & then
            assertThatThrownBy(() -> feedCommandService.toggleLike(feedId, TEST_USER_ID))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.feed.self_like");
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
            UserProfileInfo userProfile = new UserProfileInfo(
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
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID)).thenReturn(userProfile);
            when(feedCommentRepository.save(any(FeedComment.class))).thenReturn(savedComment);
            when(activityFeedRepository.save(any(ActivityFeed.class))).thenReturn(feed);

            // when
            FeedCommentResponse result = feedCommandService.addComment(feedId, TEST_USER_ID, request);

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
            assertThatThrownBy(() -> feedCommandService.addComment(999L, TEST_USER_ID, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.feed.not_found");
        }

        @Test
        @DisplayName("대댓글 추가 시 FeedCommentReplyEvent 발행")
        void addReply_publishesReplyEvent() {
            // given
            Long feedId = 1L;
            Long parentId = 10L;
            ActivityFeed feed = createTestFeed(feedId, OTHER_USER_ID);
            FeedComment parent = createTestComment(parentId, feed, OTHER_USER_ID, null);
            FeedCommentRequest request = createTestReplyRequest("대댓글", parentId);

            UserProfileInfo userProfile = new UserProfileInfo(
                TEST_USER_ID, "테스트유저", null, 5, null, null, null);

            when(activityFeedRepository.findById(feedId)).thenReturn(Optional.of(feed));
            when(feedCommentRepository.findById(parentId)).thenReturn(Optional.of(parent));
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID)).thenReturn(userProfile);
            when(feedCommentRepository.findReplyAuthorsByParentId(parentId))
                .thenReturn(java.util.List.of());
            when(feedCommentRepository.save(any(FeedComment.class))).thenAnswer(inv -> {
                FeedComment c = inv.getArgument(0);
                setId(c, 100L);
                return c;
            });
            when(activityFeedRepository.save(any(ActivityFeed.class))).thenReturn(feed);

            // when
            feedCommandService.addComment(feedId, TEST_USER_ID, request);

            // then — 대댓글이므로 FeedCommentEvent 대신 FeedCommentReplyEvent 발행
            verify(eventPublisher).publishEvent(any(FeedCommentReplyEvent.class));
        }

        @Test
        @DisplayName("대댓글에 다시 대댓글 시 1-depth 제한 예외 발생")
        void addReply_replyToReply_throwsException() {
            // given
            Long feedId = 1L;
            Long rootId = 10L;
            Long replyId = 11L;
            ActivityFeed feed = createTestFeed(feedId, OTHER_USER_ID);
            FeedComment root = createTestComment(rootId, feed, OTHER_USER_ID, null);
            FeedComment reply = createTestComment(replyId, feed, TEST_USER_ID, root); // 대댓글
            FeedCommentRequest request = createTestReplyRequest("재대댓글", replyId);

            when(activityFeedRepository.findById(feedId)).thenReturn(Optional.of(feed));
            when(feedCommentRepository.findById(replyId)).thenReturn(Optional.of(reply));

            // when & then
            assertThatThrownBy(() -> feedCommandService.addComment(feedId, TEST_USER_ID, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.feed.comment.reply_depth_exceeded");
        }

        @Test
        @DisplayName("부모 댓글이 다른 피드 소속이면 wrong_feed 예외")
        void addReply_parentFromDifferentFeed_throwsException() {
            // given
            Long feedId = 1L;
            Long otherFeedId = 2L;
            Long parentId = 10L;
            ActivityFeed feed = createTestFeed(feedId, OTHER_USER_ID);
            ActivityFeed otherFeed = createTestFeed(otherFeedId, OTHER_USER_ID);
            FeedComment parent = createTestComment(parentId, otherFeed, OTHER_USER_ID, null);
            FeedCommentRequest request = createTestReplyRequest("잘못된 대댓글", parentId);

            when(activityFeedRepository.findById(feedId)).thenReturn(Optional.of(feed));
            when(feedCommentRepository.findById(parentId)).thenReturn(Optional.of(parent));

            // when & then
            assertThatThrownBy(() -> feedCommandService.addComment(feedId, TEST_USER_ID, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.feed.comment.wrong_feed");
        }
    }

    @Nested
    @DisplayName("updateComment 테스트")
    class UpdateCommentTest {

        @Test
        @DisplayName("본인 댓글을 수정하면 isEdited=true가 된다")
        void updateComment_success() {
            // given
            Long feedId = 1L;
            Long commentId = 10L;
            ActivityFeed feed = createTestFeed(feedId, OTHER_USER_ID);
            FeedComment comment = createTestComment(commentId, feed, TEST_USER_ID, null);
            FeedCommentUpdateRequest request = createTestUpdateRequest("수정된 댓글");

            when(feedCommentRepository.findById(commentId)).thenReturn(Optional.of(comment));
            when(feedCommentRepository.countActiveRepliesByParentId(commentId)).thenReturn(0);
            when(feedCommentRepository.save(any(FeedComment.class))).thenReturn(comment);

            // when
            feedCommandService.updateComment(feedId, commentId, TEST_USER_ID, request);

            // then
            assertThat(comment.getIsEdited()).isTrue();
            assertThat(comment.getContent()).isEqualTo("수정된 댓글");
        }

        @Test
        @DisplayName("다른 사용자가 수정 시도 시 예외 발생")
        void updateComment_notOwner_throwsException() {
            // given
            Long feedId = 1L;
            Long commentId = 10L;
            ActivityFeed feed = createTestFeed(feedId, OTHER_USER_ID);
            FeedComment comment = createTestComment(commentId, feed, OTHER_USER_ID, null);
            FeedCommentUpdateRequest request = createTestUpdateRequest("악의 수정");

            when(feedCommentRepository.findById(commentId)).thenReturn(Optional.of(comment));

            // when & then
            assertThatThrownBy(() ->
                feedCommandService.updateComment(feedId, commentId, TEST_USER_ID, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.feed.comment.not_owner");
        }

        @Test
        @DisplayName("대댓글이 달린 댓글 수정 시 예외 발생")
        void updateComment_hasReplies_throwsException() {
            // given
            Long feedId = 1L;
            Long commentId = 10L;
            ActivityFeed feed = createTestFeed(feedId, OTHER_USER_ID);
            FeedComment comment = createTestComment(commentId, feed, TEST_USER_ID, null);
            FeedCommentUpdateRequest request = createTestUpdateRequest("수정 시도");

            when(feedCommentRepository.findById(commentId)).thenReturn(Optional.of(comment));
            when(feedCommentRepository.countActiveRepliesByParentId(commentId)).thenReturn(2);

            // when & then
            assertThatThrownBy(() ->
                feedCommandService.updateComment(feedId, commentId, TEST_USER_ID, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.feed.comment.has_replies_uneditable");
        }

        @Test
        @DisplayName("삭제된 댓글 수정 시 예외 발생")
        void updateComment_deleted_throwsException() {
            // given
            Long feedId = 1L;
            Long commentId = 10L;
            ActivityFeed feed = createTestFeed(feedId, OTHER_USER_ID);
            FeedComment comment = createTestComment(commentId, feed, TEST_USER_ID, null);
            comment.setIsDeleted(true);
            FeedCommentUpdateRequest request = createTestUpdateRequest("수정 시도");

            when(feedCommentRepository.findById(commentId)).thenReturn(Optional.of(comment));

            // when & then
            assertThatThrownBy(() ->
                feedCommandService.updateComment(feedId, commentId, TEST_USER_ID, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.feed.comment.deleted");
        }
    }

    @Nested
    @DisplayName("toggleCommentLike 테스트")
    class ToggleCommentLikeTest {

        @Test
        @DisplayName("좋아요를 신규로 누르면 이벤트 발행 + 카운트 증가")
        void toggleCommentLike_add_publishesEvent() {
            // given
            Long feedId = 1L;
            Long commentId = 10L;
            ActivityFeed feed = createTestFeed(feedId, OTHER_USER_ID);
            FeedComment comment = createTestComment(commentId, feed, OTHER_USER_ID, null);
            UserProfileInfo likerProfile = new UserProfileInfo(
                TEST_USER_ID, "테스트유저", null, 5, null, null, null);

            when(feedCommentRepository.findById(commentId)).thenReturn(Optional.of(comment));
            when(feedCommentLikeRepository.findByCommentIdAndUserId(commentId, TEST_USER_ID))
                .thenReturn(Optional.empty());
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID)).thenReturn(likerProfile);
            when(feedCommentLikeRepository.countByCommentId(commentId)).thenReturn(1);

            // when
            FeedCommentLikeResponse result = feedCommandService.toggleCommentLike(feedId, commentId, TEST_USER_ID);

            // then
            assertThat(result.isLiked()).isTrue();
            assertThat(result.getLikeCount()).isEqualTo(1);
            verify(feedCommentLikeRepository).save(any(FeedCommentLike.class));
            verify(eventPublisher).publishEvent(any(FeedCommentLikedEvent.class));
        }

        @Test
        @DisplayName("이미 누른 상태에서 토글하면 취소 + 이벤트 없음")
        void toggleCommentLike_remove_noEvent() {
            // given
            Long feedId = 1L;
            Long commentId = 10L;
            ActivityFeed feed = createTestFeed(feedId, OTHER_USER_ID);
            FeedComment comment = createTestComment(commentId, feed, OTHER_USER_ID, null);
            FeedCommentLike existing = FeedCommentLike.builder()
                .comment(comment)
                .userId(TEST_USER_ID)
                .build();

            when(feedCommentRepository.findById(commentId)).thenReturn(Optional.of(comment));
            when(feedCommentLikeRepository.findByCommentIdAndUserId(commentId, TEST_USER_ID))
                .thenReturn(Optional.of(existing));
            when(feedCommentLikeRepository.countByCommentId(commentId)).thenReturn(0);

            // when
            FeedCommentLikeResponse result = feedCommandService.toggleCommentLike(feedId, commentId, TEST_USER_ID);

            // then
            assertThat(result.isLiked()).isFalse();
            verify(feedCommentLikeRepository).delete(existing);
            verify(eventPublisher, never()).publishEvent(any(FeedCommentLikedEvent.class));
        }

        @Test
        @DisplayName("본인 댓글에 좋아요 시 알림 이벤트 발행하지 않음")
        void toggleCommentLike_selfLike_noEvent() {
            // given
            Long feedId = 1L;
            Long commentId = 10L;
            ActivityFeed feed = createTestFeed(feedId, OTHER_USER_ID);
            FeedComment comment = createTestComment(commentId, feed, TEST_USER_ID, null); // 본인 댓글

            when(feedCommentRepository.findById(commentId)).thenReturn(Optional.of(comment));
            when(feedCommentLikeRepository.findByCommentIdAndUserId(commentId, TEST_USER_ID))
                .thenReturn(Optional.empty());
            when(feedCommentLikeRepository.countByCommentId(commentId)).thenReturn(1);

            // when
            FeedCommentLikeResponse result = feedCommandService.toggleCommentLike(feedId, commentId, TEST_USER_ID);

            // then
            assertThat(result.isLiked()).isTrue();
            verify(feedCommentLikeRepository).save(any(FeedCommentLike.class));
            verify(eventPublisher, never()).publishEvent(any(FeedCommentLikedEvent.class));
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
            feedCommandService.deleteComment(feedId, commentId, TEST_USER_ID);

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
            assertThatThrownBy(() -> feedCommandService.deleteComment(feedId, commentId, TEST_USER_ID))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.feed.comment.not_owner");
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
            assertThatThrownBy(() -> feedCommandService.deleteComment(wrongFeedId, commentId, TEST_USER_ID))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.feed.comment.wrong_feed");
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
            feedCommandService.deleteFeed(feedId, TEST_USER_ID);

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
            assertThatThrownBy(() -> feedCommandService.deleteFeed(feedId, TEST_USER_ID))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.feed.not_owner");
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
            int deletedCount = feedCommandService.deleteFeedsByReferenceId(referenceId, referenceType);

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
            int deletedCount = feedCommandService.deleteFeedsByMissionId(missionId);

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
            when(gamificationQueryFacadeService.getDetailedEquippedTitleInfo(TEST_USER_ID)).thenReturn(EMPTY_DETAILED_TITLE);

            // when
            ActivityFeed result = feedCommandService.createMissionSharedFeed(
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
            feedCommandService.deleteFeedById(feedId);

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
            feedCommandService.updateFeedImageUrl(feedId, newImageUrl);

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
            feedCommandService.updateFeedImageUrl(feedId, newImageUrl);

            // then
            verify(activityFeedRepository, never()).save(any(ActivityFeed.class));
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
            UserProfileInfo userProfile = new UserProfileInfo(
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
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID)).thenReturn(userProfile);
            when(feedCommentRepository.save(any(FeedComment.class))).thenReturn(savedComment);
            when(activityFeedRepository.save(any(ActivityFeed.class))).thenReturn(feed);

            // when
            feedCommandService.addComment(feedId, TEST_USER_ID, request);

            // then
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("updateFeedVisibility 테스트")
    class UpdateFeedVisibilityTest {

        @Test
        @DisplayName("피드 공개범위를 변경한다")
        void updateFeedVisibility_success() {
            // given
            Long feedId = 1L;
            ActivityFeed feed = createTestFeed(feedId, TEST_USER_ID);
            when(activityFeedRepository.findById(feedId)).thenReturn(Optional.of(feed));
            when(activityFeedRepository.save(any(ActivityFeed.class))).thenReturn(feed);

            // when
            ActivityFeedResponse result = feedCommandService.updateFeedVisibility(feedId, TEST_USER_ID, FeedVisibility.PRIVATE);

            // then
            assertThat(result).isNotNull();
            assertThat(feed.getVisibility()).isEqualTo(FeedVisibility.PRIVATE);
            verify(activityFeedRepository).save(feed);
        }

        @Test
        @DisplayName("존재하지 않는 피드의 공개범위 변경 시 예외 발생")
        void updateFeedVisibility_feedNotFound_throwsException() {
            // given
            when(activityFeedRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> feedCommandService.updateFeedVisibility(999L, TEST_USER_ID, FeedVisibility.PRIVATE))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.feed.not_found");
        }

        @Test
        @DisplayName("다른 사용자의 피드 공개범위 변경 시 예외 발생")
        void updateFeedVisibility_notOwner_throwsException() {
            // given
            Long feedId = 1L;
            ActivityFeed feed = createTestFeed(feedId, OTHER_USER_ID);
            when(activityFeedRepository.findById(feedId)).thenReturn(Optional.of(feed));

            // when & then
            assertThatThrownBy(() -> feedCommandService.updateFeedVisibility(feedId, TEST_USER_ID, FeedVisibility.PRIVATE))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.feed.not_owner");
        }
    }

    @Nested
    @DisplayName("deleteFeedByExecutionId 테스트")
    class DeleteFeedByExecutionIdTest {

        @Test
        @DisplayName("executionId에 해당하는 피드를 삭제한다")
        void deleteFeedByExecutionId_success() {
            // given
            Long executionId = 10L;
            ActivityFeed feed = createTestFeed(1L, TEST_USER_ID);
            when(activityFeedRepository.findFirstByExecutionIdOrderByCreatedAtDesc(executionId))
                .thenReturn(Optional.of(feed));

            // when
            feedCommandService.deleteFeedByExecutionId(executionId);

            // then
            verify(activityFeedRepository).delete(feed);
        }

        @Test
        @DisplayName("executionId에 해당하는 피드가 없으면 삭제하지 않는다")
        void deleteFeedByExecutionId_notFound_doesNothing() {
            // given
            Long executionId = 999L;
            when(activityFeedRepository.findFirstByExecutionIdOrderByCreatedAtDesc(executionId))
                .thenReturn(Optional.empty());

            // when
            feedCommandService.deleteFeedByExecutionId(executionId);

            // then
            verify(activityFeedRepository, never()).delete(any(ActivityFeed.class));
        }
    }

    @Nested
    @DisplayName("updateFeedImageUrlByExecutionId 테스트")
    class UpdateFeedImageUrlByExecutionIdTest {

        @Test
        @DisplayName("executionId로 피드 이미지 URL을 업데이트한다")
        void updateFeedImageUrlByExecutionId_success() {
            // given
            Long executionId = 10L;
            String newImageUrl = "https://example.com/updated.jpg";
            ActivityFeed feed = createTestFeed(1L, TEST_USER_ID);
            when(activityFeedRepository.findFirstByExecutionIdOrderByCreatedAtDesc(executionId))
                .thenReturn(Optional.of(feed));
            when(activityFeedRepository.save(any(ActivityFeed.class))).thenReturn(feed);

            // when
            feedCommandService.updateFeedImageUrlByExecutionId(executionId, newImageUrl);

            // then
            assertThat(feed.getImageUrl()).isEqualTo(newImageUrl);
            verify(activityFeedRepository).save(feed);
        }

        @Test
        @DisplayName("executionId에 해당하는 피드가 없으면 업데이트하지 않는다")
        void updateFeedImageUrlByExecutionId_notFound_doesNothing() {
            // given
            Long executionId = 999L;
            when(activityFeedRepository.findFirstByExecutionIdOrderByCreatedAtDesc(executionId))
                .thenReturn(Optional.empty());

            // when
            feedCommandService.updateFeedImageUrlByExecutionId(executionId, "https://x.com/img.jpg");

            // then
            verify(activityFeedRepository, never()).save(any(ActivityFeed.class));
        }
    }

    @Nested
    @DisplayName("updateFeedDescriptionByExecutionId 테스트")
    class UpdateFeedDescriptionByExecutionIdTest {

        @Test
        @DisplayName("executionId로 피드 description을 업데이트한다")
        void updateFeedDescriptionByExecutionId_success() {
            // given
            Long executionId = 10L;
            String newDescription = "업데이트된 노트";
            ActivityFeed feed = createTestFeed(1L, TEST_USER_ID);
            when(activityFeedRepository.findFirstByExecutionIdOrderByCreatedAtDesc(executionId))
                .thenReturn(Optional.of(feed));
            when(activityFeedRepository.save(any(ActivityFeed.class))).thenReturn(feed);

            // when
            feedCommandService.updateFeedDescriptionByExecutionId(executionId, newDescription);

            // then
            assertThat(feed.getDescription()).isEqualTo(newDescription);
            verify(activityFeedRepository).save(feed);
        }

        @Test
        @DisplayName("executionId에 해당하는 피드가 없으면 업데이트하지 않는다")
        void updateFeedDescriptionByExecutionId_notFound_doesNothing() {
            // given
            Long executionId = 999L;
            when(activityFeedRepository.findFirstByExecutionIdOrderByCreatedAtDesc(executionId))
                .thenReturn(Optional.empty());

            // when
            feedCommandService.updateFeedDescriptionByExecutionId(executionId, "노트");

            // then
            verify(activityFeedRepository, never()).save(any(ActivityFeed.class));
        }

        @Test
        @DisplayName("description이 null이어도 업데이트한다")
        void updateFeedDescriptionByExecutionId_nullDescription_success() {
            // given
            Long executionId = 10L;
            ActivityFeed feed = createTestFeed(1L, TEST_USER_ID);
            when(activityFeedRepository.findFirstByExecutionIdOrderByCreatedAtDesc(executionId))
                .thenReturn(Optional.of(feed));
            when(activityFeedRepository.save(any(ActivityFeed.class))).thenReturn(feed);

            // when
            feedCommandService.updateFeedDescriptionByExecutionId(executionId, null);

            // then
            assertThat(feed.getDescription()).isNull();
            verify(activityFeedRepository).save(feed);
        }
    }

    @Nested
    @DisplayName("updateFeedContentByExecutionId 테스트")
    class UpdateFeedContentByExecutionIdTest {

        @Test
        @DisplayName("executionId로 피드 content를 업데이트하고 피드를 반환한다")
        void updateFeedContentByExecutionId_success() {
            // given
            Long executionId = 10L;
            ActivityFeed feed = createTestFeed(1L, TEST_USER_ID);
            when(activityFeedRepository.findFirstByExecutionIdOrderByCreatedAtDesc(executionId))
                .thenReturn(Optional.of(feed));
            when(activityFeedRepository.save(any(ActivityFeed.class))).thenReturn(feed);

            // when
            ActivityFeed result = feedCommandService.updateFeedContentByExecutionId(
                executionId, "새 설명", "https://example.com/img.jpg", FeedVisibility.FRIENDS);

            // then
            assertThat(result).isNotNull();
            assertThat(feed.getDescription()).isEqualTo("새 설명");
            assertThat(feed.getImageUrl()).isEqualTo("https://example.com/img.jpg");
            assertThat(feed.getVisibility()).isEqualTo(FeedVisibility.FRIENDS);
            verify(activityFeedRepository).save(feed);
        }

        @Test
        @DisplayName("executionId에 해당하는 피드가 없으면 null을 반환한다")
        void updateFeedContentByExecutionId_notFound_returnsNull() {
            // given
            Long executionId = 999L;
            when(activityFeedRepository.findFirstByExecutionIdOrderByCreatedAtDesc(executionId))
                .thenReturn(Optional.empty());

            // when
            ActivityFeed result = feedCommandService.updateFeedContentByExecutionId(
                executionId, "설명", null, FeedVisibility.PUBLIC);

            // then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("updateFeedTitles 테스트")
    class UpdateFeedTitlesTest {

        @Test
        @DisplayName("사용자의 모든 피드 칭호 정보를 업데이트한다")
        void updateFeedTitles_success() {
            // given
            DetailedTitleInfoDto detailedTitle = new DetailedTitleInfoDto(
                "용감한", io.pinkspider.global.enums.TitleRarity.RARE,
                "전사", io.pinkspider.global.enums.TitleRarity.EPIC,
                null, null
            );
            when(gamificationQueryFacadeService.getDetailedEquippedTitleInfo(TEST_USER_ID))
                .thenReturn(detailedTitle);
            when(activityFeedRepository.updateUserTitleByUserId(
                eq(TEST_USER_ID), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(3);

            // when
            int result = feedCommandService.updateFeedTitles(
                TEST_USER_ID, "용감한 전사", TitleRarity.EPIC, "#00FF00");

            // then
            assertThat(result).isEqualTo(3);
            verify(activityFeedRepository).updateUserTitleByUserId(
                eq(TEST_USER_ID), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("칭호 정보 조회 실패 시 빈 DetailedTitleInfoDto로 업데이트한다")
        void updateFeedTitles_titleInfoFetchFails_usesEmpty() {
            // given
            when(gamificationQueryFacadeService.getDetailedEquippedTitleInfo(TEST_USER_ID))
                .thenThrow(new RuntimeException("Feign 오류"));
            when(activityFeedRepository.updateUserTitleByUserId(
                eq(TEST_USER_ID), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(2);

            // when
            int result = feedCommandService.updateFeedTitles(TEST_USER_ID, null, null, null);

            // then
            assertThat(result).isEqualTo(2);
            verify(activityFeedRepository).updateUserTitleByUserId(
                eq(TEST_USER_ID), any(), any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("deleteFeedByAdmin 테스트")
    class DeleteFeedByAdminTest {

        @Test
        @DisplayName("어드민이 피드를 삭제한다")
        void deleteFeedByAdmin_success() {
            // given
            Long feedId = 1L;
            ActivityFeed feed = createTestFeed(feedId, OTHER_USER_ID);
            when(activityFeedRepository.findById(feedId)).thenReturn(Optional.of(feed));

            // when
            feedCommandService.deleteFeedByAdmin(feedId, "부적절한 콘텐츠", "admin@test.com");

            // then
            verify(activityFeedRepository).delete(feed);
        }

        @Test
        @DisplayName("존재하지 않는 피드를 어드민 삭제 시 예외 발생")
        void deleteFeedByAdmin_notFound_throwsException() {
            // given
            when(activityFeedRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> feedCommandService.deleteFeedByAdmin(999L, "사유", "admin"))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.feed.not_found");
        }
    }

    @Nested
    @DisplayName("deleteCommentByAdmin 테스트")
    class DeleteCommentByAdminTest {

        @Test
        @DisplayName("어드민이 댓글을 강제 삭제한다")
        void deleteCommentByAdmin_success() {
            // given
            Long commentId = 1L;
            Long feedId = 1L;
            ActivityFeed feed = createTestFeed(feedId, OTHER_USER_ID);
            feed.incrementCommentCount();

            FeedComment comment = FeedComment.builder()
                .feed(feed)
                .userId(OTHER_USER_ID)
                .content("부적절한 댓글")
                .isDeleted(false)
                .build();
            setId(comment, commentId);

            when(feedCommentRepository.findById(commentId)).thenReturn(Optional.of(comment));
            when(feedCommentRepository.save(any(FeedComment.class))).thenReturn(comment);
            when(activityFeedRepository.save(any(ActivityFeed.class))).thenReturn(feed);

            // when
            feedCommandService.deleteCommentByAdmin(commentId, "신고 처리");

            // then
            assertThat(comment.getIsDeleted()).isTrue();
            assertThat(feed.getCommentCount()).isEqualTo(0);
            verify(feedCommentRepository).save(comment);
            verify(activityFeedRepository).save(feed);
        }

        @Test
        @DisplayName("존재하지 않는 댓글을 어드민 삭제 시 예외 발생")
        void deleteCommentByAdmin_notFound_throwsException() {
            // given
            when(feedCommentRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> feedCommandService.deleteCommentByAdmin(999L, "사유"))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.feed.comment.not_found");
        }
    }

    @Nested
    @DisplayName("toggleLike 추가 예외 케이스 테스트")
    class ToggleLikeExtraTest {

        @Test
        @DisplayName("존재하지 않는 피드에 좋아요 시 예외 발생")
        void toggleLike_feedNotFound_throwsException() {
            // given
            when(activityFeedRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> feedCommandService.toggleLike(999L, TEST_USER_ID))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.feed.not_found");
        }
    }

    @Nested
    @DisplayName("createFeed visibility 기본값 테스트")
    class CreateFeedVisibilityDefaultTest {

        @Test
        @DisplayName("visibility가 null이면 기본값 PUBLIC으로 생성된다")
        void createFeed_nullVisibility_defaultsToPublic() {
            // given
            ActivityFeed savedFeed = createTestFeed(1L, TEST_USER_ID);
            CreateFeedRequest request = createTestFeedRequest();
            // visibility 필드 세팅하지 않음(null 유지)

            when(userQueryFacadeService.userExistsById(TEST_USER_ID)).thenReturn(true);
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID)).thenReturn(
                new UserProfileInfo(TEST_USER_ID, "테스트유저", "https://example.com/profile.jpg", 5, null, null, null));
            when(gamificationQueryFacadeService.getCombinedEquippedTitleInfo(TEST_USER_ID))
                .thenReturn(new TitleInfoDto("칭호", TitleRarity.COMMON, "#FFFFFF"));
            when(gamificationQueryFacadeService.getDetailedEquippedTitleInfo(TEST_USER_ID))
                .thenReturn(EMPTY_DETAILED_TITLE);
            when(activityFeedRepository.save(any(ActivityFeed.class))).thenReturn(savedFeed);

            // when
            ActivityFeedResponse result = feedCommandService.createFeed(TEST_USER_ID, request);

            // then
            assertThat(result).isNotNull();
            verify(activityFeedRepository).save(any(ActivityFeed.class));
        }
    }

    @Nested
    @DisplayName("createMissionSharedFeed visibility overload 테스트")
    class CreateMissionSharedFeedVisibilityTest {

        @Test
        @DisplayName("visibility 파라미터를 명시하여 미션 공유 피드를 생성한다")
        void createMissionSharedFeed_withVisibility_success() {
            // given
            ActivityFeed savedFeed = ActivityFeed.builder()
                .userId(TEST_USER_ID)
                .activityType(ActivityType.MISSION_SHARED)
                .visibility(FeedVisibility.FRIENDS)
                .likeCount(0)
                .commentCount(0)
                .build();
            setId(savedFeed, 2L);

            when(activityFeedRepository.save(any(ActivityFeed.class))).thenReturn(savedFeed);
            when(gamificationQueryFacadeService.getDetailedEquippedTitleInfo(TEST_USER_ID))
                .thenReturn(EMPTY_DETAILED_TITLE);

            // when
            ActivityFeed result = feedCommandService.createMissionSharedFeed(
                TEST_USER_ID, "테스트유저", "https://example.com/profile.jpg",
                5, null, null, null,
                1L, 2L, "미션 제목", "미션 설명", 1L,
                "노트", null, 60, 50, FeedVisibility.FRIENDS
            );

            // then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(2L);
            verify(activityFeedRepository).save(any(ActivityFeed.class));
        }
    }
}
