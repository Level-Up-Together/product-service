package io.pinkspider.leveluptogethermvp.guildservice.application;

import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildExperienceResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildExperienceHistory;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildExperienceHistory.GuildExpSourceType;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildLevelConfig;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildExperienceHistoryRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildLevelConfigRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
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
public class GuildExperienceService {

    private final GuildRepository guildRepository;
    private final GuildLevelConfigRepository levelConfigRepository;
    private final GuildExperienceHistoryRepository historyRepository;

    @Transactional
    public GuildExperienceResponse addExperience(Long guildId, int expAmount, GuildExpSourceType sourceType,
                                                  Long sourceId, String contributorId, String description) {
        Guild guild = guildRepository.findByIdAndIsActiveTrue(guildId)
            .orElseThrow(() -> new IllegalArgumentException("길드를 찾을 수 없습니다: " + guildId));

        int levelBefore = guild.getCurrentLevel();

        guild.addExperience(expAmount);

        processLevelUp(guild);

        int levelAfter = guild.getCurrentLevel();

        GuildExperienceHistory history = GuildExperienceHistory.builder()
            .guild(guild)
            .sourceType(sourceType)
            .sourceId(sourceId)
            .contributorId(contributorId)
            .expAmount(expAmount)
            .description(description)
            .levelBefore(levelBefore)
            .levelAfter(levelAfter)
            .build();
        historyRepository.save(history);

        if (levelAfter > levelBefore) {
            log.info("길드 레벨 업! guildId={}, {} -> {}", guildId, levelBefore, levelAfter);
        }

        log.info("길드 경험치 획득: guildId={}, amount={}, total={}, level={}",
            guildId, expAmount, guild.getTotalExp(), guild.getCurrentLevel());

        return getGuildExperienceInfo(guild);
    }

    public GuildExperienceResponse getGuildExperience(Long guildId) {
        Guild guild = guildRepository.findByIdAndIsActiveTrue(guildId)
            .orElseThrow(() -> new IllegalArgumentException("길드를 찾을 수 없습니다: " + guildId));

        return getGuildExperienceInfo(guild);
    }

    public Page<GuildExperienceHistory> getExperienceHistory(Long guildId, Pageable pageable) {
        return historyRepository.findByGuildIdOrderByCreatedAtDesc(guildId, pageable);
    }

    public List<GuildLevelConfig> getAllLevelConfigs() {
        return levelConfigRepository.findAllByOrderByLevelAsc();
    }

    /**
     * 길드 경험치 차감 (Saga 보상 트랜잭션용)
     *
     * @param guildId 길드 ID
     * @param expAmount 차감할 경험치
     * @param sourceType 출처 유형
     * @param sourceId 출처 ID
     * @param contributorId 기여자 ID
     * @param description 설명
     * @return 업데이트된 길드 경험치 정보
     */
    @Transactional
    public GuildExperienceResponse subtractExperience(Long guildId, int expAmount, GuildExpSourceType sourceType,
                                                       Long sourceId, String contributorId, String description) {
        Guild guild = guildRepository.findByIdAndIsActiveTrue(guildId)
            .orElseThrow(() -> new IllegalArgumentException("길드를 찾을 수 없습니다: " + guildId));

        int levelBefore = guild.getCurrentLevel();

        // 경험치 차감
        int newCurrentExp = guild.getCurrentExp() - expAmount;
        int newTotalExp = guild.getTotalExp() - expAmount;

        // 레벨 다운 처리
        if (newCurrentExp < 0) {
            processLevelDown(guild, newTotalExp);
        } else {
            guild.setCurrentExp(newCurrentExp);
            guild.setTotalExp(Math.max(0, newTotalExp));
        }

        int levelAfter = guild.getCurrentLevel();

        // 히스토리 기록 (음수 경험치로 기록)
        GuildExperienceHistory history = GuildExperienceHistory.builder()
            .guild(guild)
            .sourceType(sourceType)
            .sourceId(sourceId)
            .contributorId(contributorId)
            .expAmount(-expAmount) // 음수로 기록
            .description(description)
            .levelBefore(levelBefore)
            .levelAfter(levelAfter)
            .build();
        historyRepository.save(history);

        if (levelAfter < levelBefore) {
            log.info("길드 레벨 다운: guildId={}, {} -> {}", guildId, levelBefore, levelAfter);
            // 레벨 다운 시 맥스 멤버 수 조정
            GuildLevelConfig newLevelConfig = levelConfigRepository.findByLevel(levelAfter).orElse(null);
            if (newLevelConfig != null) {
                guild.updateMaxMembersByLevel(newLevelConfig.getMaxMembers());
            }
        }

        log.info("길드 경험치 차감: guildId={}, amount={}, total={}, level: {} -> {}",
            guildId, expAmount, guild.getTotalExp(), levelBefore, levelAfter);

        return getGuildExperienceInfo(guild);
    }

    /**
     * 레벨 다운 처리 (경험치 차감으로 인한)
     */
    private void processLevelDown(Guild guild, int targetTotalExp) {
        List<GuildLevelConfig> levelConfigs = levelConfigRepository.findAllByOrderByLevelAsc();

        if (targetTotalExp <= 0) {
            guild.setCurrentLevel(1);
            guild.setCurrentExp(0);
            guild.setTotalExp(0);
            return;
        }

        guild.setTotalExp(targetTotalExp);

        // 누적 경험치 기반으로 레벨 재계산
        int newLevel = 1;
        int remainingExp = targetTotalExp;

        for (GuildLevelConfig config : levelConfigs) {
            if (config.getCumulativeExp() != null && targetTotalExp >= config.getCumulativeExp()) {
                newLevel = config.getLevel();
                remainingExp = targetTotalExp - config.getCumulativeExp();
            } else if (config.getCumulativeExp() == null) {
                if (remainingExp >= config.getRequiredExp()) {
                    remainingExp -= config.getRequiredExp();
                    newLevel = config.getLevel() + 1;
                } else {
                    break;
                }
            }
        }

        guild.setCurrentLevel(Math.max(1, newLevel));
        guild.setCurrentExp(Math.max(0, remainingExp));
    }

    @Transactional
    public GuildLevelConfig createOrUpdateLevelConfig(Integer level, Integer requiredExp,
                                                       Integer cumulativeExp, Integer maxMembers,
                                                       String title, String description) {
        GuildLevelConfig config = levelConfigRepository.findByLevel(level)
            .orElse(GuildLevelConfig.builder().level(level).build());

        config.setRequiredExp(requiredExp);
        config.setCumulativeExp(cumulativeExp);
        config.setMaxMembers(maxMembers);
        config.setTitle(title);
        config.setDescription(description);

        return levelConfigRepository.save(config);
    }

    private void processLevelUp(Guild guild) {
        List<GuildLevelConfig> levelConfigs = levelConfigRepository.findAllByOrderByLevelAsc();
        if (levelConfigs.isEmpty()) {
            processLevelUpWithDefaultFormula(guild);
            return;
        }

        while (true) {
            int currentLevel = guild.getCurrentLevel();
            GuildLevelConfig currentLevelConfig = levelConfigs.stream()
                .filter(lc -> lc.getLevel().equals(currentLevel))
                .findFirst()
                .orElse(null);

            if (currentLevelConfig == null) {
                break;
            }

            int requiredExp = currentLevelConfig.getRequiredExp();
            if (guild.getCurrentExp() >= requiredExp) {
                guild.levelUp(requiredExp);

                GuildLevelConfig newLevelConfig = levelConfigs.stream()
                    .filter(lc -> lc.getLevel().equals(guild.getCurrentLevel()))
                    .findFirst()
                    .orElse(null);

                if (newLevelConfig != null) {
                    guild.updateMaxMembersByLevel(newLevelConfig.getMaxMembers());
                }
            } else {
                break;
            }

            Integer maxLevel = levelConfigRepository.findMaxLevel();
            if (maxLevel != null && guild.getCurrentLevel() >= maxLevel) {
                break;
            }
        }
    }

    private void processLevelUpWithDefaultFormula(Guild guild) {
        while (true) {
            int requiredExp = calculateDefaultRequiredExp(guild.getCurrentLevel());
            if (guild.getCurrentExp() >= requiredExp) {
                guild.levelUp(requiredExp);
                int newMaxMembers = calculateDefaultMaxMembers(guild.getCurrentLevel());
                guild.updateMaxMembersByLevel(newMaxMembers);
            } else {
                break;
            }
        }
    }

    private int calculateDefaultRequiredExp(int level) {
        return 500 + (level - 1) * 300;
    }

    private int calculateDefaultMaxMembers(int level) {
        return 20 + (level - 1) * 10;
    }

    private GuildExperienceResponse getGuildExperienceInfo(Guild guild) {
        Integer requiredExp = levelConfigRepository.findByLevel(guild.getCurrentLevel())
            .map(GuildLevelConfig::getRequiredExp)
            .orElse(calculateDefaultRequiredExp(guild.getCurrentLevel()));

        String levelTitle = levelConfigRepository.findByLevel(guild.getCurrentLevel())
            .map(GuildLevelConfig::getTitle)
            .orElse("Lv." + guild.getCurrentLevel());

        return GuildExperienceResponse.from(guild, requiredExp, levelTitle);
    }
}
