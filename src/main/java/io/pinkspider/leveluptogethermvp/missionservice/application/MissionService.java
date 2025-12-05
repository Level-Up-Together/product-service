package io.pinkspider.leveluptogethermvp.missionservice.application;

import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionCreateRequest;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionUpdateRequest;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MissionService {

    private final MissionRepository missionRepository;
    private final MissionParticipantRepository participantRepository;

    @Transactional
    public MissionResponse createMission(String creatorId, MissionCreateRequest request) {
        if (request.getType() == MissionType.GUILD && request.getGuildId() == null) {
            throw new IllegalArgumentException("길드 미션은 길드 ID가 필요합니다.");
        }

        Mission mission = Mission.builder()
            .title(request.getTitle())
            .description(request.getDescription())
            .status(MissionStatus.DRAFT)
            .visibility(request.getVisibility())
            .type(request.getType())
            .creatorId(creatorId)
            .guildId(request.getGuildId())
            .maxParticipants(request.getMaxParticipants())
            .startDate(request.getStartDate())
            .endDate(request.getEndDate())
            .build();

        Mission saved = missionRepository.save(mission);
        log.info("미션 생성 완료: id={}, title={}, creator={}", saved.getId(), saved.getTitle(), creatorId);

        return MissionResponse.from(saved);
    }

    public MissionResponse getMission(Long missionId) {
        Mission mission = findMissionById(missionId);
        int participantCount = (int) participantRepository.countActiveParticipants(missionId);
        return MissionResponse.from(mission, participantCount);
    }

    public List<MissionResponse> getMyMissions(String userId) {
        return missionRepository.findMyMissions(userId).stream()
            .map(MissionResponse::from)
            .toList();
    }

    public Page<MissionResponse> getPublicOpenMissions(Pageable pageable) {
        return missionRepository.findOpenPublicMissions(pageable)
            .map(MissionResponse::from);
    }

    public List<MissionResponse> getGuildMissions(String guildId) {
        List<MissionStatus> activeStatuses = List.of(
            MissionStatus.DRAFT,
            MissionStatus.OPEN,
            MissionStatus.IN_PROGRESS
        );
        return missionRepository.findGuildMissions(guildId, activeStatuses).stream()
            .map(MissionResponse::from)
            .toList();
    }

    @Transactional
    public MissionResponse updateMission(Long missionId, String userId, MissionUpdateRequest request) {
        Mission mission = findMissionById(missionId);
        validateMissionOwner(mission, userId);

        if (mission.getStatus() != MissionStatus.DRAFT) {
            throw new IllegalStateException("작성중 상태의 미션만 수정할 수 있습니다.");
        }

        if (request.getTitle() != null) {
            mission.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            mission.setDescription(request.getDescription());
        }
        if (request.getVisibility() != null) {
            mission.setVisibility(request.getVisibility());
        }
        if (request.getMaxParticipants() != null) {
            mission.setMaxParticipants(request.getMaxParticipants());
        }
        if (request.getStartDate() != null) {
            mission.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            mission.setEndDate(request.getEndDate());
        }

        log.info("미션 수정 완료: id={}", missionId);
        return MissionResponse.from(mission);
    }

    @Transactional
    public MissionResponse openMission(Long missionId, String userId) {
        Mission mission = findMissionById(missionId);
        validateMissionOwner(mission, userId);

        mission.open();
        log.info("미션 오픈: id={}", missionId);

        return MissionResponse.from(mission);
    }

    @Transactional
    public MissionResponse startMission(Long missionId, String userId) {
        Mission mission = findMissionById(missionId);
        validateMissionOwner(mission, userId);

        mission.start();
        log.info("미션 시작: id={}", missionId);

        return MissionResponse.from(mission);
    }

    @Transactional
    public MissionResponse completeMission(Long missionId, String userId) {
        Mission mission = findMissionById(missionId);
        validateMissionOwner(mission, userId);

        mission.complete();
        log.info("미션 완료: id={}", missionId);

        return MissionResponse.from(mission);
    }

    @Transactional
    public MissionResponse cancelMission(Long missionId, String userId) {
        Mission mission = findMissionById(missionId);
        validateMissionOwner(mission, userId);

        mission.cancel();
        log.info("미션 취소: id={}", missionId);

        return MissionResponse.from(mission);
    }

    @Transactional
    public void deleteMission(Long missionId, String userId) {
        Mission mission = findMissionById(missionId);
        validateMissionOwner(mission, userId);

        if (mission.getStatus() == MissionStatus.IN_PROGRESS) {
            throw new IllegalStateException("진행중인 미션은 삭제할 수 없습니다.");
        }

        missionRepository.delete(mission);
        log.info("미션 삭제: id={}", missionId);
    }

    private Mission findMissionById(Long missionId) {
        return missionRepository.findById(missionId)
            .orElseThrow(() -> new IllegalArgumentException("미션을 찾을 수 없습니다: " + missionId));
    }

    private void validateMissionOwner(Mission mission, String userId) {
        if (!mission.getCreatorId().equals(userId)) {
            throw new IllegalStateException("미션 생성자만 이 작업을 수행할 수 있습니다.");
        }
    }
}
