package io.pinkspider.leveluptogethermvp.userservice.profile.event.listener;

import io.pinkspider.global.event.UserLevelUpEvent;
import io.pinkspider.global.event.UserProfileChangedEvent;
import io.pinkspider.leveluptogethermvp.userservice.profile.application.UserProfileCacheService;
import io.pinkspider.leveluptogethermvp.userservice.profile.domain.dto.UserProfileCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 레벨업 시 프로필 캐시 무효화 + 스냅샷 동기화 이벤트 발행.
 * UserExperienceService(gamification) → UserProfileCacheService(user) 순환 의존을 제거하기 위해
 * 이벤트 리스너로 분리.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class UserLevelUpProfileSyncListener {

    private final UserProfileCacheService userProfileCacheService;
    private final ApplicationEventPublisher eventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserLevelUp(UserLevelUpEvent event) {
        userProfileCacheService.evictUserProfileCache(event.userId());
        try {
            UserProfileCache profile = userProfileCacheService.getUserProfile(event.userId());
            eventPublisher.publishEvent(new UserProfileChangedEvent(
                event.userId(), profile.nickname(), profile.picture(), event.newLevel()));
        } catch (Exception e) {
            log.warn("프로필 스냅샷 이벤트 발행 실패: userId={}", event.userId(), e);
        }
    }
}
