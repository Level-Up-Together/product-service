package io.pinkspider.leveluptogethermvp.missionservice.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.leveluptogethermvp.missionservice.api.exception.MissionIdempotencyException;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionCreateRequest;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.RecommendedMissionStartResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionIdempotencyKey;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * LUT-577: {@code POST /api/v1/missions} 의 Idempotency-Key 처리. 네트워크가 나빠 요청은 도착했는데 응답만 유실된 뒤 유저가 다시
 * 누르면, 클라이언트 가드(LUT-293)는 막지 못하고 서버만 같은 시도를 알아볼 수 있다.
 *
 * <ul>
 *   <li>헤더 없음 → 기존처럼 매번 생성 (구버전 앱 호환)
 *   <li>UUID 아님 → 400 (050401)
 *   <li>처음 보는 키 → 선점 후 생성, 응답 본문 보관
 *   <li>같은 유저·같은 키·완료 → 저장된 응답 그대로 (같은 mission_id). 본문이 없으면 mission_id 로 재조회
 *   <li>같은 키·처리 중 → 409 (050402). 선점 후 {@value #STALE_PROCESSING_MINUTES}분이 지나도 완료되지 않은 행(JVM 중단
 *       등)은 stale 로 보고 새 요청으로 취급
 *   <li>키 보관 24시간 — 지난 행은 배치가 지우고, 지우기 전에 같은 키가 오면 그 자리에서 새 요청으로 취급
 *   <li>다른 유저의 같은 키 → 스코프가 달라 충돌 없음 (유니크 = user_id + key)
 * </ul>
 *
 * 이 클래스 자체는 트랜잭션을 열지 않는다 — 경계는 {@link MissionIdempotencyTxService} 가 가진다.
 *
 * <p>LUT-782: 추천 미션 원탭 시작({@code POST /missions/recommended/{id}/start})도 같은 키 표·같은 규칙을 쓴다(LUT-578
 * 규약). 키는 유저당 유일하므로 두 API 가 표를 함께 써도 충돌하지 않는다 — 클라이언트는 누를 때마다 새 키를 만든다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MissionIdempotencyService {

    public static final String HEADER_NAME = "Idempotency-Key";

    static final Duration RETENTION = Duration.ofHours(24);
    static final long STALE_PROCESSING_MINUTES = 5;
    static final Duration STALE_PROCESSING = Duration.ofMinutes(STALE_PROCESSING_MINUTES);

    /** 선점 재시도 상한 — 만료·stale 행을 지우고 다시 선점하는 한 번의 재시도만 허용 */
    private static final int MAX_CLAIM_ATTEMPTS = 2;

    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private final MissionService missionService;
    private final RecommendedMissionService recommendedMissionService;
    private final MissionIdempotencyTxService txService;
    private final ObjectMapper objectMapper;

    public MissionResponse createMission(
            String userId, String idempotencyKey, MissionCreateRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return missionService.createMission(userId, request);
        }
        return runOnce(
                userId,
                idempotencyKey,
                keyId -> txService.createAndComplete(keyId, userId, request),
                this::replay);
    }

    /** LUT-782: 추천 미션 원탭 시작 — 헤더 없으면 매번 실행, 있으면 같은 키 재요청에 같은 미션을 돌려준다 */
    public RecommendedMissionStartResponse startRecommended(
            String userId, String idempotencyKey, Long recommendedId, String locale) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return recommendedMissionService.start(userId, recommendedId, locale);
        }
        return runOnce(
                userId,
                idempotencyKey,
                keyId ->
                        txService.startRecommendedAndComplete(keyId, userId, recommendedId, locale),
                this::replayRecommendedStart);
    }

    /** 키 선점 → 실행(실행과 키 완료는 한 트랜잭션) → 실패하면 선점 해제. 같은 키가 이미 있으면 {@code replay} 로 저장된 결과를 돌려준다. */
    private <T> T runOnce(
            String userId,
            String idempotencyKey,
            LongFunction<T> runWithClaim,
            Function<MissionIdempotencyKey, T> replay) {
        String key = normalize(idempotencyKey);

        for (int attempt = 1; attempt <= MAX_CLAIM_ATTEMPTS; attempt++) {
            Long keyId;
            try {
                keyId = txService.claim(userId, key);
            } catch (DataIntegrityViolationException e) {
                Optional<T> replayed = resolveExisting(userId, key, replay);
                if (replayed.isPresent()) {
                    return replayed.get();
                }
                // 만료·stale·삭제된 행 — 다시 선점
                continue;
            }
            return runWithClaim(keyId, runWithClaim);
        }
        // 재선점까지 연속 충돌 — 다른 요청이 방금 선점한 것이므로 처리 중으로 응답
        throw MissionIdempotencyException.inProgress();
    }

    /** 보관 기간이 지난 키 삭제 (스케줄러) */
    public int cleanupExpired() {
        int deleted = txService.deleteExpired(LocalDateTime.now().minus(RETENTION));
        if (deleted > 0) {
            log.info("미션 생성 멱등 키 만료 정리: {}건", deleted);
        }
        return deleted;
    }

    private static String normalize(String raw) {
        String key = raw.trim().toLowerCase(Locale.ROOT);
        if (!UUID_PATTERN.matcher(key).matches()) {
            throw MissionIdempotencyException.invalidKey();
        }
        return key;
    }

    /**
     * 유니크 충돌 뒤 기존 행을 판정한다.
     *
     * @return 재생할 응답. 비어 있으면 행을 지웠거나 이미 없어 호출자가 다시 선점해야 한다
     * @throws MissionIdempotencyException 처리 중(409)
     */
    private <T> Optional<T> resolveExisting(
            String userId, String key, Function<MissionIdempotencyKey, T> replay) {
        MissionIdempotencyKey existing = txService.find(userId, key).orElse(null);
        if (existing == null) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.now();
        if (existing.isOlderThan(RETENTION, now)) {
            log.info(
                    "미션 생성 멱등 키 만료 — 새 요청으로 취급: userId={}, key={}, createdAt={}",
                    userId,
                    key,
                    existing.getCreatedAt());
            txService.release(existing.getId());
            return Optional.empty();
        }
        if (existing.isCompleted()) {
            log.info(
                    "미션 생성 멱등 재생: userId={}, key={}, missionId={}",
                    userId,
                    key,
                    existing.getMissionId());
            return Optional.of(replay.apply(existing));
        }
        if (existing.isOlderThan(STALE_PROCESSING, now)) {
            log.warn(
                    "미션 생성 멱등 키가 {}분 넘게 PROCESSING — stale 로 보고 재선점: userId={}, key={}, id={}",
                    STALE_PROCESSING_MINUTES,
                    userId,
                    key,
                    existing.getId());
            txService.release(existing.getId());
            return Optional.empty();
        }
        throw MissionIdempotencyException.inProgress();
    }

    private MissionResponse replay(MissionIdempotencyKey existing) {
        if (existing.getResponseBody() != null) {
            try {
                return objectMapper.readValue(existing.getResponseBody(), MissionResponse.class);
            } catch (JsonProcessingException e) {
                log.warn("미션 생성 멱등 응답 역직렬화 실패 — mission_id 로 재조회: id={}", existing.getId(), e);
            }
        }
        if (existing.getMissionId() != null) {
            return missionService.getMission(existing.getMissionId(), null);
        }
        throw new IllegalStateException(
                "mission idempotency key completed without mission: id=" + existing.getId());
    }

    /** LUT-782: 저장된 원탭 시작 결과. 본문을 못 읽으면 미션만 다시 읽어 돌려준다(실행은 null — 클라이언트는 mission_id 로 이동) */
    private RecommendedMissionStartResponse replayRecommendedStart(MissionIdempotencyKey existing) {
        if (existing.getResponseBody() != null) {
            try {
                return objectMapper.readValue(
                        existing.getResponseBody(), RecommendedMissionStartResponse.class);
            } catch (JsonProcessingException e) {
                log.warn("추천 미션 시작 멱등 응답 역직렬화 실패 — mission_id 로 재조회: id={}", existing.getId(), e);
            }
        }
        if (existing.getMissionId() != null) {
            return new RecommendedMissionStartResponse(
                    missionService.getMission(existing.getMissionId(), null), null);
        }
        throw new IllegalStateException(
                "mission idempotency key completed without mission: id=" + existing.getId());
    }

    private <T> T runWithClaim(Long keyId, LongFunction<T> action) {
        try {
            return action.apply(keyId);
        } catch (RuntimeException e) {
            // 생성 실패 — 선점 행을 지워 같은 키 재시도가 다시 생성을 시도할 수 있게 한다
            try {
                txService.release(keyId);
            } catch (RuntimeException releaseError) {
                log.error("미션 생성 멱등 키 해제 실패: keyId={}", keyId, releaseError);
            }
            throw e;
        }
    }
}
