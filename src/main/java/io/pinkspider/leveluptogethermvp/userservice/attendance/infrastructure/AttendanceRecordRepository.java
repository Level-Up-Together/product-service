package io.pinkspider.leveluptogethermvp.userservice.attendance.infrastructure;

import io.pinkspider.leveluptogethermvp.userservice.attendance.domain.entity.AttendanceRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    Optional<AttendanceRecord> findByUserIdAndAttendanceDate(String userId, LocalDate date);

    boolean existsByUserIdAndAttendanceDate(String userId, LocalDate date);

    List<AttendanceRecord> findByUserIdAndYearMonthOrderByDayOfMonthAsc(String userId, String yearMonth);

    @Query("SELECT ar FROM AttendanceRecord ar WHERE ar.userId = :userId " +
           "AND ar.attendanceDate BETWEEN :startDate AND :endDate " +
           "ORDER BY ar.attendanceDate ASC")
    List<AttendanceRecord> findByUserIdAndDateRange(
        @Param("userId") String userId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate);

    @Query("SELECT COUNT(ar) FROM AttendanceRecord ar WHERE ar.userId = :userId AND ar.yearMonth = :yearMonth")
    int countByUserIdAndYearMonth(@Param("userId") String userId, @Param("yearMonth") String yearMonth);

    @Query("SELECT ar FROM AttendanceRecord ar WHERE ar.userId = :userId " +
           "ORDER BY ar.attendanceDate DESC LIMIT 1")
    Optional<AttendanceRecord> findLatestByUserId(@Param("userId") String userId);

    @Query("SELECT MAX(ar.consecutiveDays) FROM AttendanceRecord ar WHERE ar.userId = :userId")
    Optional<Integer> findMaxConsecutiveDaysByUserId(@Param("userId") String userId);
}
