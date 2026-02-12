package io.pinkspider.leveluptogethermvp.guildservice.event.listener;

import static io.pinkspider.global.config.AsyncConfig.EVENT_EXECUTOR;

import io.pinkspider.global.event.UserProfileChangedEvent;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildPostCommentRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 사용자 프로필 변경 시 Guild DB 스냅샷 동기화
 * MSA 전환 시 Kafka Consumer로 대체 예정
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class GuildProfileSnapshotEventListener {

    private final GuildPostRepository guildPostRepository;
    private final GuildPostCommentRepository guildPostCommentRepository;

    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserProfileChanged(UserProfileChangedEvent event) {
        try {
            int postCount = guildPostRepository.updateAuthorNicknameByUserId(event.userId(), event.nickname());
            int postCommentCount = guildPostCommentRepository.updateAuthorNicknameByUserId(event.userId(), event.nickname());
            log.info("Guild 스냅샷 동기화: userId={}, posts={}, postComments={}",
                event.userId(), postCount, postCommentCount);
        } catch (Exception e) {
            log.error("GuildProfileSync 실패: {}", e.getMessage(), e);
        }
    }
}
