package io.pinkspider.leveluptogethermvp.towerservice.application.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.towerservice.application.TowerPopulationService;
import io.pinkspider.leveluptogethermvp.towerservice.application.TowerSettingService;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerEntryException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerQueueStatus;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TowerQueueServiceTest {

    private final InMemoryTowerQueueStore store = new InMemoryTowerQueueStore();
    private final TowerSettingService settings = mock(TowerSettingService.class);
    private final TowerPopulationService population = mock(TowerPopulationService.class);
    private final AtomicLong now = new AtomicLong(1_000_000L);
    private TowerQueueService service;

    @BeforeEach
    void setUp() {
        when(settings.getMaxConcurrentUsers()).thenReturn(Optional.of(2));
        when(population.of(anyInt())).thenReturn(42L);
        service = new TowerQueueService(store, settings, population, now::get);
    }

    private TowerQueueStatus waitFor(String userId) {
        TowerQueueService.Gate gate = service.check(userId, 1, null);
        assertThat(gate.pass()).isFalse();
        return gate.waiting();
    }

    @Test
    @DisplayName("상한이 없으면 늘 통과(남은 표는 버린다) · 자리가 있고 기다리는 사람이 없으면 통과")
    void passWhenFree() {
        when(settings.getMaxConcurrentUsers()).thenReturn(Optional.empty());
        TowerQueueService noLimit = new TowerQueueService(store, settings, population, now::get);
        store.enqueue(new TowerQueueTicket("old", "u1", 1, 1, 1, null));
        assertThat(noLimit.check("u1", 1, null).pass()).isTrue();
        assertThat(store.tickets).isEmpty();
        assertThat(noLimit.admitPending()).isZero();

        store.connected = 1;
        assertThat(service.check("u2", 1, null).pass()).isTrue();
        assertThat(service.check(null, 1, null).pass()).isTrue();
    }

    @Test
    @DisplayName("상한이면 순번을 받는다 — 로그인 유저 줄이 방문객보다 앞(방문객이 먼저 섰어도), 같은 유저가 다시 요청해도 새로 서지 않는다")
    void queueOrder() {
        store.connected = 2;
        TowerQueueStatus visitor = waitFor(null);
        assertThat(visitor.position()).isEqualTo(1);
        now.addAndGet(10);
        TowerQueueStatus user = waitFor("u1");
        assertThat(user.status()).isEqualTo(TowerQueueStatus.WAITING);
        assertThat(user.position()).isEqualTo(1);
        assertThat(user.estimatedWaitSeconds()).isEqualTo(20);
        assertThat(user.population()).isEqualTo(42L);
        assertThat(service.status(visitor.ticket(), null).position()).isEqualTo(2);
        TowerQueueStatus again = waitFor("u1");
        assertThat(again.ticket()).isEqualTo(user.ticket());
        assertThat(store.users).hasSize(1);
    }

    @Test
    @DisplayName("자리가 나면 로그인 유저부터 입장 신호(30초) → 그 표로 입장하면 통과·표 삭제, 순번이 당겨진다")
    void admitAndEnter() {
        store.connected = 2;
        TowerQueueStatus visitor = waitFor(null);
        TowerQueueStatus first = waitFor("u1");
        TowerQueueStatus second = waitFor("u2");
        assertThat(second.position()).isEqualTo(2);

        store.connected = 1; // 한 명이 나갔다
        assertThat(service.admitPending()).isEqualTo(1);
        TowerQueueStatus admitted = service.status(first.ticket(), "u1");
        assertThat(admitted.status()).isEqualTo(TowerQueueStatus.ADMITTED);
        assertThat(admitted.admitExpiresInSeconds()).isEqualTo(30);
        assertThat(admitted.position()).isNull();
        assertThat(service.status(second.ticket(), "u2").position()).isEqualTo(1);
        assertThat(service.status(visitor.ticket(), null).position()).isEqualTo(2);

        // 입장 가능 표로 입장 → 통과·표 삭제
        assertThat(service.check("u1", 1, first.ticket()).pass()).isTrue();
        assertThat(store.find(first.ticket())).isEmpty();
    }

    @Test
    @DisplayName("입장 신호 뒤 30초가 지나면 그 표는 버려지고 다음 순번에 신호가 간다 · 지난 표로 조회하면 404")
    void admitWindowExpires() {
        store.connected = 2;
        TowerQueueStatus first = waitFor("u1");
        TowerQueueStatus second = waitFor("u2");
        store.connected = 1;
        service.admitPending();
        now.addAndGet(31_000);
        service.status(second.ticket(), "u2"); // 조회가 계산을 돌린다
        assertThat(store.find(first.ticket())).isEmpty();
        assertThat(service.status(second.ticket(), "u2").status())
                .isEqualTo(TowerQueueStatus.ADMITTED);
        assertThatThrownBy(() -> service.status(first.ticket(), "u1"))
                .isInstanceOf(TowerEntryException.class)
                .extracting("code")
                .isEqualTo(TowerEntryException.QUEUE_TICKET_NOT_FOUND);
    }

    @Test
    @DisplayName("표 없이 줄을 건너뛰려는 요청은 자리가 나도 줄 끝에 선다 · 기다리는 표로 다시 요청하면 그대로 대기")
    void noSkipping() {
        store.connected = 2;
        TowerQueueStatus first = waitFor("u1");
        store.connected = 0; // 자리는 났지만 u1 이 먼저다
        TowerQueueService.Gate jumper = service.check("u9", 1, null);
        assertThat(jumper.pass()).isFalse();
        assertThat(service.status(first.ticket(), "u1").status())
                .isEqualTo(TowerQueueStatus.ADMITTED);

        store.connected = 2;
        store.admitted.clear();
        TowerQueueStatus waiting = waitFor("u3");
        TowerQueueService.Gate retry = service.check("u3", 1, waiting.ticket());
        assertThat(retry.pass()).isFalse();
        assertThat(retry.waiting().ticket()).isEqualTo(waiting.ticket());
    }

    @Test
    @DisplayName("입장 창이 지난 표로 입장을 요청하면 줄 끝에 다시 선다")
    void expiredTicketRequeues() {
        store.connected = 2;
        TowerQueueStatus first = waitFor("u1");
        store.markAdmitted(store.find(first.ticket()).orElseThrow(), now.get() - 1);
        TowerQueueService.Gate gate = service.check("u1", 1, first.ticket());
        assertThat(gate.pass()).isFalse();
        assertThat(gate.waiting().ticket()).isNotEqualTo(first.ticket());
    }

    @Test
    @DisplayName("대기 화면이 20초 동안 조회하지 않으면 줄에서 빠진다 · 대기 그만하기는 즉시 · 남의 표는 보거나 지울 수 없다")
    void staleAndLeave() {
        store.connected = 2;
        TowerQueueStatus quiet = waitFor("u1");
        TowerQueueStatus active = waitFor("u2");
        now.addAndGet(21_000);
        service.status(active.ticket(), "u2");
        assertThat(store.find(quiet.ticket())).isEmpty();
        assertThat(service.status(active.ticket(), "u2").position()).isEqualTo(1);

        assertThatThrownBy(() -> service.status(active.ticket(), "intruder"))
                .isInstanceOf(TowerEntryException.class);
        service.leave(active.ticket(), "intruder");
        assertThat(store.find(active.ticket())).isPresent();
        service.leave(active.ticket(), "u2");
        assertThat(store.find(active.ticket())).isEmpty();
        service.leave("missing", null);
    }

    @Test
    @DisplayName("다른 노드가 계산 중이면(잠금) 넘긴다 · 표 정보가 사라진 줄 항목은 정리 · 상한은 5초 동안 다시 읽지 않는다")
    void lockAndCache() {
        store.connected = 2;
        waitFor("u1");
        store.locked = true;
        store.connected = 0;
        assertThat(service.admitPending()).isZero();
        store.locked = false;

        store.users.add("ghost");
        assertThat(service.admitPending()).isEqualTo(1);
        assertThat(store.users).doesNotContain("ghost");
        verify(settings, times(1)).getMaxConcurrentUsers();
        now.addAndGet(6_000);
        service.admitPending();
        verify(settings, times(2)).getMaxConcurrentUsers();
    }

    @Test
    @DisplayName("줄 머리 항목의 표 정보가 사라졌으면 건너뛴다")
    void headWithoutTicket() {
        store.connected = 2;
        waitFor("u1");
        store.users.add(0, "dangling");
        store.connected = 1;
        // 정리 단계에서 dangling 이 빠지므로 u1 이 신호를 받는다
        assertThat(service.admitPending()).isEqualTo(1);
    }
}
