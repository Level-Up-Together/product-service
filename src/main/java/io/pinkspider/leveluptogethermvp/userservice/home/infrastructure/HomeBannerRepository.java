package io.pinkspider.leveluptogethermvp.userservice.home.infrastructure;

import io.pinkspider.leveluptogethermvp.userservice.home.domain.entity.HomeBanner;
import io.pinkspider.leveluptogethermvp.userservice.home.domain.enums.BannerType;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface HomeBannerRepository extends JpaRepository<HomeBanner, Long> {

    /**
     * 현재 활성화된 배너 목록 조회 (기간 내)
     */
    @Query("""
        SELECT b FROM HomeBanner b
        WHERE b.isActive = true
          AND (b.startDate IS NULL OR b.startDate <= :now)
          AND (b.endDate IS NULL OR b.endDate >= :now)
        ORDER BY b.sortOrder ASC, b.createdAt DESC
        """)
    List<HomeBanner> findActiveBanners(@Param("now") LocalDateTime now);

    /**
     * 특정 유형의 활성화된 배너 목록 조회
     */
    @Query("""
        SELECT b FROM HomeBanner b
        WHERE b.isActive = true
          AND b.bannerType = :bannerType
          AND (b.startDate IS NULL OR b.startDate <= :now)
          AND (b.endDate IS NULL OR b.endDate >= :now)
        ORDER BY b.sortOrder ASC, b.createdAt DESC
        """)
    List<HomeBanner> findActiveBannersByType(
        @Param("bannerType") BannerType bannerType,
        @Param("now") LocalDateTime now);

    /**
     * 특정 길드의 배너 조회
     */
    List<HomeBanner> findByGuildIdAndIsActiveTrue(Long guildId);

    /**
     * 모든 배너 목록 (관리자용)
     */
    Page<HomeBanner> findAllByOrderBySortOrderAscCreatedAtDesc(Pageable pageable);

    /**
     * 배너 유형별 목록 (관리자용)
     */
    Page<HomeBanner> findByBannerTypeOrderBySortOrderAscCreatedAtDesc(BannerType bannerType, Pageable pageable);
}
