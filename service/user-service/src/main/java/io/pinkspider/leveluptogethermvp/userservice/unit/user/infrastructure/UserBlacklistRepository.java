package io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure;

import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.UserBlacklist;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.enums.BlacklistType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserBlacklistRepository extends JpaRepository<UserBlacklist, Long> {

    Optional<UserBlacklist> findByUserIdAndIsActiveTrue(String userId);

    List<UserBlacklist> findAllByUserIdOrderByCreatedAtDesc(String userId);

    boolean existsByUserIdAndIsActiveTrue(String userId);

    @Modifying
    @Query("UPDATE UserBlacklist b SET b.isActive = false WHERE b.userId = :userId AND b.isActive = true")
    int deactivateAllByUserId(@Param("userId") String userId);

    Page<UserBlacklist> findAllByIsActiveTrueOrderByCreatedAtDesc(Pageable pageable);

    Page<UserBlacklist> findAllByIsActiveTrueAndBlacklistTypeOrderByCreatedAtDesc(
        BlacklistType blacklistType, Pageable pageable);

    Page<UserBlacklist> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("""
        SELECT b FROM UserBlacklist b
        WHERE b.isActive = true
        AND b.startedAt >= :startDate AND b.startedAt < :endDate
        ORDER BY b.createdAt DESC
        """)
    Page<UserBlacklist> findByStartedAtBetweenAndIsActiveTrue(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        Pageable pageable);

    @Query("""
        SELECT b FROM UserBlacklist b
        WHERE b.isActive = true
        AND b.blacklistType = :blacklistType
        AND b.startedAt >= :startDate AND b.startedAt < :endDate
        ORDER BY b.createdAt DESC
        """)
    Page<UserBlacklist> findByStartedAtBetweenAndBlacklistTypeAndIsActiveTrue(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        @Param("blacklistType") BlacklistType blacklistType,
        Pageable pageable);

    @Query("""
        SELECT b FROM UserBlacklist b
        WHERE b.startedAt >= :startDate AND b.startedAt < :endDate
        ORDER BY b.createdAt DESC
        """)
    Page<UserBlacklist> findByStartedAtBetween(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        Pageable pageable);
}
