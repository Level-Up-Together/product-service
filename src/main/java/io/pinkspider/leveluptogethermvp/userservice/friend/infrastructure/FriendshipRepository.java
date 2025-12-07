package io.pinkspider.leveluptogethermvp.userservice.friend.infrastructure;

import io.pinkspider.leveluptogethermvp.userservice.friend.domain.entity.Friendship;
import io.pinkspider.leveluptogethermvp.userservice.friend.domain.enums.FriendshipStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    // 두 사용자 간의 친구 관계 조회
    @Query("SELECT f FROM Friendship f WHERE " +
           "(f.userId = :userId AND f.friendId = :friendId) OR " +
           "(f.userId = :friendId AND f.friendId = :userId)")
    Optional<Friendship> findFriendship(
        @Param("userId") String userId,
        @Param("friendId") String friendId);

    // 특정 사용자가 보낸 친구 요청
    Optional<Friendship> findByUserIdAndFriendId(String userId, String friendId);

    // 친구 목록 조회 (수락된 관계)
    @Query("SELECT f FROM Friendship f WHERE " +
           "(f.userId = :userId OR f.friendId = :userId) " +
           "AND f.status = 'ACCEPTED' " +
           "ORDER BY f.acceptedAt DESC")
    Page<Friendship> findFriends(@Param("userId") String userId, Pageable pageable);

    @Query("SELECT f FROM Friendship f WHERE " +
           "(f.userId = :userId OR f.friendId = :userId) " +
           "AND f.status = 'ACCEPTED'")
    List<Friendship> findAllFriends(@Param("userId") String userId);

    // 받은 친구 요청 (대기 중)
    @Query("SELECT f FROM Friendship f WHERE f.friendId = :userId AND f.status = 'PENDING' " +
           "ORDER BY f.requestedAt DESC")
    List<Friendship> findPendingRequestsReceived(@Param("userId") String userId);

    // 보낸 친구 요청 (대기 중)
    @Query("SELECT f FROM Friendship f WHERE f.userId = :userId AND f.status = 'PENDING' " +
           "ORDER BY f.requestedAt DESC")
    List<Friendship> findPendingRequestsSent(@Param("userId") String userId);

    // 친구 수 조회
    @Query("SELECT COUNT(f) FROM Friendship f WHERE " +
           "(f.userId = :userId OR f.friendId = :userId) " +
           "AND f.status = 'ACCEPTED'")
    int countFriends(@Param("userId") String userId);

    // 차단 목록 조회
    @Query("SELECT f FROM Friendship f WHERE f.userId = :userId AND f.status = 'BLOCKED'")
    List<Friendship> findBlockedUsers(@Param("userId") String userId);

    // 차단 여부 확인
    @Query("SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END FROM Friendship f " +
           "WHERE f.userId = :userId AND f.friendId = :targetId AND f.status = 'BLOCKED'")
    boolean isBlocked(@Param("userId") String userId, @Param("targetId") String targetId);

    // 친구 여부 확인
    @Query("SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END FROM Friendship f " +
           "WHERE ((f.userId = :userId AND f.friendId = :friendId) OR " +
           "(f.userId = :friendId AND f.friendId = :userId)) " +
           "AND f.status = 'ACCEPTED'")
    boolean areFriends(@Param("userId") String userId, @Param("friendId") String friendId);

    // 친구 ID 목록 조회 (피드 조회용)
    @Query("SELECT CASE WHEN f.userId = :userId THEN f.friendId ELSE f.userId END " +
           "FROM Friendship f WHERE " +
           "(f.userId = :userId OR f.friendId = :userId) " +
           "AND f.status = 'ACCEPTED'")
    List<String> findFriendIds(@Param("userId") String userId);
}
