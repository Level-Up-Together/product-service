package io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 상점 아이템 타입 (QA-225)
 */
@Getter
@RequiredArgsConstructor
public enum ShopItemType {
    BASIC("기본"),
    FULL("풀"),
    HEAD("머리"),
    EFFECT("이펙트"),
    ETC("기타");

    private final String displayName;

    /**
     * 장착 충돌 타입 목록 (LUT-308) — BASIC(날개)과 FULL(전신)은 캐릭터 몸 영역을 공유하므로 상호 배타.
     * 장착 시 이 목록에 속한 기존 장착을 모두 해제한다.
     */
    public List<ShopItemType> equipConflictTypes() {
        return switch (this) {
            case BASIC, FULL -> List.of(BASIC, FULL);
            default -> List.of(this);
        };
    }
}
