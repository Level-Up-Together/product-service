package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Achievement;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserAchievement;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.AchievementRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserAchievementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-538: user_achievement 첫 행 생성을 호출자와 **분리된 트랜잭션**에서 수행한다.
 *
 * <p>신규 가입 직후 업적 동기화가 여러 요청에서 동시에 돌면 uk_user_achievement 가 충돌한다. 기존 구현은 같은 트랜잭션에서 saveAndFlush 하고
 * 중복을 catch 해 재조회했지만, flush 실패로 세션이 이미 오염돼(`null id in UserAchievement entry`) 트랜잭션이 rollback-only
 * 가 되고 동기화 전체가 실패했다. insert 를 REQUIRES_NEW 로 분리해 호출자 컨텍스트를 보호한다.
 *
 * <p>자세한 배경은 {@link
 * io.pinkspider.leveluptogethermvp.gamificationservice.stats.application.UserStatsCreator} 참고.
 */
@Service
@RequiredArgsConstructor
public class UserAchievementCreator {

    private final UserAchievementRepository userAchievementRepository;
    private final AchievementRepository achievementRepository;

    /**
     * 신규 user_achievement 행(진행도 0)을 별도 트랜잭션에서 생성한다.
     *
     * <p>Achievement 는 id 로만 받아 내부 트랜잭션에서 참조를 얻는다 — 호출자가 가진 인스턴스는 캐시에서 온 detached 엔티티일 수 있어 다른 영속성
     * 컨텍스트로 넘기면 안 된다.
     *
     * @throws org.springframework.dao.DataIntegrityViolationException 다른 요청이 먼저 생성한 경우
     */
    @Transactional(
            transactionManager = "gamificationTransactionManager",
            propagation = Propagation.REQUIRES_NEW)
    public void create(String userId, Long achievementId) {
        Achievement achievement = achievementRepository.getReferenceById(achievementId);
        userAchievementRepository.saveAndFlush(
                UserAchievement.builder()
                        .userId(userId)
                        .achievement(achievement)
                        .currentCount(0)
                        .build());
    }
}
