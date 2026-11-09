package io.pinkspider.global.policy;

import io.pinkspider.global.enums.TitleRarity;

/**
 * LUT-348: 유저 레벨 → 희귀도 등급 매핑과, 등급 차이(gap) 기반 상점 잠금 정책.
 *
 * <p>LUT-758: 등급 차이에 따른 가격 할증은 없앴다. 상점 아이템 가격은 어드민에 입력한 값 그대로이고 레벨과 상관없다(다이아를 사는 쪽이 대부분 낮은 레벨 유저라
 * 실제로 돈을 쓰는 사람에게 웃돈을 받는 구조였고, "등급에 도달하면 더 저렴해요" 안내가 구매를 미루게 했다). 등급 차이는 이제 **잠금**(LUT-349)에만 쓴다.
 *
 * <p>레벨 구간은 레벨칩 색상 구간(프론트 changeLevelToColor)과 같은 기준이다.
 */
public final class LevelRarityPolicy {

    /**
     * LUT-349: 자기 등급보다 높은 등급에서 해금해줄 아이템 개수 — 가격이 가장 낮은 N개.
     *
     * <p>현재는 상수지만 추후 Admin 설정값(default 3)으로 대체할 수 있도록 {@link #isLocked(int, TitleRarity, int, int)}
     * 오버로드를 열어둔다.
     */
    public static final int DEFAULT_UNLOCK_COUNT = 3;

    private LevelRarityPolicy() {
        // Utility class
    }

    /** 레벨 → 희귀도 등급. 일반 1~2 / 고급 3~9 / 희귀 10~199 / 영웅 200~499 / 전설 500~899 / 신화 900~ */
    public static TitleRarity fromLevel(int level) {
        if (level < 3) {
            return TitleRarity.COMMON;
        }
        if (level < 10) {
            return TitleRarity.UNCOMMON;
        }
        if (level < 200) {
            return TitleRarity.RARE;
        }
        if (level < 500) {
            return TitleRarity.EPIC;
        }
        if (level < 900) {
            return TitleRarity.LEGENDARY;
        }
        return TitleRarity.MYTHIC;
    }

    /** 등급 차이. 자기 등급 이하의 아이템이면 0 */
    public static int gap(int userLevel, TitleRarity itemRarity) {
        if (itemRarity == null) {
            return 0;
        }
        return Math.max(0, itemRarity.ordinal() - fromLevel(userLevel).ordinal());
    }

    /**
     * LUT-349: 상점 아이템 구매 잠금 여부.
     *
     * <p>내 등급 이하는 다 열리고, 내 등급 위는 각 등급에서 가격이 가장 낮은 N개만 열린다. 정렬 기준이 곧 해금 기준이므로 rankInRarity 는 반드시
     * <b>같은 희귀도 안에서 가격(어드민 입력가) 오름차순</b> 순번이어야 한다. 어드민에서 가격을 바꾸면 열리는 N개도 바뀔 수 있다.
     *
     * @param rankInRarity 같은 희귀도 섹션 내 가격 오름차순 순번 (0부터)
     */
    public static boolean isLocked(int userLevel, TitleRarity itemRarity, int rankInRarity) {
        return isLocked(userLevel, itemRarity, rankInRarity, DEFAULT_UNLOCK_COUNT);
    }

    /** 해금 개수를 직접 지정하는 버전 (추후 Admin 설정값 연동용) */
    public static boolean isLocked(
            int userLevel, TitleRarity itemRarity, int rankInRarity, int unlockCount) {
        if (itemRarity == null) {
            return false; // 알 수 없는 등급은 잠그지 않는다
        }
        if (gap(userLevel, itemRarity) == 0) {
            return false; // 내 등급 이하는 전부 해금
        }
        return rankInRarity >= unlockCount;
    }
}
