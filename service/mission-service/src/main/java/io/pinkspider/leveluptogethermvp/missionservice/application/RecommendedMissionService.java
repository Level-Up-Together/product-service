package io.pinkspider.leveluptogethermvp.missionservice.application;

import io.pinkspider.leveluptogethermvp.missionservice.api.exception.RecommendedMissionException;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionExecutionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.RecommendedMissionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.RecommendedMissionStartResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.RecommendedMission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionExecutionMode;
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
 *
 * <p>LUT-803: 체크 미션은 수행 중을 거치지 않게, 화면은 확인을 누른 뒤 원탭 완료({@link RecommendedMissionCompleteService})를
 * 부른다.
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

    /**
     * LUT-803: 원탭 완료 대상인지 — 켜져 있는 체크(SIMPLE) 추천 미션이어야 한다. 미션을 만들기 전에 확인해, 타이머 추천에 완료를 부르면 아무것도 만들지
     * 않고 거절한다.
     */
    public RecommendedMission requireActiveSimple(Long recommendedId) {
        RecommendedMission recommended =
                repository
                        .findByIdAndActiveTrue(recommendedId)
                        .orElseThrow(RecommendedMissionException::notFound);
        if (recommended.getExecutionMode() != MissionExecutionMode.SIMPLE) {
            throw RecommendedMissionException.notSimple();
        }
        return recommended;
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
