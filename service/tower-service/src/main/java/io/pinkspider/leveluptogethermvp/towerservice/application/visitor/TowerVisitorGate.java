package io.pinkspider.leveluptogethermvp.towerservice.application.visitor;

import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerChatException;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerVisitorException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerVisitorSanctionType;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * LUT-705: 방문객이 들어오고 말할 때 지나는 문 — 세션(키·이름 번호) 발급·유지, 기기·IP 제재, IP 빈도 제한을 한곳에서 판정한다. 입장 ({@code
 * TowerEntryService})과 채팅·리액션({@code TowerChatService})이 쓴다.
 */
@Component
@RequiredArgsConstructor
public class TowerVisitorGate {
    private final TowerVisitorSessionStore sessionStore;
    private final TowerVisitorSanctionService sanctionService;
    private final TowerVisitorRateLimiter rateLimiter;

    /**
     * 방문객 입장 판정 — 제재(403 150501) → 세션 확인(모르는 키는 새 세션) → IP 빈도(429 150503). 통과하면 세션을 저장(유지 시간 갱신)해
     * 돌려준다.
     */
    public TowerVisitorSession admit(TowerVisitorContext context) {
        if (sanctionService.isBanned(
                TowerVisitorSanctionType.ENTRY, context.ip(), context.deviceId())) {
            throw TowerVisitorException.entryBanned();
        }
        TowerVisitorSession session =
                sessionStore
                        .find(context.visitorKey())
                        .map(found -> found.refreshedBy(context))
                        .orElseGet(() -> newSession(context));
        if (sanctionService.isBanned(
                TowerVisitorSanctionType.ENTRY, session.ip(), session.deviceId())) {
            throw TowerVisitorException.entryBanned();
        }
        if (!rateLimiter.allowEnter(context.ip(), session.visitorKey())) {
            throw TowerVisitorException.rateLimited();
        }
        sessionStore.save(session);
        return session;
    }

    /**
     * 방문객 채팅·리액션 판정 — 세션·지금 요청의 IP·기기가 채팅 금지면 403 150502, 같은 IP 의 1분 전송 수를 넘으면 429 150302(전송 간격과 같은
     * 안내).
     */
    public void checkChat(String visitorKey, @Nullable String requestIp) {
        TowerVisitorSession session = sessionStore.find(visitorKey).orElse(null);
        String sessionIp = session != null ? session.ip() : null;
        String deviceId = session != null ? session.deviceId() : null;
        if (sanctionService.isBanned(TowerVisitorSanctionType.CHAT, sessionIp, deviceId)
                || sanctionService.isBanned(TowerVisitorSanctionType.CHAT, requestIp, null)) {
            throw TowerVisitorException.chatBanned();
        }
        if (!rateLimiter.allowChat(requestIp != null ? requestIp : sessionIp)) {
            throw TowerChatException.rateLimited();
        }
    }

    private static TowerVisitorSession newSession(TowerVisitorContext context) {
        return new TowerVisitorSession(
                UUID.randomUUID().toString(),
                ThreadLocalRandom.current().nextInt(1000, 10000),
                context.ip(),
                context.deviceId(),
                context.userAgent(),
                System.currentTimeMillis());
    }
}
