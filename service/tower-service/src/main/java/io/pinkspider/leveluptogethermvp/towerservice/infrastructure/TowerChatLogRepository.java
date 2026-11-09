package io.pinkspider.leveluptogethermvp.towerservice.infrastructure;

import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerChatLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TowerChatLogRepository extends JpaRepository<TowerChatLog, Long> {

    /** LUT-705: 방문객 신고에 남길 그 방문객 세션의 최근 원문 */
    List<TowerChatLog> findTop20ByVisitorKeyOrderByIdDesc(String visitorKey);
}
