package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPlan;
import java.util.Map;

/**
 * 스토어 상품 → 내부 플랜 매핑 (LUT-450)
 *
 * <p><b>이 4줄이 두 스토어의 구조 차이를 흡수하는 유일한 지점이다.</b> 이 뒤의 모든 소비자(RN·웹·분석·CS)는
 * {@code MONTHLY}/{@code ANNUAL}만 본다.
 *
 * <table border="1">
 *   <tr><th>platform</th><th>product_id</th><th>base_plan_id</th><th>→ 내부 플랜</th></tr>
 *   <tr><td>ios</td><td>membership_1m</td><td>—</td><td>MONTHLY</td></tr>
 *   <tr><td>ios</td><td>membership_1y</td><td>—</td><td>ANNUAL</td></tr>
 *   <tr><td>android</td><td>membership</td><td>1m</td><td>MONTHLY</td></tr>
 *   <tr><td>android</td><td>membership</td><td>1y</td><td>ANNUAL</td></tr>
 * </table>
 *
 * <p>매핑 키는 반드시 {@code (platform, product_id, base_plan_id)} 3개 — iOS는 상품 ID만으로 플랜이
 * 결정되지만 <b>Android는 두 플랜의 상품 ID가 똑같이 {@code membership}</b>이라 base plan을 봐야 구분된다.
 * {@code product_id} 하나만 키로 잡으면 Android 연간 구독자가 월간으로 기록된다.
 */
public final class SubscriptionPlanMapping {

    public static final String PLATFORM_IOS = "ios";
    public static final String PLATFORM_ANDROID = "android";

    // dev_* 는 LUT DEV(iOS dev 앱) 전용 상품 — Apple 은 팀 전체 상품 ID 유일 제약이라
    // dev 앱에 같은 ID 를 재등록할 수 없어 접두사로 분리한다 (dev_diamond_box_* 전례).
    // Android 는 앱별 상품 네임스페이스라 Play LUT DEV 에 membership 을 그대로 등록 — 행 추가 불필요.
    // prod 에 이 매핑이 있어도 무해: dev 번들 영수증은 prod 자격증명으로 검증되지 않는다.
    private static final Map<String, SubscriptionPlan> MAPPING =
            Map.of(
                    key(PLATFORM_IOS, "membership_1m", null), SubscriptionPlan.MONTHLY,
                    key(PLATFORM_IOS, "membership_1y", null), SubscriptionPlan.ANNUAL,
                    key(PLATFORM_IOS, "dev_membership_1m", null), SubscriptionPlan.MONTHLY,
                    key(PLATFORM_IOS, "dev_membership_1y", null), SubscriptionPlan.ANNUAL,
                    key(PLATFORM_ANDROID, "membership", "1m"), SubscriptionPlan.MONTHLY,
                    key(PLATFORM_ANDROID, "membership", "1y"), SubscriptionPlan.ANNUAL);

    private SubscriptionPlanMapping() {}

    /**
     * 3키 매핑으로 내부 플랜을 결정한다.
     *
     * @param platform ios | android
     * @param productId 스토어 상품 ID
     * @param basePlanId Android base plan ID (iOS는 null)
     * @throws CustomException 매핑에 없는 조합 (120801)
     */
    public static SubscriptionPlan resolve(String platform, String productId, String basePlanId) {
        SubscriptionPlan plan = MAPPING.get(key(platform, productId, basePlanId));
        if (plan == null) {
            throw new CustomException("120801", "error.subscription.unknown_product");
        }
        return plan;
    }

    private static String key(String platform, String productId, String basePlanId) {
        return (platform == null ? "" : platform.toLowerCase())
                + "|"
                + productId
                + "|"
                + (basePlanId == null ? "" : basePlanId);
    }
}
