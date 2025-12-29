package io.pinkspider.leveluptogethermvp.guildservice.infrastructure;

import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildVisibility;
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
}
