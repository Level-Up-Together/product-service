package io.pinkspider.leveluptogethermvp.guildservice.domain.policy;

import java.util.Comparator;

/**
 * LUT-853: 길드 홈 "나의 길드"·"추천 길드" 의 순서 — 두 목록이 같은 규칙을 쓴다.
 *
 * <ol>
 *   <li>(나의 길드만) 내가 마스터인 길드
 *   <li>최근 활동한 길드 — 방금 대화 또는 방금 길드 미션. 여부만 본다(활동 시각으로 줄 세우지 않고, 대화와 미션은 같은 순위)
 *   <li>레벨 높은 순
 *   <li>레벨이 같으면 누적 활동 포인트 높은 순(길드 랭킹과 같은 기준) → 먼저 만든 길드(ID 작은 순)
 * </ol>
 *
 * 순서는 서버가 정해 내려 준다 — 프론트는 다시 정렬하지 않는다.
 */
public final class GuildHomeOrderPolicy {

    private static final Comparator<GuildOrderKey> BY_LEVEL_DESC =
            Comparator.comparingInt((GuildOrderKey key) -> orZero(key.currentLevel())).reversed();
    private static final Comparator<GuildOrderKey> BY_POINT_DESC =
            Comparator.comparingInt((GuildOrderKey key) -> orZero(key.totalPoint())).reversed();
    private static final Comparator<GuildOrderKey> BY_ID =
            Comparator.comparing(
                    GuildOrderKey::id, Comparator.nullsLast(Comparator.naturalOrder()));

    private GuildHomeOrderPolicy() {}

    /** 추천 길드: 최근 활동 → 레벨 → 활동 포인트 → ID */
    public static Comparator<GuildOrderKey> recommended(GuildRecentActivity activity) {
        return Comparator.comparing((GuildOrderKey key) -> !activity.isActive(key.id()))
                .thenComparing(BY_LEVEL_DESC)
                .thenComparing(BY_POINT_DESC)
                .thenComparing(BY_ID);
    }

    /** 나의 길드: 내가 마스터인 길드 → 그 아래는 추천 길드와 같은 순서 */
    public static Comparator<GuildOrderKey> mine(String userId, GuildRecentActivity activity) {
        return Comparator.comparing((GuildOrderKey key) -> !isMaster(key, userId))
                .thenComparing(recommended(activity));
    }

    private static boolean isMaster(GuildOrderKey key, String userId) {
        return userId != null && userId.equals(key.masterId());
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
