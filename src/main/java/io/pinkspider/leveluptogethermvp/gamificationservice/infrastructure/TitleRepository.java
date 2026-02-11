package io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Title;
import io.pinkspider.global.enums.TitlePosition;
import io.pinkspider.global.enums.TitleRarity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TitleRepository extends JpaRepository<Title, Long> {

    List<Title> findByIsActiveTrue();

    List<Title> findByRarityAndIsActiveTrue(TitleRarity rarity);

    List<Title> findByPositionTypeAndIsActiveTrue(TitlePosition positionType);

    List<Title> findByPositionTypeAndRarityAndIsActiveTrue(TitlePosition positionType, TitleRarity rarity);
}
