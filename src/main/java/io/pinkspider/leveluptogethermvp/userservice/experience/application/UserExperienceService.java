package io.pinkspider.leveluptogethermvp.userservice.experience.application;

import io.pinkspider.leveluptogethermvp.metaservice.domain.entity.LevelConfig;
import io.pinkspider.leveluptogethermvp.metaservice.infrastructure.LevelConfigRepository;
import io.pinkspider.leveluptogethermvp.userservice.experience.domain.dto.UserExperienceResponse;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.ExperienceHistory;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.ExperienceHistory.ExpSourceType;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.UserExperience;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.ExperienceHistoryRepository;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserExperienceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserExperienceService {

    private final UserExperienceRepository userExperienceRepository;
    private final ExperienceHistoryRepository experienceHistoryRepository;
    private final LevelConfigRepository levelConfigRepository;

    @Transactional
    public UserExperienceResponse addExperience(String userId, int expAmount, ExpSourceType sourceType,
                                                 Long sourceId, String description) {
        UserExperience userExp = getOrCreateUserExperience(userId);
        int levelBefore = userExp.getCurrentLevel();

        userExp.addExperience(expAmount);

        processLevelUp(userExp);

        int levelAfter = userExp.getCurrentLevel();

        ExperienceHistory history = ExperienceHistory.builder()
            .userId(userId)
            .sourceType(sourceType)
            .sourceId(sourceId)
            .expAmount(expAmount)
            .description(description)
            .levelBefore(levelBefore)
            .levelAfter(levelAfter)
            .build();
        experienceHistoryRepository.save(history);

        if (levelAfter > levelBefore) {
            log.info("레벨 업! userId={}, {} -> {}", userId, levelBefore, levelAfter);
        }

        log.info("경험치 획득: userId={}, amount={}, total={}, level={}",
            userId, expAmount, userExp.getTotalExp(), userExp.getCurrentLevel());

        return UserExperienceResponse.from(userExp, getNextLevelRequiredExp(userExp.getCurrentLevel()));
    }

    public UserExperienceResponse getUserExperience(String userId) {
        UserExperience userExp = getOrCreateUserExperience(userId);
        return UserExperienceResponse.from(userExp, getNextLevelRequiredExp(userExp.getCurrentLevel()));
    }

    public Page<ExperienceHistory> getExperienceHistory(String userId, Pageable pageable) {
        return experienceHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional
    public UserExperience getOrCreateUserExperience(String userId) {
        return userExperienceRepository.findByUserId(userId)
            .orElseGet(() -> {
                UserExperience newExp = UserExperience.builder()
                    .userId(userId)
                    .currentLevel(1)
                    .currentExp(0)
                    .totalExp(0)
                    .build();
                return userExperienceRepository.save(newExp);
            });
    }

    private void processLevelUp(UserExperience userExp) {
        List<LevelConfig> levelConfigs = levelConfigRepository.findAllByOrderByLevelAsc();
        if (levelConfigs.isEmpty()) {
            processLevelUpWithDefaultFormula(userExp);
            return;
        }

        while (true) {
            int currentLevel = userExp.getCurrentLevel();
            LevelConfig currentLevelConfig = levelConfigs.stream()
                .filter(lc -> lc.getLevel().equals(currentLevel))
                .findFirst()
                .orElse(null);

            if (currentLevelConfig == null) {
                break;
            }

            int requiredExp = currentLevelConfig.getRequiredExp();
            if (userExp.getCurrentExp() >= requiredExp) {
                userExp.levelUp(requiredExp);
            } else {
                break;
            }

            Integer maxLevel = levelConfigRepository.findMaxLevel();
            if (maxLevel != null && userExp.getCurrentLevel() >= maxLevel) {
                break;
            }
        }
    }

    private void processLevelUpWithDefaultFormula(UserExperience userExp) {
        while (true) {
            int requiredExp = calculateDefaultRequiredExp(userExp.getCurrentLevel());
            if (userExp.getCurrentExp() >= requiredExp) {
                userExp.levelUp(requiredExp);
            } else {
                break;
            }
        }
    }

    private int calculateDefaultRequiredExp(int level) {
        return 100 + (level - 1) * 50;
    }

    private Integer getNextLevelRequiredExp(int currentLevel) {
        return levelConfigRepository.findByLevel(currentLevel)
            .map(LevelConfig::getRequiredExp)
            .orElse(calculateDefaultRequiredExp(currentLevel));
    }

    public List<LevelConfig> getAllLevelConfigs() {
        return levelConfigRepository.findAllByOrderByLevelAsc();
    }

    @Transactional
    public LevelConfig createOrUpdateLevelConfig(Integer level, Integer requiredExp,
                                                  Integer cumulativeExp, String title, String description) {
        LevelConfig config = levelConfigRepository.findByLevel(level)
            .orElse(LevelConfig.builder().level(level).build());

        config.setRequiredExp(requiredExp);
        config.setCumulativeExp(cumulativeExp);
        config.setTitle(title);
        config.setDescription(description);

        return levelConfigRepository.save(config);
    }
}
