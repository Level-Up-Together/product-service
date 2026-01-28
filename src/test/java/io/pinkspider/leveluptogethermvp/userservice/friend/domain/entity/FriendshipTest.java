package io.pinkspider.leveluptogethermvp.userservice.friend.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.pinkspider.leveluptogethermvp.userservice.friend.domain.enums.FriendshipStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Friendship 엔티티 테스트")
class FriendshipTest {

    private static final String USER_ID = "user-123";
    private static final String FRIEND_ID = "friend-456";

    @Nested
    @DisplayName("isRejected 메서드 테스트")
    class IsRejectedTest {

        @Test
        @DisplayName("REJECTED 상태면 true를 반환한다")
        void isRejected_whenRejected_returnsTrue() {
            // given
            Friendship friendship = Friendship.builder()
                .userId(USER_ID)
                .friendId(FRIEND_ID)
                .status(FriendshipStatus.REJECTED)
                .build();

            // when & then
            assertThat(friendship.isRejected()).isTrue();
        }

        @Test
        @DisplayName("PENDING 상태면 false를 반환한다")
        void isRejected_whenPending_returnsFalse() {
            // given
            Friendship friendship = Friendship.builder()
                .userId(USER_ID)
                .friendId(FRIEND_ID)
                .status(FriendshipStatus.PENDING)
                .build();

            // when & then
            assertThat(friendship.isRejected()).isFalse();
        }

        @Test
        @DisplayName("ACCEPTED 상태면 false를 반환한다")
        void isRejected_whenAccepted_returnsFalse() {
            // given
            Friendship friendship = Friendship.builder()
                .userId(USER_ID)
                .friendId(FRIEND_ID)
                .status(FriendshipStatus.ACCEPTED)
                .build();

            // when & then
            assertThat(friendship.isRejected()).isFalse();
        }

        @Test
        @DisplayName("BLOCKED 상태면 false를 반환한다")
        void isRejected_whenBlocked_returnsFalse() {
            // given
            Friendship friendship = Friendship.builder()
                .userId(USER_ID)
                .friendId(FRIEND_ID)
                .status(FriendshipStatus.BLOCKED)
                .build();

            // when & then
            assertThat(friendship.isRejected()).isFalse();
        }
    }

    @Nested
    @DisplayName("resendRequest 메서드 테스트")
    class ResendRequestTest {

        @Test
        @DisplayName("REJECTED 상태에서 요청을 다시 보낼 수 있다")
        void resendRequest_whenRejected_success() {
            // given
            Friendship friendship = Friendship.builder()
                .userId(USER_ID)
                .friendId(FRIEND_ID)
                .status(FriendshipStatus.REJECTED)
                .message("이전 메시지")
                .build();

            String newRequesterId = "new-requester";
            String newRecipientId = "new-recipient";
            String newMessage = "다시 친구해요!";

            // when
            friendship.resendRequest(newRequesterId, newRecipientId, newMessage);

            // then
            assertThat(friendship.getStatus()).isEqualTo(FriendshipStatus.PENDING);
            assertThat(friendship.getUserId()).isEqualTo(newRequesterId);
            assertThat(friendship.getFriendId()).isEqualTo(newRecipientId);
            assertThat(friendship.getMessage()).isEqualTo(newMessage);
            assertThat(friendship.getRequestedAt()).isNotNull();
            assertThat(friendship.getAcceptedAt()).isNull();
        }

        @Test
        @DisplayName("REJECTED 상태에서 null 메시지로도 요청을 보낼 수 있다")
        void resendRequest_withNullMessage_success() {
            // given
            Friendship friendship = Friendship.builder()
                .userId(USER_ID)
                .friendId(FRIEND_ID)
                .status(FriendshipStatus.REJECTED)
                .message("이전 메시지")
                .build();

            // when
            friendship.resendRequest(USER_ID, FRIEND_ID, null);

            // then
            assertThat(friendship.getStatus()).isEqualTo(FriendshipStatus.PENDING);
            assertThat(friendship.getMessage()).isNull();
        }

        @Test
        @DisplayName("PENDING 상태에서는 예외가 발생한다")
        void resendRequest_whenPending_throwsException() {
            // given
            Friendship friendship = Friendship.builder()
                .userId(USER_ID)
                .friendId(FRIEND_ID)
                .status(FriendshipStatus.PENDING)
                .build();

            // when & then
            assertThatThrownBy(() -> friendship.resendRequest(USER_ID, FRIEND_ID, "메시지"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("거절된 요청만 다시 보낼 수 있습니다.");
        }

        @Test
        @DisplayName("ACCEPTED 상태에서는 예외가 발생한다")
        void resendRequest_whenAccepted_throwsException() {
            // given
            Friendship friendship = Friendship.builder()
                .userId(USER_ID)
                .friendId(FRIEND_ID)
                .status(FriendshipStatus.ACCEPTED)
                .build();

            // when & then
            assertThatThrownBy(() -> friendship.resendRequest(USER_ID, FRIEND_ID, "메시지"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("거절된 요청만 다시 보낼 수 있습니다.");
        }

        @Test
        @DisplayName("BLOCKED 상태에서는 예외가 발생한다")
        void resendRequest_whenBlocked_throwsException() {
            // given
            Friendship friendship = Friendship.builder()
                .userId(USER_ID)
                .friendId(FRIEND_ID)
                .status(FriendshipStatus.BLOCKED)
                .build();

            // when & then
            assertThatThrownBy(() -> friendship.resendRequest(USER_ID, FRIEND_ID, "메시지"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("거절된 요청만 다시 보낼 수 있습니다.");
        }

        @Test
        @DisplayName("재요청 시 요청자와 수신자가 바뀔 수 있다")
        void resendRequest_canSwapRequesterAndRecipient() {
            // given
            Friendship friendship = Friendship.builder()
                .userId(USER_ID)       // 원래 요청자
                .friendId(FRIEND_ID)    // 원래 수신자 (거절한 사람)
                .status(FriendshipStatus.REJECTED)
                .build();

            // when - 원래 수신자가 이번에는 요청자가 됨
            friendship.resendRequest(FRIEND_ID, USER_ID, "이번엔 내가 친구 신청!");

            // then
            assertThat(friendship.getUserId()).isEqualTo(FRIEND_ID);  // 요청자 변경
            assertThat(friendship.getFriendId()).isEqualTo(USER_ID);  // 수신자 변경
            assertThat(friendship.getStatus()).isEqualTo(FriendshipStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("accept 메서드 테스트")
    class AcceptTest {

        @Test
        @DisplayName("PENDING 상태에서 수락할 수 있다")
        void accept_whenPending_success() {
            // given
            Friendship friendship = Friendship.builder()
                .userId(USER_ID)
                .friendId(FRIEND_ID)
                .status(FriendshipStatus.PENDING)
                .build();

            // when
            friendship.accept();

            // then
            assertThat(friendship.getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);
            assertThat(friendship.getAcceptedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("reject 메서드 테스트")
    class RejectTest {

        @Test
        @DisplayName("PENDING 상태에서 거절할 수 있다")
        void reject_whenPending_success() {
            // given
            Friendship friendship = Friendship.builder()
                .userId(USER_ID)
                .friendId(FRIEND_ID)
                .status(FriendshipStatus.PENDING)
                .build();

            // when
            friendship.reject();

            // then
            assertThat(friendship.getStatus()).isEqualTo(FriendshipStatus.REJECTED);
        }
    }

    @Nested
    @DisplayName("block 메서드 테스트")
    class BlockTest {

        @Test
        @DisplayName("차단하면 BLOCKED 상태가 된다")
        void block_success() {
            // given
            Friendship friendship = Friendship.builder()
                .userId(USER_ID)
                .friendId(FRIEND_ID)
                .status(FriendshipStatus.ACCEPTED)
                .build();

            // when
            friendship.block();

            // then
            assertThat(friendship.getStatus()).isEqualTo(FriendshipStatus.BLOCKED);
        }
    }

    @Nested
    @DisplayName("getter 메서드 테스트")
    class GetterTest {

        @Test
        @DisplayName("userId를 반환한다")
        void getUserId() {
            // given
            Friendship friendship = Friendship.builder()
                .userId(USER_ID)
                .friendId(FRIEND_ID)
                .status(FriendshipStatus.ACCEPTED)
                .build();

            // when & then
            assertThat(friendship.getUserId()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("friendId를 반환한다")
        void getFriendId() {
            // given
            Friendship friendship = Friendship.builder()
                .userId(USER_ID)
                .friendId(FRIEND_ID)
                .status(FriendshipStatus.ACCEPTED)
                .build();

            // when & then
            assertThat(friendship.getFriendId()).isEqualTo(FRIEND_ID);
        }

        @Test
        @DisplayName("status를 반환한다")
        void getStatus() {
            // given
            Friendship friendship = Friendship.builder()
                .userId(USER_ID)
                .friendId(FRIEND_ID)
                .status(FriendshipStatus.PENDING)
                .build();

            // when & then
            assertThat(friendship.getStatus()).isEqualTo(FriendshipStatus.PENDING);
        }
    }
}
