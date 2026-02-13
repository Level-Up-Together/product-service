package io.pinkspider.leveluptogethermvp.missionservice.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionTemplateAdminPageResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionTemplateAdminRequest;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionTemplateAdminResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionTemplate;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionInterval;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionParticipationType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionSource;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionTemplateRepository;
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
@Transactional(transactionManager = "missionTransactionManager")
public class MissionTemplateAdminService {

    private final MissionTemplateRepository templateRepository;

    @Transactional(readOnly = true, transactionManager = "missionTransactionManager")
    public MissionTemplateAdminPageResponse searchTemplates(String keyword, Pageable pageable) {
        Page<MissionTemplateAdminResponse> page;
        if (keyword != null && !keyword.isBlank()) {
            page = templateRepository.searchTemplatesAdmin(keyword, pageable)
                .map(MissionTemplateAdminResponse::from);
        } else {
            page = templateRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(MissionTemplateAdminResponse::from);
        }
        return MissionTemplateAdminPageResponse.from(page);
    }

    @Transactional(readOnly = true, transactionManager = "missionTransactionManager")
    public List<MissionTemplateAdminResponse> getAllTemplates() {
        return templateRepository.findAll().stream()
            .map(MissionTemplateAdminResponse::from)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true, transactionManager = "missionTransactionManager")
    public MissionTemplateAdminResponse getTemplate(Long id) {
        MissionTemplate template = templateRepository.findById(id)
            .orElseThrow(() -> new CustomException("050201", "미션 템플릿을 찾을 수 없습니다: " + id));
        return MissionTemplateAdminResponse.from(template);
    }

    public MissionTemplateAdminResponse createTemplate(MissionTemplateAdminRequest request) {
        MissionTemplate template = MissionTemplate.builder()
            .title(request.title())
            .titleEn(request.titleEn())
            .titleAr(request.titleAr())
            .description(request.description())
            .descriptionEn(request.descriptionEn())
            .descriptionAr(request.descriptionAr())
            .visibility(request.visibility() != null ? MissionVisibility.valueOf(request.visibility()) : MissionVisibility.PUBLIC)
            .source(request.source() != null ? MissionSource.valueOf(request.source()) : MissionSource.SYSTEM)
            .participationType(request.participationType() != null
                ? MissionParticipationType.valueOf(request.participationType()) : MissionParticipationType.DIRECT)
            .missionInterval(request.missionInterval() != null
                ? MissionInterval.valueOf(request.missionInterval()) : MissionInterval.DAILY)
            .durationMinutes(request.durationMinutes())
            .bonusExpOnFullCompletion(request.bonusExpOnFullCompletion() != null ? request.bonusExpOnFullCompletion() : 50)
            .isPinned(Boolean.TRUE.equals(request.isPinned()))
            .targetDurationMinutes(request.targetDurationMinutes())
            .dailyExecutionLimit(request.dailyExecutionLimit())
            .categoryId(request.categoryId())
            .customCategory(request.customCategory())
            .creatorId("ADMIN")
            .build();

        MissionTemplate saved = templateRepository.save(template);
        log.info("미션 템플릿 생성 (Admin): {} (ID: {})", request.title(), saved.getId());
        return MissionTemplateAdminResponse.from(saved);
    }

    public MissionTemplateAdminResponse updateTemplate(Long id, MissionTemplateAdminRequest request) {
        MissionTemplate template = templateRepository.findById(id)
            .orElseThrow(() -> new CustomException("050201", "미션 템플릿을 찾을 수 없습니다: " + id));

        template.setTitle(request.title());
        template.setTitleEn(request.titleEn());
        template.setTitleAr(request.titleAr());
        template.setDescription(request.description());
        template.setDescriptionEn(request.descriptionEn());
        template.setDescriptionAr(request.descriptionAr());
        if (request.visibility() != null) {
            template.setVisibility(MissionVisibility.valueOf(request.visibility()));
        }
        if (request.source() != null) {
            template.setSource(MissionSource.valueOf(request.source()));
        }
        if (request.participationType() != null) {
            template.setParticipationType(MissionParticipationType.valueOf(request.participationType()));
        }
        if (request.missionInterval() != null) {
            template.setMissionInterval(MissionInterval.valueOf(request.missionInterval()));
        }
        template.setDurationMinutes(request.durationMinutes());
        template.setBonusExpOnFullCompletion(request.bonusExpOnFullCompletion());
        if (request.isPinned() != null) {
            template.setIsPinned(request.isPinned());
        }
        template.setTargetDurationMinutes(request.targetDurationMinutes());
        template.setDailyExecutionLimit(request.dailyExecutionLimit());
        template.setCategoryId(request.categoryId());
        template.setCustomCategory(request.customCategory());

        MissionTemplate saved = templateRepository.save(template);
        log.info("미션 템플릿 수정 (Admin): {} (ID: {})", request.title(), id);
        return MissionTemplateAdminResponse.from(saved);
    }

    public void deleteTemplate(Long id) {
        if (!templateRepository.existsById(id)) {
            throw new CustomException("050201", "미션 템플릿을 찾을 수 없습니다: " + id);
        }
        templateRepository.deleteById(id);
        log.info("미션 템플릿 삭제 (Admin): ID={}", id);
    }

    @Transactional(readOnly = true, transactionManager = "missionTransactionManager")
    public Long countBySource(String source) {
        return templateRepository.countBySource(MissionSource.valueOf(source));
    }

    @Transactional(readOnly = true, transactionManager = "missionTransactionManager")
    public Long countBySourceAndParticipationType(String source, String participationType) {
        return templateRepository.countBySourceAndParticipationType(
            MissionSource.valueOf(source), MissionParticipationType.valueOf(participationType));
    }
}
