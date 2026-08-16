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

    /**
     * LUT-340: 상대방이 탈퇴(WITHDRAWN)한 관계를 친구 목록/친구 수에서 제외하기 위한 조인 절.
     * 탈퇴는 soft delete(users.status)라 friendship row가 남으므로, 상대 유저 상태로 걸러야 한다.
     * SUSPENDED/PERMANENTLY_BANNED는 계정이 유지되는 상태이므로 친구로 계속 집계한다.
     */
    String ACTIVE_FRIEND_JOIN =
        "JOIN Users u ON u.id = CASE WHEN f.userId = :userId THEN f.friendId ELSE f.userId END "
            + "AND u.status <> 'WITHDRAWN' ";

    /**
     * LUT-383: 한 쌍에 행이 2개일 수 있다 — 상호 차단(BLOCKED 2행), 상대 차단 + 내
     * shadow 친구요청(BLOCKED+PENDING). 단건 Optional 조회는 이때 예외가 나므로,
     * 조회자(userId) 소유 행을 우선하는 정렬로 뽑아 첫 행을 쓴다 (내 상태가 내 화면의 진실).
     */
    @Query("SELECT f FROM Friendship f WHERE " +
           "(f.userId = :userId AND f.friendId = :friendId) OR " +
           "(f.userId = :friendId AND f.friendId = :userId) " +
           "ORDER BY CASE WHEN f.userId = :userId THEN 0 ELSE 1 END")
    List<Friendship> findFriendshipRows(
        @Param("userId") String userId,
        @Param("friendId") String friendId);

    // 두 사용자 간의 친구 관계 조회 (조회자 소유 행 우선)
    default Optional<Friendship> findFriendship(String userId, String friendId) {
        return findFriendshipRows(userId, friendId).stream().findFirst();
    }

    // 특정 사용자가 보낸 친구 요청
    Optional<Friendship> findByUserIdAndFriendId(String userId, String friendId);

    // 친구 목록 조회 (수락된 관계) — LUT-340: 탈퇴 유저 제외
    @Query(value = "SELECT f FROM Friendship f " + ACTIVE_FRIEND_JOIN +
           "WHERE (f.userId = :userId OR f.friendId = :userId) " +
           "AND f.status = 'ACCEPTED' " +
           "ORDER BY f.acceptedAt DESC",
           countQuery = "SELECT COUNT(f) FROM Friendship f " + ACTIVE_FRIEND_JOIN +
           "WHERE (f.userId = :userId OR f.friendId = :userId) " +
           "AND f.status = 'ACCEPTED'")
    Page<Friendship> findFriends(@Param("userId") String userId, Pageable pageable);

    // LUT-340: 탈퇴 유저 제외
    @Query("SELECT f FROM Friendship f " + ACTIVE_FRIEND_JOIN +
           "WHERE (f.userId = :userId OR f.friendId = :userId) " +
           "AND f.status = 'ACCEPTED'")
    List<Friendship> findAllFriends(@Param("userId") String userId);

    // 받은 친구 요청 (대기 중)
    // LUT-383: 내가 차단한 상대의 shadow 요청은 받은 목록에서 숨긴다 — 차단 해제 시 자연 노출
    @Query("SELECT f FROM Friendship f WHERE f.friendId = :userId AND f.status = 'PENDING' " +
           "AND NOT EXISTS (SELECT 1 FROM Friendship b WHERE b.userId = :userId " +
           "AND b.friendId = f.userId AND b.status = 'BLOCKED') " +
           "ORDER BY f.requestedAt DESC")
    List<Friendship> findPendingRequestsReceived(@Param("userId") String userId);

    // 보낸 친구 요청 (대기 중)
    @Query("SELECT f FROM Friendship f WHERE f.userId = :userId AND f.status = 'PENDING' " +
           "ORDER BY f.requestedAt DESC")
    List<Friendship> findPendingRequestsSent(@Param("userId") String userId);

    // 친구 수 조회 — LUT-340: 탈퇴 유저 제외 (친구 목록 개수와 일치시킨다)
    @Query("SELECT COUNT(f) FROM Friendship f " + ACTIVE_FRIEND_JOIN +
           "WHERE (f.userId = :userId OR f.friendId = :userId) " +
           "AND f.status = 'ACCEPTED'")
    int countFriends(@Param("userId") String userId);

    // 차단 목록 조회
    @Query("SELECT f FROM Friendship f WHERE f.userId = :userId AND f.status = 'BLOCKED'")
    List<Friendship> findBlockedUsers(@Param("userId") String userId);

    // LUT-367: 차단한 유저 ID 목록 (피드/댓글 콘텐츠 필터링용 — 파사드 경유)
    @Query("SELECT f.friendId FROM Friendship f WHERE f.userId = :userId AND f.status = 'BLOCKED'")
    List<String> findBlockedUserIds(@Param("userId") String userId);

    // 차단 여부 확인
    @Query("SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END FROM Friendship f " +
           "WHERE f.userId = :userId AND f.friendId = :targetId AND f.status = 'BLOCKED'")
    boolean isBlocked(@Param("userId") String userId, @Param("targetId") String targetId);

    // LUT-367: 양방향 차단 여부 — 어느 한쪽이라도 차단했는지 (DM/알림 차단 판정)
    @Query("SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END FROM Friendship f " +
           "WHERE ((f.userId = :userId1 AND f.friendId = :userId2) OR " +
           "(f.userId = :userId2 AND f.friendId = :userId1)) " +
           "AND f.status = 'BLOCKED'")
    boolean isBlockedBetween(@Param("userId1") String userId1, @Param("userId2") String userId2);

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
