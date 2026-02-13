package io.pinkspider.leveluptogethermvp.missionservice.application;

import io.pinkspider.global.enums.MissionStatus;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionAdminPageResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionAdminRequest;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionAdminResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionInterval;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionParticipationType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionSource;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(transactionManager = "missionTransactionManager")
public class MissionAdminService {

    private final MissionRepository missionRepository;

    @Transactional(readOnly = true, transactionManager = "missionTransactionManager")
    public MissionAdminPageResponse searchMissions(
            String keyword, String source, String status,
            String type, String participationType,
            String creatorId, Long categoryId, Pageable pageable) {
        Page<MissionAdminResponse> page;
        if (hasSearchCriteria(keyword, source, status, type, participationType, creatorId)
                || categoryId != null) {
            page = missionRepository.searchMissionsAdmin(
                keyword,
                source,
                status,
                type,
                participationType,
                creatorId,
                categoryId,
                pageable
            ).map(MissionAdminResponse::from);
        } else {
            page = missionRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(MissionAdminResponse::from);
        }
        return MissionAdminPageResponse.from(page);
    }

    @Transactional(readOnly = true, transactionManager = "missionTransactionManager")
    public List<MissionAdminResponse> getAllMissions() {
        return missionRepository.findAll().stream()
            .map(MissionAdminResponse::from)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true, transactionManager = "missionTransactionManager")
    public List<MissionAdminResponse> getMissionsBySource(String source) {
        return missionRepository.findBySource(MissionSource.valueOf(source)).stream()
            .map(MissionAdminResponse::from)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true, transactionManager = "missionTransactionManager")
    public List<MissionAdminResponse> getMissionsBySourceAndParticipationType(String source, String participationType) {
        return missionRepository.findBySourceAndParticipationType(
                MissionSource.valueOf(source), MissionParticipationType.valueOf(participationType)).stream()
            .map(MissionAdminResponse::from)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true, transactionManager = "missionTransactionManager")
    public MissionAdminResponse getMission(Long id) {
        Mission mission = missionRepository.findById(id)
            .orElseThrow(() -> new CustomException("050101", "미션을 찾을 수 없습니다: " + id));
        return MissionAdminResponse.from(mission);
    }

    public MissionAdminResponse createMission(MissionAdminRequest request) {
        Mission mission = Mission.builder()
            .title(request.title())
            .titleEn(request.titleEn())
            .titleAr(request.titleAr())
            .description(request.description())
            .descriptionEn(request.descriptionEn())
            .descriptionAr(request.descriptionAr())
            .status(MissionStatus.valueOf(request.status()))
            .visibility(MissionVisibility.valueOf(request.visibility()))
            .type(MissionType.valueOf(request.type()))
            .source(request.source() != null ? MissionSource.valueOf(request.source()) : MissionSource.SYSTEM)
            .participationType(request.participationType() != null
                ? MissionParticipationType.valueOf(request.participationType()) : MissionParticipationType.DIRECT)
            .isCustomizable(request.isCustomizable() != null ? request.isCustomizable() : true)
            .creatorId(request.creatorId())
            .guildId(request.guildId())
            .maxParticipants(request.maxParticipants())
            .startAt(request.startAt())
            .endAt(request.endAt())
            .missionInterval(request.missionInterval() != null
                ? MissionInterval.valueOf(request.missionInterval()) : MissionInterval.DAILY)
            .durationDays(request.durationDays())
            .durationMinutes(request.durationMinutes())
            .expPerCompletion(request.expPerCompletion() != null ? request.expPerCompletion() : 10)
            .bonusExpOnFullCompletion(request.bonusExpOnFullCompletion() != null ? request.bonusExpOnFullCompletion() : 50)
            .isPinned(request.isPinned() != null ? request.isPinned() : false)
            .targetDurationMinutes(request.targetDurationMinutes())
            .dailyExecutionLimit(request.dailyExecutionLimit())
            .guildExpPerCompletion(request.guildExpPerCompletion() != null ? request.guildExpPerCompletion() : 5)
            .guildBonusExpOnFullCompletion(request.guildBonusExpOnFullCompletion() != null ? request.guildBonusExpOnFullCompletion() : 20)
            .build();

        Mission saved = missionRepository.save(mission);
        log.info("미션 생성 (Admin): {} (ID: {})", request.title(), saved.getId());
        return MissionAdminResponse.from(saved);
    }

    public MissionAdminResponse updateMission(Long id, MissionAdminRequest request) {
        Mission mission = missionRepository.findById(id)
            .orElseThrow(() -> new CustomException("050101", "미션을 찾을 수 없습니다: " + id));

        mission.setTitle(request.title());
        mission.setTitleEn(request.titleEn());
        mission.setTitleAr(request.titleAr());
        mission.setDescription(request.description());
        mission.setDescriptionEn(request.descriptionEn());
        mission.setDescriptionAr(request.descriptionAr());
        mission.setStatus(MissionStatus.valueOf(request.status()));
        mission.setVisibility(MissionVisibility.valueOf(request.visibility()));
        mission.setType(MissionType.valueOf(request.type()));
        if (request.source() != null) {
            mission.setSource(MissionSource.valueOf(request.source()));
        }
        if (request.participationType() != null) {
            mission.setParticipationType(MissionParticipationType.valueOf(request.participationType()));
        }
        if (request.isCustomizable() != null) {
            mission.setIsCustomizable(request.isCustomizable());
        }
        mission.setCreatorId(request.creatorId());
        mission.setGuildId(request.guildId());
        mission.setMaxParticipants(request.maxParticipants());
        mission.setStartAt(request.startAt());
        mission.setEndAt(request.endAt());
        if (request.missionInterval() != null) {
            mission.setMissionInterval(MissionInterval.valueOf(request.missionInterval()));
        }
        mission.setDurationDays(request.durationDays());
        mission.setDurationMinutes(request.durationMinutes());
        mission.setExpPerCompletion(request.expPerCompletion());
        mission.setBonusExpOnFullCompletion(request.bonusExpOnFullCompletion());
        if (request.isPinned() != null) {
            mission.setIsPinned(request.isPinned());
        }
        mission.setTargetDurationMinutes(request.targetDurationMinutes());
        mission.setDailyExecutionLimit(request.dailyExecutionLimit());
        mission.setGuildExpPerCompletion(request.guildExpPerCompletion());
        mission.setGuildBonusExpOnFullCompletion(request.guildBonusExpOnFullCompletion());

        Mission saved = missionRepository.save(mission);
        log.info("미션 수정 (Admin): {} (ID: {})", request.title(), id);
        return MissionAdminResponse.from(saved);
    }

    public MissionAdminResponse updateMissionStatus(Long id, String status) {
        Mission mission = missionRepository.findById(id)
            .orElseThrow(() -> new CustomException("050101", "미션을 찾을 수 없습니다: " + id));
        mission.updateStatus(MissionStatus.valueOf(status));
        Mission saved = missionRepository.save(mission);
        log.info("미션 상태 변경 (Admin): ID={}, status={}", id, status);
        return MissionAdminResponse.from(saved);
    }

    public void deleteMission(Long id) {
        if (!missionRepository.existsById(id)) {
            throw new CustomException("050101", "미션을 찾을 수 없습니다: " + id);
        }
        missionRepository.deleteById(id);
        log.info("미션 삭제 (Admin): ID={}", id);
    }

    @Transactional(readOnly = true, transactionManager = "missionTransactionManager")
    public Long countBySource(String source) {
        return missionRepository.countBySource(MissionSource.valueOf(source));
    }

    @Transactional(readOnly = true, transactionManager = "missionTransactionManager")
    public Long countBySourceAndParticipationType(String source, String participationType) {
        return missionRepository.countBySourceAndParticipationType(
            MissionSource.valueOf(source), MissionParticipationType.valueOf(participationType));
    }

    @Transactional(readOnly = true, transactionManager = "missionTransactionManager")
    public Long countBySourceAndCreatorId(String source, String creatorId) {
        return missionRepository.countBySourceAndCreatorId(MissionSource.valueOf(source), creatorId);
    }

    private boolean hasSearchCriteria(String... criteria) {
        for (String c : criteria) {
            if (c != null && !c.isBlank()) return true;
        }
        return false;
    }
}
