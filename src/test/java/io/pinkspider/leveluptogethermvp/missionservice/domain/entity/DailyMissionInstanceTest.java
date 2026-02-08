package io.pinkspider.leveluptogethermvp.missionservice.domain.entity;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.pinkspider.global.test.TestReflectionUtils;

import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DailyMissionInstance 엔티티 테스트")
class DailyMissionInstanceTest {

    private static final String TEST_USER_ID = "test-user-123";

    private MissionCategory category;
    private Mission mission;
    private MissionParticipant participant;

    @BeforeEach
    void setUp() {
        category = MissionCategory.builder()
            .name("운동")
            .description("운동 관련 미션")
            .build();
        setId(category, 1L);

        mission = Mission.builder()
            .title("매일 30분 운동")
            .description("매일 30분씩 운동하기")
            .creatorId(TEST_USER_ID)
            .status(MissionStatus.IN_PROGRESS)
            .visibility(MissionVisibility.PRIVATE)
            .type(MissionType.PERSONAL)
            .category(category)
            .expPerCompletion(50)
            .isPinned(true)
            .build();
        setId(mission, 1L);

        participant = MissionParticipant.builder()
            .mission(mission)
            .userId(TEST_USER_ID)
            .status(ParticipantStatus.ACCEPTED)
            .build();
        setId(participant, 1L);
    }

    private DailyMissionInstance createInstance(LocalDate date) {
        return DailyMissionInstance.createFrom(participant, date);
    }

    @Nested
    @DisplayName("createFrom 팩토리 메서드 테스트")
    class CreateFromTest {

        @Test
        @DisplayName("참여자와 날짜로 인스턴스를 생성한다")
        void createFrom_success() {
            // given
            LocalDate today = LocalDate.now();

            // when
            DailyMissionInstance instance = DailyMissionInstance.createFrom(participant, today);

            // then
            assertThat(instance.getParticipant()).isEqualTo(participant);
            assertThat(instance.getInstanceDate()).isEqualTo(today);
            assertThat(instance.getMissionTitle()).isEqualTo("매일 30분 운동");
            assertThat(instance.getMissionDescription()).isEqualTo("매일 30분씩 운동하기");
            assertThat(instance.getCategoryName()).isEqualTo("운동");
            assertThat(instance.getCategoryId()).isEqualTo(1L);
            assertThat(instance.getExpPerCompletion()).isEqualTo(50);
            assertThat(instance.getStatus()).isEqualTo(ExecutionStatus.PENDING);
            assertThat(instance.getExpEarned()).isEqualTo(0);
            assertThat(instance.getIsSharedToFeed()).isFalse();
        }

        @Test
        @DisplayName("카테고리가 없으면 customCategory를 사용한다")
        void createFrom_withCustomCategory() {
            // given
            Mission missionWithCustomCategory = Mission.builder()
                .title("커스텀 미션")
                .description("커스텀 카테고리 미션")
                .creatorId(TEST_USER_ID)
                .status(MissionStatus.IN_PROGRESS)
                .visibility(MissionVisibility.PRIVATE)
                .type(MissionType.PERSONAL)
                .category(null)
                .customCategory("나만의 카테고리")
                .expPerCompletion(30)
                .isPinned(true)
                .build();
            setId(missionWithCustomCategory, 2L);

            MissionParticipant participantWithCustom = MissionParticipant.builder()
                .mission(missionWithCustomCategory)
                .userId(TEST_USER_ID)
                .status(ParticipantStatus.ACCEPTED)
                .build();
            setId(participantWithCustom, 2L);

            LocalDate today = LocalDate.now();

            // when
            DailyMissionInstance instance = DailyMissionInstance.createFrom(participantWithCustom, today);

            // then
            assertThat(instance.getCategoryName()).isEqualTo("나만의 카테고리");
            assertThat(instance.getCategoryId()).isNull();
        }
    }

    @Nested
    @DisplayName("start 메서드 테스트")
    class StartTest {

        @Test
        @DisplayName("PENDING 상태에서 시작하면 IN_PROGRESS 상태가 된다")
        void start_fromPending_success() {
            // given
            DailyMissionInstance instance = createInstance(LocalDate.now());

            // when
            instance.start();

            // then
            assertThat(instance.getStatus()).isEqualTo(ExecutionStatus.IN_PROGRESS);
            assertThat(instance.getStartedAt()).isNotNull();
        }

        @Test
        @DisplayName("이미 완료된 인스턴스는 시작할 수 없다")
        void start_alreadyCompleted_throwsException() {
            // given
            DailyMissionInstance instance = createInstance(LocalDate.now());
            instance.start();
            TestReflectionUtils.setField(instance, "startedAt",LocalDateTime.now().minusMinutes(5));
            instance.complete();

            // when & then
            assertThatThrownBy(instance::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 완료된 인스턴스입니다");
        }

        @Test
        @DisplayName("MISSED 상태의 인스턴스는 시작할 수 없다")
        void start_missed_throwsException() {
            // given
            DailyMissionInstance instance = createInstance(LocalDate.now());
            instance.markAsMissed();

            // when & then
            assertThatThrownBy(instance::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("미실행 처리된 인스턴스는 시작할 수 없습니다");
        }

        @Test
        @DisplayName("이미 시작된 인스턴스는 다시 시작할 수 없다")
        void start_alreadyStarted_throwsException() {
            // given
            DailyMissionInstance instance = createInstance(LocalDate.now());
            instance.start();

            // when & then
            assertThatThrownBy(instance::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 시작된 인스턴스입니다");
        }
    }

    @Nested
    @DisplayName("complete 메서드 테스트")
    class CompleteTest {

        @Test
        @DisplayName("시작된 인스턴스를 완료하면 경험치가 계산된다")
        void complete_success() {
            // given
            DailyMissionInstance instance = createInstance(LocalDate.now());
            instance.start();
            // 5분 전에 시작했다고 설정
            TestReflectionUtils.setField(instance, "startedAt",LocalDateTime.now().minusMinutes(5));

            // when
            instance.complete();

            // then
            assertThat(instance.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            assertThat(instance.getCompletedAt()).isNotNull();
            assertThat(instance.getExpEarned()).isGreaterThanOrEqualTo(5);
        }

        @Test
        @DisplayName("이미 완료된 인스턴스는 다시 완료할 수 없다")
        void complete_alreadyCompleted_throwsException() {
            // given
            DailyMissionInstance instance = createInstance(LocalDate.now());
            instance.start();
            TestReflectionUtils.setField(instance, "startedAt",LocalDateTime.now().minusMinutes(5));
            instance.complete();

            // when & then
            assertThatThrownBy(instance::complete)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 완료된 인스턴스입니다");
        }

        @Test
        @DisplayName("MISSED 상태의 인스턴스는 완료할 수 없다")
        void complete_missed_throwsException() {
            // given
            DailyMissionInstance instance = createInstance(LocalDate.now());
            instance.markAsMissed();

            // when & then
            assertThatThrownBy(instance::complete)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("미실행 처리된 인스턴스는 완료할 수 없습니다");
        }

        @Test
        @DisplayName("시작하지 않은 인스턴스는 완료할 수 없다")
        void complete_notStarted_throwsException() {
            // given
            DailyMissionInstance instance = createInstance(LocalDate.now());

            // when & then
            assertThatThrownBy(instance::complete)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("미션을 먼저 시작해야 합니다");
        }

        @Test
        @DisplayName("1분 미만 수행 시 완료할 수 없다")
        void complete_lessThanOneMinute_throwsException() {
            // given
            DailyMissionInstance instance = createInstance(LocalDate.now());
            instance.start();
            // 방금 시작 (1분 미만)

            // when & then
            assertThatThrownBy(instance::complete)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("최소 1분 이상 수행해야 완료할 수 있습니다");
        }
    }

    @Nested
    @DisplayName("calculateExpByDuration 메서드 테스트")
    class CalculateExpByDurationTest {

        @Test
        @DisplayName("수행 시간에 비례해 경험치를 계산한다 (분당 1 EXP)")
        void calculateExpByDuration_success() {
            // given
            DailyMissionInstance instance = createInstance(LocalDate.now());
            instance.start();
            TestReflectionUtils.setField(instance, "startedAt",LocalDateTime.now().minusMinutes(60));
            instance.complete();

            // when
            int exp = instance.calculateExpByDuration();

            // then
            assertThat(exp).isGreaterThanOrEqualTo(60);
        }

        @Test
        @DisplayName("시작/완료 시간이 없으면 0을 반환한다")
        void calculateExpByDuration_noTimes_returnsZero() {
            // given
            DailyMissionInstance instance = createInstance(LocalDate.now());

            // when
            int exp = instance.calculateExpByDuration();

            // then
            assertThat(exp).isEqualTo(0);
        }

        @Test
        @DisplayName("최대 480분(8시간)까지만 경험치를 계산한다")
        void calculateExpByDuration_cappedAt480() {
            // given
            DailyMissionInstance instance = createInstance(LocalDate.now());
            instance.start();
            // 10시간 전에 시작
            TestReflectionUtils.setField(instance, "startedAt",LocalDateTime.now().minusMinutes(600));
            instance.complete();

            // when
            int exp = instance.calculateExpByDuration();

            // then
            assertThat(exp).isEqualTo(480);
        }
    }

    @Nested
    @DisplayName("markAsMissed 메서드 테스트")
    class MarkAsMissedTest {

        @Test
        @DisplayName("PENDING 상태를 MISSED로 변경한다")
        void markAsMissed_fromPending_success() {
            // given
            DailyMissionInstance instance = createInstance(LocalDate.now());

            // when
            instance.markAsMissed();

            // then
            assertThat(instance.getStatus()).isEqualTo(ExecutionStatus.MISSED);
        }

        @Test
        @DisplayName("IN_PROGRESS 상태를 MISSED로 변경한다")
        void markAsMissed_fromInProgress_success() {
            // given
            DailyMissionInstance instance = createInstance(LocalDate.now());
            instance.start();

            // when
            instance.markAsMissed();

            // then
            assertThat(instance.getStatus()).isEqualTo(ExecutionStatus.MISSED);
        }

        @Test
        @DisplayName("완료된 인스턴스는 MISSED 처리할 수 없다")
        void markAsMissed_completed_throwsException() {
            // given
            DailyMissionInstance instance = createInstance(LocalDate.now());
            instance.start();
            TestReflectionUtils.setField(instance, "startedAt",LocalDateTime.now().minusMinutes(5));
            instance.complete();

            // when & then
            assertThatThrownBy(instance::markAsMissed)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("완료된 인스턴스는 미실행 처리할 수 없습니다");
        }
    }

    @Nested
    @DisplayName("skip 메서드 테스트")
    class SkipTest {

        @Test
        @DisplayName("IN_PROGRESS 상태를 PENDING으로 되돌린다")
        void skip_fromInProgress_success() {
            // given
            DailyMissionInstance instance = createInstance(LocalDate.now());
            instance.start();

            // when
            instance.skip();

            // then
            assertThat(instance.getStatus()).isEqualTo(ExecutionStatus.PENDING);
            assertThat(instance.getStartedAt()).isNull();
        }

        @Test
        @DisplayName("완료된 인스턴스는 취소할 수 없다")
        void skip_completed_throwsException() {
            // given
            DailyMissionInstance instance = createInstance(LocalDate.now());
            instance.start();
            TestReflectionUtils.setField(instance, "startedAt",LocalDateTime.now().minusMinutes(5));
            instance.complete();

            // when & then
            assertThatThrownBy(instance::skip)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("완료된 인스턴스는 취소할 수 없습니다");
        }

        @Test
        @DisplayName("MISSED 상태의 인스턴스는 취소할 수 없다")
        void skip_missed_throwsException() {
            // given
            DailyMissionInstance instance = createInstance(LocalDate.now());
            instance.markAsMissed();

            // when & then
            assertThatThrownBy(instance::skip)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("미실행 처리된 인스턴스는 취소할 수 없습니다");
        }
    }

    @Nested
    @DisplayName("피드 공유 테스트")
    class FeedSharingTest {

        @Test
        @DisplayName("피드 공유 시 feedId와 isSharedToFeed가 설정된다")
        void shareToFeed_success() {
            // given
            DailyMissionInstance instance = createInstance(LocalDate.now());
            Long feedId = 100L;

            // when
            instance.shareToFeed(feedId);

            // then
            assertThat(instance.getFeedId()).isEqualTo(100L);
            assertThat(instance.getIsSharedToFeed()).isTrue();
        }

        @Test
        @DisplayName("피드 공유 취소 시 feedId와 isSharedToFeed가 초기화된다")
        void unshareFromFeed_success() {
            // given
            DailyMissionInstance instance = createInstance(LocalDate.now());
            instance.shareToFeed(100L);

            // when
            instance.unshareFromFeed();

            // then
            assertThat(instance.getFeedId()).isNull();
            assertThat(instance.getIsSharedToFeed()).isFalse();
        }
    }

    @Nested
    @DisplayName("resetToPending 메서드 테스트 (고정 미션 반복 수행)")
    class ResetToPendingTest {

        @Test
        @DisplayName("완료된 인스턴스를 PENDING 상태로 리셋한다")
        void resetToPending_fromCompleted_success() {
            // given
            DailyMissionInstance instance = createInstance(LocalDate.now());
            instance.start();
            TestReflectionUtils.setField(instance, "startedAt",LocalDateTime.now().minusMinutes(5));
            instance.complete();

            // when
            instance.resetToPending();

            // then
            assertThat(instance.getStatus()).isEqualTo(ExecutionStatus.PENDING);
            assertThat(instance.getStartedAt()).isNull();
            assertThat(instance.getCompletedAt()).isNull();
            assertThat(instance.getExpEarned()).isEqualTo(0);
            // completionCount와 totalExpEarned는 유지됨
            assertThat(instance.getCompletionCount()).isEqualTo(1);
            assertThat(instance.getTotalExpEarned()).isGreaterThanOrEqualTo(5);
        }

        @Test
        @DisplayName("PENDING 상태에서는 리셋할 수 없다")
        void resetToPending_fromPending_throwsException() {
            // given
            DailyMissionInstance instance = createInstance(LocalDate.now());

            // when & then
            assertThatThrownBy(instance::resetToPending)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("완료 상태의 인스턴스만 리셋할 수 있습니다");
        }

        @Test
        @DisplayName("IN_PROGRESS 상태에서는 리셋할 수 없다")
        void resetToPending_fromInProgress_throwsException() {
            // given
            DailyMissionInstance instance = createInstance(LocalDate.now());
            instance.start();

            // when & then
            assertThatThrownBy(instance::resetToPending)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("완료 상태의 인스턴스만 리셋할 수 있습니다");
        }

        @Test
        @DisplayName("여러 번 완료-리셋을 반복하면 completionCount가 누적된다")
        void resetToPending_multipleCompletions_accumulatesCount() {
            // given
            DailyMissionInstance instance = createInstance(LocalDate.now());

            // 첫 번째 완료
            instance.start();
            TestReflectionUtils.setField(instance, "startedAt",LocalDateTime.now().minusMinutes(10));
            instance.complete();
            assertThat(instance.getCompletionCount()).isEqualTo(1);
            int firstExpEarned = instance.getTotalExpEarned();
            instance.resetToPending();

            // 두 번째 완료
            instance.start();
            TestReflectionUtils.setField(instance, "startedAt",LocalDateTime.now().minusMinutes(20));
            instance.complete();
            assertThat(instance.getCompletionCount()).isEqualTo(2);
            assertThat(instance.getTotalExpEarned()).isGreaterThan(firstExpEarned);
            instance.resetToPending();

            // 세 번째 완료
            instance.start();
            TestReflectionUtils.setField(instance, "startedAt",LocalDateTime.now().minusMinutes(15));
            instance.complete();

            // then
            assertThat(instance.getCompletionCount()).isEqualTo(3);
            assertThat(instance.getTotalExpEarned()).isGreaterThanOrEqualTo(45); // 최소 10+20+15 = 45
        }

        @Test
        @DisplayName("리셋 후 다시 시작할 수 있다")
        void resetToPending_canStartAgain() {
            // given
            DailyMissionInstance instance = createInstance(LocalDate.now());
            instance.start();
            TestReflectionUtils.setField(instance, "startedAt",LocalDateTime.now().minusMinutes(5));
            instance.complete();
            instance.resetToPending();

            // when
            instance.start();

            // then
            assertThat(instance.getStatus()).isEqualTo(ExecutionStatus.IN_PROGRESS);
            assertThat(instance.getStartedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("getDurationMinutes 메서드 테스트")
    class GetDurationMinutesTest {

        @Test
        @DisplayName("시작과 완료 시간이 있으면 수행 시간을 분으로 반환한다")
        void getDurationMinutes_success() {
            // given
            DailyMissionInstance instance = createInstance(LocalDate.now());
            instance.start();
            TestReflectionUtils.setField(instance, "startedAt",LocalDateTime.now().minusMinutes(45));
            instance.complete();

            // when
            Integer durationMinutes = instance.getDurationMinutes();

            // then
            assertThat(durationMinutes).isGreaterThanOrEqualTo(45);
        }

        @Test
        @DisplayName("시작 시간이 없으면 null을 반환한다")
        void getDurationMinutes_noStartTime_returnsNull() {
            // given
            DailyMissionInstance instance = createInstance(LocalDate.now());

            // when
            Integer durationMinutes = instance.getDurationMinutes();

            // then
            assertThat(durationMinutes).isNull();
        }

        @Test
        @DisplayName("완료 시간이 없으면 null을 반환한다")
        void getDurationMinutes_noCompleteTime_returnsNull() {
            // given
            DailyMissionInstance instance = createInstance(LocalDate.now());
            instance.start();

            // when
            Integer durationMinutes = instance.getDurationMinutes();

            // then
            assertThat(durationMinutes).isNull();
        }
    }
}
