package io.pinkspider.leveluptogethermvp.userservice.profile.application;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserExperience;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserExperienceRepository;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.TitleService;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.TitleService.TitleInfo;
import io.pinkspider.leveluptogethermvp.userservice.profile.domain.dto.UserProfileCache;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 프로필 캐싱 서비스
 * - 피드 생성, 길드 멤버 조회 등에서 사용되는 사용자 정보 캐싱
 * - 다수의 서비스 호출을 하나로 통합하여 성능 개선
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserProfileCacheService {

    private final UserRepository userRepository;
    private final UserExperienceRepository userExperienceRepository;
    private final TitleService titleService;

    /**
     * 사용자 프로필 정보 조회 (캐싱)
     * - nickname, picture, level, titleName, titleRarity, titleColorCode 포함
     * - TTL: 5분 (RedisConfig에서 설정)
     */
    @Cacheable(value = "userProfile", key = "#userId")
    public UserProfileCache getUserProfile(String userId) {
        log.debug("캐시 미스 - DB에서 사용자 프로필 조회: userId={}", userId);

        // 사용자 정보 조회
        Users user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("사용자를 찾을 수 없음: userId={}", userId);
            return UserProfileCache.defaultProfile(userId);
        }

        // 사용자 레벨 조회
        Integer level = userExperienceRepository.findByUserId(userId)
            .map(UserExperience::getCurrentLevel)
            .orElse(1);

        // 사용자 칭호 조회 (TitleService의 캐시 사용)
        TitleInfo titleInfo = titleService.getCombinedEquippedTitleInfo(userId);

        return new UserProfileCache(
            userId,
            user.getNickname(),
            user.getPicture(),
            level,
            titleInfo.name(),
            titleInfo.rarity(),
            titleInfo.colorCode()
        );
    }

    /**
     * 사용자 프로필 캐시 무효화
     * - 닉네임, 프로필 사진, 레벨, 칭호 변경 시 호출
     */
    @CacheEvict(value = "userProfile", key = "#userId")
    public void evictUserProfileCache(String userId) {
        log.debug("사용자 프로필 캐시 무효화: userId={}", userId);
    }
}
