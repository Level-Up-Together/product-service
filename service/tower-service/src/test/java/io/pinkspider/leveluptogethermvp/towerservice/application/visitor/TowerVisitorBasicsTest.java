package io.pinkspider.leveluptogethermvp.towerservice.application.visitor;

import static org.assertj.core.api.Assertions.assertThat;

import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerVisitorSanction;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerVisitorSanctionTarget;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerVisitorSanctionType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TowerVisitorBasicsTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 4, 12, 0);

    @Test
    @DisplayName("문맥: 빈 값은 null, 앞뒤 공백 제거, 긴 값은 자른다 · none 은 전부 null")
    void context() {
        TowerVisitorContext context =
                TowerVisitorContext.of(" vk ", " ", "1.2.3.4", "x".repeat(600));
        assertThat(context.visitorKey()).isEqualTo("vk");
        assertThat(context.deviceId()).isNull();
        assertThat(context.ip()).isEqualTo("1.2.3.4");
        assertThat(context.userAgent()).hasSize(500);
        assertThat(TowerVisitorContext.of(null, "d".repeat(200), null, null).deviceId())
                .hasSize(128);
        assertThat(TowerVisitorContext.none())
                .isEqualTo(new TowerVisitorContext(null, null, null, null));
    }

    @Test
    @DisplayName("세션 갱신: 이번 입장 값이 있으면 덮고 없으면 이전 값 유지 · 키·번호·생성 시각은 그대로")
    void sessionRefresh() {
        TowerVisitorSession session =
                new TowerVisitorSession("vk", 4821, "1.1.1.1", "d1", "ua", 5L);
        TowerVisitorSession refreshed =
                session.refreshedBy(TowerVisitorContext.of("vk", null, "2.2.2.2", null));
        assertThat(refreshed)
                .isEqualTo(new TowerVisitorSession("vk", 4821, "2.2.2.2", "d1", "ua", 5L));
        assertThat(session.refreshedBy(TowerVisitorContext.of(null, "d2", null, "ua2")))
                .isEqualTo(new TowerVisitorSession("vk", 4821, "1.1.1.1", "d2", "ua2", 5L));
    }

    @Test
    @DisplayName("태그: 같은 키면 같은 16자리, 다른 키면 다르다 · 키가 없으면 null · 키 자체는 드러나지 않는다")
    void tag() {
        String tag = TowerVisitorTag.of("vk-1");
        assertThat(tag).hasSize(16).isEqualTo(TowerVisitorTag.of("vk-1"));
        assertThat(tag).isNotEqualTo(TowerVisitorTag.of("vk-2")).doesNotContain("vk-1");
        assertThat(TowerVisitorTag.of(null)).isNull();
        assertThat(TowerVisitorTag.of(" ")).isNull();
    }

    @Test
    @DisplayName("제재: 입장 금지는 채팅도 막고 채팅 금지는 입장을 막지 않는다 · IP/기기 대상 · 끝났거나 해제되면 무효")
    void sanction() {
        assertThat(TowerVisitorSanctionType.ENTRY.blocks(TowerVisitorSanctionType.CHAT)).isTrue();
        assertThat(TowerVisitorSanctionType.CHAT.blocks(TowerVisitorSanctionType.ENTRY)).isFalse();

        TowerVisitorSanction ipChat =
                sanction(
                        TowerVisitorSanctionTarget.IP,
                        "1.2.3.4",
                        TowerVisitorSanctionType.CHAT,
                        null);
        assertThat(ipChat.blocks(TowerVisitorSanctionType.CHAT, "1.2.3.4", null, NOW)).isTrue();
        assertThat(ipChat.blocks(TowerVisitorSanctionType.CHAT, "9.9.9.9", "1.2.3.4", NOW))
                .isFalse();
        assertThat(ipChat.blocks(TowerVisitorSanctionType.ENTRY, "1.2.3.4", null, NOW)).isFalse();

        TowerVisitorSanction deviceEntry =
                sanction(
                        TowerVisitorSanctionTarget.DEVICE,
                        "dev-1",
                        TowerVisitorSanctionType.ENTRY,
                        NOW.plusMinutes(5));
        assertThat(deviceEntry.blocks(TowerVisitorSanctionType.CHAT, null, "dev-1", NOW)).isTrue();
        assertThat(deviceEntry.blocks(TowerVisitorSanctionType.ENTRY, "dev-1", null, NOW))
                .isFalse();
        assertThat(
                        deviceEntry.blocks(
                                TowerVisitorSanctionType.ENTRY, null, "dev-1", NOW.plusMinutes(6)))
                .isFalse();

        deviceEntry.lift(NOW);
        LocalDateTime first = deviceEntry.getLiftedAt();
        deviceEntry.lift(NOW.plusDays(1));
        assertThat(deviceEntry.getLiftedAt()).isEqualTo(first);
        assertThat(deviceEntry.isActive(NOW)).isFalse();
    }

    static TowerVisitorSanction sanction(
            TowerVisitorSanctionTarget target,
            String value,
            TowerVisitorSanctionType type,
            LocalDateTime expiresAt) {
        return TowerVisitorSanction.builder()
                .id(1L)
                .targetType(target)
                .targetValue(value)
                .sanctionType(type)
                .expiresAt(expiresAt)
                .build();
    }
}
