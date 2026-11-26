package io.pinkspider.leveluptogethermvp.missionservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.pinkspider.global.enums.ExpSourceType;
import io.pinkspider.global.facade.GamificationQueryFacade;
import io.pinkspider.global.facade.GuildQueryFacade;
import io.pinkspider.global.facade.dto.UserExperienceDto;
import io.pinkspider.leveluptogethermvp.missionservice.api.exception.MissionExecutionCancelException;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionExecutionCancelTxService.CancelledExecution;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionExecutionCancelResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.policy.MissionExpPolicy;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** LUT-860: 완료한 미션 수행 취소 — 커밋 뒤 되돌리기(마일스톤·길드·통계·사진 파일)와 응답 조립 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MissionExecutionCancelService (LUT-860 완료한 수행 취소 — 커밋 뒤 단계)")
class MissionExecutionCancelServiceTest {

    private static final String USER_ID = "user-860";
    private static final Long MISSION_ID = 10L;
    private static final Long RECORD_ID = 30L;
    private static final LocalDate EXECUTION_DATE = LocalDate.of(2026, 11, 26);

    /** 2026-11-26 01:30 KST = 2026-11-25 11:30 EST(뉴욕) */
    private static final LocalDateTime COMPLETED_AT_UTC = LocalDateTime.of(2026, 11, 25, 16, 30);

    @Mock private MissionExecutionCancelTxService cancelTxService;
    @Mock private GamificationQueryFacade gamificationQueryFacade;
    @Mock private GuildQueryFacade guildQueryFacade;
    @Mock private MissionImageStorageService missionImageStorageService;

    @InjectMocks private MissionExecutionCancelService cancelService;

    /** 취소한 수행 — 기본은 개인·타이머·Saga 완료·사진 없음 */
    private static final class Cancelled {
        private Long guildId;
        private int expEarned = 30;
        private LocalDateTime completedAtUtc = COMPLETED_AT_UTC;
        private boolean simpleMode;
        private boolean statsRecorded = true;
        private boolean guildExpGranted = true;
        private List<String> imageUrls = List.of();
        private long remainingSimpleCount;
        private int expDebt;

        Cancelled guild(Long guildId) {
            this.guildId = guildId;
            return this;
        }

        Cancelled exp(int expEarned) {
            this.expEarned = expEarned;
            return this;
        }

        Cancelled completedAt(LocalDateTime completedAtUtc) {
            this.completedAtUtc = completedAtUtc;
            return this;
        }

        Cancelled simple(long remainingSimpleCount) {
            this.simpleMode = true;
            this.remainingSimpleCount = remainingSimpleCount;
            return this;
        }

        Cancelled autoCompleted() {
            this.statsRecorded = false;
            return this;
        }

        Cancelled guildExpNotGranted() {
            this.guildExpGranted = false;
            return this;
        }

        Cancelled images(String... urls) {
            this.imageUrls = List.of(urls);
            return this;
        }

        Cancelled debt(int expDebt) {
            this.expDebt = expDebt;
            return this;
        }

        CancelledExecution build() {
            return new CancelledExecution(
                    RECORD_ID,
                    MISSION_ID,
                    "아침 스트레칭",
                    guildId,
                    expEarned,
                    completedAtUtc,
                    EXECUTION_DATE,
                    simpleMode,
                    statsRecorded,
                    guildExpGranted,
                    imageUrls,
                    remainingSimpleCount,
                    expDebt);
        }
    }

    private void givenCancelled(Cancelled cancelled) {
        when(cancelTxService.cancel(eq(MISSION_ID), eq(USER_ID), eq(EXECUTION_DATE), any(), any()))
                .thenReturn(cancelled.build());
    }

    private MissionExecutionCancelResponse cancel() {
        return cancelService.cancel(MISSION_ID, USER_ID, EXECUTION_DATE, null, "Asia/Seoul");
    }

    private void givenMilestoneGranted(int milestone, int granted) {
        when(gamificationQueryFacade.sumExperienceBySource(
                        eq(USER_ID),
                        eq(ExpSourceType.DAILY_MISSION_MILESTONE),
                        eq((long) milestone),
                        any(),
                        any()))
                .thenReturn(granted);
    }

    private void givenMilestoneRevoked(int milestone, UserExperienceDto after) {
        when(gamificationQueryFacade.cancelExperience(
                        eq(USER_ID),
                        anyInt(),
                        eq(ExpSourceType.DAILY_MISSION_MILESTONE),
                        eq((long) milestone),
                        anyString(),
                        isNull(),
                        anyString()))
                .thenReturn(after);
    }

    private static UserExperienceDto expWithDebt(Integer expDebt) {
        return new UserExperienceDto(1L, USER_ID, 3, 0, 300, 200, 200, 0.0, 0, expDebt);
    }

    private void verifyNoMilestoneRevoked() {
        verify(gamificationQueryFacade, never())
                .cancelExperience(any(), anyInt(), any(), any(), any(), any(), any());
    }

    // ===== 응답·위임 =====

    @Nested
    @DisplayName("응답과 위임")
    class ResponseAndDelegation {

        @Test
        @DisplayName("요청 값과 되돌린 경험치·남은 누적 마이너스 경험치로 응답을 만든다")
        void buildsResponse() {
            givenCancelled(new Cancelled().exp(30).debt(12));

            MissionExecutionCancelResponse response =
                    cancelService.cancel(MISSION_ID, USER_ID, EXECUTION_DATE, 77L, "Asia/Seoul");

            assertThat(response.missionId()).isEqualTo(MISSION_ID);
            assertThat(response.executionDate()).isEqualTo(EXECUTION_DATE);
            assertThat(response.expCancelled()).isEqualTo(30);
            assertThat(response.milestoneExpCancelled()).isZero();
            assertThat(response.expDebt()).isEqualTo(12);
            verify(cancelTxService)
                    .cancel(MISSION_ID, USER_ID, EXECUTION_DATE, 77L, ZoneId.of("Asia/Seoul"));
        }

        @Test
        @DisplayName("상태·경험치 되돌리기(TxService)가 먼저고, 나머지는 그 뒤에 한다")
        void txServiceFirst() {
            givenCancelled(new Cancelled().guild(5L).images("https://cdn/a.jpg"));

            cancel();

            InOrder order =
                    inOrder(
                            cancelTxService,
                            guildQueryFacade,
                            gamificationQueryFacade,
                            missionImageStorageService);
            order.verify(cancelTxService).cancel(any(), any(), any(), any(), any());
            order.verify(guildQueryFacade)
                    .cancelGuildMissionExperience(any(), anyInt(), any(), any(), any(), any());
            order.verify(gamificationQueryFacade).undoMissionCompletion(USER_ID, true);
            order.verify(gamificationQueryFacade).undoMissionPractice(USER_ID, COMPLETED_AT_UTC);
            order.verify(missionImageStorageService).delete("https://cdn/a.jpg");
        }

        @Test
        @DisplayName("TxService 가 오류를 던지면 그대로 나가고 뒤 단계는 하나도 하지 않는다")
        void txServiceThrows_nothingElseRuns() {
            when(cancelTxService.cancel(any(), any(), any(), any(), any()))
                    .thenThrow(MissionExecutionCancelException.notToday());

            assertThatThrownBy(() -> cancel())
                    .isInstanceOf(MissionExecutionCancelException.class)
                    .extracting("code")
                    .isEqualTo(MissionExecutionCancelException.CODE_NOT_TODAY);

            verifyNoInteractions(
                    gamificationQueryFacade, guildQueryFacade, missionImageStorageService);
        }
    }

    // ===== 시간대 =====

    @Nested
    @DisplayName("시간대 해석 — 없거나 잘못되면 Asia/Seoul")
    class Timezone {

        private ZoneId zonePassedFor(String timezone) {
            givenCancelled(new Cancelled());
            cancelService.cancel(MISSION_ID, USER_ID, EXECUTION_DATE, null, timezone);
            ArgumentCaptor<ZoneId> captor = ArgumentCaptor.forClass(ZoneId.class);
            verify(cancelTxService)
                    .cancel(
                            eq(MISSION_ID),
                            eq(USER_ID),
                            eq(EXECUTION_DATE),
                            isNull(),
                            captor.capture());
            return captor.getValue();
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" → Asia/Seoul")
        @NullSource
        @ValueSource(strings = {"", "   ", "Not/AZone", "KST+9:00:00:00"})
        @DisplayName("헤더가 없거나 비었거나 잘못된 값이면 Asia/Seoul")
        void fallsBackToSeoul(String timezone) {
            assertThat(zonePassedFor(timezone)).isEqualTo(ZoneId.of("Asia/Seoul"));
        }

        @Test
        @DisplayName("올바른 값이면 그 시간대를 쓴다")
        void validZone() {
            assertThat(zonePassedFor("America/New_York")).isEqualTo(ZoneId.of("America/New_York"));
        }

        @Test
        @DisplayName("앞뒤 공백은 잘라 내고 해석한다")
        void trimsWhitespace() {
            assertThat(zonePassedFor("  Europe/Paris ")).isEqualTo(ZoneId.of("Europe/Paris"));
        }
    }

    // ===== 일일 마일스톤 보너스 =====

    @Nested
    @DisplayName("일일 체크 미션 마일스톤 보너스 회수")
    class Milestone {

        @Test
        @DisplayName("체크(SIMPLE) 수행이 아니면 마일스톤을 보지 않는다")
        void notSimple_skipped() {
            givenCancelled(new Cancelled());

            MissionExecutionCancelResponse response = cancel();

            assertThat(response.milestoneExpCancelled()).isZero();
            verify(gamificationQueryFacade, never())
                    .sumExperienceBySource(any(), any(), any(), any(), any());
            verifyNoMilestoneRevoked();
        }

        @Test
        @DisplayName("완료 시각을 모르면(null) 마일스톤을 보지 않는다")
        void completedAtUnknown_skipped() {
            givenCancelled(new Cancelled().simple(0).completedAt(null));

            MissionExecutionCancelResponse response = cancel();

            assertThat(response.milestoneExpCancelled()).isZero();
            verify(gamificationQueryFacade, never())
                    .sumExperienceBySource(any(), any(), any(), any(), any());
        }

        @ParameterizedTest(name = "남은 완료 {0}개 → 회수 없음")
        @ValueSource(longs = {5, 6, 9})
        @DisplayName("남은 완료 수가 가장 큰 마일스톤(5) 이상이면 아무것도 회수하지 않는다")
        void remainingAtOrAboveAllMilestones_nothingRevoked(long remaining) {
            givenCancelled(new Cancelled().simple(remaining));

            MissionExecutionCancelResponse response = cancel();

            assertThat(response.milestoneExpCancelled()).isZero();
            verify(gamificationQueryFacade, never())
                    .sumExperienceBySource(any(), any(), any(), any(), any());
            verifyNoMilestoneRevoked();
        }

        @ParameterizedTest(name = "남은 완료 {0}개 → 5개 보너스만 회수")
        @ValueSource(longs = {3, 4})
        @DisplayName("남은 완료 수가 3 이상 5 미만이면 5개 보너스만 회수한다 (3개 보너스는 그대로)")
        void remainingBetweenMilestones_onlyUpperRevoked(long remaining) {
            givenCancelled(new Cancelled().simple(remaining).debt(0));
            givenMilestoneGranted(5, 20);
            givenMilestoneRevoked(5, expWithDebt(0));

            MissionExecutionCancelResponse response = cancel();

            assertThat(response.milestoneExpCancelled()).isEqualTo(20);
            verify(gamificationQueryFacade, never())
                    .sumExperienceBySource(any(), any(), eq(3L), any(), any());
            verify(gamificationQueryFacade)
                    .cancelExperience(
                            USER_ID,
                            20,
                            ExpSourceType.DAILY_MISSION_MILESTONE,
                            5L,
                            "미션 취소: 일일 체크 미션 5개 보너스",
                            null,
                            MissionExpPolicy.MILESTONE_CATEGORY_NAME);
        }

        @Test
        @DisplayName("남은 완료 수가 3 미만이면 3·5개 보너스를 둘 다 회수하고 합계를 응답에 싣는다")
        void bothMilestonesRevoked() {
            givenCancelled(new Cancelled().simple(2).debt(0));
            givenMilestoneGranted(3, 20);
            givenMilestoneGranted(5, 20);
            givenMilestoneRevoked(3, expWithDebt(0));
            givenMilestoneRevoked(5, expWithDebt(15));

            MissionExecutionCancelResponse response = cancel();

            assertThat(response.milestoneExpCancelled()).isEqualTo(40);
            assertThat(response.expDebt()).isEqualTo(15);
            verify(gamificationQueryFacade)
                    .cancelExperience(
                            USER_ID,
                            20,
                            ExpSourceType.DAILY_MISSION_MILESTONE,
                            3L,
                            "미션 취소: 일일 체크 미션 3개 보너스",
                            null,
                            MissionExpPolicy.MILESTONE_CATEGORY_NAME);
            verify(gamificationQueryFacade)
                    .cancelExperience(
                            USER_ID,
                            20,
                            ExpSourceType.DAILY_MISSION_MILESTONE,
                            5L,
                            "미션 취소: 일일 체크 미션 5개 보너스",
                            null,
                            MissionExpPolicy.MILESTONE_CATEGORY_NAME);
        }

        @ParameterizedTest(name = "그날 지급 순합 {0} → 회수 없음")
        @ValueSource(ints = {0, -20})
        @DisplayName("그날 그 마일스톤의 지급 순합이 0 이하이면(받은 적 없음·이미 회수) 회수하지 않는다")
        void notGranted_notRevoked(int netGranted) {
            givenCancelled(new Cancelled().simple(2).debt(9));
            givenMilestoneGranted(3, netGranted);
            givenMilestoneGranted(5, netGranted);

            MissionExecutionCancelResponse response = cancel();

            assertThat(response.milestoneExpCancelled()).isZero();
            assertThat(response.expDebt()).isEqualTo(9);
            verifyNoMilestoneRevoked();
        }

        @Test
        @DisplayName("받은 보너스 금액 그대로 회수한다 (정책 상수가 아니라 그날 내역의 순합)")
        void revokesGrantedAmount() {
            givenCancelled(new Cancelled().simple(2));
            givenMilestoneGranted(3, 35);
            givenMilestoneGranted(5, 0);
            givenMilestoneRevoked(3, expWithDebt(0));

            MissionExecutionCancelResponse response = cancel();

            assertThat(response.milestoneExpCancelled()).isEqualTo(35);
            verify(gamificationQueryFacade)
                    .cancelExperience(
                            eq(USER_ID),
                            eq(35),
                            eq(ExpSourceType.DAILY_MISSION_MILESTONE),
                            eq(3L),
                            anyString(),
                            isNull(),
                            anyString());
        }

        @Test
        @DisplayName("지급 내역은 완료 시각이 속한 요청 시간대 하루(UTC 구간)에서 찾는다 — KST")
        void searchWindow_kstDay() {
            givenCancelled(new Cancelled().simple(2));

            cancelService.cancel(MISSION_ID, USER_ID, EXECUTION_DATE, null, "Asia/Seoul");

            // 2026-11-25T16:30Z = 11-26 01:30 KST → 11-26 KST 하루 = [11-25T15:00Z, 11-26T15:00Z)
            verify(gamificationQueryFacade)
                    .sumExperienceBySource(
                            USER_ID,
                            ExpSourceType.DAILY_MISSION_MILESTONE,
                            3L,
                            LocalDateTime.of(2026, 11, 25, 15, 0),
                            LocalDateTime.of(2026, 11, 26, 15, 0));
            verify(gamificationQueryFacade)
                    .sumExperienceBySource(
                            USER_ID,
                            ExpSourceType.DAILY_MISSION_MILESTONE,
                            5L,
                            LocalDateTime.of(2026, 11, 25, 15, 0),
                            LocalDateTime.of(2026, 11, 26, 15, 0));
        }

        @Test
        @DisplayName("지급 내역은 완료 시각이 속한 요청 시간대 하루(UTC 구간)에서 찾는다 — 뉴욕")
        void searchWindow_newYorkDay() {
            givenCancelled(new Cancelled().simple(2));

            cancelService.cancel(MISSION_ID, USER_ID, EXECUTION_DATE, null, "America/New_York");

            // 2026-11-25T16:30Z = 11-25 11:30 EST → 11-25 EST 하루 = [11-25T05:00Z, 11-26T05:00Z)
            verify(gamificationQueryFacade)
                    .sumExperienceBySource(
                            USER_ID,
                            ExpSourceType.DAILY_MISSION_MILESTONE,
                            3L,
                            LocalDateTime.of(2026, 11, 25, 5, 0),
                            LocalDateTime.of(2026, 11, 26, 5, 0));
        }
    }

    // ===== exp_debt =====

    @Nested
    @DisplayName("응답의 exp_debt")
    class ExpDebt {

        @Test
        @DisplayName("마일스톤을 회수했으면 회수 뒤 값이 미션 경험치 취소 직후 값보다 우선한다")
        void milestoneDebtWins() {
            givenCancelled(new Cancelled().simple(2).debt(7));
            givenMilestoneGranted(3, 20);
            givenMilestoneGranted(5, 0);
            givenMilestoneRevoked(3, expWithDebt(27));

            assertThat(cancel().expDebt()).isEqualTo(27);
        }

        @Test
        @DisplayName("마일스톤 회수로 누적 마이너스가 0 이 됐으면 0 이다 (취소 직후 값으로 돌아가지 않는다)")
        void milestoneDebtZeroWins() {
            givenCancelled(new Cancelled().simple(4).debt(7));
            givenMilestoneGranted(5, 20);
            givenMilestoneRevoked(5, expWithDebt(0));

            assertThat(cancel().expDebt()).isZero();
        }

        @Test
        @DisplayName("회수한 마일스톤이 없으면 미션 경험치 취소 직후 값이다")
        void noMilestone_usesCancelDebt() {
            givenCancelled(new Cancelled().simple(2).debt(7));
            givenMilestoneGranted(3, 0);
            givenMilestoneGranted(5, 0);

            assertThat(cancel().expDebt()).isEqualTo(7);
        }

        @Test
        @DisplayName("마일스톤 회수 결과가 null 이거나 exp_debt 가 null 이면 미션 경험치 취소 직후 값으로 답한다")
        void milestoneResultWithoutDebt_fallsBack() {
            givenCancelled(new Cancelled().simple(2).debt(7));
            givenMilestoneGranted(3, 20);
            givenMilestoneGranted(5, 20);
            givenMilestoneRevoked(3, null);
            givenMilestoneRevoked(5, expWithDebt(null));

            MissionExecutionCancelResponse response = cancel();

            assertThat(response.milestoneExpCancelled()).isEqualTo(40);
            assertThat(response.expDebt()).isEqualTo(7);
        }
    }

    // ===== 길드 =====

    @Nested
    @DisplayName("길드 경험치·포인트 회수")
    class Guild {

        @Test
        @DisplayName("길드 미션이면 길드 경험치·포인트를 회수한다")
        void guildMission_revokes() {
            givenCancelled(new Cancelled().guild(5L).exp(30));

            cancel();

            verify(guildQueryFacade)
                    .cancelGuildMissionExperience(
                            5L, 30, MISSION_ID, USER_ID, "길드 미션 취소: 아침 스트레칭", COMPLETED_AT_UTC);
        }

        @Test
        @DisplayName("자동 종료에서 길드 경험치를 받지 못한 수행이면 길드 쪽은 부르지 않는다")
        void guildExpNotGranted_skipped() {
            givenCancelled(new Cancelled().guild(5L).exp(30).autoCompleted().guildExpNotGranted());

            cancel();

            verifyNoInteractions(guildQueryFacade);
        }

        @Test
        @DisplayName("개인 미션이면 길드 쪽은 부르지 않는다")
        void personalMission_skipped() {
            givenCancelled(new Cancelled().exp(30));

            cancel();

            verifyNoInteractions(guildQueryFacade);
        }

        @Test
        @DisplayName("길드 미션이어도 되돌릴 경험치가 0 이면 부르지 않는다")
        void guildMissionZeroExp_skipped() {
            givenCancelled(new Cancelled().guild(5L).exp(0));

            cancel();

            verifyNoInteractions(guildQueryFacade);
        }
    }

    // ===== 통계·스트릭 =====

    @Nested
    @DisplayName("통계·스트릭 되돌리기")
    class Stats {

        @Test
        @DisplayName("Saga 로 완료한 개인 미션 — 총 완료 수(개인)와 연속 실천을 되돌린다")
        void personalMission() {
            givenCancelled(new Cancelled());

            cancel();

            verify(gamificationQueryFacade).undoMissionCompletion(USER_ID, false);
            verify(gamificationQueryFacade).undoMissionPractice(USER_ID, COMPLETED_AT_UTC);
        }

        @Test
        @DisplayName("Saga 로 완료한 길드 미션 — 길드 미션 완료 수도 되돌린다")
        void guildMission() {
            givenCancelled(new Cancelled().guild(5L));

            cancel();

            verify(gamificationQueryFacade).undoMissionCompletion(USER_ID, true);
        }

        @Test
        @DisplayName("자동 종료된 수행(statsRecorded=false)은 완료 수를 내리지 않는다 — 연속 실천 되돌리기는 한다")
        void autoCompleted_onlyPracticeUndone() {
            givenCancelled(new Cancelled().autoCompleted());

            cancel();

            verify(gamificationQueryFacade, never()).undoMissionCompletion(any(), anyBoolean());
            verify(gamificationQueryFacade).undoMissionPractice(USER_ID, COMPLETED_AT_UTC);
        }
    }

    // ===== 사진 파일 =====

    @Nested
    @DisplayName("사진 파일 삭제")
    class ImageFiles {

        @Test
        @DisplayName("취소한 수행의 사진 파일을 모두 지운다")
        void deletesAll() {
            givenCancelled(new Cancelled().images("https://cdn/a.jpg", "https://cdn/b.jpg"));

            cancel();

            verify(missionImageStorageService).delete("https://cdn/a.jpg");
            verify(missionImageStorageService).delete("https://cdn/b.jpg");
        }

        @Test
        @DisplayName("한 장 삭제가 실패해도 나머지를 지우고 응답한다")
        void deleteFailureIgnored() {
            givenCancelled(new Cancelled().images("https://cdn/a.jpg", "https://cdn/b.jpg"));
            doThrow(new IllegalStateException("s3 down"))
                    .when(missionImageStorageService)
                    .delete("https://cdn/a.jpg");

            MissionExecutionCancelResponse response = cancel();

            assertThat(response.expCancelled()).isEqualTo(30);
            verify(missionImageStorageService).delete("https://cdn/b.jpg");
        }

        @Test
        @DisplayName("사진이 없으면 저장소를 부르지 않는다")
        void noImages() {
            givenCancelled(new Cancelled());

            cancel();

            verifyNoInteractions(missionImageStorageService);
        }
    }

    // ===== 후속 단계 실패 =====

    @Nested
    @DisplayName("커밋 뒤 단계가 실패해도 취소 응답은 나간다")
    class FollowUpFailures {

        @Test
        @DisplayName("마일스톤 지급 내역 조회가 실패해도 길드·통계·사진 단계는 계속하고 응답한다")
        void milestoneLookupFails() {
            givenCancelled(new Cancelled().simple(2).guild(5L).debt(4).images("https://cdn/a.jpg"));
            when(gamificationQueryFacade.sumExperienceBySource(any(), any(), any(), any(), any()))
                    .thenThrow(new IllegalStateException("gamification down"));

            MissionExecutionCancelResponse response = cancel();

            assertThat(response.expCancelled()).isEqualTo(30);
            assertThat(response.milestoneExpCancelled()).isZero();
            assertThat(response.expDebt()).isEqualTo(4);
            verify(guildQueryFacade)
                    .cancelGuildMissionExperience(any(), anyInt(), any(), any(), any(), any());
            verify(gamificationQueryFacade).undoMissionCompletion(USER_ID, true);
            verify(gamificationQueryFacade).undoMissionPractice(USER_ID, COMPLETED_AT_UTC);
            verify(missionImageStorageService).delete("https://cdn/a.jpg");
        }

        @Test
        @DisplayName("3개 보너스를 회수한 뒤 5개 보너스 회수가 실패하면 회수한 만큼만 응답에 싣는다")
        void secondMilestoneFails_keepsFirst() {
            givenCancelled(new Cancelled().simple(2).debt(0));
            givenMilestoneGranted(3, 20);
            givenMilestoneGranted(5, 20);
            givenMilestoneRevoked(3, expWithDebt(11));
            when(gamificationQueryFacade.cancelExperience(
                            eq(USER_ID),
                            anyInt(),
                            eq(ExpSourceType.DAILY_MISSION_MILESTONE),
                            eq(5L),
                            anyString(),
                            isNull(),
                            anyString()))
                    .thenThrow(new IllegalStateException("gamification down"));

            MissionExecutionCancelResponse response = cancel();

            assertThat(response.milestoneExpCancelled()).isEqualTo(20);
            assertThat(response.expDebt()).isEqualTo(11);
            verify(gamificationQueryFacade).undoMissionPractice(USER_ID, COMPLETED_AT_UTC);
        }

        @Test
        @DisplayName("길드 회수가 실패해도 통계·사진 단계는 계속하고 응답한다")
        void guildFails() {
            givenCancelled(new Cancelled().guild(5L).images("https://cdn/a.jpg"));
            when(guildQueryFacade.cancelGuildMissionExperience(
                            anyLong(), anyInt(), anyLong(), anyString(), anyString(), any()))
                    .thenThrow(new IllegalArgumentException("길드를 찾을 수 없습니다: 5"));

            MissionExecutionCancelResponse response = cancel();

            assertThat(response.expCancelled()).isEqualTo(30);
            verify(gamificationQueryFacade).undoMissionCompletion(USER_ID, true);
            verify(gamificationQueryFacade).undoMissionPractice(USER_ID, COMPLETED_AT_UTC);
            verify(missionImageStorageService).delete("https://cdn/a.jpg");
        }

        @Test
        @DisplayName("완료 수 되돌리기가 실패해도 연속 기록 되돌리기와 사진 단계는 계속하고 응답한다")
        void undoCompletionFails() {
            givenCancelled(new Cancelled().images("https://cdn/a.jpg"));
            doThrow(new IllegalStateException("gamification down"))
                    .when(gamificationQueryFacade)
                    .undoMissionCompletion(USER_ID, false);

            MissionExecutionCancelResponse response = cancel();

            assertThat(response.expCancelled()).isEqualTo(30);
            verify(gamificationQueryFacade).undoMissionPractice(USER_ID, COMPLETED_AT_UTC);
            verify(missionImageStorageService).delete("https://cdn/a.jpg");
        }

        @Test
        @DisplayName("연속 실천 되돌리기가 실패해도 사진 단계는 계속하고 응답한다")
        void undoPracticeFails() {
            givenCancelled(new Cancelled().images("https://cdn/a.jpg"));
            doThrow(new IllegalStateException("gamification down"))
                    .when(gamificationQueryFacade)
                    .undoMissionPractice(USER_ID, COMPLETED_AT_UTC);

            MissionExecutionCancelResponse response = cancel();

            assertThat(response.expCancelled()).isEqualTo(30);
            verify(gamificationQueryFacade).undoMissionCompletion(USER_ID, false);
            verify(missionImageStorageService).delete("https://cdn/a.jpg");
        }
    }
}
