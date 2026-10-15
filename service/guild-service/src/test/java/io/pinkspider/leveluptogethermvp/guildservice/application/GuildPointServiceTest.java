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
}
