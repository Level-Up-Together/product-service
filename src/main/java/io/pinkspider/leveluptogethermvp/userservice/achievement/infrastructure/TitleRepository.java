package io.pinkspider.leveluptogethermvp.userservice.achievement.infrastructure;

import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.entity.Title;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.enums.TitleRarity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TitleRepository extends JpaRepository<Title, Long> {

    List<Title> findByIsActiveTrue();

    List<Title> findByRarityAndIsActiveTrue(TitleRarity rarity);
}
