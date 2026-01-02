package io.pinkspider.leveluptogethermvp.guildservice.application;

import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildExperienceResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildExperienceHistory;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildExperienceHistory.GuildExpSourceType;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildLevelConfig;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildExperienceHistoryRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildLevelConfigRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import io.pinkspider.leveluptogethermvp.metaservice.domain.entity.LevelConfig;
import io.pinkspider.leveluptogethermvp.metaservice.infrastructure.LevelConfigRepository;
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
    private final GuildLevelConfigRepository guildLevelConfigRepository;
    private final GuildExperienceHistoryRepository historyRepository;
    private final GuildMemberRepository guildMemberRepository;
    private final LevelConfigRepository userLevelConfigRepository;

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
        return guildLevelConfigRepository.findAllByOrderByLevelAsc();
    }

    /**
     * 길드 레벨업에 필요한 경험치 계산 공식: 길드 인원수 * 해당 레벨의 유저 레벨업 필요 경험치
     */
    public int calculateGuildRequiredExp(Long guildId, int level) {
        int memberCount = (int) guildMemberRepository.countActiveMembers(guildId);
        // 최소 1명으로 계산 (마스터만 있는 경우)
        memberCount = Math.max(1, memberCount);

        int userRequiredExp = userLevelConfigRepository.findByLevel(level)
            .map(LevelConfig::getRequiredExp)
            .orElse(calculateDefaultUserRequiredExp(level));

        return memberCount * userRequiredExp;
    }

    /**
     * 유저 레벨업 기본 공식 (설정이 없을 경우)
     */
    private int calculateDefaultUserRequiredExp(int level) {
        return 100 + (level - 1) * 50;
    }

    /**
     * 길드 경험치 차감 (Saga 보상 트랜잭션용)
     *
     * @param guildId       길드 ID
     * @param expAmount     차감할 경험치
     * @param sourceType    출처 유형
     * @param sourceId      출처 ID
     * @param contributorId 기여자 ID
     * @param description   설명
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
            GuildLevelConfig newLevelConfig = guildLevelConfigRepository.findByLevel(levelAfter).orElse(null);
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
        List<GuildLevelConfig> levelConfigs = guildLevelConfigRepository.findAllByOrderByLevelAsc();

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
        GuildLevelConfig config = guildLevelConfigRepository.findByLevel(level)
            .orElse(GuildLevelConfig.builder().level(level).build());

        config.setRequiredExp(requiredExp);
        config.setCumulativeExp(cumulativeExp);
        config.setMaxMembers(maxMembers);
        config.setTitle(title);
        config.setDescription(description);

        return guildLevelConfigRepository.save(config);
    }

    /**
     * 길드 레벨업 처리 레벨업 조건: 인원수 * 유저 레벨 필요 경험치
     */
    private void processLevelUp(Guild guild) {
        List<GuildLevelConfig> guildLevelConfigs = guildLevelConfigRepository.findAllByOrderByLevelAsc();

        while (true) {
            int currentLevel = guild.getCurrentLevel();

            // 길드 레벨업에 필요한 경험치 = 인원수 * 유저 레벨 필요 경험치
            int requiredExp = calculateGuildRequiredExp(guild.getId(), currentLevel);

            if (guild.getCurrentExp() >= requiredExp) {
                guild.levelUp(requiredExp);

                // 레벨업 시 최대 인원수 업데이트 (guild_level_config에서 조회)
                GuildLevelConfig newLevelConfig = guildLevelConfigs.stream()
                    .filter(lc -> lc.getLevel().equals(guild.getCurrentLevel()))
                    .findFirst()
                    .orElse(null);

                if (newLevelConfig != null) {
                    guild.updateMaxMembersByLevel(newLevelConfig.getMaxMembers());
                } else {
                    // 길드 레벨 설정이 없으면 기본 공식 사용
                    int newMaxMembers = calculateDefaultMaxMembers(guild.getCurrentLevel());
                    guild.updateMaxMembersByLevel(newMaxMembers);
                }

                log.info("길드 레벨업 조건: guildId={}, level={}, memberCount={}, requiredExp={}",
                    guild.getId(), currentLevel, guildMemberRepository.countActiveMembers(guild.getId()), requiredExp);
            } else {
                break;
            }

            // 최대 레벨 체크 (유저 레벨 기준)
            Integer maxUserLevel = userLevelConfigRepository.findMaxLevel();
            if (maxUserLevel != null && guild.getCurrentLevel() >= maxUserLevel) {
                break;
            }
        }
    }

    /**
     * 기본 최대 인원 공식 (guild_level_config 설정이 없을 경우)
     */
    private int calculateDefaultMaxMembers(int level) {
        return 10 + (level - 1) * 5;
    }

    private GuildExperienceResponse getGuildExperienceInfo(Guild guild) {
        // 길드 레벨업에 필요한 경험치 = 인원수 * 유저 레벨 필요 경험치
        int requiredExp = calculateGuildRequiredExp(guild.getId(), guild.getCurrentLevel());

        String levelTitle = guildLevelConfigRepository.findByLevel(guild.getCurrentLevel())
            .map(GuildLevelConfig::getTitle)
            .orElse("Lv." + guild.getCurrentLevel());

        return GuildExperienceResponse.from(guild, requiredExp, levelTitle);
    }
}
