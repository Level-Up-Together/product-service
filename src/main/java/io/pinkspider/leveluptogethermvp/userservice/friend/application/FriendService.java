package io.pinkspider.leveluptogethermvp.userservice.friend.application;

import io.pinkspider.global.event.FriendRequestAcceptedEvent;
import io.pinkspider.global.event.FriendRequestEvent;
import io.pinkspider.global.event.FriendRequestProcessedEvent;
import io.pinkspider.global.event.FriendRequestRejectedEvent;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.AchievementService;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserTitle;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.TitlePosition;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserTitleRepository;
import io.pinkspider.leveluptogethermvp.userservice.friend.domain.dto.FriendRequestResponse;
import io.pinkspider.leveluptogethermvp.userservice.friend.domain.dto.FriendResponse;
import io.pinkspider.leveluptogethermvp.userservice.friend.domain.entity.Friendship;
import io.pinkspider.leveluptogethermvp.userservice.friend.domain.enums.FriendshipStatus;
import io.pinkspider.leveluptogethermvp.userservice.friend.infrastructure.FriendshipRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserExperience;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserExperienceRepository;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
    private final FriendCacheService friendCacheService;
    private final ApplicationEventPublisher eventPublisher;
    private final UserRepository userRepository;
    private final UserExperienceRepository userExperienceRepository;
    private final UserTitleRepository userTitleRepository;
    private final AchievementService achievementService;

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

        // 친구 요청 이벤트 발행
        Users requester = userRepository.findById(userId).orElse(null);
        String requesterNickname = requester != null ? requester.getNickname() : "사용자";
        eventPublisher.publishEvent(new FriendRequestEvent(
            userId, friendId, requesterNickname, saved.getId()
        ));

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

        // 양쪽 사용자의 친구 캐시 무효화
        friendCacheService.evictBothFriendCaches(userId, friendship.getUserId());

        // 친구 요청 처리 완료 이벤트 발행 (알림 삭제용)
        eventPublisher.publishEvent(new FriendRequestProcessedEvent(userId, requestId));

        // 친구 요청 수락 이벤트 발행
        Users accepter = userRepository.findById(userId).orElse(null);
        String accepterNickname = accepter != null ? accepter.getNickname() : "사용자";
        eventPublisher.publishEvent(new FriendRequestAcceptedEvent(
            userId, friendship.getUserId(), accepterNickname, friendship.getId()
        ));

        // 양쪽 모두 친구 업적 체크 - 동적 Strategy 패턴 사용
        try {
            achievementService.checkAchievementsByDataSource(userId, "FRIEND_SERVICE");
            achievementService.checkAchievementsByDataSource(friendship.getUserId(), "FRIEND_SERVICE");
        } catch (Exception e) {
            log.warn("친구 업적 체크 실패: {}", e.getMessage());
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

        // 친구 요청 처리 완료 이벤트 발행 (알림 삭제용)
        eventPublisher.publishEvent(new FriendRequestProcessedEvent(userId, requestId));

        // 친구 요청 거절 이벤트 발행
        Users rejecter = userRepository.findById(userId).orElse(null);
        String rejecterNickname = rejecter != null ? rejecter.getNickname() : "사용자";
        eventPublisher.publishEvent(new FriendRequestRejectedEvent(
            userId, friendship.getUserId(), rejecterNickname, friendship.getId()
        ));

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

        // 양쪽 사용자의 친구 캐시 무효화
        friendCacheService.evictBothFriendCaches(userId, friendId);

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

        // 양쪽 사용자의 친구 캐시 무효화 (기존 친구 관계가 차단으로 변경될 수 있음)
        friendCacheService.evictBothFriendCaches(userId, targetId);

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

        // 양쪽 사용자의 친구 캐시 무효화
        friendCacheService.evictBothFriendCaches(userId, targetId);

        log.info("차단 해제: {} unblocked {}", userId, targetId);
    }

    // 친구 목록 조회
    public Page<FriendResponse> getFriends(String userId, Pageable pageable) {
        Page<Friendship> friendships = friendshipRepository.findFriends(userId, pageable);

        // 친구 ID 목록 추출
        List<String> friendIds = friendships.getContent().stream()
            .map(f -> f.getUserId().equals(userId) ? f.getFriendId() : f.getUserId())
            .toList();

        // 사용자 정보, 레벨, 칭호 조회
        Map<String, Users> userMap = getUserMap(friendIds);
        Map<String, Integer> levelMap = getLevelMap(friendIds);
        Map<String, String> titleMap = getTitleMap(friendIds);

        return friendships.map(friendship -> {
            String friendId = friendship.getUserId().equals(userId)
                ? friendship.getFriendId()
                : friendship.getUserId();
            Users friend = userMap.get(friendId);
            return FriendResponse.from(
                friendship,
                userId,
                friend != null ? friend.getNickname() : null,
                friend != null ? friend.getPicture() : null,
                levelMap.getOrDefault(friendId, 1),
                titleMap.get(friendId)
            );
        });
    }

    // 전체 친구 목록 조회
    public List<FriendResponse> getAllFriends(String userId) {
        List<Friendship> friendships = friendshipRepository.findAllFriends(userId);

        // 친구 ID 목록 추출
        List<String> friendIds = friendships.stream()
            .map(f -> f.getUserId().equals(userId) ? f.getFriendId() : f.getUserId())
            .toList();

        // 사용자 정보, 레벨, 칭호 조회
        Map<String, Users> userMap = getUserMap(friendIds);
        Map<String, Integer> levelMap = getLevelMap(friendIds);
        Map<String, String> titleMap = getTitleMap(friendIds);

        return friendships.stream()
            .map(friendship -> {
                String friendId = friendship.getUserId().equals(userId)
                    ? friendship.getFriendId()
                    : friendship.getUserId();
                Users friend = userMap.get(friendId);
                return FriendResponse.from(
                    friendship,
                    userId,
                    friend != null ? friend.getNickname() : null,
                    friend != null ? friend.getPicture() : null,
                    levelMap.getOrDefault(friendId, 1),
                    titleMap.get(friendId)
                );
            })
            .toList();
    }

    // 사용자 정보 조회 헬퍼 메서드
    private Map<String, Users> getUserMap(List<String> userIds) {
        return userRepository.findAllById(userIds).stream()
            .collect(Collectors.toMap(Users::getId, u -> u));
    }

    // 레벨 정보 조회 헬퍼 메서드
    private Map<String, Integer> getLevelMap(List<String> userIds) {
        return userIds.stream()
            .collect(Collectors.toMap(
                id -> id,
                id -> userExperienceRepository.findByUserId(id)
                    .map(UserExperience::getCurrentLevel)
                    .orElse(1)
            ));
    }

    // 칭호 정보 조회 헬퍼 메서드 (LEFT 포지션의 칭호를 대표 칭호로 사용)
    private Map<String, String> getTitleMap(List<String> userIds) {
        Map<String, String> result = new HashMap<>();
        for (String id : userIds) {
            String title = userTitleRepository.findEquippedByUserIdAndPosition(id, TitlePosition.LEFT)
                .map(UserTitle::getTitle)
                .map(t -> t.getDisplayName())
                .orElse(null);
            result.put(id, title);
        }
        return result;
    }

    // 받은 친구 요청 목록
    public List<FriendRequestResponse> getPendingRequestsReceived(String userId) {
        List<Friendship> friendships = friendshipRepository.findPendingRequestsReceived(userId);

        // 요청자 ID 목록 추출
        List<String> requesterIds = friendships.stream()
            .map(Friendship::getUserId)
            .toList();

        // 사용자 정보 조회
        Map<String, Users> userMap = userRepository.findAllById(requesterIds).stream()
            .collect(Collectors.toMap(Users::getId, u -> u));

        // 레벨 정보 조회
        Map<String, Integer> levelMap = requesterIds.stream()
            .collect(Collectors.toMap(
                id -> id,
                id -> userExperienceRepository.findByUserId(id)
                    .map(UserExperience::getCurrentLevel)
                    .orElse(1)
            ));

        return friendships.stream()
            .map(friendship -> {
                Users requester = userMap.get(friendship.getUserId());
                String nickname = requester != null ? requester.getNickname() : null;
                String profileImageUrl = requester != null ? requester.getPicture() : null;
                Integer level = levelMap.getOrDefault(friendship.getUserId(), 1);
                return FriendRequestResponse.from(friendship, nickname, profileImageUrl, level);
            })
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
