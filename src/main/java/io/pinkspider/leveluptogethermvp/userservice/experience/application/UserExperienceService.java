package io.pinkspider.leveluptogethermvp.userservice.experience.application;

import io.pinkspider.leveluptogethermvp.metaservice.domain.entity.LevelConfig;
import io.pinkspider.leveluptogethermvp.metaservice.infrastructure.LevelConfigRepository;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.AchievementService;
import io.pinkspider.leveluptogethermvp.userservice.experience.domain.dto.UserExperienceResponse;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.ExperienceHistory;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.ExperienceHistory.ExpSourceType;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.UserExperience;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.ExperienceHistoryRepository;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserExperienceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
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
    private final ApplicationContext applicationContext;

    @Transactional
    public UserExperienceResponse addExperience(String userId, int expAmount, ExpSourceType sourceType,
                                                 Long sourceId, String description) {
        return addExperience(userId, expAmount, sourceType, sourceId, description, null);
    }

    @Transactional
    public UserExperienceResponse addExperience(String userId, int expAmount, ExpSourceType sourceType,
                                                 Long sourceId, String description, String categoryName) {
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
            .categoryName(categoryName)
            .levelBefore(levelBefore)
            .levelAfter(levelAfter)
            .build();
        experienceHistoryRepository.save(history);

        if (levelAfter > levelBefore) {
            log.info("레벨 업! userId={}, {} -> {}", userId, levelBefore, levelAfter);
            // 레벨 업적 체크 (순환 의존성 방지를 위해 ApplicationContext 사용)
            try {
                AchievementService achievementService = applicationContext.getBean(AchievementService.class);
                achievementService.checkLevelAchievements(userId, levelAfter);
            } catch (Exception e) {
                log.warn("레벨 업적 체크 실패: userId={}, level={}, error={}", userId, levelAfter, e.getMessage());
            }
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

    /**
     * 경험치 차감 (Saga 보상 트랜잭션용)
     *
     * @param userId 사용자 ID
     * @param expAmount 차감할 경험치
     * @param sourceType 출처 유형
     * @param sourceId 출처 ID
     * @param description 설명
     * @return 업데이트된 경험치 정보
     */
    @Transactional
    public UserExperienceResponse subtractExperience(String userId, int expAmount, ExpSourceType sourceType,
                                                      Long sourceId, String description) {
        return subtractExperience(userId, expAmount, sourceType, sourceId, description, null);
    }

    @Transactional
    public UserExperienceResponse subtractExperience(String userId, int expAmount, ExpSourceType sourceType,
                                                      Long sourceId, String description, String categoryName) {
        UserExperience userExp = getOrCreateUserExperience(userId);
        int levelBefore = userExp.getCurrentLevel();

        // 경험치 차감
        int newCurrentExp = userExp.getCurrentExp() - expAmount;
        int newTotalExp = userExp.getTotalExp() - expAmount;

        // 레벨 다운 처리
        if (newCurrentExp < 0) {
            // 이전 레벨로 이동하면서 경험치 조정
            processLevelDown(userExp, newTotalExp);
        } else {
            userExp.setCurrentExp(newCurrentExp);
            userExp.setTotalExp(Math.max(0, newTotalExp));
        }

        int levelAfter = userExp.getCurrentLevel();

        // 히스토리 기록 (음수 경험치로 기록)
        ExperienceHistory history = ExperienceHistory.builder()
            .userId(userId)
            .sourceType(sourceType)
            .sourceId(sourceId)
            .expAmount(-expAmount) // 음수로 기록
            .description(description)
            .categoryName(categoryName)
            .levelBefore(levelBefore)
            .levelAfter(levelAfter)
            .build();
        experienceHistoryRepository.save(history);

        log.info("경험치 차감: userId={}, amount={}, total={}, level: {} -> {}",
            userId, expAmount, userExp.getTotalExp(), levelBefore, levelAfter);

        return UserExperienceResponse.from(userExp, getNextLevelRequiredExp(userExp.getCurrentLevel()));
    }

    /**
     * 레벨 다운 처리 (경험치 차감으로 인한)
     */
    private void processLevelDown(UserExperience userExp, int targetTotalExp) {
        List<LevelConfig> levelConfigs = levelConfigRepository.findAllByOrderByLevelAsc();

        if (targetTotalExp <= 0) {
            userExp.setCurrentLevel(1);
            userExp.setCurrentExp(0);
            userExp.setTotalExp(0);
            return;
        }

        userExp.setTotalExp(targetTotalExp);

        // 누적 경험치 기반으로 레벨 재계산
        int newLevel = 1;
        int remainingExp = targetTotalExp;

        for (LevelConfig config : levelConfigs) {
            if (config.getCumulativeExp() != null && targetTotalExp >= config.getCumulativeExp()) {
                newLevel = config.getLevel();
                remainingExp = targetTotalExp - config.getCumulativeExp();
            } else if (config.getCumulativeExp() == null) {
                // 누적 경험치가 없으면 필요 경험치로 계산
                if (remainingExp >= config.getRequiredExp()) {
                    remainingExp -= config.getRequiredExp();
                    newLevel = config.getLevel() + 1;
                } else {
                    break;
                }
            }
        }

        userExp.setCurrentLevel(Math.max(1, newLevel));
        userExp.setCurrentExp(Math.max(0, remainingExp));
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
