package io.pinkspider.leveluptogethermvp.guildservice.infrastructure;

import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildVisibility;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GuildRepository extends JpaRepository<Guild, Long> {

    Optional<Guild> findByIdAndIsActiveTrue(Long id);

    @Query("SELECT g FROM Guild g WHERE g.visibility = :visibility AND g.isActive = true")
    Page<Guild> findByVisibilityAndIsActiveTrue(@Param("visibility") GuildVisibility visibility, Pageable pageable);

    @Query("SELECT g FROM Guild g WHERE g.isActive = true AND " +
           "(g.visibility = 'PUBLIC' OR g.masterId = :userId)")
    Page<Guild> findAccessibleGuilds(@Param("userId") String userId, Pageable pageable);

    @Query("SELECT g FROM Guild g WHERE g.visibility = 'PUBLIC' AND g.isActive = true")
    Page<Guild> findPublicGuilds(Pageable pageable);

    @Query("SELECT g FROM Guild g WHERE g.masterId = :userId AND g.isActive = true")
    List<Guild> findByMasterId(@Param("userId") String userId);

    /**
     * 여러 길드 ID로 활성 길드 배치 조회 (N+1 방지)
     */
    @Query("SELECT g FROM Guild g WHERE g.id IN :guildIds AND g.isActive = true")
    List<Guild> findByIdInAndIsActiveTrue(@Param("guildIds") List<Long> guildIds);

    boolean existsByIdAndIsActiveTrue(Long id);

    boolean existsByNameAndIsActiveTrue(String name);

    @Query("SELECT g FROM Guild g WHERE g.isActive = true AND " +
           "(LOWER(g.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(g.description) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
           "g.visibility = 'PUBLIC'")
    Page<Guild> searchPublicGuilds(@Param("keyword") String keyword, Pageable pageable);

    /**
     * 거점이 설정된 모든 활성 길드 조회 (거점 검증용)
     */
    @Query("SELECT g FROM Guild g WHERE g.isActive = true " +
           "AND g.baseLatitude IS NOT NULL AND g.baseLongitude IS NOT NULL")
    List<Guild> findAllWithHeadquarters();

    /**
     * 특정 길드 제외, 거점이 설정된 모든 활성 길드 조회
     */
    @Query("SELECT g FROM Guild g WHERE g.isActive = true " +
           "AND g.baseLatitude IS NOT NULL AND g.baseLongitude IS NOT NULL " +
           "AND g.id != :excludeGuildId")
    List<Guild> findAllWithHeadquartersExcluding(@Param("excludeGuildId") Long excludeGuildId);

    /**
     * 카테고리별 공개 길드 조회 (멤버 수 기준 정렬)
     * 서브쿼리로 멤버 수 계산하여 정렬
     */
    @Query("""
        SELECT g FROM Guild g
        WHERE g.categoryId = :categoryId
        AND g.visibility = 'PUBLIC'
        AND g.isActive = true
        ORDER BY (SELECT COUNT(m) FROM GuildMember m
                  WHERE m.guild = g AND m.status = 'ACTIVE') DESC
        """)
    List<Guild> findPublicGuildsByCategoryOrderByMemberCount(
        @Param("categoryId") Long categoryId, Pageable pageable);

    // ========== Admin Internal API 쿼리 ==========

    @Query("SELECT g FROM Guild g WHERE " +
           "(:keyword IS NULL OR :keyword = '' OR LOWER(g.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(g.description) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:hasCategoryFilter = false OR g.categoryId IN :categoryIds) " +
           "AND (:isActive IS NULL OR g.isActive = :isActive) " +
           "AND (:visibility IS NULL OR g.visibility = :visibility)")
    Page<Guild> searchGuildsForAdmin(
        @Param("keyword") String keyword,
        @Param("hasCategoryFilter") boolean hasCategoryFilter,
        @Param("categoryIds") List<Long> categoryIds,
        @Param("isActive") Boolean isActive,
        @Param("visibility") GuildVisibility visibility,
        Pageable pageable
    );

    long countByIsActiveTrue();

    long countByIsActiveFalse();

    long countByVisibility(GuildVisibility visibility);

    @Query("SELECT COUNT(g) FROM Guild g WHERE g.createdAt >= :date")
    long countByCreatedAtAfter(@Param("date") LocalDateTime date);

    @Query("SELECT g.categoryId, COUNT(g) FROM Guild g WHERE g.isActive = true GROUP BY g.categoryId")
    List<Object[]> countGuildsByCategory();

    @Query("SELECT CAST(g.createdAt AS date), COUNT(g) FROM Guild g " +
           "WHERE g.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY CAST(g.createdAt AS date) " +
           "ORDER BY CAST(g.createdAt AS date)")
    List<Object[]> countDailyNewGuilds(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
}
