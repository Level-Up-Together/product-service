package io.pinkspider.leveluptogethermvp.adminservice.application;

import io.pinkspider.leveluptogethermvp.adminservice.api.dto.SeasonMvpData;
import io.pinkspider.leveluptogethermvp.adminservice.api.dto.SeasonMvpGuildResponse;
import io.pinkspider.leveluptogethermvp.adminservice.api.dto.SeasonMvpPlayerResponse;
import io.pinkspider.leveluptogethermvp.adminservice.api.dto.SeasonResponse;
import io.pinkspider.leveluptogethermvp.adminservice.domain.entity.Season;
import io.pinkspider.leveluptogethermvp.adminservice.infrastructure.SeasonRepository;
import io.pinkspider.leveluptogethermvp.bffservice.api.dto.SeasonMyRankingResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildMember;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionCategory;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionCategoryRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Title;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserExperience;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserTitle;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.TitlePosition;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.ExperienceHistoryRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserExperienceRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserTitleRepository;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildExperienceHistoryRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import io.pinkspider.global.translation.LocaleUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeasonRankingService {

    private final SeasonRepository seasonRepository;
    private final ExperienceHistoryRepository experienceHistoryRepository;
    private final GuildExperienceHistoryRepository guildExperienceHistoryRepository;
    private final UserRepository userRepository;
    private final UserExperienceRepository userExperienceRepository;
    private final UserTitleRepository userTitleRepository;
    private final GuildRepository guildRepository;
    private final GuildMemberRepository guildMemberRepository;
    private final MissionCategoryRepository missionCategoryRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 현재 활성 시즌 조회
     */
    @Cacheable(value = "currentSeason", cacheManager = "redisCacheManager")
    public Optional<SeasonResponse> getCurrentSeason() {
        return seasonRepository.findCurrentSeason(LocalDateTime.now())
            .map(SeasonResponse::from);
    }

    /**
     * 시즌 MVP 데이터 조회 (시즌 정보 + 플레이어 랭킹 + 길드 랭킹)
     */
    public Optional<SeasonMvpData> getSeasonMvpData() {
        return getSeasonMvpData(null);
    }

    /**
     * 시즌 MVP 데이터 조회 (다국어 지원)
     * 캐시는 Optional을 피하고 null을 반환하도록 하여 직렬화 문제 방지
     */
    public Optional<SeasonMvpData> getSeasonMvpData(String locale) {
        SeasonMvpData cached = getSeasonMvpDataCached(locale);
        return Optional.ofNullable(cached);
    }

    /**
     * 시즌 MVP 데이터 캐시 조회 (내부용 - null 허용)
     */
    @Cacheable(value = "seasonMvpData", key = "#locale ?: 'ko'", cacheManager = "redisCacheManager", unless = "#result == null")
    public SeasonMvpData getSeasonMvpDataCached(String locale) {
        Optional<Season> currentSeasonOpt = seasonRepository.findCurrentSeason(LocalDateTime.now());

        if (currentSeasonOpt.isEmpty()) {
            return null;
        }

        Season season = currentSeasonOpt.get();
        SeasonResponse seasonResponse = SeasonResponse.from(season);

        List<SeasonMvpPlayerResponse> players = getSeasonMvpPlayers(season, 10, locale);
        List<SeasonMvpGuildResponse> guilds = getSeasonMvpGuilds(season, 5);

        return SeasonMvpData.of(seasonResponse, players, guilds);
    }

    /**
     * 시즌 MVP 플레이어 조회 (시즌 기간 동안 가장 많은 경험치를 획득한 플레이어)
     */
    private List<SeasonMvpPlayerResponse> getSeasonMvpPlayers(Season season, int limit, String locale) {
        List<Object[]> topGainers = experienceHistoryRepository.findTopExpGainersByPeriod(
            season.getStartAt(), season.getEndAt(), PageRequest.of(0, limit));

        if (topGainers.isEmpty()) {
            return List.of();
        }

        // 1. 모든 사용자 ID 추출
        List<String> userIds = topGainers.stream()
            .map(row -> (String) row[0])
            .collect(Collectors.toList());

        // 2. 배치 조회: 사용자 정보
        Map<String, Users> userMap = userRepository.findAllById(userIds).stream()
            .collect(Collectors.toMap(Users::getId, u -> u));

        // 3. 배치 조회: 레벨 정보
        Map<String, Integer> levelMap = userExperienceRepository.findByUserIdIn(userIds).stream()
            .collect(Collectors.toMap(UserExperience::getUserId, UserExperience::getCurrentLevel));

        // 4. 배치 조회: 장착된 칭호
        Map<String, List<UserTitle>> titleMap = userTitleRepository.findEquippedTitlesByUserIdIn(userIds).stream()
            .collect(Collectors.groupingBy(UserTitle::getUserId));

        // 5. 결과 조합
        List<SeasonMvpPlayerResponse> result = new ArrayList<>();
        int rank = 1;

        for (Object[] row : topGainers) {
            String odayUserId = (String) row[0];
            Long earnedExp = ((Number) row[1]).longValue();

            Users user = userMap.get(odayUserId);
            if (user == null) {
                continue;
            }

            Integer level = levelMap.getOrDefault(odayUserId, 1);
            TitleInfo titleInfo = buildTitleInfoFromList(titleMap.get(odayUserId), locale);

            result.add(SeasonMvpPlayerResponse.of(
                odayUserId,
                user.getNickname(),
                user.getPicture(),
                level,
                titleInfo.name(),
                titleInfo.rarity(),
                earnedExp,
                rank++
            ));
        }

        return result;
    }

    /**
     * 시즌 MVP 길드 조회 (시즌 기간 동안 가장 많은 경험치를 획득한 길드)
     */
    private List<SeasonMvpGuildResponse> getSeasonMvpGuilds(Season season, int limit) {
        List<Object[]> topGuilds = guildExperienceHistoryRepository.findTopExpGuildsByPeriod(
            season.getStartAt(), season.getEndAt(), PageRequest.of(0, limit));

        if (topGuilds.isEmpty()) {
            return List.of();
        }

        // 1. 모든 길드 ID 추출
        List<Long> guildIds = topGuilds.stream()
            .map(row -> ((Number) row[0]).longValue())
            .collect(Collectors.toList());

        // 2. 배치 조회: 길드 정보
        Map<Long, Guild> guildMap = guildRepository.findByIdInAndIsActiveTrue(guildIds).stream()
            .collect(Collectors.toMap(Guild::getId, g -> g));

        // 3. 배치 조회: 멤버 수
        Map<Long, Long> memberCountMap = guildMemberRepository.countActiveMembersByGuildIds(guildIds).stream()
            .collect(Collectors.toMap(
                row -> (Long) row[0],
                row -> (Long) row[1]
            ));

        // 4. 결과 조합
        List<SeasonMvpGuildResponse> result = new ArrayList<>();
        int rank = 1;

        for (Object[] row : topGuilds) {
            Long guildId = ((Number) row[0]).longValue();
            Long earnedExp = ((Number) row[1]).longValue();

            Guild guild = guildMap.get(guildId);
            if (guild == null) {
                continue;
            }

            int memberCount = memberCountMap.getOrDefault(guildId, 0L).intValue();

            result.add(SeasonMvpGuildResponse.of(
                guildId,
                guild.getName(),
                guild.getImageUrl(),
                guild.getCurrentLevel(),
                memberCount,
                earnedExp,
                rank++
            ));
        }

        return result;
    }

    /**
     * 배치 조회된 칭호 리스트에서 TitleInfo 생성 (N+1 방지용)
     */
    private TitleInfo buildTitleInfoFromList(List<UserTitle> equippedTitles, String locale) {
        if (equippedTitles == null || equippedTitles.isEmpty()) {
            return new TitleInfo(null, null);
        }

        UserTitle leftUserTitle = equippedTitles.stream()
            .filter(ut -> ut.getEquippedPosition() == TitlePosition.LEFT)
            .findFirst()
            .orElse(null);

        UserTitle rightUserTitle = equippedTitles.stream()
            .filter(ut -> ut.getEquippedPosition() == TitlePosition.RIGHT)
            .findFirst()
            .orElse(null);

        String leftTitle = leftUserTitle != null ?
            getLocalizedTitleName(leftUserTitle.getTitle(), locale) : null;
        String rightTitle = rightUserTitle != null ?
            getLocalizedTitleName(rightUserTitle.getTitle(), locale) : null;

        TitleRarity highestRarity = getHighestRarity(
            leftUserTitle != null ? leftUserTitle.getTitle().getRarity() : null,
            rightUserTitle != null ? rightUserTitle.getTitle().getRarity() : null
        );

        String combinedTitle;
        if (leftTitle == null && rightTitle == null) {
            combinedTitle = null;
        } else if (leftTitle == null) {
            combinedTitle = rightTitle;
        } else if (rightTitle == null) {
            combinedTitle = leftTitle;
        } else {
            combinedTitle = leftTitle + " " + rightTitle;
        }

        return new TitleInfo(combinedTitle, highestRarity);
    }

    private String getLocalizedTitleName(Title title, String locale) {
        if (title == null) {
            return null;
        }
        return LocaleUtils.getLocalizedText(title.getName(), title.getNameEn(), title.getNameAr(), locale);
    }

    private TitleRarity getHighestRarity(TitleRarity r1, TitleRarity r2) {
        if (r1 == null) return r2;
        if (r2 == null) return r1;
        return r1.ordinal() > r2.ordinal() ? r1 : r2;
    }

    private record TitleInfo(String name, TitleRarity rarity) {}

    // ===== 캐시 관리 메서드들 =====

    /**
     * 모든 시즌 관련 캐시 삭제 (RedisTemplate 직접 사용)
     */
    public void evictAllSeasonCaches() {
        evictCurrentSeasonCache();
        evictSeasonMvpDataCache();
        log.info("모든 시즌 캐시 삭제 완료");
    }

    /**
     * 현재 시즌 캐시만 삭제
     */
    public void evictCurrentSeasonCache() {
        deleteKeysByPattern("currentSeason::*");
        log.info("currentSeason 캐시 삭제 완료");
    }

    /**
     * 시즌 MVP 데이터 캐시만 삭제
     */
    public void evictSeasonMvpDataCache() {
        deleteKeysByPattern("seasonMvpData::*");
        log.info("seasonMvpData 캐시 삭제 완료");
    }

    /**
     * Redis 키 패턴으로 삭제
     */
    private void deleteKeysByPattern(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("Redis 캐시 삭제 - 패턴: {}, 삭제된 키 수: {}", pattern, keys.size());
        } else {
            log.info("Redis 캐시 삭제 - 패턴: {}, 삭제할 키 없음", pattern);
        }
    }

    // ===== 시즌 상세 페이지용 메서드들 =====

    /**
     * 시즌 ID로 시즌 조회
     */
    public Optional<Season> getSeasonById(Long seasonId) {
        return seasonRepository.findById(seasonId)
            .filter(Season::getIsActive);
    }

    /**
     * 시즌 플레이어 랭킹 조회 (1-10위)
     * @param season 시즌 엔티티
     * @param categoryName 카테고리명 (null이면 전체)
     * @param limit 조회 개수
     * @param locale 다국어 지원
     */
    public List<SeasonMvpPlayerResponse> getSeasonPlayerRankings(
        Season season, String categoryName, int limit, String locale) {

        List<Object[]> topGainers;
        if (categoryName == null) {
            topGainers = experienceHistoryRepository.findTopExpGainersByPeriod(
                season.getStartAt(), season.getEndAt(), PageRequest.of(0, limit));
        } else {
            topGainers = experienceHistoryRepository.findTopExpGainersByCategoryAndPeriod(
                categoryName, season.getStartAt(), season.getEndAt(), PageRequest.of(0, limit));
        }

        if (topGainers.isEmpty()) {
            return List.of();
        }

        return buildPlayerResponses(topGainers, locale);
    }

    /**
     * 시즌 길드 랭킹 조회 (1-10위)
     */
    public List<SeasonMvpGuildResponse> getSeasonGuildRankings(Season season, int limit) {
        return getSeasonMvpGuilds(season, limit);
    }

    /**
     * 내 시즌 랭킹 조회
     */
    public SeasonMyRankingResponse getMySeasonRanking(Season season, String userId) {
        // 1. 내 플레이어 경험치 및 순위 조회
        Long myPlayerExp = experienceHistoryRepository.sumExpByUserIdAndPeriod(
            userId, season.getStartAt(), season.getEndAt());

        Integer playerRank = null;
        if (myPlayerExp != null && myPlayerExp > 0) {
            Long usersAboveMe = experienceHistoryRepository.countUsersWithMoreExpByPeriod(
                season.getStartAt(), season.getEndAt(), myPlayerExp);
            playerRank = usersAboveMe.intValue() + 1;
        }

        // 2. 내 길드 정보 조회
        Long guildId = null;
        String guildName = null;
        Integer guildRank = null;
        Long guildSeasonExp = null;

        List<GuildMember> myGuildMembers = guildMemberRepository.findAllActiveGuildMemberships(userId);
        if (!myGuildMembers.isEmpty()) {
            // 첫 번째 활성 길드를 주요 길드로 사용
            Guild guild = myGuildMembers.get(0).getGuild();
            guildId = guild.getId();
            guildName = guild.getName();

            // 길드 경험치 및 순위 조회
            guildSeasonExp = guildExperienceHistoryRepository.sumExpByGuildIdAndPeriod(
                guildId, season.getStartAt(), season.getEndAt());

            if (guildSeasonExp != null && guildSeasonExp > 0) {
                Long guildsAboveMe = guildExperienceHistoryRepository.countGuildsWithMoreExpByPeriod(
                    season.getStartAt(), season.getEndAt(), guildSeasonExp);
                guildRank = guildsAboveMe.intValue() + 1;
            }
        }

        return SeasonMyRankingResponse.of(
            playerRank,
            myPlayerExp != null ? myPlayerExp : 0L,
            guildRank,
            guildSeasonExp,
            guildId,
            guildName
        );
    }

    /**
     * 플레이어 응답 빌드 (내부 헬퍼 메서드)
     */
    private List<SeasonMvpPlayerResponse> buildPlayerResponses(List<Object[]> topGainers, String locale) {
        List<String> userIds = topGainers.stream()
            .map(row -> (String) row[0])
            .collect(Collectors.toList());

        Map<String, Users> userMap = userRepository.findAllById(userIds).stream()
            .collect(Collectors.toMap(Users::getId, u -> u));

        Map<String, Integer> levelMap = userExperienceRepository.findByUserIdIn(userIds).stream()
            .collect(Collectors.toMap(UserExperience::getUserId, UserExperience::getCurrentLevel));

        Map<String, List<UserTitle>> titleMap = userTitleRepository.findEquippedTitlesByUserIdIn(userIds).stream()
            .collect(Collectors.groupingBy(UserTitle::getUserId));

        List<SeasonMvpPlayerResponse> result = new ArrayList<>();
        int rank = 1;

        for (Object[] row : topGainers) {
            String odayUserId = (String) row[0];
            Long earnedExp = ((Number) row[1]).longValue();

            Users user = userMap.get(odayUserId);
            if (user == null) {
                continue;
            }

            Integer level = levelMap.getOrDefault(odayUserId, 1);
            TitleInfo titleInfo = buildTitleInfoFromList(titleMap.get(odayUserId), locale);

            result.add(SeasonMvpPlayerResponse.of(
                odayUserId,
                user.getNickname(),
                user.getPicture(),
                level,
                titleInfo.name(),
                titleInfo.rarity(),
                earnedExp,
                rank++
            ));
        }

        return result;
    }
}
