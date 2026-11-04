package io.pinkspider.leveluptogethermvp.towerservice.application;

import io.pinkspider.global.facade.TowerQueryFacade;
import io.pinkspider.global.facade.dto.TowerMissionSeatDto;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorVisitRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerMissionSeatRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** LUT-632: {@link TowerQueryFacade} 구현 — mission-service 가 완료 응답에 탑 자리를 실을 때 읽는다 */
@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "towerTransactionManager", readOnly = true)
public class TowerQueryFacadeService implements TowerQueryFacade {

    private final TowerMissionSeatRepository towerMissionSeatRepository;
    private final TowerFloorVisitRepository towerFloorVisitRepository;

    @Override
    public Optional<TowerMissionSeatDto> findMissionSeat(
            String userId, Long missionId, Long executionId, String executionKind) {
        if (userId == null || executionId == null || executionKind == null) {
            return Optional.empty();
        }
        return towerMissionSeatRepository
                .findFirstByUserIdAndExecutionKindAndExecutionIdOrderBySeatedAtDesc(
                        userId, executionKind, executionId)
                .map(
                        seat ->
                                new TowerMissionSeatDto(
                                        seat.getFloorNumber(),
                                        seat.isAutoSeated(),
                                        seat.getCheerCount()));
    }

    @Override
    public int countVisitedFloors(String userId) {
        if (userId == null) return 0;
        return (int) towerFloorVisitRepository.countByUserId(userId);
    }

    @Override
    public int highestVisitedFloor(String userId) {
        if (userId == null) return 0;
        return towerFloorVisitRepository.findHighestFloorNumber(userId);
    }
}
