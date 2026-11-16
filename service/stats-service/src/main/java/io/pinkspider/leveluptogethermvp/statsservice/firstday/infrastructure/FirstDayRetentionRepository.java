package io.pinkspider.leveluptogethermvp.statsservice.firstday.infrastructure;

import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.entity.FirstDayRetention;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FirstDayRetentionRepository extends JpaRepository<FirstDayRetention, String> {

    /** 이미 분류한 마지막 가입일 — 다음 실행은 그 며칠 전부터만 가입자를 읽는다 */
    @Query("select max(r.signupDate) from FirstDayRetention r")
    Optional<LocalDate> findMaxSignupDate();

    @Query("select r.userId from FirstDayRetention r where r.userId in :ids")
    List<String> findExistingUserIds(@Param("ids") Collection<String> ids);

    /** D1·D7·D30 중 아직 확정 안 된 값이 있는 행 */
    @Query(
            "select r from FirstDayRetention r"
                    + " where (r.d1 is null or r.d7 is null or r.d30 is null)"
                    + " and r.signupDate <= :latestSignupDate")
    List<FirstDayRetention> findOpen(@Param("latestSignupDate") LocalDate latestSignupDate);

    List<FirstDayRetention> findAllBySignupDateBetween(LocalDate from, LocalDate to);
}
