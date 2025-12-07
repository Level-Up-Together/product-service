package io.pinkspider.leveluptogethermvp.userservice.quest.infrastructure;

import io.pinkspider.leveluptogethermvp.userservice.quest.domain.entity.UserQuest;
import io.pinkspider.leveluptogethermvp.userservice.quest.domain.enums.QuestType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserQuestRepository extends JpaRepository<UserQuest, Long> {

    Optional<UserQuest> findByUserIdAndQuestIdAndPeriodKey(String userId, Long questId, String periodKey);

    @Query("SELECT uq FROM UserQuest uq JOIN FETCH uq.quest q " +
           "WHERE uq.userId = :userId AND uq.periodKey = :periodKey " +
           "ORDER BY q.sortOrder")
    List<UserQuest> findByUserIdAndPeriodKeyWithQuest(
        @Param("userId") String userId,
        @Param("periodKey") String periodKey);

    @Query("SELECT uq FROM UserQuest uq JOIN FETCH uq.quest q " +
           "WHERE uq.userId = :userId AND q.questType = :questType AND uq.periodKey = :periodKey " +
           "ORDER BY q.sortOrder")
    List<UserQuest> findByUserIdAndQuestTypeAndPeriodKey(
        @Param("userId") String userId,
        @Param("questType") QuestType questType,
        @Param("periodKey") String periodKey);

    @Query("SELECT uq FROM UserQuest uq JOIN FETCH uq.quest " +
           "WHERE uq.userId = :userId AND uq.isCompleted = true AND uq.isRewardClaimed = false")
    List<UserQuest> findClaimableQuests(@Param("userId") String userId);

    @Query("SELECT COUNT(uq) FROM UserQuest uq " +
           "WHERE uq.userId = :userId AND uq.periodKey = :periodKey AND uq.isCompleted = true")
    int countCompletedQuestsInPeriod(@Param("userId") String userId, @Param("periodKey") String periodKey);

    @Query("SELECT uq FROM UserQuest uq JOIN FETCH uq.quest q " +
           "WHERE uq.userId = :userId AND q.questType = :questType " +
           "AND uq.periodKey IN :periodKeys " +
           "ORDER BY uq.periodKey DESC, q.sortOrder")
    List<UserQuest> findByUserIdAndQuestTypeAndPeriodKeys(
        @Param("userId") String userId,
        @Param("questType") QuestType questType,
        @Param("periodKeys") List<String> periodKeys);
}
