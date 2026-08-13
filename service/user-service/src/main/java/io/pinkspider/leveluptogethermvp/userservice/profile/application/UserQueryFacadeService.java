package io.pinkspider.leveluptogethermvp.userservice.profile.application;

import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.global.facade.dto.UserProfileInfo;
import io.pinkspider.leveluptogethermvp.userservice.core.application.UserExistsCacheService;
import io.pinkspider.leveluptogethermvp.userservice.friend.application.FriendCacheService;
import io.pinkspider.leveluptogethermvp.userservice.friend.application.FriendService;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 외부 서비스용 사용자 Facade
 * userservice 외부에서 user_db에 직접 접근하지 않고 이 서비스를 통해 조회한다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = "userTransactionManager")
public class UserQueryFacadeService implements UserQueryFacade {

    private final UserProfileCacheService userProfileCacheService;
    private final UserExistsCacheService userExistsCacheService;
    private final FriendCacheService friendCacheService;
    private final FriendService friendService;
    private final UserRepository userRepository;

    // ========== 프로필 조회 (캐시) ==========

    public UserProfileInfo getUserProfile(String userId) {
        return userProfileCacheService.getUserProfile(userId);
    }

    public Map<String, UserProfileInfo> getUserProfiles(List<String> userIds) {
        return userProfileCacheService.getUserProfiles(userIds);
    }

    public String getUserNickname(String userId) {
        return userProfileCacheService.getUserNickname(userId);
    }

    public String getUserEmail(String userId) {
        return userProfileCacheService.getUserEmail(userId);
    }

    // ========== 캐시 무효화 ==========

    public void evictUserProfileCache(String userId) {
        userProfileCacheService.evictUserProfileCache(userId);
    }

    // ========== 활성 사용자 필터링 ==========

    public List<String> getActiveUserIds(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return userRepository.findActiveUserIds(userIds);
    }

    // ========== 존재 확인 ==========

    public boolean userExistsById(String userId) {
        return userExistsCacheService.existsById(userId);
    }

    // ========== 신규 유저 확인 ==========

    public boolean isNewUserToday(String userId) {
        return userRepository.findById(userId)
            .map(user -> user.getCreatedAt() != null
                && user.getCreatedAt().toLocalDate().equals(LocalDate.now()))
            .orElse(false);
    }

    // ========== 닉네임 직접 조회 ==========

    public String findUserNickname(String userId) {
        return userRepository.findById(userId)
            .map(Users::getNickname)
            .orElse(null);
    }

    /** LUT-328: 닉네임 검색 결과 상한 — 어드민 검색 IN 절 폭주 방지 */
    private static final int NICKNAME_SEARCH_LIMIT = 200;

    @Override
    public List<String> findUserIdsByNicknameContaining(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return userRepository.findIdsByNicknameContaining(
            keyword.trim(), PageRequest.of(0, NICKNAME_SEARCH_LIMIT));
    }

    // ========== 피드 공개범위 선호 ==========

    @Override
    public String getPreferredFeedVisibility(String userId) {
        return userRepository.findById(userId)
            .map(Users::getPreferredFeedVisibility)
            .orElse("PUBLIC");
    }

    @Override
    @Transactional(transactionManager = "userTransactionManager")
    public void updatePreferredFeedVisibility(String userId, String feedVisibility) {
        userRepository.findById(userId).ifPresent(user -> {
            user.updatePreferredFeedVisibility(feedVisibility);
            userRepository.save(user);
        });
    }

    // ========== 선호 타임존 ==========

    @Override
    public String getPreferredTimezone(String userId) {
        return userRepository.findById(userId)
            .map(Users::getPreferredTimezone)
            .orElse("Asia/Seoul");
    }

    // ========== 친구 관계 ==========

    public List<String> getFriendIds(String userId) {
        return friendCacheService.getFriendIds(userId);
    }

    public boolean areFriends(String userId1, String userId2) {
        return friendService.areFriends(userId1, userId2);
    }

    // ========== 차단 관계 (LUT-367) ==========

    public List<String> getBlockedUserIds(String userId) {
        return friendService.getBlockedUserIds(userId);
    }

    public boolean isBlockedBetween(String userId1, String userId2) {
        return friendService.isBlockedBetween(userId1, userId2);
    }
}
