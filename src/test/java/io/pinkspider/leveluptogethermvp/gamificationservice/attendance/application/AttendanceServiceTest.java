package io.pinkspider.leveluptogethermvp.gamificationservice.attendance.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.AttendanceRecord;
import io.pinkspider.global.cache.AttendanceRewardConfigCacheService;
import io.pinkspider.global.enums.ExpSourceType;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.AttendanceRecordRepository;
import io.pinkspider.leveluptogethermvp.metaservice.attendancerewardconfig.domain.entity.AttendanceRewardConfig;
import io.pinkspider.leveluptogethermvp.metaservice.attendancerewardconfig.domain.enums.AttendanceRewardType;
import io.pinkspider.leveluptogethermvp.gamificationservice.attendance.domain.dto.AttendanceCheckInResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.attendance.domain.dto.MonthlyAttendanceResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.experience.application.UserExperienceService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;

    @Mock
    private AttendanceRewardConfigCacheService rewardConfigCacheService;

    @Mock
    private UserExperienceService userExperienceService;

    @InjectMocks
    private AttendanceService attendanceService;

    private static final String TEST_USER_ID = "test-user-123";

    private AttendanceRecord createTestAttendanceRecord(Long id, String userId, LocalDate date, int consecutiveDays) {
        AttendanceRecord record = AttendanceRecord.create(userId, date, consecutiveDays);
        setId(record, id);
        record.setRewardExp(10);
        record.setBonusRewardExp(0);
        return record;
    }

    @Nested
    @DisplayName("checkIn 테스트")
    class CheckInTest {

        @Test
        @DisplayName("첫 출석 체크를 정상적으로 수행한다")
        void checkIn_firstTime_success() {
            // given
            LocalDate today = LocalDate.now();
            AttendanceRecord savedRecord = createTestAttendanceRecord(1L, TEST_USER_ID, today, 1);

            when(attendanceRecordRepository.findByUserIdAndAttendanceDate(TEST_USER_ID, today))
                .thenReturn(Optional.empty());
            when(attendanceRecordRepository.findByUserIdAndAttendanceDate(TEST_USER_ID, today.minusDays(1)))
                .thenReturn(Optional.empty());
            when(rewardConfigCacheService.getConfigByRewardType(AttendanceRewardType.DAILY))
                .thenReturn(AttendanceRewardConfig.builder()
                    .rewardType(AttendanceRewardType.DAILY)
                    .rewardExp(10)
                    .build());
            when(attendanceRecordRepository.saveAndFlush(any(AttendanceRecord.class))).thenReturn(savedRecord);

            // when
            AttendanceCheckInResponse result = attendanceService.checkIn(TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.isAlreadyCheckedIn()).isFalse();
            verify(attendanceRecordRepository).saveAndFlush(any(AttendanceRecord.class));
            verify(userExperienceService).addExperience(
                eq(TEST_USER_ID), anyInt(), eq(ExpSourceType.EVENT), anyLong(), anyString(), eq("기타"));
        }

        @Test
        @DisplayName("이미 출석한 경우 중복 출석으로 처리한다")
        void checkIn_alreadyCheckedIn() {
            // given
            LocalDate today = LocalDate.now();
            AttendanceRecord existingRecord = createTestAttendanceRecord(1L, TEST_USER_ID, today, 1);

            when(attendanceRecordRepository.findByUserIdAndAttendanceDate(TEST_USER_ID, today))
                .thenReturn(Optional.of(existingRecord));

            // when
            AttendanceCheckInResponse result = attendanceService.checkIn(TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.isAlreadyCheckedIn()).isTrue();
            verify(attendanceRecordRepository, never()).save(any());
        }

        @Test
        @DisplayName("연속 출석 시 연속 일수가 증가한다")
        void checkIn_consecutiveDays_incremented() {
            // given
            LocalDate today = LocalDate.now();
            LocalDate yesterday = today.minusDays(1);

            AttendanceRecord yesterdayRecord = createTestAttendanceRecord(1L, TEST_USER_ID, yesterday, 5);
            AttendanceRecord savedRecord = createTestAttendanceRecord(2L, TEST_USER_ID, today, 6);

            when(attendanceRecordRepository.findByUserIdAndAttendanceDate(TEST_USER_ID, today))
                .thenReturn(Optional.empty());
            when(attendanceRecordRepository.findByUserIdAndAttendanceDate(TEST_USER_ID, yesterday))
                .thenReturn(Optional.of(yesterdayRecord));
            when(rewardConfigCacheService.getConfigByRewardType(AttendanceRewardType.DAILY))
                .thenReturn(AttendanceRewardConfig.builder()
                    .rewardType(AttendanceRewardType.DAILY)
                    .rewardExp(10)
                    .build());
            when(attendanceRecordRepository.saveAndFlush(any(AttendanceRecord.class))).thenReturn(savedRecord);

            // when
            AttendanceCheckInResponse result = attendanceService.checkIn(TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.isAlreadyCheckedIn()).isFalse();
        }

        @Test
        @DisplayName("3일 연속 출석 시 보너스 경험치를 지급한다")
        void checkIn_3dayStreak_bonusExp() {
            // given
            LocalDate today = LocalDate.now();
            LocalDate yesterday = today.minusDays(1);

            AttendanceRecord yesterdayRecord = createTestAttendanceRecord(1L, TEST_USER_ID, yesterday, 2);
            AttendanceRecord savedRecord = createTestAttendanceRecord(2L, TEST_USER_ID, today, 3);
            savedRecord.setBonusRewardExp(20);

            when(attendanceRecordRepository.findByUserIdAndAttendanceDate(TEST_USER_ID, today))
                .thenReturn(Optional.empty());
            when(attendanceRecordRepository.findByUserIdAndAttendanceDate(TEST_USER_ID, yesterday))
                .thenReturn(Optional.of(yesterdayRecord));
            when(rewardConfigCacheService.getConfigByRewardType(AttendanceRewardType.DAILY))
                .thenReturn(AttendanceRewardConfig.builder()
                    .rewardType(AttendanceRewardType.DAILY)
                    .rewardExp(10)
                    .build());
            when(rewardConfigCacheService.getConfigByRewardType(AttendanceRewardType.CONSECUTIVE_3))
                .thenReturn(AttendanceRewardConfig.builder()
                    .rewardType(AttendanceRewardType.CONSECUTIVE_3)
                    .rewardExp(20)
                    .build());
            when(attendanceRecordRepository.saveAndFlush(any(AttendanceRecord.class))).thenReturn(savedRecord);

            // when
            AttendanceCheckInResponse result = attendanceService.checkIn(TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getBonusReasons()).contains("3일 연속 출석 보너스!");
        }
    }

    @Nested
    @DisplayName("hasCheckedInToday 테스트")
    class HasCheckedInTodayTest {

        @Test
        @DisplayName("오늘 출석했으면 true를 반환한다")
        void hasCheckedInToday_true() {
            // given
            when(attendanceRecordRepository.existsByUserIdAndAttendanceDate(TEST_USER_ID, LocalDate.now()))
                .thenReturn(true);

            // when
            boolean result = attendanceService.hasCheckedInToday(TEST_USER_ID);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("오늘 출석하지 않았으면 false를 반환한다")
        void hasCheckedInToday_false() {
            // given
            when(attendanceRecordRepository.existsByUserIdAndAttendanceDate(TEST_USER_ID, LocalDate.now()))
                .thenReturn(false);

            // when
            boolean result = attendanceService.hasCheckedInToday(TEST_USER_ID);

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("getMonthlyAttendance 테스트")
    class GetMonthlyAttendanceTest {

        @Test
        @DisplayName("월별 출석 현황을 조회한다")
        void getMonthlyAttendance_success() {
            // given
            String yearMonth = "2024-01";
            List<AttendanceRecord> records = List.of(
                createTestAttendanceRecord(1L, TEST_USER_ID, LocalDate.of(2024, 1, 1), 1),
                createTestAttendanceRecord(2L, TEST_USER_ID, LocalDate.of(2024, 1, 2), 2),
                createTestAttendanceRecord(3L, TEST_USER_ID, LocalDate.of(2024, 1, 3), 3)
            );

            when(attendanceRecordRepository.findByUserIdAndYearMonthOrderByDayOfMonthAsc(TEST_USER_ID, yearMonth))
                .thenReturn(records);
            when(attendanceRecordRepository.findLatestByUserId(TEST_USER_ID))
                .thenReturn(Optional.of(records.get(2)));
            when(attendanceRecordRepository.findMaxConsecutiveDaysByUserId(TEST_USER_ID))
                .thenReturn(Optional.of(5));

            // when
            MonthlyAttendanceResponse result = attendanceService.getMonthlyAttendance(TEST_USER_ID, yearMonth);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getYearMonth()).isEqualTo(yearMonth);
            assertThat(result.getAttendedDays()).isEqualTo(3);
            assertThat(result.getTotalDays()).isEqualTo(31);
            assertThat(result.getMaxStreak()).isEqualTo(5);
        }

        @Test
        @DisplayName("yearMonth가 null이면 현재 월을 조회한다")
        void getMonthlyAttendance_nullYearMonth_usesCurrentMonth() {
            // given
            LocalDate now = LocalDate.now();
            String currentYearMonth = now.getYear() + "-" + String.format("%02d", now.getMonthValue());

            when(attendanceRecordRepository.findByUserIdAndYearMonthOrderByDayOfMonthAsc(TEST_USER_ID, currentYearMonth))
                .thenReturn(List.of());
            when(attendanceRecordRepository.findLatestByUserId(TEST_USER_ID))
                .thenReturn(Optional.empty());
            when(attendanceRecordRepository.findMaxConsecutiveDaysByUserId(TEST_USER_ID))
                .thenReturn(Optional.empty());

            // when
            MonthlyAttendanceResponse result = attendanceService.getMonthlyAttendance(TEST_USER_ID, null);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getYearMonth()).isEqualTo(currentYearMonth);
        }
    }

    @Nested
    @DisplayName("getCurrentStreak 테스트")
    class GetCurrentStreakTest {

        @Test
        @DisplayName("오늘 출석한 경우 현재 연속 일수를 반환한다")
        void getCurrentStreak_checkedInToday() {
            // given
            LocalDate today = LocalDate.now();
            AttendanceRecord record = createTestAttendanceRecord(1L, TEST_USER_ID, today, 5);

            when(attendanceRecordRepository.findLatestByUserId(TEST_USER_ID))
                .thenReturn(Optional.of(record));

            // when
            int result = attendanceService.getCurrentStreak(TEST_USER_ID);

            // then
            assertThat(result).isEqualTo(5);
        }

        @Test
        @DisplayName("어제 출석한 경우 현재 연속 일수를 반환한다")
        void getCurrentStreak_checkedInYesterday() {
            // given
            LocalDate yesterday = LocalDate.now().minusDays(1);
            AttendanceRecord record = createTestAttendanceRecord(1L, TEST_USER_ID, yesterday, 3);

            when(attendanceRecordRepository.findLatestByUserId(TEST_USER_ID))
                .thenReturn(Optional.of(record));

            // when
            int result = attendanceService.getCurrentStreak(TEST_USER_ID);

            // then
            assertThat(result).isEqualTo(3);
        }

        @Test
        @DisplayName("출석 기록이 없으면 0을 반환한다")
        void getCurrentStreak_noRecord() {
            // given
            when(attendanceRecordRepository.findLatestByUserId(TEST_USER_ID))
                .thenReturn(Optional.empty());

            // when
            int result = attendanceService.getCurrentStreak(TEST_USER_ID);

            // then
            assertThat(result).isZero();
        }

        @Test
        @DisplayName("2일 이상 출석하지 않으면 0을 반환한다")
        void getCurrentStreak_streakBroken() {
            // given
            LocalDate twoDaysAgo = LocalDate.now().minusDays(2);
            AttendanceRecord record = createTestAttendanceRecord(1L, TEST_USER_ID, twoDaysAgo, 10);

            when(attendanceRecordRepository.findLatestByUserId(TEST_USER_ID))
                .thenReturn(Optional.of(record));

            // when
            int result = attendanceService.getCurrentStreak(TEST_USER_ID);

            // then
            assertThat(result).isZero();
        }
    }

    @Nested
    @DisplayName("initializeDefaultRewardConfigs 테스트")
    class InitializeDefaultRewardConfigsTest {

        @Test
        @DisplayName("캐시 서비스에 위임한다")
        void initializeDefaultRewardConfigs_delegatesToCacheService() {
            // when
            attendanceService.initializeDefaultRewardConfigs();

            // then
            verify(rewardConfigCacheService).initializeDefaultRewardConfigs();
        }
    }
}
