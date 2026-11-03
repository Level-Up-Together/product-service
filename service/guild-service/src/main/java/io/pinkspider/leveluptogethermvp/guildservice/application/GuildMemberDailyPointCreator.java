package io.pinkspider.leveluptogethermvp.guildservice.application;

import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildMemberDailyPoint;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberDailyPointRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-539: guild_member_daily_point(유저×일자) 첫 행 생성을 호출자와 **분리된 트랜잭션**에서 수행한다.
 *
 * <p>같은 유저의 길드 미션 EXP 적립이 동시에 돌면 uk_guild_member_daily_point 가 충돌한다. 예전처럼 호출자 트랜잭션에서 {@code
 * saveAndFlush + catch 재조회} 하면 세션 오염으로 길드 미션 EXP 적립 트랜잭션 전체가 롤백됐다. insert 를 REQUIRES_NEW 로 분리해 호출자
 * 컨텍스트를 보호한다. 빈 행(dailyExp 0)만 커밋되므로 호출자가 실패해도 데이터 정합성 문제는 없다.
 *
 * <p>중복 예외는 삼키지 않고 호출자에게 던진다. 구조는 gamification 의 {@code UserStatsCreator}(LUT-538) 와 동일.
 */
@Service
@RequiredArgsConstructor
public class GuildMemberDailyPointCreator {

    private final GuildMemberDailyPointRepository dailyPointRepository;

    /**
     * 유저×일자 행을 별도 트랜잭션에서 생성한다. 호출자는 반환값 대신 (guildId, userId, date) 로 재조회해 managed 엔티티를 쓸 것.
     *
     * @throws org.springframework.dao.DataIntegrityViolationException 다른 요청이 먼저 생성한 경우
     */
    @Transactional(
            transactionManager = "guildTransactionManager",
            propagation = Propagation.REQUIRES_NEW)
    public void create(Long guildId, String userId, LocalDate date) {
        dailyPointRepository.saveAndFlush(
                GuildMemberDailyPoint.builder()
                        .guildId(guildId)
                        .userId(userId)
                        .pointDate(date)
                        .build());
    }
}
