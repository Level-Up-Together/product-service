package io.pinkspider.leveluptogethermvp.userservice.home.application;

import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildExperienceHistoryRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import io.pinkspider.leveluptogethermvp.metaservice.domain.entity.FeaturedPlayer;
import io.pinkspider.leveluptogethermvp.metaservice.infrastructure.FeaturedPlayerRepository;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionCategory;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionCategoryRepository;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.entity.UserTitle;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.enums.TitlePosition;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.userservice.achievement.infrastructure.UserTitleRepository;
import io.pinkspider.leveluptogethermvp.userservice.home.api.dto.HomeBannerResponse;
import io.pinkspider.leveluptogethermvp.userservice.home.api.dto.MvpGuildResponse;
import io.pinkspider.leveluptogethermvp.userservice.home.api.dto.TodayPlayerResponse;
import io.pinkspider.leveluptogethermvp.userservice.home.domain.entity.HomeBanner;
import io.pinkspider.leveluptogethermvp.userservice.home.domain.enums.BannerType;
import io.pinkspider.leveluptogethermvp.userservice.home.infrastructure.HomeBannerRepository;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.UserExperience;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.ExperienceHistoryRepository;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserExperienceRepository;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeService {

    private final HomeBannerRepository homeBannerRepository;
    private final ExperienceHistoryRepository experienceHistoryRepository;
    private final UserRepository userRepository;
    private final UserExperienceRepository userExperienceRepository;
    private final UserTitleRepository userTitleRepository;
    private final FeaturedPlayerRepository featuredPlayerRepository;
    private final MissionCategoryRepository missionCategoryRepository;
    private final GuildExperienceHistoryRepository guildExperienceHistoryRepository;
    private final GuildRepository guildRepository;
    private final GuildMemberRepository guildMemberRepository;

    /**
     * 활성화된 배너 목록 조회
     */
    public List<HomeBannerResponse> getActiveBanners() {
        LocalDateTime now = LocalDateTime.now();
        List<HomeBanner> banners = homeBannerRepository.findActiveBanners(now);
        return banners.stream()
            .map(HomeBannerResponse::from)
            .collect(Collectors.toList());
    }

    /**
     * 특정 유형의 활성화된 배너 목록 조회
     */
    public List<HomeBannerResponse> getActiveBannersByType(BannerType bannerType) {
        LocalDateTime now = LocalDateTime.now();
        List<HomeBanner> banners = homeBannerRepository.findActiveBannersByType(bannerType, now);
        return banners.stream()
            .map(HomeBannerResponse::from)
            .collect(Collectors.toList());
    }

    /**
     * MVP 목록 조회 (금일 00:00 ~ 24:00 기준 가장 경험치를 많이 획득한 사람 5명)
     */
    public List<TodayPlayerResponse> getTodayPlayers() {
        // 오늘 00:00 ~ 23:59:59
        LocalDate today = LocalDate.now();
        LocalDateTime startDate = today.atStartOfDay();
        LocalDateTime endDate = today.atTime(LocalTime.MAX);

        // 상위 5명 조회
        List<Object[]> topGainers = experienceHistoryRepository.findTopExpGainersByPeriod(
            startDate, endDate, PageRequest.of(0, 5));

        if (topGainers.isEmpty()) {
            return List.of();
        }

        List<TodayPlayerResponse> result = new ArrayList<>();
        int rank = 1;

        for (Object[] row : topGainers) {
            String odayUserId = (String) row[0];
            Long earnedExp = ((Number) row[1]).longValue();

            // 사용자 정보 조회
            Users user = userRepository.findById(odayUserId).orElse(null);
            if (user == null) {
                continue;
            }

            // 레벨 정보 조회
            Integer level = userExperienceRepository.findByUserId(odayUserId)
                .map(UserExperience::getCurrentLevel)
                .orElse(1);

            // 장착된 칭호 조회 (LEFT + RIGHT 조합)
            TitleInfo titleInfo = getCombinedEquippedTitleInfo(odayUserId);

            result.add(TodayPlayerResponse.of(
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
     * 카테고리별 MVP 목록 조회 (하이브리드 선정)
     * 1. Admin이 설정한 Featured Player 먼저 표시
     * 2. 자동 선정 (금일 해당 카테고리에서 가장 경험치를 많이 획득한 사람)
     * 3. 중복 제거 후 최대 5명 반환
     */
    public List<TodayPlayerResponse> getTodayPlayersByCategory(Long categoryId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();
        LocalDateTime startDate = today.atStartOfDay();
        LocalDateTime endDate = today.atTime(LocalTime.MAX);

        List<TodayPlayerResponse> result = new ArrayList<>();
        Set<String> addedUserIds = new HashSet<>();
        int rank = 1;
        int maxPlayers = 5;

        // 1. Admin Featured Players 먼저 조회
        List<FeaturedPlayer> featuredPlayers = featuredPlayerRepository.findActiveFeaturedPlayers(categoryId, now);
        for (FeaturedPlayer fp : featuredPlayers) {
            if (result.size() >= maxPlayers) break;

            String userId = fp.getUserId();
            if (addedUserIds.contains(userId)) continue;

            TodayPlayerResponse player = buildTodayPlayerResponse(userId, 0L, rank);
            if (player != null) {
                result.add(player);
                addedUserIds.add(userId);
                rank++;
            }
        }

        // 2. 자동 선정: 해당 카테고리에서 어제 가장 경험치 많이 획득한 사람
        if (result.size() < maxPlayers) {
            // 카테고리명 조회
            String categoryName = missionCategoryRepository.findById(categoryId)
                .map(MissionCategory::getName)
                .orElse(null);

            if (categoryName != null) {
                int remaining = maxPlayers - result.size();
                List<Object[]> topGainers = experienceHistoryRepository.findTopExpGainersByCategoryAndPeriod(
                    categoryName, startDate, endDate, PageRequest.of(0, remaining + addedUserIds.size()));

                for (Object[] row : topGainers) {
                    if (result.size() >= maxPlayers) break;

                    String odayUserId = (String) row[0];
                    Long earnedExp = ((Number) row[1]).longValue();

                    if (addedUserIds.contains(odayUserId)) continue;

                    TodayPlayerResponse player = buildTodayPlayerResponse(odayUserId, earnedExp, rank);
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
     */
    public List<MvpGuildResponse> getMvpGuilds() {
        // 오늘 00:00 ~ 23:59:59
        LocalDate today = LocalDate.now();
        LocalDateTime startDate = today.atStartOfDay();
        LocalDateTime endDate = today.atTime(LocalTime.MAX);

        // 상위 5개 길드 조회
        List<Object[]> topGuilds = guildExperienceHistoryRepository.findTopExpGuildsByPeriod(
            startDate, endDate, PageRequest.of(0, 5));

        if (topGuilds.isEmpty()) {
            return List.of();
        }

        List<MvpGuildResponse> result = new ArrayList<>();
        int rank = 1;

        for (Object[] row : topGuilds) {
            Long guildId = ((Number) row[0]).longValue();
            Long earnedExp = ((Number) row[1]).longValue();

            // 길드 정보 조회
            Guild guild = guildRepository.findByIdAndIsActiveTrue(guildId).orElse(null);
            if (guild == null) {
                continue;
            }

            // 멤버 수 조회
            int memberCount = (int) guildMemberRepository.countActiveMembers(guildId);

            result.add(MvpGuildResponse.of(
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
     * TodayPlayerResponse 생성 헬퍼 메서드
     */
    private TodayPlayerResponse buildTodayPlayerResponse(String userId, Long earnedExp, int rank) {
        Users user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return null;
        }

        Integer level = userExperienceRepository.findByUserId(userId)
            .map(UserExperience::getCurrentLevel)
            .orElse(1);

        TitleInfo titleInfo = getCombinedEquippedTitleInfo(userId);

        return TodayPlayerResponse.of(
            userId,
            user.getNickname(),
            user.getPicture(),
            level,
            titleInfo.name(),
            titleInfo.rarity(),
            earnedExp,
            rank
        );
    }

    /**
     * 배너 생성 (관리자용)
     */
    @Transactional
    public HomeBannerResponse createBanner(HomeBanner banner) {
        HomeBanner saved = homeBannerRepository.save(banner);
        log.info("Banner created: id={}, type={}, title={}", saved.getId(), saved.getBannerType(), saved.getTitle());
        return HomeBannerResponse.from(saved);
    }

    /**
     * 배너 수정 (관리자용)
     */
    @Transactional
    public HomeBannerResponse updateBanner(Long bannerId, HomeBanner updateData) {
        HomeBanner banner = homeBannerRepository.findById(bannerId)
            .orElseThrow(() -> new IllegalArgumentException("배너를 찾을 수 없습니다: " + bannerId));

        if (updateData.getTitle() != null) {
            banner.setTitle(updateData.getTitle());
        }
        if (updateData.getDescription() != null) {
            banner.setDescription(updateData.getDescription());
        }
        if (updateData.getImageUrl() != null) {
            banner.setImageUrl(updateData.getImageUrl());
        }
        if (updateData.getLinkType() != null) {
            banner.setLinkType(updateData.getLinkType());
        }
        if (updateData.getLinkUrl() != null) {
            banner.setLinkUrl(updateData.getLinkUrl());
        }
        if (updateData.getSortOrder() != null) {
            banner.setSortOrder(updateData.getSortOrder());
        }
        if (updateData.getIsActive() != null) {
            banner.setIsActive(updateData.getIsActive());
        }
        if (updateData.getStartAt() != null) {
            banner.setStartAt(updateData.getStartAt());
        }
        if (updateData.getEndAt() != null) {
            banner.setEndAt(updateData.getEndAt());
        }

        HomeBanner saved = homeBannerRepository.save(banner);
        log.info("Banner updated: id={}", saved.getId());
        return HomeBannerResponse.from(saved);
    }

    /**
     * 배너 삭제 (관리자용)
     */
    @Transactional
    public void deleteBanner(Long bannerId) {
        homeBannerRepository.deleteById(bannerId);
        log.info("Banner deleted: id={}", bannerId);
    }

    /**
     * 배너 비활성화 (관리자용)
     */
    @Transactional
    public HomeBannerResponse deactivateBanner(Long bannerId) {
        HomeBanner banner = homeBannerRepository.findById(bannerId)
            .orElseThrow(() -> new IllegalArgumentException("배너를 찾을 수 없습니다: " + bannerId));

        banner.setIsActive(false);
        HomeBanner saved = homeBannerRepository.save(banner);
        log.info("Banner deactivated: id={}", saved.getId());
        return HomeBannerResponse.from(saved);
    }

    /**
     * 사용자의 장착된 칭호 조합 정보 조회 (LEFT + RIGHT)
     * 예: "용감한 전사", "전설적인 챔피언"
     */
    private TitleInfo getCombinedEquippedTitleInfo(String userId) {
        List<UserTitle> equippedTitles = userTitleRepository.findEquippedTitlesByUserId(userId);
        if (equippedTitles.isEmpty()) {
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

        String leftTitle = leftUserTitle != null ? leftUserTitle.getTitle().getDisplayName() : null;
        String rightTitle = rightUserTitle != null ? rightUserTitle.getTitle().getDisplayName() : null;

        // 가장 높은 등급 선택 (둘 중 하나만 있으면 그것 사용)
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

    /**
     * 두 등급 중 더 높은 등급 반환
     */
    private TitleRarity getHighestRarity(TitleRarity r1, TitleRarity r2) {
        if (r1 == null) return r2;
        if (r2 == null) return r1;
        return r1.ordinal() > r2.ordinal() ? r1 : r2;
    }

    /**
     * 칭호 정보 (이름과 등급)
     */
    private record TitleInfo(String name, TitleRarity rarity) {}
}
