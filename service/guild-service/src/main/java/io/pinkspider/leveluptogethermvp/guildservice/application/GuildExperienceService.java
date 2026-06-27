package io.pinkspider.leveluptogethermvp.guildservice.application;

import io.pinkspider.leveluptogethermvp.metaservice.guildlevelconfig.application.GuildLevelConfigCacheService;
import io.pinkspider.leveluptogethermvp.metaservice.userlevelconfig.application.UserLevelConfigCacheService;
import io.pinkspider.global.event.GuildLevelUpEvent;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildExperienceResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildExperienceHistory;
import io.pinkspider.global.enums.GuildExpSourceType;
import io.pinkspider.leveluptogethermvp.metaservice.guildlevelconfig.domain.entity.GuildLevelConfig;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildExperienceHistoryRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import io.pinkspider.leveluptogethermvp.metaservice.userlevelconfig.domain.entity.UserLevelConfig;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = "guildTransactionManager")
public class GuildExperienceService {

    private final GuildRepository guildRepository;
    private final GuildLevelConfigCacheService guildLevelConfigCacheService;
    private final GuildExperienceHistoryRepository historyRepository;
    private final GuildMemberRepository guildMemberRepository;
    private final UserLevelConfigCacheService userLevelConfigCacheService;
    private final ApplicationEventPublisher eventPublisher;

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

            // 길드 레벨업 피드 프로젝션 이벤트 발행
            eventPublisher.publishEvent(new GuildLevelUpEvent(
                contributorId, guildId, guild.getName(), levelAfter));
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
        return guildLevelConfigCacheService.getAllLevelConfigs();
    }

    /**
     * 길드 레벨업에 필요한 경험치 계산 공식: 길드 인원수 * 해당 레벨의 유저 레벨업 필요 경험치
     */
    public int calculateGuildRequiredExp(Long guildId, int level) {
        int memberCount = (int) guildMemberRepository.countActiveMembers(guildId);
        // 최소 1명으로 계산 (마스터만 있는 경우)
        memberCount = Math.max(1, memberCount);

        UserLevelConfig config = userLevelConfigCacheService.getLevelConfigByLevel(level);
        int userRequiredExp = config != null ? config.getRequiredExp() : calculateDefaultUserRequiredExp(level);

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
            GuildLevelConfig newLevelConfig = guildLevelConfigCacheService.getLevelConfigByLevel(levelAfter);
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
        List<GuildLevelConfig> levelConfigs = guildLevelConfigCacheService.getAllLevelConfigs();

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

    public GuildLevelConfig createOrUpdateLevelConfig(Integer level, Integer requiredExp,
                                                      Integer cumulativeExp, Integer maxMembers,
                                                      String title, String description) {
        return guildLevelConfigCacheService.createOrUpdateLevelConfig(level, requiredExp, cumulativeExp, maxMembers, title, description);
    }

    /**
     * 길드 레벨/현재 경험치 재계산.
     *
     * <p>QA-204: 어드민 설정(guild_level_config.cumulative_exp)을 단일 기준으로, 누적 경험치(totalExp)
     * 로부터 레벨과 현재 레벨 내 경험치(currentExp)를 계산한다. 기존에는 "인원수 × 유저 레벨 필요 경험치"
     * 라는 별도 공식을 사용해 적은 경험치로도 레벨이 올라가 어드민 설정값과 어긋났다. (레벨 다운 경로인
     * processLevelDown 과 동일한 cumulative 기준으로 통일.)
     */
    private void processLevelUp(Guild guild) {
        List<GuildLevelConfig> levelConfigs = guildLevelConfigCacheService.getAllLevelConfigs();
        int totalExp = Math.max(0, guild.getTotalExp());

        int newLevel = 1;
        int cumulativeForLevel = 0;
        if (levelConfigs != null) {
            for (GuildLevelConfig config : levelConfigs) {
                Integer level = config.getLevel();
                Integer cumulative = config.getCumulativeExp();
                if (level != null
                        && cumulative != null
                        && level > newLevel
                        && totalExp >= cumulative) {
                    newLevel = level;
                    cumulativeForLevel = cumulative;
                }
            }
        }

        guild.setCurrentLevel(Math.max(1, newLevel));
        guild.setCurrentExp(Math.max(0, totalExp - cumulativeForLevel));

        // 현재 레벨의 최대 인원수 갱신 (설정 없으면 기본 공식)
        GuildLevelConfig levelConfig =
                guildLevelConfigCacheService.getLevelConfigByLevel(guild.getCurrentLevel());
        int maxMembers =
                levelConfig != null && levelConfig.getMaxMembers() != null
                        ? levelConfig.getMaxMembers()
                        : calculateDefaultMaxMembers(guild.getCurrentLevel());
        guild.updateMaxMembersByLevel(maxMembers);
    }

    /**
     * 기본 최대 인원 공식 (guild_level_config 설정이 없을 경우)
     */
    private int calculateDefaultMaxMembers(int level) {
        return 10 + (level - 1) * 5;
    }

    private GuildExperienceResponse getGuildExperienceInfo(Guild guild) {
        GuildLevelConfig levelConfig =
                guildLevelConfigCacheService.getLevelConfigByLevel(guild.getCurrentLevel());

        // QA-204: 다음 레벨까지 필요한 경험치는 어드민 설정(required_exp)을 사용한다.
        // 기존 "인원수 × 유저 레벨 필요 경험치" 공식은 어드민 설정과 어긋났다.
        int requiredExp =
                levelConfig != null && levelConfig.getRequiredExp() != null
                        ? levelConfig.getRequiredExp()
                        : calculateDefaultUserRequiredExp(guild.getCurrentLevel());

        String levelTitle = levelConfig != null ? levelConfig.getTitle() : "Lv." + guild.getCurrentLevel();

        return GuildExperienceResponse.from(guild, requiredExp, levelTitle);
    }
}
