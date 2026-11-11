package io.pinkspider.leveluptogethermvp.statsservice.firstday.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.statsservice.activity.config.UserActivityProperties;
import io.pinkspider.leveluptogethermvp.statsservice.activity.domain.entity.UserDailyActivity;
import io.pinkspider.leveluptogethermvp.statsservice.activity.infrastructure.UserDailyActivityRepository;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.dto.FirstDayRetentionRunResponse;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.entity.FirstDayRetention;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.enums.FirstDayGroup;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.enums.SignupPlatform;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.infrastructure.FirstDayRetentionRepository;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.ExcludedUsers;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.SignupRecord;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.UserEvent;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source.ChatSourceReader;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source.FeedSourceReader;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source.GuildSourceReader;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source.MissionSourceReader;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source.TowerSourceReader;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source.UserSourceReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("FirstDayRetentionService — 첫날 행동 분류·재방문 채우기 (LUT-779)")
class FirstDayRetentionServiceTest {

    private static final LocalDate DATA_START = LocalDate.of(2026, 10, 1);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);

    /** 2026-10-11 15:00 UTC = 10-12 00:00 KST */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 11, 15, 50);

    @Mock FirstDayRetentionRepository repository;
    @Mock UserDailyActivityRepository activityRepository;
    @Mock UserActivityProperties activityProperties;
    @Mock UserSourceReader userReader;
    @Mock MissionSourceReader missionReader;
    @Mock GuildSourceReader guildReader;
    @Mock FeedSourceReader feedReader;
    @Mock ChatSourceReader chatReader;
    @Mock TowerSourceReader towerReader;
    @InjectMocks FirstDayRetentionService service;

    @BeforeEach
    void setUp() {
        lenient().when(activityProperties.getExcludedUserIds()).thenReturn(Set.of("staff"));
        lenient().when(repository.findOpen(any())).thenReturn(List.of());
    }

    /** 상호작용 원천 기본값(없음) — 테스트가 일부를 덮어쓴다 */
    private void noInteractions() {
        lenient()
                .when(userReader.findFriendshipEvents(anyCollection(), any(), any()))
                .thenReturn(List.of());
        lenient().when(guildReader.findJoins(anyCollection(), any(), any())).thenReturn(List.of());
        lenient()
                .when(feedReader.findLikesAndComments(anyCollection(), any(), any()))
                .thenReturn(List.of());
        lenient()
                .when(chatReader.findDirectMessages(anyCollection(), any(), any()))
                .thenReturn(List.of());
        lenient()
                .when(towerReader.findChatsAndCheers(anyCollection(), any(), any()))
                .thenReturn(List.of());
    }

    @Test
    @DisplayName("접속 기록이 없으면 아무것도 하지 않는다")
    void noActivityData() {
        when(activityRepository.findMinActivityDate()).thenReturn(Optional.empty());

        FirstDayRetentionRunResponse result = service.runDaily(TODAY, NOW);

        assertThat(result).isEqualTo(new FirstDayRetentionRunResponse(null, 0, 0));
        verifyNoInteractions(userReader, repository);
    }

    @Nested
    @DisplayName("분류")
    class Classify {

        @BeforeEach
        void setUp() {
            when(activityRepository.findMinActivityDate()).thenReturn(Optional.of(DATA_START));
        }

        @Test
        @DisplayName("첫날 완료·상호작용으로 그룹을 정하고, 이미 있는 유저는 건너뛴다")
        void classifies() {
            LocalDateTime social = LocalDateTime.of(2026, 10, 5, 14, 0); // KST 10-05 23:00
            LocalDateTime solo = LocalDateTime.of(2026, 10, 5, 15, 30); // KST 10-06 00:30
            LocalDateTime none = LocalDateTime.of(2026, 10, 6, 1, 0);
            when(repository.findMaxSignupDate()).thenReturn(Optional.empty());
            when(userReader.findSignups(any(), any(), any()))
                    .thenReturn(
                            List.of(
                                    new SignupRecord("social", social, "ios"),
                                    new SignupRecord("solo", solo, "android"),
                                    new SignupRecord("none", none, null),
                                    new SignupRecord("old", none, "web")));
            when(repository.findExistingUserIds(anyCollection())).thenReturn(List.of("old"));
            when(missionReader.findCompletions(anyCollection(), any(), any()))
                    .thenReturn(
                            List.of(
                                    new UserEvent("social", social.plusHours(2)),
                                    new UserEvent("solo", solo.plusHours(23)),
                                    // 24시간 뒤 완료는 첫날이 아니다
                                    new UserEvent("none", none.plusHours(24))));
            noInteractions();
            when(userReader.findFriendshipEvents(anyCollection(), any(), any()))
                    .thenReturn(List.of(new UserEvent("social", social.plusHours(1))));
            // 완료하지 않은 유저의 상호작용은 그룹을 바꾸지 않는다
            when(feedReader.findLikesAndComments(anyCollection(), any(), any()))
                    .thenReturn(List.of(new UserEvent("none", none.plusHours(1))));

            FirstDayRetentionRunResponse result = service.runDaily(TODAY, NOW);

            assertThat(result.classified()).isEqualTo(3);
            assertThat(result.dataStartDate()).isEqualTo(DATA_START);
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<FirstDayRetention>> saved = ArgumentCaptor.forClass(List.class);
            verify(repository).saveAll(saved.capture());
            assertThat(saved.getValue())
                    .extracting(
                            FirstDayRetention::getUserId,
                            FirstDayRetention::getFirstDayGroup,
                            FirstDayRetention::getPlatform,
                            FirstDayRetention::getSignupDate,
                            FirstDayRetention::isFirstDayInteracted)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(
                                    "social",
                                    FirstDayGroup.SOCIAL,
                                    SignupPlatform.IOS,
                                    LocalDate.of(2026, 10, 5),
                                    true),
                            org.assertj.core.groups.Tuple.tuple(
                                    "solo",
                                    FirstDayGroup.SOLO,
                                    SignupPlatform.ANDROID,
                                    LocalDate.of(2026, 10, 6),
                                    false),
                            org.assertj.core.groups.Tuple.tuple(
                                    "none",
                                    FirstDayGroup.NOT_COMPLETED,
                                    SignupPlatform.UNKNOWN,
                                    LocalDate.of(2026, 10, 6),
                                    true));
            // 원천 조회 창 = 가장 이른 가입 ~ 가장 늦은 가입 + 24h
            verify(missionReader)
                    .findCompletions(anyCollection(), eq(social), eq(none.plusHours(24)));
        }

        @Test
        @DisplayName("가입자는 접속 기록 시작일부터, 지금 − 24시간까지 — 테스트·제외 유저를 뺀다")
        void signupWindowFromDataStart() {
            when(repository.findMaxSignupDate()).thenReturn(Optional.empty());
            when(userReader.findSignups(any(), any(), any())).thenReturn(List.of());

            service.runDaily(TODAY, NOW);

            ArgumentCaptor<ExcludedUsers> excluded = ArgumentCaptor.forClass(ExcludedUsers.class);
            verify(userReader)
                    .findSignups(
                            eq(LocalDateTime.of(2026, 9, 30, 15, 0)),
                            eq(NOW.minusHours(24)),
                            excluded.capture());
            assertThat(excluded.getValue().contains("staff")).isTrue();
            verify(repository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("이미 분류한 마지막 가입일 − 2일부터 다시 읽는다")
        void rescanFromLastClassified() {
            when(repository.findMaxSignupDate()).thenReturn(Optional.of(LocalDate.of(2026, 10, 9)));
            when(userReader.findSignups(any(), any(), any())).thenReturn(List.of());

            service.runDaily(TODAY, NOW);

            verify(userReader)
                    .findSignups(
                            eq(LocalDateTime.of(2026, 10, 6, 15, 0)),
                            eq(NOW.minusHours(24)),
                            any());
        }

        @Test
        @DisplayName("다시 읽을 날이 기록 시작일보다 앞이면 시작일부터")
        void rescanNotBeforeDataStart() {
            when(repository.findMaxSignupDate()).thenReturn(Optional.of(DATA_START.plusDays(1)));
            when(userReader.findSignups(any(), any(), any())).thenReturn(List.of());

            service.runDaily(TODAY, NOW);

            verify(userReader).findSignups(eq(LocalDateTime.of(2026, 9, 30, 15, 0)), any(), any());
        }

        @Test
        @DisplayName("아직 24시간이 지난 가입자가 있을 수 없으면 읽지 않는다")
        void tooEarly() {
            when(repository.findMaxSignupDate()).thenReturn(Optional.empty());

            FirstDayRetentionRunResponse result =
                    service.runDaily(DATA_START, LocalDateTime.of(2026, 9, 30, 20, 0));

            assertThat(result.classified()).isZero();
            verify(userReader, never()).findSignups(any(), any(), any());
        }

        @Test
        @DisplayName("500명씩 나눠 조회한다")
        void chunks() {
            List<SignupRecord> signups = new ArrayList<>();
            IntStream.range(0, 501)
                    .forEach(
                            i ->
                                    signups.add(
                                            new SignupRecord(
                                                    "u" + i,
                                                    LocalDateTime.of(2026, 10, 5, 0, 0),
                                                    "web")));
            when(repository.findMaxSignupDate()).thenReturn(Optional.empty());
            when(userReader.findSignups(any(), any(), any())).thenReturn(signups);
            when(repository.findExistingUserIds(anyCollection()))
                    .thenAnswer(
                            inv ->
                                    new ArrayList<>(
                                            (java.util.Collection<String>) inv.getArgument(0)));

            FirstDayRetentionRunResponse result = service.runDaily(TODAY, NOW);

            assertThat(result.classified()).isZero();
            verify(repository, times(2)).findExistingUserIds(anyCollection());
            verify(repository, never()).saveAll(anyList());
        }
    }

    @Test
    @DisplayName("미확정 행의 D1·D7·D30 을 어제까지의 접속으로 채운다")
    void fillsRetention() {
        when(activityRepository.findMinActivityDate()).thenReturn(Optional.of(DATA_START));
        when(repository.findMaxSignupDate()).thenReturn(Optional.of(TODAY));
        LocalDate signup = LocalDate.of(2026, 10, 3);
        FirstDayRetention open =
                FirstDayRetention.classify(
                        "u1",
                        LocalDateTime.of(2026, 10, 2, 16, 0),
                        signup,
                        SignupPlatform.WEB,
                        true,
                        false);
        FirstDayRetention untouched =
                FirstDayRetention.classify(
                        "u2",
                        LocalDateTime.of(2026, 10, 2, 16, 0),
                        signup,
                        SignupPlatform.WEB,
                        false,
                        false);
        when(repository.findOpen(TODAY.minusDays(2))).thenReturn(List.of(open, untouched));
        when(activityRepository.findAllByActivityDateBetween(
                        signup.plusDays(1), TODAY.minusDays(1)))
                .thenReturn(
                        List.of(
                                UserDailyActivity.create("u1", signup.plusDays(1), List.of("ios")),
                                UserDailyActivity.create("u1", signup.plusDays(7), List.of("ios")),
                                UserDailyActivity.create(
                                        "other", signup.plusDays(1), List.of("web"))));

        FirstDayRetentionRunResponse result = service.runDaily(TODAY, NOW);

        assertThat(result.retentionUpdated()).isEqualTo(2);
        assertThat(open.getD1()).isTrue();
        assertThat(open.getD7()).isTrue();
        assertThat(open.getD30()).isNull();
        assertThat(untouched.getD1()).isFalse();
        assertThat(untouched.getD7()).isFalse();
    }

    @Test
    @DisplayName("rebuildAll 은 표를 비운 뒤 처음부터 다시 만든다")
    void rebuildAll() {
        when(activityRepository.findMinActivityDate()).thenReturn(Optional.of(DATA_START));
        when(repository.findMaxSignupDate()).thenReturn(Optional.empty());
        when(userReader.findSignups(any(), any(), any())).thenReturn(List.of());

        service.rebuildAll(TODAY, NOW);

        InOrder order = inOrder(repository, userReader);
        order.verify(repository).deleteAllInBatch();
        order.verify(userReader).findSignups(any(), any(), any());
    }

    @Test
    @DisplayName("UTC 가입 시각 → KST 가입일")
    void kstDate() {
        assertThat(FirstDayRetentionService.toKstDate(LocalDateTime.of(2026, 10, 5, 14, 59)))
                .isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(FirstDayRetentionService.toKstDate(LocalDateTime.of(2026, 10, 5, 15, 0)))
                .isEqualTo(LocalDate.of(2026, 10, 6));
    }
}
