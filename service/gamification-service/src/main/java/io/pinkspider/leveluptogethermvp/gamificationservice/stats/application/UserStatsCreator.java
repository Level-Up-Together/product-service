package io.pinkspider.leveluptogethermvp.gamificationservice.stats.application;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserStats;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-538: user_stats 첫 행 생성을 호출자와 **분리된 트랜잭션**에서 수행한다.
 *
 * <p>왜 별도 빈·별도 트랜잭션인가 — 신규 가입 직후에는 `/api/v1/mypage`(프리페치 2건)와 BFF home 이 동시에 "없으면 생성"을 수행해 유니크 제약
 * (uk_user_stats_user_id)이 충돌한다. 이때 호출자와 같은 트랜잭션에서 flush 가 실패하면 Hibernate 세션이 오염되고 (`don't flush
 * the Session after an exception occurs`) 트랜잭션이 rollback-only 가 되어, 중복을 catch 하고 재조회해도 요청 전체가 500
 * 으로 끝난다. insert 를 REQUIRES_NEW 로 띄우면 제약 위반은 이 내부 트랜잭션만 롤백시키고 호출자의 영속성 컨텍스트는 깨끗하게 남는다.
 *
 * <p>중복 예외는 삼키지 않고 호출자에게 던진다 — 내부에서 catch 하면 rollback-only 로 표시된 내부 트랜잭션이 커밋되며
 * UnexpectedRollbackException 이 나기 때문이다. 호출자는 예외를 받으면 재조회해 상대 요청이 만든 행을 쓴다.
 */
@Service
@RequiredArgsConstructor
public class UserStatsCreator {

    private final UserStatsRepository userStatsRepository;

    /**
     * 신규 user_stats 행을 별도 트랜잭션에서 생성한다.
     *
     * <p>호출자는 반환값에 의존하지 말고(내부 트랜잭션이 끝나면 detached 라 이후 변경이 flush 되지 않는다) 생성 후 자신의 컨텍스트에서 재조회할 것.
     *
     * @throws org.springframework.dao.DataIntegrityViolationException 다른 요청이 먼저 생성한 경우
     */
    @Transactional(
            transactionManager = "gamificationTransactionManager",
            propagation = Propagation.REQUIRES_NEW)
    public void create(String userId) {
        userStatsRepository.saveAndFlush(UserStats.builder().userId(userId).build());
    }
}
