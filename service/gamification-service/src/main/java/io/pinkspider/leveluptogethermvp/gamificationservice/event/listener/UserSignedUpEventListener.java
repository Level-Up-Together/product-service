package io.pinkspider.leveluptogethermvp.gamificationservice.event.listener;

import io.pinkspider.global.event.UserSignedUpEvent;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application.TitleService;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.application.UserItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 회원가입 이벤트 수신하여 기본 칭호를 부여.
 * Oauth2Service/TestLoginService(user) → TitleService(gamification) 순환 의존을 제거하기 위해
 * 이벤트 리스너로 분리.
 *
 * @Async: 비동기 실행하여 칭호 부여 실패가 회원가입 응답(JWT)에 영향을 주지 않도록 함 (QA-89)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class UserSignedUpEventListener {

    private final TitleService titleService;
    private final UserItemService userItemService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleUserSignedUp(UserSignedUpEvent event) {
        try {
            titleService.grantAndEquipDefaultTitles(event.userId());
            log.info("기본 칭호 부여 완료: userId={}", event.userId());
        } catch (Exception e) {
            log.error("기본 칭호 부여 실패: userId={}", event.userId(), e);
        }

        // LUT-296: 기본 아이템(ID:2) 지급 — 칭호 부여 실패와 독립적으로 수행
        try {
            userItemService.grantDefaultItems(event.userId());
            log.info("기본 아이템 지급 완료: userId={}", event.userId());
        } catch (Exception e) {
            log.error("기본 아이템 지급 실패: userId={}", event.userId(), e);
        }
    }
}
