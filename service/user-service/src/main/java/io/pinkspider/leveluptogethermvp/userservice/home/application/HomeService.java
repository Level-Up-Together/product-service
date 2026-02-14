package io.pinkspider.leveluptogethermvp.userservice.home.application;

import io.pinkspider.global.enums.BannerType;
import io.pinkspider.global.feign.admin.AdminBannerDto;
import io.pinkspider.global.feign.admin.AdminInternalFeignClient;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildQueryFacadeService;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildFacadeDto.GuildWithMemberCount;
import io.pinkspider.leveluptogethermvp.metaservice.application.MissionCategoryService;
import io.pinkspider.leveluptogethermvp.metaservice.domain.dto.MissionCategoryResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.application.GamificationQueryFacadeService;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserTitle;
import io.pinkspider.global.enums.TitlePosition;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.userservice.home.api.dto.HomeBannerResponse;
import io.pinkspider.leveluptogethermvp.userservice.home.api.dto.MvpGuildResponse;
import io.pinkspider.leveluptogethermvp.userservice.home.api.dto.TodayPlayerResponse;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import io.pinkspider.global.translation.LocaleUtils;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class HomeService {

    private final AdminInternalFeignClient adminInternalFeignClient;
    private final GamificationQueryFacadeService gamificationQueryFacadeService;
    private final UserRepository userRepository;
    private final MissionCategoryService missionCategoryService;
    private final GuildQueryFacadeService guildQueryFacadeService;

    /**
     * 활성화된 배너 목록 조회
     */
    public List<HomeBannerResponse> getActiveBanners() {
        List<AdminBannerDto> banners = adminInternalFeignClient.getActiveBanners();
        return banners.stream()
            .map(HomeBannerResponse::from)
            .collect(Collectors.toList());
    }

    /**
     * 특정 유형의 활성화된 배너 목록 조회
     */
    public List<HomeBannerResponse> getActiveBannersByType(BannerType bannerType) {
        List<AdminBannerDto> banners = adminInternalFeignClient.getBannersByType(bannerType.name());
        return banners.stream()
            .map(HomeBannerResponse::from)
            .collect(Collectors.toList());
    }

    /**
     * MVP 목록 조회 (금일 00:00 ~ 24:00 기준 가장 경험치를 많이 획득한 사람 5명)
     */
    public List<TodayPlayerResponse> getTodayPlayers() {
        return getTodayPlayers(null);
    }

    /**
     * MVP 목록 조회 (금일 00:00 ~ 24:00 기준 가장 경험치를 많이 획득한 사람 5명) - 다국어 지원
     * N+1 문제 해결을 위해 배치 조회 사용
     * Redis 캐싱 적용 (5분 TTL)
     *
     * @param locale Accept-Language 헤더에서 추출한 locale (null이면 기본 한국어)
     */
    @Cacheable(value = "todayPlayers", key = "#locale ?: 'ko'", cacheManager = "redisCacheManager")
    public List<TodayPlayerResponse> getTodayPlayers(String locale) {
        // 오늘 00:00 ~ 23:59:59
        LocalDate today = LocalDate.now();
        LocalDateTime startDate = today.atStartOfDay();
        LocalDateTime endDate = today.atTime(LocalTime.MAX);

        // 상위 5명 조회
        List<Object[]> topGainers = gamificationQueryFacadeService.findTopExpGainersByPeriod(
            startDate, endDate, PageRequest.of(0, 5));

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
        Map<String, Integer> levelMap = gamificationQueryFacadeService.getUserLevelMap(userIds);

        // 4. 배치 조회: 장착된 칭호
        Map<String, List<UserTitle>> titleMap = gamificationQueryFacadeService.getEquippedTitleEntitiesByUserIds(userIds);

        // 5. 결과 조합
        List<TodayPlayerResponse> result = new ArrayList<>();
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

            result.add(TodayPlayerResponse.of(
                odayUserId,
                user.getNickname(),
                user.getPicture(),
                level,
                titleInfo.name(),
                titleInfo.rarity(),
                titleInfo.colorCode(),
                titleInfo.leftTitle(),
                titleInfo.leftRarity(),
                titleInfo.leftColorCode(),
                titleInfo.rightTitle(),
                titleInfo.rightRarity(),
                titleInfo.rightColorCode(),
                earnedExp,
                rank++
            ));
        }

        return result;
    }

    /**
     * 카테고리별 MVP 목록 조회 (하이브리드 선정)
     * 1. Admin이 설정한 Featured Player 먼저 표시
     * 2. 자동 선정 (금일 해당 카테고리에서 가장 경험치를 많이 획득한 사람)
     * 3. 중복 제거 후 최대 5명 반환
     */
    public List<TodayPlayerResponse> getTodayPlayersByCategory(Long categoryId) {
        return getTodayPlayersByCategory(categoryId, null);
    }

    /**
     * 카테고리별 MVP 목록 조회 (하이브리드 선정) - 다국어 지원
     * Redis 캐싱 적용 (5분 TTL)
     *
     * @param categoryId 카테고리 ID
     * @param locale Accept-Language 헤더에서 추출한 locale (null이면 기본 한국어)
     */
    @Cacheable(value = "todayPlayersByCategory", key = "#categoryId + '_' + (#locale ?: 'ko')", cacheManager = "redisCacheManager")
    public List<TodayPlayerResponse> getTodayPlayersByCategory(Long categoryId, String locale) {
        LocalDate today = LocalDate.now();
        LocalDateTime startDate = today.atStartOfDay();
        LocalDateTime endDate = today.atTime(LocalTime.MAX);

        List<TodayPlayerResponse> result = new ArrayList<>();
        Set<String> addedUserIds = new HashSet<>();
        int rank = 1;
        int maxPlayers = 5;

        // 1. Admin Featured Players 먼저 조회
        List<String> featuredUserIds = adminInternalFeignClient.getFeaturedPlayerUserIds(categoryId);
        for (String userId : featuredUserIds) {
            if (result.size() >= maxPlayers) break;

            if (addedUserIds.contains(userId)) continue;

            TodayPlayerResponse player = buildTodayPlayerResponse(userId, 0L, rank, locale);
            if (player != null) {
                result.add(player);
                addedUserIds.add(userId);
                rank++;
            }
        }

        // 2. 자동 선정: 해당 카테고리에서 어제 가장 경험치 많이 획득한 사람
        if (result.size() < maxPlayers) {
            // 카테고리명 조회
            String categoryName;
            try {
                MissionCategoryResponse categoryResponse = missionCategoryService.getCategory(categoryId);
                categoryName = categoryResponse.getName();
            } catch (Exception e) {
                categoryName = null;
            }

            if (categoryName != null) {
                int remaining = maxPlayers - result.size();
                List<Object[]> topGainers = gamificationQueryFacadeService.findTopExpGainersByCategoryAndPeriod(
                    categoryName, startDate, endDate, PageRequest.of(0, remaining + addedUserIds.size()));

                for (Object[] row : topGainers) {
                    if (result.size() >= maxPlayers) break;

                    String odayUserId = (String) row[0];
                    Long earnedExp = ((Number) row[1]).longValue();

                    if (addedUserIds.contains(odayUserId)) continue;

                    TodayPlayerResponse player = buildTodayPlayerResponse(odayUserId, earnedExp, rank, locale);
                    if (player != null) {
                        result.add(player);
                        addedUserIds.add(odayUserId);
                        rank++;
                    }
                }
            }
        }

        return result;
    }

    /**
     * MVP 길드 목록 조회 (금일 00:00 ~ 24:00 기준 가장 경험치를 많이 획득한 길드 5개)
     * N+1 문제 해결을 위해 배치 조회 사용
     * Redis 캐싱 적용 (5분 TTL)
     */
    @Cacheable(value = "mvpGuilds", cacheManager = "redisCacheManager")
    public List<MvpGuildResponse> getMvpGuilds() {
        // 오늘 00:00 ~ 23:59:59
        LocalDate today = LocalDate.now();
        LocalDateTime startDate = today.atStartOfDay();
        LocalDateTime endDate = today.atTime(LocalTime.MAX);

        // 상위 5개 길드 조회
        List<Object[]> topGuilds = guildQueryFacadeService.getTopExpGuildsByPeriod(
            startDate, endDate, PageRequest.of(0, 5));

        if (topGuilds.isEmpty()) {
            return List.of();
        }

        // 1. 모든 길드 ID 추출
        List<Long> guildIds = topGuilds.stream()
            .map(row -> ((Number) row[0]).longValue())
            .collect(Collectors.toList());

        // 2. 배치 조회: 길드 정보 + 멤버 수
        Map<Long, GuildWithMemberCount> guildMap = guildQueryFacadeService.getGuildsWithMemberCounts(guildIds).stream()
            .collect(Collectors.toMap(GuildWithMemberCount::id, g -> g));

        // 3. 결과 조합
        List<MvpGuildResponse> result = new ArrayList<>();
        int rank = 1;

        for (Object[] row : topGuilds) {
            Long guildId = ((Number) row[0]).longValue();
            Long earnedExp = ((Number) row[1]).longValue();

            GuildWithMemberCount guild = guildMap.get(guildId);
            if (guild == null) {
                continue;
            }

            result.add(MvpGuildResponse.of(
                guildId,
                guild.name(),
                guild.imageUrl(),
                guild.currentLevel(),
                guild.memberCount(),
                earnedExp,
                rank++
            ));
        }

        return result;
    }

    /**
     * TodayPlayerResponse 생성 헬퍼 메서드
     */
    private TodayPlayerResponse buildTodayPlayerResponse(String userId, Long earnedExp, int rank) {
        return buildTodayPlayerResponse(userId, earnedExp, rank, null);
    }

    /**
     * TodayPlayerResponse 생성 헬퍼 메서드 - 다국어 지원
     */
    private TodayPlayerResponse buildTodayPlayerResponse(String userId, Long earnedExp, int rank, String locale) {
        Users user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return null;
        }

        Integer level = gamificationQueryFacadeService.getUserLevel(userId);

        TitleInfo titleInfo = getCombinedEquippedTitleInfo(userId, locale);

        return TodayPlayerResponse.of(
            userId,
            user.getNickname(),
            user.getPicture(),
            level,
            titleInfo.name(),
            titleInfo.rarity(),
            titleInfo.colorCode(),
            titleInfo.leftTitle(),
            titleInfo.leftRarity(),
            titleInfo.leftColorCode(),
            titleInfo.rightTitle(),
            titleInfo.rightRarity(),
            titleInfo.rightColorCode(),
            earnedExp,
            rank
        );
    }

    /**
     * 사용자의 장착된 칭호 조합 정보 조회 (LEFT + RIGHT)
     * 예: "용감한 전사", "전설적인 챔피언"
     */
    private TitleInfo getCombinedEquippedTitleInfo(String userId) {
        return getCombinedEquippedTitleInfo(userId, null);
    }

    /**
     * 사용자의 장착된 칭호 조합 정보 조회 (LEFT + RIGHT) - 다국어 지원
     * 예: "용감한 전사", "Brave Warrior", "المحارب الشجاع"
     *
     * @param userId 사용자 ID
     * @param locale Accept-Language 헤더에서 추출한 locale (null이면 기본 한국어)
     */
    private TitleInfo getCombinedEquippedTitleInfo(String userId, String locale) {
        List<UserTitle> equippedTitles = gamificationQueryFacadeService.getEquippedTitleEntitiesByUserId(userId);
        if (equippedTitles.isEmpty()) {
            return new TitleInfo(null, null, null, null, null, null, null, null, null);
        }

        UserTitle leftUserTitle = equippedTitles.stream()
            .filter(ut -> ut.getEquippedPosition() == TitlePosition.LEFT)
            .findFirst()
            .orElse(null);

        UserTitle rightUserTitle = equippedTitles.stream()
            .filter(ut -> ut.getEquippedPosition() == TitlePosition.RIGHT)
            .findFirst()
            .orElse(null);

        // 로컬라이즈된 칭호 이름 가져오기
        String leftTitle = leftUserTitle != null ?
            getLocalizedTitleName(leftUserTitle.getTitle(), locale) : null;
        String rightTitle = rightUserTitle != null ?
            getLocalizedTitleName(rightUserTitle.getTitle(), locale) : null;

        // 개별 등급 및 색상 코드 추출
        TitleRarity leftRarity = leftUserTitle != null ? leftUserTitle.getTitle().getRarity() : null;
        TitleRarity rightRarity = rightUserTitle != null ? rightUserTitle.getTitle().getRarity() : null;
        String leftColorCode = leftUserTitle != null ? leftUserTitle.getTitle().getColorCode() : null;
        String rightColorCode = rightUserTitle != null ? rightUserTitle.getTitle().getColorCode() : null;

        // 가장 높은 등급 선택 (둘 중 하나만 있으면 그것 사용) - 기존 호환성 유지
        TitleRarity highestRarity = getHighestRarity(leftRarity, rightRarity);

        // 가장 높은 등급의 색상 코드 선택
        String highestColorCode = null;
        if (highestRarity != null) {
            if (leftRarity == highestRarity && leftUserTitle != null) {
                highestColorCode = leftColorCode;
            } else if (rightUserTitle != null) {
                highestColorCode = rightColorCode;
            }
        }

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

        return new TitleInfo(combinedTitle, highestRarity, highestColorCode, leftTitle, leftRarity, leftColorCode, rightTitle, rightRarity, rightColorCode);
    }

    /**
     * 배치 조회된 칭호 리스트에서 TitleInfo 생성 (N+1 방지용)
     *
     * @param equippedTitles 사용자의 장착된 칭호 리스트 (null 가능)
     * @param locale Accept-Language 헤더에서 추출한 locale (null이면 기본 한국어)
     */
    private TitleInfo buildTitleInfoFromList(List<UserTitle> equippedTitles, String locale) {
        if (equippedTitles == null || equippedTitles.isEmpty()) {
            return new TitleInfo(null, null, null, null, null, null, null, null, null);
        }

        UserTitle leftUserTitle = equippedTitles.stream()
            .filter(ut -> ut.getEquippedPosition() == TitlePosition.LEFT)
            .findFirst()
            .orElse(null);

        UserTitle rightUserTitle = equippedTitles.stream()
            .filter(ut -> ut.getEquippedPosition() == TitlePosition.RIGHT)
            .findFirst()
            .orElse(null);

        // 로컬라이즈된 칭호 이름 가져오기
        String leftTitle = leftUserTitle != null ?
            getLocalizedTitleName(leftUserTitle.getTitle(), locale) : null;
        String rightTitle = rightUserTitle != null ?
            getLocalizedTitleName(rightUserTitle.getTitle(), locale) : null;

        // 개별 등급 및 색상 코드 추출
        TitleRarity leftRarity = leftUserTitle != null ? leftUserTitle.getTitle().getRarity() : null;
        TitleRarity rightRarity = rightUserTitle != null ? rightUserTitle.getTitle().getRarity() : null;
        String leftColorCode = leftUserTitle != null ? leftUserTitle.getTitle().getColorCode() : null;
        String rightColorCode = rightUserTitle != null ? rightUserTitle.getTitle().getColorCode() : null;

        // 가장 높은 등급 선택 (둘 중 하나만 있으면 그것 사용) - 기존 호환성 유지
        TitleRarity highestRarity = getHighestRarity(leftRarity, rightRarity);

        // 가장 높은 등급의 색상 코드 선택
        String highestColorCode = null;
        if (highestRarity != null) {
            if (leftRarity == highestRarity && leftUserTitle != null) {
                highestColorCode = leftColorCode;
            } else if (rightUserTitle != null) {
                highestColorCode = rightColorCode;
            }
        }

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

        return new TitleInfo(combinedTitle, highestRarity, highestColorCode, leftTitle, leftRarity, leftColorCode, rightTitle, rightRarity, rightColorCode);
    }

    /**
     * 칭호의 로컬라이즈된 이름 반환
     */
    private String getLocalizedTitleName(io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Title title, String locale) {
        if (title == null) {
            return null;
        }
        return LocaleUtils.getLocalizedText(title.getName(), title.getNameEn(), title.getNameAr(), locale);
    }

    /**
     * 두 등급 중 더 높은 등급 반환
     */
    private TitleRarity getHighestRarity(TitleRarity r1, TitleRarity r2) {
        if (r1 == null) return r2;
        if (r2 == null) return r1;
        return r1.ordinal() > r2.ordinal() ? r1 : r2;
    }

    /**
     * 칭호 정보 (이름, 등급, 색상 코드) - LEFT/RIGHT 개별 정보 포함
     */
    private record TitleInfo(
        String name,
        TitleRarity rarity,
        String colorCode,
        String leftTitle,
        TitleRarity leftRarity,
        String leftColorCode,
        String rightTitle,
        TitleRarity rightRarity,
        String rightColorCode
    ) {}
}
