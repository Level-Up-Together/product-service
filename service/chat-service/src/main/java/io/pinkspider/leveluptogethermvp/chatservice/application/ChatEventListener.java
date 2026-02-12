package io.pinkspider.leveluptogethermvp.chatservice.application;

import io.pinkspider.global.event.GuildMemberJoinedChatNotifyEvent;
import io.pinkspider.global.event.GuildMemberKickedChatNotifyEvent;
import io.pinkspider.global.event.GuildMemberLeftChatNotifyEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
@RequiredArgsConstructor
public class ChatEventListener {

    private final GuildChatService guildChatService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleMemberJoined(GuildMemberJoinedChatNotifyEvent event) {
        log.debug("채팅 알림 이벤트 수신 - 멤버 가입: guildId={}, nickname={}", event.guildId(), event.memberNickname());
        guildChatService.notifyMemberJoin(event.guildId(), event.memberNickname());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleMemberLeft(GuildMemberLeftChatNotifyEvent event) {
        log.debug("채팅 알림 이벤트 수신 - 멤버 탈퇴: guildId={}, nickname={}", event.guildId(), event.memberNickname());
        guildChatService.notifyMemberLeave(event.guildId(), event.memberNickname());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleMemberKicked(GuildMemberKickedChatNotifyEvent event) {
        log.debug("채팅 알림 이벤트 수신 - 멤버 추방: guildId={}, nickname={}", event.guildId(), event.memberNickname());
        guildChatService.notifyMemberKick(event.guildId(), event.memberNickname());
    }
}
