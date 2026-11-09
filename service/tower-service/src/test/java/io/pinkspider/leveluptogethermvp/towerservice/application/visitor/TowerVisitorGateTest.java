package io.pinkspider.leveluptogethermvp.towerservice.application.visitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerChatException;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerVisitorException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerVisitorSanctionType;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TowerVisitorGateTest {
    private final TowerVisitorSessionStore store = mock(TowerVisitorSessionStore.class);
    private final TowerVisitorSanctionService sanctions = mock(TowerVisitorSanctionService.class);
    private final TowerVisitorRateLimiter limiter = mock(TowerVisitorRateLimiter.class);
    private final TowerVisitorGate gate = new TowerVisitorGate(store, sanctions, limiter);
    private final TowerVisitorSession known =
            new TowerVisitorSession("vk", 4821, "1.1.1.1", "dev-old", "ua", 5L);

    @Test
    @DisplayName("LUT-705 입장: 모르는 키면 새 세션(새 키·네 자리 번호) · 아는 키면 같은 번호에 이번 IP·기기로 갱신해 저장")
    void admit() {
        when(limiter.allowEnter(any(), anyString())).thenReturn(true);
        when(store.find(null)).thenReturn(Optional.empty());
        TowerVisitorSession fresh =
                gate.admit(TowerVisitorContext.of(null, "dev-1", "2.2.2.2", "ua"));
        assertThat(fresh.visitorKey()).isNotBlank();
        assertThat(fresh.visitorNo()).isBetween(1000, 9999);
        assertThat(fresh.deviceId()).isEqualTo("dev-1");
        verify(store).save(fresh);

        when(store.find("vk")).thenReturn(Optional.of(known));
        TowerVisitorSession again =
                gate.admit(TowerVisitorContext.of("vk", "dev-2", "3.3.3.3", null));
        assertThat(again.visitorKey()).isEqualTo("vk");
        assertThat(again.visitorNo()).isEqualTo(4821);
        assertThat(again.ip()).isEqualTo("3.3.3.3");
        assertThat(again.deviceId()).isEqualTo("dev-2");
        verify(limiter).allowEnter("3.3.3.3", "vk");
    }

    @Test
    @DisplayName(
            "LUT-705 입장 거절: 이번 요청 IP·기기 또는 세션에 남은 IP·기기가 입장 금지면 150501 · IP 빈도 초과는 150503(저장 안 함)")
    void admitRejected() {
        TowerVisitorContext context = TowerVisitorContext.of("vk", "dev-2", "3.3.3.3", null);
        when(sanctions.isBanned(TowerVisitorSanctionType.ENTRY, "3.3.3.3", "dev-2"))
                .thenReturn(true);
        assertThatThrownBy(() -> gate.admit(context))
                .hasFieldOrPropertyWithValue("code", TowerVisitorException.ENTRY_BANNED);

        when(sanctions.isBanned(TowerVisitorSanctionType.ENTRY, "3.3.3.3", "dev-2"))
                .thenReturn(false);
        when(store.find("vk")).thenReturn(Optional.of(known));
        when(sanctions.isBanned(eq(TowerVisitorSanctionType.ENTRY), eq("3.3.3.3"), eq("dev-2")))
                .thenReturn(false, true);
        assertThatThrownBy(() -> gate.admit(context))
                .hasFieldOrPropertyWithValue("code", TowerVisitorException.ENTRY_BANNED);

        when(sanctions.isBanned(any(), any(), any())).thenReturn(false);
        when(limiter.allowEnter("3.3.3.3", "vk")).thenReturn(false);
        assertThatThrownBy(() -> gate.admit(context))
                .hasFieldOrPropertyWithValue("code", TowerVisitorException.RATE_LIMITED);
        verify(store, never()).save(any());
    }

    @Test
    @DisplayName(
            "LUT-705 채팅: 세션 IP·기기 또는 요청 IP 가 채팅 금지면 150502 · IP 빈도 초과는 150302 · 요청 IP 가 없으면 세션 IP 로"
                    + " 센다")
    void checkChat() {
        when(store.find("vk")).thenReturn(Optional.of(known));
        when(limiter.allowChat(any())).thenReturn(true);
        gate.checkChat("vk", "9.9.9.9");
        verify(limiter).allowChat("9.9.9.9");

        when(sanctions.isBanned(TowerVisitorSanctionType.CHAT, "1.1.1.1", "dev-old"))
                .thenReturn(true);
        assertThatThrownBy(() -> gate.checkChat("vk", "9.9.9.9"))
                .hasFieldOrPropertyWithValue("code", TowerVisitorException.CHAT_BANNED);
        when(sanctions.isBanned(TowerVisitorSanctionType.CHAT, "1.1.1.1", "dev-old"))
                .thenReturn(false);
        when(sanctions.isBanned(TowerVisitorSanctionType.CHAT, "9.9.9.9", null)).thenReturn(true);
        assertThatThrownBy(() -> gate.checkChat("vk", "9.9.9.9"))
                .hasFieldOrPropertyWithValue("code", TowerVisitorException.CHAT_BANNED);

        when(sanctions.isBanned(any(), any(), any())).thenReturn(false);
        when(limiter.allowChat("1.1.1.1")).thenReturn(false);
        assertThatThrownBy(() -> gate.checkChat("vk", null))
                .hasFieldOrPropertyWithValue("code", TowerChatException.RATE_LIMITED);

        when(store.find("gone")).thenReturn(Optional.empty());
        gate.checkChat("gone", null);
        ArgumentCaptor<String> ip = ArgumentCaptor.forClass(String.class);
        verify(limiter, org.mockito.Mockito.atLeastOnce()).allowChat(ip.capture());
        assertThat(ip.getAllValues()).containsNull();
    }
}
