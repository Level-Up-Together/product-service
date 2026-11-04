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
 * LUT-633: 전송 제한(Redis, prod 2대 공유). 보낸 사람 단위로 — 로그인 1초 1건·방문객 5초 1건, 같은 내용 연속 차단, MEDIUM 반복 시 잠시
 * 제한. LUT-705: 단위({@code subject})는 점유자가 아니라 유저({@code u:{userId}})·방문객 세션({@code v:{visitorKey}})
 * — 층을 옮기거나 다시 들어와 점유자 ID 가 바뀌어도 간격·제한이 이어진다. Redis 장애 시에는 제한 없이 통과시킨다(채팅이 멈추는 것보다 낫다).
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
    public boolean tryAcquireSend(String subject, boolean visitor) {
        return acquire(SEND_PREFIX + subject, visitor);
    }

    /** 리액션은 채팅과 같은 간격, 다른 카운터 */
    public boolean tryAcquireReaction(String subject, boolean visitor) {
        return acquire(REACTION_PREFIX + subject, visitor);
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
    public boolean isDuplicate(String subject, String content) {
        try {
            String last = redis.opsForValue().get(LAST_PREFIX + subject);
            return last != null && last.equals(digest(content));
        } catch (RuntimeException e) {
            return false;
        }
    }

    public void rememberContent(String subject, String content) {
        try {
            redis.opsForValue()
                    .set(
                            LAST_PREFIX + subject,
                            digest(content),
                            Duration.ofSeconds(properties.getDuplicateWindowSeconds()));
        } catch (RuntimeException e) {
            log.warn("[tower-chat] 직전 내용 기록 실패: {}", e.getMessage());
        }
    }

    public boolean isMuted(String subject) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(MUTE_PREFIX + subject));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** MEDIUM 비속어 1회 적립. 창 안 횟수가 상한에 닿으면 제한을 걸고 true */
    public boolean recordMediumHit(String subject) {
        try {
            String key = MEDIUM_PREFIX + subject;
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, Duration.ofSeconds(properties.getMediumWindowSeconds()));
            }
            if (count != null && count >= properties.getMediumRepeatCount()) {
                redis.opsForValue()
                        .set(
                                MUTE_PREFIX + subject,
                                "1",
                                Duration.ofSeconds(properties.getMuteSeconds()));
                redis.delete(key);
                log.info(
                        "[tower-chat] MEDIUM 반복으로 채팅 제한: subject={} {}s",
                        subject,
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
