package io.pinkspider.leveluptogethermvp.missionservice.scheduler;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.event.MissionReminderEvent;
import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.global.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MissionReminderScheduler 테스트 (LUT-282)")
class MissionReminderSchedulerTest {

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private MissionParticipantRepository participantRepository;

    @Mock
    private MissionExecutionRepository executionRepository;

    @Mock
    private DailyMissionInstanceRepository instanceRepository;

    @Mock
    private UserQueryFacade userQueryFacadeService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private MissionReminderScheduler scheduler;

    private static final String USER_ID = "user-1";

    // 2026-07-27(월) 00:00 UTC = 서울(UTC+9) 기준 월요일 09:00
    private static final Instant MON_00_UTC = Instant.parse("2026-07-27T00:00:00Z");
    // 2026-07-27(월) 00:30 UTC = 콜카타(UTC+5:30) 기준 월요일 06:00
    private static final Instant MON_0030_UTC = Instant.parse("2026-07-27T00:30:00Z");

    private Mission reminderMission;

    @BeforeEach
    void setUp() {
        reminderMission = Mission.builder()
            .title("아침 운동")
            .status(MissionStatus.IN_PROGRESS)
            .type(MissionType.PERSONAL)
            .creatorId(USER_ID)
            .isPinned(false)
            .build();
        reminderMission.updateReminder(9, 0, List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY));
        setId(reminderMission, 1L);

        when(userQueryFacadeService.getPreferredTimezone(USER_ID)).thenReturn("Asia/Seoul");
        when(missionRepository.findActiveReminderMissions()).thenReturn(List.of(reminderMission));
        when(participantRepository.findByMissionIdAndUserId(anyLong(), anyString()))
            .thenReturn(Optional.empty());
        scheduler.setClock(Clock.fixed(MON_00_UTC, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("설정 요일·시각(유저 로컬)에 리마인더 이벤트를 발행한다")
    void sendReminders_dueMission_publishesEvent() {
        scheduler.sendReminders();

        ArgumentCaptor<MissionReminderEvent> captor =
            ArgumentCaptor.forClass(MissionReminderEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().missionId()).isEqualTo(1L);
        assertThat(captor.getValue().missionTitle()).isEqualTo("아침 운동");
    }

    @Test
    @DisplayName("설정 시각이 아니면 발행하지 않는다")
    void sendReminders_wrongHour_skips() {
        reminderMission.updateReminder(10, 0, List.of(DayOfWeek.MONDAY));

        scheduler.sendReminders();

        verify(eventPublisher, never()).publishEvent(any(MissionReminderEvent.class));
    }

    @Test
    @DisplayName("설정 요일이 아니면 발행하지 않는다")
    void sendReminders_wrongDay_skips() {
        reminderMission.updateReminder(9, 0, List.of(DayOfWeek.TUESDAY));

        scheduler.sendReminders();

        verify(eventPublisher, never()).publishEvent(any(MissionReminderEvent.class));
    }

    @Test
    @DisplayName("30분 오프셋 타임존은 UTC 30분 실행에서 로컬 정각으로 매칭된다")
    void sendReminders_halfHourZone_matchesOnHalfHourRun() {
        when(userQueryFacadeService.getPreferredTimezone(USER_ID)).thenReturn("Asia/Kolkata");
        reminderMission.updateReminder(6, 0, List.of(DayOfWeek.MONDAY));

        // UTC 정각 실행: 콜카타 로컬 05:30 → 분(minute) 30 이상이라 미발행
        scheduler.setClock(Clock.fixed(MON_00_UTC, ZoneOffset.UTC));
        scheduler.sendReminders();
        verify(eventPublisher, never()).publishEvent(any(MissionReminderEvent.class));

        // UTC 30분 실행: 콜카타 로컬 06:00 → 발행
        scheduler.setClock(Clock.fixed(MON_0030_UTC, ZoneOffset.UTC));
        scheduler.sendReminders();
        verify(eventPublisher).publishEvent(any(MissionReminderEvent.class));
    }

    @Test
    @DisplayName("LUT-295: 30분 설정 미션은 로컬 30분 구간 실행에서만 발행된다")
    void sendReminders_minute30_matchesOnHalfHourWindow() {
        reminderMission.updateReminder(9, 30, List.of(DayOfWeek.MONDAY));

        // UTC 정각 실행: 서울 로컬 09:00 → 0분 구간이라 미발행
        scheduler.setClock(Clock.fixed(MON_00_UTC, ZoneOffset.UTC));
        scheduler.sendReminders();
        verify(eventPublisher, never()).publishEvent(any(MissionReminderEvent.class));

        // UTC 30분 실행: 서울 로컬 09:30 → 발행
        scheduler.setClock(Clock.fixed(MON_0030_UTC, ZoneOffset.UTC));
        scheduler.sendReminders();
        verify(eventPublisher).publishEvent(any(MissionReminderEvent.class));
    }

    @Test
    @DisplayName("LUT-295: 30분 오프셋 타임존의 30분 설정은 UTC 정각 실행에서 매칭된다")
    void sendReminders_minute30_halfHourZone_matchesOnTopOfHourRun() {
        when(userQueryFacadeService.getPreferredTimezone(USER_ID)).thenReturn("Asia/Kolkata");
        reminderMission.updateReminder(5, 30, List.of(DayOfWeek.MONDAY));

        // UTC 30분 실행: 콜카타 로컬 06:00 → 시(5) 불일치라 미발행
        scheduler.setClock(Clock.fixed(MON_0030_UTC, ZoneOffset.UTC));
        scheduler.sendReminders();
        verify(eventPublisher, never()).publishEvent(any(MissionReminderEvent.class));

        // UTC 정각 실행: 콜카타 로컬 05:30 → 시(5)+30분 구간 매칭 → 발행
        scheduler.setClock(Clock.fixed(MON_00_UTC, ZoneOffset.UTC));
        scheduler.sendReminders();
        verify(eventPublisher).publishEvent(any(MissionReminderEvent.class));
    }

    @Test
    @DisplayName("당일(유저 로컬) 이미 완료한 일반 미션은 발행하지 않는다")
    void sendReminders_completedToday_skips() {
        MissionParticipant participant = MissionParticipant.builder()
            .mission(reminderMission)
            .userId(USER_ID)
            .build();
        setId(participant, 11L);
        MissionExecution completed = MissionExecution.builder()
            .participant(participant)
            .executionDate(LocalDate.of(2026, 7, 27))
            .status(ExecutionStatus.COMPLETED)
            .build();

        when(participantRepository.findByMissionIdAndUserId(1L, USER_ID))
            .thenReturn(Optional.of(participant));
        when(executionRepository.findByParticipantIdAndExecutionDate(11L, LocalDate.of(2026, 7, 27)))
            .thenReturn(Optional.of(completed));

        scheduler.sendReminders();

        verify(eventPublisher, never()).publishEvent(any(MissionReminderEvent.class));
    }

    @Test
    @DisplayName("당일 완료한 고정 미션은 발행하지 않는다")
    void sendReminders_pinnedCompletedToday_skips() {
        reminderMission.setIsPinned(true);
        MissionParticipant participant = MissionParticipant.builder()
            .mission(reminderMission)
            .userId(USER_ID)
            .build();
        setId(participant, 11L);

        when(participantRepository.findByMissionIdAndUserId(1L, USER_ID))
            .thenReturn(Optional.of(participant));
        when(instanceRepository.countCompletedByParticipantIdAndDate(11L, LocalDate.of(2026, 7, 27)))
            .thenReturn(1L);

        scheduler.sendReminders();

        verify(eventPublisher, never()).publishEvent(any(MissionReminderEvent.class));
    }

    @Test
    @DisplayName("타임존 조회 실패 시 Asia/Seoul 폴백으로 동작한다")
    void sendReminders_timezoneLookupFails_fallsBackToSeoul() {
        when(userQueryFacadeService.getPreferredTimezone(USER_ID))
            .thenThrow(new RuntimeException("user_db unavailable"));

        scheduler.sendReminders();

        // 서울 폴백: 월요일 09:00 → 설정(월 09시)과 일치해 발행
        verify(eventPublisher).publishEvent(any(MissionReminderEvent.class));
    }

    @Test
    @DisplayName("개별 미션 처리 실패가 다른 미션 발송을 막지 않는다")
    void sendReminders_oneMissionFails_othersContinue() {
        Mission failing = Mission.builder()
            .title("실패 미션")
            .status(MissionStatus.IN_PROGRESS)
            .type(MissionType.PERSONAL)
            .creatorId("user-err")
            .isPinned(false)
            .build();
        failing.updateReminder(9, 0, List.of(DayOfWeek.MONDAY));
        setId(failing, 2L);

        when(missionRepository.findActiveReminderMissions())
            .thenReturn(List.of(failing, reminderMission));
        // user-err 참가자 조회에서 예외 발생 (getPreferredTimezone 은 내부 try/catch 로 폴백되므로
        // 폴백이 없는 참가자 조회 단계에서 실패시킨다)
        when(userQueryFacadeService.getPreferredTimezone("user-err")).thenReturn("Asia/Seoul");
        when(participantRepository.findByMissionIdAndUserId(2L, "user-err"))
            .thenThrow(new RuntimeException("db error"));

        scheduler.sendReminders();

        ArgumentCaptor<MissionReminderEvent> captor =
            ArgumentCaptor.forClass(MissionReminderEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().missionId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("리마인더 대상 미션이 없으면 아무 것도 하지 않는다")
    void sendReminders_noMissions_noop() {
        when(missionRepository.findActiveReminderMissions()).thenReturn(List.of());

        scheduler.sendReminders();

        verify(eventPublisher, never()).publishEvent(any());
    }
}
