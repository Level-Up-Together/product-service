package io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserExperience;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserExperienceRepository extends JpaRepository<UserExperience, Long> {

    Optional<UserExperience> findByUserId(String userId);

    boolean existsByUserId(String userId);

    /**
     * 레벨 기준 랭킹 조회 (레벨 내림차순, 동일 레벨 시 총 경험치 내림차순)
     */
    Page<UserExperience> findAllByOrderByCurrentLevelDescTotalExpDesc(Pageable pageable);

    /**
     * 특정 사용자의 레벨 기준 랭킹 순위 계산
     */
    @Query("""
        SELECT COUNT(ue) + 1 FROM UserExperience ue
        WHERE ue.currentLevel > :level
           OR (ue.currentLevel = :level AND ue.totalExp > :totalExp)
        """)
    long calculateLevelRank(@Param("level") int level, @Param("totalExp") int totalExp);

    /**
     * 전체 사용자 수 (경험치 테이블 기준)
     */
    @Query("SELECT COUNT(ue) FROM UserExperience ue")
    long countTotalUsers();

    /**
     * 여러 사용자의 경험치 정보 배치 조회 (N+1 방지)
     */
    @Query("SELECT ue FROM UserExperience ue WHERE ue.userId IN :userIds")
    List<UserExperience> findByUserIdIn(@Param("userIds") List<String> userIds);
}
