package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.AchievementCategoryAdminRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.AchievementCategoryAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.AchievementCategory;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.AchievementCategoryRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.AchievementRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(transactionManager = "gamificationTransactionManager")
public class AchievementCategoryAdminService {

    private final AchievementCategoryRepository achievementCategoryRepository;
    private final AchievementRepository achievementRepository;

    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public List<AchievementCategoryAdminResponse> getAllCategories() {
        return achievementCategoryRepository.findAllByOrderBySortOrderAsc().stream()
            .map(AchievementCategoryAdminResponse::from)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public List<AchievementCategoryAdminResponse> getActiveCategories() {
        return achievementCategoryRepository.findByIsActiveTrueOrderBySortOrderAsc().stream()
            .map(AchievementCategoryAdminResponse::from)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public AchievementCategoryAdminResponse getCategory(Long id) {
        AchievementCategory category = achievementCategoryRepository.findById(id)
            .orElseThrow(() -> new CustomException("404", "error.achievement.category.not_found"));
        return AchievementCategoryAdminResponse.from(category);
    }

    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public AchievementCategoryAdminResponse getCategoryByCode(String code) {
        AchievementCategory category = achievementCategoryRepository.findByCode(code)
            .orElseThrow(() -> new CustomException("404", "error.achievement.category.not_found"));
        return AchievementCategoryAdminResponse.from(category);
    }

    @Caching(evict = {
        @CacheEvict(value = "achievementCategories", allEntries = true)
    })
    public AchievementCategoryAdminResponse createCategory(AchievementCategoryAdminRequest request) {
        if (achievementCategoryRepository.existsByCode(request.getCode())) {
            throw new CustomException("400", "error.achievement.category.duplicate_code");
        }

        AchievementCategory category = AchievementCategory.builder()
            .code(request.getCode())
            .name(request.getName())
            .description(request.getDescription())
            .sortOrder(request.getSortOrder())
            .isActive(request.getIsActive() != null ? request.getIsActive() : true)
            .build();

        AchievementCategory saved = achievementCategoryRepository.save(category);
        log.info("업적 카테고리 생성 및 캐시 갱신: id={}, code={}", saved.getId(), saved.getCode());
        return AchievementCategoryAdminResponse.from(saved);
    }

    @Caching(evict = {
        @CacheEvict(value = "achievementCategories", allEntries = true),
        @CacheEvict(value = "achievements", allEntries = true)
    })
    public AchievementCategoryAdminResponse updateCategory(Long id, AchievementCategoryAdminRequest request) {
        AchievementCategory category = achievementCategoryRepository.findById(id)
            .orElseThrow(() -> new CustomException("404", "error.achievement.category.not_found"));

        if (!category.getCode().equals(request.getCode())
            && achievementCategoryRepository.existsByCodeAndIdNot(request.getCode(), id)) {
            throw new CustomException("400", "error.achievement.category.duplicate_code");
        }

        String oldCode = category.getCode();

        category.setCode(request.getCode());
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        category.setSortOrder(request.getSortOrder());
        category.setIsActive(request.getIsActive());

        AchievementCategory saved = achievementCategoryRepository.save(category);
        log.info("업적 카테고리 수정 및 캐시 갱신: id={}, code={}", id, saved.getCode());

        // 코드가 변경된 경우, 관련 업적들의 categoryCode 업데이트
        if (!oldCode.equals(request.getCode())) {
            updateAchievementsCategoryCode(oldCode, request.getCode());
        }

        return AchievementCategoryAdminResponse.from(saved);
    }

    @Caching(evict = {
        @CacheEvict(value = "achievementCategories", allEntries = true)
    })
    public AchievementCategoryAdminResponse toggleActiveStatus(Long id) {
        AchievementCategory category = achievementCategoryRepository.findById(id)
            .orElseThrow(() -> new CustomException("404", "error.achievement.category.not_found"));

        category.setIsActive(!category.getIsActive());
        AchievementCategory saved = achievementCategoryRepository.save(category);
        log.info("업적 카테고리 활성 상태 변경 및 캐시 갱신: id={}, isActive={}", id, saved.getIsActive());
        return AchievementCategoryAdminResponse.from(saved);
    }

    @Caching(evict = {
        @CacheEvict(value = "achievementCategories", allEntries = true)
    })
    public void deleteCategory(Long id) {
        AchievementCategory category = achievementCategoryRepository.findById(id)
            .orElseThrow(() -> new CustomException("404", "error.achievement.category.not_found"));

        if (!achievementRepository.findByCategoryCode(category.getCode()).isEmpty()) {
            throw new CustomException("400", "error.achievement.category.has_achievements");
        }

        achievementCategoryRepository.deleteById(id);
        log.info("업적 카테고리 삭제 및 캐시 갱신: id={}", id);
    }

    private void updateAchievementsCategoryCode(String oldCode, String newCode) {
        achievementRepository.findByCategoryCode(oldCode).forEach(achievement -> {
            achievement.setCategoryCode(newCode);
            achievementRepository.save(achievement);
        });
        log.info("업적들의 카테고리 코드 업데이트: {} -> {}", oldCode, newCode);
    }
}
