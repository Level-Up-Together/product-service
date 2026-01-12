package io.pinkspider.leveluptogethermvp.adminservice.infrastructure;

import io.pinkspider.leveluptogethermvp.adminservice.domain.entity.SeasonRewardHistory;
import io.pinkspider.leveluptogethermvp.adminservice.domain.enums.SeasonRewardStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeasonRewardHistoryRepository extends JpaRepository<SeasonRewardHistory, Long> {

    /**
     * 시즌별 보상 이력 조회
     */
    Page<SeasonRewardHistory> findBySeasonIdOrderByFinalRankAsc(Long seasonId, Pageable pageable);

    /**
     * 유저의 시즌 보상 이력 조회
     */
    List<SeasonRewardHistory> findByUserIdOrderByCreatedAtDesc(String userId);

    /**
     * 특정 시즌-유저 보상 이력 존재 여부
     */
    boolean existsBySeasonIdAndUserId(Long seasonId, String userId);

    /**
     * 시즌별 상태 통계
     */
    @Query("""
        SELECT srh.status, COUNT(srh)
        FROM SeasonRewardHistory srh
        WHERE srh.seasonId = :seasonId
        GROUP BY srh.status
        """)
    List<Object[]> countBySeasonIdGroupByStatus(@Param("seasonId") Long seasonId);

    /**
     * 시즌 보상 이력 존재 여부 (이미 처리된 시즌인지 확인)
     */
    boolean existsBySeasonId(Long seasonId);

    /**
     * 실패한 보상 재처리 대상 조회
     */
    List<SeasonRewardHistory> findBySeasonIdAndStatus(Long seasonId, SeasonRewardStatus status);
}
