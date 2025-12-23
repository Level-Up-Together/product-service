package io.pinkspider.leveluptogethermvp.missionservice.application;

import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionCreateRequest;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionUpdateRequest;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionCategory;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionSource;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionCategoryRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionRepository;
import io.pinkspider.leveluptogethermvp.profanity.application.ProfanityValidationService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final MissionCategoryRepository missionCategoryRepository;
    private final MissionParticipantService missionParticipantService;
    private final ProfanityValidationService profanityValidationService;

    @Transactional
    public MissionResponse createMission(String creatorId, MissionCreateRequest request) {
        if (request.getType() == MissionType.GUILD && request.getGuildId() == null) {
            throw new IllegalArgumentException("길드 미션은 길드 ID가 필요합니다.");
        }

        // 금칙어 검증
        Map<String, String> contentsToValidate = new HashMap<>();
        contentsToValidate.put("제목", request.getTitle());
        if (request.getDescription() != null) {
            contentsToValidate.put("설명", request.getDescription());
        }
        if (request.getCustomCategory() != null) {
            contentsToValidate.put("커스텀 카테고리", request.getCustomCategory());
        }
        profanityValidationService.validateContents(contentsToValidate);

        // 카테고리 처리: categoryId 또는 customCategory 중 하나만 사용
        MissionCategory category = null;
        String customCategory = null;

        if (request.getCategoryId() != null) {
            category = missionCategoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 카테고리입니다: " + request.getCategoryId()));

            if (!category.getIsActive()) {
                throw new IllegalArgumentException("비활성화된 카테고리입니다.");
            }
        } else if (request.getCustomCategory() != null && !request.getCustomCategory().isBlank()) {
            customCategory = request.getCustomCategory().trim();
        }

        Mission mission = Mission.builder()
            .title(request.getTitle())
            .description(request.getDescription())
            .status(MissionStatus.DRAFT)
            .visibility(request.getVisibility())
            .type(request.getType())
            .source(MissionSource.USER)  // 명시적으로 USER로 설정
            .creatorId(creatorId)
            .guildId(request.getGuildId())
            .maxParticipants(request.getMaxParticipants())
            .startDate(request.getStartDate())
            .endDate(request.getEndDate())
            .missionInterval(request.getMissionInterval())
            .durationDays(request.getDurationDays())
            .expPerCompletion(request.getExpPerCompletion())
            .bonusExpOnFullCompletion(request.getBonusExpOnFullCompletion())
            .category(category)
            .customCategory(customCategory)
            .build();

        Mission saved = missionRepository.save(mission);
        log.info("미션 생성 완료: id={}, title={}, creator={}, category={}",
            saved.getId(), saved.getTitle(), creatorId, saved.getCategoryName());

        // 생성자를 자동으로 참여자로 등록하고 실행 스케줄 생성
        missionParticipantService.addCreatorAsParticipant(saved, creatorId);

        return MissionResponse.from(saved);
    }

    public MissionResponse getMission(Long missionId) {
        Mission mission = findMissionById(missionId);
        int participantCount = (int) participantRepository.countActiveParticipants(missionId);
        return MissionResponse.from(mission, participantCount);
    }

    public List<MissionResponse> getMyMissions(String userId) {
        // 고정미션 > 길드미션 > 일반미션 순으로 정렬된 목록 반환
        return missionRepository.findMyMissionsSorted(userId).stream()
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

        // 금칙어 검증 (변경되는 필드만)
        Map<String, String> contentsToValidate = new HashMap<>();
        if (request.getTitle() != null) {
            contentsToValidate.put("제목", request.getTitle());
        }
        if (request.getDescription() != null) {
            contentsToValidate.put("설명", request.getDescription());
        }
        if (request.getCustomCategory() != null && !request.getCustomCategory().isBlank()) {
            contentsToValidate.put("커스텀 카테고리", request.getCustomCategory());
        }
        if (!contentsToValidate.isEmpty()) {
            profanityValidationService.validateContents(contentsToValidate);
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
        if (request.getMissionInterval() != null) {
            mission.setMissionInterval(request.getMissionInterval());
        }
        if (request.getDurationDays() != null) {
            mission.setDurationDays(request.getDurationDays());
        }
        if (request.getExpPerCompletion() != null) {
            mission.setExpPerCompletion(request.getExpPerCompletion());
        }
        if (request.getBonusExpOnFullCompletion() != null) {
            mission.setBonusExpOnFullCompletion(request.getBonusExpOnFullCompletion());
        }

        // 카테고리 수정 처리
        if (Boolean.TRUE.equals(request.getClearCategory())) {
            // 카테고리 제거
            mission.setCategory(null);
            mission.setCustomCategory(null);
        } else if (request.getCategoryId() != null) {
            // 기존 카테고리 선택
            MissionCategory category = missionCategoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 카테고리입니다: " + request.getCategoryId()));

            if (!category.getIsActive()) {
                throw new IllegalArgumentException("비활성화된 카테고리입니다.");
            }

            mission.setCategory(category);
            mission.setCustomCategory(null);
        } else if (request.getCustomCategory() != null && !request.getCustomCategory().isBlank()) {
            // 사용자 정의 카테고리
            mission.setCategory(null);
            mission.setCustomCategory(request.getCustomCategory().trim());
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
