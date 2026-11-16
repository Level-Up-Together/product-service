package io.pinkspider.leveluptogethermvp.missionservice.application;

import io.pinkspider.leveluptogethermvp.metaservice.application.MissionCategoryService;
import io.pinkspider.leveluptogethermvp.metaservice.domain.dto.MissionCategoryResponse;
import io.pinkspider.leveluptogethermvp.missionservice.api.exception.RecommendedMissionException;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.RecommendedMissionAdminResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.RecommendedMissionRequest;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.RecommendedMission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionExecutionMode;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.RecommendedMissionRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-782: 어드민 추천 미션 관리 (내부 API, 화면은 LUT-783). 목록은 순서대로 전부(꺼진 것 포함)이고 항목마다 성과(시작 수·완료 수)를 붙인다.
 *
 * <p>저장 규칙: 한국어 이름·이모지·종류 필수, 타이머는 목표 시간 1~240분 필수(체크는 목표 시간을 버린다), 카테고리는 있으면 켜진 카테고리여야 한다. 새 항목은 맨
 * 뒤. 순서 바꾸기는 전체 ID 를 빠짐없이 한 번씩 보내야 한다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(transactionManager = "missionTransactionManager", readOnly = true)
public class RecommendedMissionAdminService {

    private final RecommendedMissionRepository repository;
    private final MissionRepository missionRepository;
    private final MissionCategoryService missionCategoryService;

    public List<RecommendedMissionAdminResponse> getAll() {
        Map<Long, long[]> stats = loadStats();
        return repository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(m -> toResponse(m, stats))
                .toList();
    }

    public RecommendedMissionAdminResponse get(Long id) {
        return toResponse(find(id), loadStats());
    }

    @Transactional(transactionManager = "missionTransactionManager")
    public RecommendedMissionAdminResponse create(RecommendedMissionRequest request) {
        validate(request);
        RecommendedMission saved =
                repository.save(
                        RecommendedMission.create(
                                request.titleKo().trim(),
                                request.titleEn(),
                                request.titleJa(),
                                request.titleAr(),
                                request.categoryId(),
                                request.executionMode(),
                                request.targetMinutes(),
                                request.icon().trim(),
                                request.active() == null || request.active(),
                                repository.findMaxSortOrder() + 1));
        log.info("추천 미션 작성: id={}", saved.getId());
        return RecommendedMissionAdminResponse.from(saved, 0, 0);
    }

    @Transactional(transactionManager = "missionTransactionManager")
    public RecommendedMissionAdminResponse update(Long id, RecommendedMissionRequest request) {
        RecommendedMission mission = find(id);
        validate(request);
        mission.update(
                request.titleKo().trim(),
                request.titleEn(),
                request.titleJa(),
                request.titleAr(),
                request.categoryId(),
                request.executionMode(),
                request.targetMinutes(),
                request.icon().trim(),
                request.active() == null ? mission.isActive() : request.active());
        log.info("추천 미션 수정: id={}", id);
        return toResponse(mission, loadStats());
    }

    @Transactional(transactionManager = "missionTransactionManager")
    public void delete(Long id) {
        repository.delete(find(id));
        log.info("추천 미션 삭제: id={}", id);
    }

    @Transactional(transactionManager = "missionTransactionManager")
    public RecommendedMissionAdminResponse toggleActive(Long id) {
        RecommendedMission mission = find(id);
        mission.toggleActive();
        return toResponse(mission, loadStats());
    }

    /** 전체 ID 를 새 순서대로 받아 1부터 다시 매긴다 */
    @Transactional(transactionManager = "missionTransactionManager")
    public List<RecommendedMissionAdminResponse> reorder(List<Long> orderedIds) {
        List<RecommendedMission> all = repository.findAllByOrderBySortOrderAscIdAsc();
        Set<Long> unique = new HashSet<>(orderedIds);
        Set<Long> existing =
                all.stream().map(RecommendedMission::getId).collect(Collectors.toSet());
        if (unique.size() != orderedIds.size() || !unique.equals(existing)) {
            throw RecommendedMissionException.invalidReorder();
        }
        Map<Long, RecommendedMission> byId =
                all.stream()
                        .collect(Collectors.toMap(RecommendedMission::getId, Function.identity()));
        for (int i = 0; i < orderedIds.size(); i++) {
            byId.get(orderedIds.get(i)).changeSortOrder(i + 1);
        }
        log.info("추천 미션 순서 변경: {}", orderedIds);
        Map<Long, long[]> stats = loadStats();
        return orderedIds.stream().map(id -> toResponse(byId.get(id), stats)).toList();
    }

    private void validate(RecommendedMissionRequest request) {
        if (request.executionMode() == MissionExecutionMode.TIMED
                && request.targetMinutes() == null) {
            throw RecommendedMissionException.targetMinutesRequired();
        }
        if (request.categoryId() != null) {
            MissionCategoryResponse category;
            try {
                category = missionCategoryService.getCategory(request.categoryId());
            } catch (RuntimeException e) {
                throw RecommendedMissionException.invalidCategory();
            }
            if (!Boolean.TRUE.equals(category.getIsActive())) {
                throw RecommendedMissionException.invalidCategory();
            }
        }
    }

    private RecommendedMission find(Long id) {
        return repository.findById(id).orElseThrow(RecommendedMissionException::notFound);
    }

    /** 추천 미션 ID → [시작 수, 완료 수] */
    private Map<Long, long[]> loadStats() {
        Map<Long, long[]> stats = new HashMap<>();
        for (Object[] row : missionRepository.countRecommendedMissionStats()) {
            stats.put(
                    ((Number) row[0]).longValue(),
                    new long[] {((Number) row[1]).longValue(), ((Number) row[2]).longValue()});
        }
        return stats;
    }

    private static RecommendedMissionAdminResponse toResponse(
            RecommendedMission mission, Map<Long, long[]> stats) {
        long[] s = stats.getOrDefault(mission.getId(), new long[] {0, 0});
        return RecommendedMissionAdminResponse.from(mission, s[0], s[1]);
    }
}
