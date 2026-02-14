package io.pinkspider.leveluptogethermvp.missionservice.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.event.MissionCommentEvent;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionCommentRequest;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionCommentResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionComment;
import io.pinkspider.global.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionCommentRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionRepository;
import io.pinkspider.leveluptogethermvp.userservice.profile.application.UserQueryFacadeService;
import io.pinkspider.leveluptogethermvp.userservice.profile.domain.dto.UserProfileCache;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class MissionCommentServiceTest {

    @Mock
    private MissionCommentRepository missionCommentRepository;

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private UserQueryFacadeService userQueryFacadeService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Captor
    private ArgumentCaptor<MissionCommentEvent> eventCaptor;

    @InjectMocks
    private MissionCommentService missionCommentService;

    private static final String TEST_USER_ID = "test-user-123";
    private static final String CREATOR_USER_ID = "creator-user-456";
    private static final String OTHER_USER_ID = "other-user-789";
    private static final Long MISSION_ID = 1L;
    private static final Long COMMENT_ID = 100L;

    private Mission createTestMission() {
        Mission mission = Mission.builder()
            .title("테스트 미션")
            .description("테스트 미션 설명")
            .status(MissionStatus.OPEN)
            .visibility(MissionVisibility.PUBLIC)
            .type(MissionType.PERSONAL)
            .creatorId(CREATOR_USER_ID)
            .build();
        setId(mission, MISSION_ID);
        return mission;
    }

    private MissionComment createTestComment(Mission mission, String userId) {
        MissionComment comment = MissionComment.builder()
            .mission(mission)
            .userId(userId)
            .userNickname("테스트유저")
            .userProfileImageUrl("https://example.com/profile.jpg")
            .userLevel(5)
            .content("테스트 댓글입니다")
            .isDeleted(false)
            .build();
        setId(comment, COMMENT_ID);
        return comment;
    }

    private UserProfileCache createTestUserProfile() {
        return new UserProfileCache(
            TEST_USER_ID,
            "테스트유저",
            "https://example.com/profile.jpg",
            5,
            null,
            null,
            null
        );
    }

    @Nested
    @DisplayName("댓글 작성 테스트")
    class AddCommentTest {

        @Test
        @DisplayName("댓글을 성공적으로 작성한다")
        void addComment_success() {
            // given
            Mission mission = createTestMission();
            MissionCommentRequest request = MissionCommentRequest.builder()
                .content("새로운 댓글입니다")
                .build();
            UserProfileCache userProfile = createTestUserProfile();

            when(missionRepository.findById(MISSION_ID)).thenReturn(Optional.of(mission));
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID)).thenReturn(userProfile);
            when(missionCommentRepository.save(any(MissionComment.class))).thenAnswer(invocation -> {
                MissionComment saved = invocation.getArgument(0);
                setId(saved, COMMENT_ID);
                return saved;
            });

            // when
            MissionCommentResponse response = missionCommentService.addComment(MISSION_ID, TEST_USER_ID, request);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getContent()).isEqualTo("새로운 댓글입니다");
            assertThat(response.getUserId()).isEqualTo(TEST_USER_ID);
            assertThat(response.getUserNickname()).isEqualTo("테스트유저");

            verify(missionCommentRepository).save(any(MissionComment.class));
        }

        @Test
        @DisplayName("미션 생성자가 아닌 사람이 댓글을 달면 알림 이벤트가 발행된다")
        void addComment_publishesEventForCreator() {
            // given
            Mission mission = createTestMission();
            MissionCommentRequest request = MissionCommentRequest.builder()
                .content("새로운 댓글입니다")
                .build();
            UserProfileCache userProfile = createTestUserProfile();

            when(missionRepository.findById(MISSION_ID)).thenReturn(Optional.of(mission));
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID)).thenReturn(userProfile);
            when(missionCommentRepository.save(any(MissionComment.class))).thenAnswer(invocation -> {
                MissionComment saved = invocation.getArgument(0);
                setId(saved, COMMENT_ID);
                return saved;
            });

            // when
            missionCommentService.addComment(MISSION_ID, TEST_USER_ID, request);

            // then
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            MissionCommentEvent event = eventCaptor.getValue();
            assertThat(event.userId()).isEqualTo(TEST_USER_ID);
            assertThat(event.missionCreatorId()).isEqualTo(CREATOR_USER_ID);
            assertThat(event.missionId()).isEqualTo(MISSION_ID);
        }

        @Test
        @DisplayName("미션 생성자가 본인 미션에 댓글을 달면 알림 이벤트가 발행되지 않는다")
        void addComment_noEventWhenCreatorComments() {
            // given
            Mission mission = createTestMission();
            MissionCommentRequest request = MissionCommentRequest.builder()
                .content("생성자의 댓글입니다")
                .build();
            UserProfileCache userProfile = new UserProfileCache(
                CREATOR_USER_ID, "미션생성자", "https://example.com/creator.jpg", 10, null, null, null
            );

            when(missionRepository.findById(MISSION_ID)).thenReturn(Optional.of(mission));
            when(userQueryFacadeService.getUserProfile(CREATOR_USER_ID)).thenReturn(userProfile);
            when(missionCommentRepository.save(any(MissionComment.class))).thenAnswer(invocation -> {
                MissionComment saved = invocation.getArgument(0);
                setId(saved, COMMENT_ID);
                return saved;
            });

            // when
            missionCommentService.addComment(MISSION_ID, CREATOR_USER_ID, request);

            // then
            verify(eventPublisher, never()).publishEvent(any(MissionCommentEvent.class));
        }

        @Test
        @DisplayName("존재하지 않는 미션에 댓글을 달면 예외가 발생한다")
        void addComment_missionNotFound() {
            // given
            MissionCommentRequest request = MissionCommentRequest.builder()
                .content("댓글입니다")
                .build();

            when(missionRepository.findById(MISSION_ID)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> missionCommentService.addComment(MISSION_ID, TEST_USER_ID, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("미션을 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("댓글 목록 조회 테스트")
    class GetCommentsTest {

        @Test
        @DisplayName("댓글 목록을 성공적으로 조회한다")
        void getComments_success() {
            // given
            Mission mission = createTestMission();
            MissionComment comment1 = createTestComment(mission, TEST_USER_ID);
            MissionComment comment2 = createTestComment(mission, OTHER_USER_ID);
            setId(comment2, 101L);

            Page<MissionComment> commentPage = new PageImpl<>(
                List.of(comment1, comment2),
                PageRequest.of(0, 20),
                2
            );

            when(missionRepository.existsById(MISSION_ID)).thenReturn(true);
            when(missionCommentRepository.findByMissionId(anyLong(), any(PageRequest.class)))
                .thenReturn(commentPage);

            // when
            Page<MissionCommentResponse> result = missionCommentService.getComments(MISSION_ID, TEST_USER_ID, 0, 20);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent().get(0).getIsMyComment()).isTrue();
            assertThat(result.getContent().get(1).getIsMyComment()).isFalse();
        }

        @Test
        @DisplayName("비로그인 상태에서 댓글 목록을 조회하면 isMyComment가 모두 false다")
        void getComments_anonymousUser() {
            // given
            Mission mission = createTestMission();
            MissionComment comment = createTestComment(mission, TEST_USER_ID);

            Page<MissionComment> commentPage = new PageImpl<>(
                List.of(comment),
                PageRequest.of(0, 20),
                1
            );

            when(missionRepository.existsById(MISSION_ID)).thenReturn(true);
            when(missionCommentRepository.findByMissionId(anyLong(), any(PageRequest.class)))
                .thenReturn(commentPage);

            // when
            Page<MissionCommentResponse> result = missionCommentService.getComments(MISSION_ID, null, 0, 20);

            // then
            assertThat(result.getContent().get(0).getIsMyComment()).isFalse();
        }

        @Test
        @DisplayName("존재하지 않는 미션의 댓글을 조회하면 예외가 발생한다")
        void getComments_missionNotFound() {
            // given
            when(missionRepository.existsById(MISSION_ID)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> missionCommentService.getComments(MISSION_ID, TEST_USER_ID, 0, 20))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("미션을 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("댓글 삭제 테스트")
    class DeleteCommentTest {

        @Test
        @DisplayName("본인 댓글을 성공적으로 삭제한다")
        void deleteComment_success() {
            // given
            Mission mission = createTestMission();
            MissionComment comment = createTestComment(mission, TEST_USER_ID);

            when(missionCommentRepository.findByIdAndIsDeletedFalse(COMMENT_ID))
                .thenReturn(Optional.of(comment));

            // when
            missionCommentService.deleteComment(MISSION_ID, COMMENT_ID, TEST_USER_ID);

            // then
            assertThat(comment.getIsDeleted()).isTrue();
            assertThat(comment.getDeletedAt()).isNotNull();
            assertThat(comment.getContent()).isEqualTo("[삭제된 댓글입니다]");
            verify(missionCommentRepository).save(comment);
        }

        @Test
        @DisplayName("다른 사람의 댓글을 삭제하려고 하면 예외가 발생한다")
        void deleteComment_notAuthor() {
            // given
            Mission mission = createTestMission();
            MissionComment comment = createTestComment(mission, OTHER_USER_ID);

            when(missionCommentRepository.findByIdAndIsDeletedFalse(COMMENT_ID))
                .thenReturn(Optional.of(comment));

            // when & then
            assertThatThrownBy(() -> missionCommentService.deleteComment(MISSION_ID, COMMENT_ID, TEST_USER_ID))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("본인의 댓글만 삭제할 수 있습니다");
        }

        @Test
        @DisplayName("존재하지 않는 댓글을 삭제하려고 하면 예외가 발생한다")
        void deleteComment_commentNotFound() {
            // given
            when(missionCommentRepository.findByIdAndIsDeletedFalse(COMMENT_ID))
                .thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> missionCommentService.deleteComment(MISSION_ID, COMMENT_ID, TEST_USER_ID))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("댓글을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("다른 미션의 댓글을 삭제하려고 하면 예외가 발생한다")
        void deleteComment_wrongMission() {
            // given
            Mission mission = createTestMission();
            MissionComment comment = createTestComment(mission, TEST_USER_ID);

            when(missionCommentRepository.findByIdAndIsDeletedFalse(COMMENT_ID))
                .thenReturn(Optional.of(comment));

            // when & then
            Long wrongMissionId = 999L;
            assertThatThrownBy(() -> missionCommentService.deleteComment(wrongMissionId, COMMENT_ID, TEST_USER_ID))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("해당 미션의 댓글이 아닙니다");
        }
    }

    @Nested
    @DisplayName("댓글 수 조회 테스트")
    class GetCommentCountTest {

        @Test
        @DisplayName("미션의 댓글 수를 조회한다")
        void getCommentCount_success() {
            // given
            when(missionCommentRepository.countByMissionId(MISSION_ID)).thenReturn(5);

            // when
            int count = missionCommentService.getCommentCount(MISSION_ID);

            // then
            assertThat(count).isEqualTo(5);
        }
    }
}
