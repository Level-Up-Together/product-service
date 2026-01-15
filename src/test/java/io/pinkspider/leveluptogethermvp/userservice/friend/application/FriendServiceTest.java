package io.pinkspider.leveluptogethermvp.userservice.friend.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.event.FriendRequestAcceptedEvent;
import io.pinkspider.global.event.FriendRequestEvent;
import io.pinkspider.global.event.FriendRequestProcessedEvent;
import io.pinkspider.global.event.FriendRequestRejectedEvent;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Title;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserTitle;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.TitlePosition;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserExperienceRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserTitleRepository;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.AchievementService;
import io.pinkspider.leveluptogethermvp.userservice.friend.domain.dto.FriendRequestResponse;
import io.pinkspider.leveluptogethermvp.userservice.friend.domain.dto.FriendResponse;
import io.pinkspider.leveluptogethermvp.userservice.friend.domain.entity.Friendship;
import io.pinkspider.leveluptogethermvp.userservice.friend.domain.enums.FriendshipStatus;
import io.pinkspider.leveluptogethermvp.userservice.friend.infrastructure.FriendshipRepository;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class FriendServiceTest {

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private FriendCacheService friendCacheService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserExperienceRepository userExperienceRepository;

    @Mock
    private UserTitleRepository userTitleRepository;

    @Mock
    private AchievementService achievementService;

    @InjectMocks
    private FriendService friendService;

    private static final String TEST_USER_ID = "test-user-123";
    private static final String FRIEND_USER_ID = "friend-user-456";

    private void setFriendshipId(Friendship friendship, Long id) {
        try {
            Field idField = Friendship.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(friendship, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Friendship createTestFriendship(Long id, String userId, String friendId, FriendshipStatus status) {
        Friendship friendship = Friendship.builder()
            .userId(userId)
            .friendId(friendId)
            .status(status)
            .build();
        setFriendshipId(friendship, id);
        return friendship;
    }

    private Users createTestUser(String userId, String nickname) {
        Users user = Users.builder()
            .nickname(nickname)
            .email(userId + "@test.com")
            .build();
        try {
            Field idField = Users.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, userId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return user;
    }

    private Title createTestTitle(Long id, String name) {
        Title title = Title.builder()
            .name(name)
            .description(name + " 설명")
            .build();
        try {
            Field idField = Title.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(title, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return title;
    }

    private UserTitle createTestUserTitle(Long id, String userId, Title title) {
        UserTitle userTitle = UserTitle.builder()
            .userId(userId)
            .title(title)
            .isEquipped(true)
            .build();
        try {
            Field idField = UserTitle.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(userTitle, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return userTitle;
    }

    @Nested
    @DisplayName("sendFriendRequest 테스트")
    class SendFriendRequestTest {

        @Test
        @DisplayName("친구 요청을 정상적으로 보낸다")
        void sendFriendRequest_success() {
            // given
            Friendship savedFriendship = createTestFriendship(1L, TEST_USER_ID, FRIEND_USER_ID, FriendshipStatus.PENDING);
            Users requester = createTestUser(TEST_USER_ID, "테스터");

            when(friendshipRepository.findFriendship(TEST_USER_ID, FRIEND_USER_ID))
                .thenReturn(Optional.empty());
            when(friendshipRepository.save(any(Friendship.class))).thenReturn(savedFriendship);
            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(requester));

            // when
            FriendRequestResponse result = friendService.sendFriendRequest(TEST_USER_ID, FRIEND_USER_ID, "친구하자!");

            // then
            assertThat(result).isNotNull();
            verify(friendshipRepository).save(any(Friendship.class));
            verify(eventPublisher).publishEvent(any(FriendRequestEvent.class));
        }

        @Test
        @DisplayName("자기 자신에게 친구 요청을 보내면 예외가 발생한다")
        void sendFriendRequest_toSelf_throwsException() {
            // when & then
            assertThatThrownBy(() -> friendService.sendFriendRequest(TEST_USER_ID, TEST_USER_ID, "메시지"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("자기 자신에게 친구 요청을 보낼 수 없습니다.");
        }

        @Test
        @DisplayName("이미 친구인 경우 예외가 발생한다")
        void sendFriendRequest_alreadyFriends_throwsException() {
            // given
            Friendship existingFriendship = createTestFriendship(1L, TEST_USER_ID, FRIEND_USER_ID, FriendshipStatus.ACCEPTED);

            when(friendshipRepository.findFriendship(TEST_USER_ID, FRIEND_USER_ID))
                .thenReturn(Optional.of(existingFriendship));

            // when & then
            assertThatThrownBy(() -> friendService.sendFriendRequest(TEST_USER_ID, FRIEND_USER_ID, "메시지"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이미 친구입니다.");
        }

        @Test
        @DisplayName("이미 요청 중인 경우 예외가 발생한다")
        void sendFriendRequest_alreadyPending_throwsException() {
            // given
            Friendship pendingFriendship = createTestFriendship(1L, TEST_USER_ID, FRIEND_USER_ID, FriendshipStatus.PENDING);

            when(friendshipRepository.findFriendship(TEST_USER_ID, FRIEND_USER_ID))
                .thenReturn(Optional.of(pendingFriendship));

            // when & then
            assertThatThrownBy(() -> friendService.sendFriendRequest(TEST_USER_ID, FRIEND_USER_ID, "메시지"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이미 친구 요청이 진행 중입니다.");
        }

        @Test
        @DisplayName("차단된 사용자에게 친구 요청을 보내면 예외가 발생한다")
        void sendFriendRequest_blocked_throwsException() {
            // given
            Friendship blockedFriendship = createTestFriendship(1L, TEST_USER_ID, FRIEND_USER_ID, FriendshipStatus.BLOCKED);

            when(friendshipRepository.findFriendship(TEST_USER_ID, FRIEND_USER_ID))
                .thenReturn(Optional.of(blockedFriendship));

            // when & then
            assertThatThrownBy(() -> friendService.sendFriendRequest(TEST_USER_ID, FRIEND_USER_ID, "메시지"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("차단된 사용자에게 친구 요청을 보낼 수 없습니다.");
        }
    }

    @Nested
    @DisplayName("acceptFriendRequest 테스트")
    class AcceptFriendRequestTest {

        @Test
        @DisplayName("친구 요청을 정상적으로 수락한다")
        void acceptFriendRequest_success() {
            // given
            Long requestId = 1L;
            Friendship friendship = createTestFriendship(requestId, FRIEND_USER_ID, TEST_USER_ID, FriendshipStatus.PENDING);
            Users accepter = createTestUser(TEST_USER_ID, "수락자");

            when(friendshipRepository.findById(requestId)).thenReturn(Optional.of(friendship));
            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(accepter));

            // when
            FriendResponse result = friendService.acceptFriendRequest(TEST_USER_ID, requestId);

            // then
            assertThat(result).isNotNull();
            assertThat(friendship.getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);
            verify(eventPublisher).publishEvent(any(FriendRequestProcessedEvent.class));
            verify(eventPublisher).publishEvent(any(FriendRequestAcceptedEvent.class));
            // 동적 업적 체크 호출 확인
            verify(achievementService).checkAchievementsByDataSource(eq(TEST_USER_ID), eq("FRIEND_SERVICE"));
            verify(achievementService).checkAchievementsByDataSource(eq(FRIEND_USER_ID), eq("FRIEND_SERVICE"));
        }

        @Test
        @DisplayName("존재하지 않는 요청을 수락하면 예외가 발생한다")
        void acceptFriendRequest_notFound_throwsException() {
            // given
            when(friendshipRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> friendService.acceptFriendRequest(TEST_USER_ID, 999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("친구 요청을 찾을 수 없습니다.");
        }

        @Test
        @DisplayName("본인에게 온 요청이 아니면 예외가 발생한다")
        void acceptFriendRequest_notReceiver_throwsException() {
            // given
            Long requestId = 1L;
            String otherUserId = "other-user-789";
            Friendship friendship = createTestFriendship(requestId, FRIEND_USER_ID, otherUserId, FriendshipStatus.PENDING);

            when(friendshipRepository.findById(requestId)).thenReturn(Optional.of(friendship));

            // when & then
            assertThatThrownBy(() -> friendService.acceptFriendRequest(TEST_USER_ID, requestId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("본인에게 온 요청만 수락할 수 있습니다.");
        }
    }

    @Nested
    @DisplayName("rejectFriendRequest 테스트")
    class RejectFriendRequestTest {

        @Test
        @DisplayName("친구 요청을 정상적으로 거절한다")
        void rejectFriendRequest_success() {
            // given
            Long requestId = 1L;
            Friendship friendship = createTestFriendship(requestId, FRIEND_USER_ID, TEST_USER_ID, FriendshipStatus.PENDING);
            Users rejecter = createTestUser(TEST_USER_ID, "거절자");

            when(friendshipRepository.findById(requestId)).thenReturn(Optional.of(friendship));
            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(rejecter));

            // when
            friendService.rejectFriendRequest(TEST_USER_ID, requestId);

            // then
            assertThat(friendship.getStatus()).isEqualTo(FriendshipStatus.REJECTED);
            verify(eventPublisher).publishEvent(any(FriendRequestProcessedEvent.class));
            verify(eventPublisher).publishEvent(any(FriendRequestRejectedEvent.class));
        }
    }

    @Nested
    @DisplayName("removeFriend 테스트")
    class RemoveFriendTest {

        @Test
        @DisplayName("친구를 정상적으로 삭제한다")
        void removeFriend_success() {
            // given
            Friendship friendship = createTestFriendship(1L, TEST_USER_ID, FRIEND_USER_ID, FriendshipStatus.ACCEPTED);

            when(friendshipRepository.findFriendship(TEST_USER_ID, FRIEND_USER_ID))
                .thenReturn(Optional.of(friendship));

            // when
            friendService.removeFriend(TEST_USER_ID, FRIEND_USER_ID);

            // then
            verify(friendshipRepository).delete(friendship);
        }

        @Test
        @DisplayName("친구 관계가 아닌 경우 예외가 발생한다")
        void removeFriend_notFriends_throwsException() {
            // given
            Friendship friendship = createTestFriendship(1L, TEST_USER_ID, FRIEND_USER_ID, FriendshipStatus.PENDING);

            when(friendshipRepository.findFriendship(TEST_USER_ID, FRIEND_USER_ID))
                .thenReturn(Optional.of(friendship));

            // when & then
            assertThatThrownBy(() -> friendService.removeFriend(TEST_USER_ID, FRIEND_USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("친구 관계가 아닙니다.");
        }
    }

    @Nested
    @DisplayName("blockUser 테스트")
    class BlockUserTest {

        @Test
        @DisplayName("기존 관계가 있는 사용자를 차단한다")
        void blockUser_existingRelation() {
            // given
            Friendship friendship = createTestFriendship(1L, TEST_USER_ID, FRIEND_USER_ID, FriendshipStatus.ACCEPTED);

            when(friendshipRepository.findByUserIdAndFriendId(TEST_USER_ID, FRIEND_USER_ID))
                .thenReturn(Optional.of(friendship));

            // when
            friendService.blockUser(TEST_USER_ID, FRIEND_USER_ID);

            // then
            assertThat(friendship.getStatus()).isEqualTo(FriendshipStatus.BLOCKED);
        }

        @Test
        @DisplayName("관계가 없는 사용자를 차단한다")
        void blockUser_noExistingRelation() {
            // given
            when(friendshipRepository.findByUserIdAndFriendId(TEST_USER_ID, FRIEND_USER_ID))
                .thenReturn(Optional.empty());

            // when
            friendService.blockUser(TEST_USER_ID, FRIEND_USER_ID);

            // then
            verify(friendshipRepository).save(any(Friendship.class));
        }
    }

    @Nested
    @DisplayName("unblockUser 테스트")
    class UnblockUserTest {

        @Test
        @DisplayName("차단을 정상적으로 해제한다")
        void unblockUser_success() {
            // given
            Friendship friendship = createTestFriendship(1L, TEST_USER_ID, FRIEND_USER_ID, FriendshipStatus.BLOCKED);

            when(friendshipRepository.findByUserIdAndFriendId(TEST_USER_ID, FRIEND_USER_ID))
                .thenReturn(Optional.of(friendship));

            // when
            friendService.unblockUser(TEST_USER_ID, FRIEND_USER_ID);

            // then
            verify(friendshipRepository).delete(friendship);
        }

        @Test
        @DisplayName("차단된 사용자가 아니면 예외가 발생한다")
        void unblockUser_notBlocked_throwsException() {
            // given
            Friendship friendship = createTestFriendship(1L, TEST_USER_ID, FRIEND_USER_ID, FriendshipStatus.ACCEPTED);

            when(friendshipRepository.findByUserIdAndFriendId(TEST_USER_ID, FRIEND_USER_ID))
                .thenReturn(Optional.of(friendship));

            // when & then
            assertThatThrownBy(() -> friendService.unblockUser(TEST_USER_ID, FRIEND_USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("차단된 사용자가 아닙니다.");
        }
    }

    @Nested
    @DisplayName("getFriends 테스트")
    class GetFriendsTest {

        @Test
        @DisplayName("친구 목록을 페이지로 조회한다")
        void getFriends_success() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            Friendship friendship = createTestFriendship(1L, TEST_USER_ID, FRIEND_USER_ID, FriendshipStatus.ACCEPTED);
            Page<Friendship> page = new PageImpl<>(List.of(friendship), pageable, 1);
            Users friend = createTestUser(FRIEND_USER_ID, "친구");
            Title title = createTestTitle(1L, "테스트 칭호");
            UserTitle userTitle = createTestUserTitle(1L, FRIEND_USER_ID, title);

            when(friendshipRepository.findFriends(TEST_USER_ID, pageable)).thenReturn(page);
            when(userRepository.findAllById(List.of(FRIEND_USER_ID))).thenReturn(List.of(friend));
            when(userExperienceRepository.findByUserId(FRIEND_USER_ID)).thenReturn(Optional.empty());
            when(userTitleRepository.findEquippedByUserIdAndPosition(FRIEND_USER_ID, TitlePosition.LEFT))
                .thenReturn(Optional.of(userTitle));

            // when
            Page<FriendResponse> result = friendService.getFriends(TEST_USER_ID, pageable);

            // then
            assertThat(result.getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("cancelFriendRequest 테스트")
    class CancelFriendRequestTest {

        @Test
        @DisplayName("친구 요청을 취소한다")
        void cancelFriendRequest_success() {
            // given
            Long requestId = 1L;
            Friendship friendship = createTestFriendship(requestId, TEST_USER_ID, FRIEND_USER_ID, FriendshipStatus.PENDING);

            when(friendshipRepository.findById(requestId)).thenReturn(Optional.of(friendship));

            // when
            friendService.cancelFriendRequest(TEST_USER_ID, requestId);

            // then
            verify(friendshipRepository).delete(friendship);
        }

        @Test
        @DisplayName("본인이 보낸 요청이 아니면 예외가 발생한다")
        void cancelFriendRequest_notSender_throwsException() {
            // given
            Long requestId = 1L;
            Friendship friendship = createTestFriendship(requestId, FRIEND_USER_ID, TEST_USER_ID, FriendshipStatus.PENDING);

            when(friendshipRepository.findById(requestId)).thenReturn(Optional.of(friendship));

            // when & then
            assertThatThrownBy(() -> friendService.cancelFriendRequest(TEST_USER_ID, requestId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("본인이 보낸 요청만 취소할 수 있습니다.");
        }

        @Test
        @DisplayName("대기 중인 요청이 아니면 예외가 발생한다")
        void cancelFriendRequest_notPending_throwsException() {
            // given
            Long requestId = 1L;
            Friendship friendship = createTestFriendship(requestId, TEST_USER_ID, FRIEND_USER_ID, FriendshipStatus.ACCEPTED);

            when(friendshipRepository.findById(requestId)).thenReturn(Optional.of(friendship));

            // when & then
            assertThatThrownBy(() -> friendService.cancelFriendRequest(TEST_USER_ID, requestId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("대기 중인 요청만 취소할 수 있습니다.");
        }
    }

    @Nested
    @DisplayName("기타 조회 메서드 테스트")
    class OtherMethodsTest {

        @Test
        @DisplayName("친구 수를 조회한다")
        void getFriendCount_success() {
            // given
            when(friendshipRepository.countFriends(TEST_USER_ID)).thenReturn(5);

            // when
            int result = friendService.getFriendCount(TEST_USER_ID);

            // then
            assertThat(result).isEqualTo(5);
        }

        @Test
        @DisplayName("친구 여부를 확인한다")
        void areFriends_true() {
            // given
            when(friendshipRepository.areFriends(TEST_USER_ID, FRIEND_USER_ID)).thenReturn(true);

            // when
            boolean result = friendService.areFriends(TEST_USER_ID, FRIEND_USER_ID);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("차단 여부를 확인한다")
        void isBlocked_true() {
            // given
            when(friendshipRepository.isBlocked(TEST_USER_ID, FRIEND_USER_ID)).thenReturn(true);

            // when
            boolean result = friendService.isBlocked(TEST_USER_ID, FRIEND_USER_ID);

            // then
            assertThat(result).isTrue();
        }
    }
}
