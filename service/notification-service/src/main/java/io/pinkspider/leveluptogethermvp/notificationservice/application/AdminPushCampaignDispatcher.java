package io.pinkspider.leveluptogethermvp.notificationservice.application;

import static io.pinkspider.global.config.AsyncConfig.EVENT_EXECUTOR;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.global.enums.NotificationType;
import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.NotificationResponse;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.entity.AdminPushCampaign;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.enums.AdminPushTargetType;
import io.pinkspider.leveluptogethermvp.notificationservice.event.AdminPushCampaignCreatedEvent;
import io.pinkspider.leveluptogethermvp.notificationservice.infrastructure.AdminPushCampaignRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * LUT-508: 관리자 푸시 캠페인 비동기 발송기.
 *
 * <p>캠페인 이력 커밋 후 유저별로 {@link NotificationService#createNotification} 을 호출한다 — 기존 알림
 * 파이프라인(카테고리 토글 → DB 저장 → 실시간 → 방해금지 판정 → Redis Stream → FCM)을 그대로 타므로 유저의
 * 시스템 알림 설정이 존중된다. 유저 한 명의 실패가 나머지를 막지 않도록 개별 try/catch 로 세고, 각 호출은
 * 자기 트랜잭션(REQUIRED, 여기는 비트랜잭션)으로 커밋된다. 결과 건수·상태는 캠페인 행에 남긴다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminPushCampaignDispatcher {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    static final String REFERENCE_TYPE = "ADMIN_PUSH";

    private final AdminPushCampaignRepository campaignRepository;
    private final NotificationService notificationService;
    private final UserQueryFacade userQueryFacade;
    private final ObjectMapper objectMapper;

    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCampaignCreated(AdminPushCampaignCreatedEvent event) {
        dispatch(event.campaignId());
    }

    /** 발송 본체 — 테스트·재시도 진입점. PENDING 이 아니면(중복 트리거) 아무것도 하지 않는다. */
    public void dispatch(Long campaignId) {
        AdminPushCampaign campaign = campaignRepository.findById(campaignId).orElse(null);
        if (campaign == null) {
            log.warn("관리자 푸시 캠페인 없음 — 발송 스킵: id={}", campaignId);
            return;
        }
        if (!campaign.isPending()) {
            log.info("관리자 푸시 캠페인 상태가 PENDING 아님 — 발송 스킵: id={}, status={}",
                    campaignId, campaign.getStatus());
            return;
        }
        campaign.markSending(LocalDateTime.now());
        campaignRepository.save(campaign);

        int sent = 0;
        int skipped = 0;
        int failed = 0;
        try {
            List<String> targets = resolveTargets(campaign);
            log.info("관리자 푸시 발송 시작: id={}, targetType={}, targets={}",
                    campaignId, campaign.getTargetType(), targets.size());
            for (String userId : targets) {
                try {
                    NotificationResponse created =
                            notificationService.createNotification(
                                    userId,
                                    NotificationType.ADMIN_PUSH,
                                    campaign.getTitle(),
                                    campaign.getBody(),
                                    REFERENCE_TYPE,
                                    campaignId,
                                    campaign.getActionUrl());
                    if (created != null) {
                        sent++;
                    } else {
                        // 유저가 시스템 알림 카테고리를 껐음 — 파이프라인이 저장·발송 모두 건너뜀
                        skipped++;
                    }
                } catch (Exception e) {
                    failed++;
                    log.warn("관리자 푸시 개별 발송 실패: campaignId={}, userId={}, error={}",
                            campaignId, userId, e.getMessage());
                }
            }
            campaign.complete(sent, skipped, failed, LocalDateTime.now());
            campaignRepository.save(campaign);
            log.info("관리자 푸시 발송 완료: id={}, sent={}, skipped={}, failed={}",
                    campaignId, sent, skipped, failed);
        } catch (Exception e) {
            log.error("관리자 푸시 발송 실패: id={}", campaignId, e);
            campaign.fail(sent, skipped, failed, e.getMessage(), LocalDateTime.now());
            campaignRepository.save(campaign);
        }
    }

    private List<String> resolveTargets(AdminPushCampaign campaign) throws Exception {
        if (campaign.getTargetType() == AdminPushTargetType.ALL) {
            // 생성 시점 이후 가입한 유저도 포함될 수 있다 — target_count 와 미세하게 다를 수 있음
            return userQueryFacade.findAllActiveUserIds();
        }
        if (campaign.getTargetUserIds() == null) {
            return List.of();
        }
        return objectMapper.readValue(campaign.getTargetUserIds(), STRING_LIST);
    }
}
