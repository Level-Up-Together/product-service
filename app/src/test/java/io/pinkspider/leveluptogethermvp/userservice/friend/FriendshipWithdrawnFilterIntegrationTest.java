package io.pinkspider.leveluptogethermvp.userservice.friend;

import static org.assertj.core.api.Assertions.assertThat;

import io.pinkspider.leveluptogethermvp.userservice.friend.domain.entity.Friendship;
import io.pinkspider.leveluptogethermvp.userservice.friend.infrastructure.FriendshipRepository;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.enums.UserStatus;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-340: 탈퇴 유저를 친구 목록/친구 수에서 제외하는 쿼리 검증.
 *
 * <p>{@code FriendshipRepository.ACTIVE_FRIEND_JOIN}은 CASE 식으로 상대방을 특정하는 엔티티 조인이라 단위 테스트(Mockito)로는
 * 검증되지 않는다. 실제 SQL 실행 결과를 확인하기 위해 통합 테스트로 둔다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "userTransactionManager")
class FriendshipWithdrawnFilterIntegrationTest {

    @Autowired private FriendshipRepository friendshipRepository;

    @Autowired private UserRepository userRepository;

    private String ownerId;
    private String withdrawnFriendId;
    private String withdrawnRequesterId;

    @BeforeEach
    void setUp() {
        ownerId = saveUser(UserStatus.ACTIVE);
        String activeFriendId = saveUser(UserStatus.ACTIVE);
        String suspendedFriendId = saveUser(UserStatus.SUSPENDED);
        withdrawnFriendId = saveUser(UserStatus.WITHDRAWN);
        withdrawnRequesterId = saveUser(UserStatus.WITHDRAWN);

        // 내가 보낸 요청 (CASE 의 THEN 분기 — 상대방은 friendId)
        saveAcceptedFriendship(ownerId, activeFriendId);
        saveAcceptedFriendship(ownerId, suspendedFriendId);
        saveAcceptedFriendship(ownerId, withdrawnFriendId);

        // 상대가 보낸 요청 (CASE 의 ELSE 분기 — 상대방은 userId)
        saveAcceptedFriendship(withdrawnRequesterId, ownerId);
    }

    @Test
    @DisplayName("친구 수는 탈퇴 유저를 제외하고, 정지 계정은 포함한다")
    void countFriends_excludesWithdrawnOnly() {
        assertThat(friendshipRepository.countFriends(ownerId)).isEqualTo(2);
    }

    @Test
    @DisplayName("친구 목록은 탈퇴 유저를 제외한다 — 어느 쪽이 요청자였는지와 무관하게")
    void findAllFriends_excludesWithdrawn() {
        List<Friendship> friends = friendshipRepository.findAllFriends(ownerId);

        assertThat(friends).hasSize(2);
        assertThat(friends)
                .noneMatch(
                        f ->
                                f.getUserId().equals(withdrawnFriendId)
                                        || f.getFriendId().equals(withdrawnFriendId)
                                        || f.getUserId().equals(withdrawnRequesterId)
                                        || f.getFriendId().equals(withdrawnRequesterId));
    }

    @Test
    @DisplayName("페이징 친구 목록의 totalElements 도 탈퇴 유저를 제외한 수와 일치한다")
    void findFriends_paged_countMatchesList() {
        var page = friendshipRepository.findFriends(ownerId, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(friendshipRepository.countFriends(ownerId));
    }

    private String saveUser(UserStatus status) {
        Users user =
                Users.builder()
                        .nickname("lut340-" + status.name().toLowerCase())
                        .email("lut340-" + status.name() + "-" + System.nanoTime() + "@test.com")
                        .provider("google")
                        .status(status)
                        .build();
        return userRepository.save(user).getId();
    }

    private void saveAcceptedFriendship(String requesterId, String recipientId) {
        Friendship friendship = Friendship.createRequest(requesterId, recipientId, null);
        friendship.accept();
        friendshipRepository.save(friendship);
    }
}
