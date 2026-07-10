package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.application;

import io.pinkspider.global.facade.MissionQueryFacade;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.DiamondMigrationResultResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserExperience;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.DiamondType;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.DiamondHistoryRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserExperienceRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * QA-220: 기존 유저 다이아 소급 지급 (일회성, 내부 API 트리거).
 *
 * 1. 레벨 기반: 현재 레벨 기준 (레벨-1)개 — 유저당 한 행으로 일괄 지급
 * 2. 미션북 기반: 목표달성(has_achieved_target)한 템플릿당 1개
 *
 * 멱등: 레벨 기반은 last_rewarded_level, 미션북은 (user_id, type, source_id) 기존 이력으로
 * 이미 지급된 몫을 건너뛰므로 재실행해도 중복 지급되지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiamondMigrationService {

    private static final int PAGE_SIZE = 500;

    private final UserExperienceRepository userExperienceRepository;
    private final DiamondHistoryRepository diamondHistoryRepository;
    private final DiamondService diamondService;
    private final MissionQueryFacade missionQueryFacade;

    public DiamondMigrationResultResponse migrate() {
        int usersProcessed = 0;
        int levelUpGranted = 0;
        int missionBookGranted = 0;

        int pageNumber = 0;
        Page<UserExperience> page;
        do {
            page = userExperienceRepository.findAll(
                PageRequest.of(pageNumber, PAGE_SIZE, Sort.by("id").ascending()));

            for (UserExperience userExp : page.getContent()) {
                String userId = userExp.getUserId();
                try {
                    levelUpGranted += diamondService.awardLevelUpDiamondsAggregated(
                        userId, userExp.getCurrentLevel() != null ? userExp.getCurrentLevel() : 1);
                    missionBookGranted += migrateMissionBookDiamonds(userId);
                    usersProcessed++;
                } catch (Exception e) {
                    // 유저 단위 실패는 건너뛰고 계속 진행 (재실행으로 보완 가능)
                    log.error("다이아 마이그레이션 실패 (skip): userId={}, error={}", userId, e.getMessage(), e);
                }
            }
            pageNumber++;
        } while (page.hasNext());

        log.info("다이아 마이그레이션 완료: users={}, levelUp={}, missionBook={}",
            usersProcessed, levelUpGranted, missionBookGranted);
        return new DiamondMigrationResultResponse(usersProcessed, levelUpGranted, missionBookGranted);
    }

    private int migrateMissionBookDiamonds(String userId) {
        Set<Long> clearedTemplateIds = missionQueryFacade.findClearedMissionBookTemplateIds(userId);
        if (clearedTemplateIds == null || clearedTemplateIds.isEmpty()) {
            return 0;
        }

        List<Long> alreadyAwarded = diamondHistoryRepository.findAwardedSourceIds(
            userId, DiamondType.MISSION_BOOK, clearedTemplateIds);
        Set<Long> targets = new HashSet<>(clearedTemplateIds);
        alreadyAwarded.forEach(targets::remove);
        if (targets.isEmpty()) {
            return 0;
        }

        Map<Long, String> titles = missionQueryFacade.getMissionBookTemplateTitles(targets);
        int granted = 0;
        for (Long templateId : targets) {
            String title = titles.getOrDefault(templateId, "미션북 미션");
            if (diamondService.awardMissionBookDiamond(userId, templateId, title)) {
                granted++;
            }
        }
        return granted;
    }
}
