package io.pinkspider.leveluptogethermvp.guildservice.infrastructure;

import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildPost;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildPostType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface GuildPostRepository extends JpaRepository<GuildPost, Long> {

    Optional<GuildPost> findByIdAndIsDeletedFalse(Long id);

    @Query("SELECT p FROM GuildPost p WHERE p.guild.id = :guildId AND p.isDeleted = false " +
           "ORDER BY p.isPinned DESC, p.createdAt DESC")
    Page<GuildPost> findByGuildIdOrderByPinnedAndCreatedAt(
        @Param("guildId") Long guildId,
        Pageable pageable
    );

    @Query("SELECT p FROM GuildPost p WHERE p.guild.id = :guildId AND p.postType = :postType AND p.isDeleted = false " +
           "ORDER BY p.isPinned DESC, p.createdAt DESC")
    Page<GuildPost> findByGuildIdAndPostType(
        @Param("guildId") Long guildId,
        @Param("postType") GuildPostType postType,
        Pageable pageable
    );

    @Query("SELECT p FROM GuildPost p WHERE p.guild.id = :guildId AND p.isPinned = true AND p.isDeleted = false " +
           "ORDER BY p.createdAt DESC")
    List<GuildPost> findPinnedPosts(@Param("guildId") Long guildId);

    @Query("SELECT p FROM GuildPost p WHERE p.guild.id = :guildId AND p.postType = 'NOTICE' AND p.isDeleted = false " +
           "ORDER BY p.isPinned DESC, p.createdAt DESC")
    List<GuildPost> findNotices(@Param("guildId") Long guildId);

    @Query("SELECT p FROM GuildPost p WHERE p.guild.id = :guildId AND p.isDeleted = false AND " +
           "(LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY p.createdAt DESC")
    Page<GuildPost> searchPosts(
        @Param("guildId") Long guildId,
        @Param("keyword") String keyword,
        Pageable pageable
    );

    @Query("SELECT p FROM GuildPost p WHERE p.authorId = :authorId AND p.isDeleted = false " +
           "ORDER BY p.createdAt DESC")
    Page<GuildPost> findByAuthorId(@Param("authorId") String authorId, Pageable pageable);

    @Query("SELECT COUNT(p) FROM GuildPost p WHERE p.guild.id = :guildId AND p.isDeleted = false")
    long countByGuildId(@Param("guildId") Long guildId);

    @Modifying
    @Transactional(transactionManager = "guildTransactionManager")
    @Query("UPDATE GuildPost p SET p.authorNickname = :nickname WHERE p.authorId = :userId")
    int updateAuthorNicknameByUserId(@Param("userId") String userId, @Param("nickname") String nickname);
}
