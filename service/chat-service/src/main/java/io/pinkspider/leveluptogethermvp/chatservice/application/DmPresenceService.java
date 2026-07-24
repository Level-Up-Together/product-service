package io.pinkspider.leveluptogethermvp.chatservice.application;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * DM 대화방 presence 관리 (LUT-263)
 *
 * <p>유저가 현재 보고 있는 대화방을 Redis에 TTL과 함께 기록한다. 클라이언트가 방 입장/하트비트(25초)마다
 * 갱신하고 이탈 시 해제한다. 발송 측은 수신자가 해당 대화방을 보고 있으면 푸시 알림을 생략한다.
 *
 * <p>presence는 본인 알림 억제에만 사용되므로 대화방 접근 권한 검증은 하지 않는다 (타 대화방 ID를 넣어도
 * 본인이 그 대화의 참여자가 아니면 억제 조건에 걸리지 않아 무해).
 *
 * <p>Redis 장애 시 fail-open: 조회 실패는 "보고 있지 않음"으로 간주해 푸시가 정상 발송된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DmPresenceService {

    static final String KEY_PREFIX = "chat:dm:viewing:";

    /** 클라이언트 하트비트(25초)의 2배 + 여유. 이 시간 안에 갱신이 없으면 이탈로 간주. */
    static final Duration VIEWING_TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate stringRedisTemplate;

    /** 유저가 대화방을 보고 있음을 기록 (TTL 갱신 포함) */
    public void markViewing(String userId, Long conversationId) {
        if (userId == null || conversationId == null) {
            return;
        }
        try {
            stringRedisTemplate
                    .opsForValue()
                    .set(KEY_PREFIX + userId, conversationId.toString(), VIEWING_TTL);
        } catch (Exception e) {
            log.warn("DM presence 기록 실패: userId={}, error={}", userId, e.getMessage());
        }
    }

    /** 유저가 대화방에서 이탈했음을 기록. 다른 방으로 이미 이동한 경우는 건드리지 않는다. */
    public void clearViewing(String userId, Long conversationId) {
        if (userId == null) {
            return;
        }
        try {
            String key = KEY_PREFIX + userId;
            if (conversationId == null) {
                stringRedisTemplate.delete(key);
                return;
            }
            String current = stringRedisTemplate.opsForValue().get(key);
            if (conversationId.toString().equals(current)) {
                stringRedisTemplate.delete(key);
            }
        } catch (Exception e) {
            log.warn("DM presence 해제 실패: userId={}, error={}", userId, e.getMessage());
        }
    }

    /** WebSocket 연결 종료 등 대화방 무관 전체 해제 */
    public void clearViewing(String userId) {
        clearViewing(userId, null);
    }

    /** 유저가 해당 대화방을 보고 있는지 확인 (Redis 장애 시 false) */
    public boolean isViewing(String userId, Long conversationId) {
        if (userId == null || conversationId == null) {
            return false;
        }
        try {
            String current = stringRedisTemplate.opsForValue().get(KEY_PREFIX + userId);
            return conversationId.toString().equals(current);
        } catch (Exception e) {
            log.warn("DM presence 조회 실패: userId={}, error={}", userId, e.getMessage());
            return false;
        }
    }
}
