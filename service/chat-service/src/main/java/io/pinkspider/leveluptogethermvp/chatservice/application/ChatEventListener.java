package io.pinkspider.leveluptogethermvp.chatservice.application;

import io.pinkspider.global.event.GuildMemberJoinedChatNotifyEvent;
import io.pinkspider.global.event.GuildMemberKickedChatNotifyEvent;
import io.pinkspider.global.event.GuildMemberLeftChatNotifyEvent;
import io.pinkspider.global.event.GuildMemberRemovedEvent;
import io.pinkspider.global.event.UserWithdrawnEvent;
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
    private final GuildDirectMessageService guildDirectMessageService;

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

    /** LUT-287: 길드 탈퇴/추방 시 해당 길드에서의 DM 대화방을 목록에서 숨긴다 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleGuildMemberRemoved(GuildMemberRemovedEvent event) {
        int count = guildDirectMessageService.deactivateConversations(event.guildId(), event.userId());
        log.info("길드 탈퇴/추방 DM 대화방 비활성화: guildId={}, userId={}, count={}",
            event.guildId(), event.userId(), count);
    }

    /** LUT-287: 회원 탈퇴 시 전 길드의 DM 대화방을 목록에서 숨긴다 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleUserWithdrawn(UserWithdrawnEvent event) {
        int count = guildDirectMessageService.deactivateConversationsForUser(event.userId());
        log.info("회원 탈퇴 DM 대화방 비활성화: userId={}, count={}", event.userId(), count);
    }
}
