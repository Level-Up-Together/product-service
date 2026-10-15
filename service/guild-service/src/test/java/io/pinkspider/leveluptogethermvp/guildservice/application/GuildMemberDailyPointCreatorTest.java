package io.pinkspider.leveluptogethermvp.guildservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildMemberDailyPoint;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberDailyPointRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/** LUT-539: guild_member_daily_point 첫 행 생성을 별도 트랜잭션(REQUIRES_NEW)에서 수행하는 전용 빈. */
@ExtendWith(MockitoExtension.class)
class GuildMemberDailyPointCreatorTest {

    @Mock private GuildMemberDailyPointRepository dailyPointRepository;

    @InjectMocks private GuildMemberDailyPointCreator creator;

    @Test
    @DisplayName("유저×일자 빈 행(dailyExp 0)을 즉시 flush 하며 생성한다")
    void create_savesAndFlushesEmptyRow() {
        LocalDate date = LocalDate.of(2026, 10, 13);
        when(dailyPointRepository.saveAndFlush(any(GuildMemberDailyPoint.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        creator.create(1L, "user-1", date);

        ArgumentCaptor<GuildMemberDailyPoint> captor =
                ArgumentCaptor.forClass(GuildMemberDailyPoint.class);
        verify(dailyPointRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getGuildId()).isEqualTo(1L);
        assertThat(captor.getValue().getUserId()).isEqualTo("user-1");
        assertThat(captor.getValue().getPointDate()).isEqualTo(date);
        assertThat(captor.getValue().getDailyExp()).isZero();
    }

    @Test
    @DisplayName("중복 키 예외는 삼키지 않고 호출자에게 전파한다")
    void create_propagatesDuplicateKey() {
        when(dailyPointRepository.saveAndFlush(any(GuildMemberDailyPoint.class)))
                .thenThrow(new DataIntegrityViolationException("uk_guild_member_daily_point"));

        assertThatThrownBy(() -> creator.create(1L, "user-1", LocalDate.of(2026, 10, 13)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
