package io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.policy;

import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.enums.RetentionVerdictCode;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.enums.VerdictComparison;

/**
 * LUT-779: 첫날 행동별 D7 리텐션 판정. 비교는 두 가지 — ① SOLO vs NOT_COMPLETED(혼자 써도 남는가) ② SOCIAL vs SOLO(함께하면 더
 * 남는가). 차이는 %p(뒤 그룹 − 앞 그룹), 유의성은 두 비율 z-검정 95%(양측). 기준값은 여기 상수로만 바꾼다.
 */
public final class RetentionVerdictPolicy {

    /** 의미 있는 차이 (%p) */
    public static final double MIN_DIFF_PP = 5.0;

    /** 비교 그룹마다 필요한 최소 판정 가능 인원 — 미만이면 NOT_ENOUGH_DATA */
    public static final int MIN_GROUP_USERS = 30;

    /** 95% 양측 임계값 */
    public static final double Z_CRITICAL = 1.96;

    /** 비율 계산의 부동소수점 오차(5.0 이 4.9999999 로 나오는 것)를 경계에서 흡수 */
    private static final double EPSILON = 1e-9;

    private RetentionVerdictPolicy() {}

    /** 그룹의 D7 — 판정 가능 인원(users) 중 돌아온 사람(retained) */
    public record Counts(int retained, int users) {
        double ratePercent() {
            return users == 0 ? 0.0 : retained * 100.0 / users;
        }
    }

    /** 판정 하나. diffPp 는 반올림 전 값(%p) — 두 그룹 중 하나라도 0명이면 null */
    public record Result(
            VerdictComparison comparison,
            RetentionVerdictCode code,
            Double diffPp,
            boolean significant) {}

    /** ① 혼자 써도 남는가 */
    public static Result solo(Counts notCompleted, Counts solo) {
        return judge(VerdictComparison.SOLO_VS_NOT_COMPLETED, notCompleted, solo);
    }

    /** ② 함께하면 더 남는가 */
    public static Result social(Counts solo, Counts social) {
        return judge(VerdictComparison.SOCIAL_VS_SOLO, solo, social);
    }

    private static Result judge(VerdictComparison comparison, Counts base, Counts target) {
        Double diff =
                base.users() == 0 || target.users() == 0
                        ? null
                        : target.ratePercent() - base.ratePercent();
        boolean significant = isSignificant(base, target);
        if (base.users() < MIN_GROUP_USERS || target.users() < MIN_GROUP_USERS) {
            return new Result(comparison, RetentionVerdictCode.NOT_ENOUGH_DATA, diff, significant);
        }
        return new Result(comparison, code(comparison, diff, significant), diff, significant);
    }

    private static RetentionVerdictCode code(
            VerdictComparison comparison, double diff, boolean significant) {
        if (comparison == VerdictComparison.SOLO_VS_NOT_COMPLETED) {
            return diff + EPSILON >= MIN_DIFF_PP && significant
                    ? RetentionVerdictCode.SOLO_RETAINS
                    : RetentionVerdictCode.SOLO_WEAK;
        }
        if (diff + EPSILON >= MIN_DIFF_PP && significant) {
            return RetentionVerdictCode.SOCIAL_BOOSTS;
        }
        if (diff - EPSILON <= -MIN_DIFF_PP && significant) {
            return RetentionVerdictCode.SOCIAL_HURTS;
        }
        return RetentionVerdictCode.SOCIAL_NO_DIFF;
    }

    /** 두 비율 z-검정(합동 비율) — |z| ≥ 1.96. 표준오차가 0 이면(둘 다 0% 또는 100%) 유의하지 않음 */
    static boolean isSignificant(Counts a, Counts b) {
        if (a.users() == 0 || b.users() == 0) {
            return false;
        }
        double pooled = (double) (a.retained() + b.retained()) / (a.users() + b.users());
        double se = Math.sqrt(pooled * (1 - pooled) * (1.0 / a.users() + 1.0 / b.users()));
        if (se == 0) {
            return false;
        }
        double z = ((double) b.retained() / b.users() - (double) a.retained() / a.users()) / se;
        return Math.abs(z) >= Z_CRITICAL;
    }
}
