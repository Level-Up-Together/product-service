package io.pinkspider.leveluptogethermvp.missionservice.saga.steps;

import io.pinkspider.global.saga.SagaStep;
import io.pinkspider.global.saga.SagaStepResult;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionContext;
import io.pinkspider.leveluptogethermvp.userservice.notification.application.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Step 7: 알림 발송
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SendNotificationStep implements SagaStep<MissionCompletionContext> {

    private final NotificationService notificationService;

    @Override
    public String getName() {
        return "SendNotification";
    }

    @Override
    public boolean isMandatory() {
        // 알림 발송 실패는 미션 완료 자체를 실패시키지 않음
        return false;
    }

    @Override
    public SagaStepResult execute(MissionCompletionContext context) {
        String userId = context.getUserId();
        String missionTitle = context.getMission().getTitle();
        Long missionId = context.getMission().getId();

        log.debug("Sending mission completion notification: userId={}, missionId={}", userId, missionId);

        try {
            notificationService.notifyMissionCompleted(userId, missionTitle, missionId);
            log.info("Notification sent: userId={}, missionId={}", userId, missionId);
            return SagaStepResult.success("알림 발송 완료");

        } catch (Exception e) {
            log.warn("Failed to send notification: userId={}, error={}", userId, e.getMessage());
            return SagaStepResult.failure("알림 발송 실패", e);
        }
    }

    @Override
    public SagaStepResult compensate(MissionCompletionContext context) {
        // 알림은 보상 불필요 (발송된 알림을 취소하는 것은 의미 없음)
        log.debug("Notification compensation - no action needed");
        return SagaStepResult.success();
    }
}
