package io.pinkspider.leveluptogethermvp.guildservice.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.admin.GuildPostAdminPageResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.admin.GuildPostAdminResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.admin.GuildPostCommentAdminPageResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.admin.GuildPostCommentAdminResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildPost;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildPostComment;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildPostType;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildVisibility;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildPostCommentRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildPostRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import java.util.List;
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
class GuildPostAdminInternalServiceTest {

    @Mock
    private GuildRepository guildRepository;

    @Mock
    private GuildPostRepository guildPostRepository;

    @Mock
    private GuildPostCommentRepository guildPostCommentRepository;

    @InjectMocks
    private GuildPostAdminInternalService service;

    private Guild createTestGuild(Long id) {
        Guild guild = Guild.builder()
            .name("테스트 길드")
            .description("설명")
            .visibility(GuildVisibility.PUBLIC)
            .masterId("master-1")
            .categoryId(1L)
            .isActive(true)
            .build();
        setId(guild, id);
        return guild;
    }

    private GuildPost createTestPost(Long id) {
        Guild guild = createTestGuild(1L);
        GuildPost post = GuildPost.builder()
            .guild(guild)
            .authorId("user-1")
            .authorNickname("작성자")
            .title("테스트 게시글")
            .content("내용")
            .postType(GuildPostType.NORMAL)
            .isDeleted(false)
            .commentCount(0)
            .build();
        setId(post, id);
        return post;
    }

    private GuildPostComment createTestComment(Long id) {
        GuildPost post = createTestPost(1L);
        GuildPostComment comment = GuildPostComment.builder()
            .post(post)
            .authorId("user-1")
            .authorNickname("작성자")
            .content("댓글 내용")
            .isDeleted(false)
            .build();
        setId(comment, id);
        return comment;
    }

    @Nested
    @DisplayName("getPostsByGuildId 테스트")
    class GetPostsByGuildIdTest {

        @Test
        @DisplayName("삭제된 글 포함하여 조회한다")
        void includeDeleted() {
            Pageable pageable = PageRequest.of(0, 10);
            GuildPost post = createTestPost(1L);
            when(guildPostRepository.findByGuildIdOrderByCreatedAtDesc(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(post)));

            GuildPostAdminPageResponse result = service.getPostsByGuildId(1L, true, pageable);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("삭제되지 않은 글만 조회한다")
        void excludeDeleted() {
            Pageable pageable = PageRequest.of(0, 10);
            when(guildPostRepository.findByGuildIdAndNotDeletedPaged(1L, pageable))
                .thenReturn(new PageImpl<>(List.of()));

            GuildPostAdminPageResponse result = service.getPostsByGuildId(1L, false, pageable);

            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("getAllPostsByGuildId 테스트")
    class GetAllPostsByGuildIdTest {

        @Test
        @DisplayName("전체 게시글을 조회한다")
        void getAllPosts() {
            GuildPost post = createTestPost(1L);
            when(guildPostRepository.findAllByGuildId(1L)).thenReturn(List.of(post));

            List<GuildPostAdminResponse> result = service.getAllPostsByGuildId(1L, true);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("삭제되지 않은 게시글만 조회한다")
        void getNotDeletedPosts() {
            when(guildPostRepository.findByGuildIdAndNotDeleted(1L)).thenReturn(List.of());

            List<GuildPostAdminResponse> result = service.getAllPostsByGuildId(1L, false);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("softDeletePost 테스트")
    class SoftDeletePostTest {

        @Test
        @DisplayName("게시글을 소프트 삭제한다")
        void softDelete() {
            GuildPost post = createTestPost(1L);
            when(guildPostRepository.findByIdAndGuildId(1L, 1L))
                .thenReturn(Optional.of(post));

            service.softDeletePost(1L, 1L);

            assertThat(post.getIsDeleted()).isTrue();
            verify(guildPostRepository).save(post);
        }

        @Test
        @DisplayName("존재하지 않는 게시글은 예외를 발생시킨다")
        void throwsWhenNotFound() {
            when(guildPostRepository.findByIdAndGuildId(999L, 1L))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.softDeletePost(1L, 999L))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("restorePost 테스트")
    class RestorePostTest {

        @Test
        @DisplayName("삭제된 게시글을 복원한다")
        void restore() {
            GuildPost post = createTestPost(1L);
            post.delete();
            when(guildPostRepository.findByIdAndGuildId(1L, 1L))
                .thenReturn(Optional.of(post));
            when(guildPostRepository.save(any(GuildPost.class))).thenReturn(post);

            GuildPostAdminResponse result = service.restorePost(1L, 1L);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("삭제되지 않은 게시글 복원 시 예외를 발생시킨다")
        void throwsWhenNotDeleted() {
            GuildPost post = createTestPost(1L);
            when(guildPostRepository.findByIdAndGuildId(1L, 1L))
                .thenReturn(Optional.of(post));

            assertThatThrownBy(() -> service.restorePost(1L, 1L))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("softDeleteComment 테스트")
    class SoftDeleteCommentTest {

        @Test
        @DisplayName("댓글을 소프트 삭제하고 게시글 댓글 수를 감소시킨다")
        void softDeleteWithDecrementCount() {
            GuildPostComment comment = createTestComment(1L);
            GuildPost post = createTestPost(1L);
            post.setCommentCount(3);

            when(guildPostCommentRepository.findByIdAndPostId(1L, 1L))
                .thenReturn(Optional.of(comment));
            when(guildPostRepository.findById(1L)).thenReturn(Optional.of(post));

            service.softDeleteComment(1L, 1L);

            assertThat(comment.getIsDeleted()).isTrue();
            assertThat(post.getCommentCount()).isEqualTo(2);
            verify(guildPostCommentRepository).save(comment);
            verify(guildPostRepository).save(post);
        }
    }

    @Nested
    @DisplayName("restoreComment 테스트")
    class RestoreCommentTest {

        @Test
        @DisplayName("삭제된 댓글을 복원하고 게시글 댓글 수를 증가시킨다")
        void restoreWithIncrementCount() {
            GuildPostComment comment = createTestComment(1L);
            comment.delete();
            GuildPost post = createTestPost(1L);
            post.setCommentCount(2);

            when(guildPostCommentRepository.findByIdAndPostId(1L, 1L))
                .thenReturn(Optional.of(comment));
            when(guildPostCommentRepository.save(any(GuildPostComment.class))).thenReturn(comment);
            when(guildPostRepository.findById(1L)).thenReturn(Optional.of(post));

            GuildPostCommentAdminResponse result = service.restoreComment(1L, 1L);

            assertThat(result).isNotNull();
            assertThat(post.getCommentCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("삭제되지 않은 댓글 복원 시 예외를 발생시킨다")
        void throwsWhenNotDeleted() {
            GuildPostComment comment = createTestComment(1L);
            when(guildPostCommentRepository.findByIdAndPostId(1L, 1L))
                .thenReturn(Optional.of(comment));

            assertThatThrownBy(() -> service.restoreComment(1L, 1L))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("getCommentsByPostId 테스트")
    class GetCommentsByPostIdTest {

        @Test
        @DisplayName("삭제된 댓글 포함하여 조회한다")
        void includeDeleted() {
            Pageable pageable = PageRequest.of(0, 10);
            GuildPostComment comment = createTestComment(1L);
            when(guildPostCommentRepository.findByPostIdOrderByCreatedAtAsc(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(comment)));

            GuildPostCommentAdminPageResponse result = service.getCommentsByPostId(1L, true, pageable);

            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("통계 테스트")
    class StatisticsTest {

        @Test
        @DisplayName("게시글 수를 반환한다")
        void countPosts() {
            when(guildPostRepository.countByGuildIdAndNotDeleted(1L)).thenReturn(5L);

            long result = service.countPostsByGuildId(1L);

            assertThat(result).isEqualTo(5L);
        }

        @Test
        @DisplayName("댓글 수를 반환한다")
        void countComments() {
            when(guildPostCommentRepository.countByGuildIdAndNotDeleted(1L)).thenReturn(10L);

            long result = service.countCommentsByGuildId(1L);

            assertThat(result).isEqualTo(10L);
        }
    }
}
