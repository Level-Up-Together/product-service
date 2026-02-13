package io.pinkspider.leveluptogethermvp.gamificationservice.event.listener;

import io.pinkspider.global.event.UserSignedUpEvent;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application.TitleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 회원가입 이벤트 수신하여 기본 칭호를 부여.
 * Oauth2Service/TestLoginService(user) → TitleService(gamification) 순환 의존을 제거하기 위해
 * 이벤트 리스너로 분리.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class UserSignedUpEventListener {

    private final TitleService titleService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleUserSignedUp(UserSignedUpEvent event) {
        try {
            titleService.grantAndEquipDefaultTitles(event.userId());
            log.info("기본 칭호 부여 완료: userId={}", event.userId());
        } catch (Exception e) {
            log.error("기본 칭호 부여 실패: userId={}", event.userId(), e);
        }
    }
}
