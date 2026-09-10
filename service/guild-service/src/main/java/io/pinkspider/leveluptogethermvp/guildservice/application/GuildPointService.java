package io.pinkspider.leveluptogethermvp.guildservice.application;

import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildMemberDailyPoint;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberDailyPointRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-483: 길드 활동 포인트 적립.
 *
 * <p>길드 미션 EXP 획득 시점에 해당 유저의 그날(KST) 누적 EXP 로 점수를 재계산하고,
 * 직전 점수와의 차분만큼 길드 totalPoint 에 더한다. 유저×일자 단위 멱등이며 단조 증가만 한다.
 * 누적 포인트가 길드 랭킹과 레벨의 기준이다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GuildPointService {

    // "하루" 경계는 KST — 일일 미션 인스턴스 생성(DailyMissionInstanceScheduler)과 동일 기준.
    // 백필(SQL) 집계도 created_at + 9h 로 같은 기준을 쓴다.
    private static final ZoneId POINT_ZONE = ZoneId.of("Asia/Seoul");

    private final GuildMemberDailyPointRepository dailyPointRepository;
    private final GuildPointProperties pointProperties;

    /**
     * 길드 미션 EXP 획득분을 포인트로 적립한다. 호출자는 guildTransactionManager 트랜잭션 안에서
     * 관리 상태의 Guild 를 넘겨야 한다 (totalPoint 반영이 같은 트랜잭션에 묶인다).
     *
     * @return 이번 적립으로 오른 포인트 (0 이면 경계 미달)
     */
    @Transactional(transactionManager = "guildTransactionManager")
    public int accruePoints(Guild guild, String userId, int expDelta) {
        if (expDelta <= 0 || userId == null) {
            return 0;
        }
        LocalDate today = LocalDate.now(POINT_ZONE);
        GuildMemberDailyPoint daily = findOrCreateDaily(guild.getId(), userId, today);

        int diff = daily.accumulate(expDelta, pointProperties.getUnitExp(), pointProperties.getDailyCapExp());
        dailyPointRepository.save(daily);

        if (diff > 0) {
            guild.addPoint(diff);
            log.info("길드 포인트 적립: guildId={}, userId={}, dailyExp={}, +{}점, totalPoint={}",
                guild.getId(), userId, daily.getDailyExp(), diff, guild.getTotalPoint());
        }
        return diff;
    }

    /**
     * 유저×일자 행 확보 — 동시 적립 레이스는 uk_guild_member_daily_point 가 방어한다
     * (saveAndFlush + DataIntegrityViolationException 재조회 관례).
     */
    private GuildMemberDailyPoint findOrCreateDaily(Long guildId, String userId, LocalDate date) {
        return dailyPointRepository.findByGuildIdAndUserIdAndPointDate(guildId, userId, date)
            .orElseGet(() -> {
                try {
                    return dailyPointRepository.saveAndFlush(GuildMemberDailyPoint.builder()
                        .guildId(guildId)
                        .userId(userId)
                        .pointDate(date)
                        .build());
                } catch (DataIntegrityViolationException e) {
                    return dailyPointRepository.findByGuildIdAndUserIdAndPointDate(guildId, userId, date)
                        .orElseThrow(() -> e);
                }
            });
    }
}
