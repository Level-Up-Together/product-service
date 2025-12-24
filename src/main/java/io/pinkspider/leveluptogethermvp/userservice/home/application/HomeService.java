package io.pinkspider.leveluptogethermvp.userservice.home.application;

import io.pinkspider.leveluptogethermvp.userservice.achievement.infrastructure.UserTitleRepository;
import io.pinkspider.leveluptogethermvp.userservice.home.api.dto.HomeBannerResponse;
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
import java.util.List;
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
     * 오늘의 플레이어 목록 조회 (어제 가장 경험치를 많이 획득한 사람 5명)
     */
    public List<TodayPlayerResponse> getTodayPlayers() {
        // 어제 00:00 ~ 23:59:59
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDateTime startDate = yesterday.atStartOfDay();
        LocalDateTime endDate = yesterday.atTime(LocalTime.MAX);

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

            // 장착된 칭호 조회
            String equippedTitle = userTitleRepository.findEquippedByUserId(odayUserId)
                .map(ut -> ut.getTitle().getDisplayName())
                .orElse(null);

            result.add(TodayPlayerResponse.of(
                odayUserId,
                user.getNickname(),
                user.getPicture(),
                level,
                equippedTitle,
                earnedExp,
                rank++
            ));
        }

        return result;
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
}
