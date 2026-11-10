package io.pinkspider.leveluptogethermvp.towerservice.infrastructure;

import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerVisitorReport;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TowerVisitorReportRepository extends JpaRepository<TowerVisitorReport, Long> {

    Page<TowerVisitorReport> findAllByOrderByIdDesc(Pageable pageable);

    Optional<TowerVisitorReport>
            findFirstByReporterUserIdAndVisitorKeyAndCreatedAtAfterOrderByIdDesc(
                    String reporterUserId, String visitorKey, LocalDateTime after);
}
