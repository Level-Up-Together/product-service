package io.pinkspider.leveluptogethermvp.missionservice.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.pinkspider.global.enums.ExpSourceType;
import io.pinkspider.global.enums.MissionStatus;
import io.pinkspider.global.event.MissionFeedUnsharedEvent;
import io.pinkspider.global.facade.GamificationQueryFacade;
import io.pinkspider.global.facade.dto.UserExperienceDto;
import io.pinkspider.leveluptogethermvp.missionservice.api.exception.MissionExecutionCancelException;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionExecutionCancelTxService.CancelledExecution;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstanceImage;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecutionImage;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionExecutionMode;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceImageRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionImageRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * LUT-860: 완료한 미션 수행 취소의 mission_db 쪽 — 수행 상태 되돌리기와 유저 경험치 취소.
 *
 * <p>완료 시각은 UTC 로 저장되므로 테스트도 {@code LocalDateTime.now(ZoneOffset.UTC)} 를 기준으로 만든다(테스트 JVM 의 기본 시간대와
 * 무관하게 같은 결과).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MissionExecutionCancelTxService (LUT-860 완료한 수행 취소 — 상태·경험치)")
class MissionExecutionCancelTxServiceTest {

    private static final String USER_ID = "user-860";
    private static final Long MISSION_ID = 10L;
    private static final Long PARTICIPANT_ID = 20L;
    private static final Long EXECUTION_ID = 30L;
    private static final Long INSTANCE_ID = 40L;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Mock private MissionParticipantRepository participantRepository;
    @Mock private MissionExecutionRepository executionRepository;
    @Mock private MissionExecutionImageRepository executionImageRepository;
    @Mock private DailyMissionInstanceRepository instanceRepository;
    @Mock private DailyMissionInstanceImageRepository instanceImageRepository;
    @Mock private GamificationQueryFacade gamificationQueryFacade;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private MissionExecutionCancelTxService cancelTxService;

    // ===== 픽스처 =====

    private static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    /** 지금(UTC)의 그 시간대 날짜 */
    private static LocalDate today(ZoneId zone) {
        return LocalDate.now(zone);
    }

    private static Mission mission(
            boolean pinned, MissionType type, String guildId, MissionExecutionMode mode) {
        Mission mission =
                Mission.builder()
                        .title("아침 스트레칭")
                        .description("10분 스트레칭")
                        .creatorId(USER_ID)
                        .status(MissionStatus.IN_PROGRESS)
                        .visibility(MissionVisibility.PUBLIC)
                        .type(type)
                        .guildId(guildId)
                        .categoryId(7L)
                        .categoryName("운동")
                        .expPerCompletion(10)
                        .executionMode(mode)
                        .isPinned(pinned)
                        .build();
        setId(mission, MISSION_ID);
        return mission;
    }

    private static Mission regularMission() {
        return mission(false, MissionType.PERSONAL, null, MissionExecutionMode.TIMED);
    }

    private static Mission pinnedMission() {
        return mission(true, MissionType.PERSONAL, null, MissionExecutionMode.TIMED);
    }

    private static MissionParticipant participant(Mission mission, ParticipantStatus status) {
        MissionParticipant participant =
                MissionParticipant.builder()
                        .mission(mission)
                        .userId(USER_ID)
                        .status(status)
                        .progress(100)
                        .completedAt(nowUtc())
                        .build();
        setId(participant, PARTICIPANT_ID);
        return participant;
    }

    /** 방금(UTC) 완료한 일반 미션 수행 */
    private static MissionExecution completedExecution(MissionParticipant participant, int exp) {
        LocalDateTime completedAt = nowUtc();
        MissionExecution execution =
                MissionExecution.builder()
                        .participant(participant)
                        .executionDate(today(KST))
                        .status(ExecutionStatus.COMPLETED)
                        .expEarned(exp)
                        .note("잘 했다")
                        .build();
        execution.setStartedAt(completedAt.minusMinutes(30));
        execution.setCompletedAt(completedAt);
        setId(execution, EXECUTION_ID);
        return execution;
    }

    /** 방금(UTC) 완료한 고정 미션 인스턴스 */
    private static DailyMissionInstance completedInstance(
            MissionParticipant participant, Long id, int sequence, int exp) {
        LocalDateTime completedAt = nowUtc();
        DailyMissionInstance instance =
                DailyMissionInstance.createFrom(participant, today(KST), sequence);
        instance.setStatus(ExecutionStatus.COMPLETED);
        instance.setStartedAt(completedAt.minusMinutes(30));
        instance.setCompletedAt(completedAt);
        instance.setExpEarned(exp);
        instance.setCompletionCount(1);
        instance.setTotalExpEarned(exp);
        instance.setNote("오늘도 완료");
        setId(instance, id);
        return instance;
    }

    private static DailyMissionInstance siblingInstance(
            MissionParticipant participant,
            Long id,
            int sequence,
            ExecutionStatus status,
            LocalDateTime startedAt) {
        DailyMissionInstance sibling =
                DailyMissionInstance.createFrom(participant, today(KST), sequence);
        sibling.setStatus(status);
        sibling.setStartedAt(startedAt);
        setId(sibling, id);
        return sibling;
    }

    private static MissionExecutionImage executionImage(MissionExecution execution, String url) {
        return MissionExecutionImage.builder()
                .execution(execution)
                .imageUrl(url)
                .sortOrder(0)
                .build();
    }

    private static DailyMissionInstanceImage instanceImage(
            DailyMissionInstance instance, String url) {
        return DailyMissionInstanceImage.builder()
                .instance(instance)
                .imageUrl(url)
                .sortOrder(0)
                .build();
    }

    private static UserExperienceDto expAfterCancel(int expDebt) {
        return new UserExperienceDto(1L, USER_ID, 3, 0, 300, 200, 200, 0.0, 0, expDebt);
    }

    private void givenParticipant(MissionParticipant participant) {
        when(participantRepository.findByMissionIdAndUserId(MISSION_ID, USER_ID))
                .thenReturn(Optional.of(participant));
    }

    private void givenExecution(MissionExecution execution) {
        when(executionRepository.findByParticipantIdAndExecutionDate(
                        PARTICIPANT_ID, execution.getExecutionDate()))
                .thenReturn(Optional.of(execution));
    }

    private void givenInstanceById(DailyMissionInstance instance) {
        when(instanceRepository.findByIdWithParticipantAndMission(instance.getId()))
                .thenReturn(Optional.of(instance));
    }

    private void givenSiblings(DailyMissionInstance instance, DailyMissionInstance... others) {
        List<DailyMissionInstance> all = new java.util.ArrayList<>(List.of(others));
        all.add(instance);
        when(instanceRepository.findByParticipantIdAndInstanceDateOrderBySequenceDesc(
                        PARTICIPANT_ID, instance.getInstanceDate()))
                .thenReturn(all);
    }

    private void givenExpCancelled(int expDebt) {
        when(gamificationQueryFacade.cancelExperience(
                        anyString(), anyInt(), any(), anyLong(), anyString(), any(), any()))
                .thenReturn(expAfterCancel(expDebt));
    }

    private CancelledExecution cancelRegular(MissionExecution execution) {
        return cancelTxService.cancel(MISSION_ID, USER_ID, execution.getExecutionDate(), null, KST);
    }

    private CancelledExecution cancelPinned(DailyMissionInstance instance) {
        return cancelTxService.cancel(
                MISSION_ID, USER_ID, instance.getInstanceDate(), instance.getId(), KST);
    }

    private static void assertCancelError(Throwable thrown, String code) {
        assertThat(thrown).isInstanceOf(MissionExecutionCancelException.class);
        assertThat(((MissionExecutionCancelException) thrown).getCode()).isEqualTo(code);
    }

    // ===== 일반 미션 =====

    @Nested
    @DisplayName("일반 미션")
    class Regular {

        @Test
        @DisplayName("수행을 PENDING 으로 되돌리고 사진 행을 지우며, 참여는 COMPLETED → IN_PROGRESS·진행도 0 이 된다")
        void revertsExecutionAndReopensParticipant() {
            MissionParticipant participant =
                    participant(regularMission(), ParticipantStatus.COMPLETED);
            MissionExecution execution = completedExecution(participant, 30);
            execution.setImageUrl("https://cdn/u1.jpg");
            LocalDateTime completedAt = execution.getCompletedAt();
            givenParticipant(participant);
            givenExecution(execution);
            when(executionImageRepository.findByExecutionIdOrderBySortOrderAsc(EXECUTION_ID))
                    .thenReturn(
                            List.of(
                                    executionImage(execution, "https://cdn/u1.jpg"),
                                    executionImage(execution, "https://cdn/u2.jpg")));
            givenExpCancelled(0);

            CancelledExecution result = cancelRegular(execution);

            // 수행 행
            assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.PENDING);
            assertThat(execution.getStartedAt()).isNull();
            assertThat(execution.getCompletedAt()).isNull();
            assertThat(execution.getExpEarned()).isZero();
            assertThat(execution.getNote()).isNull();
            assertThat(execution.getImageUrl()).isNull();
            verify(executionImageRepository).deleteByExecutionId(EXECUTION_ID);
            verify(executionRepository).save(execution);

            // 참여
            assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.IN_PROGRESS);
            assertThat(participant.getProgress()).isZero();
            assertThat(participant.getCompletedAt()).isNull();
            verify(participantRepository).saveAndFlush(participant);

            // 커밋 뒤 처리에 넘길 값 — 되돌리기 전에 떠 둔 값이어야 한다
            assertThat(result.recordId()).isEqualTo(EXECUTION_ID);
            assertThat(result.missionId()).isEqualTo(MISSION_ID);
            assertThat(result.missionTitle()).isEqualTo("아침 스트레칭");
            assertThat(result.guildId()).isNull();
            assertThat(result.expEarned()).isEqualTo(30);
            assertThat(result.completedAtUtc()).isEqualTo(completedAt);
            assertThat(result.executionDate()).isEqualTo(execution.getExecutionDate());
            assertThat(result.simpleMode()).isFalse();
            assertThat(result.statsRecorded()).isTrue();
            assertThat(result.imageUrls())
                    .containsExactly("https://cdn/u1.jpg", "https://cdn/u2.jpg");
            assertThat(result.remainingSimpleCount()).isZero();
            assertThat(result.expDebt()).isZero();

            // 유저 경험치 — 미션 카테고리로, 미션 ID 를 출처로
            verify(gamificationQueryFacade)
                    .cancelExperience(
                            USER_ID,
                            30,
                            ExpSourceType.MISSION_CANCEL,
                            MISSION_ID,
                            "미션 취소: 아침 스트레칭",
                            7L,
                            "운동");
            // 고정 미션 쪽 저장소·피드 이벤트는 건드리지 않는다
            verify(instanceRepository, never()).delete(any());
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("참여가 아직 ACCEPTED 여도 진행도를 0 으로 되돌린다")
        void acceptedParticipant_progressReset() {
            MissionParticipant participant =
                    participant(regularMission(), ParticipantStatus.ACCEPTED);
            MissionExecution execution = completedExecution(participant, 10);
            givenParticipant(participant);
            givenExecution(execution);
            givenExpCancelled(0);

            cancelRegular(execution);

            assertThat(participant.getProgress()).isZero();
            // 진행도만 맞춘다 — 상태를 진행 중으로 바꾸지 않는다
            assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.ACCEPTED);
            verify(participantRepository).saveAndFlush(participant);
        }

        @Test
        @DisplayName("다른 완료 기록이 남아 있으면 진행도는 100 으로 둔다 (완료 때와 같은 계산)")
        void otherCompletionRemains_progressStays100() {
            MissionParticipant participant =
                    participant(regularMission(), ParticipantStatus.COMPLETED);
            MissionExecution execution = completedExecution(participant, 10);
            givenParticipant(participant);
            givenExecution(execution);
            givenExpCancelled(0);
            when(executionRepository.countByParticipantIdAndStatus(
                            PARTICIPANT_ID, ExecutionStatus.COMPLETED))
                    .thenReturn(1L);

            cancelRegular(execution);

            assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.IN_PROGRESS);
            assertThat(participant.getProgress()).isEqualTo(100);
        }

        @Test
        @DisplayName("참여가 진행 중이면 상태는 그대로, 진행도만 0 이 된다")
        void inProgressParticipant_progressReset() {
            MissionParticipant participant =
                    participant(regularMission(), ParticipantStatus.IN_PROGRESS);
            MissionExecution execution = completedExecution(participant, 10);
            givenParticipant(participant);
            givenExecution(execution);
            givenExpCancelled(0);

            cancelRegular(execution);

            assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.IN_PROGRESS);
            assertThat(participant.getProgress()).isZero();
        }

        @Test
        @DisplayName("참여가 탈퇴 상태면 참여 상태·진행도는 건드리지 않는다 (수행만 되돌린다)")
        void withdrawnParticipant_untouched() {
            MissionParticipant participant =
                    participant(regularMission(), ParticipantStatus.WITHDRAWN);
            MissionExecution execution = completedExecution(participant, 10);
            givenParticipant(participant);
            givenExecution(execution);
            givenExpCancelled(0);

            cancelRegular(execution);

            assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.PENDING);
            assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.WITHDRAWN);
            assertThat(participant.getProgress()).isEqualTo(100);
        }

        @Test
        @DisplayName("일반 미션은 instanceId 를 무시한다 — 그 날짜의 수행 행을 되돌린다")
        void ignoresInstanceId() {
            MissionParticipant participant =
                    participant(regularMission(), ParticipantStatus.COMPLETED);
            MissionExecution execution = completedExecution(participant, 10);
            givenParticipant(participant);
            givenExecution(execution);
            givenExpCancelled(0);

            CancelledExecution result =
                    cancelTxService.cancel(
                            MISSION_ID, USER_ID, execution.getExecutionDate(), 999L, KST);

            assertThat(result.recordId()).isEqualTo(EXECUTION_ID);
            verify(instanceRepository, never()).findByIdWithParticipantAndMission(anyLong());
        }

        @Test
        @DisplayName("체크(SIMPLE) 수행이면 취소 뒤 그날 남은 체크 완료 수(일반 + 고정)를 센다")
        void simpleMode_countsRemainingSimpleCompletions() {
            MissionParticipant participant =
                    participant(
                            mission(false, MissionType.PERSONAL, null, MissionExecutionMode.SIMPLE),
                            ParticipantStatus.COMPLETED);
            MissionExecution execution = completedExecution(participant, 10);
            LocalDate executionDate = execution.getExecutionDate();
            givenParticipant(participant);
            givenExecution(execution);
            when(executionRepository.countSimpleCompletedByUserIdAndDate(USER_ID, executionDate))
                    .thenReturn(2L);
            when(instanceRepository.countSimpleCompletedByUserIdAndDate(USER_ID, executionDate))
                    .thenReturn(1L);
            givenExpCancelled(0);

            CancelledExecution result = cancelRegular(execution);

            assertThat(result.simpleMode()).isTrue();
            assertThat(result.remainingSimpleCount()).isEqualTo(3L);
        }

        @Test
        @DisplayName("타이머(TIMED) 수행이면 남은 체크 완료 수를 조회하지 않는다")
        void timedMode_doesNotCountSimpleCompletions() {
            MissionParticipant participant =
                    participant(regularMission(), ParticipantStatus.COMPLETED);
            MissionExecution execution = completedExecution(participant, 30);
            givenParticipant(participant);
            givenExecution(execution);
            givenExpCancelled(0);

            CancelledExecution result = cancelRegular(execution);

            assertThat(result.simpleMode()).isFalse();
            assertThat(result.remainingSimpleCount()).isZero();
            verify(executionRepository, never()).countSimpleCompletedByUserIdAndDate(any(), any());
            verify(instanceRepository, never()).countSimpleCompletedByUserIdAndDate(any(), any());
        }

        @Test
        @DisplayName("길드 미션이면 길드 ID 를 싣는다")
        void guildMission_returnsGuildId() {
            MissionParticipant participant =
                    participant(
                            mission(false, MissionType.GUILD, "77", MissionExecutionMode.TIMED),
                            ParticipantStatus.COMPLETED);
            MissionExecution execution = completedExecution(participant, 30);
            givenParticipant(participant);
            givenExecution(execution);
            givenExpCancelled(0);

            CancelledExecution result = cancelRegular(execution);

            assertThat(result.guildId()).isEqualTo(77L);
        }

        @Test
        @DisplayName("자동 종료(isAutoCompleted)된 수행은 통계에 반영된 적이 없다 — statsRecorded=false")
        void autoCompleted_statsNotRecorded() {
            MissionParticipant participant =
                    participant(regularMission(), ParticipantStatus.COMPLETED);
            MissionExecution execution = completedExecution(participant, 120);
            execution.setIsAutoCompleted(true);
            givenParticipant(participant);
            givenExecution(execution);
            givenExpCancelled(0);

            CancelledExecution result = cancelRegular(execution);

            assertThat(result.statsRecorded()).isFalse();
            assertThat(execution.getIsAutoCompleted()).isFalse();
        }

        @Test
        @DisplayName("Saga 로 완료한 수행은 길드 경험치를 받은 것으로 본다")
        void sagaCompleted_guildExpGranted() {
            MissionParticipant participant =
                    participant(regularMission(), ParticipantStatus.COMPLETED);
            MissionExecution execution = completedExecution(participant, 10);
            givenParticipant(participant);
            givenExecution(execution);
            givenExpCancelled(0);

            assertThat(cancelRegular(execution).guildExpGranted()).isTrue();
        }

        @Test
        @DisplayName("자동 종료는 길드 경험치 지급에 성공했을 때만 받은 것으로 본다")
        void autoCompleted_guildExpGrantedFollowsFlag() {
            MissionParticipant participant =
                    participant(regularMission(), ParticipantStatus.COMPLETED);
            MissionExecution notGranted = completedExecution(participant, 10);
            notGranted.setIsAutoCompleted(true);
            givenParticipant(participant);
            givenExecution(notGranted);
            givenExpCancelled(0);

            assertThat(cancelRegular(notGranted).guildExpGranted()).isFalse();
        }

        @Test
        @DisplayName("자동 종료여도 길드 경험치를 받았으면 받은 것으로 본다")
        void autoCompletedAndGranted_guildExpGranted() {
            MissionParticipant participant =
                    participant(regularMission(), ParticipantStatus.COMPLETED);
            MissionExecution granted = completedExecution(participant, 10);
            granted.setIsAutoCompleted(true);
            granted.setGuildExpGranted(true);
            givenParticipant(participant);
            givenExecution(granted);
            givenExpCancelled(0);

            assertThat(cancelRegular(granted).guildExpGranted()).isTrue();
        }
    }

    // ===== 사진 =====

    @Nested
    @DisplayName("사진 목록")
    class Images {

        @Test
        @DisplayName("대표 사진 URL 이 사진 행에 없어도 지울 목록에 포함한다")
        void representativeImageMissingFromRows_isIncluded() {
            MissionParticipant participant =
                    participant(regularMission(), ParticipantStatus.COMPLETED);
            MissionExecution execution = completedExecution(participant, 10);
            execution.setImageUrl("https://cdn/legacy.jpg");
            givenParticipant(participant);
            givenExecution(execution);
            when(executionImageRepository.findByExecutionIdOrderBySortOrderAsc(EXECUTION_ID))
                    .thenReturn(List.of(executionImage(execution, "https://cdn/u2.jpg")));
            givenExpCancelled(0);

            CancelledExecution result = cancelRegular(execution);

            assertThat(result.imageUrls())
                    .containsExactly("https://cdn/u2.jpg", "https://cdn/legacy.jpg");
        }

        @Test
        @DisplayName("대표 사진이 이미 사진 행에 있으면 두 번 넣지 않는다")
        void representativeImageAlreadyInRows_notDuplicated() {
            MissionParticipant participant =
                    participant(regularMission(), ParticipantStatus.COMPLETED);
            MissionExecution execution = completedExecution(participant, 10);
            execution.setImageUrl("https://cdn/u1.jpg");
            givenParticipant(participant);
            givenExecution(execution);
            when(executionImageRepository.findByExecutionIdOrderBySortOrderAsc(EXECUTION_ID))
                    .thenReturn(List.of(executionImage(execution, "https://cdn/u1.jpg")));
            givenExpCancelled(0);

            CancelledExecution result = cancelRegular(execution);

            assertThat(result.imageUrls()).containsExactly("https://cdn/u1.jpg");
        }

        @Test
        @DisplayName("대표 사진이 없거나 빈 문자열이면 목록은 사진 행 그대로다")
        void blankRepresentativeImage_ignored() {
            MissionParticipant participant =
                    participant(regularMission(), ParticipantStatus.COMPLETED);
            MissionExecution execution = completedExecution(participant, 10);
            execution.setImageUrl("   ");
            givenParticipant(participant);
            givenExecution(execution);
            givenExpCancelled(0);

            CancelledExecution result = cancelRegular(execution);

            assertThat(result.imageUrls()).isEmpty();
        }

        @Test
        @DisplayName("고정 미션은 인스턴스 사진 행 + 인스턴스 대표 사진을 모으고 사진 행을 지운다")
        void pinned_collectsInstanceImages() {
            MissionParticipant participant =
                    participant(pinnedMission(), ParticipantStatus.IN_PROGRESS);
            DailyMissionInstance instance = completedInstance(participant, INSTANCE_ID, 1, 10);
            instance.setImageUrl("https://cdn/main.jpg");
            givenParticipant(participant);
            givenInstanceById(instance);
            givenSiblings(instance);
            when(instanceImageRepository.findByInstanceIdOrderBySortOrderAsc(INSTANCE_ID))
                    .thenReturn(List.of(instanceImage(instance, "https://cdn/p1.jpg")));
            givenExpCancelled(0);

            CancelledExecution result = cancelPinned(instance);

            assertThat(result.imageUrls())
                    .containsExactly("https://cdn/p1.jpg", "https://cdn/main.jpg");
            verify(instanceImageRepository).deleteByInstanceId(INSTANCE_ID);
            verify(executionImageRepository, never()).deleteByExecutionId(anyLong());
        }
    }

    // ===== 고정 미션 =====

    @Nested
    @DisplayName("고정 미션 — 같은 날 대기 인스턴스가 둘이 되지 않게 맞춘다")
    class Pinned {

        @Test
        @DisplayName("① 손대지 않은 다음 회차가 있으면 그것을 지우고 취소한 회차를 PENDING 으로 되돌린다")
        void untouchedNextExists_deletesNextAndRevertsCancelled() {
            MissionParticipant participant =
                    participant(pinnedMission(), ParticipantStatus.IN_PROGRESS);
            DailyMissionInstance instance = completedInstance(participant, INSTANCE_ID, 1, 10);
            DailyMissionInstance next =
                    siblingInstance(participant, 41L, 2, ExecutionStatus.PENDING, null);
            LocalDateTime completedAt = instance.getCompletedAt();
            givenParticipant(participant);
            givenInstanceById(instance);
            givenSiblings(instance, next);
            givenExpCancelled(0);

            CancelledExecution result = cancelPinned(instance);

            verify(instanceRepository).deleteAll(List.of(next));
            verify(instanceRepository, never()).delete(any());
            verify(instanceRepository).save(instance);
            assertThat(instance.getStatus()).isEqualTo(ExecutionStatus.PENDING);
            assertThat(instance.getStartedAt()).isNull();
            assertThat(instance.getCompletedAt()).isNull();
            assertThat(instance.getExpEarned()).isZero();
            assertThat(instance.getNote()).isNull();
            assertThat(instance.getCompletionCount()).isZero();
            assertThat(instance.getTotalExpEarned()).isZero();

            assertThat(result.recordId()).isEqualTo(INSTANCE_ID);
            assertThat(result.expEarned()).isEqualTo(10);
            assertThat(result.completedAtUtc()).isEqualTo(completedAt);
            assertThat(result.executionDate()).isEqualTo(instance.getInstanceDate());
            assertThat(result.statsRecorded()).isTrue();
            // 고정 미션은 참여 상태를 건드리지 않는다. 진행도는 남은 완료가 없어 0 으로 돌아간다(QA-194 의 반대 방향)
            assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.IN_PROGRESS);
            assertThat(participant.getProgress()).isZero();
            verify(participantRepository).saveAndFlush(participant);
        }

        @Test
        @DisplayName("고정 미션에 다른 완료 인스턴스가 남아 있으면 진행도 100 을 유지한다")
        void pinnedOtherCompletionRemains_progressStays100() {
            MissionParticipant participant =
                    participant(pinnedMission(), ParticipantStatus.IN_PROGRESS);
            DailyMissionInstance instance = completedInstance(participant, INSTANCE_ID, 1, 10);
            givenParticipant(participant);
            givenInstanceById(instance);
            givenSiblings(instance);
            givenExpCancelled(0);
            when(instanceRepository.countByParticipantIdAndStatus(
                            PARTICIPANT_ID, ExecutionStatus.COMPLETED))
                    .thenReturn(3L);

            cancelPinned(instance);

            assertThat(participant.getProgress()).isEqualTo(100);
        }

        @Test
        @DisplayName("② 다음 회차를 이미 진행 중이면 취소한 회차를 지운다 (되돌리지 않는다)")
        void nextInProgress_deletesCancelledInstance() {
            MissionParticipant participant =
                    participant(pinnedMission(), ParticipantStatus.IN_PROGRESS);
            DailyMissionInstance instance = completedInstance(participant, INSTANCE_ID, 1, 25);
            DailyMissionInstance next =
                    siblingInstance(
                            participant,
                            41L,
                            2,
                            ExecutionStatus.IN_PROGRESS,
                            nowUtc().minusMinutes(3));
            givenParticipant(participant);
            givenInstanceById(instance);
            givenSiblings(instance, next);
            givenExpCancelled(0);

            CancelledExecution result = cancelPinned(instance);

            verify(instanceRepository).delete(instance);
            verify(instanceRepository, never()).deleteAll(anyIterable());
            verify(instanceRepository, never()).save(any());
            assertThat(next.getStatus()).isEqualTo(ExecutionStatus.IN_PROGRESS);
            // 지운 회차의 경험치도 되돌린다
            assertThat(result.recordId()).isEqualTo(INSTANCE_ID);
            assertThat(result.expEarned()).isEqualTo(25);
            verify(gamificationQueryFacade)
                    .cancelExperience(
                            USER_ID,
                            25,
                            ExpSourceType.MISSION_CANCEL,
                            MISSION_ID,
                            "미션 취소: 아침 스트레칭",
                            7L,
                            "운동");
        }

        @Test
        @DisplayName("③ 다음 회차가 없으면(하루 횟수를 다 채움) 취소한 회차를 PENDING 으로 되돌린다")
        void noNext_revertsCancelled() {
            MissionParticipant participant =
                    participant(pinnedMission(), ParticipantStatus.IN_PROGRESS);
            DailyMissionInstance earlier =
                    siblingInstance(
                            participant, 39L, 1, ExecutionStatus.COMPLETED, nowUtc().minusHours(3));
            DailyMissionInstance instance = completedInstance(participant, INSTANCE_ID, 2, 10);
            givenParticipant(participant);
            givenInstanceById(instance);
            givenSiblings(instance, earlier);
            givenExpCancelled(0);

            cancelPinned(instance);

            verify(instanceRepository, never()).deleteAll(anyIterable());
            verify(instanceRepository, never()).delete(any());
            verify(instanceRepository).save(instance);
            assertThat(instance.getStatus()).isEqualTo(ExecutionStatus.PENDING);
            assertThat(earlier.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
        }

        @Test
        @DisplayName("시작한 적이 있는 PENDING 회차는 '손대지 않은 다음 회차'가 아니다 — 지우지 않고 취소한 회차만 되돌린다")
        void startedPendingSibling_isNotDeleted() {
            MissionParticipant participant =
                    participant(pinnedMission(), ParticipantStatus.IN_PROGRESS);
            DailyMissionInstance instance = completedInstance(participant, INSTANCE_ID, 1, 10);
            DailyMissionInstance touched =
                    siblingInstance(
                            participant,
                            41L,
                            2,
                            ExecutionStatus.PENDING,
                            nowUtc().minusMinutes(10));
            givenParticipant(participant);
            givenInstanceById(instance);
            givenSiblings(instance, touched);
            givenExpCancelled(0);

            cancelPinned(instance);

            verify(instanceRepository, never()).deleteAll(anyIterable());
            verify(instanceRepository, never()).delete(any());
            verify(instanceRepository).save(instance);
            assertThat(instance.getStatus()).isEqualTo(ExecutionStatus.PENDING);
        }

        @Test
        @DisplayName("손대지 않은 다음 회차와 진행 중 회차가 함께 있으면 대기 회차를 지우는 쪽(①)이 먼저다")
        void untouchedPendingWinsOverInProgress() {
            MissionParticipant participant =
                    participant(pinnedMission(), ParticipantStatus.IN_PROGRESS);
            DailyMissionInstance instance = completedInstance(participant, INSTANCE_ID, 1, 10);
            DailyMissionInstance inProgress =
                    siblingInstance(
                            participant,
                            41L,
                            2,
                            ExecutionStatus.IN_PROGRESS,
                            nowUtc().minusMinutes(3));
            DailyMissionInstance pending =
                    siblingInstance(participant, 42L, 3, ExecutionStatus.PENDING, null);
            givenParticipant(participant);
            givenInstanceById(instance);
            givenSiblings(instance, pending, inProgress);
            givenExpCancelled(0);

            cancelPinned(instance);

            verify(instanceRepository).deleteAll(List.of(pending));
            verify(instanceRepository, never()).delete(any());
            assertThat(instance.getStatus()).isEqualTo(ExecutionStatus.PENDING);
        }

        @Test
        @DisplayName("instanceId 가 없으면 그 날짜의 마지막 완료 인스턴스를 취소한다")
        void withoutInstanceId_usesLastCompletedOfDate() {
            MissionParticipant participant =
                    participant(pinnedMission(), ParticipantStatus.IN_PROGRESS);
            DailyMissionInstance first = completedInstance(participant, 39L, 1, 10);
            DailyMissionInstance last = completedInstance(participant, INSTANCE_ID, 2, 15);
            LocalDate date = last.getInstanceDate();
            givenParticipant(participant);
            when(instanceRepository.findCompletedByParticipantIdAndDate(PARTICIPANT_ID, date))
                    .thenReturn(List.of(first, last));
            when(instanceRepository.findByParticipantIdAndInstanceDateOrderBySequenceDesc(
                            PARTICIPANT_ID, date))
                    .thenReturn(List.of(last, first));
            givenExpCancelled(0);

            CancelledExecution result =
                    cancelTxService.cancel(MISSION_ID, USER_ID, date, null, KST);

            assertThat(result.recordId()).isEqualTo(INSTANCE_ID);
            assertThat(result.expEarned()).isEqualTo(15);
            assertThat(last.getStatus()).isEqualTo(ExecutionStatus.PENDING);
            assertThat(first.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            verify(instanceRepository, never()).findByIdWithParticipantAndMission(anyLong());
        }

        @Test
        @DisplayName("instanceId 가 없고 그 날짜에 완료 인스턴스가 없으면 404 050701")
        void withoutInstanceId_noneCompleted_notFound() {
            MissionParticipant participant =
                    participant(pinnedMission(), ParticipantStatus.IN_PROGRESS);
            LocalDate date = today(KST);
            givenParticipant(participant);
            when(instanceRepository.findCompletedByParticipantIdAndDate(PARTICIPANT_ID, date))
                    .thenReturn(List.of());

            Throwable thrown =
                    catchThrowable(
                            () -> cancelTxService.cancel(MISSION_ID, USER_ID, date, null, KST));

            assertCancelError(thrown, MissionExecutionCancelException.CODE_NOT_FOUND);
            verifyNoInteractions(gamificationQueryFacade);
        }

        @Test
        @DisplayName("없는 instanceId 는 404 050701")
        void unknownInstanceId_notFound() {
            MissionParticipant participant =
                    participant(pinnedMission(), ParticipantStatus.IN_PROGRESS);
            givenParticipant(participant);
            when(instanceRepository.findByIdWithParticipantAndMission(INSTANCE_ID))
                    .thenReturn(Optional.empty());

            Throwable thrown =
                    catchThrowable(
                            () ->
                                    cancelTxService.cancel(
                                            MISSION_ID, USER_ID, today(KST), INSTANCE_ID, KST));

            assertCancelError(thrown, MissionExecutionCancelException.CODE_NOT_FOUND);
        }

        @Test
        @DisplayName("남의(다른 참여의) 인스턴스는 '없음' 404 050701 로 답하고 아무것도 바꾸지 않는다")
        void othersInstance_notFound() {
            MissionParticipant participant =
                    participant(pinnedMission(), ParticipantStatus.IN_PROGRESS);
            MissionParticipant other = participant(pinnedMission(), ParticipantStatus.IN_PROGRESS);
            setId(other, 999L);
            DailyMissionInstance othersInstance = completedInstance(other, INSTANCE_ID, 1, 10);
            givenParticipant(participant);
            givenInstanceById(othersInstance);

            Throwable thrown =
                    catchThrowable(
                            () ->
                                    cancelTxService.cancel(
                                            MISSION_ID, USER_ID, today(KST), INSTANCE_ID, KST));

            assertCancelError(thrown, MissionExecutionCancelException.CODE_NOT_FOUND);
            assertThat(othersInstance.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            verify(instanceImageRepository, never()).deleteByInstanceId(anyLong());
            verify(instanceRepository, never()).save(any());
            verifyNoInteractions(gamificationQueryFacade);
        }

        @Test
        @DisplayName("제목·카테고리는 미션이 아니라 인스턴스에 남긴 값(스냅샷)을 쓴다")
        void usesInstanceSnapshotForTitleAndCategory() {
            MissionParticipant participant =
                    participant(pinnedMission(), ParticipantStatus.IN_PROGRESS);
            DailyMissionInstance instance = completedInstance(participant, INSTANCE_ID, 1, 10);
            instance.setMissionTitle("예전 제목");
            instance.setCategoryId(99L);
            instance.setCategoryName("독서");
            givenParticipant(participant);
            givenInstanceById(instance);
            givenSiblings(instance);
            givenExpCancelled(0);

            CancelledExecution result = cancelPinned(instance);

            assertThat(result.missionTitle()).isEqualTo("예전 제목");
            verify(gamificationQueryFacade)
                    .cancelExperience(
                            USER_ID,
                            10,
                            ExpSourceType.MISSION_CANCEL,
                            MISSION_ID,
                            "미션 취소: 예전 제목",
                            99L,
                            "독서");
        }

        @Test
        @DisplayName("체크(SIMPLE) 고정 미션이면 인스턴스 날짜로 남은 체크 완료 수를 센다")
        void simplePinned_countsByInstanceDate() {
            MissionParticipant participant =
                    participant(
                            mission(true, MissionType.PERSONAL, null, MissionExecutionMode.SIMPLE),
                            ParticipantStatus.IN_PROGRESS);
            DailyMissionInstance instance = completedInstance(participant, INSTANCE_ID, 1, 10);
            LocalDate date = instance.getInstanceDate();
            givenParticipant(participant);
            givenInstanceById(instance);
            givenSiblings(instance);
            when(executionRepository.countSimpleCompletedByUserIdAndDate(USER_ID, date))
                    .thenReturn(1L);
            when(instanceRepository.countSimpleCompletedByUserIdAndDate(USER_ID, date))
                    .thenReturn(3L);
            givenExpCancelled(0);

            CancelledExecution result = cancelPinned(instance);

            assertThat(result.simpleMode()).isTrue();
            assertThat(result.remainingSimpleCount()).isEqualTo(4L);
        }

        @Test
        @DisplayName("자동 종료된 인스턴스는 statsRecorded=false, 길드 고정 미션이면 길드 ID 를 싣는다")
        void autoCompletedGuildPinned() {
            MissionParticipant participant =
                    participant(
                            mission(true, MissionType.GUILD, "55", MissionExecutionMode.TIMED),
                            ParticipantStatus.IN_PROGRESS);
            DailyMissionInstance instance = completedInstance(participant, INSTANCE_ID, 1, 120);
            instance.setIsAutoCompleted(true);
            givenParticipant(participant);
            givenInstanceById(instance);
            givenSiblings(instance);
            givenExpCancelled(0);

            CancelledExecution result = cancelPinned(instance);

            assertThat(result.statsRecorded()).isFalse();
            assertThat(result.guildId()).isEqualTo(55L);
        }

        @Test
        @DisplayName("공유된 인스턴스면 인스턴스 ID 로 피드 삭제 이벤트를 낸다")
        void sharedInstance_publishesUnsharedEvent() {
            MissionParticipant participant =
                    participant(pinnedMission(), ParticipantStatus.IN_PROGRESS);
            DailyMissionInstance instance = completedInstance(participant, INSTANCE_ID, 1, 10);
            instance.setIsSharedToFeed(true);
            givenParticipant(participant);
            givenInstanceById(instance);
            givenSiblings(instance);
            givenExpCancelled(0);

            cancelPinned(instance);

            ArgumentCaptor<MissionFeedUnsharedEvent> captor =
                    ArgumentCaptor.forClass(MissionFeedUnsharedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().userId()).isEqualTo(USER_ID);
            assertThat(captor.getValue().executionId()).isEqualTo(INSTANCE_ID);
            assertThat(instance.getIsSharedToFeed()).isFalse();
        }

        @Test
        @DisplayName("인스턴스의 경험치가 null 이면 0 으로 보고 경험치를 건드리지 않는다")
        void nullExpEarned_treatedAsZero() {
            MissionParticipant participant =
                    participant(pinnedMission(), ParticipantStatus.IN_PROGRESS);
            DailyMissionInstance instance = completedInstance(participant, INSTANCE_ID, 1, 0);
            instance.setExpEarned(null);
            givenParticipant(participant);
            givenInstanceById(instance);
            givenSiblings(instance);

            CancelledExecution result = cancelPinned(instance);

            assertThat(result.expEarned()).isZero();
            verifyNoInteractions(gamificationQueryFacade);
        }
    }

    // ===== 오류 =====

    @Nested
    @DisplayName("오류 — 050701·050702·050703")
    class Errors {

        @Test
        @DisplayName("참여 정보가 없으면 404 050701")
        void participantMissing_notFound() {
            when(participantRepository.findByMissionIdAndUserId(MISSION_ID, USER_ID))
                    .thenReturn(Optional.empty());

            Throwable thrown =
                    catchThrowable(
                            () ->
                                    cancelTxService.cancel(
                                            MISSION_ID, USER_ID, today(KST), null, KST));

            assertCancelError(thrown, MissionExecutionCancelException.CODE_NOT_FOUND);
            verifyNoInteractions(executionRepository, instanceRepository, gamificationQueryFacade);
        }

        @Test
        @DisplayName("그 날짜의 수행 기록이 없으면 404 050701")
        void executionMissing_notFound() {
            MissionParticipant participant =
                    participant(regularMission(), ParticipantStatus.IN_PROGRESS);
            LocalDate date = today(KST);
            givenParticipant(participant);
            when(executionRepository.findByParticipantIdAndExecutionDate(PARTICIPANT_ID, date))
                    .thenReturn(Optional.empty());

            Throwable thrown =
                    catchThrowable(
                            () -> cancelTxService.cancel(MISSION_ID, USER_ID, date, null, KST));

            assertCancelError(thrown, MissionExecutionCancelException.CODE_NOT_FOUND);
        }

        @ParameterizedTest(name = "일반 미션 수행 상태 {0} → 400 050702")
        @EnumSource(
                value = ExecutionStatus.class,
                names = {"COMPLETED"},
                mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("완료한 수행이 아니면 400 050702 이고 아무것도 바꾸지 않는다")
        void notCompleted_badRequest(ExecutionStatus status) {
            MissionParticipant participant =
                    participant(regularMission(), ParticipantStatus.IN_PROGRESS);
            MissionExecution execution = completedExecution(participant, 0);
            execution.setStatus(status);
            givenParticipant(participant);
            givenExecution(execution);

            Throwable thrown = catchThrowable(() -> cancelRegular(execution));

            assertCancelError(thrown, MissionExecutionCancelException.CODE_NOT_COMPLETED);
            assertThat(execution.getStatus()).isEqualTo(status);
            verify(executionImageRepository, never()).deleteByExecutionId(anyLong());
            verify(executionRepository, never()).save(any());
            verify(executionRepository, never()).flush();
            verify(participantRepository, never()).saveAndFlush(any());
            verifyNoInteractions(gamificationQueryFacade, eventPublisher);
        }

        @Test
        @DisplayName("상태는 COMPLETED 인데 완료 시각이 없으면 400 050702")
        void completedWithoutCompletedAt_badRequest() {
            MissionParticipant participant =
                    participant(regularMission(), ParticipantStatus.COMPLETED);
            MissionExecution execution = completedExecution(participant, 10);
            execution.setCompletedAt(null);
            givenParticipant(participant);
            givenExecution(execution);

            Throwable thrown = catchThrowable(() -> cancelRegular(execution));

            assertCancelError(thrown, MissionExecutionCancelException.CODE_NOT_COMPLETED);
        }

        @Test
        @DisplayName("고정 미션 인스턴스가 진행 중이면 400 050702")
        void pinnedNotCompleted_badRequest() {
            MissionParticipant participant =
                    participant(pinnedMission(), ParticipantStatus.IN_PROGRESS);
            DailyMissionInstance instance = completedInstance(participant, INSTANCE_ID, 1, 0);
            instance.setStatus(ExecutionStatus.IN_PROGRESS);
            instance.setCompletedAt(null);
            givenParticipant(participant);
            givenInstanceById(instance);

            Throwable thrown = catchThrowable(() -> cancelPinned(instance));

            assertCancelError(thrown, MissionExecutionCancelException.CODE_NOT_COMPLETED);
            verify(instanceImageRepository, never()).deleteByInstanceId(anyLong());
            verify(instanceRepository, never()).save(any());
        }

        @Test
        @DisplayName("어제 완료한 수행은 400 050703 이고 아무것도 바꾸지 않는다")
        void completedYesterday_notToday() {
            MissionParticipant participant =
                    participant(regularMission(), ParticipantStatus.COMPLETED);
            MissionExecution execution = completedExecution(participant, 30);
            execution.setCompletedAt(nowUtc().minusDays(1));
            givenParticipant(participant);
            givenExecution(execution);

            Throwable thrown = catchThrowable(() -> cancelRegular(execution));

            assertCancelError(thrown, MissionExecutionCancelException.CODE_NOT_TODAY);
            assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            assertThat(execution.getExpEarned()).isEqualTo(30);
            assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.COMPLETED);
            verify(executionImageRepository, never()).deleteByExecutionId(anyLong());
            verify(executionRepository, never()).save(any());
            verifyNoInteractions(gamificationQueryFacade, eventPublisher);
        }

        @Test
        @DisplayName("고정 미션 인스턴스를 어제 완료했으면 400 050703")
        void pinnedCompletedYesterday_notToday() {
            MissionParticipant participant =
                    participant(pinnedMission(), ParticipantStatus.IN_PROGRESS);
            DailyMissionInstance instance = completedInstance(participant, INSTANCE_ID, 1, 10);
            instance.setCompletedAt(nowUtc().minusDays(1));
            givenParticipant(participant);
            givenInstanceById(instance);

            Throwable thrown = catchThrowable(() -> cancelPinned(instance));

            assertCancelError(thrown, MissionExecutionCancelException.CODE_NOT_TODAY);
            assertThat(instance.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            verify(instanceRepository, never()).deleteAll(anyIterable());
        }
    }

    // ===== "오늘" 판정 — 완료 시각(UTC)을 요청 시간대 날짜로 바꿔 본다 =====

    @Nested
    @DisplayName("'오늘' 판정 — completedAt(UTC)을 넘겨받은 시간대의 날짜로 바꾼다")
    class TodayByZone {

        /** 지금(UTC)의 로컬 시각이 {@code localSecondOfDay} 가 되는 고정 오프셋 시간대 */
        private ZoneId zoneWhereNowIs(LocalDateTime nowUtc, int localSecondOfDay) {
            int offset = localSecondOfDay - nowUtc.toLocalTime().toSecondOfDay();
            while (offset > 12 * 3600) {
                offset -= 24 * 3600;
            }
            while (offset < -12 * 3600) {
                offset += 24 * 3600;
            }
            return ZoneOffset.ofTotalSeconds(offset);
        }

        private MissionExecution executionCompletedAt(
                MissionParticipant participant, LocalDateTime completedAtUtc) {
            MissionExecution execution = completedExecution(participant, 10);
            execution.setCompletedAt(completedAtUtc);
            return execution;
        }

        @Test
        @DisplayName("그 시간대 자정 1초 전에 완료했으면 어제 — 050703, 자정 정각이면 오늘 — 취소된다")
        void midnightBoundaryInZone() {
            LocalDateTime kstMidnightUtc =
                    today(KST)
                            .atStartOfDay(KST)
                            .withZoneSameInstant(ZoneOffset.UTC)
                            .toLocalDateTime();

            // 자정 1초 전 → 어제
            MissionParticipant participant =
                    participant(regularMission(), ParticipantStatus.COMPLETED);
            MissionExecution before =
                    executionCompletedAt(participant, kstMidnightUtc.minusSeconds(1));
            givenParticipant(participant);
            givenExecution(before);

            Throwable thrown = catchThrowable(() -> cancelRegular(before));
            assertCancelError(thrown, MissionExecutionCancelException.CODE_NOT_TODAY);

            // 자정 정각 → 오늘
            MissionExecution atMidnight = executionCompletedAt(participant, kstMidnightUtc);
            givenExecution(atMidnight);
            givenExpCancelled(0);

            CancelledExecution result = cancelRegular(atMidnight);

            assertThat(result.completedAtUtc()).isEqualTo(kstMidnightUtc);
            assertThat(atMidnight.getStatus()).isEqualTo(ExecutionStatus.PENDING);
        }

        @Test
        @DisplayName("같은 완료 시각이라도 시간대에 따라 판정이 달라진다 — 2시간 전 완료: 로컬 01시면 어제, 로컬 12시면 오늘")
        void sameInstant_differentVerdictByZone() {
            LocalDateTime now = nowUtc();
            LocalDateTime completedAt = now.minusHours(2);
            ZoneId earlyMorning = zoneWhereNowIs(now, 1 * 3600); // 완료는 로컬 전날 23시
            ZoneId noon = zoneWhereNowIs(now, 12 * 3600); // 완료는 로컬 오늘 10시

            MissionParticipant participant =
                    participant(regularMission(), ParticipantStatus.COMPLETED);
            MissionExecution execution = executionCompletedAt(participant, completedAt);
            givenParticipant(participant);
            givenExecution(execution);

            Throwable thrown =
                    catchThrowable(
                            () ->
                                    cancelTxService.cancel(
                                            MISSION_ID,
                                            USER_ID,
                                            execution.getExecutionDate(),
                                            null,
                                            earlyMorning));
            assertCancelError(thrown, MissionExecutionCancelException.CODE_NOT_TODAY);
            assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);

            givenExpCancelled(0);
            cancelTxService.cancel(MISSION_ID, USER_ID, execution.getExecutionDate(), null, noon);

            assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.PENDING);
        }

        @Test
        @DisplayName("UTC 날짜가 아니라 시간대 날짜로 본다 — UTC+14·UTC-11 어느 쪽에서도 방금 완료한 수행은 오늘이다")
        void usesZoneDateNotUtcDate() {
            // UTC+14 는 UTC 10시 이후, UTC-11 은 UTC 11시 이전에 UTC 와 날짜가 다르다 —
            // 언제 돌려도 둘 중 하나는 "완료 시각의 UTC 날짜 ≠ 그 시간대의 오늘" 이다.
            for (ZoneId zone :
                    List.of(ZoneId.of("Pacific/Kiritimati"), ZoneId.of("Pacific/Pago_Pago"))) {
                MissionParticipant participant =
                        participant(regularMission(), ParticipantStatus.COMPLETED);
                MissionExecution execution = executionCompletedAt(participant, nowUtc());
                givenParticipant(participant);
                givenExecution(execution);
                givenExpCancelled(0);

                cancelTxService.cancel(
                        MISSION_ID, USER_ID, execution.getExecutionDate(), null, zone);

                assertThat(execution.getStatus())
                        .as("zone=%s", zone)
                        .isEqualTo(ExecutionStatus.PENDING);
            }
        }

        @Test
        @DisplayName("고정 미션도 같은 기준 — 2시간 전 완료한 인스턴스가 로컬 01시 시간대에서는 050703")
        void pinnedUsesSameRule() {
            LocalDateTime now = nowUtc();
            ZoneId earlyMorning = zoneWhereNowIs(now, 1 * 3600);
            MissionParticipant participant =
                    participant(pinnedMission(), ParticipantStatus.IN_PROGRESS);
            DailyMissionInstance instance = completedInstance(participant, INSTANCE_ID, 1, 10);
            instance.setCompletedAt(now.minusHours(2));
            givenParticipant(participant);
            givenInstanceById(instance);

            Throwable thrown =
                    catchThrowable(
                            () ->
                                    cancelTxService.cancel(
                                            MISSION_ID,
                                            USER_ID,
                                            instance.getInstanceDate(),
                                            INSTANCE_ID,
                                            earlyMorning));

            assertCancelError(thrown, MissionExecutionCancelException.CODE_NOT_TODAY);
        }
    }

    // ===== 경험치·순서·피드 =====

    @Nested
    @DisplayName("유저 경험치 취소")
    class Experience {

        @Test
        @DisplayName("경험치가 0 인 수행(한도에 걸린 체크 미션 등)은 gamification 을 부르지 않는다")
        void zeroExp_doesNotCallGamification() {
            MissionParticipant participant =
                    participant(regularMission(), ParticipantStatus.COMPLETED);
            MissionExecution execution = completedExecution(participant, 0);
            givenParticipant(participant);
            givenExecution(execution);

            CancelledExecution result = cancelRegular(execution);

            assertThat(result.expEarned()).isZero();
            assertThat(result.expDebt()).isZero();
            assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.PENDING);
            verifyNoInteractions(gamificationQueryFacade);
        }

        @Test
        @DisplayName("수행의 경험치가 null 이어도 0 으로 보고 gamification 을 부르지 않는다")
        void nullExp_doesNotCallGamification() {
            MissionParticipant participant =
                    participant(regularMission(), ParticipantStatus.COMPLETED);
            MissionExecution execution = completedExecution(participant, 0);
            execution.setExpEarned(null);
            givenParticipant(participant);
            givenExecution(execution);

            CancelledExecution result = cancelRegular(execution);

            assertThat(result.expEarned()).isZero();
            verifyNoInteractions(gamificationQueryFacade);
        }

        @Test
        @DisplayName("경험치 취소 결과의 누적 마이너스 경험치(exp_debt)를 싣는다")
        void returnsExpDebtFromGamification() {
            MissionParticipant participant =
                    participant(regularMission(), ParticipantStatus.COMPLETED);
            MissionExecution execution = completedExecution(participant, 50);
            givenParticipant(participant);
            givenExecution(execution);
            givenExpCancelled(35);

            CancelledExecution result = cancelRegular(execution);

            assertThat(result.expDebt()).isEqualTo(35);
        }

        @Test
        @DisplayName("경험치 취소 결과가 null 이거나 exp_debt 가 null 이면 0 으로 본다")
        void nullExpResult_debtZero() {
            MissionParticipant participant =
                    participant(regularMission(), ParticipantStatus.COMPLETED);
            MissionExecution first = completedExecution(participant, 50);
            givenParticipant(participant);
            givenExecution(first);
            when(gamificationQueryFacade.cancelExperience(
                            anyString(), anyInt(), any(), anyLong(), anyString(), any(), any()))
                    .thenReturn(null)
                    .thenReturn(
                            new UserExperienceDto(
                                    1L, USER_ID, 3, 0, 300, 200, 200, 0.0, null, null));

            assertThat(cancelRegular(first).expDebt()).isZero();

            MissionExecution second = completedExecution(participant, 50);
            participant.setStatus(ParticipantStatus.COMPLETED);
            givenExecution(second);

            assertThat(cancelRegular(second).expDebt()).isZero();
        }

        @Test
        @DisplayName("경험치 취소가 예외를 던지면 그대로 나간다 — 트랜잭션이 롤백돼 수행은 완료로 남는다")
        void cancelExperienceThrows_propagates() {
            MissionParticipant participant =
                    participant(regularMission(), ParticipantStatus.COMPLETED);
            MissionExecution execution = completedExecution(participant, 50);
            execution.setIsSharedToFeed(true);
            givenParticipant(participant);
            givenExecution(execution);
            IllegalStateException failure = new IllegalStateException("gamification down");
            when(gamificationQueryFacade.cancelExperience(
                            anyString(), anyInt(), any(), anyLong(), anyString(), any(), any()))
                    .thenThrow(failure);

            assertThatThrownBy(() -> cancelRegular(execution)).isSameAs(failure);

            // 피드 삭제 이벤트는 경험치 취소 뒤에만 낸다
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("상태 변경을 flush 한 뒤에 경험치를 되돌린다 (충돌은 경험치를 건드리기 전에 드러난다)")
        void flushesBeforeCancellingExperience() {
            MissionParticipant participant =
                    participant(regularMission(), ParticipantStatus.COMPLETED);
            MissionExecution execution = completedExecution(participant, 50);
            givenParticipant(participant);
            givenExecution(execution);
            givenExpCancelled(0);

            cancelRegular(execution);

            InOrder order =
                    inOrder(executionRepository, participantRepository, gamificationQueryFacade);
            order.verify(executionRepository).save(execution);
            order.verify(executionRepository).flush();
            // 참여 진행도 쓰기도 경험치를 건드리기 전에 내보낸다
            order.verify(participantRepository).saveAndFlush(participant);
            order.verify(gamificationQueryFacade)
                    .cancelExperience(
                            anyString(), anyInt(), any(), anyLong(), anyString(), any(), any());
        }

        @Test
        @DisplayName("고정 미션도 flush 뒤에 경험치를 되돌린다")
        void pinned_flushesBeforeCancellingExperience() {
            MissionParticipant participant =
                    participant(pinnedMission(), ParticipantStatus.IN_PROGRESS);
            DailyMissionInstance instance = completedInstance(participant, INSTANCE_ID, 1, 10);
            givenParticipant(participant);
            givenInstanceById(instance);
            givenSiblings(instance);
            givenExpCancelled(0);

            cancelPinned(instance);

            InOrder order =
                    inOrder(instanceRepository, executionRepository, gamificationQueryFacade);
            order.verify(instanceRepository).save(instance);
            order.verify(executionRepository).flush();
            order.verify(gamificationQueryFacade)
                    .cancelExperience(
                            anyString(), anyInt(), any(), anyLong(), anyString(), any(), any());
        }

        @Test
        @DisplayName("flush 가 실패하면(다른 쓰기와 충돌) 경험치는 건드리지 않는다")
        void flushFails_experienceUntouched() {
            MissionParticipant participant =
                    participant(regularMission(), ParticipantStatus.COMPLETED);
            MissionExecution execution = completedExecution(participant, 50);
            givenParticipant(participant);
            givenExecution(execution);
            RuntimeException conflict =
                    new org.springframework.orm.ObjectOptimisticLockingFailureException(
                            MissionExecution.class, EXECUTION_ID);
            org.mockito.Mockito.doThrow(conflict).when(executionRepository).flush();

            assertThatThrownBy(() -> cancelRegular(execution)).isSameAs(conflict);

            verifyNoInteractions(gamificationQueryFacade, eventPublisher);
        }
    }

    @Nested
    @DisplayName("피드")
    class Feed {

        @Test
        @DisplayName("피드에 공유된 수행이면 수행 ID 로 MissionFeedUnsharedEvent 를 낸다")
        void shared_publishesUnsharedEvent() {
            MissionParticipant participant =
                    participant(regularMission(), ParticipantStatus.COMPLETED);
            MissionExecution execution = completedExecution(participant, 10);
            execution.setIsSharedToFeed(true);
            givenParticipant(participant);
            givenExecution(execution);
            givenExpCancelled(0);

            cancelRegular(execution);

            ArgumentCaptor<MissionFeedUnsharedEvent> captor =
                    ArgumentCaptor.forClass(MissionFeedUnsharedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().userId()).isEqualTo(USER_ID);
            assertThat(captor.getValue().executionId()).isEqualTo(EXECUTION_ID);
            // 수행 행의 공유 표시도 내린다
            assertThat(execution.getIsSharedToFeed()).isFalse();
        }

        @Test
        @DisplayName("공유하지 않은 수행이면 이벤트를 내지 않는다")
        void notShared_noEvent() {
            MissionParticipant participant =
                    participant(regularMission(), ParticipantStatus.COMPLETED);
            MissionExecution execution = completedExecution(participant, 10);
            givenParticipant(participant);
            givenExecution(execution);
            givenExpCancelled(0);

            cancelRegular(execution);

            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("경험치가 0 이어도 공유된 수행이면 피드 삭제 이벤트는 낸다")
        void sharedWithZeroExp_stillPublishes() {
            MissionParticipant participant =
                    participant(regularMission(), ParticipantStatus.COMPLETED);
            MissionExecution execution = completedExecution(participant, 0);
            execution.setIsSharedToFeed(true);
            givenParticipant(participant);
            givenExecution(execution);

            cancelRegular(execution);

            verify(eventPublisher).publishEvent(any(MissionFeedUnsharedEvent.class));
            verifyNoInteractions(gamificationQueryFacade);
        }
    }
}
