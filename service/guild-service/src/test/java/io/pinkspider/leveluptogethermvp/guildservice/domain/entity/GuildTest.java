package io.pinkspider.leveluptogethermvp.guildservice.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildVisibility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Guild 엔티티 테스트")
class GuildTest {

    private static Guild guild() {
        return Guild.builder()
                .name("테스트 길드")
                .visibility(GuildVisibility.PUBLIC)
                .masterId("master-1")
                .categoryId(1L)
                .build();
    }

    @Nested
    @DisplayName("removePoint — 취소한 길드 미션 수행의 포인트를 뺀다 (LUT-860)")
    class RemovePointTest {

        @Test
        @DisplayName("누적 포인트와 현재 포인트에서 같은 만큼 뺀다")
        void removePoint_subtractsBoth() {
            Guild guild = guild();
            guild.addPoint(10);

            guild.removePoint(3);

            assertThat(guild.getTotalPoint()).isEqualTo(7);
            assertThat(guild.getCurrentPoint()).isEqualTo(7);
        }

        @Test
        @DisplayName("가진 것보다 많이 빼도 0 밑으로 내려가지 않는다 — 누적·현재 따로 0 에서 멈춘다")
        void removePoint_floorsAtZero() {
            // 레벨 2(누적 30)에서 5점을 더 모은 길드: 누적 35, 현재 5
            Guild guild = guild();
            guild.setTotalPoint(35);
            guild.setCurrentPoint(5);

            guild.removePoint(8);

            assertThat(guild.getTotalPoint()).isEqualTo(27);
            assertThat(guild.getCurrentPoint()).isZero();

            guild.removePoint(100);

            assertThat(guild.getTotalPoint()).isZero();
            assertThat(guild.getCurrentPoint()).isZero();
        }

        @Test
        @DisplayName("포인트가 레벨 기준 밑으로 내려가도 레벨·정원은 건드리지 않는다 (레벨은 내리지 않는다)")
        void removePoint_keepsLevel() {
            // 레벨 3(누적 60)에서 5점을 더 모은 길드
            Guild guild = guild();
            guild.setCurrentLevel(3);
            guild.setTotalPoint(65);
            guild.setCurrentPoint(5);
            Integer maxMembers = guild.getMaxMembers();

            guild.removePoint(6);

            assertThat(guild.getCurrentLevel()).isEqualTo(3);
            assertThat(guild.getMaxMembers()).isEqualTo(maxMembers);
            assertThat(guild.getTotalPoint()).isEqualTo(59);
            assertThat(guild.getCurrentPoint()).isZero();
        }

        @Test
        @DisplayName("0 을 빼면 그대로다")
        void removePoint_zero() {
            Guild guild = guild();
            guild.addPoint(4);

            guild.removePoint(0);

            assertThat(guild.getTotalPoint()).isEqualTo(4);
            assertThat(guild.getCurrentPoint()).isEqualTo(4);
        }
    }
}
