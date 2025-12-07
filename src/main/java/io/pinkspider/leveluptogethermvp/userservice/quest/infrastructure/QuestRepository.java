package io.pinkspider.leveluptogethermvp.userservice.quest.infrastructure;

import io.pinkspider.leveluptogethermvp.userservice.quest.domain.entity.Quest;
import io.pinkspider.leveluptogethermvp.userservice.quest.domain.enums.QuestActionType;
import io.pinkspider.leveluptogethermvp.userservice.quest.domain.enums.QuestType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface QuestRepository extends JpaRepository<Quest, Long> {

    List<Quest> findByQuestTypeAndIsActiveTrueOrderBySortOrderAsc(QuestType questType);

    @Query("SELECT q FROM Quest q WHERE q.isActive = true ORDER BY q.questType, q.sortOrder")
    List<Quest> findAllActiveQuests();

    @Query("SELECT q FROM Quest q WHERE q.isActive = true AND q.actionType = :actionType")
    List<Quest> findActiveQuestsByActionType(@Param("actionType") QuestActionType actionType);

    Optional<Quest> findByActionTypeAndQuestTypeAndIsActiveTrue(QuestActionType actionType, QuestType questType);
}
