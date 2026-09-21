package io.pinkspider.leveluptogethermvp.notificationservice.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.AdminPushCampaignPageResponse;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.AdminPushCampaignRequest;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.AdminPushCampaignResponse;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.entity.AdminPushCampaign;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.enums.AdminPushTargetType;
import io.pinkspider.leveluptogethermvp.notificationservice.event.AdminPushCampaignCreatedEvent;
import io.pinkspider.leveluptogethermvp.notificationservice.infrastructure.AdminPushCampaignRepository;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-508: 관리자 푸시 알림 관리 — 이력 행 생성 + 비동기 발송 트리거 + 이력 조회.
 *
 * <p>대상 산출은 요청 시점에 확정한다(전체 = ACTIVE 유저 전원, 일부 = 요청 ID 중 활성 유저). 이력 행이 커밋된
 * 뒤 {@link AdminPushCampaignCreatedEvent} 로 {@link AdminPushCampaignDispatcher} 가 유저별 알림을 만든다 —
 * 어드민 요청은 대상 수를 받고 바로 끝나고, 실제 발송 결과는 이력 조회로 확인한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true, transactionManager = "notificationTransactionManager")
public class AdminPushCampaignService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final AdminPushCampaignRepository campaignRepository;
    private final UserQueryFacade userQueryFacade;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Transactional(transactionManager = "notificationTransactionManager")
    public AdminPushCampaignResponse create(AdminPushCampaignRequest request, Long adminId) {
        String title = request.getTitle().trim();
        String body = request.getBody().trim();
        String actionUrl =
                request.getActionUrl() == null || request.getActionUrl().isBlank()
                        ? null
                        : request.getActionUrl().trim();

        List<String> targets = resolveTargets(request);
        String targetUserIdsJson =
                request.getTargetType() == AdminPushTargetType.USERS ? toJson(targets) : null;

        AdminPushCampaign saved =
                campaignRepository.save(
                        AdminPushCampaign.create(
                                title,
                                body,
                                actionUrl,
                                request.getTargetType(),
                                targetUserIdsJson,
                                targets.size(),
                                adminId));
        log.info(
                "관리자 푸시 캠페인 생성: id={}, targetType={}, targetCount={}, adminId={}",
                saved.getId(),
                saved.getTargetType(),
                saved.getTargetCount(),
                adminId);
        // 커밋 후 비동기 발송 (AFTER_COMMIT 리스너) — 롤백되면 발송도 없다
        eventPublisher.publishEvent(new AdminPushCampaignCreatedEvent(saved.getId()));
        return AdminPushCampaignResponse.from(
                saved, request.getTargetType() == AdminPushTargetType.USERS ? targets : null);
    }

    public AdminPushCampaignPageResponse getCampaigns(int page, int size) {
        Page<AdminPushCampaign> campaigns =
                campaignRepository.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(page, size));
        // 목록은 대상 유저 목록을 싣지 않는다 (상세에서만)
        return AdminPushCampaignPageResponse.from(
                campaigns.map(campaign -> AdminPushCampaignResponse.from(campaign, null)));
    }

    public AdminPushCampaignResponse getCampaign(Long campaignId) {
        AdminPushCampaign campaign =
                campaignRepository
                        .findById(campaignId)
                        .orElseThrow(
                                () ->
                                        new CustomException(
                                                "140103", "error.push_campaign.not_found"));
        return AdminPushCampaignResponse.from(
                campaign, parseTargetUserIds(campaign.getTargetUserIds()));
    }

    /**
     * 대상 유저 확정. ALL = 활성 유저 전원. USERS = 요청 ID(중복 제거) 중 활성 유저만 — 미존재·정지·탈퇴 ID 는
     * 조용히 제외하되 남는 대상이 없으면 요청 오류로 돌려준다(빈 캠페인 이력 방지).
     */
    private List<String> resolveTargets(AdminPushCampaignRequest request) {
        if (request.getTargetType() == AdminPushTargetType.ALL) {
            List<String> all = userQueryFacade.findAllActiveUserIds();
            if (all.isEmpty()) {
                throw new CustomException("140102", "error.push_campaign.no_active_target");
            }
            return all;
        }
        List<String> requested =
                request.getUserIds() == null
                        ? List.of()
                        : request.getUserIds().stream()
                                .filter(id -> id != null && !id.isBlank())
                                .map(String::trim)
                                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
                                .stream()
                                .toList();
        if (requested.isEmpty()) {
            throw new CustomException("140101", "error.push_campaign.target_required");
        }
        List<String> active = userQueryFacade.getActiveUserIds(requested);
        if (active.isEmpty()) {
            throw new CustomException("140102", "error.push_campaign.no_active_target");
        }
        return active;
    }

    private String toJson(List<String> ids) {
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("target user ids 직렬화 실패", e);
        }
    }

    List<String> parseTargetUserIds(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (JsonProcessingException e) {
            log.warn("캠페인 대상 유저 JSON 파싱 실패: {}", e.getMessage());
            return null;
        }
    }
}
