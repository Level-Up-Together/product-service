package io.pinkspider.leveluptogethermvp.missionservice.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionCreateRequest;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionIdempotencyKey;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionIdempotencyKeyRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-577: 멱등 키의 트랜잭션 경계를 담당한다. 선점(claim)·해제(release)는 호출자와 분리된 REQUIRES_NEW — 유니크 충돌이 호출자의 영속성
 * 컨텍스트를 오염시키지 않게 하고(LUT-538 패턴), 선점 행이 즉시 커밋돼 다른 인스턴스의 동시 요청도 충돌을 본다. 미션 생성과 키 완료는 **한
 * 트랜잭션**(createAndComplete)이라 생성이 실패하면 완료 표시도 함께 롤백되고, 호출자가 선점 행을 release 한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MissionIdempotencyTxService {

    private final MissionIdempotencyKeyRepository repository;
    private final MissionService missionService;
    private final ObjectMapper objectMapper;

    /**
     * 키를 선점한다 (PROCESSING 행 insert + 즉시 커밋).
     *
     * @throws org.springframework.dao.DataIntegrityViolationException 같은 유저의 같은 키가 이미 있음
     */
    @Transactional(
            transactionManager = "missionTransactionManager",
            propagation = Propagation.REQUIRES_NEW)
    public Long claim(String userId, String idempotencyKey) {
        return repository.saveAndFlush(MissionIdempotencyKey.claim(userId, idempotencyKey)).getId();
    }

    /** 선점 행 제거 — 생성 실패·stale·만료 시 같은 키를 다시 새 요청으로 받기 위해 */
    @Transactional(
            transactionManager = "missionTransactionManager",
            propagation = Propagation.REQUIRES_NEW)
    public void release(Long keyId) {
        repository.deleteById(keyId);
    }

    @Transactional(transactionManager = "missionTransactionManager", readOnly = true)
    public Optional<MissionIdempotencyKey> find(String userId, String idempotencyKey) {
        return repository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
    }

    /** 미션 생성 + 키 완료(응답 본문 보관)를 한 트랜잭션에서 수행한다. */
    @Transactional(transactionManager = "missionTransactionManager")
    public MissionResponse createAndComplete(
            Long keyId, String userId, MissionCreateRequest request) {
        MissionResponse response = missionService.createMission(userId, request);
        MissionIdempotencyKey row =
                repository
                        .findById(keyId)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "mission idempotency key row not found: id="
                                                        + keyId));
        row.complete(response.getId(), serialize(response));
        return response;
    }

    @Transactional(transactionManager = "missionTransactionManager")
    public int deleteExpired(LocalDateTime threshold) {
        return repository.deleteByCreatedAtBefore(threshold);
    }

    /** 직렬화 실패는 생성 자체를 막지 않는다 — 본문 없이 완료되면 재요청 시 mission_id 로 재조회해 응답한다. */
    private String serialize(MissionResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            log.warn("미션 생성 멱등 응답 직렬화 실패 — mission_id 로 재조회 폴백: missionId={}", response.getId(), e);
            return null;
        }
    }
}
