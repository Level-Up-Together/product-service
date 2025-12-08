package io.pinkspider.leveluptogethermvp.userservice.quest.application;

import io.pinkspider.leveluptogethermvp.userservice.experience.application.UserExperienceService;
import io.pinkspider.leveluptogethermvp.userservice.quest.domain.dto.QuestProgressResponse;
import io.pinkspider.leveluptogethermvp.userservice.quest.domain.dto.QuestResponse;
import io.pinkspider.leveluptogethermvp.userservice.quest.domain.dto.UserQuestResponse;
import io.pinkspider.leveluptogethermvp.userservice.quest.domain.entity.Quest;
import io.pinkspider.leveluptogethermvp.userservice.quest.domain.entity.UserQuest;
import io.pinkspider.leveluptogethermvp.userservice.quest.domain.enums.QuestActionType;
import io.pinkspider.leveluptogethermvp.userservice.quest.domain.enums.QuestCategory;
import io.pinkspider.leveluptogethermvp.userservice.quest.domain.enums.QuestType;
import io.pinkspider.leveluptogethermvp.userservice.quest.infrastructure.QuestRepository;
import io.pinkspider.leveluptogethermvp.userservice.quest.infrastructure.UserQuestRepository;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.ExperienceHistory.ExpSourceType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuestService {

    private final QuestRepository questRepository;
    private final UserQuestRepository userQuestRepository;
    private final UserExperienceService userExperienceService;

    // 일일 퀘스트 조회
    public QuestProgressResponse getDailyQuests(String userId) {
        LocalDate today = LocalDate.now();
        String periodKey = UserQuest.generateDailyPeriodKey(today);

        List<Quest> dailyQuests = questRepository.findByQuestTypeAndIsActiveTrueOrderBySortOrderAsc(QuestType.DAILY);
        List<UserQuest> userQuests = userQuestRepository.findByUserIdAndQuestTypeAndPeriodKey(
            userId, QuestType.DAILY, periodKey);

        Map<Long, UserQuest> userQuestMap = userQuests.stream()
            .collect(Collectors.toMap(uq -> uq.getQuest().getId(), uq -> uq));

        List<UserQuestResponse> responses = dailyQuests.stream()
            .map(quest -> {
                UserQuest userQuest = userQuestMap.get(quest.getId());
                if (userQuest == null) {
                    userQuest = createEmptyUserQuest(quest, periodKey);
                }
                return UserQuestResponse.from(userQuest);
            })
            .toList();

        return QuestProgressResponse.of(periodKey, responses);
    }

    // 주간 퀘스트 조회
    public QuestProgressResponse getWeeklyQuests(String userId) {
        LocalDate today = LocalDate.now();
        String periodKey = UserQuest.generateWeeklyPeriodKey(today);

        List<Quest> weeklyQuests = questRepository.findByQuestTypeAndIsActiveTrueOrderBySortOrderAsc(QuestType.WEEKLY);
        List<UserQuest> userQuests = userQuestRepository.findByUserIdAndQuestTypeAndPeriodKey(
            userId, QuestType.WEEKLY, periodKey);

        Map<Long, UserQuest> userQuestMap = userQuests.stream()
            .collect(Collectors.toMap(uq -> uq.getQuest().getId(), uq -> uq));

        List<UserQuestResponse> responses = weeklyQuests.stream()
            .map(quest -> {
                UserQuest userQuest = userQuestMap.get(quest.getId());
                if (userQuest == null) {
                    userQuest = createEmptyUserQuest(quest, periodKey);
                }
                return UserQuestResponse.from(userQuest);
            })
            .toList();

        return QuestProgressResponse.of(periodKey, responses);
    }

    // 수령 가능한 보상 조회
    public List<UserQuestResponse> getClaimableQuests(String userId) {
        return userQuestRepository.findClaimableQuests(userId).stream()
            .map(UserQuestResponse::from)
            .toList();
    }

    // 보상 수령
    @Transactional
    public UserQuestResponse claimReward(String userId, Long userQuestId) {
        UserQuest userQuest = userQuestRepository.findById(userQuestId)
            .orElseThrow(() -> new IllegalArgumentException("퀘스트를 찾을 수 없습니다."));

        if (!userQuest.getUserId().equals(userId)) {
            throw new IllegalStateException("본인의 퀘스트만 보상을 수령할 수 있습니다.");
        }

        userQuest.claimReward();

        Quest quest = userQuest.getQuest();
        if (quest.getRewardExp() > 0) {
            userExperienceService.addExperience(
                userId,
                quest.getRewardExp(),
                ExpSourceType.EVENT,
                quest.getId(),
                "퀘스트 완료 보상: " + quest.getName()
            );
        }

        log.info("퀘스트 보상 수령: userId={}, quest={}", userId, quest.getName());
        return UserQuestResponse.from(userQuest);
    }

    // 모든 수령 가능한 보상 일괄 수령
    @Transactional
    public List<UserQuestResponse> claimAllRewards(String userId) {
        List<UserQuest> claimableQuests = userQuestRepository.findClaimableQuests(userId);
        List<UserQuestResponse> results = new ArrayList<>();

        for (UserQuest userQuest : claimableQuests) {
            userQuest.claimReward();

            Quest quest = userQuest.getQuest();
            if (quest.getRewardExp() > 0) {
                userExperienceService.addExperience(
                    userId,
                    quest.getRewardExp(),
                    ExpSourceType.EVENT,
                    quest.getId(),
                    "퀘스트 완료 보상: " + quest.getName()
                );
            }

            results.add(UserQuestResponse.from(userQuest));
        }

        log.info("퀘스트 보상 일괄 수령: userId={}, count={}", userId, results.size());
        return results;
    }

    // 퀘스트 진행도 업데이트
    @Transactional
    public void updateQuestProgress(String userId, QuestActionType actionType, int count) {
        List<Quest> quests = questRepository.findActiveQuestsByActionType(actionType);

        for (Quest quest : quests) {
            String periodKey = quest.isDaily()
                ? UserQuest.generateDailyPeriodKey(LocalDate.now())
                : UserQuest.generateWeeklyPeriodKey(LocalDate.now());

            UserQuest userQuest = userQuestRepository
                .findByUserIdAndQuestIdAndPeriodKey(userId, quest.getId(), periodKey)
                .orElseGet(() -> createNewUserQuest(userId, quest, periodKey));

            if (!userQuest.getIsCompleted()) {
                userQuest.incrementProgress(count);
                userQuestRepository.save(userQuest);

                if (userQuest.getIsCompleted()) {
                    log.info("퀘스트 완료! userId={}, quest={}", userId, quest.getName());
                }
            }
        }
    }

    // 퀘스트 진행도 증가 (1회)
    @Transactional
    public void incrementQuestProgress(String userId, QuestActionType actionType) {
        updateQuestProgress(userId, actionType, 1);
    }

    // 모든 퀘스트 목록 조회
    public List<QuestResponse> getAllQuests() {
        return questRepository.findAllActiveQuests().stream()
            .map(QuestResponse::from)
            .toList();
    }

    private UserQuest createNewUserQuest(String userId, Quest quest, String periodKey) {
        UserQuest userQuest = UserQuest.builder()
            .userId(userId)
            .quest(quest)
            .periodKey(periodKey)
            .currentCount(0)
            .build();
        return userQuestRepository.save(userQuest);
    }

    private UserQuest createEmptyUserQuest(Quest quest, String periodKey) {
        return UserQuest.builder()
            .quest(quest)
            .periodKey(periodKey)
            .currentCount(0)
            .isCompleted(false)
            .isRewardClaimed(false)
            .build();
    }
}
