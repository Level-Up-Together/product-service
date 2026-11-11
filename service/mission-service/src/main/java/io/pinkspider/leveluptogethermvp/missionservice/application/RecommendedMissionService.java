package io.pinkspider.leveluptogethermvp.missionservice.application;

import io.pinkspider.leveluptogethermvp.missionservice.api.exception.RecommendedMissionException;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionExecutionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.RecommendedMissionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.RecommendedMissionStartResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.RecommendedMission;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.RecommendedMissionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-782: 첫 미션 온보딩 추천 미션 — 목록과 원탭 시작.
 *
 * <p>원탭 시작은 <b>한 트랜잭션</b>에서 내 미션을 만들고 오늘 수행을 시작한다. 이미 진행 중인 미션이 있으면 시작이 기존 규칙과 같은 오류를 던지고 만든 미션도 함께
 * 롤백된다(빈 미션이 남지 않음, 생성 이벤트는 AFTER_COMMIT 이라 나가지 않음). 체크(SIMPLE) 미션도 시작만 하고 완료는 기존 완료 API 로 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "missionTransactionManager", readOnly = true)
public class RecommendedMissionService {

    private final RecommendedMissionRepository repository;
    private final MissionService missionService;
    private final MissionExecutionService executionService;

    public List<RecommendedMissionResponse> getActive(String locale) {
        return repository.findAllByActiveTrueOrderBySortOrderAscIdAsc().stream()
                .map(m -> RecommendedMissionResponse.from(m, locale))
                .toList();
    }

    @Transactional(transactionManager = "missionTransactionManager")
    public RecommendedMissionStartResponse start(String userId, Long recommendedId, String locale) {
        RecommendedMission recommended =
                repository
                        .findByIdAndActiveTrue(recommendedId)
                        .orElseThrow(RecommendedMissionException::notFound);
        MissionResponse mission =
                missionService.createMissionFromRecommended(recommended, userId, locale);
        MissionExecutionResponse execution =
                executionService.startExecutionToday(mission.getId(), userId);
        return new RecommendedMissionStartResponse(mission, execution);
    }
}
