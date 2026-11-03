package io.pinkspider.leveluptogethermvp.missionservice.application;

import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-539: daily_mission_instance 다음 인스턴스 생성을 호출자와 **분리된 트랜잭션**에서 수행한다.
 *
 * <p>{@code CreateNextPinnedInstanceStep} 은 REQUIRES_NEW 스텝이지만 sequence 충돌(uk
 * participant_id+instance_date+ sequence_number) 을 **같은 트랜잭션 안에서** catch 하고 재시도했다 — 첫 flush 실패로 세션이
 * 오염돼 재시도 flush 도 실패하고 커밋 시 UnexpectedRollbackException 이 나는 구조였다. insert 를 한 단계 더 안쪽 REQUIRES_NEW
 * 로 분리해 스텝 트랜잭션을 깨끗하게 유지한다. 참여자는 id 로만 받아 내부 트랜잭션에서 로드한다 (saga 컨텍스트의 엔티티는 다른 영속성 컨텍스트의 것).
 *
 * <p>중복 예외는 삼키지 않고 호출자에게 던진다. 호출자(스텝)는 sequence 를 재조회해 재시도한다.
 */
@Service
@RequiredArgsConstructor
public class DailyMissionInstanceCreator {

    private final DailyMissionInstanceRepository instanceRepository;
    private final MissionParticipantRepository participantRepository;

    /**
     * 다음 인스턴스를 별도 트랜잭션에서 생성하고 id 를 돌려준다.
     *
     * @throws org.springframework.dao.DataIntegrityViolationException sequence 충돌(동시 요청이 같은 번호를 선점)
     */
    @Transactional(
            transactionManager = "missionTransactionManager",
            propagation = Propagation.REQUIRES_NEW)
    public Long create(Long participantId, LocalDate instanceDate, int sequenceNumber) {
        MissionParticipant participant =
                participantRepository
                        .findById(participantId)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "MissionParticipant not found: id="
                                                        + participantId));
        DailyMissionInstance saved =
                instanceRepository.saveAndFlush(
                        DailyMissionInstance.createFrom(participant, instanceDate, sequenceNumber));
        return saved.getId();
    }
}
