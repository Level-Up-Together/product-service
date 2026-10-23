package io.pinkspider.leveluptogethermvp.statsservice.activity.infrastructure;

import io.pinkspider.leveluptogethermvp.statsservice.activity.domain.entity.UserDailyActivity;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserDailyActivityRepository extends JpaRepository<UserDailyActivity, Long> {

    List<UserDailyActivity> findAllByActivityDate(LocalDate activityDate);

    long countByActivityDate(LocalDate activityDate);
}
