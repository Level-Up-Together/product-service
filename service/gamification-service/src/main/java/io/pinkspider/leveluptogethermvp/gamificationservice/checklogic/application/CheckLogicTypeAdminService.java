package io.pinkspider.leveluptogethermvp.gamificationservice.checklogic.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.checklogic.domain.dto.CheckLogicTypeAdminPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.checklogic.domain.dto.CheckLogicTypeAdminRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.checklogic.domain.dto.CheckLogicTypeAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.checklogic.domain.dto.ComparisonOperatorAdminInfo;
import io.pinkspider.leveluptogethermvp.gamificationservice.checklogic.domain.dto.DataSourceAdminInfo;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.CheckLogicType;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.CheckLogicComparisonOperator;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.CheckLogicDataSource;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.AchievementRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.CheckLogicTypeRepository;
import io.pinkspider.leveluptogethermvp.metaservice.application.MissionCategoryService;
import io.pinkspider.leveluptogethermvp.metaservice.domain.dto.MissionCategoryResponse;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(transactionManager = "gamificationTransactionManager")
public class CheckLogicTypeAdminService {

    private final CheckLogicTypeRepository checkLogicTypeRepository;
    private final MissionCategoryService missionCategoryService;
    private final AchievementRepository achievementRepository;

    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public CheckLogicTypeAdminPageResponse searchCheckLogicTypes(Pageable pageable) {
        Page<CheckLogicTypeAdminResponse> page = checkLogicTypeRepository.findAllByOrderBySortOrderAsc(pageable)
            .map(CheckLogicTypeAdminResponse::from);
        return CheckLogicTypeAdminPageResponse.from(page);
    }

    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public List<CheckLogicTypeAdminResponse> getAllCheckLogicTypes() {
        return checkLogicTypeRepository.findAllByOrderBySortOrderAsc().stream()
            .map(CheckLogicTypeAdminResponse::from)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public List<CheckLogicTypeAdminResponse> getActiveCheckLogicTypes() {
        return checkLogicTypeRepository.findByIsActiveTrueOrderBySortOrderAsc().stream()
            .map(CheckLogicTypeAdminResponse::from)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public List<CheckLogicTypeAdminResponse> getCheckLogicTypesByDataSource(String dataSourceCode) {
        CheckLogicDataSource dataSource = CheckLogicDataSource.fromCode(dataSourceCode);
        return checkLogicTypeRepository.findByDataSourceAndIsActiveTrueOrderBySortOrderAsc(dataSource).stream()
            .map(CheckLogicTypeAdminResponse::from)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public CheckLogicTypeAdminResponse getCheckLogicType(Long id) {
        CheckLogicType entity = checkLogicTypeRepository.findById(id)
            .orElseThrow(() -> new CustomException("120201", "error.checklogic.not_found"));
        return CheckLogicTypeAdminResponse.from(entity);
    }

    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public CheckLogicTypeAdminResponse getCheckLogicTypeByCode(String code) {
        CheckLogicType entity = checkLogicTypeRepository.findByCode(code)
            .orElseThrow(() -> new CustomException("120201", "error.checklogic.not_found"));
        return CheckLogicTypeAdminResponse.from(entity);
    }

    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public List<DataSourceAdminInfo> getDataSources() {
        // QA-145: USER_CATEGORY_EXPERIENCE 의 availableFields 는 실시간 mission_category 에서 채운다.
        List<MissionCategoryResponse> activeCategories = missionCategoryService.getActiveCategories();
        return Arrays.stream(CheckLogicDataSource.values())
            .map(ds -> DataSourceAdminInfo.from(ds, activeCategories))
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public List<ComparisonOperatorAdminInfo> getComparisonOperators() {
        return Arrays.stream(CheckLogicComparisonOperator.values())
            .map(ComparisonOperatorAdminInfo::from)
            .collect(Collectors.toList());
    }

    public CheckLogicTypeAdminResponse createCheckLogicType(CheckLogicTypeAdminRequest request) {
        if (checkLogicTypeRepository.existsByCode(request.code())) {
            throw new CustomException("120202", "error.checklogic.duplicate_code");
        }

        CheckLogicDataSource dataSource = CheckLogicDataSource.fromCode(request.dataSource());
        CheckLogicComparisonOperator operator = request.comparisonOperator() != null
            ? CheckLogicComparisonOperator.fromCode(request.comparisonOperator())
            : CheckLogicComparisonOperator.GTE;

        CheckLogicType entity = CheckLogicType.builder()
            .code(request.code())
            .name(request.name())
            .description(request.description())
            .dataSource(dataSource)
            .dataField(request.dataField())
            .comparisonOperator(operator)
            .configJson(request.configJson())
            .sortOrder(request.sortOrder() != null ? request.sortOrder() : 0)
            .isActive(request.isActive() != null ? request.isActive() : true)
            .build();

        CheckLogicType saved = checkLogicTypeRepository.save(entity);
        log.info("체크 로직 유형 생성: {} (ID: {})", request.code(), saved.getId());
        return CheckLogicTypeAdminResponse.from(saved);
    }

    public CheckLogicTypeAdminResponse updateCheckLogicType(Long id, CheckLogicTypeAdminRequest request) {
        CheckLogicType entity = checkLogicTypeRepository.findById(id)
            .orElseThrow(() -> new CustomException("120201", "error.checklogic.not_found"));

        if (checkLogicTypeRepository.existsByCodeAndIdNot(request.code(), id)) {
            throw new CustomException("120202", "error.checklogic.duplicate_code");
        }

        CheckLogicDataSource dataSource = CheckLogicDataSource.fromCode(request.dataSource());
        CheckLogicComparisonOperator operator = request.comparisonOperator() != null
            ? CheckLogicComparisonOperator.fromCode(request.comparisonOperator())
            : entity.getComparisonOperator();

        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setDataSource(dataSource);
        entity.setDataField(request.dataField());
        entity.setComparisonOperator(operator);
        entity.setConfigJson(request.configJson());
        if (request.sortOrder() != null) {
            entity.setSortOrder(request.sortOrder());
        }
        if (request.isActive() != null) {
            entity.setIsActive(request.isActive());
        }

        CheckLogicType updated = checkLogicTypeRepository.save(entity);
        log.info("체크 로직 유형 수정: {} (ID: {})", request.code(), id);
        return CheckLogicTypeAdminResponse.from(updated);
    }

    public CheckLogicTypeAdminResponse toggleActiveStatus(Long id) {
        CheckLogicType entity = checkLogicTypeRepository.findById(id)
            .orElseThrow(() -> new CustomException("120201", "error.checklogic.not_found"));

        entity.setIsActive(!entity.getIsActive());
        CheckLogicType updated = checkLogicTypeRepository.save(entity);
        log.info("체크 로직 유형 상태 변경: {} (ID: {}) -> {}", entity.getCode(), id, entity.getIsActive());
        return CheckLogicTypeAdminResponse.from(updated);
    }

    public void deleteCheckLogicType(Long id) {
        CheckLogicType entity = checkLogicTypeRepository.findById(id)
            .orElseThrow(() -> new CustomException("120201", "error.checklogic.not_found"));

        // QA-154: achievement 가 참조 중이면 FK violation 으로 500 발생 → 사전 확인 후 명확한 에러 반환.
        long referencingAchievements = achievementRepository.countByCheckLogicTypeId(id);
        if (referencingAchievements > 0) {
            log.warn("체크 로직 유형 삭제 거부 (참조 업적 존재): id={}, code={}, refs={}",
                id, entity.getCode(), referencingAchievements);
            throw new CustomException("120203", "error.checklogic.in_use");
        }

        log.info("체크 로직 유형 삭제: {} (ID: {})", entity.getCode(), id);
        checkLogicTypeRepository.delete(entity);
    }
}
