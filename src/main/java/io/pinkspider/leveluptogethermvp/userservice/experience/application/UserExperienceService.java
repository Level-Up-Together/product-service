package io.pinkspider.leveluptogethermvp.userservice.experience.application;

import io.pinkspider.global.cache.UserLevelConfigCacheService;
import io.pinkspider.global.event.GuildCreationEligibleEvent;
import io.pinkspider.global.event.UserProfileChangedEvent;
import io.pinkspider.leveluptogethermvp.gamificationservice.userlevelconfig.domain.entity.UserLevelConfig;
import io.pinkspider.leveluptogethermvp.gamificationservice.userlevelconfig.infrastructure.UserLevelConfigRepository;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.AchievementService;
import io.pinkspider.leveluptogethermvp.userservice.experience.domain.dto.UserExperienceResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.ExperienceHistory;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserCategoryExperience;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.ExpSourceType;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserExperience;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.ExperienceHistoryRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserCategoryExperienceRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserExperienceRepository;
import io.pinkspider.leveluptogethermvp.userservice.profile.application.UserProfileCacheService;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
public class UserExperienceService {

    private static final int GUILD_CREATION_MIN_LEVEL = 20;

    private final UserExperienceRepository userExperienceRepository;
    private final ExperienceHistoryRepository experienceHistoryRepository;
    private final UserCategoryExperienceRepository userCategoryExperienceRepository;
    private final UserLevelConfigCacheService userLevelConfigCacheService;
    private final UserLevelConfigRepository userLevelConfigRepository; // for write operations
    private final ApplicationContext applicationContext;
    private final ApplicationEventPublisher eventPublisher;
    private final UserProfileCacheService userProfileCacheService;
    private final UserRepository userRepository;

    @Transactional(transactionManager = "gamificationTransactionManager")
    public UserExperienceResponse addExperience(String userId, int expAmount, ExpSourceType sourceType,
                                                 Long sourceId, String description) {
        return addExperience(userId, expAmount, sourceType, sourceId, description, null, null);
    }

    @Transactional(transactionManager = "gamificationTransactionManager")
    public UserExperienceResponse addExperience(String userId, int expAmount, ExpSourceType sourceType,
                                                 Long sourceId, String description, String categoryName) {
        return addExperience(userId, expAmount, sourceType, sourceId, description, null, categoryName);
    }

    @Transactional(transactionManager = "gamificationTransactionManager")
    public UserExperienceResponse addExperience(String userId, int expAmount, ExpSourceType sourceType,
                                                 Long sourceId, String description, Long categoryId, String categoryName) {
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

        // 카테고리별 경험치 업데이트 (categoryId가 있을 때만)
        if (categoryId != null && expAmount > 0) {
            updateCategoryExperience(userId, categoryId, categoryName, expAmount);
        }

        if (levelAfter > levelBefore) {
            log.info("레벨 업! userId={}, {} -> {}", userId, levelBefore, levelAfter);

            // 프로필 캐시 무효화 + 스냅샷 동기화 이벤트 발행
            userProfileCacheService.evictUserProfileCache(userId);
            try {
                Users user = userRepository.findById(userId).orElse(null);
                if (user != null) {
                    eventPublisher.publishEvent(new UserProfileChangedEvent(
                        userId, user.getNickname(), user.getPicture(), levelAfter));
                }
            } catch (Exception e) {
                log.warn("프로필 스냅샷 이벤트 발행 실패: userId={}, error={}", userId, e.getMessage());
            }

            // 동적 Strategy 패턴으로 USER_EXPERIENCE 관련 업적 체크 (순환 의존성 방지를 위해 ApplicationContext 사용)
            try {
                AchievementService achievementService = applicationContext.getBean(AchievementService.class);
                achievementService.checkAchievementsByDataSource(userId, "USER_EXPERIENCE");
            } catch (Exception e) {
                log.warn("레벨 업적 체크 실패: userId={}, level={}, error={}", userId, levelAfter, e.getMessage());
            }

            // 길드 창설 가능 레벨(20) 도달 시 이벤트 발행
            if (levelAfter >= GUILD_CREATION_MIN_LEVEL && levelBefore < GUILD_CREATION_MIN_LEVEL) {
                eventPublisher.publishEvent(new GuildCreationEligibleEvent(userId, levelAfter));
                log.info("길드 창설 가능 레벨 도달: userId={}, level={}", userId, levelAfter);
            }
        }

        log.info("경험치 획득: userId={}, amount={}, total={}, level={}, categoryId={}",
            userId, expAmount, userExp.getTotalExp(), userExp.getCurrentLevel(), categoryId);

        return UserExperienceResponse.from(userExp, getNextLevelRequiredExp(userExp.getCurrentLevel()));
    }

    /**
     * 카테고리별 경험치 업데이트
     */
    private void updateCategoryExperience(String userId, Long categoryId, String categoryName, int expAmount) {
        UserCategoryExperience categoryExp = userCategoryExperienceRepository
            .findByUserIdAndCategoryId(userId, categoryId)
            .orElseGet(() -> UserCategoryExperience.create(userId, categoryId, categoryName, 0));

        categoryExp.addExperience(expAmount);

        // categoryName이 변경되었을 수 있으므로 업데이트
        if (categoryName != null && !categoryName.equals(categoryExp.getCategoryName())) {
            categoryExp.setCategoryName(categoryName);
        }

        userCategoryExperienceRepository.save(categoryExp);
        log.debug("카테고리별 경험치 업데이트: userId={}, categoryId={}, categoryExp={}",
            userId, categoryId, categoryExp.getTotalExp());
    }

    @Transactional(transactionManager = "gamificationTransactionManager")
    public UserExperienceResponse getUserExperience(String userId) {
        UserExperience userExp = getOrCreateUserExperience(userId);
        return UserExperienceResponse.from(userExp, getNextLevelRequiredExp(userExp.getCurrentLevel()));
    }

    public Page<ExperienceHistory> getExperienceHistory(String userId, Pageable pageable) {
        return experienceHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional(transactionManager = "gamificationTransactionManager")
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
        List<UserLevelConfig> levelConfigs = userLevelConfigCacheService.getAllLevelConfigs();
        if (levelConfigs.isEmpty()) {
            processLevelUpWithDefaultFormula(userExp);
            return;
        }

        while (true) {
            int currentLevel = userExp.getCurrentLevel();
            int nextLevel = currentLevel + 1;

            // 다음 레벨의 설정을 조회하여 필요 경험치 확인
            UserLevelConfig nextLevelConfig = levelConfigs.stream()
                .filter(lc -> lc.getLevel().equals(nextLevel))
                .findFirst()
                .orElse(null);

            // 다음 레벨 설정이 없으면 최대 레벨 도달
            if (nextLevelConfig == null) {
                break;
            }

            int requiredExp = nextLevelConfig.getRequiredExp();
            if (userExp.getCurrentExp() >= requiredExp) {
                userExp.levelUp(requiredExp);
            } else {
                break;
            }

            Integer maxLevel = userLevelConfigCacheService.getMaxLevel();
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
        UserLevelConfig config = userLevelConfigCacheService.getLevelConfigByLevel(currentLevel);
        return config != null ? config.getRequiredExp() : calculateDefaultRequiredExp(currentLevel);
    }

    public List<UserLevelConfig> getAllLevelConfigs() {
        return userLevelConfigCacheService.getAllLevelConfigs();
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
    @Transactional(transactionManager = "gamificationTransactionManager")
    public UserExperienceResponse subtractExperience(String userId, int expAmount, ExpSourceType sourceType,
                                                      Long sourceId, String description) {
        return subtractExperience(userId, expAmount, sourceType, sourceId, description, null, null);
    }

    @Transactional(transactionManager = "gamificationTransactionManager")
    public UserExperienceResponse subtractExperience(String userId, int expAmount, ExpSourceType sourceType,
                                                      Long sourceId, String description, String categoryName) {
        return subtractExperience(userId, expAmount, sourceType, sourceId, description, null, categoryName);
    }

    @Transactional(transactionManager = "gamificationTransactionManager")
    public UserExperienceResponse subtractExperience(String userId, int expAmount, ExpSourceType sourceType,
                                                      Long sourceId, String description, Long categoryId, String categoryName) {
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

        // 카테고리별 경험치 차감 (categoryId가 있을 때만)
        if (categoryId != null && expAmount > 0) {
            subtractCategoryExperience(userId, categoryId, expAmount);
        }

        log.info("경험치 차감: userId={}, amount={}, total={}, level: {} -> {}, categoryId={}",
            userId, expAmount, userExp.getTotalExp(), levelBefore, levelAfter, categoryId);

        return UserExperienceResponse.from(userExp, getNextLevelRequiredExp(userExp.getCurrentLevel()));
    }

    /**
     * 카테고리별 경험치 차감
     */
    private void subtractCategoryExperience(String userId, Long categoryId, int expAmount) {
        userCategoryExperienceRepository.findByUserIdAndCategoryId(userId, categoryId)
            .ifPresent(categoryExp -> {
                long newExp = Math.max(0, categoryExp.getTotalExp() - expAmount);
                categoryExp.setTotalExp(newExp);
                userCategoryExperienceRepository.save(categoryExp);
                log.debug("카테고리별 경험치 차감: userId={}, categoryId={}, categoryExp={}",
                    userId, categoryId, newExp);
            });
    }

    /**
     * 레벨 다운 처리 (경험치 차감으로 인한)
     */
    private void processLevelDown(UserExperience userExp, int targetTotalExp) {
        List<UserLevelConfig> levelConfigs = userLevelConfigCacheService.getAllLevelConfigs();

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

        for (UserLevelConfig config : levelConfigs) {
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

    @Transactional(transactionManager = "metaTransactionManager")
    public UserLevelConfig createOrUpdateLevelConfig(Integer level, Integer requiredExp,
                                                  Integer cumulativeExp, String title, String description) {
        UserLevelConfig config = userLevelConfigRepository.findByLevel(level)
            .orElse(UserLevelConfig.builder().level(level).build());

        config.setRequiredExp(requiredExp);
        config.setCumulativeExp(cumulativeExp);
        config.setTitle(title);
        config.setDescription(description);

        return userLevelConfigRepository.save(config);
    }
}
