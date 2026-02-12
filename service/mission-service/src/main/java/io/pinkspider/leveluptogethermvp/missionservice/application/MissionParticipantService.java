package io.pinkspider.leveluptogethermvp.missionservice.application;

import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionParticipantResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.global.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(transactionManager = "missionTransactionManager", readOnly = true)
public class MissionParticipantService {

    private final MissionRepository missionRepository;
    private final MissionParticipantRepository participantRepository;
    private final MissionExecutionService missionExecutionService;

    @Transactional(transactionManager = "missionTransactionManager")
    public MissionParticipantResponse joinMission(Long missionId, String userId) {
        Mission mission = findMissionById(missionId);

        validateMissionJoinable(mission, userId);

        ParticipantStatus initialStatus = mission.isPublic()
            ? ParticipantStatus.ACCEPTED
            : ParticipantStatus.PENDING;

        // 기존 참여 기록이 있는지 확인 (탈퇴/실패 상태 포함)
        MissionParticipant participant = participantRepository.findByMissionIdAndUserId(missionId, userId)
            .map(existing -> {
                // 탈퇴/실패 상태인 경우 재참여 처리
                existing.rejoin(initialStatus);
                log.info("미션 재참여: missionId={}, userId={}, status={}", missionId, userId, initialStatus);
                return existing;
            })
            .orElseGet(() -> {
                // 신규 참여
                MissionParticipant newParticipant = MissionParticipant.builder()
                    .mission(mission)
                    .userId(userId)
                    .status(initialStatus)
                    .progress(0)
                    .joinedAt(LocalDateTime.now())
                    .build();
                log.info("미션 참여 신청: missionId={}, userId={}, status={}", missionId, userId, initialStatus);
                return newParticipant;
            });

        MissionParticipant saved = participantRepository.save(participant);

        // 공개 미션은 바로 수락되므로 실행 스케줄 생성
        if (initialStatus == ParticipantStatus.ACCEPTED) {
            missionExecutionService.generateExecutionsForParticipant(saved);
        }

        return MissionParticipantResponse.from(saved);
    }

    /**
     * 미션 생성자를 자동으로 참여자로 등록 (상태 체크 없이)
     */
    @Transactional(transactionManager = "missionTransactionManager")
    public void addCreatorAsParticipant(Mission mission, String creatorId) {
        MissionParticipant participant = MissionParticipant.builder()
            .mission(mission)
            .userId(creatorId)
            .status(ParticipantStatus.ACCEPTED)
            .progress(0)
            .joinedAt(LocalDateTime.now())
            .build();

        MissionParticipant saved = participantRepository.save(participant);
        log.info("미션 생성자 참여 등록: missionId={}, userId={}", mission.getId(), creatorId);

        // 실행 스케줄 생성
        missionExecutionService.generateExecutionsForParticipant(saved);
    }

    /**
     * 길드원을 길드 미션 참여자로 자동 등록
     * (이미 참여 중인 경우 건너뜀)
     */
    @Transactional(transactionManager = "missionTransactionManager")
    public void addGuildMemberAsParticipant(Mission mission, String userId) {
        // 이미 참여 중인지 확인
        if (participantRepository.existsActiveParticipation(mission.getId(), userId)) {
            log.debug("이미 미션에 참여 중인 길드원: missionId={}, userId={}", mission.getId(), userId);
            return;
        }

        MissionParticipant participant = MissionParticipant.builder()
            .mission(mission)
            .userId(userId)
            .status(ParticipantStatus.ACCEPTED)
            .progress(0)
            .joinedAt(LocalDateTime.now())
            .build();

        MissionParticipant saved = participantRepository.save(participant);
        log.info("길드원 미션 참여 등록: missionId={}, userId={}", mission.getId(), userId);

        // 실행 스케줄 생성
        missionExecutionService.generateExecutionsForParticipant(saved);
    }

    @Transactional(transactionManager = "missionTransactionManager")
    public MissionParticipantResponse acceptParticipant(Long missionId, Long participantId, String ownerId) {
        Mission mission = findMissionById(missionId);
        validateMissionOwner(mission, ownerId);

        MissionParticipant participant = findParticipantById(participantId);
        validateParticipantBelongsToMission(participant, missionId);

        participant.accept();
        log.info("참여자 승인: missionId={}, participantId={}", missionId, participantId);

        // 참여자 승인 시 실행 스케줄 생성
        missionExecutionService.generateExecutionsForParticipant(participant);

        return MissionParticipantResponse.from(participant);
    }

    @Transactional(transactionManager = "missionTransactionManager")
    public MissionParticipantResponse startProgress(Long missionId, String userId) {
        MissionParticipant participant = findParticipantByMissionAndUser(missionId, userId);

        participant.startProgress();
        log.info("미션 진행 시작: missionId={}, userId={}", missionId, userId);

        return MissionParticipantResponse.from(participant);
    }

    @Transactional(transactionManager = "missionTransactionManager")
    public MissionParticipantResponse updateProgress(Long missionId, String userId, int progress) {
        MissionParticipant participant = findParticipantByMissionAndUser(missionId, userId);

        participant.updateProgress(progress);
        log.info("진행률 업데이트: missionId={}, userId={}, progress={}", missionId, userId, progress);

        return MissionParticipantResponse.from(participant);
    }

    @Transactional(transactionManager = "missionTransactionManager")
    public MissionParticipantResponse completeParticipant(Long missionId, String userId) {
        MissionParticipant participant = findParticipantByMissionAndUser(missionId, userId);

        participant.complete();
        log.info("미션 완료: missionId={}, userId={}", missionId, userId);

        return MissionParticipantResponse.from(participant);
    }

    @Transactional(transactionManager = "missionTransactionManager")
    public MissionParticipantResponse withdrawFromMission(Long missionId, String userId) {
        MissionParticipant participant = findParticipantByMissionAndUser(missionId, userId);

        participant.withdraw();
        log.info("미션 철회: missionId={}, userId={}", missionId, userId);

        return MissionParticipantResponse.from(participant);
    }

    public List<MissionParticipantResponse> getMissionParticipants(Long missionId) {
        return participantRepository.findByMissionId(missionId).stream()
            .map(MissionParticipantResponse::from)
            .toList();
    }

    public List<MissionParticipantResponse> getMyParticipations(String userId) {
        return participantRepository.findByUserIdWithMission(userId).stream()
            .map(MissionParticipantResponse::from)
            .toList();
    }

    public MissionParticipantResponse getMyParticipation(Long missionId, String userId) {
        MissionParticipant participant = findParticipantByMissionAndUser(missionId, userId);
        return MissionParticipantResponse.from(participant);
    }

    /**
     * 사용자가 해당 미션에 참여 중인지 확인
     */
    public boolean isParticipating(Long missionId, String userId) {
        return participantRepository.existsActiveParticipation(missionId, userId);
    }

    private Mission findMissionById(Long missionId) {
        return missionRepository.findById(missionId)
            .orElseThrow(() -> new IllegalArgumentException("미션을 찾을 수 없습니다: " + missionId));
    }

    private MissionParticipant findParticipantById(Long participantId) {
        return participantRepository.findById(participantId)
            .orElseThrow(() -> new IllegalArgumentException("참여자를 찾을 수 없습니다: " + participantId));
    }

    private MissionParticipant findParticipantByMissionAndUser(Long missionId, String userId) {
        return participantRepository.findByMissionIdAndUserId(missionId, userId)
            .orElseThrow(() -> new IllegalArgumentException("미션 참여 정보를 찾을 수 없습니다."));
    }

    private void validateMissionJoinable(Mission mission, String userId) {
        if (!mission.getStatus().isJoinable()) {
            throw new IllegalStateException("모집중인 미션에만 참여할 수 있습니다.");
        }

        if (participantRepository.existsActiveParticipation(mission.getId(), userId)) {
            throw new IllegalStateException("이미 참여한 미션입니다.");
        }

        if (mission.getMaxParticipants() != null) {
            long currentCount = participantRepository.countActiveParticipants(mission.getId());
            if (currentCount >= mission.getMaxParticipants()) {
                throw new IllegalStateException("참여 인원이 초과되었습니다.");
            }
        }
    }

    private void validateMissionOwner(Mission mission, String userId) {
        if (!mission.getCreatorId().equals(userId)) {
            throw new IllegalStateException("미션 생성자만 이 작업을 수행할 수 있습니다.");
        }
    }

    private void validateParticipantBelongsToMission(MissionParticipant participant, Long missionId) {
        if (!participant.getMission().getId().equals(missionId)) {
            throw new IllegalArgumentException("해당 미션의 참여자가 아닙니다.");
        }
    }
}
