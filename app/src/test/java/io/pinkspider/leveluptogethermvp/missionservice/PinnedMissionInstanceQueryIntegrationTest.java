package io.pinkspider.leveluptogethermvp.missionservice;

import static org.assertj.core.api.Assertions.assertThat;

import io.pinkspider.global.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-566: 고정 미션 인스턴스의 "예정" 생성·집계 기준 JPQL 검증.
 *
 * <p>두 쿼리 모두 조건이 JPQL 안에 있어 Mockito 단위 테스트로는 검증되지 않는다. 실제 SQL 실행 결과를 H2(mission_db)로 확인한다.
 *
 * <ul>
 *   <li>{@code findAllActivePinnedMissionParticipants} — 스케줄러가 매일 인스턴스를 만들 대상. 미션이 활성
 *       상태(DRAFT/OPEN/IN_PROGRESS)·고정·미삭제이고 참여자가 ACCEPTED 또는 IN_PROGRESS 여야 한다. 예전엔 종료(COMPLETED)된
 *       미션에도 매일 MISSED 가 쌓였고(미션 상태 미확인), 첫 완료로 IN_PROGRESS 가 된 참여자는 빠졌다.
 *   <li>{@code countScheduledInPeriod} — 월간 리포트 분모. 1회차는 상태 무관, 2회차 이상은 COMPLETED 만 센다(완료 직후 자동
 *       생성되는 seq+1 미완료 제외).
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "missionTransactionManager")
class PinnedMissionInstanceQueryIntegrationTest {

    private static final String PREFIX = "lut566-";

    @Autowired private MissionRepository missionRepository;
    @Autowired private MissionParticipantRepository participantRepository;
    @Autowired private DailyMissionInstanceRepository instanceRepository;

    @Nested
    @DisplayName("findAllActivePinnedMissionParticipants — 스케줄러 대상")
    class ActivePinnedParticipants {

        @Test
        @DisplayName("활성 미션의 ACCEPTED·IN_PROGRESS 참여자만 대상이고, 종료·취소·삭제·비고정 미션과 WITHDRAWN 참여자는 제외된다")
        void includesActiveMissionAcceptedOrInProgressOnly() {
            Mission draftPinned = saveMission(MissionStatus.DRAFT, true, false);
            Mission openPinned = saveMission(MissionStatus.OPEN, true, false);
            Mission inProgressPinned = saveMission(MissionStatus.IN_PROGRESS, true, false);
            Mission completedPinned = saveMission(MissionStatus.COMPLETED, true, false);
            Mission cancelledPinned = saveMission(MissionStatus.CANCELLED, true, false);
            Mission deletedPinned = saveMission(MissionStatus.IN_PROGRESS, true, true);
            Mission regular = saveMission(MissionStatus.IN_PROGRESS, false, false);

            // 포함되어야 하는 참여자
            saveParticipant(draftPinned, "in-draft-accepted", ParticipantStatus.ACCEPTED);
            saveParticipant(openPinned, "in-open-accepted", ParticipantStatus.ACCEPTED);
            saveParticipant(inProgressPinned, "in-progress-accepted", ParticipantStatus.ACCEPTED);
            // 첫 완료 뒤 IN_PROGRESS 로 유지되는 고정 미션 참여자 — 예전엔 빠져서 미수행일 인스턴스가 생기지 않았다
            saveParticipant(
                    inProgressPinned, "in-progress-inprogress", ParticipantStatus.IN_PROGRESS);

            // 제외되어야 하는 참여자
            saveParticipant(completedPinned, "out-completed", ParticipantStatus.ACCEPTED);
            saveParticipant(cancelledPinned, "out-cancelled", ParticipantStatus.ACCEPTED);
            saveParticipant(deletedPinned, "out-deleted", ParticipantStatus.ACCEPTED);
            saveParticipant(regular, "out-regular", ParticipantStatus.ACCEPTED);
            saveParticipant(inProgressPinned, "out-withdrawn", ParticipantStatus.WITHDRAWN);
            saveParticipant(inProgressPinned, "out-pending", ParticipantStatus.PENDING);
            saveParticipant(inProgressPinned, "out-completed-p", ParticipantStatus.COMPLETED);

            List<String> targets =
                    participantRepository.findAllActivePinnedMissionParticipants().stream()
                            .map(MissionParticipant::getUserId)
                            .filter(id -> id.startsWith(PREFIX))
                            .toList();

            assertThat(targets)
                    .containsExactlyInAnyOrder(
                            PREFIX + "in-draft-accepted",
                            PREFIX + "in-open-accepted",
                            PREFIX + "in-progress-accepted",
                            PREFIX + "in-progress-inprogress");
        }

        @Test
        @DisplayName(
                "유저별 on-demand 생성용 findPinnedMissionParticipants 도 같은 기준을 쓴다 — 종료된 미션은 다시 만들어지지"
                        + " 않는다")
        void perUserQueryUsesSameRule() {
            String me = "me";
            Mission draftPinned = saveMission(MissionStatus.DRAFT, true, false);
            Mission openPinned = saveMission(MissionStatus.OPEN, true, false);
            Mission inProgressPinned = saveMission(MissionStatus.IN_PROGRESS, true, false);
            Mission completedPinned = saveMission(MissionStatus.COMPLETED, true, false);
            Mission cancelledPinned = saveMission(MissionStatus.CANCELLED, true, false);
            Mission deletedPinned = saveMission(MissionStatus.IN_PROGRESS, true, true);
            Mission regular = saveMission(MissionStatus.IN_PROGRESS, false, false);

            saveParticipant(draftPinned, me, ParticipantStatus.ACCEPTED); // in
            saveParticipant(openPinned, me, ParticipantStatus.IN_PROGRESS); // in
            saveParticipant(inProgressPinned, me, ParticipantStatus.WITHDRAWN); // out
            saveParticipant(completedPinned, me, ParticipantStatus.ACCEPTED); // out (종료)
            saveParticipant(cancelledPinned, me, ParticipantStatus.IN_PROGRESS); // out (취소)
            saveParticipant(deletedPinned, me, ParticipantStatus.ACCEPTED); // out (삭제)
            saveParticipant(regular, me, ParticipantStatus.ACCEPTED); // out (비고정)
            saveParticipant(
                    inProgressPinned, "someone-else", ParticipantStatus.ACCEPTED); // out (타인)

            List<Long> missionIds =
                    participantRepository.findPinnedMissionParticipants(PREFIX + me).stream()
                            .map(mp -> mp.getMission().getId())
                            .toList();

            assertThat(missionIds)
                    .containsExactlyInAnyOrder(draftPinned.getId(), openPinned.getId());
        }
    }

    @Nested
    @DisplayName("countScheduledInPeriod — 월간 리포트 예정 수")
    class ScheduledCount {

        private static final LocalDate START = LocalDate.of(2026, 10, 1);
        private static final LocalDate END = LocalDate.of(2026, 10, 31);

        @Test
        @DisplayName("1회차는 상태 무관으로 세고, 2회차 이상은 COMPLETED 만 센다")
        void countsFirstSequenceAlwaysAndLaterSequencesOnlyWhenCompleted() {
            String userId = PREFIX + "stats";
            Mission pinned = saveMission(MissionStatus.IN_PROGRESS, true, false);
            MissionParticipant participant =
                    saveParticipant(pinned, "stats", ParticipantStatus.IN_PROGRESS);

            // 10/1: 완료 1건 + 완료 직후 자동 생성된 2회차가 자정에 MISSED → 예정 1
            saveInstance(participant, LocalDate.of(2026, 10, 1), 1, ExecutionStatus.COMPLETED);
            saveInstance(participant, LocalDate.of(2026, 10, 1), 2, ExecutionStatus.MISSED);
            // 10/2: 미수행 → 예정 1
            saveInstance(participant, LocalDate.of(2026, 10, 2), 1, ExecutionStatus.MISSED);
            // 10/3: 오늘 아직 미수행(PENDING) → 예정 1
            saveInstance(participant, LocalDate.of(2026, 10, 3), 1, ExecutionStatus.PENDING);
            // 10/4: 2회 완료 + 3회차 PENDING → 예정 2 (완료한 2회차는 포함, 미완료 3회차는 제외)
            saveInstance(participant, LocalDate.of(2026, 10, 4), 1, ExecutionStatus.COMPLETED);
            saveInstance(participant, LocalDate.of(2026, 10, 4), 2, ExecutionStatus.COMPLETED);
            saveInstance(participant, LocalDate.of(2026, 10, 4), 3, ExecutionStatus.PENDING);
            // 범위 밖·다른 유저는 제외
            saveInstance(participant, LocalDate.of(2026, 9, 30), 1, ExecutionStatus.MISSED);
            saveInstance(participant, LocalDate.of(2026, 11, 1), 1, ExecutionStatus.PENDING);
            MissionParticipant other =
                    saveParticipant(pinned, "stats-other", ParticipantStatus.ACCEPTED);
            saveInstance(other, LocalDate.of(2026, 10, 5), 1, ExecutionStatus.MISSED);

            long scheduled = instanceRepository.countScheduledInPeriod(userId, START, END);
            long completed = instanceRepository.countCompletedInPeriod(userId, START, END);

            assertThat(scheduled).isEqualTo(5);
            assertThat(completed).isEqualTo(3);
        }

        @Test
        @DisplayName("예정이 없는 유저는 0")
        void returnsZeroWhenNoInstances() {
            assertThat(instanceRepository.countScheduledInPeriod(PREFIX + "nobody", START, END))
                    .isZero();
        }
    }

    private Mission saveMission(MissionStatus status, boolean pinned, boolean deleted) {
        Mission mission =
                Mission.builder()
                        .title(PREFIX + status.name() + (pinned ? "-pinned" : "-regular"))
                        .status(status)
                        .visibility(MissionVisibility.PUBLIC)
                        .type(MissionType.PERSONAL)
                        .creatorId(PREFIX + "creator")
                        .isPinned(pinned)
                        .isDeleted(deleted)
                        .build();
        return missionRepository.save(mission);
    }

    private MissionParticipant saveParticipant(
            Mission mission, String userSuffix, ParticipantStatus status) {
        MissionParticipant participant =
                MissionParticipant.builder()
                        .mission(mission)
                        .userId(PREFIX + userSuffix)
                        .status(status)
                        .build();
        return participantRepository.save(participant);
    }

    private void saveInstance(
            MissionParticipant participant,
            LocalDate date,
            int sequenceNumber,
            ExecutionStatus status) {
        DailyMissionInstance instance =
                DailyMissionInstance.createFrom(participant, date, sequenceNumber);
        instance.setStatus(status);
        instanceRepository.save(instance);
    }
}
