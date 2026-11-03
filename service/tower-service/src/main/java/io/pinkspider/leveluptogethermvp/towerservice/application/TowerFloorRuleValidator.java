package io.pinkspider.leveluptogethermvp.towerservice.application;

import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerFloorException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * LUT-629: 층 저장 규칙 7개. 저장 뒤의 전체 층 목록({@code floorsAfterChange}, 수정·추가 대상 포함)으로 판정한다.
 *
 * <ol>
 *   <li>층 번호 중복
 *   <li>1층에 입장 레벨 지정 (1층은 항상 제한 없음)
 *   <li>입장 레벨이 있는 층에 비로그인 허용 (방문객은 레벨이 없다)
 *   <li>입장 레벨 순서 역전 — 위층이 아래층보다 낮을 수 없다. 같은 값은 허용, 제한 없음은 가장 낮은 값
 *   <li>비로그인 허용이 1층부터 이어지지 않음 (아래층 비허용·위층 허용 불가)
 *   <li>공개 층 번호가 1부터 연속이 아님 (아래층 비공개·위층 공개 불가, 빈 번호 불가)
 *   <li>입장 지점이 마스크 밖 (대상 층만, {@link FloorMaskChecker})
 * </ol>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TowerFloorRuleValidator {

    private final FloorMaskChecker floorMaskChecker;

    /** dev·테스트에서 이미지 서버가 없을 때만 끈다. 운영은 켜 둔다 */
    @Value("${tower.floor.mask-check.enabled:true}")
    private boolean maskCheckEnabled;

    /**
     * @param floorsAfterChange 저장이 끝난 뒤의 전체 층(대상 포함)
     * @param target 추가·수정 대상 (삭제 검증이면 null — 남는 층끼리의 규칙만 본다)
     */
    public void validate(List<TowerFloor> floorsAfterChange, TowerFloor target) {
        List<TowerFloor> sorted =
                floorsAfterChange.stream()
                        .sorted(Comparator.comparing(TowerFloor::getFloorNumber))
                        .toList();

        if (target != null) {
            boolean duplicate =
                    sorted.stream()
                            .anyMatch(
                                    f ->
                                            f != target
                                                    && Objects.equals(
                                                            f.getFloorNumber(),
                                                            target.getFloorNumber()));
            if (duplicate) {
                throw TowerFloorException.duplicateNumber();
            }
            if (target.getFloorNumber() == 1 && target.getEntryLevel() != null) {
                throw TowerFloorException.floor1EntryLevel();
            }
            if (target.isGuestAllowed() && target.getEntryLevel() != null) {
                throw TowerFloorException.guestRequiresNoLevel();
            }
        }

        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).effectiveEntryLevel() < sorted.get(i - 1).effectiveEntryLevel()) {
                throw TowerFloorException.entryLevelOrder();
            }
        }

        boolean guestBlocked = false;
        for (TowerFloor floor : sorted) {
            if (!floor.isGuestAllowed()) {
                guestBlocked = true;
            } else if (guestBlocked) {
                throw TowerFloorException.guestNotContiguous();
            }
        }

        Set<Integer> publicNumbers =
                sorted.stream()
                        .filter(TowerFloor::isPublicFloor)
                        .map(TowerFloor::getFloorNumber)
                        .collect(Collectors.toSet());
        for (int n = 1; n <= publicNumbers.size(); n++) {
            if (!publicNumbers.contains(n)) {
                throw TowerFloorException.publicNotContiguous();
            }
        }

        if (target != null) {
            validateEntryPoint(target);
        }
    }

    private void validateEntryPoint(TowerFloor target) {
        if (!maskCheckEnabled) {
            log.warn(
                    "탑 층 마스크 검사가 꺼져 있다(tower.floor.mask-check.enabled=false): floor={}",
                    target.getFloorNumber());
            return;
        }
        boolean inside;
        try {
            inside =
                    floorMaskChecker.isWalkable(
                            target.getFloorMaskUrl(),
                            target.getBackgroundImageUrl(),
                            target.getEntryPointX(),
                            target.getEntryPointY());
        } catch (IOException | RuntimeException e) {
            log.warn(
                    "탑 층 마스크를 읽지 못함: floor={}, mask={}, error={}",
                    target.getFloorNumber(),
                    target.getFloorMaskUrl(),
                    e.getMessage());
            throw TowerFloorException.maskUnreadable();
        }
        if (!inside) {
            throw TowerFloorException.entryPointOutsideMask();
        }
    }
}
