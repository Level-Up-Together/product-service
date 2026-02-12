package io.pinkspider.leveluptogethermvp.chatservice.event.listener;

import static io.pinkspider.global.config.AsyncConfig.EVENT_EXECUTOR;

import io.pinkspider.global.event.UserProfileChangedEvent;
import io.pinkspider.leveluptogethermvp.chatservice.infrastructure.GuildChatMessageRepository;
import io.pinkspider.leveluptogethermvp.chatservice.infrastructure.GuildChatParticipantRepository;
import io.pinkspider.leveluptogethermvp.chatservice.infrastructure.GuildDirectMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 사용자 프로필 변경 시 Chat DB 스냅샷 동기화
 * MSA 전환 시 Kafka Consumer로 대체 예정
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ChatProfileSnapshotEventListener {

    private final GuildChatMessageRepository guildChatMessageRepository;
    private final GuildChatParticipantRepository guildChatParticipantRepository;
    private final GuildDirectMessageRepository guildDirectMessageRepository;

    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserProfileChanged(UserProfileChangedEvent event) {
        try {
            int chatCount = guildChatMessageRepository.updateSenderNicknameByUserId(event.userId(), event.nickname());
            int dmCount = guildDirectMessageRepository.updateSenderNicknameByUserId(event.userId(), event.nickname());
            int participantCount = guildChatParticipantRepository.updateUserNicknameByUserId(event.userId(), event.nickname());
            log.info("Chat 스냅샷 동기화: userId={}, chats={}, dms={}, participants={}",
                event.userId(), chatCount, dmCount, participantCount);
        } catch (Exception e) {
            log.error("ChatProfileSync 실패: {}", e.getMessage(), e);
        }
    }
}
