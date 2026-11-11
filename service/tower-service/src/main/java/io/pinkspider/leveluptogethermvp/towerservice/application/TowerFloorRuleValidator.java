package io.pinkspider.leveluptogethermvp.towerservice.application;

import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerFloorException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloorEffectLayer;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerEffectKind;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
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
            // LUT-637: 공개 층은 배경 음악 필수 — 대상 층만 본다(기존 층은 다음 저장 때 걸린다)
            if (target.isPublicFloor()
                    && (target.getBgmUrl() == null || target.getBgmUrl().isBlank())) {
                throw TowerFloorException.publicRequiresBgm();
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
            validateEffects(target);
            validateEntryPoint(target);
        }
    }

    /**
     * LUT-725: 효과 레이어(종류당 1개·크기 최소 ≤ 최대·기간 시작 ≤ 종료 — 150115)와 앞 가림·효과 마스크 비율(배경과 같아야 — 150116, 못
     * 읽으면 150109). 개수·범위는 요청 검증(@Size·@Min·@Max)이 먼저 막는다.
     */
    /**
     * LUT-740: 종류별 값 범위 — 눈·별은 개수(밀도) 1~300·속도 1~100·크기 1~20(최소 ≤ 최대), 구름은 짙기·속도 0~100·번짐 0~200(크기
     * 무시). 요청 검증은 두 종류를 합친 범위만 막는다. LUT-785: 물방울은 밀도·속도 1~100·흐르는 물방울 수 0~120(크기 무시).
     */
    static boolean inKindRange(TowerFloorEffectLayer layer) {
        if (layer.getKind() == TowerEffectKind.CLOUD) {
            return between(layer.getAmount(), 0, 100)
                    && between(layer.getSpeed(), 0, 100)
                    && between(layer.getSpread(), 0, 200);
        }
        if (layer.getKind() == TowerEffectKind.RAIN) {
            return between(layer.getAmount(), 1, 100)
                    && between(layer.getSpeed(), 1, 100)
                    && between(layer.getDropCount(), 0, 120);
        }
        return between(layer.getAmount(), 1, 300)
                && between(layer.getSpeed(), 1, 100)
                && between(layer.getSizeMin(), 1, 20)
                && between(layer.getSizeMax(), 1, 20)
                && layer.getSizeMin() <= layer.getSizeMax();
    }

    private static boolean between(Integer value, int min, int max) {
        return value != null && value >= min && value <= max;
    }

    private void validateEffects(TowerFloor target) {
        Set<TowerEffectKind> kinds = new HashSet<>();
        for (TowerFloorEffectLayer layer : target.getEffectLayers()) {
            if (!kinds.add(layer.getKind())
                    || !inKindRange(layer)
                    || (layer.getStartsOn() != null
                            && layer.getEndsOn() != null
                            && layer.getStartsOn().isAfter(layer.getEndsOn()))) {
                throw TowerFloorException.effectInvalid();
            }
        }
        if (!maskCheckEnabled) {
            return;
        }
        List<String> masks = new ArrayList<>();
        if (target.getOcclusionMaskUrl() != null) {
            masks.add(target.getOcclusionMaskUrl());
        }
        target.getEffectLayers().forEach(layer -> masks.add(layer.getMaskUrl()));
        for (String mask : masks) {
            boolean same;
            try {
                same = floorMaskChecker.sameAspectRatio(mask, target.getBackgroundImageUrl());
            } catch (IOException | RuntimeException e) {
                log.warn(
                        "탑 층 효과 마스크를 읽지 못함: floor={}, mask={}, error={}",
                        target.getFloorNumber(),
                        mask,
                        e.getMessage());
                throw TowerFloorException.maskUnreadable();
            }
            if (!same) {
                throw TowerFloorException.maskAspectMismatch();
            }
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
