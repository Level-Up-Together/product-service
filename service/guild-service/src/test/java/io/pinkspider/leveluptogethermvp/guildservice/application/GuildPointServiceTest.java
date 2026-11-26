package io.pinkspider.leveluptogethermvp.guildservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.pinkspider.global.test.TestReflectionUtils;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildMemberDailyPoint;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildVisibility;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberDailyPointRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@DisplayName("GuildPointService 테스트 (LUT-483)")
class GuildPointServiceTest {

    @Mock private GuildMemberDailyPointRepository dailyPointRepository;

    @Mock private GuildMemberDailyPointCreator dailyPointCreator;

    @Spy
    private GuildPointProperties pointProperties = new GuildPointProperties(); // unit 10 / cap 60

    @InjectMocks private GuildPointService guildPointService;

    private Guild guild;
    private static final String USER = "user-1";

    @BeforeEach
    void setUp() {
        guild =
                Guild.builder()
                        .name("테스트 길드")
                        .visibility(GuildVisibility.PUBLIC)
                        .masterId(USER)
                        .maxMembers(10)
                        .categoryId(1L)
                        .build();
        TestReflectionUtils.setField(guild, "id", 1L);
    }

    private GuildMemberDailyPoint dailyRow(int dailyExp, int points) {
        GuildMemberDailyPoint row =
                GuildMemberDailyPoint.builder()
                        .guildId(1L)
                        .userId(USER)
                        .pointDate(LocalDate.now())
                        .dailyExp(dailyExp)
                        .points(points)
                        .build();
        return row;
    }

    @Test
    @DisplayName("10 EXP 경계를 넘는 순간 포인트가 적립된다 (19 EXP → 1점)")
    void accrue_firstExp() {
        when(dailyPointRepository.findByGuildIdAndUserIdAndPointDate(any(), any(), any()))
                .thenReturn(Optional.of(dailyRow(0, 0)));

        int diff = guildPointService.accruePoints(guild, USER, 19);

        assertThat(diff).isEqualTo(1);
        assertThat(guild.getTotalPoint()).isEqualTo(1);
        assertThat(guild.getLastPointAt()).isNotNull();
    }

    @Test
    @DisplayName("누적 기준 차분 적립 — 19 이후 13 획득 시 누적 32 로 +2점 (건별 계산이면 +1점)")
    void accrue_cumulativeDiff() {
        when(dailyPointRepository.findByGuildIdAndUserIdAndPointDate(any(), any(), any()))
                .thenReturn(Optional.of(dailyRow(19, 1)));

        int diff = guildPointService.accruePoints(guild, USER, 13);

        assertThat(diff).isEqualTo(2); // floor(32/10)=3, 직전 1점 → +2
        assertThat(guild.getTotalPoint()).isEqualTo(2);
    }

    @Test
    @DisplayName("상한 60 EXP — 누적 62 라도 6점에서 멈춘다 (티켓 예시 19→13→30)")
    void accrue_dailyCap() {
        when(dailyPointRepository.findByGuildIdAndUserIdAndPointDate(any(), any(), any()))
                .thenReturn(Optional.of(dailyRow(32, 3)));

        int diff = guildPointService.accruePoints(guild, USER, 30);

        assertThat(diff).isEqualTo(3); // min(62,60)/10=6, 직전 3점 → +3
        assertThat(guild.getTotalPoint()).isEqualTo(3);
    }

    @Test
    @DisplayName("상한 도달 후 추가 EXP 는 버려진다 (0점 적립, lastPointAt 미변경)")
    void accrue_afterCap_noMorePoints() {
        when(dailyPointRepository.findByGuildIdAndUserIdAndPointDate(any(), any(), any()))
                .thenReturn(Optional.of(dailyRow(60, 6)));

        int diff = guildPointService.accruePoints(guild, USER, 100);

        assertThat(diff).isZero();
        assertThat(guild.getTotalPoint()).isZero();
        assertThat(guild.getLastPointAt()).isNull();
    }

    @Test
    @DisplayName("경계 미달(9 EXP)이면 0점 — 길드 포인트 불변")
    void accrue_belowUnit_noPoint() {
        when(dailyPointRepository.findByGuildIdAndUserIdAndPointDate(any(), any(), any()))
                .thenReturn(Optional.of(dailyRow(0, 0)));

        int diff = guildPointService.accruePoints(guild, USER, 9);

        assertThat(diff).isZero();
        assertThat(guild.getTotalPoint()).isZero();
    }

    @Test
    @DisplayName("유저×일자 행이 없으면 크리에이터(REQUIRES_NEW)로 생성한 뒤 재조회한 managed 행에 적립한다")
    void accrue_createsDailyRow() {
        GuildMemberDailyPoint created = dailyRow(0, 0);
        when(dailyPointRepository.findByGuildIdAndUserIdAndPointDate(any(), any(), any()))
                .thenReturn(Optional.empty(), Optional.of(created));

        int diff = guildPointService.accruePoints(guild, USER, 25);

        assertThat(diff).isEqualTo(2);
        assertThat(guild.getTotalPoint()).isEqualTo(2);
        verify(dailyPointCreator).create(eq(1L), eq(USER), any(LocalDate.class));
        // 재조회된 행에 누적됐다 (내부 트랜잭션 반환값이 아니라)
        assertThat(created.getDailyExp()).isEqualTo(25);
        verify(dailyPointRepository).save(created);
    }

    @Test
    @DisplayName("LUT-539: 동시 적립로 중복 키가 나도 EXP 적립 트랜잭션이 깨지지 않고 상대가 만든 행에 적립한다")
    void accrue_duplicateRace_usesOtherRow() {
        GuildMemberDailyPoint createdByOther = dailyRow(0, 0);
        when(dailyPointRepository.findByGuildIdAndUserIdAndPointDate(any(), any(), any()))
                .thenReturn(Optional.empty(), Optional.of(createdByOther));
        doThrow(new DataIntegrityViolationException("uk_guild_member_daily_point"))
                .when(dailyPointCreator)
                .create(eq(1L), eq(USER), any(LocalDate.class));

        int diff = guildPointService.accruePoints(guild, USER, 25);

        assertThat(diff).isEqualTo(2);
        assertThat(createdByOther.getDailyExp()).isEqualTo(25);
    }

    @Test
    @DisplayName("LUT-539: 생성 후 재조회에 행이 없으면 IllegalStateException")
    void accrue_missingAfterCreate_throws() {
        when(dailyPointRepository.findByGuildIdAndUserIdAndPointDate(any(), any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> guildPointService.accruePoints(guild, USER, 25))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("after creation");
    }

    @Test
    @DisplayName("EXP 0 이하 또는 userId 없음 — no-op")
    void accrue_invalidInput_noop() {
        assertThat(guildPointService.accruePoints(guild, USER, 0)).isZero();
        assertThat(guildPointService.accruePoints(guild, null, 10)).isZero();
        verifyNoInteractions(dailyPointRepository);
    }

    // ===== LUT-860 완료한 길드 미션 수행 취소 — 포인트 회수 =====

    private static final LocalDate POINT_DATE = LocalDate.of(2026, 11, 26);

    @Test
    @DisplayName("LUT-860: 그날 누적에서 EXP 를 빼고 다시 계산한 포인트와의 차이만큼 길드 포인트에서 뺀다 (32 → 19 EXP, -2점)")
    void revoke_reducesGuildPoints() {
        guild.addPoint(10);
        GuildMemberDailyPoint daily = dailyRow(32, 3);
        when(dailyPointRepository.findByGuildIdAndUserIdAndPointDate(1L, USER, POINT_DATE))
                .thenReturn(Optional.of(daily));

        int diff = guildPointService.revokePoints(guild, USER, 13, POINT_DATE);

        assertThat(diff).isEqualTo(2);
        assertThat(daily.getDailyExp()).isEqualTo(19);
        assertThat(daily.getPoints()).isEqualTo(1);
        assertThat(guild.getTotalPoint()).isEqualTo(8);
        assertThat(guild.getCurrentPoint()).isEqualTo(8);
        verify(dailyPointRepository).save(daily);
    }

    @Test
    @DisplayName("LUT-860: 다시 계산한 포인트가 같으면(상한 위 100 → 70 EXP) 누적 EXP 만 줄이고 길드 포인트는 그대로다")
    void revoke_noPointDiff_guildUntouched() {
        guild.addPoint(10);
        GuildMemberDailyPoint daily = dailyRow(100, 6);
        when(dailyPointRepository.findByGuildIdAndUserIdAndPointDate(1L, USER, POINT_DATE))
                .thenReturn(Optional.of(daily));

        int diff = guildPointService.revokePoints(guild, USER, 30, POINT_DATE);

        assertThat(diff).isZero();
        assertThat(daily.getDailyExp()).isEqualTo(70);
        assertThat(daily.getPoints()).isEqualTo(6);
        assertThat(guild.getTotalPoint()).isEqualTo(10);
        // 줄어든 누적 EXP 는 저장한다 — 다음 적립이 이 값에서 이어진다
        verify(dailyPointRepository).save(daily);
    }

    @Test
    @DisplayName("LUT-860: 그날 행이 없으면 아무것도 하지 않는다 (행을 만들지도 않는다)")
    void revoke_noDailyRow_noop() {
        guild.addPoint(10);
        when(dailyPointRepository.findByGuildIdAndUserIdAndPointDate(1L, USER, POINT_DATE))
                .thenReturn(Optional.empty());

        int diff = guildPointService.revokePoints(guild, USER, 13, POINT_DATE);

        assertThat(diff).isZero();
        assertThat(guild.getTotalPoint()).isEqualTo(10);
        verify(dailyPointRepository, org.mockito.Mockito.never()).save(any());
        verifyNoInteractions(dailyPointCreator);
    }

    @Test
    @DisplayName("LUT-860: 포인트 날짜(완료한 날)의 행을 찾는다 — 오늘 행이 아니다")
    void revoke_usesGivenPointDate() {
        LocalDate yesterday = POINT_DATE.minusDays(1);
        when(dailyPointRepository.findByGuildIdAndUserIdAndPointDate(1L, USER, yesterday))
                .thenReturn(Optional.of(dailyRow(20, 2)));

        int diff = guildPointService.revokePoints(guild, USER, 20, yesterday);

        assertThat(diff).isEqualTo(2);
        verify(dailyPointRepository).findByGuildIdAndUserIdAndPointDate(1L, USER, yesterday);
    }

    @Test
    @DisplayName("LUT-860: 길드 포인트가 회수할 양보다 적어도 0 밑으로 내려가지 않는다")
    void revoke_guildPointsFloorAtZero() {
        guild.addPoint(1);
        when(dailyPointRepository.findByGuildIdAndUserIdAndPointDate(1L, USER, POINT_DATE))
                .thenReturn(Optional.of(dailyRow(60, 6)));

        int diff = guildPointService.revokePoints(guild, USER, 60, POINT_DATE);

        assertThat(diff).isEqualTo(6);
        assertThat(guild.getTotalPoint()).isZero();
        assertThat(guild.getCurrentPoint()).isZero();
    }

    @Test
    @DisplayName("LUT-860: EXP 0 이하 · userId 없음 · 날짜 없음 — no-op")
    void revoke_invalidInput_noop() {
        assertThat(guildPointService.revokePoints(guild, USER, 0, POINT_DATE)).isZero();
        assertThat(guildPointService.revokePoints(guild, USER, -5, POINT_DATE)).isZero();
        assertThat(guildPointService.revokePoints(guild, null, 10, POINT_DATE)).isZero();
        assertThat(guildPointService.revokePoints(guild, USER, 10, null)).isZero();
        verifyNoInteractions(dailyPointRepository);
    }

    @Test
    @DisplayName("LUT-860: 적립한 것을 같은 날짜로 회수하면 길드 포인트가 적립 전으로 돌아간다")
    void accrueThenRevoke_roundTrip() {
        GuildMemberDailyPoint daily = dailyRow(19, 1);
        when(dailyPointRepository.findByGuildIdAndUserIdAndPointDate(any(), any(), any()))
                .thenReturn(Optional.of(daily));
        guild.addPoint(1);

        int gained = guildPointService.accruePoints(guild, USER, 13);
        int revoked = guildPointService.revokePoints(guild, USER, 13, POINT_DATE);

        assertThat(gained).isEqualTo(2);
        assertThat(revoked).isEqualTo(2);
        assertThat(guild.getTotalPoint()).isEqualTo(1);
        assertThat(daily.getDailyExp()).isEqualTo(19);
        assertThat(daily.getPoints()).isEqualTo(1);
    }

    @Test
    @DisplayName("LUT-860: pointDateOf — 포인트의 하루는 KST. UTC 14:59:59 는 그날, 15:00:00 은 다음 날")
    void pointDateOf_kstBoundary() {
        assertThat(
                        GuildPointService.pointDateOf(
                                java.time.LocalDateTime.of(2026, 11, 25, 14, 59, 59)))
                .isEqualTo(LocalDate.of(2026, 11, 25));
        assertThat(
                        GuildPointService.pointDateOf(
                                java.time.LocalDateTime.of(2026, 11, 25, 15, 0, 0)))
                .isEqualTo(LocalDate.of(2026, 11, 26));
        // UTC 자정 근처는 KST 로 같은 날 오전이다
        assertThat(GuildPointService.pointDateOf(java.time.LocalDateTime.of(2026, 11, 25, 23, 59)))
                .isEqualTo(LocalDate.of(2026, 11, 26));
        assertThat(GuildPointService.pointDateOf(java.time.LocalDateTime.of(2026, 11, 26, 0, 0)))
                .isEqualTo(LocalDate.of(2026, 11, 26));
        // 연말 경계
        assertThat(GuildPointService.pointDateOf(java.time.LocalDateTime.of(2026, 12, 31, 15, 0)))
                .isEqualTo(LocalDate.of(2027, 1, 1));
    }
}
