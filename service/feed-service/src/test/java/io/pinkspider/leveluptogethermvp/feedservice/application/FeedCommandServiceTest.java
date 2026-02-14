package io.pinkspider.leveluptogethermvp.feedservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.event.FeedLikedEvent;
import io.pinkspider.global.event.FeedUnlikedEvent;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.global.test.TestReflectionUtils;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.ActivityFeedResponse;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.CreateFeedRequest;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.FeedCommentRequest;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.FeedCommentResponse;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.FeedLikeResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application.TitleService.TitleInfo;
import io.pinkspider.leveluptogethermvp.gamificationservice.application.GamificationQueryFacadeService;
import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.ActivityFeed;
import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.FeedComment;
import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.FeedLike;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.ActivityType;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.FeedVisibility;
import io.pinkspider.leveluptogethermvp.feedservice.infrastructure.ActivityFeedRepository;
import io.pinkspider.leveluptogethermvp.feedservice.infrastructure.FeedCommentRepository;
import io.pinkspider.leveluptogethermvp.feedservice.infrastructure.FeedLikeRepository;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.userservice.profile.application.UserQueryFacadeService;
import io.pinkspider.leveluptogethermvp.userservice.profile.domain.dto.UserProfileCache;
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
    private FeedLikeRepository feedLikeRepository;

    @Mock
    private FeedCommentRepository feedCommentRepository;

    @Mock
    private UserQueryFacadeService userQueryFacadeService;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Mock
    private GamificationQueryFacadeService gamificationQueryFacadeService;

    @InjectMocks
    private FeedCommandService feedCommandService;

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
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID)).thenReturn(new UserProfileCache(TEST_USER_ID, "테스트유저", "https://example.com/profile.jpg", 5, null, null, null));
            when(gamificationQueryFacadeService.getCombinedEquippedTitleInfo(TEST_USER_ID))
                .thenReturn(new TitleInfo("초보 모험가", TitleRarity.COMMON, "#FFFFFF"));
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
                .hasMessageContaining("사용자를 찾을 수 없습니다");
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
                .hasMessageContaining("피드를 찾을 수 없습니다");
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
            assertThatThrownBy(() -> feedCommandService.deleteComment(wrongFeedId, commentId, TEST_USER_ID))
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
                .hasMessageContaining("본인의 피드만 삭제할 수 있습니다");
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
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID)).thenReturn(userProfile);
            when(feedCommentRepository.save(any(FeedComment.class))).thenReturn(savedComment);
            when(activityFeedRepository.save(any(ActivityFeed.class))).thenReturn(feed);

            // when
            feedCommandService.addComment(feedId, TEST_USER_ID, request);

            // then
            verify(eventPublisher, never()).publishEvent(any());
        }
    }
}
