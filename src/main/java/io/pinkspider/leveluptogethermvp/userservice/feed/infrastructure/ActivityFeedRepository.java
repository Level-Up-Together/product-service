package io.pinkspider.leveluptogethermvp.userservice.feed.infrastructure;

import io.pinkspider.leveluptogethermvp.userservice.feed.domain.entity.ActivityFeed;
import io.pinkspider.leveluptogethermvp.userservice.feed.domain.enums.ActivityType;
import io.pinkspider.leveluptogethermvp.userservice.feed.domain.enums.FeedVisibility;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ActivityFeedRepository extends JpaRepository<ActivityFeed, Long> {

    // 전체 공개 피드 조회
    @Query("SELECT f FROM ActivityFeed f WHERE f.visibility = 'PUBLIC' ORDER BY f.createdAt DESC")
    Page<ActivityFeed> findPublicFeeds(Pageable pageable);

    // 사용자의 피드 조회
    @Query("SELECT f FROM ActivityFeed f WHERE f.userId = :userId ORDER BY f.createdAt DESC")
    Page<ActivityFeed> findByUserId(@Param("userId") String userId, Pageable pageable);

    // 친구 피드 조회 (공개 + 친구공개)
    @Query("SELECT f FROM ActivityFeed f WHERE f.userId IN :friendIds " +
           "AND f.visibility IN ('PUBLIC', 'FRIENDS') ORDER BY f.createdAt DESC")
    Page<ActivityFeed> findFriendsFeeds(@Param("friendIds") List<String> friendIds, Pageable pageable);

    // 길드 피드 조회
    @Query("SELECT f FROM ActivityFeed f WHERE f.guildId = :guildId " +
           "AND f.visibility IN ('PUBLIC', 'GUILD') ORDER BY f.createdAt DESC")
    Page<ActivityFeed> findGuildFeeds(@Param("guildId") Long guildId, Pageable pageable);

    // 내 타임라인 피드 조회 (내 피드 + 친구 피드)
    @Query("SELECT f FROM ActivityFeed f WHERE " +
           "(f.userId = :userId) OR " +
           "(f.userId IN :friendIds AND f.visibility IN ('PUBLIC', 'FRIENDS')) " +
           "ORDER BY f.createdAt DESC")
    Page<ActivityFeed> findTimelineFeeds(
        @Param("userId") String userId,
        @Param("friendIds") List<String> friendIds,
        Pageable pageable);

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

    // ID 목록으로 피드 조회 (Featured Feed 조회용)
    @Query("SELECT f FROM ActivityFeed f WHERE f.id IN :feedIds ORDER BY f.createdAt DESC")
    List<ActivityFeed> findByIdIn(@Param("feedIds") List<Long> feedIds);

    // 사용자의 모든 피드의 userTitle 업데이트
    @Modifying
    @Query("UPDATE ActivityFeed f SET f.userTitle = :userTitle WHERE f.userId = :userId")
    int updateUserTitleByUserId(@Param("userId") String userId, @Param("userTitle") String userTitle);
}
