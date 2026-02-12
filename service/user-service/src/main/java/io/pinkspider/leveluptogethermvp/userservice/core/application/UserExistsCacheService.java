package io.pinkspider.leveluptogethermvp.userservice.core.application;

import io.pinkspider.global.security.UserExistenceChecker;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 존재 여부 캐싱 서비스
 * - JWT 인증 시 매 요청마다 DB 조회를 피하기 위해 Redis 캐싱 사용
 * - TTL: 5분 (RedisConfig에서 설정)
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = "userTransactionManager")
public class UserExistsCacheService implements UserExistenceChecker {

    private final UserRepository userRepository;

    /**
     * 사용자 존재 여부 확인 (캐싱)
     * - JWT 인증 필터에서 사용
     * - 존재하지 않는 userId로 요청 시 false 반환 → 401 응답
     */
    @Cacheable(value = "userExists", key = "#userId")
    public boolean existsById(String userId) {
        log.debug("캐시 미스 - DB에서 사용자 존재 여부 조회: userId={}", userId);
        return userRepository.existsById(userId);
    }

    /**
     * 사용자 존재 여부 캐시 무효화
     * - 사용자 탈퇴 시 호출
     */
    @CacheEvict(value = "userExists", key = "#userId")
    public void evictUserExistsCache(String userId) {
        log.debug("사용자 존재 여부 캐시 무효화: userId={}", userId);
    }
}
