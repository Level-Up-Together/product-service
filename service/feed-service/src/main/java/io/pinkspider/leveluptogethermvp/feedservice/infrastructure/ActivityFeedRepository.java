package io.pinkspider.leveluptogethermvp.feedservice.infrastructure;

import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.ActivityFeed;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.ActivityType;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.FeedVisibility;
import java.time.LocalDateTime;
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
public interface ActivityFeedRepository extends JpaRepository<ActivityFeed, Long> {

    // 전체 공개 피드 조회
    @Query("SELECT f FROM ActivityFeed f WHERE f.visibility = 'PUBLIC' ORDER BY f.createdAt DESC")
    Page<ActivityFeed> findPublicFeeds(Pageable pageable);

    // 전체 공개 피드 조회 (시간 범위 필터)
    @Query("SELECT f FROM ActivityFeed f WHERE f.visibility = 'PUBLIC' " +
           "AND f.createdAt >= :startTime AND f.createdAt < :endTime " +
           "ORDER BY f.createdAt DESC")
    Page<ActivityFeed> findPublicFeedsInTimeRange(
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime,
        Pageable pageable);

    // 사용자의 피드 조회 (전체 — 마이페이지 내가 쓴 글용)
    @Query("SELECT f FROM ActivityFeed f WHERE f.userId = :userId ORDER BY f.createdAt DESC")
    Page<ActivityFeed> findByUserId(@Param("userId") String userId, Pageable pageable);

    // 사용자의 공개 피드 조회 (PRIVATE 제외 — 홈피드 MINE 필터용)
    @Query("SELECT f FROM ActivityFeed f WHERE f.userId = :userId " +
           "AND f.visibility != 'PRIVATE' ORDER BY f.createdAt DESC")
    Page<ActivityFeed> findPublicFeedsByUserId(@Param("userId") String userId, Pageable pageable);

    // 친구 피드 조회 (공개 + 친구공개) — 타임라인용
    @Query("SELECT f FROM ActivityFeed f WHERE f.userId IN :friendIds " +
           "AND f.visibility IN ('PUBLIC', 'FRIENDS') ORDER BY f.createdAt DESC")
    Page<ActivityFeed> findFriendsFeeds(@Param("friendIds") List<String> friendIds, Pageable pageable);

    // 친구공개 피드만 조회 — FRIENDS 필터 탭용
    @Query("SELECT f FROM ActivityFeed f WHERE f.userId IN :friendIds " +
           "AND f.visibility = 'FRIENDS' ORDER BY f.createdAt DESC")
    Page<ActivityFeed> findFriendsOnlyFeeds(@Param("friendIds") List<String> friendIds, Pageable pageable);

    // 길드 피드 조회 (멤버용: PUBLIC + GUILD)
    @Query("SELECT f FROM ActivityFeed f WHERE f.guildId = :guildId " +
           "AND f.visibility IN ('PUBLIC', 'GUILD') ORDER BY f.createdAt DESC")
    Page<ActivityFeed> findGuildFeeds(@Param("guildId") Long guildId, Pageable pageable);

    // 길드 피드 조회 (비멤버용: PUBLIC만)
    @Query("SELECT f FROM ActivityFeed f WHERE f.guildId = :guildId " +
           "AND f.visibility = 'PUBLIC' ORDER BY f.createdAt DESC")
    Page<ActivityFeed> findPublicFeedsByGuildId(@Param("guildId") Long guildId, Pageable pageable);

    // 내 타임라인 피드 조회 (내 피드 + 친구 피드)
    @Query("SELECT f FROM ActivityFeed f WHERE " +
           "(f.userId = :userId) OR " +
           "(f.userId IN :friendIds AND f.visibility IN ('PUBLIC', 'FRIENDS')) " +
           "ORDER BY f.createdAt DESC")
    Page<ActivityFeed> findTimelineFeeds(
        @Param("userId") String userId,
        @Param("friendIds") List<String> friendIds,
        Pageable pageable);

    // 유저가 속한 길드들의 피드 조회 (공개 + 길드공개) — 길드 상세 등
    @Query("SELECT f FROM ActivityFeed f WHERE f.guildId IN :guildIds " +
           "AND f.visibility IN ('PUBLIC', 'GUILD') ORDER BY f.createdAt DESC")
    Page<ActivityFeed> findGuildFeedsByGuildIds(
        @Param("guildIds") List<Long> guildIds, Pageable pageable);

    // 길드공개 피드만 조회 — GUILD 필터 탭용
    @Query("SELECT f FROM ActivityFeed f WHERE f.guildId IN :guildIds " +
           "AND f.visibility = 'GUILD' ORDER BY f.createdAt DESC")
    Page<ActivityFeed> findGuildOnlyFeedsByGuildIds(
        @Param("guildIds") List<Long> guildIds, Pageable pageable);

    // 특정 타입 피드 조회
    Page<ActivityFeed> findByActivityTypeAndVisibilityOrderByCreatedAtDesc(
        ActivityType activityType, FeedVisibility visibility, Pageable pageable);

    // 카테고리별 피드 조회
    @Query("SELECT f FROM ActivityFeed f WHERE f.activityType IN :types " +
           "AND f.visibility = 'PUBLIC' ORDER BY f.createdAt DESC")
    Page<ActivityFeed> findByCategoryTypes(
        @Param("types") List<ActivityType> types, Pageable pageable);

    // 검색 기능 - 제목(미션명) 기준 검색 (전체 카테고리)
    @Query("SELECT f FROM ActivityFeed f WHERE f.visibility = 'PUBLIC' " +
           "AND LOWER(f.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "ORDER BY f.createdAt DESC")
    Page<ActivityFeed> searchByKeyword(
        @Param("keyword") String keyword, Pageable pageable);

    // 검색 기능 - 제목(미션명) 기준 검색 (카테고리 내 검색)
    @Query("SELECT f FROM ActivityFeed f WHERE f.visibility = 'PUBLIC' " +
           "AND f.activityType IN :types " +
           "AND LOWER(f.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "ORDER BY f.createdAt DESC")
    Page<ActivityFeed> searchByKeywordAndCategory(
        @Param("keyword") String keyword,
        @Param("types") List<ActivityType> types,
        Pageable pageable);

    // 카테고리별 공개 피드 조회 (미션 카테고리 기준)
    @Query("SELECT f FROM ActivityFeed f WHERE f.visibility = 'PUBLIC' " +
           "AND f.categoryId = :categoryId ORDER BY f.createdAt DESC")
    Page<ActivityFeed> findPublicFeedsByCategoryId(
        @Param("categoryId") Long categoryId, Pageable pageable);

    // 카테고리별 공개 피드 조회 (시간 범위 필터)
    @Query("SELECT f FROM ActivityFeed f WHERE f.visibility = 'PUBLIC' " +
           "AND f.categoryId = :categoryId " +
           "AND f.createdAt >= :startTime AND f.createdAt < :endTime " +
           "ORDER BY f.createdAt DESC")
    Page<ActivityFeed> findPublicFeedsByCategoryIdInTimeRange(
        @Param("categoryId") Long categoryId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime,
        Pageable pageable);

    // ID 목록으로 피드 조회 (Featured Feed 조회용)
    @Query("SELECT f FROM ActivityFeed f WHERE f.id IN :feedIds ORDER BY f.createdAt DESC")
    List<ActivityFeed> findByIdIn(@Param("feedIds") List<Long> feedIds);

    // 사용자의 모든 피드의 칭호 정보 업데이트 (조합 + 좌/우 개별)
    @Modifying
    @Transactional(transactionManager = "feedTransactionManager")
    @Query("UPDATE ActivityFeed f SET f.userTitle = :userTitle, f.userTitleRarity = :userTitleRarity, f.userTitleColorCode = :userTitleColorCode, " +
           "f.userLeftTitle = :userLeftTitle, f.userLeftTitleRarity = :userLeftTitleRarity, " +
           "f.userRightTitle = :userRightTitle, f.userRightTitleRarity = :userRightTitleRarity " +
           "WHERE f.userId = :userId")
    int updateUserTitleByUserId(
        @Param("userId") String userId,
        @Param("userTitle") String userTitle,
        @Param("userTitleRarity") TitleRarity userTitleRarity,
        @Param("userTitleColorCode") String userTitleColorCode,
        @Param("userLeftTitle") String userLeftTitle,
        @Param("userLeftTitleRarity") TitleRarity userLeftTitleRarity,
        @Param("userRightTitle") String userRightTitle,
        @Param("userRightTitleRarity") TitleRarity userRightTitleRarity);

    // referenceId로 피드 삭제 (미션 삭제 시 관련 피드 삭제용)
    @Modifying
    @Transactional(transactionManager = "feedTransactionManager")
    @Query("DELETE FROM ActivityFeed f WHERE f.referenceId = :referenceId AND f.referenceType = :referenceType")
    int deleteByReferenceIdAndReferenceType(
        @Param("referenceId") Long referenceId,
        @Param("referenceType") String referenceType);

    // missionId로 피드 삭제 (미션 삭제 시 관련 피드 삭제용)
    @Modifying
    @Transactional(transactionManager = "feedTransactionManager")
    @Query("DELETE FROM ActivityFeed f WHERE f.missionId = :missionId")
    int deleteByMissionId(@Param("missionId") Long missionId);

    // executionId로 피드 조회 (mission → feed 단방향 조회용)
    // executionId로 피드 조회 (중복 방지: 최신 1건)
    Optional<ActivityFeed> findFirstByExecutionIdOrderByCreatedAtDesc(Long executionId);

    // 사용자의 모든 피드의 프로필 스냅샷 업데이트
    @Modifying
    @Transactional(transactionManager = "feedTransactionManager")
    @Query("UPDATE ActivityFeed f SET f.userNickname = :nickname, f.userProfileImageUrl = :profileImageUrl, f.userLevel = :level WHERE f.userId = :userId")
    int updateUserProfileByUserId(
        @Param("userId") String userId,
        @Param("nickname") String nickname,
        @Param("profileImageUrl") String profileImageUrl,
        @Param("level") Integer level);

    // ===== Admin 내부 API용 쿼리 =====

    // Admin 피드 검색 (optional 필터 + 페이징)
    @Query("SELECT f FROM ActivityFeed f " +
           "WHERE (:activityType IS NULL OR f.activityType = :activityType) " +
           "AND (:visibility IS NULL OR f.visibility = :visibility) " +
           "AND (:userId IS NULL OR f.userId = :userId) " +
           "AND (:categoryId IS NULL OR f.categoryId = :categoryId) " +
           "AND (:keyword IS NULL OR f.title LIKE %:keyword% OR f.description LIKE %:keyword% OR f.userNickname LIKE %:keyword%)")
    Page<ActivityFeed> searchFeedsForAdmin(
        @Param("activityType") ActivityType activityType,
        @Param("visibility") FeedVisibility visibility,
        @Param("userId") String userId,
        @Param("categoryId") Long categoryId,
        @Param("keyword") String keyword,
        Pageable pageable);

    // Admin 통계: 공개 범위별 카운트
    long countByVisibility(FeedVisibility visibility);

    // Admin 통계: 활동 타입별 카운트
    long countByActivityType(ActivityType activityType);

    // Admin 통계: 특정 시간 이후 생성된 피드 카운트
    @Query("SELECT COUNT(f) FROM ActivityFeed f WHERE f.createdAt >= :since")
    long countByCreatedAtSince(@Param("since") LocalDateTime since);
}
