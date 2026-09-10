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
    private final GuildPointService guildPointService;

    @Transactional
    public GuildExperienceResponse addExperience(Long guildId, int expAmount, GuildExpSourceType sourceType,
                                                 Long sourceId, String contributorId, String description) {
        Guild guild = guildRepository.findByIdAndIsActiveTrue(guildId)
            .orElseThrow(() -> new IllegalArgumentException("길드를 찾을 수 없습니다: " + guildId));

        int levelBefore = guild.getCurrentLevel();

        guild.addExperience(expAmount);

        // LUT-483: 길드 미션 EXP 는 일간 활동 포인트로도 적립된다 (유저×일자 사다리, 누적 차분).
        // 레벨·랭킹의 기준은 포인트이므로 적립 직후 레벨을 재판정한다.
        if (sourceType == GuildExpSourceType.GUILD_MISSION_EXECUTION) {
            guildPointService.accruePoints(guild, contributorId, expAmount);
        }

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

        // 경험치 차감 (Saga 보상 트랜잭션용 EXP 정정).
        // LUT-483: 레벨·랭킹의 기준이 포인트로 전환되어 EXP 차감은 레벨에 영향을 주지 않는다.
        // 포인트는 정책상 단조 증가만 하므로(회수 없음) 여기서 건드리지 않는다.
        guild.setCurrentExp(Math.max(0, guild.getCurrentExp() - expAmount));
        guild.setTotalExp(Math.max(0, guild.getTotalExp() - expAmount));

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

        log.info("길드 경험치 차감: guildId={}, amount={}, total={}, level: {} -> {}",
            guildId, expAmount, guild.getTotalExp(), levelBefore, levelAfter);

        return getGuildExperienceInfo(guild);
    }

    public GuildLevelConfig createOrUpdateLevelConfig(Integer level, Integer requiredExp,
                                                      Integer cumulativeExp, Integer maxMembers,
                                                      String title, String description) {
        return guildLevelConfigCacheService.createOrUpdateLevelConfig(level, requiredExp, cumulativeExp, maxMembers, title, description);
    }

    /**
     * 길드 레벨/현재 포인트 재계산.
     *
     * <p>QA-204: 어드민 설정(guild_level_config)을 단일 기준으로 레벨을 계산한다.
     * LUT-483: 레벨 기준을 누적 경험치(totalExp) → 누적 활동 포인트(totalPoint)로 전환.
     * 누적 EXP 는 카테고리별 하루 획득 총량 차이로 순위·레벨이 편향되므로, 상한 있는 일간
     * 포인트 사다리의 누적값(cumulative_point)으로 판정한다. EXP 필드(currentExp/totalExp)는
     * 표기용으로만 유지되며 레벨과 무관해진다.
     */
    private void processLevelUp(Guild guild) {
        List<GuildLevelConfig> levelConfigs = guildLevelConfigCacheService.getAllLevelConfigs();
        int totalPoint = Math.max(0, guild.getTotalPoint());

        int newLevel = 1;
        int cumulativeForLevel = 0;
        if (levelConfigs != null) {
            for (GuildLevelConfig config : levelConfigs) {
                Integer level = config.getLevel();
                Integer cumulative = config.getCumulativePoint();
                if (level != null
                        && cumulative != null
                        && level > newLevel
                        && totalPoint >= cumulative) {
                    newLevel = level;
                    cumulativeForLevel = cumulative;
                }
            }
        }

        guild.setCurrentLevel(Math.max(1, newLevel));
        guild.setCurrentPoint(Math.max(0, totalPoint - cumulativeForLevel));

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
