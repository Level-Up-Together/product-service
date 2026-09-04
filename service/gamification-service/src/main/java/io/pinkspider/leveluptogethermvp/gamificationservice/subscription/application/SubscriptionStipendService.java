package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application;

import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.UserSubscription;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.infrastructure.UserSubscriptionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * LUT-453: 구독자 일일 다이아 스티펜드 — 블루(무상) 1개/일, 이월 허용·소멸 없음.
 *
 * <p>서버 스케줄러 전용 경로다 — 앱 실행 시점 지급은 중복·누락이 나서 금지. 지급 대상은 권한
 * 보유(활성·유예기간) 구독 전체이고, 구독별로 개별 트랜잭션으로 지급해 한 건의 실패가 배치를 멈추지
 * 않는다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionStipendService {

    /** 일일 지급량 (블루) */
    static final int DAILY_STIPEND_AMOUNT = 1;

    private final UserSubscriptionRepository userSubscriptionRepository;
    private final SubscriptionStipendTxService stipendTxService;

    /**
     * 지급일(UTC 오늘) 기준 일괄 지급.
     *
     * @return 실제 지급 건수
     */
    public int grantDailyStipends() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDate stipendDate = now.toLocalDate();

        List<UserSubscription> entitled = userSubscriptionRepository.findAllEntitled(now);
        if (entitled.isEmpty()) {
            log.info("구독 스티펜드 지급 대상 없음: date={}", stipendDate);
            return 0;
        }

        int granted = 0;
        int failed = 0;
        for (UserSubscription subscription : entitled) {
            try {
                if (stipendTxService.grantForSubscription(
                        subscription, stipendDate, DAILY_STIPEND_AMOUNT)) {
                    granted++;
                }
            } catch (Exception e) {
                // 한 구독 실패가 배치를 멈추지 않게 격리 — 다음 실행(멱등)에서 재시도된다
                failed++;
                log.error(
                        "구독 스티펜드 지급 실패: subscriptionId={}, userId={}",
                        subscription.getId(),
                        subscription.getUserId(),
                        e);
            }
        }
        log.info(
                "구독 스티펜드 지급 완료: date={}, 대상={}, 지급={}, 실패={}",
                stipendDate,
                entitled.size(),
                granted,
                failed);
        return granted;
    }
}
