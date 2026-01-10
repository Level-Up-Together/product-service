package io.pinkspider.leveluptogethermvp.userservice.friend.application;

import io.pinkspider.leveluptogethermvp.userservice.friend.infrastructure.FriendshipRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 친구 관계 캐싱 서비스
 * - 타임라인 피드 조회 시 N+1 문제 해결을 위한 캐싱
 * - 친구 관계 변경 시 캐시 무효화
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FriendCacheService {

    private final FriendshipRepository friendshipRepository;

    /**
     * 사용자의 친구 ID 목록 조회 (캐싱)
     * TTL: 10분 (RedisConfig에서 설정)
     */
    @Cacheable(value = "userFriendIds", key = "#userId")
    public List<String> getFriendIds(String userId) {
        log.debug("캐시 미스 - DB에서 친구 목록 조회: userId={}", userId);
        return friendshipRepository.findFriendIds(userId);
    }

    /**
     * 친구 관계 변경 시 양쪽 사용자의 캐시 무효화
     */
    @CacheEvict(value = "userFriendIds", key = "#userId")
    public void evictFriendCache(String userId) {
        log.debug("친구 캐시 무효화: userId={}", userId);
    }

    /**
     * 양쪽 사용자의 친구 캐시 무효화
     */
    public void evictBothFriendCaches(String userId1, String userId2) {
        evictFriendCache(userId1);
        evictFriendCache(userId2);
        log.debug("양쪽 친구 캐시 무효화: user1={}, user2={}", userId1, userId2);
    }
}
