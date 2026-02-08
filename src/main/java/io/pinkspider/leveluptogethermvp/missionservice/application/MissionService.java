package io.pinkspider.leveluptogethermvp.missionservice.application;

import io.pinkspider.global.event.GuildMissionArrivedEvent;
import io.pinkspider.global.event.MissionStateChangedEvent;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildMember;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionCreateRequest;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionTemplateResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionUpdateRequest;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionCategory;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionTemplate;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionSource;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionCategoryRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionTemplateRepository;
import io.pinkspider.leveluptogethermvp.userservice.feed.application.ActivityFeedService;
import io.pinkspider.leveluptogethermvp.supportservice.report.api.dto.ReportTargetType;
import io.pinkspider.leveluptogethermvp.supportservice.report.application.ReportService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(transactionManager = "missionTransactionManager", readOnly = true)
public class MissionService {

    private final MissionRepository missionRepository;
    private final MissionTemplateRepository missionTemplateRepository;
    private final MissionParticipantRepository participantRepository;
    private final MissionCategoryRepository missionCategoryRepository;
    private final MissionParticipantService missionParticipantService;
    private final GuildMemberRepository guildMemberRepository;
    private final GuildRepository guildRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ActivityFeedService activityFeedService;
    private final ReportService reportService;

    @Transactional(transactionManager = "missionTransactionManager")
    public MissionResponse createMission(String creatorId, MissionCreateRequest request) {
        if (request.getType() == MissionType.GUILD && request.getGuildId() == null) {
            throw new IllegalArgumentException("길드 미션은 길드 ID가 필요합니다.");
        }

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

        // 길드 미션인 경우 길드 이름 조회
        String guildName = null;
        if (request.getType() == MissionType.GUILD && request.getGuildId() != null) {
            try {
                Long guildId = Long.parseLong(request.getGuildId());
                Guild guild = guildRepository.findById(guildId).orElse(null);
                if (guild != null) {
                    guildName = guild.getName();
                }
            } catch (NumberFormatException e) {
                log.warn("길드 ID 파싱 실패: guildId={}", request.getGuildId());
            }
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
            .guildName(guildName)
            .maxParticipants(request.getMaxParticipants())
            .startAt(request.getStartAt())
            .endAt(request.getEndAt())
            .missionInterval(request.getMissionInterval())
            .durationDays(request.getDurationDays())
            .durationMinutes(request.getDurationMinutes())
            .expPerCompletion(request.getExpPerCompletion())
            .bonusExpOnFullCompletion(request.getBonusExpOnFullCompletion())
            .category(category)
            .customCategory(customCategory)
            .isPinned(Boolean.TRUE.equals(request.getIsPinned()))
            .targetDurationMinutes(request.getTargetDurationMinutes())
            .dailyExecutionLimit(request.getDailyExecutionLimit())
            .build();

        Mission saved = missionRepository.save(mission);
        log.info("미션 생성 완료: id={}, title={}, creator={}, category={}",
            saved.getId(), saved.getTitle(), creatorId, saved.getCategoryName());

        // 상태 히스토리 이벤트 발행
        eventPublisher.publishEvent(MissionStateChangedEvent.ofCreation(creatorId, saved.getId(), saved.getStatus()));

        // 생성자를 자동으로 참여자로 등록하고 실행 스케줄 생성
        missionParticipantService.addCreatorAsParticipant(saved, creatorId);

        return MissionResponse.from(saved);
    }

    public MissionResponse getMission(Long missionId) {
        Mission mission = findMissionById(missionId);
        int participantCount = (int) participantRepository.countActiveParticipants(missionId);
        MissionResponse response = MissionResponse.from(mission, participantCount);

        // 신고 처리중 여부 확인
        response.setIsUnderReview(reportService.isUnderReview(ReportTargetType.MISSION, String.valueOf(missionId)));

        return response;
    }

    public List<MissionResponse> getMyMissions(String userId) {
        // 사용자가 참여중인 미션 목록 (ACCEPTED 상태)
        // 고정미션 > 길드미션 > 일반미션 순으로 정렬된 목록 반환
        List<Mission> missions = missionRepository.findByParticipantUserIdSorted(userId);
        List<MissionResponse> result = missions.stream()
            .map(MissionResponse::from)
            .toList();

        // 배치로 신고 상태 조회
        if (!result.isEmpty()) {
            List<String> missionIds = result.stream()
                .map(r -> String.valueOf(r.getId()))
                .toList();
            Map<String, Boolean> underReviewMap = reportService.isUnderReviewBatch(ReportTargetType.MISSION, missionIds);
            result.forEach(r -> r.setIsUnderReview(underReviewMap.getOrDefault(String.valueOf(r.getId()), false)));
        }

        return result;
    }

    public Page<MissionResponse> getPublicOpenMissions(Pageable pageable) {
        Page<Mission> missions = missionRepository.findOpenPublicMissions(pageable);

        // 배치로 신고 상태 조회
        List<String> missionIds = missions.getContent().stream()
            .map(m -> String.valueOf(m.getId()))
            .toList();
        Map<String, Boolean> underReviewMap = reportService.isUnderReviewBatch(ReportTargetType.MISSION, missionIds);

        return missions.map(mission -> {
            MissionResponse response = MissionResponse.from(mission);
            response.setIsUnderReview(underReviewMap.getOrDefault(String.valueOf(mission.getId()), false));
            return response;
        });
    }

    public List<MissionResponse> getGuildMissions(String guildId) {
        List<MissionStatus> activeStatuses = List.of(
            MissionStatus.DRAFT,
            MissionStatus.OPEN,
            MissionStatus.IN_PROGRESS
        );
        List<Mission> missions = missionRepository.findGuildMissions(guildId, activeStatuses);
        List<MissionResponse> result = missions.stream()
            .map(MissionResponse::from)
            .toList();

        // 배치로 신고 상태 조회
        if (!result.isEmpty()) {
            List<String> missionIds = result.stream()
                .map(r -> String.valueOf(r.getId()))
                .toList();
            Map<String, Boolean> underReviewMap = reportService.isUnderReviewBatch(ReportTargetType.MISSION, missionIds);
            result.forEach(r -> r.setIsUnderReview(underReviewMap.getOrDefault(String.valueOf(r.getId()), false)));
        }

        return result;
    }

    /**
     * 시스템 미션 템플릿 목록 조회 (미션북용)
     * mission_template 테이블에서 공개 시스템 템플릿 반환
     */
    public Page<MissionTemplateResponse> getSystemMissions(Pageable pageable) {
        Page<MissionTemplate> templates = missionTemplateRepository.findPublicTemplates(
            MissionSource.SYSTEM, MissionVisibility.PUBLIC, pageable);
        return templates.map(MissionTemplateResponse::from);
    }

    /**
     * 카테고리별 시스템 미션 템플릿 목록 조회
     */
    public Page<MissionTemplateResponse> getSystemMissionsByCategory(Long categoryId, Pageable pageable) {
        Page<MissionTemplate> templates = missionTemplateRepository.findPublicTemplatesByCategory(
            MissionSource.SYSTEM, MissionVisibility.PUBLIC, categoryId, pageable);
        return templates.map(MissionTemplateResponse::from);
    }

    @Transactional(transactionManager = "missionTransactionManager")
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
        if (request.getStartAt() != null) {
            mission.setStartAt(request.getStartAt());
        }
        if (request.getEndAt() != null) {
            mission.setEndAt(request.getEndAt());
        }
        if (request.getMissionInterval() != null) {
            mission.setMissionInterval(request.getMissionInterval());
        }
        if (request.getDurationDays() != null) {
            mission.setDurationDays(request.getDurationDays());
        }
        if (request.getDurationMinutes() != null) {
            mission.setDurationMinutes(request.getDurationMinutes());
        }
        if (request.getExpPerCompletion() != null) {
            mission.setExpPerCompletion(request.getExpPerCompletion());
        }
        if (request.getBonusExpOnFullCompletion() != null) {
            mission.setBonusExpOnFullCompletion(request.getBonusExpOnFullCompletion());
        }
        if (request.getTargetDurationMinutes() != null) {
            mission.setTargetDurationMinutes(request.getTargetDurationMinutes());
        }
        if (request.getDailyExecutionLimit() != null) {
            mission.setDailyExecutionLimit(request.getDailyExecutionLimit());
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

    @Transactional(transactionManager = "missionTransactionManager")
    public MissionResponse openMission(Long missionId, String userId) {
        Mission mission = findMissionById(missionId);
        validateMissionOwner(mission, userId);

        MissionStatus fromStatus = mission.getStatus();
        mission.open();
        log.info("미션 모집 시작: id={}", missionId);

        // 상태 히스토리 이벤트 발행
        eventPublisher.publishEvent(MissionStateChangedEvent.ofOpen(userId, missionId, fromStatus));

        // 길드 미션인 경우 길드원에게 모집 알림 전송 (참여는 직접 신청)
        if (mission.getType() == MissionType.GUILD && mission.getGuildId() != null) {
            notifyGuildMembersAboutRecruitment(mission, userId);
        }

        return MissionResponse.from(mission);
    }

    /**
     * 길드원에게 미션 모집 시작 알림 전송 (참여는 길드원이 직접 신청)
     * 이벤트를 발행하여 비동기로 알림 처리
     */
    private void notifyGuildMembersAboutRecruitment(Mission mission, String creatorId) {
        try {
            Long guildId = Long.parseLong(mission.getGuildId());
            List<GuildMember> activeMembers = guildMemberRepository.findActiveMembers(guildId);

            // 생성자를 제외한 활성 길드원 ID 목록 추출
            List<String> memberIds = activeMembers.stream()
                .map(GuildMember::getUserId)
                .filter(memberId -> !memberId.equals(creatorId))
                .toList();

            if (!memberIds.isEmpty()) {
                // 이벤트 발행 (리스너에서 비동기로 알림 처리)
                eventPublisher.publishEvent(new GuildMissionArrivedEvent(
                    creatorId,
                    memberIds,
                    mission.getId(),
                    mission.getTitle()
                ));
                log.info("길드 미션 모집 이벤트 발행: missionId={}, guildId={}, memberCount={}",
                    mission.getId(), guildId, memberIds.size());
            }
        } catch (NumberFormatException e) {
            log.error("길드 ID 파싱 실패: guildId={}", mission.getGuildId(), e);
        } catch (Exception e) {
            log.error("길드원 조회 실패: guildId={}, error={}", mission.getGuildId(), e.getMessage(), e);
        }
    }

    @Transactional(transactionManager = "missionTransactionManager")
    public MissionResponse startMission(Long missionId, String userId) {
        Mission mission = findMissionById(missionId);
        validateMissionOwner(mission, userId);

        MissionStatus fromStatus = mission.getStatus();
        mission.start();
        log.info("미션 시작: id={}", missionId);

        // 상태 히스토리 이벤트 발행
        eventPublisher.publishEvent(MissionStateChangedEvent.ofStart(userId, missionId, fromStatus));

        return MissionResponse.from(mission);
    }

    @Transactional(transactionManager = "missionTransactionManager")
    public MissionResponse completeMission(Long missionId, String userId) {
        Mission mission = findMissionById(missionId);
        validateMissionOwner(mission, userId);

        MissionStatus fromStatus = mission.getStatus();
        mission.complete();
        log.info("미션 완료: id={}", missionId);

        // 상태 히스토리 이벤트 발행
        eventPublisher.publishEvent(MissionStateChangedEvent.ofComplete(userId, missionId, fromStatus));

        return MissionResponse.from(mission);
    }

    @Transactional(transactionManager = "missionTransactionManager")
    public MissionResponse cancelMission(Long missionId, String userId) {
        Mission mission = findMissionById(missionId);
        validateMissionOwner(mission, userId);

        MissionStatus fromStatus = mission.getStatus();
        mission.cancel();
        log.info("미션 취소: id={}", missionId);

        // 상태 히스토리 이벤트 발행
        eventPublisher.publishEvent(MissionStateChangedEvent.ofCancel(userId, missionId, fromStatus));

        return MissionResponse.from(mission);
    }

    @Transactional(transactionManager = "missionTransactionManager")
    public void deleteMission(Long missionId, String userId) {
        Mission mission = findMissionById(missionId);

        // 시스템 미션의 참여자인 경우: 참여 철회로 처리
        if (mission.getSource() == MissionSource.SYSTEM && !mission.getCreatorId().equals(userId)) {
            missionParticipantService.withdrawFromMission(missionId, userId);
            log.info("시스템 미션 참여 철회: missionId={}, userId={}", missionId, userId);
            return;
        }

        // 미션 생성자이거나 길드 관리자인 경우: 미션 자체를 삭제
        boolean isCreator = mission.getCreatorId().equals(userId);
        boolean isGuildAdmin = false;

        if (mission.getType() == MissionType.GUILD && mission.getGuildId() != null) {
            try {
                Long guildId = Long.parseLong(mission.getGuildId());
                GuildMember member = guildMemberRepository.findByGuildIdAndUserId(guildId, userId).orElse(null);
                if (member != null && member.isActive() && member.isMasterOrSubMaster()) {
                    isGuildAdmin = true;
                }
            } catch (NumberFormatException e) {
                log.warn("길드 ID 파싱 실패: guildId={}", mission.getGuildId());
            }
        }

        if (isCreator || isGuildAdmin) {
            if (mission.getStatus() == MissionStatus.IN_PROGRESS) {
                throw new IllegalStateException("진행중인 미션은 삭제할 수 없습니다.");
            }
            missionRepository.delete(mission);
            log.info("미션 삭제: id={}, deletedBy={}", missionId, userId);

            // 관련 피드 삭제 (미션 공유 피드)
            try {
                int deletedFeedCount = activityFeedService.deleteFeedsByMissionId(missionId);
                log.info("미션 관련 피드 삭제: missionId={}, deletedFeedCount={}", missionId, deletedFeedCount);
            } catch (Exception e) {
                log.warn("미션 관련 피드 삭제 실패: missionId={}, error={}", missionId, e.getMessage());
            }
            return;
        }

        // 그 외의 경우: 권한 없음
        throw new IllegalStateException("미션 생성자 또는 길드 관리자만 이 작업을 수행할 수 있습니다.");
    }

    private Mission findMissionById(Long missionId) {
        return missionRepository.findById(missionId)
            .orElseThrow(() -> new IllegalArgumentException("미션을 찾을 수 없습니다: " + missionId));
    }

    private void validateMissionOwner(Mission mission, String userId) {
        // 미션 생성자인 경우 허용
        if (mission.getCreatorId().equals(userId)) {
            return;
        }

        // 길드 미션인 경우 길드 마스터 또는 부길드마스터도 허용
        if (mission.getType() == MissionType.GUILD && mission.getGuildId() != null) {
            try {
                Long guildId = Long.parseLong(mission.getGuildId());
                GuildMember member = guildMemberRepository.findByGuildIdAndUserId(guildId, userId).orElse(null);
                if (member != null && member.isActive() && member.isMasterOrSubMaster()) {
                    return;
                }
            } catch (NumberFormatException e) {
                log.warn("길드 ID 파싱 실패: guildId={}", mission.getGuildId());
            }
        }

        throw new IllegalStateException("미션 생성자 또는 길드 관리자만 이 작업을 수행할 수 있습니다.");
    }
}
