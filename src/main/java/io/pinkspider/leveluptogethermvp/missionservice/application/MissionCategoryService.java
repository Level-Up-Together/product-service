package io.pinkspider.leveluptogethermvp.missionservice.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionCategoryCreateRequest;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionCategoryResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionCategoryUpdateRequest;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionCategory;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionCategoryRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(transactionManager = "missionTransactionManager")
public class MissionCategoryService {

    private final MissionCategoryRepository missionCategoryRepository;

    /**
     * 카테고리 생성 (Admin용)
     */
    @CacheEvict(value = "activeMissionCategories", allEntries = true)
    public MissionCategoryResponse createCategory(MissionCategoryCreateRequest request) {
        if (missionCategoryRepository.existsByName(request.getName())) {
            throw new CustomException("DUPLICATE_RESOURCE", "이미 존재하는 카테고리 이름입니다.");
        }

        MissionCategory category = MissionCategory.builder()
            .name(request.getName())
            .description(request.getDescription())
            .icon(request.getIcon())
            .displayOrder(request.getDisplayOrder())
            .isActive(true)
            .build();

        MissionCategory saved = missionCategoryRepository.save(category);
        log.info("Mission category created: id={}, name={}", saved.getId(), saved.getName());

        return MissionCategoryResponse.from(saved);
    }

    /**
     * 카테고리 수정 (Admin용)
     */
    @Caching(evict = {
        @CacheEvict(value = "missionCategories", key = "#categoryId"),
        @CacheEvict(value = "activeMissionCategories", allEntries = true)
    })
    public MissionCategoryResponse updateCategory(Long categoryId, MissionCategoryUpdateRequest request) {
        MissionCategory category = missionCategoryRepository.findById(categoryId)
            .orElseThrow(() -> new CustomException("NOT_FOUND", "카테고리를 찾을 수 없습니다."));

        if (request.getName() != null && !request.getName().equals(category.getName())) {
            if (missionCategoryRepository.existsByName(request.getName())) {
                throw new CustomException("DUPLICATE_RESOURCE", "이미 존재하는 카테고리 이름입니다.");
            }
            category.setName(request.getName());
        }

        if (request.getDescription() != null) {
            category.setDescription(request.getDescription());
        }

        if (request.getIcon() != null) {
            category.setIcon(request.getIcon());
        }

        if (request.getDisplayOrder() != null) {
            category.setDisplayOrder(request.getDisplayOrder());
        }

        if (request.getIsActive() != null) {
            category.setIsActive(request.getIsActive());
        }

        MissionCategory saved = missionCategoryRepository.save(category);
        log.info("Mission category updated: id={}, name={}", saved.getId(), saved.getName());

        return MissionCategoryResponse.from(saved);
    }

    /**
     * 카테고리 삭제 (Admin용)
     */
    @Caching(evict = {
        @CacheEvict(value = "missionCategories", key = "#categoryId"),
        @CacheEvict(value = "activeMissionCategories", allEntries = true)
    })
    public void deleteCategory(Long categoryId) {
        MissionCategory category = missionCategoryRepository.findById(categoryId)
            .orElseThrow(() -> new CustomException("NOT_FOUND", "카테고리를 찾을 수 없습니다."));

        missionCategoryRepository.delete(category);
        log.info("Mission category deleted: id={}, name={}", categoryId, category.getName());
    }

    /**
     * 카테고리 비활성화 (Admin용) - 삭제 대신 비활성화
     */
    @Caching(evict = {
        @CacheEvict(value = "missionCategories", key = "#categoryId"),
        @CacheEvict(value = "activeMissionCategories", allEntries = true)
    })
    public MissionCategoryResponse deactivateCategory(Long categoryId) {
        MissionCategory category = missionCategoryRepository.findById(categoryId)
            .orElseThrow(() -> new CustomException("NOT_FOUND", "카테고리를 찾을 수 없습니다."));

        category.deactivate();
        MissionCategory saved = missionCategoryRepository.save(category);
        log.info("Mission category deactivated: id={}, name={}", categoryId, category.getName());

        return MissionCategoryResponse.from(saved);
    }

    /**
     * 모든 카테고리 조회 (Admin용 - 비활성화 포함)
     */
    @Transactional(readOnly = true, transactionManager = "missionTransactionManager")
    public List<MissionCategoryResponse> getAllCategories() {
        return missionCategoryRepository.findAllOrderByDisplayOrder().stream()
            .map(MissionCategoryResponse::from)
            .collect(Collectors.toList());
    }

    /**
     * 활성화된 카테고리만 조회 (사용자용)
     * 1시간 TTL로 캐싱됨 (홈 화면 로딩 속도 최적화)
     */
    @Cacheable(value = "activeMissionCategories", key = "'all'")
    @Transactional(readOnly = true, transactionManager = "missionTransactionManager")
    public List<MissionCategoryResponse> getActiveCategories() {
        return missionCategoryRepository.findAllActiveCategories().stream()
            .map(MissionCategoryResponse::from)
            .collect(Collectors.toList());
    }

    /**
     * 카테고리 단건 조회
     */
    @Cacheable(value = "missionCategories", key = "#categoryId")
    @Transactional(readOnly = true, transactionManager = "missionTransactionManager")
    public MissionCategoryResponse getCategory(Long categoryId) {
        MissionCategory category = missionCategoryRepository.findById(categoryId)
            .orElseThrow(() -> new CustomException("NOT_FOUND", "카테고리를 찾을 수 없습니다."));

        return MissionCategoryResponse.from(category);
    }

    /**
     * 카테고리 이름으로 조회 (내부용)
     */
    @Transactional(readOnly = true, transactionManager = "missionTransactionManager")
    public MissionCategory findByName(String name) {
        return missionCategoryRepository.findByName(name).orElse(null);
    }

    /**
     * 카테고리 ID로 엔티티 조회 (내부용)
     */
    @Transactional(readOnly = true, transactionManager = "missionTransactionManager")
    public MissionCategory findById(Long categoryId) {
        return missionCategoryRepository.findById(categoryId).orElse(null);
    }
}
