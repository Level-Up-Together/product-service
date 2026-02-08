package io.pinkspider.leveluptogethermvp.guildservice.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.event.GuildBulletinCreatedEvent;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildPostCommentCreateRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildPostCommentResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildPostCreateRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildPostListResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildPostResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildPostUpdateRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildPostComment;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildMember;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildPost;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberRole;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberStatus;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildPostType;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildVisibility;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildPostCommentRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildPostRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import io.pinkspider.leveluptogethermvp.supportservice.report.api.dto.ReportTargetType;
import io.pinkspider.leveluptogethermvp.supportservice.report.application.ReportService;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class GuildPostServiceTest {

    @Mock
    private GuildRepository guildRepository;

    @Mock
    private GuildMemberRepository guildMemberRepository;

    @Mock
    private GuildPostRepository guildPostRepository;

    @Mock
    private GuildPostCommentRepository guildPostCommentRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ReportService reportService;

    @InjectMocks
    private GuildPostService guildPostService;

    private Guild testGuild;
    private GuildMember masterMember;
    private GuildMember normalMember;
    private GuildPost testPost;
    private String masterId;
    private String memberId;

    @BeforeEach
    void setUp() {
        masterId = "master-user-id";
        memberId = "member-user-id";

        testGuild = Guild.builder()
            .name("테스트 길드")
            .description("테스트 길드 설명")
            .visibility(GuildVisibility.PUBLIC)
            .masterId(masterId)
            .maxMembers(50)
            .categoryId(1L)
            .build();
        setId(testGuild, 1L);

        masterMember = GuildMember.builder()
            .guild(testGuild)
            .userId(masterId)
            .role(GuildMemberRole.MASTER)
            .status(GuildMemberStatus.ACTIVE)
            .joinedAt(LocalDateTime.now())
            .build();

        normalMember = GuildMember.builder()
            .guild(testGuild)
            .userId(memberId)
            .role(GuildMemberRole.MEMBER)
            .status(GuildMemberStatus.ACTIVE)
            .joinedAt(LocalDateTime.now())
            .build();

        testPost = GuildPost.builder()
            .guild(testGuild)
            .authorId(memberId)
            .authorNickname("테스터")
            .title("테스트 게시글")
            .content("테스트 내용")
            .postType(GuildPostType.NORMAL)
            .isPinned(false)
            .build();
        setId(testPost, 1L);
    }

    @Nested
    @DisplayName("게시글 작성 테스트")
    class CreatePostTest {

        @Test
        @DisplayName("일반 멤버가 일반 게시글을 작성한다")
        void createPost_normalMember_success() {
            // given
            GuildPostCreateRequest request = GuildPostCreateRequest.builder()
                .title("테스트 게시글")
                .content("테스트 내용")
                .postType(GuildPostType.NORMAL)
                .build();

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, memberId)).thenReturn(Optional.of(normalMember));
            when(guildPostRepository.save(any(GuildPost.class))).thenAnswer(inv -> {
                GuildPost post = inv.getArgument(0);
                setId(post, 1L);
                return post;
            });

            // when
            GuildPostResponse response = guildPostService.createPost(1L, memberId, "테스터", request);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getTitle()).isEqualTo("테스트 게시글");
            assertThat(response.getPostType()).isEqualTo(GuildPostType.NORMAL);
            verify(guildPostRepository).save(any(GuildPost.class));
        }

        @Test
        @DisplayName("마스터만 공지글을 작성할 수 있다")
        void createPost_noticeByMaster_success() {
            // given
            GuildPostCreateRequest request = GuildPostCreateRequest.builder()
                .title("공지사항")
                .content("중요 공지")
                .postType(GuildPostType.NOTICE)
                .isPinned(true)
                .build();

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, masterId)).thenReturn(Optional.of(masterMember));
            when(guildPostRepository.save(any(GuildPost.class))).thenAnswer(inv -> {
                GuildPost post = inv.getArgument(0);
                setId(post, 1L);
                return post;
            });

            // when
            GuildPostResponse response = guildPostService.createPost(1L, masterId, "마스터", request);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getPostType()).isEqualTo(GuildPostType.NOTICE);
        }

        @Test
        @DisplayName("일반 멤버는 공지글을 작성할 수 없다")
        void createPost_noticeByMember_fail() {
            // given
            GuildPostCreateRequest request = GuildPostCreateRequest.builder()
                .title("공지사항")
                .content("중요 공지")
                .postType(GuildPostType.NOTICE)
                .build();

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, memberId)).thenReturn(Optional.of(normalMember));

            // when & then
            assertThatThrownBy(() -> guildPostService.createPost(1L, memberId, "테스터", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("공지글은 길드 마스터 또는 부길드마스터만 작성할 수 있습니다");
        }

        @Test
        @DisplayName("비멤버는 게시글을 작성할 수 없다")
        void createPost_nonMember_fail() {
            // given
            GuildPostCreateRequest request = GuildPostCreateRequest.builder()
                .title("테스트")
                .content("테스트")
                .postType(GuildPostType.NORMAL)
                .build();

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, "non-member")).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> guildPostService.createPost(1L, "non-member", "비멤버", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("길드원만 접근할 수 있습니다");
        }

        @Test
        @DisplayName("공지글 작성 시 길드원들에게 알림 이벤트가 발행된다")
        void createPost_notice_publishesEvent() {
            // given
            GuildPostCreateRequest request = GuildPostCreateRequest.builder()
                .title("중요 공지사항")
                .content("중요 공지 내용")
                .postType(GuildPostType.NOTICE)
                .build();

            String otherMemberId = "other-member-id";
            GuildMember otherMember = GuildMember.builder()
                .guild(testGuild)
                .userId(otherMemberId)
                .role(GuildMemberRole.MEMBER)
                .status(GuildMemberStatus.ACTIVE)
                .build();

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, masterId)).thenReturn(Optional.of(masterMember));
            when(guildMemberRepository.findActiveMembers(1L)).thenReturn(List.of(masterMember, otherMember));
            when(guildPostRepository.save(any(GuildPost.class))).thenAnswer(inv -> {
                GuildPost post = inv.getArgument(0);
                setId(post, 1L);
                return post;
            });

            // when
            GuildPostResponse response = guildPostService.createPost(1L, masterId, "마스터", request);

            // then
            assertThat(response).isNotNull();
            verify(eventPublisher).publishEvent(any(GuildBulletinCreatedEvent.class));
            verify(eventPublisher).publishEvent(argThat((GuildBulletinCreatedEvent bulletinEvent) ->
                bulletinEvent.userId().equals(masterId)
                    && bulletinEvent.guildId().equals(1L)
                    && bulletinEvent.postTitle().equals("중요 공지사항")
                    && bulletinEvent.memberIds().contains(otherMemberId)
                    && !bulletinEvent.memberIds().contains(masterId)  // 작성자 제외
            ));
        }

        @Test
        @DisplayName("일반 게시글 작성 시 알림 이벤트가 발행되지 않는다")
        void createPost_normal_noEvent() {
            // given
            GuildPostCreateRequest request = GuildPostCreateRequest.builder()
                .title("일반 게시글")
                .content("일반 내용")
                .postType(GuildPostType.NORMAL)
                .build();

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, memberId)).thenReturn(Optional.of(normalMember));
            when(guildPostRepository.save(any(GuildPost.class))).thenAnswer(inv -> {
                GuildPost post = inv.getArgument(0);
                setId(post, 1L);
                return post;
            });

            // when
            GuildPostResponse response = guildPostService.createPost(1L, memberId, "테스터", request);

            // then
            assertThat(response).isNotNull();
            verify(eventPublisher, never()).publishEvent(any(GuildBulletinCreatedEvent.class));
        }
    }

    @Nested
    @DisplayName("게시글 조회 테스트")
    class GetPostTest {

        @Test
        @DisplayName("게시글 목록을 조회한다")
        void getPosts_success() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            Page<GuildPost> postPage = new PageImpl<>(List.of(testPost), pageable, 1);

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, memberId)).thenReturn(Optional.of(normalMember));
            when(guildPostRepository.findByGuildIdOrderByPinnedAndCreatedAt(1L, pageable)).thenReturn(postPage);
            when(reportService.isUnderReviewBatch(any(ReportTargetType.class), anyList())).thenReturn(Collections.emptyMap());

            // when
            Page<GuildPostListResponse> result = guildPostService.getPosts(1L, memberId, pageable);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("공지글 목록을 조회한다")
        void getNotices_success() {
            // given
            GuildPost noticePost = GuildPost.builder()
                .guild(testGuild)
                .authorId(masterId)
                .authorNickname("마스터")
                .title("공지사항")
                .content("중요 공지")
                .postType(GuildPostType.NOTICE)
                .isPinned(true)
                .build();
            setId(noticePost, 2L);

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, memberId)).thenReturn(Optional.of(normalMember));
            when(guildPostRepository.findNotices(1L)).thenReturn(List.of(noticePost));
            when(reportService.isUnderReviewBatch(any(ReportTargetType.class), anyList())).thenReturn(Collections.emptyMap());

            // when
            List<GuildPostListResponse> result = guildPostService.getNotices(1L, memberId);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPostType()).isEqualTo(GuildPostType.NOTICE);
        }

        @Test
        @DisplayName("게시글 상세를 조회하면 조회수가 증가한다")
        void getPost_incrementViewCount() {
            // given
            int initialViewCount = testPost.getViewCount();

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, memberId)).thenReturn(Optional.of(normalMember));
            when(guildPostRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(testPost));
            when(reportService.isUnderReview(any(ReportTargetType.class), anyString())).thenReturn(false);

            // when
            GuildPostResponse response = guildPostService.getPost(1L, 1L, memberId);

            // then
            assertThat(response).isNotNull();
            assertThat(testPost.getViewCount()).isEqualTo(initialViewCount + 1);
        }
    }

    @Nested
    @DisplayName("게시글 수정 테스트")
    class UpdatePostTest {

        @Test
        @DisplayName("작성자가 게시글을 수정한다")
        void updatePost_success() {
            // given
            GuildPostUpdateRequest request = GuildPostUpdateRequest.builder()
                .title("수정된 제목")
                .content("수정된 내용")
                .build();

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, memberId)).thenReturn(Optional.of(normalMember));
            when(guildPostRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(testPost));

            // when
            GuildPostResponse response = guildPostService.updatePost(1L, 1L, memberId, request);

            // then
            assertThat(response).isNotNull();
            assertThat(testPost.getTitle()).isEqualTo("수정된 제목");
            assertThat(testPost.getContent()).isEqualTo("수정된 내용");
        }

        @Test
        @DisplayName("작성자가 아니면 게시글을 수정할 수 없다")
        void updatePost_notAuthor_fail() {
            // given
            GuildPostUpdateRequest request = GuildPostUpdateRequest.builder()
                .title("수정된 제목")
                .content("수정된 내용")
                .build();

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, masterId)).thenReturn(Optional.of(masterMember));
            when(guildPostRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(testPost));

            // when & then
            assertThatThrownBy(() -> guildPostService.updatePost(1L, 1L, masterId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("게시글 작성자만 수정할 수 있습니다");
        }
    }

    @Nested
    @DisplayName("게시글 삭제 테스트")
    class DeletePostTest {

        @Test
        @DisplayName("작성자가 게시글을 삭제한다")
        void deletePost_byAuthor_success() {
            // given
            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, memberId)).thenReturn(Optional.of(normalMember));
            when(guildPostRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(testPost));

            // when
            guildPostService.deletePost(1L, 1L, memberId);

            // then
            assertThat(testPost.getIsDeleted()).isTrue();
        }

        @Test
        @DisplayName("마스터가 다른 사람 게시글을 삭제할 수 있다")
        void deletePost_byMaster_success() {
            // given
            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, masterId)).thenReturn(Optional.of(masterMember));
            when(guildPostRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(testPost));

            // when
            guildPostService.deletePost(1L, 1L, masterId);

            // then
            assertThat(testPost.getIsDeleted()).isTrue();
        }

        @Test
        @DisplayName("일반 멤버는 다른 사람 게시글을 삭제할 수 없다")
        void deletePost_byOtherMember_fail() {
            // given
            String otherMemberId = "other-member-id";
            GuildMember otherMember = GuildMember.builder()
                .guild(testGuild)
                .userId(otherMemberId)
                .role(GuildMemberRole.MEMBER)
                .status(GuildMemberStatus.ACTIVE)
                .build();

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, otherMemberId)).thenReturn(Optional.of(otherMember));
            when(guildPostRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(testPost));

            // when & then
            assertThatThrownBy(() -> guildPostService.deletePost(1L, 1L, otherMemberId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("게시글을 삭제할 권한이 없습니다");
        }
    }

    @Nested
    @DisplayName("게시글 상단 고정 테스트")
    class TogglePinTest {

        @Test
        @DisplayName("마스터가 게시글을 상단 고정한다")
        void togglePin_success() {
            // given
            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, masterId)).thenReturn(Optional.of(masterMember));
            when(guildPostRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(testPost));

            // when
            GuildPostResponse response = guildPostService.togglePin(1L, 1L, masterId);

            // then
            assertThat(response).isNotNull();
            assertThat(testPost.getIsPinned()).isTrue();
        }

        @Test
        @DisplayName("일반 멤버는 게시글을 상단 고정할 수 없다")
        void togglePin_notMaster_fail() {
            // given
            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, memberId)).thenReturn(Optional.of(normalMember));

            // when & then
            assertThatThrownBy(() -> guildPostService.togglePin(1L, 1L, memberId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("게시글 상단 고정은 길드 마스터 또는 부길드마스터만 할 수 있습니다");
        }
    }

    @Nested
    @DisplayName("댓글 작성 테스트")
    class CreateCommentTest {

        @Test
        @DisplayName("댓글을 작성한다")
        void createComment_success() {
            // given
            GuildPostCommentCreateRequest request = GuildPostCommentCreateRequest.builder()
                .content("테스트 댓글")
                .build();

            GuildPostComment savedComment = GuildPostComment.builder()
                .post(testPost)
                .authorId(memberId)
                .authorNickname("멤버닉네임")
                .content("테스트 댓글")
                .build();
            setId(savedComment, 1L);

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, memberId)).thenReturn(Optional.of(normalMember));
            when(guildPostRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(testPost));
            when(guildPostCommentRepository.save(any(GuildPostComment.class))).thenReturn(savedComment);

            // when
            GuildPostCommentResponse response = guildPostService.createComment(1L, 1L, memberId, "멤버닉네임", request);

            // then
            assertThat(response).isNotNull();
            verify(guildPostCommentRepository).save(any(GuildPostComment.class));
        }

        @Test
        @DisplayName("비회원은 댓글을 작성할 수 없다")
        void createComment_notMember_fail() {
            // given
            String nonMemberId = "non-member-id";
            GuildPostCommentCreateRequest request = GuildPostCommentCreateRequest.builder()
                .content("테스트 댓글")
                .build();

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, nonMemberId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> guildPostService.createComment(1L, 1L, nonMemberId, "닉네임", request))
                .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("댓글 조회 테스트")
    class GetCommentsTest {

        @Test
        @DisplayName("댓글 목록을 조회한다")
        void getComments_success() {
            // given
            GuildPostComment comment = GuildPostComment.builder()
                .post(testPost)
                .authorId(memberId)
                .authorNickname("멤버닉네임")
                .content("테스트 댓글")
                .build();
            setId(comment, 1L);

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, memberId)).thenReturn(Optional.of(normalMember));
            when(guildPostRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(testPost));
            when(guildPostCommentRepository.findAllByPostId(1L)).thenReturn(List.of(comment));
            when(guildPostCommentRepository.findRepliesByParentId(1L)).thenReturn(List.of());

            // when
            List<GuildPostCommentResponse> response = guildPostService.getComments(1L, 1L, memberId);

            // then
            assertThat(response).hasSize(1);
        }
    }

    @Nested
    @DisplayName("댓글 삭제 테스트")
    class DeleteCommentTest {

        @Test
        @DisplayName("작성자가 댓글을 삭제한다")
        void deleteComment_byAuthor_success() {
            // given
            GuildPostComment comment = GuildPostComment.builder()
                .post(testPost)
                .authorId(memberId)
                .authorNickname("멤버닉네임")
                .content("테스트 댓글")
                .build();
            setId(comment, 1L);

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, memberId)).thenReturn(Optional.of(normalMember));
            when(guildPostRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(testPost));
            when(guildPostCommentRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(comment));

            // when
            guildPostService.deleteComment(1L, 1L, 1L, memberId);

            // then
            assertThat(comment.getIsDeleted()).isTrue();
        }

        @Test
        @DisplayName("마스터가 다른 사람의 댓글을 삭제한다")
        void deleteComment_byMaster_success() {
            // given
            GuildPostComment comment = GuildPostComment.builder()
                .post(testPost)
                .authorId(memberId)  // 다른 사람이 작성
                .authorNickname("멤버닉네임")
                .content("테스트 댓글")
                .build();
            setId(comment, 1L);

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, masterId)).thenReturn(Optional.of(masterMember));
            when(guildPostRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(testPost));
            when(guildPostCommentRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(comment));

            // when
            guildPostService.deleteComment(1L, 1L, 1L, masterId);

            // then
            assertThat(comment.getIsDeleted()).isTrue();
        }

        @Test
        @DisplayName("다른 멤버의 댓글을 삭제할 수 없다")
        void deleteComment_byOtherMember_fail() {
            // given
            String otherMemberId = "other-member-id";
            GuildMember otherMember = GuildMember.builder()
                .guild(testGuild)
                .userId(otherMemberId)
                .role(GuildMemberRole.MEMBER)
                .status(GuildMemberStatus.ACTIVE)
                .build();

            GuildPostComment comment = GuildPostComment.builder()
                .post(testPost)
                .authorId(memberId)  // 다른 사람이 작성
                .authorNickname("멤버닉네임")
                .content("테스트 댓글")
                .build();
            setId(comment, 1L);

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, otherMemberId)).thenReturn(Optional.of(otherMember));
            when(guildPostRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(testPost));
            when(guildPostCommentRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(comment));

            // when & then
            assertThatThrownBy(() -> guildPostService.deleteComment(1L, 1L, 1L, otherMemberId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("댓글을 삭제할 권한이 없습니다.");
        }
    }

    @Nested
    @DisplayName("게시글 유형별 조회 테스트")
    class GetPostsByTypeTest {

        @Test
        @DisplayName("유형별 게시글을 조회한다")
        void getPostsByType_success() {
            // given
            GuildPost noticePost = GuildPost.builder()
                .guild(testGuild)
                .authorId(masterId)
                .authorNickname("마스터")
                .title("공지사항")
                .content("중요 공지")
                .postType(GuildPostType.NOTICE)
                .isPinned(true)
                .build();
            setId(noticePost, 2L);

            Pageable pageable = PageRequest.of(0, 10);
            Page<GuildPost> postPage = new PageImpl<>(List.of(noticePost), pageable, 1);

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, memberId)).thenReturn(Optional.of(normalMember));
            when(guildPostRepository.findByGuildIdAndPostType(1L, GuildPostType.NOTICE, pageable))
                .thenReturn(postPage);
            when(reportService.isUnderReviewBatch(any(ReportTargetType.class), anyList()))
                .thenReturn(Collections.emptyMap());

            // when
            Page<GuildPostListResponse> result = guildPostService.getPostsByType(1L, memberId, GuildPostType.NOTICE, pageable);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getPostType()).isEqualTo(GuildPostType.NOTICE);
        }
    }

    @Nested
    @DisplayName("게시글 검색 테스트")
    class SearchPostsTest {

        @Test
        @DisplayName("키워드로 게시글을 검색한다")
        void searchPosts_success() {
            // given
            String keyword = "테스트";
            Pageable pageable = PageRequest.of(0, 10);
            Page<GuildPost> postPage = new PageImpl<>(List.of(testPost), pageable, 1);

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, memberId)).thenReturn(Optional.of(normalMember));
            when(guildPostRepository.searchPosts(1L, keyword, pageable)).thenReturn(postPage);
            when(reportService.isUnderReviewBatch(any(ReportTargetType.class), anyList()))
                .thenReturn(Collections.emptyMap());

            // when
            Page<GuildPostListResponse> result = guildPostService.searchPosts(1L, memberId, keyword, pageable);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("대댓글 작성 테스트")
    class CreateReplyCommentTest {

        @Test
        @DisplayName("대댓글을 작성한다")
        void createComment_withParent_success() {
            // given
            GuildPostComment parentComment = GuildPostComment.builder()
                .post(testPost)
                .authorId(memberId)
                .authorNickname("멤버닉네임")
                .content("원댓글")
                .build();
            setId(parentComment, 1L);

            GuildPostCommentCreateRequest request = GuildPostCommentCreateRequest.builder()
                .content("대댓글 내용")
                .parentId(1L)
                .build();

            GuildPostComment replyComment = GuildPostComment.builder()
                .post(testPost)
                .authorId(memberId)
                .authorNickname("멤버닉네임")
                .content("대댓글 내용")
                .parent(parentComment)
                .build();
            setId(replyComment, 2L);

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, memberId)).thenReturn(Optional.of(normalMember));
            when(guildPostRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(testPost));
            when(guildPostCommentRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(parentComment));
            when(guildPostCommentRepository.save(any(GuildPostComment.class))).thenReturn(replyComment);

            // when
            GuildPostCommentResponse response = guildPostService.createComment(1L, 1L, memberId, "멤버닉네임", request);

            // then
            assertThat(response).isNotNull();
            verify(guildPostCommentRepository).save(any(GuildPostComment.class));
        }

        @Test
        @DisplayName("상위 댓글이 없으면 예외 발생")
        void createComment_parentNotFound_fail() {
            // given
            GuildPostCommentCreateRequest request = GuildPostCommentCreateRequest.builder()
                .content("대댓글 내용")
                .parentId(999L)
                .build();

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, memberId)).thenReturn(Optional.of(normalMember));
            when(guildPostRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(testPost));
            when(guildPostCommentRepository.findByIdAndIsDeletedFalse(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> guildPostService.createComment(1L, 1L, memberId, "멤버닉네임", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("상위 댓글을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("다른 게시글의 댓글을 상위 댓글로 지정하면 예외 발생")
        void createComment_parentFromOtherPost_fail() {
            // given
            GuildPost otherPost = GuildPost.builder()
                .guild(testGuild)
                .authorId(memberId)
                .authorNickname("테스터")
                .title("다른 게시글")
                .content("다른 내용")
                .postType(GuildPostType.NORMAL)
                .build();
            setId(otherPost, 2L);

            GuildPostComment otherComment = GuildPostComment.builder()
                .post(otherPost)
                .authorId(memberId)
                .authorNickname("멤버닉네임")
                .content("다른 게시글의 댓글")
                .build();
            setId(otherComment, 1L);

            GuildPostCommentCreateRequest request = GuildPostCommentCreateRequest.builder()
                .content("대댓글 내용")
                .parentId(1L)
                .build();

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, memberId)).thenReturn(Optional.of(normalMember));
            when(guildPostRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(testPost));
            when(guildPostCommentRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(otherComment));

            // when & then
            assertThatThrownBy(() -> guildPostService.createComment(1L, 1L, memberId, "멤버닉네임", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("상위 댓글이 해당 게시글에 속하지 않습니다");
        }
    }

    @Nested
    @DisplayName("댓글 수정 테스트")
    class UpdateCommentTest {

        @Test
        @DisplayName("댓글을 수정한다")
        void updateComment_success() {
            // given
            GuildPostComment comment = GuildPostComment.builder()
                .post(testPost)
                .authorId(memberId)
                .authorNickname("멤버닉네임")
                .content("원래 내용")
                .build();
            setId(comment, 1L);

            io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildPostCommentUpdateRequest request =
                io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildPostCommentUpdateRequest.builder()
                    .content("수정된 내용")
                    .build();

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, memberId)).thenReturn(Optional.of(normalMember));
            when(guildPostRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(testPost));
            when(guildPostCommentRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(comment));

            // when
            GuildPostCommentResponse response = guildPostService.updateComment(1L, 1L, 1L, memberId, request);

            // then
            assertThat(response).isNotNull();
            assertThat(comment.getContent()).isEqualTo("수정된 내용");
        }

        @Test
        @DisplayName("작성자가 아니면 댓글을 수정할 수 없다")
        void updateComment_notAuthor_fail() {
            // given
            GuildPostComment comment = GuildPostComment.builder()
                .post(testPost)
                .authorId(memberId)
                .authorNickname("멤버닉네임")
                .content("원래 내용")
                .build();
            setId(comment, 1L);

            io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildPostCommentUpdateRequest request =
                io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildPostCommentUpdateRequest.builder()
                    .content("수정된 내용")
                    .build();

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, masterId)).thenReturn(Optional.of(masterMember));
            when(guildPostRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(testPost));
            when(guildPostCommentRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(comment));

            // when & then
            assertThatThrownBy(() -> guildPostService.updateComment(1L, 1L, 1L, masterId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("댓글 작성자만 수정할 수 있습니다");
        }

        @Test
        @DisplayName("다른 게시글의 댓글을 수정할 수 없다")
        void updateComment_wrongPost_fail() {
            // given
            GuildPost otherPost = GuildPost.builder()
                .guild(testGuild)
                .authorId(memberId)
                .title("다른 게시글")
                .content("다른 내용")
                .postType(GuildPostType.NORMAL)
                .build();
            setId(otherPost, 2L);

            GuildPostComment comment = GuildPostComment.builder()
                .post(otherPost)
                .authorId(memberId)
                .authorNickname("멤버닉네임")
                .content("원래 내용")
                .build();
            setId(comment, 1L);

            io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildPostCommentUpdateRequest request =
                io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildPostCommentUpdateRequest.builder()
                    .content("수정된 내용")
                    .build();

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, memberId)).thenReturn(Optional.of(normalMember));
            when(guildPostRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(testPost));
            when(guildPostCommentRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(comment));

            // when & then
            assertThatThrownBy(() -> guildPostService.updateComment(1L, 1L, 1L, memberId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("댓글이 해당 게시글에 속하지 않습니다");
        }

        @Test
        @DisplayName("삭제된 댓글을 수정할 수 없다")
        void updateComment_deletedComment_fail() {
            // given
            io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildPostCommentUpdateRequest request =
                io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildPostCommentUpdateRequest.builder()
                    .content("수정된 내용")
                    .build();

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, memberId)).thenReturn(Optional.of(normalMember));
            when(guildPostRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(testPost));
            when(guildPostCommentRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> guildPostService.updateComment(1L, 1L, 1L, memberId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("댓글을 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("게시글/댓글 검증 테스트")
    class ValidationTest {

        @Test
        @DisplayName("존재하지 않는 게시글에 댓글 작성 시 예외 발생")
        void createComment_postNotFound_fail() {
            // given
            GuildPostCommentCreateRequest request = GuildPostCommentCreateRequest.builder()
                .content("테스트 댓글")
                .build();

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, memberId)).thenReturn(Optional.of(normalMember));
            when(guildPostRepository.findByIdAndIsDeletedFalse(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> guildPostService.createComment(1L, 999L, memberId, "멤버닉네임", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("게시글을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("다른 길드의 게시글에 접근 시 예외 발생")
        void getPost_wrongGuild_fail() {
            // given
            Guild otherGuild = Guild.builder()
                .name("다른 길드")
                .masterId("other-master")
                .build();
            setId(otherGuild, 2L);

            GuildPost otherPost = GuildPost.builder()
                .guild(otherGuild)
                .authorId(memberId)
                .title("다른 길드 게시글")
                .content("다른 내용")
                .postType(GuildPostType.NORMAL)
                .build();
            setId(otherPost, 2L);

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, memberId)).thenReturn(Optional.of(normalMember));
            when(guildPostRepository.findByIdAndIsDeletedFalse(2L)).thenReturn(Optional.of(otherPost));

            // when & then
            assertThatThrownBy(() -> guildPostService.getPost(1L, 2L, memberId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("게시글이 해당 길드에 속하지 않습니다");
        }

        @Test
        @DisplayName("삭제된 게시글은 조회할 수 없다")
        void getPost_deletedPost_fail() {
            // given
            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, memberId)).thenReturn(Optional.of(normalMember));
            when(guildPostRepository.findByIdAndIsDeletedFalse(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> guildPostService.getPost(1L, 999L, memberId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("게시글을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("존재하지 않는 길드에 게시글 작성 시 예외 발생")
        void createPost_guildNotFound_fail() {
            // given
            GuildPostCreateRequest request = GuildPostCreateRequest.builder()
                .title("테스트")
                .content("테스트")
                .postType(GuildPostType.NORMAL)
                .build();

            when(guildRepository.findByIdAndIsActiveTrue(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> guildPostService.createPost(999L, memberId, "테스터", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("길드를 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("신고 처리중 상태 통합 테스트")
    class IsUnderReviewIntegrationTest {

        @Test
        @DisplayName("게시글 상세 조회 시 신고 처리중 상태가 설정된다")
        void getPost_setsUnderReviewStatus() {
            // given
            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, memberId)).thenReturn(Optional.of(normalMember));
            when(guildPostRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(testPost));
            when(reportService.isUnderReview(eq(ReportTargetType.GUILD_NOTICE), eq("1"))).thenReturn(true);

            // when
            GuildPostResponse response = guildPostService.getPost(1L, 1L, memberId);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getIsUnderReview()).isTrue();
            verify(reportService).isUnderReview(ReportTargetType.GUILD_NOTICE, "1");
        }

        @Test
        @DisplayName("공지글 목록 조회 시 신고 상태가 일괄 조회된다")
        void getNotices_batchUnderReviewCheck() {
            // given
            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.findByGuildIdAndUserId(1L, memberId)).thenReturn(Optional.of(normalMember));
            when(guildPostRepository.findNotices(1L)).thenReturn(List.of(testPost));

            Map<String, Boolean> underReviewMap = new HashMap<>();
            underReviewMap.put("1", true);
            when(reportService.isUnderReviewBatch(eq(ReportTargetType.GUILD_NOTICE), anyList()))
                .thenReturn(underReviewMap);

            // when
            List<GuildPostListResponse> result = guildPostService.getNotices(1L, memberId);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getIsUnderReview()).isTrue();
            verify(reportService).isUnderReviewBatch(eq(ReportTargetType.GUILD_NOTICE), anyList());
        }
    }
}
