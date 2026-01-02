package io.pinkspider.leveluptogethermvp.userservice.friend.application;

import io.pinkspider.leveluptogethermvp.userservice.friend.domain.dto.FriendRequestResponse;
import io.pinkspider.leveluptogethermvp.userservice.friend.domain.dto.FriendResponse;
import io.pinkspider.leveluptogethermvp.userservice.friend.domain.entity.Friendship;
import io.pinkspider.leveluptogethermvp.userservice.friend.domain.enums.FriendshipStatus;
import io.pinkspider.leveluptogethermvp.userservice.friend.infrastructure.FriendshipRepository;
import io.pinkspider.leveluptogethermvp.userservice.notification.application.NotificationService;
import io.pinkspider.leveluptogethermvp.userservice.notification.domain.enums.NotificationType;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FriendService {

    private final FriendshipRepository friendshipRepository;
    private final NotificationService notificationService;

    // 친구 요청 보내기
    @Transactional
    public FriendRequestResponse sendFriendRequest(String userId, String friendId, String message) {
        if (userId.equals(friendId)) {
            throw new IllegalArgumentException("자기 자신에게 친구 요청을 보낼 수 없습니다.");
        }

        // 이미 존재하는 관계 확인
        Optional<Friendship> existing = friendshipRepository.findFriendship(userId, friendId);
        if (existing.isPresent()) {
            Friendship friendship = existing.get();
            if (friendship.isAccepted()) {
                throw new IllegalStateException("이미 친구입니다.");
            }
            if (friendship.isPending()) {
                throw new IllegalStateException("이미 친구 요청이 진행 중입니다.");
            }
            if (friendship.isBlocked()) {
                throw new IllegalStateException("차단된 사용자에게 친구 요청을 보낼 수 없습니다.");
            }
        }

        Friendship friendship = Friendship.createRequest(userId, friendId, message);
        Friendship saved = friendshipRepository.save(friendship);
        log.debug("친구 요청 저장 완료: id={}, userId={}, friendId={}, status={}",
            saved.getId(), saved.getUserId(), saved.getFriendId(), saved.getStatus());

        // 알림 발송
        try {
            notificationService.createNotification(
                friendId,
                NotificationType.SYSTEM,
                "새 친구 요청",
                "새로운 친구 요청이 도착했습니다.",
                "FRIEND_REQUEST",
                saved.getId(),
                "/friends/requests"
            );
        } catch (Exception e) {
            log.warn("친구 요청 알림 발송 실패: {}", e.getMessage());
        }

        log.info("친구 요청 전송: {} -> {}", userId, friendId);
        return FriendRequestResponse.simpleFrom(saved);
    }

    // 친구 요청 수락
    @Transactional
    public FriendResponse acceptFriendRequest(String userId, Long requestId) {
        Friendship friendship = friendshipRepository.findById(requestId)
            .orElseThrow(() -> new IllegalArgumentException("친구 요청을 찾을 수 없습니다."));

        if (!friendship.getFriendId().equals(userId)) {
            throw new IllegalStateException("본인에게 온 요청만 수락할 수 있습니다.");
        }

        friendship.accept();

        // 요청자에게 알림 발송
        try {
            notificationService.createNotification(
                friendship.getUserId(),
                NotificationType.SYSTEM,
                "친구 요청 수락",
                "친구 요청이 수락되었습니다!",
                "FRIEND",
                friendship.getId(),
                "/friends"
            );
        } catch (Exception e) {
            log.warn("친구 수락 알림 발송 실패: {}", e.getMessage());
        }

        log.info("친구 요청 수락: {} accepted {}", userId, friendship.getUserId());
        return FriendResponse.simpleFrom(friendship, userId);
    }

    // 친구 요청 거절
    @Transactional
    public void rejectFriendRequest(String userId, Long requestId) {
        Friendship friendship = friendshipRepository.findById(requestId)
            .orElseThrow(() -> new IllegalArgumentException("친구 요청을 찾을 수 없습니다."));

        if (!friendship.getFriendId().equals(userId)) {
            throw new IllegalStateException("본인에게 온 요청만 거절할 수 있습니다.");
        }

        friendship.reject();
        log.info("친구 요청 거절: {} rejected {}", userId, friendship.getUserId());
    }

    // 친구 삭제
    @Transactional
    public void removeFriend(String userId, String friendId) {
        Friendship friendship = friendshipRepository.findFriendship(userId, friendId)
            .orElseThrow(() -> new IllegalArgumentException("친구 관계를 찾을 수 없습니다."));

        if (!friendship.isAccepted()) {
            throw new IllegalStateException("친구 관계가 아닙니다.");
        }

        friendshipRepository.delete(friendship);
        log.info("친구 삭제: {} removed {}", userId, friendId);
    }

    // 사용자 차단
    @Transactional
    public void blockUser(String userId, String targetId) {
        Optional<Friendship> existing = friendshipRepository.findByUserIdAndFriendId(userId, targetId);

        if (existing.isPresent()) {
            Friendship friendship = existing.get();
            friendship.block();
        } else {
            Friendship friendship = Friendship.builder()
                .userId(userId)
                .friendId(targetId)
                .status(FriendshipStatus.BLOCKED)
                .build();
            friendship.block();
            friendshipRepository.save(friendship);
        }

        log.info("사용자 차단: {} blocked {}", userId, targetId);
    }

    // 차단 해제
    @Transactional
    public void unblockUser(String userId, String targetId) {
        Friendship friendship = friendshipRepository.findByUserIdAndFriendId(userId, targetId)
            .orElseThrow(() -> new IllegalArgumentException("차단 관계를 찾을 수 없습니다."));

        if (!friendship.isBlocked()) {
            throw new IllegalStateException("차단된 사용자가 아닙니다.");
        }

        friendshipRepository.delete(friendship);
        log.info("차단 해제: {} unblocked {}", userId, targetId);
    }

    // 친구 목록 조회
    public Page<FriendResponse> getFriends(String userId, Pageable pageable) {
        return friendshipRepository.findFriends(userId, pageable)
            .map(f -> FriendResponse.simpleFrom(f, userId));
    }

    // 전체 친구 목록 조회
    public List<FriendResponse> getAllFriends(String userId) {
        return friendshipRepository.findAllFriends(userId).stream()
            .map(f -> FriendResponse.simpleFrom(f, userId))
            .toList();
    }

    // 받은 친구 요청 목록
    public List<FriendRequestResponse> getPendingRequestsReceived(String userId) {
        return friendshipRepository.findPendingRequestsReceived(userId).stream()
            .map(FriendRequestResponse::simpleFrom)
            .toList();
    }

    // 보낸 친구 요청 목록
    public List<FriendRequestResponse> getPendingRequestsSent(String userId) {
        return friendshipRepository.findPendingRequestsSent(userId).stream()
            .map(FriendRequestResponse::simpleFrom)
            .toList();
    }

    // 차단 목록 조회
    public List<FriendResponse> getBlockedUsers(String userId) {
        return friendshipRepository.findBlockedUsers(userId).stream()
            .map(f -> FriendResponse.simpleFrom(f, userId))
            .toList();
    }

    // 친구 수 조회
    public int getFriendCount(String userId) {
        return friendshipRepository.countFriends(userId);
    }

    // 친구 여부 확인
    public boolean areFriends(String userId, String friendId) {
        return friendshipRepository.areFriends(userId, friendId);
    }

    // 차단 여부 확인
    public boolean isBlocked(String userId, String targetId) {
        return friendshipRepository.isBlocked(userId, targetId);
    }

    // 친구 요청 취소
    @Transactional
    public void cancelFriendRequest(String userId, Long requestId) {
        Friendship friendship = friendshipRepository.findById(requestId)
            .orElseThrow(() -> new IllegalArgumentException("친구 요청을 찾을 수 없습니다."));

        if (!friendship.getUserId().equals(userId)) {
            throw new IllegalStateException("본인이 보낸 요청만 취소할 수 있습니다.");
        }

        if (!friendship.isPending()) {
            throw new IllegalStateException("대기 중인 요청만 취소할 수 있습니다.");
        }

        friendshipRepository.delete(friendship);
        log.info("친구 요청 취소: {} cancelled request to {}", userId, friendship.getFriendId());
    }
}
