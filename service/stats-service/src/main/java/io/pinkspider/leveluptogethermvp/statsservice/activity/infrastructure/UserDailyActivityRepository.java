package io.pinkspider.leveluptogethermvp.statsservice.activity.infrastructure;

import io.pinkspider.leveluptogethermvp.statsservice.activity.domain.entity.UserDailyActivity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserDailyActivityRepository extends JpaRepository<UserDailyActivity, Long> {

    List<UserDailyActivity> findAllByActivityDate(LocalDate activityDate);

    long countByActivityDate(LocalDate activityDate);

    /** LUT-601: 기간 내 활동 행 (KPI 스냅샷·코호트 리텐션 REQUEST 원천) */
    List<UserDailyActivity> findAllByActivityDateBetween(LocalDate from, LocalDate to);

    /** LUT-601: 요청 기반 활동 데이터가 시작된 날 — 그 전 코호트의 REQUEST 리텐션은 계산하지 않는다 */
    @Query("select min(a.activityDate) from UserDailyActivity a")
    Optional<LocalDate> findMinActivityDate();
}
