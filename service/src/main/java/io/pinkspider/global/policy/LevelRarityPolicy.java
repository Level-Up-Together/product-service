package io.pinkspider.global.policy;

import io.pinkspider.global.enums.TitleRarity;

/**
 * LUT-348: 유저 레벨 → 희귀도 등급 매핑과, 등급 차이(gap) 기반 상점 가격 할증 정책.
 *
 * <p>자기 등급보다 높은 등급의 아이템은 등급 차이만큼 비싸게 산다. 레벨업하면 ① 다이아가 늘고 ② 같은 아이템이 싸지는 이중 보상 구조다.
 *
 * <pre>
 * gap             = max(0, 아이템등급 − 내등급)
 * effective_price = gap == 0 ? base : ceil(base × 배수[gap] / 10) × 10   // 결제가
 * list_price      = 최저등급(COMMON) 유저 기준가 = 최대 할증가            // 취소선 anchor
 * </pre>
 *
 * <p>프론트도 동일 공식으로 표시만 계산하고, 실제 결제 검증은 반드시 이 클래스를 통해 서버에서 한다 (ShopService.purchaseItem).
 *
 * <p>레벨 구간은 레벨칩 색상 구간(프론트 changeLevelToColor)과 같은 기준이다.
 */
public final class LevelRarityPolicy {

    /**
     * gap(0~5)별 가격 배수를 10배 정수로 표현한 것 — [1, 1.5, 2.2, 3.3, 5, 8].
     *
     * <p>double 로 두면 안 된다. {@code 1000 * 2.2 == 2200.0000000000005} 라서 10단위 올림이 2210 을 만든다. 정수 연산으로
     * 고정해야 프론트/백엔드 결과가 비트 단위로 일치한다.
     */
    private static final int[] MULTIPLIER_X10 = {10, 15, 22, 33, 50, 80};

    /** 가격 반올림 단위 (10단위 올림) */
    private static final int PRICE_UNIT = 10;

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

    /** 등급 차이. 자기 등급 이하의 아이템이면 0 (할증 없음) */
    public static int gap(int userLevel, TitleRarity itemRarity) {
        if (itemRarity == null) {
            return 0;
        }
        return Math.max(0, itemRarity.ordinal() - fromLevel(userLevel).ordinal());
    }

    /** 이 유저가 실제로 결제하는 금액. 결제 검증도 이 값을 쓴다. */
    public static int effectivePrice(Integer basePrice, int userLevel, TitleRarity itemRarity) {
        return applySurcharge(basePrice, gap(userLevel, itemRarity));
    }

    /**
     * 정가 — 최저등급(COMMON) 유저 기준가이자 가능한 최대 할증가.
     *
     * <p>취소선 anchor 로 쓰인다. 레벨업으로 얻은 할인폭이 이 값과의 차이로 드러난다.
     */
    public static int listPrice(Integer basePrice, TitleRarity itemRarity) {
        return applySurcharge(basePrice, itemRarity == null ? 0 : itemRarity.ordinal());
    }

    /**
     * LUT-349: 상점 아이템 구매 잠금 여부.
     *
     * <p>내 등급 이하는 다 열리고, 내 등급 위는 각 등급에서 가격이 가장 낮은 N개만 열린다. 정렬 기준이 곧 해금 기준이므로 rankInRarity 는 반드시
     * <b>같은 희귀도 안에서 가격 오름차순</b> 순번이어야 한다.
     *
     * <p>가격 정렬을 기본가로 하든 할증가로 하든 결과는 같다 — 같은 등급 섹션 안에서는 gap 이 동일해 배수가 같고, 10단위 올림도 단조 증가라 순서가 뒤집히지
     * 않는다.
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

    /**
     * 기본가에 gap 배수를 적용하고 10단위로 올림한다.
     *
     * <p>gap 0 은 할증이 없으므로 기본가를 그대로 둔다. 여기서도 올림하면 5다이아 아이템이 자기 등급에서 10다이아가 된다.
     */
    private static int applySurcharge(Integer basePrice, int gap) {
        int base = basePrice == null ? 0 : basePrice;
        if (gap <= 0 || base <= 0) {
            return base;
        }
        int multiplierX10 = MULTIPLIER_X10[Math.min(gap, MULTIPLIER_X10.length - 1)];
        // base × (multiplierX10 / 10) 을 10단위 올림 → ceil(base × multiplierX10 / 100) × 10
        long scaled = (long) base * multiplierX10;
        long units = (scaled + 99) / 100;
        return Math.toIntExact(units * PRICE_UNIT);
    }
}
