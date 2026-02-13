package io.pinkspider.leveluptogethermvp.gamificationservice.event.infrastructure;

import io.pinkspider.leveluptogethermvp.gamificationservice.event.domain.entity.Event;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    /**
     * 활성화된 이벤트 목록 조회
     */
    List<Event> findByIsActiveTrueOrderByStartAtDesc();

    /**
     * 현재 진행중인 이벤트 조회
     */
    @Query("SELECT e FROM Event e WHERE e.isActive = true AND e.startAt <= :now AND e.endAt >= :now ORDER BY e.startAt DESC")
    List<Event> findCurrentEvents(@Param("now") LocalDateTime now);

    /**
     * 현재 진행중 또는 예정된 이벤트 조회 (Home 표시용)
     */
    @Query("SELECT e FROM Event e WHERE e.isActive = true AND e.endAt >= :now ORDER BY e.startAt ASC")
    List<Event> findActiveOrUpcomingEvents(@Param("now") LocalDateTime now);

    /**
     * 키워드 검색 (Admin)
     */
    @Query("SELECT e FROM Event e WHERE " +
        "(:keyword IS NULL OR :keyword = '' OR LOWER(e.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
        "OR LOWER(e.nameEn) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
        "OR LOWER(e.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Event> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    /**
     * 전체 이벤트 페이징 조회 (Admin)
     */
    Page<Event> findAllByOrderByStartAtDesc(Pageable pageable);
}
