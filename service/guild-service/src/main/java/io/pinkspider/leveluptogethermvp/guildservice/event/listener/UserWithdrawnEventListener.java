package io.pinkspider.leveluptogethermvp.guildservice.event.listener;

import static io.pinkspider.global.config.AsyncConfig.EVENT_EXECUTOR;

import io.pinkspider.global.event.UserWithdrawnEvent;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 회원 탈퇴 이벤트 수신 - 탈퇴 회원의 길드 멤버십 정리 (LUT-287)
 *
 * <p>일반 멤버는 탈퇴 처리, 마스터는 승계 후 탈퇴(남은 멤버가 없으면 길드 해체).
 * 정리 과정에서 GuildMemberRemovedEvent가 발행되어 길드 미션 참여 정리와
 * DM 대화방 비활성화(chatservice)로 이어진다.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class UserWithdrawnEventListener {

    private final GuildMemberService guildMemberService;

    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleUserWithdrawn(UserWithdrawnEvent event) {
        log.info("회원 탈퇴 이벤트 수신 - 길드 멤버십 정리: userId={}", event.userId());
        guildMemberService.cleanupMembershipsForWithdrawnUser(event.userId());
    }
}
