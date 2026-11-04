package io.pinkspider.leveluptogethermvp.towerservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.leveluptogethermvp.profanity.domain.enums.ProfanitySeverity;
import io.pinkspider.leveluptogethermvp.towerservice.application.chat.TowerChatGuard;
import io.pinkspider.leveluptogethermvp.towerservice.application.chat.TowerChatHistoryStore;
import io.pinkspider.leveluptogethermvp.towerservice.application.chat.TowerChatLogWriter;
import io.pinkspider.leveluptogethermvp.towerservice.application.chat.TowerChatModerationService;
import io.pinkspider.leveluptogethermvp.towerservice.application.chat.TowerChatModerationService.ModerationResult;
import io.pinkspider.leveluptogethermvp.towerservice.application.chat.TowerChatProperties;
import io.pinkspider.leveluptogethermvp.towerservice.application.chat.TowerCheerWindowStore;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerChatException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerChatHistoryResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerChatMessage;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerInstanceEvent;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerReactionResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerChatLog;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerOccupant;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerOccupantRepository;
import io.pinkspider.leveluptogethermvp.towerservice.presence.TowerPresence;
import io.pinkspider.leveluptogethermvp.towerservice.presence.TowerPresenceStore;
import io.pinkspider.leveluptogethermvp.towerservice.realtime.TowerRealtimePublisher;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TowerChatService (LUT-633)")
class TowerChatServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 4, 13, 0);
    @Mock private TowerOccupantRepository occupantRepository;
    @Mock private TowerPresenceStore presenceStore;
    @Mock private TowerChatModerationService moderationService;
    @Mock private TowerChatGuard guard;
    @Mock private TowerChatHistoryStore historyStore;
    @Mock private TowerCheerWindowStore cheerWindowStore;
    @Mock private TowerChatLogWriter logWriter;
    @Mock private TowerRealtimePublisher publisher;
    @Mock private TowerSeatService seatService;
    @Mock private UserQueryFacade userQueryFacade;
    private final TowerChatProperties properties = new TowerChatProperties();
    private TowerChatService service;

    private TowerOccupant user;
    private TowerOccupant visitor;

    @BeforeEach
    void setUp() {
        service =
                new TowerChatService(
                        occupantRepository,
                        presenceStore,
                        moderationService,
                        guard,
                        historyStore,
                        cheerWindowStore,
                        logWriter,
                        publisher,
                        seatService,
                        properties,
                        userQueryFacade);
        user = TowerOccupant.direct(5L, 1, "u1", false, 10, 10, NOW);
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", 7L);
        visitor = TowerOccupant.visitor(5L, 1, "vk", 4821, 10, 10, NOW);
        org.springframework.test.util.ReflectionTestUtils.setField(visitor, "id", 8L);
    }

    private void loginOk() {
        when(occupantRepository.findByUserId("u1")).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("전송: 점유자 확인 → 가드 → 금칙어 처리 → 이력·로그·브로드캐스트, 닉네임은 presence 에서")
    void send() {
        loginOk();
        when(guard.tryAcquireSend(7L, false)).thenReturn(true);
        when(moderationService.moderate("안녕 바보야"))
                .thenReturn(
                        new ModerationResult(
                                "안녕 **야", true, false, ProfanitySeverity.LOW, List.of("바보")));
        when(presenceStore.find(5L, 7L))
                .thenReturn(
                        Optional.of(
                                new TowerPresence(
                                        7L, 5L, 1, "u1", "DIRECT", null, "루미", null, 12, List.of(),
                                        10, 10, "REST", true, false, NOW)));

        TowerChatMessage message = service.send(5L, "u1", null, "  안녕 바보야 ");

        assertThat(message.content()).isEqualTo("안녕 **야");
        assertThat(message.masked()).isTrue();
        assertThat(message.nickname()).isEqualTo("루미");
        assertThat(message.occupantId()).isEqualTo(7L);
        verify(guard).rememberContent(7L, "안녕 바보야");
        verify(guard, never()).recordMediumHit(anyLong());
        verify(historyStore).push(message);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publishToInstance(eq(5L), captor.capture());
        TowerInstanceEvent event = (TowerInstanceEvent) captor.getValue();
        assertThat(event.type()).isEqualTo("CHAT");
        assertThat(event.chat().content()).isEqualTo("안녕 **야");
        ArgumentCaptor<TowerChatLog> log = ArgumentCaptor.forClass(TowerChatLog.class);
        verify(logWriter).write(log.capture());
        assertThat(log.getValue().getOriginalContent()).isEqualTo("안녕 바보야");
        assertThat(log.getValue().getResult()).isEqualTo("MASKED");
        assertThat(log.getValue().getMatchedWords()).isEqualTo("바보");
    }

    @Test
    @DisplayName(
            "거절 순서: 방 밖 150301 · 미션 중 150304 · 길이 150307 · 제한 중 150306 · 간격 150302 · 중복 150303")
    void rejections() {
        when(occupantRepository.findByUserId("nobody")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.send(5L, "nobody", null, "x"))
                .hasFieldOrPropertyWithValue("code", TowerChatException.NOT_IN_INSTANCE);
        TowerOccupant elsewhere = TowerOccupant.direct(9L, 2, "u2", false, 0, 0, NOW);
        when(occupantRepository.findByUserId("u2")).thenReturn(Optional.of(elsewhere));
        assertThatThrownBy(() -> service.send(5L, "u2", null, "x"))
                .hasFieldOrPropertyWithValue("code", TowerChatException.NOT_IN_INSTANCE);
        assertThatThrownBy(() -> service.send(5L, null, " ", "x"))
                .hasFieldOrPropertyWithValue("code", TowerChatException.NOT_IN_INSTANCE);

        loginOk();
        user.startMission(NOW);
        assertThatThrownBy(() -> service.send(5L, "u1", null, "x"))
                .hasFieldOrPropertyWithValue("code", TowerChatException.MISSION_IN_PROGRESS);
        user.endMission();

        assertThatThrownBy(() -> service.send(5L, "u1", null, "   "))
                .hasFieldOrPropertyWithValue("code", TowerChatException.CONTENT_INVALID);
        assertThatThrownBy(() -> service.send(5L, "u1", null, "a".repeat(201)))
                .hasFieldOrPropertyWithValue("code", TowerChatException.CONTENT_INVALID);

        when(guard.isMuted(7L)).thenReturn(true);
        assertThatThrownBy(() -> service.send(5L, "u1", null, "x"))
                .hasFieldOrPropertyWithValue("code", TowerChatException.MUTED);
        when(guard.isMuted(7L)).thenReturn(false);
        when(guard.tryAcquireSend(7L, false)).thenReturn(false);
        assertThatThrownBy(() -> service.send(5L, "u1", null, "x"))
                .hasFieldOrPropertyWithValue("code", TowerChatException.RATE_LIMITED);
        when(guard.tryAcquireSend(7L, false)).thenReturn(true);
        when(guard.isDuplicate(7L, "x")).thenReturn(true);
        assertThatThrownBy(() -> service.send(5L, "u1", null, "x"))
                .hasFieldOrPropertyWithValue("code", TowerChatException.DUPLICATE);
        verify(publisher, never()).publishToInstance(anyLong(), any());
    }

    @Test
    @DisplayName("HIGH 금칙어는 거부(150305)하되 원문을 로그에 남기고, MEDIUM 은 가리고 반복 적립")
    void profanity() {
        loginOk();
        when(guard.tryAcquireSend(7L, false)).thenReturn(true);
        when(moderationService.moderate("very bad"))
                .thenReturn(
                        new ModerationResult(
                                null, false, true, ProfanitySeverity.HIGH, List.of("bad")));
        assertThatThrownBy(() -> service.send(5L, "u1", null, "very bad"))
                .hasFieldOrPropertyWithValue("code", TowerChatException.PROFANITY_REJECTED);
        ArgumentCaptor<TowerChatLog> log = ArgumentCaptor.forClass(TowerChatLog.class);
        verify(logWriter).write(log.capture());
        assertThat(log.getValue().getResult()).isEqualTo("REJECTED");
        assertThat(log.getValue().getRejectCode()).isEqualTo("150305");
        assertThat(log.getValue().getProcessedContent()).isNull();
        verify(historyStore, never()).push(any());

        when(moderationService.moderate("멍청이"))
                .thenReturn(
                        new ModerationResult(
                                "**", true, false, ProfanitySeverity.MEDIUM, List.of("멍청이")));
        when(presenceStore.find(5L, 7L)).thenReturn(Optional.empty());
        TowerChatMessage message = service.send(5L, "u1", null, "멍청이");
        assertThat(message.content()).isEqualTo("**");
        assertThat(message.nickname()).isNull();
        verify(guard).recordMediumHit(7L);
    }

    @Test
    @DisplayName("방문객: visitor_key 로 점유자 확인, 40자·URL/전화/메신저 차단(150308, 로그 남김), 5초 간격 카운터")
    void visitorRules() {
        when(occupantRepository.findByVisitorKey("vk")).thenReturn(Optional.of(visitor));
        assertThatThrownBy(() -> service.send(5L, null, "vk", "a".repeat(41)))
                .hasFieldOrPropertyWithValue("code", TowerChatException.CONTENT_INVALID);
        assertThatThrownBy(() -> service.send(5L, null, "vk", "010-1234-5678"))
                .hasFieldOrPropertyWithValue("code", TowerChatException.VISITOR_CONTENT_BLOCKED);
        ArgumentCaptor<TowerChatLog> log = ArgumentCaptor.forClass(TowerChatLog.class);
        verify(logWriter).write(log.capture());
        assertThat(log.getValue().getVisitorKey()).isEqualTo("vk");
        assertThat(log.getValue().getRejectCode()).isEqualTo("150308");
        assertThat(log.getValue().getMatchedWords()).isEqualTo("phone");

        when(guard.tryAcquireSend(8L, true)).thenReturn(true);
        when(moderationService.moderate("안녕")).thenReturn(ModerationResult.clean("안녕"));
        when(presenceStore.find(5L, 8L)).thenReturn(Optional.empty());
        TowerChatMessage message = service.send(5L, null, "vk", "안녕");
        assertThat(message.userId()).isNull();
        assertThat(message.visitorNo()).isEqualTo(4821);
        assertThat(message.masked()).isFalse();
    }

    @Test
    @DisplayName("리액션: 프리셋만, 응원 창 안·처음일 때만 addCheer, 본인 완료에는 응원 없음, 미션 중도 보낼 수 있다")
    void react() {
        loginOk();
        user.startMission(NOW);
        assertThatThrownBy(() -> service.react(5L, "u1", null, "🎉", null))
                .hasFieldOrPropertyWithValue("code", TowerChatException.INVALID_REACTION);
        when(guard.tryAcquireReaction(7L, false)).thenReturn(false);
        assertThatThrownBy(() -> service.react(5L, "u1", null, "👍", null))
                .hasFieldOrPropertyWithValue("code", TowerChatException.RATE_LIMITED);

        when(guard.tryAcquireReaction(7L, false)).thenReturn(true);
        when(cheerWindowStore.isOpen(5L, "u9")).thenReturn(true);
        when(cheerWindowStore.markCheered(5L, "u9", 7L)).thenReturn(true, false);
        when(seatService.addCheer("u9")).thenReturn(3);
        TowerReactionResponse cheered = service.react(5L, "u1", null, "👏", "u9");
        assertThat(cheered.cheer()).isTrue();
        assertThat(cheered.cheerCount()).isEqualTo(3);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publishToInstance(eq(5L), captor.capture());
        TowerInstanceEvent event = (TowerInstanceEvent) captor.getValue();
        assertThat(event.type()).isEqualTo("REACTION");
        assertThat(event.emoji()).isEqualTo("👏");
        assertThat(event.targetUserId()).isEqualTo("u9");
        assertThat(event.cheer()).isTrue();

        TowerReactionResponse second = service.react(5L, "u1", null, "👏", "u9");
        assertThat(second.cheer()).isFalse();
        assertThat(second.cheerCount()).isNull();
        TowerReactionResponse self = service.react(5L, "u1", null, "👏", "u1");
        assertThat(self.cheer()).isFalse();
        when(cheerWindowStore.isOpen(5L, "u8")).thenReturn(true);
        when(cheerWindowStore.markCheered(5L, "u8", 7L)).thenReturn(true);
        when(seatService.addCheer("u8")).thenReturn(-1);
        assertThat(service.react(5L, "u1", null, "👏", "u8").cheer()).isFalse();
        when(guard.isMuted(7L)).thenReturn(true);
        assertThatThrownBy(() -> service.react(5L, "u1", null, "👍", null))
                .hasFieldOrPropertyWithValue("code", TowerChatException.MUTED);
    }

    @Test
    @DisplayName("이력: 점유자만, 로그인 요청자는 차단한 유저 메시지를 뺀다, 프리셋 조회")
    void history() {
        loginOk();
        when(historyStore.recent(5L))
                .thenReturn(
                        List.of(
                                new TowerChatMessage(
                                        "a", 5L, 1L, "blocked", null, "b", "x", false, NOW),
                                new TowerChatMessage(
                                        "b", 5L, 2L, null, 1234, null, "y", false, NOW),
                                new TowerChatMessage(
                                        "c", 5L, 3L, "ok", null, "o", "z", false, NOW)));
        when(userQueryFacade.getBlockedUserIds("u1")).thenReturn(List.of("blocked"));
        TowerChatHistoryResponse res = service.history(5L, "u1", null);
        assertThat(res.messages()).extracting(TowerChatMessage::id).containsExactly("b", "c");

        when(userQueryFacade.getBlockedUserIds("u1")).thenThrow(new RuntimeException("down"));
        assertThat(service.history(5L, "u1", null).messages()).hasSize(3);
        when(occupantRepository.findByVisitorKey("vk")).thenReturn(Optional.of(visitor));
        assertThat(service.history(5L, null, "vk").messages()).hasSize(3);
        assertThat(service.presets().emojis()).contains("👍", "❤️").hasSize(6);
        assertThatThrownBy(() -> service.history(5L, null, null))
                .hasFieldOrPropertyWithValue("code", TowerChatException.NOT_IN_INSTANCE);
    }

    @Test
    @DisplayName("가드가 비면 Redis 장애로 보고 통과(제한 없이)")
    void guardFailOpen() {
        loginOk();
        when(guard.tryAcquireSend(anyLong(), eq(false))).thenReturn(true);
        when(moderationService.moderate(anyString())).thenReturn(ModerationResult.clean("ok"));
        when(presenceStore.find(5L, 7L)).thenReturn(Optional.empty());
        assertThat(service.send(5L, "u1", null, "ok").content()).isEqualTo("ok");
    }
}
