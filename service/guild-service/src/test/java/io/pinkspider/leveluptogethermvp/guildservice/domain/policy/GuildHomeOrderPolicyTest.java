package io.pinkspider.leveluptogethermvp.guildservice.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("GuildHomeOrderPolicy — 길드 홈 목록 순서 (LUT-853)")
class GuildHomeOrderPolicyTest {

    private static final String ME = "me";

    private static GuildOrderKey key(long id, String masterId, Integer level, Integer point) {
        return new GuildOrderKey(id, masterId, level, point);
    }

    private static List<Long> sortedIds(
            Comparator<GuildOrderKey> comparator, GuildOrderKey... keys) {
        List<GuildOrderKey> list = new ArrayList<>(Arrays.asList(keys));
        list.sort(comparator);
        return list.stream().map(GuildOrderKey::id).toList();
    }

    @Nested
    @DisplayName("추천 길드")
    class Recommended {

        @Test
        @DisplayName("최근 활동한 길드가 먼저 오고, 그 안에서 레벨 높은 순이다")
        void activeFirst_thenLevelDesc() {
            GuildRecentActivity activity = new GuildRecentActivity(Set.of(1L), Set.of(2L));

            List<Long> ids =
                    sortedIds(
                            GuildHomeOrderPolicy.recommended(activity),
                            key(3, "a", 9, 100), // 활동 없음 — 레벨이 가장 높아도 뒤
                            key(1, "a", 2, 10), // 방금 대화
                            key(2, "a", 5, 10), // 방금 길드 미션
                            key(4, "a", 1, 0));

            assertThat(ids).containsExactly(2L, 1L, 3L, 4L);
        }

        @Test
        @DisplayName("대화와 미션은 같은 순위다 — 둘 다 있는 길드가 하나만 있는 길드를 앞서지 않는다")
        void chatAndMissionSameRank() {
            GuildRecentActivity activity = new GuildRecentActivity(Set.of(1L, 2L), Set.of(1L, 3L));

            List<Long> ids =
                    sortedIds(
                            GuildHomeOrderPolicy.recommended(activity),
                            key(1, "a", 1, 0), // 대화 + 미션, 레벨 1
                            key(2, "a", 3, 0), // 대화만, 레벨 3
                            key(3, "a", 2, 0)); // 미션만, 레벨 2

            assertThat(ids).containsExactly(2L, 3L, 1L);
        }

        @Test
        @DisplayName("레벨이 같으면 활동 포인트 높은 순, 그것도 같으면 먼저 만든 길드(ID 작은 순)")
        void tieBreak_pointDesc_thenId() {
            List<Long> ids =
                    sortedIds(
                            GuildHomeOrderPolicy.recommended(GuildRecentActivity.none()),
                            key(7, "a", 3, 50),
                            key(5, "a", 3, 80),
                            key(9, "a", 3, 50),
                            key(6, "a", 3, 50));

            assertThat(ids).containsExactly(5L, 6L, 7L, 9L);
        }

        @Test
        @DisplayName("레벨·포인트가 없으면 0 으로 보고, ID 가 없는 값은 맨 뒤로 보낸다")
        void nullSafe() {
            List<GuildOrderKey> list =
                    new ArrayList<>(
                            List.of(
                                    new GuildOrderKey(null, "a", null, null),
                                    key(2, "a", null, null),
                                    key(1, "a", 1, null)));

            list.sort(GuildHomeOrderPolicy.recommended(GuildRecentActivity.none()));

            assertThat(list.stream().map(GuildOrderKey::id).toList()).containsExactly(1L, 2L, null);
        }
    }

    @Nested
    @DisplayName("나의 길드")
    class Mine {

        @Test
        @DisplayName("내가 마스터인 길드가 항상 맨 위 — 활동도 없고 레벨이 낮아도")
        void masterGuildsFirst() {
            GuildRecentActivity activity = new GuildRecentActivity(Set.of(2L), Set.of());

            List<Long> ids =
                    sortedIds(
                            GuildHomeOrderPolicy.mine(ME, activity),
                            key(2, "other", 9, 100), // 최근 활동 + 레벨 9
                            key(1, ME, 1, 0), // 내가 마스터, 활동 없음
                            key(3, "other", 5, 0));

            assertThat(ids).containsExactly(1L, 2L, 3L);
        }

        @Test
        @DisplayName("마스터인 길드가 여러 개면 그 안에서도 최근 활동 → 레벨 순이다")
        void mastersOrderedByActivityThenLevel() {
            GuildRecentActivity activity = new GuildRecentActivity(Set.of(), Set.of(3L));

            List<Long> ids =
                    sortedIds(
                            GuildHomeOrderPolicy.mine(ME, activity),
                            key(1, ME, 7, 0),
                            key(3, ME, 2, 0), // 방금 길드 미션
                            key(2, "other", 9, 0));

            assertThat(ids).containsExactly(3L, 1L, 2L);
        }

        @Test
        @DisplayName("유저를 모르면(null) 마스터 우선 없이 추천 길드와 같은 순서다")
        void nullUser_sameAsRecommended() {
            List<Long> ids =
                    sortedIds(
                            GuildHomeOrderPolicy.mine(null, GuildRecentActivity.none()),
                            key(1, null, 1, 0),
                            key(2, "a", 4, 0));

            assertThat(ids).containsExactly(2L, 1L);
        }
    }

    @Nested
    @DisplayName("GuildRecentActivity")
    class RecentActivity {

        @Test
        @DisplayName("대화·미션 여부를 따로 알려 주고, 둘 중 하나라도 있으면 활동한 길드다")
        void flags() {
            GuildRecentActivity activity = new GuildRecentActivity(Set.of(1L), Set.of(2L));

            assertThat(activity.hasRecentChat(1L)).isTrue();
            assertThat(activity.hasRecentMission(1L)).isFalse();
            assertThat(activity.hasRecentMission(2L)).isTrue();
            assertThat(activity.hasRecentChat(2L)).isFalse();
            assertThat(activity.isActive(1L)).isTrue();
            assertThat(activity.isActive(2L)).isTrue();
            assertThat(activity.isActive(3L)).isFalse();
        }

        @Test
        @DisplayName("집합이 null 이면 빈 집합으로, 길드 ID 가 null 이면 활동 없음으로 본다")
        void nullSafe() {
            GuildRecentActivity activity = new GuildRecentActivity(null, null);

            assertThat(activity.chatGuildIds()).isEmpty();
            assertThat(activity.missionGuildIds()).isEmpty();
            assertThat(activity.isActive(null)).isFalse();
            assertThat(new GuildRecentActivity(Set.of(1L), Set.of(1L)).hasRecentChat(null))
                    .isFalse();
            assertThat(new GuildRecentActivity(Set.of(1L), Set.of(1L)).hasRecentMission(null))
                    .isFalse();
            assertThat(GuildRecentActivity.none().isActive(1L)).isFalse();
        }
    }
}
