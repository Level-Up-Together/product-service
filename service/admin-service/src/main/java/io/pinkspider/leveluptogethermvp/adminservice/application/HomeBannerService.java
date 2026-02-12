package io.pinkspider.leveluptogethermvp.adminservice.application;

import io.pinkspider.leveluptogethermvp.adminservice.domain.entity.HomeBanner;
import io.pinkspider.global.enums.BannerType;
import io.pinkspider.leveluptogethermvp.adminservice.infrastructure.HomeBannerRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 홈 배너 서비스
 * adminservice 외부에서 HomeBannerRepository에 직접 접근하지 않고 이 서비스를 통해 조회/수정한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "adminTransactionManager", readOnly = true)
public class HomeBannerService {

    private final HomeBannerRepository homeBannerRepository;

    public List<HomeBanner> getActiveBanners(LocalDateTime now) {
        return homeBannerRepository.findActiveBanners(now);
    }

    public List<HomeBanner> getActiveBannersByType(BannerType bannerType, LocalDateTime now) {
        return homeBannerRepository.findActiveBannersByType(bannerType, now);
    }

    public Optional<HomeBanner> findById(Long bannerId) {
        return homeBannerRepository.findById(bannerId);
    }

    @Transactional(transactionManager = "adminTransactionManager")
    public HomeBanner saveBanner(HomeBanner banner) {
        return homeBannerRepository.save(banner);
    }

    @Transactional(transactionManager = "adminTransactionManager")
    public void deleteById(Long bannerId) {
        homeBannerRepository.deleteById(bannerId);
    }
}
