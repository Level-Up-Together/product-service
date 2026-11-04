package io.pinkspider.leveluptogethermvp.towerservice.application.chat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * LUT-633: 전송 제한(Redis, prod 2대 공유). 점유자 단위로 — 로그인 1초 1건·방문객 5초 1건, 같은 내용 연속 차단, MEDIUM 반복 시 잠시 제한.
 * Redis 장애 시에는 제한 없이 통과시킨다(채팅이 멈추는 것보다 낫다).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TowerChatGuard {
    static final String SEND_PREFIX = "tower:chat:rate:";
    static final String REACTION_PREFIX = "tower:chat:rrate:";
    static final String LAST_PREFIX = "tower:chat:last:";
    static final String MEDIUM_PREFIX = "tower:chat:medium:";
    static final String MUTE_PREFIX = "tower:chat:mute:";

    private final StringRedisTemplate redis;
    private final TowerChatProperties properties;

    /** 전송 간격 슬롯을 잡는다. 못 잡으면 false(150302) */
    public boolean tryAcquireSend(Long occupantId, boolean visitor) {
        return acquire(SEND_PREFIX + occupantId, visitor);
    }

    /** 리액션은 채팅과 같은 간격, 다른 카운터 */
    public boolean tryAcquireReaction(Long occupantId, boolean visitor) {
        return acquire(REACTION_PREFIX + occupantId, visitor);
    }

    private boolean acquire(String key, boolean visitor) {
        long interval =
                visitor ? properties.getVisitorIntervalMs() : properties.getUserIntervalMs();
        try {
            Boolean ok = redis.opsForValue().setIfAbsent(key, "1", Duration.ofMillis(interval));
            return !Boolean.FALSE.equals(ok);
        } catch (RuntimeException e) {
            log.warn("[tower-chat] 전송 제한 Redis 오류 — 통과: {}", e.getMessage());
            return true;
        }
    }

    /** 직전 메시지와 같은 내용인가 */
    public boolean isDuplicate(Long occupantId, String content) {
        try {
            String last = redis.opsForValue().get(LAST_PREFIX + occupantId);
            return last != null && last.equals(digest(content));
        } catch (RuntimeException e) {
            return false;
        }
    }

    public void rememberContent(Long occupantId, String content) {
        try {
            redis.opsForValue()
                    .set(
                            LAST_PREFIX + occupantId,
                            digest(content),
                            Duration.ofSeconds(properties.getDuplicateWindowSeconds()));
        } catch (RuntimeException e) {
            log.warn("[tower-chat] 직전 내용 기록 실패: {}", e.getMessage());
        }
    }

    public boolean isMuted(Long occupantId) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(MUTE_PREFIX + occupantId));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** MEDIUM 비속어 1회 적립. 창 안 횟수가 상한에 닿으면 제한을 걸고 true */
    public boolean recordMediumHit(Long occupantId) {
        try {
            String key = MEDIUM_PREFIX + occupantId;
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, Duration.ofSeconds(properties.getMediumWindowSeconds()));
            }
            if (count != null && count >= properties.getMediumRepeatCount()) {
                redis.opsForValue()
                        .set(
                                MUTE_PREFIX + occupantId,
                                "1",
                                Duration.ofSeconds(properties.getMuteSeconds()));
                redis.delete(key);
                log.info(
                        "[tower-chat] MEDIUM 반복으로 채팅 제한: occupantId={} {}s",
                        occupantId,
                        properties.getMuteSeconds());
                return true;
            }
            return false;
        } catch (RuntimeException e) {
            log.warn("[tower-chat] MEDIUM 적립 실패: {}", e.getMessage());
            return false;
        }
    }

    static String digest(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(md.digest(content.trim().getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            return content.trim();
        }
    }
}
