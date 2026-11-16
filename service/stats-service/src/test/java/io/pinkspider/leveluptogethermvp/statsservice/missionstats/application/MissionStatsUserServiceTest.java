package io.pinkspider.leveluptogethermvp.statsservice.missionstats.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.statsservice.activity.config.UserActivityProperties;
import io.pinkspider.leveluptogethermvp.statsservice.api.exception.StatsInvalidParamException;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.UserCompletionStat;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.UserNickname;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source.GamificationSourceReader;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source.MissionSourceReader;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source.UserSourceReader;
import io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.dto.MissionStatsUsersResponse;
import io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.dto.MissionStatsUsersResponse.Bucket;
import io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.dto.MissionStatsUsersResponse.UserRow;
import io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.enums.CompletionBucket;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("MissionStatsUserService 테스트 (LUT-790)")
class MissionStatsUserServiceTest {

    private static final LocalDate FROM = LocalDate.of(2026, 11, 1);
    private static final LocalDate TO = LocalDate.of(2026, 11, 30);

    /** 2026-11-09 23:30 KST 완료 = UTC 14:30 */
    private static final LocalDateTime LATE = LocalDateTime.of(2026, 11, 9, 14, 30);

    /** 2026-11-10 00:30 KST 완료 = UTC 11-09 15:30 — KST 로는 다음 날 */
    private static final LocalDateTime AFTER_MIDNIGHT = LocalDateTime.of(2026, 11, 9, 15, 30);

    @Mock private MissionSourceReader missionReader;
    @Mock private UserSourceReader userReader;
    @Mock private GamificationSourceReader gamificationReader;

    private MissionStatsUserService service;

    @BeforeEach
    void setUp() {
        service =
                new MissionStatsUserService(
                        missionReader,
                        userReader,
                        gamificationReader,
                        new UserActivityProperties());
    }

    private void stubStats(UserCompletionStat... stats) {
        when(missionReader.findUserCompletions(any(), any(), any())).thenReturn(List.of(stats));
    }

    @Test
    @DisplayName("기본 — 완료 수 내림차순, 같으면 최근 완료 순, 닉네임·레벨은 그 페이지만 읽는다")
    void defaultOrderReadsPageOnly() {
        stubStats(
                new UserCompletionStat("u1", 3, 0, LATE),
                new UserCompletionStat("u2", 12, 340, AFTER_MIDNIGHT),
                new UserCompletionStat("u3", 3, 30, AFTER_MIDNIGHT),
                new UserCompletionStat("u4", 1, 0, null));
        when(userReader.findNicknames(anyCollection()))
                .thenReturn(
                        Map.of(
                                "u2", new UserNickname("Rumi", false),
                                "u3", new UserNickname("탈퇴한 사용자", true)));
        when(gamificationReader.findLevels(anyCollection())).thenReturn(Map.of("u2", 12));

        MissionStatsUsersResponse r = service.users(FROM, TO, 0, 2, null, " ");

        assertThat(r.sort()).isEqualTo("completions,desc");
        assertThat(r.keyword()).isNull();
        assertThat(r.totalUsers()).isEqualTo(4);
        assertThat(r.totalPages()).isEqualTo(2);
        assertThat(r.users())
                .containsExactly(
                        new UserRow("u2", "Rumi", 12, false, 12, 340, LocalDate.of(2026, 11, 10)),
                        new UserRow(
                                "u3", "탈퇴한 사용자", null, true, 3, 30, LocalDate.of(2026, 11, 10)));
        assertThat(r.distribution())
                .containsExactly(
                        new Bucket(CompletionBucket.ONE, 1, 1, 1),
                        new Bucket(CompletionBucket.TWO_TO_FOUR, 2, 4, 2),
                        new Bucket(CompletionBucket.FIVE_TO_NINE, 5, 9, 0),
                        new Bucket(CompletionBucket.TEN_OR_MORE, 10, null, 1));
        assertLookedUp(userReader, "u2", "u3");
    }

    @Test
    @DisplayName("닉네임 검색 — 전원 닉네임을 읽어 일부(대소문자 무시)·유저 ID 로 거른다, 분포는 전체 그대로")
    void keywordFilters() {
        stubStats(
                new UserCompletionStat("u1", 3, 0, LATE),
                new UserCompletionStat("u2", 12, 0, LATE),
                new UserCompletionStat("u3", 5, 0, LATE),
                new UserCompletionStat("u4", 2, 0, LATE));
        Map<String, UserNickname> nicknames = new HashMap<>();
        nicknames.put("u1", new UserNickname("rumi_day", false));
        nicknames.put("u2", new UserNickname("Rumi", false));
        nicknames.put("u3", new UserNickname(null, false));
        when(userReader.findNicknames(anyCollection())).thenReturn(nicknames);
        when(gamificationReader.findLevels(anyCollection())).thenReturn(Map.of());

        MissionStatsUsersResponse r = service.users(FROM, TO, 0, 20, null, " RUMI ");

        assertThat(r.keyword()).isEqualTo("RUMI");
        assertThat(r.users()).extracting(UserRow::userId).containsExactly("u2", "u1");
        assertThat(r.distribution()).extracting(Bucket::users).containsExactly(0, 2, 1, 1);
        assertThat(service.users(FROM, TO, 0, 20, null, "u4").users())
                .extracting(UserRow::userId)
                .containsExactly("u4");
    }

    @Test
    @DisplayName("닉네임 정렬 — 대소문자 무시 오름차순, 닉네임 없는 유저는 맨 뒤")
    void nicknameSort() {
        stubStats(
                new UserCompletionStat("u1", 1, 0, LATE),
                new UserCompletionStat("u2", 1, 0, LATE),
                new UserCompletionStat("u3", 1, 0, LATE));
        when(userReader.findNicknames(anyCollection()))
                .thenReturn(
                        Map.of(
                                "u1", new UserNickname("bora", false),
                                "u3", new UserNickname("Ari", false)));
        when(gamificationReader.findLevels(anyCollection())).thenReturn(Map.of());

        assertThat(service.users(FROM, TO, 0, 20, "nickname", null).users())
                .extracting(UserRow::userId)
                .containsExactly("u3", "u1", "u2");
        assertThat(service.users(FROM, TO, 0, 20, "nickname,desc", null).users())
                .extracting(UserRow::userId)
                .containsExactly("u1", "u3", "u2");
    }

    @Test
    @DisplayName("레벨 정렬 — 전원 레벨을 읽는다, 레벨 없는 유저는 맨 뒤")
    void levelSort() {
        stubStats(
                new UserCompletionStat("u1", 1, 0, LATE),
                new UserCompletionStat("u2", 1, 0, LATE),
                new UserCompletionStat("u3", 1, 0, LATE));
        when(userReader.findNicknames(anyCollection())).thenReturn(Map.of());
        when(gamificationReader.findLevels(anyCollection())).thenReturn(Map.of("u1", 3, "u3", 40));

        MissionStatsUsersResponse r = service.users(FROM, TO, 0, 1, "level", null);

        assertThat(r.users()).extracting(UserRow::userId).containsExactly("u3");
        assertThat(r.users().get(0).level()).isEqualTo(40);
        assertLookedUp(gamificationReader, "u1", "u2", "u3");
        assertThat(service.users(FROM, TO, 0, 20, "level,asc", null).users())
                .extracting(UserRow::userId)
                .containsExactly("u1", "u3", "u2");
    }

    @Test
    @DisplayName("타이머 분·마지막 완료 정렬 — 오름·내림")
    void numericSorts() {
        stubStats(
                new UserCompletionStat("u1", 1, 90, LATE),
                new UserCompletionStat("u2", 1, 30, AFTER_MIDNIGHT),
                new UserCompletionStat("u3", 1, 60, null));
        when(userReader.findNicknames(anyCollection())).thenReturn(Map.of());
        when(gamificationReader.findLevels(anyCollection())).thenReturn(Map.of());

        assertThat(service.users(FROM, TO, 0, 20, "timer_minutes", null).users())
                .extracting(UserRow::userId)
                .containsExactly("u1", "u3", "u2");
        assertThat(service.users(FROM, TO, 0, 20, "timer_minutes,asc", null).users())
                .extracting(UserRow::userId)
                .containsExactly("u2", "u3", "u1");
        assertThat(service.users(FROM, TO, 0, 20, "last_completed_at,asc", null).users())
                .extracting(UserRow::userId)
                .containsExactly("u1", "u2", "u3");
        assertThat(service.users(FROM, TO, 0, 20, "completions,asc", null).users())
                .extracting(UserRow::userId)
                .containsExactly("u2", "u1", "u3");
    }

    @Test
    @DisplayName("페이지 — 범위 밖 페이지는 빈 목록, 500명 넘게 읽을 때는 나눠 읽는다")
    void pagingAndChunks() {
        UserCompletionStat[] stats =
                IntStream.range(0, 501)
                        .mapToObj(i -> new UserCompletionStat("u" + i, 1, 0, LATE))
                        .toArray(UserCompletionStat[]::new);
        stubStats(stats);
        when(userReader.findNicknames(anyCollection())).thenReturn(Map.of());

        MissionStatsUsersResponse r = service.users(FROM, TO, 6, 100, "nickname", null);

        assertThat(r.totalUsers()).isEqualTo(501);
        assertThat(r.totalPages()).isEqualTo(6);
        assertThat(r.users()).isEmpty();
        verify(userReader, times(2)).findNicknames(anyCollection());
        verifyNoInteractions(gamificationReader);
    }

    @Test
    @DisplayName("파라미터 오류는 읽기 전에 400 100101 — page 음수, size 0·101, 정렬 허용값 밖")
    void rejectsInvalidParams() {
        assertThatThrownBy(() -> service.users(FROM, TO, -1, 20, null, null))
                .isInstanceOf(StatsInvalidParamException.class)
                .hasFieldOrPropertyWithValue("name", "page");
        assertThatThrownBy(() -> service.users(FROM, TO, 0, 0, null, null))
                .hasFieldOrPropertyWithValue("name", "size");
        assertThatThrownBy(() -> service.users(FROM, TO, 0, 101, null, null))
                .hasFieldOrPropertyWithValue("name", "size");
        assertThatThrownBy(() -> service.users(FROM, TO, 0, 20, "email", null))
                .hasFieldOrPropertyWithValue("name", "sort");
        verifyNoInteractions(missionReader, userReader, gamificationReader);
    }

    @SuppressWarnings("unchecked")
    private static void assertLookedUp(Object reader, String... ids) {
        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        if (reader instanceof UserSourceReader user) {
            verify(user).findNicknames(captor.capture());
        } else {
            verify((GamificationSourceReader) reader).findLevels(captor.capture());
        }
        assertThat(captor.getValue()).containsExactlyInAnyOrder(ids);
    }
}
