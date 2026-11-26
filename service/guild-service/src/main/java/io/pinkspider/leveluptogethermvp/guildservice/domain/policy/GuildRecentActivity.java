package io.pinkspider.leveluptogethermvp.guildservice.domain.policy;

import java.util.Set;

/**
 * LUT-853: 길드 홈 목록의 "최근 활동" — 방금 단체채팅 대화가 있었던 길드와 방금 길드 미션(시작·완료)이 있었던 길드의 ID.
 *
 * <p>길드 서비스는 채팅·미션 서비스를 모른다. 두 집합은 BFF 가 각 서비스에서 모아 넘긴다. 활동은 여부만 본다 — 시각으로 줄 세우지 않고, 순서에서는 대화와 미션이
 * 같은 순위다({@link GuildHomeOrderPolicy}).
 */
public record GuildRecentActivity(Set<Long> chatGuildIds, Set<Long> missionGuildIds) {

    public GuildRecentActivity {
        chatGuildIds = chatGuildIds == null ? Set.of() : Set.copyOf(chatGuildIds);
        missionGuildIds = missionGuildIds == null ? Set.of() : Set.copyOf(missionGuildIds);
    }

    /** 최근 활동을 모른다(조회 실패·비활성) — 모든 길드가 활동 없음 */
    public static GuildRecentActivity none() {
        return new GuildRecentActivity(Set.of(), Set.of());
    }

    public boolean hasRecentChat(Long guildId) {
        return guildId != null && chatGuildIds.contains(guildId);
    }

    public boolean hasRecentMission(Long guildId) {
        return guildId != null && missionGuildIds.contains(guildId);
    }

    /** 순서에서 "최근 활동한 길드" 인가 — 대화·미션 중 하나라도 있었으면 */
    public boolean isActive(Long guildId) {
        return hasRecentChat(guildId) || hasRecentMission(guildId);
    }
}
