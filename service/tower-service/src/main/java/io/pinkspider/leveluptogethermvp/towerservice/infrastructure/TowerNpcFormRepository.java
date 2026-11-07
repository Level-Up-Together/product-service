package io.pinkspider.leveluptogethermvp.towerservice.infrastructure;

import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpcForm;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** LUT-760: NPC 입력 받기 설정 (NPC 당 1행) */
public interface TowerNpcFormRepository extends JpaRepository<TowerNpcForm, Long> {

    Optional<TowerNpcForm> findByNpcId(Long npcId);

    List<TowerNpcForm> findAllByNpcIdIn(Collection<Long> npcIds);

    /** 지운 공개 영역 마스크를 다른 NPC 가 쓰는지 (파일 정리 전 확인) */
    long countByDisplayMaskUrl(String displayMaskUrl);
}
