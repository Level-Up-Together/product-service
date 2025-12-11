package io.pinkspider.leveluptogethermvp.guildservice.infrastructure;

import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildPostComment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GuildPostCommentRepository extends JpaRepository<GuildPostComment, Long> {

    Optional<GuildPostComment> findByIdAndIsDeletedFalse(Long id);

    @Query("SELECT c FROM GuildPostComment c WHERE c.post.id = :postId AND c.parent IS NULL AND c.isDeleted = false " +
           "ORDER BY c.createdAt ASC")
    Page<GuildPostComment> findRootCommentsByPostId(@Param("postId") Long postId, Pageable pageable);

    @Query("SELECT c FROM GuildPostComment c WHERE c.post.id = :postId AND c.isDeleted = false " +
           "ORDER BY c.createdAt ASC")
    List<GuildPostComment> findAllByPostId(@Param("postId") Long postId);

    @Query("SELECT c FROM GuildPostComment c WHERE c.parent.id = :parentId AND c.isDeleted = false " +
           "ORDER BY c.createdAt ASC")
    List<GuildPostComment> findRepliesByParentId(@Param("parentId") Long parentId);

    @Query("SELECT COUNT(c) FROM GuildPostComment c WHERE c.post.id = :postId AND c.isDeleted = false")
    long countByPostId(@Param("postId") Long postId);

    @Query("SELECT c FROM GuildPostComment c WHERE c.authorId = :authorId AND c.isDeleted = false " +
           "ORDER BY c.createdAt DESC")
    Page<GuildPostComment> findByAuthorId(@Param("authorId") String authorId, Pageable pageable);
}
