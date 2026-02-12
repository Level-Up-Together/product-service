package io.pinkspider.leveluptogethermvp.adminservice.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.adminservice.domain.dto.FeaturedFeedPageResponse;
import io.pinkspider.leveluptogethermvp.adminservice.domain.dto.FeaturedFeedRequest;
import io.pinkspider.leveluptogethermvp.adminservice.domain.dto.FeaturedFeedResponse;
import io.pinkspider.leveluptogethermvp.adminservice.domain.entity.FeaturedFeed;
import io.pinkspider.leveluptogethermvp.adminservice.infrastructure.FeaturedFeedRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(transactionManager = "adminTransactionManager")
public class FeaturedFeedAdminService {

    private final FeaturedFeedRepository featuredFeedRepository;

    @Transactional(readOnly = true, transactionManager = "adminTransactionManager")
    public FeaturedFeedPageResponse getFeaturedFeeds(Pageable pageable) {
        Page<FeaturedFeedResponse> page = featuredFeedRepository
            .findAllOrderByCategoryAndDisplayOrder(pageable)
            .map(FeaturedFeedResponse::from);
        return FeaturedFeedPageResponse.from(page);
    }

    @Transactional(readOnly = true, transactionManager = "adminTransactionManager")
    public FeaturedFeedPageResponse getFeaturedFeedsByCategory(Long categoryId, Pageable pageable) {
        Page<FeaturedFeedResponse> page = featuredFeedRepository
            .findByExactCategoryId(categoryId, pageable)
            .map(FeaturedFeedResponse::from);
        return FeaturedFeedPageResponse.from(page);
    }

    @Transactional(readOnly = true, transactionManager = "adminTransactionManager")
    public FeaturedFeedResponse getFeaturedFeed(Long id) {
        FeaturedFeed entity = featuredFeedRepository.findById(id)
            .orElseThrow(() -> new CustomException("404", "추천 피드를 찾을 수 없습니다."));
        return FeaturedFeedResponse.from(entity);
    }

    public FeaturedFeedResponse createFeaturedFeed(FeaturedFeedRequest request) {
        if (featuredFeedRepository.existsByCategoryIdAndFeedId(request.getCategoryId(), request.getFeedId())) {
            throw new CustomException("400", "해당 카테고리에 이미 등록된 피드입니다.");
        }

        FeaturedFeed entity = FeaturedFeed.builder()
            .categoryId(request.getCategoryId())
            .feedId(request.getFeedId())
            .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 1)
            .isActive(request.getIsActive() != null ? request.getIsActive() : true)
            .startAt(request.getStartAt())
            .endAt(request.getEndAt())
            .createdBy(request.getCreatedBy())
            .modifiedBy(request.getModifiedBy())
            .build();

        FeaturedFeed saved = featuredFeedRepository.save(entity);
        log.info("추천 피드 생성: id={}, categoryId={}, feedId={}", saved.getId(), saved.getCategoryId(), saved.getFeedId());
        return FeaturedFeedResponse.from(saved);
    }

    public FeaturedFeedResponse updateFeaturedFeed(Long id, FeaturedFeedRequest request) {
        FeaturedFeed entity = featuredFeedRepository.findById(id)
            .orElseThrow(() -> new CustomException("404", "추천 피드를 찾을 수 없습니다."));

        boolean categoryChanged = (entity.getCategoryId() == null && request.getCategoryId() != null)
            || (entity.getCategoryId() != null && !entity.getCategoryId().equals(request.getCategoryId()));
        boolean feedChanged = !entity.getFeedId().equals(request.getFeedId());

        if (categoryChanged || feedChanged) {
            if (featuredFeedRepository.existsByCategoryIdAndFeedId(request.getCategoryId(), request.getFeedId())) {
                throw new CustomException("400", "해당 카테고리에 이미 등록된 피드입니다.");
            }
        }

        entity.setCategoryId(request.getCategoryId());
        entity.setFeedId(request.getFeedId());
        if (request.getDisplayOrder() != null) {
            entity.setDisplayOrder(request.getDisplayOrder());
        }
        if (request.getIsActive() != null) {
            entity.setIsActive(request.getIsActive());
        }
        entity.setStartAt(request.getStartAt());
        entity.setEndAt(request.getEndAt());
        entity.setModifiedBy(request.getModifiedBy());

        FeaturedFeed saved = featuredFeedRepository.save(entity);
        log.info("추천 피드 수정: id={}", id);
        return FeaturedFeedResponse.from(saved);
    }

    public void deleteFeaturedFeed(Long id) {
        if (!featuredFeedRepository.existsById(id)) {
            throw new CustomException("404", "추천 피드를 찾을 수 없습니다.");
        }
        featuredFeedRepository.deleteById(id);
        log.info("추천 피드 삭제: id={}", id);
    }

    public FeaturedFeedResponse toggleActive(Long id, String adminId) {
        FeaturedFeed entity = featuredFeedRepository.findById(id)
            .orElseThrow(() -> new CustomException("404", "추천 피드를 찾을 수 없습니다."));

        entity.setIsActive(!entity.getIsActive());
        entity.setModifiedBy(adminId);

        FeaturedFeed saved = featuredFeedRepository.save(entity);
        log.info("추천 피드 활성화 토글: id={}, isActive={}", id, saved.getIsActive());
        return FeaturedFeedResponse.from(saved);
    }
}
