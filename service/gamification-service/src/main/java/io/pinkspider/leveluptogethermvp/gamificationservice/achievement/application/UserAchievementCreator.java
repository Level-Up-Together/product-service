package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application;

import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserAchievementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-538: user_achievement 첫 행 생성을 호출자와 **분리된 트랜잭션**에서 수행한다.
 *
 * <p>신규 가입 직후·홈 BFF 병렬 섹션에서 업적 동기화가 여러 요청에서 동시에 돌면 uk_user_achievement 가 충돌한다. 기존 구현은 같은 트랜잭션에서
 * saveAndFlush 하고 중복을 catch 해 재조회했지만, flush 실패로 세션이 이미 오염돼(`null id in UserAchievement entry`)
 * 트랜잭션이 rollback-only 가 되고 동기화 전체가 실패했다. insert 를 REQUIRES_NEW 로 분리해 호출자 컨텍스트를 보호한다.
 *
 * <p>LUT-719: insert 는 {@code ON CONFLICT DO NOTHING} 네이티브 쿼리라 경합에서도 예외가 나지 않는다 — 예전엔 두 번째 insert 의
 * 중복 키 예외를 잡아 처리했어도 Hibernate·log4jdbc 가 ERROR 로그 3줄을 남겨 운영 로그를 더럽혔다. 생성 여부는 반환값으로 알린다.
 *
 * <p>자세한 배경은 {@link
 * io.pinkspider.leveluptogethermvp.gamificationservice.stats.application.UserStatsCreator} 참고.
 */
@Service
@RequiredArgsConstructor
public class UserAchievementCreator {

    private final UserAchievementRepository userAchievementRepository;

    /**
     * 신규 user_achievement 행(진행도 0)을 별도 트랜잭션에서 생성한다. 이미 있으면 아무것도 바꾸지 않는다.
     *
     * @return true = 이번 호출이 새로 넣음, false = 이미 있어서(다른 요청이 먼저 생성) 넣지 않음
     */
    @Transactional(
            transactionManager = "gamificationTransactionManager",
            propagation = Propagation.REQUIRES_NEW)
    public boolean create(String userId, Long achievementId) {
        return userAchievementRepository.insertIfAbsent(userId, achievementId) > 0;
    }
}
