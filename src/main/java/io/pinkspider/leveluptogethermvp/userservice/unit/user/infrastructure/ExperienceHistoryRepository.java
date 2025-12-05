package io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure;

import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.ExperienceHistory;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.ExperienceHistory.ExpSourceType;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ExperienceHistoryRepository extends JpaRepository<ExperienceHistory, Long> {

    Page<ExperienceHistory> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    List<ExperienceHistory> findByUserIdAndSourceType(String userId, ExpSourceType sourceType);

    @Query("SELECT SUM(eh.expAmount) FROM ExperienceHistory eh WHERE eh.userId = :userId")
    Long sumExpByUserId(@Param("userId") String userId);

    @Query("SELECT SUM(eh.expAmount) FROM ExperienceHistory eh WHERE eh.userId = :userId AND eh.sourceType = :sourceType")
    Long sumExpByUserIdAndSourceType(@Param("userId") String userId, @Param("sourceType") ExpSourceType sourceType);
}
