package io.pinkspider.leveluptogethermvp.gamificationservice.application;

import io.pinkspider.global.enums.ExpSourceType;
import io.pinkspider.global.facade.GamificationQueryFacade;
import io.pinkspider.global.facade.dto.DetailedTitleInfoDto;
import io.pinkspider.global.facade.dto.EquippedItemRarityDto;
import io.pinkspider.global.facade.dto.SeasonDto;
import io.pinkspider.global.facade.dto.SeasonMvpDataDto;
import io.pinkspider.global.facade.dto.SeasonMvpGuildDto;
import io.pinkspider.global.facade.dto.SeasonMvpPlayerDto;
import io.pinkspider.global.facade.dto.SeasonMyRankingDto;
import io.pinkspider.global.facade.dto.SeasonRankRewardDto;
import io.pinkspider.global.facade.dto.TitleChangeResultDto;
import io.pinkspider.global.facade.dto.TitleInfoDto;
import io.pinkspider.global.facade.dto.UserAchievementDto;
import io.pinkspider.global.facade.dto.UserExperienceDto;
import io.pinkspider.global.facade.dto.UserItemDto;
import io.pinkspider.global.facade.dto.UserStatsDto;
import io.pinkspider.global.facade.dto.UserTitleDto;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application.AchievementService;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application.TitleService;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application.TitleService.DetailedTitleInfo;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application.TitleService.TitleChangeResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application.TitleService.TitleInfo;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.UserAchievementResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.attendance.application.AttendanceService;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.application.DiamondService;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Title;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserExperience;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserStats;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserTitle;
import io.pinkspider.leveluptogethermvp.gamificationservice.experience.application.UserExperienceService;
import io.pinkspider.leveluptogethermvp.gamificationservice.experience.domain.dto.UserExperienceResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto.SeasonMvpData;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.application.SeasonRankingService;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto.SeasonMyRankingResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.entity.Season;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.entity.SeasonRankReward;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.infrastructure.SeasonRankRewardRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.application.UserItemService;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ShopItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.ShopItemRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.stats.application.UserStatsService;
import io.pinkspider.leveluptogethermvp.metaservice.application.MissionCategoryService;
import io.pinkspider.leveluptogethermvp.metaservice.domain.dto.MissionCategoryResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 외부 서비스용 게임화 Facade gamificationservice 외부에서 gamification_db에 직접 접근하지 않고 이 서비스를 통해 접근한다.
 */
@Service
@Slf4j
@Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
public class GamificationQueryFacadeService implements GamificationQueryFacade {

    private final TitleService titleService;
    private final UserItemService userItemService;
    private final UserExperienceService userExperienceService;
    private final UserStatsService userStatsService;
    private final AchievementService achievementService;
    private final AttendanceService attendanceService;
    private final SeasonRankingService seasonRankingService;
    private final SeasonRankRewardRepository seasonRankRewardRepository;
    private final ShopItemRepository shopItemRepository;
    private final DiamondService diamondService;
    private final MissionCategoryService missionCategoryService;

    public GamificationQueryFacadeService(
        TitleService titleService,
        UserItemService userItemService,
        UserExperienceService userExperienceService,
        UserStatsService userStatsService,
        AchievementService achievementService,
        AttendanceService attendanceService,
        @Lazy SeasonRankingService seasonRankingService,
        SeasonRankRewardRepository seasonRankRewardRepository,
        ShopItemRepository shopItemRepository,
        DiamondService diamondService,
        MissionCategoryService missionCategoryService
    ) {
        this.titleService = titleService;
        this.userItemService = userItemService;
        this.userExperienceService = userExperienceService;
        this.userStatsService = userStatsService;
        this.achievementService = achievementService;
        this.attendanceService = attendanceService;
        this.seasonRankingService = seasonRankingService;
        this.seasonRankRewardRepository = seasonRankRewardRepository;
        this.shopItemRepository = shopItemRepository;
        this.diamondService = diamondService;
        this.missionCategoryService = missionCategoryService;
    }

    // ========== 레벨 조회 ==========

    @Override
    public int getUserLevel(String userId) {
        return userExperienceService.getUserLevel(userId);
    }

    @Override
    public Map<String, Integer> getUserLevelMap(List<String> userIds) {
        return userExperienceService.getUserLevelMap(userIds);
    }

    @Override
    @Transactional(transactionManager = "gamificationTransactionManager")
    public UserExperienceDto getOrCreateUserExperience(String userId) {
        UserExperience ue = userExperienceService.getOrCreateUserExperience(userId);
        return toExperienceDto(ue);
    }

    @Override
    public List<Object[]> findTopExpGainersByPeriod(LocalDateTime start, LocalDateTime end, Pageable pageable) {
        return userExperienceService.findTopExpGainersByPeriod(start, end, pageable);
    }

    @Override
    public List<Object[]> findTopExpGainersByCategoryAndPeriod(String categoryName, LocalDateTime start,
                                                               LocalDateTime end, Pageable pageable) {
        return userExperienceService.findTopExpGainersByCategoryAndPeriod(categoryName, start, end, pageable);
    }

    @Override
    public Map<LocalDate, Long> getDailyExpSummary(String userId, LocalDateTime startUtc,
                                                   LocalDateTime endUtc, String timezone) {
        return userExperienceService.getDailyExpSummary(userId, startUtc, endUtc, timezone);
    }

    // ========== 칭호 조회 ==========

    @Override
    public TitleInfoDto getCombinedEquippedTitleInfo(String userId) {
        TitleInfo info = titleService.getCombinedEquippedTitleInfo(userId);
        return new TitleInfoDto(info.name(), info.rarity(), info.colorCode());
    }

    @Override
    public DetailedTitleInfoDto getDetailedEquippedTitleInfo(String userId) {
        DetailedTitleInfo info = titleService.getDetailedEquippedTitleInfo(userId);
        return new DetailedTitleInfoDto(
            info.combinedName(), info.highestRarity(),
            info.leftTitle(), info.leftRarity(),
            info.rightTitle(), info.rightRarity()
        );
    }

    @Override
    public Map<String, String> getEquippedLeftTitleNameMap(List<String> userIds) {
        return titleService.getEquippedLeftTitleNameMap(userIds);
    }

    @Override
    public List<UserTitleDto> getEquippedTitlesByUserId(String userId) {
        return titleService.getEquippedTitleEntitiesByUserId(userId).stream()
            .map(this::toTitleDto)
            .toList();
    }

    @Override
    public Map<String, List<UserTitleDto>> getEquippedTitlesByUserIds(List<String> userIds) {
        return titleService.getEquippedTitleEntitiesByUserIds(userIds).entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().stream().map(this::toTitleDto).toList()
            ));
    }

    @Override
    public List<UserTitleDto> getUserTitlesWithTitleInfo(String userId) {
        return titleService.getUserTitleEntitiesWithTitle(userId).stream()
            .map(this::toTitleDto)
            .toList();
    }

    @Override
    public long countUserTitles(String userId) {
        return titleService.countUserTitles(userId);
    }

    @Override
    @Transactional(transactionManager = "gamificationTransactionManager")
    public TitleChangeResultDto changeTitles(String userId, Long leftUserTitleId, Long rightUserTitleId) {
        TitleChangeResult result = titleService.changeTitles(userId, leftUserTitleId, rightUserTitleId);
        return new TitleChangeResultDto(
            toTitleDto(result.leftTitle()),
            toTitleDto(result.rightTitle())
        );
    }

    // ========== 칭호 부여 ==========

    @Override
    @Transactional(transactionManager = "gamificationTransactionManager")
    public void grantAndEquipDefaultTitles(String userId) {
        titleService.grantAndEquipDefaultTitles(userId);
    }

    // ========== 아이템 조회 (LUT-296) ==========

    @Override
    public List<UserItemDto> getEquippedItemsByUserId(String userId) {
        return userItemService.getEquippedItemEntities(userId).stream()
            .map(this::toUserItemDto)
            .toList();
    }

    @Override
    public Map<String, List<EquippedItemRarityDto>> getEquippedItemRaritiesByUserIds(
            List<String> userIds) {
        return userItemService.getEquippedItemRarityMap(userIds);
    }

    private UserItemDto toUserItemDto(
            io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.UserItem userItem) {
        var item = userItem.getShopItem();
        return new UserItemDto(
            userItem.getId(),
            userItem.getUserId(),
            item.getId(),
            item.getName(),
            item.getNameEn(),
            item.getNameAr(),
            item.getNameJa(),
            item.getDescription(),
            item.getDescriptionEn(),
            item.getDescriptionAr(),
            item.getDescriptionJa(),
            item.getItemType() != null ? item.getItemType().name() : null,
            item.getRarity(),
            item.getImageUrl(),
            item.getImagePosition() != null ? item.getImagePosition().name() : null,
            item.getEffectCode(),
            userItem.getIsEquipped(),
            userItem.getAcquiredAt());
    }

    // ========== 스탯 조회 ==========

    @Override
    @Transactional(transactionManager = "gamificationTransactionManager")
    public UserStatsDto getOrCreateUserStats(String userId) {
        UserStats us = userStatsService.getOrCreateUserStats(userId);
        return toStatsDto(us);
    }

    @Override
    public Double calculateRankingPercentile(long rankingPoints) {
        return userStatsService.calculateRankingPercentile(rankingPoints);
    }

    // ========== 출석 조회 ==========

    @Override
    public long countAttendanceDays(String userId) {
        return attendanceService.countAttendanceDays(userId);
    }

    // ========== 업적 조회 ==========

    @Override
    public List<UserAchievementDto> getUserAchievements(String userId) {
        return achievementService.getUserAchievements(userId).stream()
            .map(this::toAchievementDto)
            .toList();
    }

    @Override
    @Transactional(transactionManager = "gamificationTransactionManager")
    public boolean syncUserAchievements(String userId) {
        return achievementService.syncUserAchievements(userId);
    }

    // ========== 경험치 WRITE (Saga step용) ==========

    @Override
    @Transactional(transactionManager = "gamificationTransactionManager")
    public UserExperienceDto addExperience(String userId, int expAmount, ExpSourceType sourceType,
                                           Long sourceId, String description, Long categoryId, String categoryName) {
        UserExperienceResponse resp = userExperienceService.addExperience(userId, expAmount, sourceType, sourceId, description, categoryId,
            categoryName);
        return toExperienceResponseDto(resp);
    }

    @Override
    @Transactional(transactionManager = "gamificationTransactionManager")
    public UserExperienceDto subtractExperience(String userId, int expAmount, ExpSourceType sourceType,
                                                Long sourceId, String description, Long categoryId, String categoryName) {
        UserExperienceResponse resp = userExperienceService.subtractExperience(userId, expAmount, sourceType, sourceId, description, categoryId,
            categoryName);
        return toExperienceResponseDto(resp);
    }

    // ========== 다이아 WRITE (Saga step용) ==========

    @Override
    @Transactional(transactionManager = "gamificationTransactionManager")
    public boolean awardMissionBookDiamond(String userId, Long templateId, String missionTitle) {
        return diamondService.awardMissionBookDiamond(userId, templateId, missionTitle);
    }

    // ========== 통계 WRITE (Saga step용) ==========

    @Override
    @Transactional(transactionManager = "gamificationTransactionManager")
    public void recordMissionCompletion(String userId, boolean isGuildMission) {
        userStatsService.recordMissionCompletion(userId, isGuildMission);
    }

    @Override
    @Transactional(transactionManager = "gamificationTransactionManager")
    public void undoMissionCompletion(String userId, boolean isGuildMission) {
        userStatsService.undoMissionCompletion(userId, isGuildMission);
    }

    @Override
    @Transactional(transactionManager = "gamificationTransactionManager")
    public void recordMissionFullCompletion(String userId, int durationDays) {
        userStatsService.recordMissionFullCompletion(userId, durationDays);
    }

    @Override
    @Transactional(transactionManager = "gamificationTransactionManager")
    public void undoMissionFullCompletion(String userId) {
        userStatsService.undoMissionFullCompletion(userId);
    }

    // ========== 업적 체크 (Saga step용) ==========

    @Override
    @Transactional(transactionManager = "gamificationTransactionManager")
    public void checkAchievementsByDataSource(String userId, String dataSource) {
        achievementService.checkAchievementsByDataSource(userId, dataSource);
    }

    // ========== 시즌 조회 ==========

    @Override
    public Optional<SeasonMvpDataDto> getSeasonMvpData(String locale) {
        return seasonRankingService.getSeasonMvpData(locale)
            .map(this::toSeasonMvpDataDto);
    }

    @Override
    public Optional<SeasonDto> getSeasonById(Long seasonId) {
        return seasonRankingService.getSeasonById(seasonId)
            .map(this::toSeasonDto);
    }

    @Override
    public Optional<SeasonDto> getCurrentSeason() {
        return seasonRankingService.getCurrentSeason()
            .map(r -> new SeasonDto(
                r.id(), r.title(), r.description(), r.startAt(), r.endAt(),
                r.rewardTitleId(), r.rewardTitleName(),
                r.status() != null ? r.status().name() : null, r.statusName()
            ));
    }

    @Override
    public List<SeasonRankRewardDto> getSeasonRankRewards(Long seasonId) {
        var rewards = seasonRankRewardRepository.findBySeasonIdOrderBySortOrder(seasonId);

        // LUT-374: 보상 아이템 정보 배치 로드 (N+1 방지). 아이템 미지정 보상은 item=null.
        List<Long> itemIds = rewards.stream()
            .map(r -> r.getItemId())
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
        Map<Long, ShopItem> itemById = itemIds.isEmpty()
            ? Map.of()
            : shopItemRepository.findAllById(itemIds).stream()
                .collect(Collectors.toMap(ShopItem::getId, item -> item));

        // LUT-414: 카테고리형 보상의 로케일 변형(카테고리명/랭킹타입)용 카테고리 배치 로드.
        // 조회 실패는 한글 스냅샷 폴백 — display 필드가 시즌 화면 렌더를 막지 않게 한다.
        Map<Long, MissionCategoryResponse> categoryById = new java.util.HashMap<>();
        rewards.stream()
            .map(r -> r.getCategoryId())
            .filter(java.util.Objects::nonNull)
            .distinct()
            .forEach(categoryId -> {
                try {
                    categoryById.put(categoryId, missionCategoryService.getCategory(categoryId));
                } catch (Exception e) {
                    log.warn("시즌 보상 카테고리 조회 실패 (한글 스냅샷 폴백): categoryId={}", categoryId);
                }
            });

        return rewards.stream()
            .map(r -> {
                MissionCategoryResponse category =
                    r.getCategoryId() == null ? null : categoryById.get(r.getCategoryId());
                String categoryNameEn = category != null ? category.getNameEn() : null;
                String categoryNameAr = category != null ? category.getNameAr() : null;
                String categoryNameJa = category != null ? category.getNameJa() : null;
                return new SeasonRankRewardDto(
                    r.getId(), r.getSeason().getId(), r.getRankStart(), r.getRankEnd(),
                    r.getRankRangeDisplay(), r.getRankRangeDisplayEn(),
                    r.getRankRangeDisplayAr(), r.getRankRangeDisplayJa(),
                    r.getCategoryId(), r.getCategoryName(),
                    categoryNameEn, categoryNameAr, categoryNameJa,
                    r.getRankingTypeDisplay(),
                    rankingTypeDisplayLocalized(r, categoryNameEn, "Overall"),
                    rankingTypeDisplayLocalized(r, categoryNameAr, "الإجمالي"),
                    rankingTypeDisplayLocalized(r, categoryNameJa, "総合"),
                    r.getTitleId(), r.getTitleName(),
                    r.getTitleRarity(), r.getSortOrder(), r.getIsActive(),
                    // Map.of() 불변 맵은 get(null) 에서 NPE — itemId 미지정 보상은 선분기
                    toSeasonRewardItemDto(
                        r.getItemId() == null ? null : itemById.get(r.getItemId()))
                );
            })
            .toList();
    }

    /**
     * LUT-414: 랭킹 타입 로케일 표기 — 전체 랭킹은 로케일별 "전체" 라벨, 카테고리 랭킹은 해당 로케일
     * 카테고리명(미등록 언어는 한글 스냅샷 폴백).
     */
    private String rankingTypeDisplayLocalized(
            SeasonRankReward reward, String localizedCategoryName, String overallLabel) {
        if (reward.getCategoryId() == null) {
            return overallLabel;
        }
        return localizedCategoryName != null && !localizedCategoryName.isBlank()
            ? localizedCategoryName
            : reward.getCategoryName();
    }

    private io.pinkspider.global.facade.dto.SeasonRewardItemDto toSeasonRewardItemDto(ShopItem item) {
        if (item == null) {
            return null;
        }
        return new io.pinkspider.global.facade.dto.SeasonRewardItemDto(
            item.getId(), item.getName(), item.getNameEn(), item.getNameAr(), item.getNameJa(),
            item.getDescription(), item.getDescriptionEn(), item.getDescriptionAr(),
            item.getDescriptionJa(),
            item.getRarity() != null ? item.getRarity().name() : null,
            item.getImageUrl());
    }

    @Override
    public List<SeasonMvpPlayerDto> getSeasonPlayerRankings(Long seasonId, String categoryName, int limit, String locale) {
        Season season = seasonRankingService.getSeasonById(seasonId).orElse(null);
        if (season == null) {
            return List.of();
        }
        return seasonRankingService.getSeasonPlayerRankings(season, categoryName, limit, locale).stream()
            .map(p -> new SeasonMvpPlayerDto(
                p.userId(), p.nickname(), p.profileImageUrl(), p.level(),
                p.title(), p.titleRarity(), p.leftTitle(), p.leftTitleRarity(),
                p.rightTitle(), p.rightTitleRarity(), p.seasonExp(), p.rank(),
                p.equippedItemRarities() != null ? p.equippedItemRarities() : List.of()
            ))
            .toList();
    }

    @Override
    public List<SeasonMvpGuildDto> getSeasonGuildRankings(Long seasonId, int limit) {
        Season season = seasonRankingService.getSeasonById(seasonId).orElse(null);
        if (season == null) {
            return List.of();
        }
        return seasonRankingService.getSeasonGuildRankings(season, limit).stream()
            .map(g -> new SeasonMvpGuildDto(
                g.guildId(), g.name(), g.imageUrl(), g.level(),
                g.memberCount(), g.seasonExp(), g.rank()
            ))
            .toList();
    }

    @Override
    public SeasonMyRankingDto getMySeasonRanking(Long seasonId, String userId) {
        Season season = seasonRankingService.getSeasonById(seasonId).orElse(null);
        if (season == null) {
            return SeasonMyRankingDto.empty();
        }
        SeasonMyRankingResponse r = seasonRankingService.getMySeasonRanking(season, userId);
        return new SeasonMyRankingDto(
            r.playerRank(), r.playerSeasonExp(), r.guildRank(),
            r.guildSeasonExp(), r.guildId(), r.guildName()
        );
    }

    @Override
    public void evictAllSeasonCaches() {
        seasonRankingService.evictAllSeasonCaches();
    }

    // ========== 변환 유틸리티 ==========

    private UserExperienceDto toExperienceDto(UserExperience ue) {
        return new UserExperienceDto(
            ue.getId(), ue.getUserId(), ue.getCurrentLevel(), ue.getCurrentExp(),
            ue.getTotalExp(), null, null, null
        );
    }

    private UserExperienceDto toExperienceResponseDto(UserExperienceResponse resp) {
        return new UserExperienceDto(
            resp.getId(), resp.getUserId(), resp.getCurrentLevel(), resp.getCurrentExp(),
            resp.getTotalExp(), resp.getNextLevelRequiredExp(), resp.getExpToNextLevel(), resp.getProgressToNextLevel()
        );
    }

    private UserTitleDto toTitleDto(UserTitle ut) {
        Title t = ut.getTitle();
        return new UserTitleDto(
            ut.getId(), ut.getUserId(), t.getId(),
            t.getName(), t.getNameEn(), t.getNameAr(), t.getNameJa(),
            t.getDescription(), t.getDescriptionEn(), t.getDescriptionAr(), t.getDescriptionJa(),
            t.getRarity(), t.getPositionType(), t.getColorCode(), t.getIconUrl(),
            ut.getIsEquipped(), ut.getEquippedPosition(), ut.getAcquiredAt()
        );
    }

    private UserStatsDto toStatsDto(UserStats us) {
        return new UserStatsDto(
            us.getId(), us.getUserId(),
            us.getTotalMissionCompletions(), us.getTotalMissionFullCompletions(),
            us.getTotalGuildMissionCompletions(), us.getCurrentStreak(), us.getMaxStreak(),
            us.getLastActivityDate(), us.getTotalAchievementsCompleted(),
            us.getTotalTitlesAcquired(), us.getRankingPoints(),
            us.getMaxCompletedMissionDuration(), us.getTotalLikesReceived(), us.getFriendCount()
        );
    }

    private UserAchievementDto toAchievementDto(UserAchievementResponse resp) {
        return new UserAchievementDto(
            resp.getId(), resp.getAchievementId(), resp.getName(), resp.getDescription(),
            resp.getCategoryCode(), resp.getMissionCategoryId(), resp.getMissionCategoryName(),
            resp.getIconUrl(), resp.getCurrentCount(), resp.getRequiredCount(),
            resp.getProgressPercent(), resp.getIsCompleted(), resp.getCompletedAt(),
            resp.getIsRewardClaimed(), resp.getRewardExp(), resp.getRewardTitleId()
        );
    }

    private SeasonDto toSeasonDto(Season season) {
        String status = season.getStatus() != null ? season.getStatus().name() : null;
        String statusName = season.getStatus() != null ? season.getStatus().getDescription() : null;
        return new SeasonDto(
            season.getId(), season.getTitle(), season.getDescription(),
            season.getStartAt(), season.getEndAt(),
            season.getRewardTitleId(), season.getRewardTitleName(),
            status, statusName
        );
    }

    private SeasonMvpDataDto toSeasonMvpDataDto(SeasonMvpData data) {
        SeasonDto season = new SeasonDto(
            data.currentSeason().id(), data.currentSeason().title(),
            data.currentSeason().description(), data.currentSeason().startAt(),
            data.currentSeason().endAt(), data.currentSeason().rewardTitleId(),
            data.currentSeason().rewardTitleName(),
            data.currentSeason().status() != null ? data.currentSeason().status().name() : null,
            data.currentSeason().statusName()
        );

        List<SeasonMvpPlayerDto> players = data.seasonMvpPlayers().stream()
            .map(p -> new SeasonMvpPlayerDto(
                p.userId(), p.nickname(), p.profileImageUrl(), p.level(),
                p.title(), p.titleRarity(), p.leftTitle(), p.leftTitleRarity(),
                p.rightTitle(), p.rightTitleRarity(), p.seasonExp(), p.rank(),
                p.equippedItemRarities() != null ? p.equippedItemRarities() : List.of()
            ))
            .toList();

        List<SeasonMvpGuildDto> guilds = data.seasonMvpGuilds().stream()
            .map(g -> new SeasonMvpGuildDto(
                g.guildId(), g.name(), g.imageUrl(), g.level(),
                g.memberCount(), g.seasonExp(), g.rank()
            ))
            .toList();

        return new SeasonMvpDataDto(season, players, guilds);
    }
}
