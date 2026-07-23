package io.pinkspider.leveluptogethermvp.missionservice.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.global.event.GuildMissionArrivedEvent;
import io.pinkspider.global.event.MissionStateChangedEvent;
import io.pinkspider.global.facade.GuildQueryFacade;
import io.pinkspider.global.facade.dto.GuildPermissionCheck;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionCreateRequest;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionTemplateResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionUpdateRequest;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionTemplate;
import io.pinkspider.leveluptogethermvp.metaservice.application.MissionCategoryService;
import io.pinkspider.leveluptogethermvp.metaservice.domain.dto.MissionCategoryResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionSource;
import io.pinkspider.global.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionTemplateRepository;
import io.pinkspider.global.event.MissionDeletedEvent;
import io.pinkspider.global.enums.ReportTargetType;
import io.pinkspider.leveluptogethermvp.supportservice.report.application.ReportService;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
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
    private final io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository executionRepository;
    private final io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository dailyMissionInstanceRepository;
    private final MissionCategoryService missionCategoryService;
    private final MissionParticipantService missionParticipantService;
    private final GuildQueryFacade guildQueryFacadeService;
    private final io.pinkspider.global.facade.UserQueryFacade userQueryFacadeService;
    private final ApplicationEventPublisher eventPublisher;
    private final ReportService reportService;

    @Transactional(transactionManager = "missionTransactionManager")
    public MissionResponse createMission(String creatorId, MissionCreateRequest request) {
        if (request.getType() == MissionType.GUILD && request.getGuildId() == null) {
            throw new IllegalArgumentException("길드 미션은 길드 ID가 필요합니다.");
        }

        validateMissionCreationLimit(creatorId, request.getType());

        // 카테고리 처리: categoryId 또는 customCategory 중 하나만 사용 (스냅샷 패턴)
        Long categoryId = null;
        String categoryName = null;
        String customCategory = null;

        if (request.getCategoryId() != null) {
            MissionCategoryResponse categoryResponse = missionCategoryService.getCategory(request.getCategoryId());

            if (!categoryResponse.getIsActive()) {
                throw new IllegalArgumentException("비활성화된 카테고리입니다.");
            }

            categoryId = categoryResponse.getId();
            categoryName = categoryResponse.getName();
        } else if (request.getCustomCategory() != null && !request.getCustomCategory().isBlank()) {
            customCategory = request.getCustomCategory().trim();
        }

        // 길드 미션인 경우 길드 이름 조회
        String guildName = null;
        if (request.getType() == MissionType.GUILD && request.getGuildId() != null) {
            try {
                Long guildId = Long.parseLong(request.getGuildId());
                guildName = guildQueryFacadeService.getGuildName(guildId);
            } catch (NumberFormatException e) {
                log.warn("길드 ID 파싱 실패: guildId={}", request.getGuildId());
            }
        }

        Mission mission = Mission.builder()
            .title(request.getTitle())
            .description(request.getDescription())
            // LUT-227: 길드 미션은 '모집중(OPEN)' 단계 없이 생성 즉시 진행중(IN_PROGRESS)
            .status(request.getType() == MissionType.GUILD
                ? MissionStatus.IN_PROGRESS
                : MissionStatus.DRAFT)
            // LUT-257: 길드 미션 공개범위는 길드 공개여부로 강제 (요청값 무시)
            .visibility(request.getType() == MissionType.GUILD && request.getGuildId() != null
                ? resolveGuildMissionVisibility(request.getGuildId())
                : request.getVisibility())
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
            .categoryId(categoryId)
            .categoryName(categoryName)
            .customCategory(customCategory)
            .isPinned(Boolean.TRUE.equals(request.getIsPinned()))
            .executionMode(request.getExecutionMode() != null ? request.getExecutionMode() : io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionExecutionMode.TIMED)
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

        // LUT-227: 길드 미션은 '모집중(OPEN)' 상태 제거 — 생성 즉시 진행중(IN_PROGRESS)으로
        // 길드 상세 미션 탭·나의 미션에 곧장 노출되고, 길드원 자동 참여/알림도 생성 시점에 발송된다.
        if (saved.getType() == MissionType.GUILD && saved.getGuildId() != null) {
            enrollAndNotifyGuildMembers(saved, creatorId);
        }

        return MissionResponse.from(saved);
    }

    /**
     * 미션 템플릿으로부터 개인 미션 생성 (미션북에서 추가)
     */
    @Transactional(transactionManager = "missionTransactionManager")
    public MissionResponse createMissionFromTemplate(Long templateId, String userId) {
        MissionTemplate template = missionTemplateRepository.findById(templateId)
            .orElseThrow(() -> new IllegalArgumentException("미션 템플릿을 찾을 수 없습니다: " + templateId));

        // QA-143: 활성 참여중인 미션만 중복으로 본다. 완료/탈퇴/실패한 과거 미션은 재추가 허용.
        if (missionRepository.existsActiveByBaseMissionIdAndCreatorId(templateId, userId)) {
            throw new IllegalStateException("이미 추가한 미션입니다.");
        }

        validateMissionCreationLimit(userId, MissionType.PERSONAL);

        Mission mission = Mission.builder()
            .title(template.getTitle())
            .titleEn(template.getTitleEn())
            .titleAr(template.getTitleAr())
            .titleJa(template.getTitleJa())
            .description(template.getDescription())
            .descriptionEn(template.getDescriptionEn())
            .descriptionAr(template.getDescriptionAr())
            .descriptionJa(template.getDescriptionJa())
            .status(MissionStatus.DRAFT)
            // LUT-257: 미션북에서 추가한 미션은 무조건 공개
            .visibility(MissionVisibility.PUBLIC)
            .type(MissionType.PERSONAL)
            .source(MissionSource.SYSTEM)
            .participationType(template.getParticipationType())
            .baseMissionId(templateId)
            .creatorId(userId)
            .missionInterval(template.getMissionInterval())
            .durationMinutes(template.getDurationMinutes())
            .bonusExpOnFullCompletion(template.getBonusExpOnFullCompletion())
            .isPinned(Boolean.TRUE.equals(template.getIsPinned()))
            .executionMode(template.getExecutionMode() != null ? template.getExecutionMode() : io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionExecutionMode.TIMED)
            .targetDurationMinutes(template.getTargetDurationMinutes())
            .dailyExecutionLimit(template.getDailyExecutionLimit())
            .categoryId(template.getCategoryId())
            .categoryName(template.getCategoryName())
            .customCategory(template.getCustomCategory())
            .build();

        Mission saved = missionRepository.save(mission);
        log.info("템플릿으로 미션 생성: missionId={}, templateId={}, userId={}", saved.getId(), templateId, userId);

        eventPublisher.publishEvent(MissionStateChangedEvent.ofCreation(userId, saved.getId(), saved.getStatus()));
        missionParticipantService.addCreatorAsParticipant(saved, userId);

        return MissionResponse.from(saved);
    }

    public MissionResponse getMission(Long missionId) {
        return getMission(missionId, null);
    }

    /** LUT-255: locale에 맞는 title/description/categoryName으로 미션 상세 조회 */
    public MissionResponse getMission(Long missionId, String locale) {
        Mission mission = findMissionById(missionId);
        int participantCount = (int) participantRepository.countActiveParticipants(missionId);
        MissionResponse response = MissionResponse.from(mission, participantCount, locale);
        localizeMissionCategoryNames(List.of(response), locale);

        // QA-176: 미션 누적 EXP (탈퇴/실패 참여자 제외)
        fillTotalExpEarned(response);

        // 신고 처리중 여부 확인
        response.setIsUnderReview(reportService.isUnderReview(ReportTargetType.MISSION, String.valueOf(missionId)));

        return response;
    }

    public List<MissionResponse> getMyMissions(String userId) {
        return getMyMissions(userId, null);
    }

    /** LUT-255: locale에 맞는 title/description/categoryName으로 내 미션 목록 조회 */
    public List<MissionResponse> getMyMissions(String userId, String locale) {
        // 사용자가 참여중인 미션 목록 (ACCEPTED 상태)
        // 고정미션 > 길드미션 > 일반미션 순으로 정렬된 목록 반환
        List<Mission> missions = missionRepository.findByParticipantUserIdSorted(userId);
        List<MissionResponse> result = missions.stream()
            .map(mission -> MissionResponse.from(mission, locale))
            .toList();
        localizeMissionCategoryNames(result, locale);

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
     * QA-71: 내 미션 목록 순서 일괄 변경.
     * orderedMissionIds 순서대로 mission_participant.user_order 를 0..N-1 로 갱신한다.
     * 본인이 활성 참여중이 아닌 missionId 가 섞여 있으면 거부한다.
     * QA-140: 일반/고정 미션 간 교차 정렬 금지 (mission.isPinned 가 모두 같아야 함).
     * QA-142: 길드 미션은 항상 일반(not-FIXED) 그룹으로 분류 — 프론트 getMissionDisplayType 매핑 일치.
     */
    @Transactional(transactionManager = "missionTransactionManager")
    public void reorderMyMissions(String userId, List<Long> orderedMissionIds) {
        if (orderedMissionIds == null || orderedMissionIds.isEmpty()) {
            throw new CustomException("050105", "error.mission.reorder.empty");
        }

        Set<Long> uniqueIds = new HashSet<>(orderedMissionIds);
        if (uniqueIds.size() != orderedMissionIds.size()) {
            throw new CustomException("050106", "error.mission.reorder.duplicate");
        }

        List<io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant> participants =
            participantRepository.findActiveByUserIdAndMissionIds(userId, orderedMissionIds);

        if (participants.size() != orderedMissionIds.size()) {
            throw new CustomException("050107", "error.mission.reorder.not_participant");
        }

        // 프론트가 WEEKLY 도 고정 섹션으로 표시하므로 백엔드도 동일한 기준을 따른다.
        boolean firstPinned = isPinnedLikeForReorder(participants.get(0).getMission());
        for (var mp : participants) {
            if (isPinnedLikeForReorder(mp.getMission()) != firstPinned) {
                throw new CustomException("050108", "error.mission.reorder.mixed_type");
            }
        }

        Map<Long, io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant> byMissionId =
            new java.util.HashMap<>();
        for (var mp : participants) {
            byMissionId.put(mp.getMission().getId(), mp);
        }

        for (int i = 0; i < orderedMissionIds.size(); i++) {
            var mp = byMissionId.get(orderedMissionIds.get(i));
            mp.setUserOrder(i);
        }
    }

    // 프론트 getMissionDisplayType 과 동일 매핑.
    // QA-186: 길드 미션도 isPinned/WEEKLY 면 고정 섹션으로 분류 — QA-142 의 GUILD 우선 분기를 제거.
    private boolean isPinnedLikeForReorder(
        io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission mission) {
        return Boolean.TRUE.equals(mission.getIsPinned())
            || mission.getMissionInterval()
                == io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionInterval.WEEKLY;
    }

    /**
     * 특정 유저의 미션 목록 조회 (공개 범위 필터링)
     * - Self: 전체 표시
     * - Friend: PUBLIC + FRIENDS_ONLY
     * - Stranger: PUBLIC만
     */
    public List<MissionResponse> getUserMissions(String targetUserId, String currentUserId) {
        return getUserMissions(targetUserId, currentUserId, null);
    }

    /** LUT-255: locale에 맞는 title/description/categoryName으로 유저 미션 목록 조회 */
    public List<MissionResponse> getUserMissions(
        String targetUserId, String currentUserId, String locale) {
        boolean isSelf = currentUserId != null && currentUserId.equals(targetUserId);
        boolean isFriend = currentUserId != null && !isSelf
            && userQueryFacadeService.areFriends(currentUserId, targetUserId);

        List<MissionVisibility> allowedVisibilities;
        if (isSelf) {
            allowedVisibilities = List.of(MissionVisibility.PUBLIC, MissionVisibility.FRIENDS_ONLY,
                MissionVisibility.GUILD_ONLY, MissionVisibility.PRIVATE);
        } else if (isFriend) {
            allowedVisibilities = List.of(MissionVisibility.PUBLIC, MissionVisibility.FRIENDS_ONLY);
        } else {
            allowedVisibilities = List.of(MissionVisibility.PUBLIC);
        }

        List<Mission> missions = missionRepository.findUserMissionsByVisibility(targetUserId, allowedVisibilities);
        List<MissionResponse> result = missions.stream()
            .map(mission -> MissionResponse.from(mission, locale))
            .toList();
        localizeMissionCategoryNames(result, locale);
        return result;
    }

    public Page<MissionResponse> getPublicOpenMissions(Pageable pageable) {
        return getPublicOpenMissions(pageable, null);
    }

    /** LUT-255: locale에 맞는 title/description/categoryName으로 공개 미션 목록 조회 */
    public Page<MissionResponse> getPublicOpenMissions(Pageable pageable, String locale) {
        Page<Mission> missions = missionRepository.findOpenPublicMissions(pageable);

        // 배치로 신고 상태 조회
        List<String> missionIds = missions.getContent().stream()
            .map(m -> String.valueOf(m.getId()))
            .toList();
        Map<String, Boolean> underReviewMap = reportService.isUnderReviewBatch(ReportTargetType.MISSION, missionIds);

        Page<MissionResponse> responses = missions.map(mission -> {
            MissionResponse response = MissionResponse.from(mission, locale);
            response.setIsUnderReview(underReviewMap.getOrDefault(String.valueOf(mission.getId()), false));
            return response;
        });
        localizeMissionCategoryNames(responses.getContent(), locale);
        return responses;
    }

    /**
     * QA-175: 종료된 길드 미션도 목록에 노출(상태는 COMPLETED 로 표시). CANCELLED 는 제외.
     */
    private static final List<MissionStatus> GUILD_LIST_VISIBLE_STATUSES =
        List.of(MissionStatus.OPEN, MissionStatus.IN_PROGRESS, MissionStatus.COMPLETED);

    public List<MissionResponse> getGuildMissions(String guildId) {
        return getGuildMissions(guildId, null);
    }

    /** LUT-255: locale에 맞는 title/description/categoryName으로 길드 미션 목록 조회 */
    public List<MissionResponse> getGuildMissions(String guildId, String locale) {
        List<Mission> missions = missionRepository.findGuildMissions(guildId, GUILD_LIST_VISIBLE_STATUSES);
        List<MissionResponse> result = missions.stream()
            .map(mission -> MissionResponse.from(mission, locale))
            .toList();
        localizeMissionCategoryNames(result, locale);

        // QA-176: 미션별 누적 EXP 채우기 (탈퇴/실패 참여자 제외)
        result.forEach(this::fillTotalExpEarned);

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
     * QA-176: 미션 응답에 누적 EXP 를 채운다.
     * 길드 EXP 와 동일하게 historic 합산이므로 탈퇴/실패 참여자의 기여도 유지된다.
     *
     * <p>QA-194: 고정 미션은 DailyMissionInstance 에 expEarned 가 저장되므로 두 테이블을 모두 합산한다.
     */
    private void fillTotalExpEarned(MissionResponse response) {
        if (response == null || response.getId() == null) return;
        Long missionId = response.getId();
        int executionSum = nullToZero(executionRepository.sumExpEarnedByMissionId(missionId));
        int pinnedSum = nullToZero(dailyMissionInstanceRepository.sumExpEarnedByMissionId(missionId));
        response.setTotalExpEarned(executionSum + pinnedSum);
    }

    private static int nullToZero(Integer value) {
        return value != null ? value : 0;
    }

    /**
     * LUT-255: 미션 응답의 categoryName을 locale에 맞는 카테고리명으로 덮어쓴다.
     * locale이 없으면(한국어) denormalized 스냅샷 이름을 그대로 두고 meta 조회를 생략한다.
     * 카테고리 조회 실패/미존재 시에도 기존 스냅샷 이름(fallback)이 유지된다.
     */
    private void localizeMissionCategoryNames(List<MissionResponse> responses, String locale) {
        if (locale == null || locale.isBlank() || responses.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = getLocalizedCategoryNames(
            responses.stream().map(MissionResponse::getCategoryId).toList(), locale);
        responses.forEach(r -> {
            String localized = r.getCategoryId() != null ? nameMap.get(r.getCategoryId()) : null;
            if (localized != null) {
                r.setCategoryName(localized);
            }
        });
    }

    /** LUT-255: 미션북 템플릿 응답의 categoryName을 locale에 맞는 카테고리명으로 덮어쓴다. */
    private void localizeTemplateCategoryNames(
        List<MissionTemplateResponse> responses, String locale) {
        if (locale == null || locale.isBlank() || responses.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = getLocalizedCategoryNames(
            responses.stream().map(MissionTemplateResponse::getCategoryId).toList(), locale);
        responses.forEach(r -> {
            String localized = r.getCategoryId() != null ? nameMap.get(r.getCategoryId()) : null;
            if (localized != null) {
                r.setCategoryName(localized);
            }
        });
    }

    /** LUT-255: 카테고리 ID 목록 → locale에 맞는 카테고리명 Map (meta 배치 조회, 실패 시 빈 Map) */
    private Map<Long, String> getLocalizedCategoryNames(List<Long> categoryIds, String locale) {
        List<Long> ids = categoryIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        try {
            return missionCategoryService.getCategoriesByIds(ids).stream()
                .filter(c -> c.getId() != null && c.getLocalizedName(locale) != null)
                .collect(Collectors.toMap(
                    MissionCategoryResponse::getId,
                    c -> c.getLocalizedName(locale),
                    (a, b) -> a));
        } catch (Exception e) {
            log.warn("카테고리 다국어 배치 조회 실패: locale={}, error={}", locale, e.getMessage());
            return Map.of();
        }
    }

    /**
     * 시스템 미션 템플릿 목록 조회 (미션북용)
     * mission_template 테이블에서 공개 시스템 템플릿 반환
     */
    public Page<MissionTemplateResponse> getSystemMissions(String userId, Pageable pageable) {
        return getSystemMissions(userId, pageable, null);
    }

    /** LUT-255: locale에 맞는 title/description/categoryName으로 미션북 템플릿 목록 조회 */
    public Page<MissionTemplateResponse> getSystemMissions(
        String userId, Pageable pageable, String locale) {
        Page<MissionTemplate> templates = missionTemplateRepository.findPublicTemplates(
            MissionSource.SYSTEM, MissionVisibility.PUBLIC, pageable);

        // 비로그인 또는 목표시간 없는 경우 달성 여부 없이 반환
        if (userId == null) {
            Page<MissionTemplateResponse> responses =
                templates.map(t -> MissionTemplateResponse.from(t, locale));
            localizeTemplateCategoryNames(responses.getContent(), locale);
            return responses;
        }

        List<Long> templateIds = templates.stream()
            .filter(t -> t.getTargetDurationMinutes() != null)
            .map(MissionTemplate::getId)
            .toList();

        Set<Long> achievedIds = new HashSet<>();
        if (!templateIds.isEmpty()) {
            achievedIds.addAll(dailyMissionInstanceRepository.findAchievedTargetTemplateIds(userId, templateIds));
            achievedIds.addAll(executionRepository.findAchievedTargetTemplateIds(userId, templateIds));
        }

        Set<Long> finalAchievedIds = achievedIds;
        Page<MissionTemplateResponse> responses = templates.map(t -> {
            MissionTemplateResponse response = MissionTemplateResponse.from(t, locale);
            if (t.getTargetDurationMinutes() != null) {
                response.setHasAchievedTarget(finalAchievedIds.contains(t.getId()));
            }
            return response;
        });
        localizeTemplateCategoryNames(responses.getContent(), locale);
        return responses;
    }

    /**
     * 카테고리별 시스템 미션 템플릿 목록 조회.
     * QA-158: has_achieved_target 도 같이 채워 마이페이지/미션북 응답 정의를 일관화한다.
     */
    public Page<MissionTemplateResponse> getSystemMissionsByCategory(String userId, Long categoryId, Pageable pageable) {
        return getSystemMissionsByCategory(userId, categoryId, pageable, null);
    }

    /** LUT-255: locale에 맞는 title/description/categoryName으로 카테고리별 미션북 템플릿 목록 조회 */
    public Page<MissionTemplateResponse> getSystemMissionsByCategory(
        String userId, Long categoryId, Pageable pageable, String locale) {
        Page<MissionTemplate> templates = missionTemplateRepository.findPublicTemplatesByCategory(
            MissionSource.SYSTEM, MissionVisibility.PUBLIC, categoryId, pageable);

        if (userId == null) {
            Page<MissionTemplateResponse> responses =
                templates.map(t -> MissionTemplateResponse.from(t, locale));
            localizeTemplateCategoryNames(responses.getContent(), locale);
            return responses;
        }

        List<Long> templateIds = templates.stream()
            .filter(t -> t.getTargetDurationMinutes() != null)
            .map(MissionTemplate::getId)
            .toList();

        Set<Long> achievedIds = new HashSet<>();
        if (!templateIds.isEmpty()) {
            achievedIds.addAll(dailyMissionInstanceRepository.findAchievedTargetTemplateIds(userId, templateIds));
            achievedIds.addAll(executionRepository.findAchievedTargetTemplateIds(userId, templateIds));
        }

        Set<Long> finalAchievedIds = achievedIds;
        Page<MissionTemplateResponse> responses = templates.map(t -> {
            MissionTemplateResponse response = MissionTemplateResponse.from(t, locale);
            if (t.getTargetDurationMinutes() != null) {
                response.setHasAchievedTarget(finalAchievedIds.contains(t.getId()));
            }
            return response;
        });
        localizeTemplateCategoryNames(responses.getContent(), locale);
        return responses;
    }

    @Transactional(transactionManager = "missionTransactionManager")
    public MissionResponse updateMission(Long missionId, String userId, MissionUpdateRequest request) {
        Mission mission = findMissionById(missionId);
        validateMissionOwner(mission, userId);

        // LUT-257: 완료/취소 미션만 수정 불가. 작성중(DRAFT)은 전체 수정,
        // 모집중/진행중은 안전 필드(제목·설명·공개범위·카테고리)만 수정 허용 —
        // 진행 데이터에 영향 주는 필드(기간·경험치·인원 등)는 DRAFT 에서만 변경 가능.
        if (!mission.getStatus().isActive()) {
            throw new IllegalStateException("완료되거나 취소된 미션은 수정할 수 없습니다.");
        }
        boolean fullEdit = mission.getStatus().isModifiable();

        if (request.getTitle() != null) {
            mission.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            mission.setDescription(request.getDescription());
        }
        if (request.getVisibility() != null) {
            // LUT-257: 길드 미션 공개범위는 길드 공개여부로 강제되므로 변경 요청 무시
            if (mission.getType() != MissionType.GUILD) {
                mission.setVisibility(request.getVisibility());
            }
        }
        if (fullEdit && request.getMaxParticipants() != null) {
            mission.setMaxParticipants(request.getMaxParticipants());
        }
        if (fullEdit && request.getStartAt() != null) {
            mission.setStartAt(request.getStartAt());
        }
        if (fullEdit && request.getEndAt() != null) {
            mission.setEndAt(request.getEndAt());
        }
        if (fullEdit && request.getMissionInterval() != null) {
            mission.setMissionInterval(request.getMissionInterval());
        }
        if (fullEdit && request.getDurationDays() != null) {
            mission.setDurationDays(request.getDurationDays());
        }
        if (fullEdit && request.getDurationMinutes() != null) {
            mission.setDurationMinutes(request.getDurationMinutes());
        }
        if (fullEdit && request.getExpPerCompletion() != null) {
            mission.setExpPerCompletion(request.getExpPerCompletion());
        }
        if (fullEdit && request.getBonusExpOnFullCompletion() != null) {
            mission.setBonusExpOnFullCompletion(request.getBonusExpOnFullCompletion());
        }
        if (fullEdit && request.getTargetDurationMinutes() != null) {
            mission.setTargetDurationMinutes(request.getTargetDurationMinutes());
        }
        if (fullEdit && request.getDailyExecutionLimit() != null) {
            mission.setDailyExecutionLimit(request.getDailyExecutionLimit());
        }

        // 카테고리 수정 처리 (스냅샷 패턴)
        if (Boolean.TRUE.equals(request.getClearCategory())) {
            // 카테고리 제거
            mission.setCategoryId(null);
            mission.setCategoryName(null);
            mission.setCustomCategory(null);
        } else if (request.getCategoryId() != null) {
            // 기존 카테고리 선택
            MissionCategoryResponse categoryResponse = missionCategoryService.getCategory(request.getCategoryId());

            if (!categoryResponse.getIsActive()) {
                throw new IllegalArgumentException("비활성화된 카테고리입니다.");
            }

            mission.setCategoryId(categoryResponse.getId());
            mission.setCategoryName(categoryResponse.getName());
            mission.setCustomCategory(null);
        } else if (request.getCustomCategory() != null && !request.getCustomCategory().isBlank()) {
            // 사용자 정의 카테고리
            mission.setCategoryId(null);
            mission.setCategoryName(null);
            mission.setCustomCategory(request.getCustomCategory().trim());
        }

        log.info("미션 수정 완료: id={}", missionId);
        return MissionResponse.from(mission);
    }

    /**
     * LUT-257: 길드 미션 공개범위는 길드 공개여부를 따른다 (공개 길드=PUBLIC, 비공개 길드=PRIVATE).
     */
    private MissionVisibility resolveGuildMissionVisibility(String guildIdRaw) {
        try {
            Long guildId = Long.parseLong(guildIdRaw);
            return guildQueryFacadeService.isGuildPublic(guildId)
                ? MissionVisibility.PUBLIC
                : MissionVisibility.PRIVATE;
        } catch (NumberFormatException e) {
            log.warn("길드 ID 파싱 실패로 비공개 처리: guildId={}", guildIdRaw);
            return MissionVisibility.PRIVATE;
        }
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

        // 길드 미션인 경우 길드원 자동 참여 + 알림 전송
        if (mission.getType() == MissionType.GUILD && mission.getGuildId() != null) {
            enrollAndNotifyGuildMembers(mission, userId);
        }

        return MissionResponse.from(mission);
    }

    /**
     * 길드 미션 OPEN 시 활성 길드원 자동 참여 + 알림 전송
     */
    private void enrollAndNotifyGuildMembers(Mission mission, String creatorId) {
        try {
            Long guildId = mission.getGuildIdAsLong();

            // 생성자를 제외한 활성 길드원 ID 목록 추출
            List<String> memberIds = guildQueryFacadeService.getActiveMemberUserIds(guildId).stream()
                .filter(memberId -> !memberId.equals(creatorId))
                .toList();

            if (!memberIds.isEmpty()) {
                // 길드원 자동 참여 등록
                int enrolled = 0;
                for (String memberId : memberIds) {
                    try {
                        missionParticipantService.addGuildMemberAsParticipant(mission, memberId);
                        enrolled++;
                    } catch (Exception e) {
                        log.warn("길드원 미션 자동 참여 실패: missionId={}, userId={}, error={}",
                            mission.getId(), memberId, e.getMessage());
                    }
                }
                log.info("길드원 미션 자동 참여 완료: missionId={}, guildId={}, enrolled={}/{}",
                    mission.getId(), guildId, enrolled, memberIds.size());

                // 알림 이벤트 발행
                eventPublisher.publishEvent(new GuildMissionArrivedEvent(
                    creatorId,
                    memberIds,
                    mission.getId(),
                    mission.getTitle()
                ));
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
        // QA-175: 길드 미션은 길드 관리자(마스터/서브마스터)도 종료할 수 있다.
        boolean isCreator = mission.getCreatorId().equals(userId);
        boolean isGuildAdmin = isGuildAdmin(mission, userId);
        if (!isCreator && !isGuildAdmin) {
            throw new IllegalStateException("미션 생성자 또는 길드 관리자만 종료할 수 있습니다.");
        }

        MissionStatus fromStatus = mission.getStatus();
        mission.complete();
        log.info("미션 완료: id={}, by={}", missionId, userId);

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

        // 시스템 미션의 참여자인 경우: 참여 철회로 처리 (이 사용자의 진행중 인스턴스 검사)
        if (mission.getSource() == MissionSource.SYSTEM && !mission.getCreatorId().equals(userId)) {
            validateNoInProgressForUser(missionId, userId);
            missionParticipantService.withdrawFromMission(missionId, userId);
            log.info("시스템 미션 참여 철회: missionId={}, userId={}", missionId, userId);
            return;
        }

        // 미션 생성자이거나 길드 관리자인 경우: 미션 자체를 삭제
        boolean isCreator = mission.getCreatorId().equals(userId);
        boolean isGuildAdmin = isGuildAdmin(mission, userId);

        if (isCreator || isGuildAdmin) {
            // QA-175: 길드 관리자는 진행 중(IN_PROGRESS) 미션도 강제 삭제할 수 있다.
            // (이미 수행한 execution/instance 는 그대로 보존된다.)
            if (!isGuildAdmin && !mission.getStatus().isDeletable()) {
                throw new IllegalStateException("진행중인 미션은 삭제할 수 없습니다.");
            }
            if (!isGuildAdmin) {
                validateNoInProgressForMission(missionId);
            }
            mission.delete();
            missionRepository.save(mission);
            log.info("미션 소프트 삭제: id={}, deletedBy={}, guildAdmin={}", missionId, userId, isGuildAdmin);

            eventPublisher.publishEvent(new MissionDeletedEvent(userId, missionId));
            return;
        }

        // 그 외의 경우: 권한 없음
        throw new IllegalStateException("미션 생성자 또는 길드 관리자만 이 작업을 수행할 수 있습니다.");
    }

    /**
     * 호출 유저가 해당 미션의 길드 관리자(마스터/서브마스터)인지 확인. QA-175.
     */
    private boolean isGuildAdmin(Mission mission, String userId) {
        if (!mission.isGuildMission() || mission.getGuildIdAsLong() == null) {
            return false;
        }
        GuildPermissionCheck perm =
                guildQueryFacadeService.checkPermissions(mission.getGuildIdAsLong(), userId);
        return perm.isActiveMember() && perm.isMasterOrSubMaster();
    }

    /**
     * 미션에 진행 중(IN_PROGRESS) 수행/인스턴스가 존재하면 차단.
     * QA-112: 수행중 인스턴스가 있는 부모 미션 삭제 시 orphan IN_PROGRESS row가 남아
     * 이후 새 미션 시작이 영구 차단되는 문제 방지.
     */
    private void validateNoInProgressForMission(Long missionId) {
        if (executionRepository.existsInProgressByMissionId(missionId)
            || dailyMissionInstanceRepository.existsInProgressByMissionId(missionId)) {
            throw new io.pinkspider.global.exception.CustomException(
                "050102", "error.mission.cannot_delete_in_progress");
        }
    }

    private void validateNoInProgressForUser(Long missionId, String userId) {
        if (executionRepository.existsInProgressByMissionIdAndUserId(missionId, userId)
            || dailyMissionInstanceRepository.existsInProgressByMissionIdAndUserId(missionId, userId)) {
            throw new io.pinkspider.global.exception.CustomException(
                "050103", "error.mission.cannot_withdraw_in_progress");
        }
    }

    private Mission findMissionById(Long missionId) {
        return missionRepository.findByIdAndIsDeletedFalse(missionId)
            .orElseThrow(() -> new IllegalArgumentException("미션을 찾을 수 없습니다: " + missionId));
    }

    private void validateMissionOwner(Mission mission, String userId) {
        // 미션 생성자인 경우 허용
        if (mission.getCreatorId().equals(userId)) {
            return;
        }

        // 길드 미션인 경우 길드 마스터 또는 부길드마스터도 허용
        if (mission.isGuildMission() && mission.getGuildIdAsLong() != null) {
            GuildPermissionCheck perm = guildQueryFacadeService.checkPermissions(mission.getGuildIdAsLong(), userId);
            if (perm.isActiveMember() && perm.isMasterOrSubMaster()) {
                return;
            }
        }

        throw new IllegalStateException("미션 생성자 또는 길드 관리자만 이 작업을 수행할 수 있습니다.");
    }

    private void validateMissionCreationLimit(String userId, MissionType type) {
        if (type != MissionType.PERSONAL) {
            return;
        }
        long count = missionRepository.countActivePersonalByCreatorId(userId);
        if (count >= Mission.MAX_PERSONAL_MISSIONS_PER_USER) {
            throw new CustomException("050104", "error.mission.creation_limit_exceeded");
        }
    }
}
