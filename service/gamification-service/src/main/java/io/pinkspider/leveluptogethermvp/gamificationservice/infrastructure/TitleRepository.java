package io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure;

import io.pinkspider.global.enums.TitlePosition;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Title;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TitleRepository extends JpaRepository<Title, Long> {

    List<Title> findByIsActiveTrue();

    List<Title> findByIsActiveTrueOrderByIdAsc();

    List<Title> findByRarity(TitleRarity rarity);

    List<Title> findByRarityAndIsActiveTrue(TitleRarity rarity);

    List<Title> findByPositionTypeAndIsActiveTrue(TitlePosition positionType);

    List<Title> findByPositionTypeAndIsActiveTrueOrderByRarityAscIdAsc(TitlePosition positionType);

    List<Title> findByPositionTypeAndRarityAndIsActiveTrue(
            TitlePosition positionType, TitleRarity rarity);

    boolean existsByName(String name);

    @Query("SELECT COUNT(t) FROM Title t WHERE t.isActive = true")
    Long countActiveTitles();

    @Query(
            "SELECT COUNT(t) FROM Title t WHERE t.positionType = :positionType AND t.isActive ="
                    + " true")
    Long countByPositionTypeAndActive(@Param("positionType") TitlePosition positionType);

    @Query(
            "SELECT t FROM Title t WHERE (:keyword IS NULL OR :keyword = '' OR LOWER(t.name) LIKE"
                    + " LOWER(CONCAT('%', :keyword, '%')) OR LOWER(t.nameEn) LIKE LOWER(CONCAT('%',"
                    + " :keyword, '%')) OR LOWER(t.description) LIKE LOWER(CONCAT('%', :keyword,"
                    + " '%')))")
    Page<Title> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    @Query(
            "SELECT t FROM Title t WHERE (:keyword IS NULL OR :keyword = '' OR LOWER(t.name) LIKE"
                + " LOWER(CONCAT('%', :keyword, '%')) OR LOWER(t.nameEn) LIKE LOWER(CONCAT('%',"
                + " :keyword, '%')) OR LOWER(t.description) LIKE LOWER(CONCAT('%', :keyword, '%')))"
                + " AND t.positionType = :positionType")
    Page<Title> searchByKeywordAndPosition(
            @Param("keyword") String keyword,
            @Param("positionType") TitlePosition positionType,
            Pageable pageable);
}
