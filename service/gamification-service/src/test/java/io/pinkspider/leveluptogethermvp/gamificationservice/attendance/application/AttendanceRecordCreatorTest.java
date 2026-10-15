package io.pinkspider.leveluptogethermvp.gamificationservice.attendance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.AttendanceRecord;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.AttendanceRecordRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/** LUT-539: attendance_record 첫 행 생성을 별도 트랜잭션(REQUIRES_NEW)에서 수행하는 전용 빈. */
@ExtendWith(MockitoExtension.class)
class AttendanceRecordCreatorTest {

    @Mock private AttendanceRecordRepository attendanceRecordRepository;

    @InjectMocks private AttendanceRecordCreator creator;

    @Test
    @DisplayName("출석 행을 보상 EXP 까지 채워 즉시 flush 하며 생성한다")
    void create_savesAndFlushesRecordWithRewards() {
        LocalDate date = LocalDate.of(2026, 10, 13);
        when(attendanceRecordRepository.saveAndFlush(any(AttendanceRecord.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        creator.create("user-1", date, 3, 10, 5);

        ArgumentCaptor<AttendanceRecord> captor = ArgumentCaptor.forClass(AttendanceRecord.class);
        verify(attendanceRecordRepository).saveAndFlush(captor.capture());
        AttendanceRecord saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo("user-1");
        assertThat(saved.getAttendanceDate()).isEqualTo(date);
        assertThat(saved.getConsecutiveDays()).isEqualTo(3);
        assertThat(saved.getRewardExp()).isEqualTo(10);
        assertThat(saved.getBonusRewardExp()).isEqualTo(5);
    }

    @Test
    @DisplayName("중복 키 예외는 삼키지 않고 호출자에게 전파한다 (호출자가 재조회로 '이미 출석' 응답)")
    void create_propagatesDuplicateKey() {
        when(attendanceRecordRepository.saveAndFlush(any(AttendanceRecord.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> creator.create("user-1", LocalDate.of(2026, 10, 13), 1, 10, 0))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
