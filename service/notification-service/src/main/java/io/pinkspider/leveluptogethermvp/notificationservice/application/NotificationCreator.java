package io.pinkspider.leveluptogethermvp.notificationservice.application;

import io.pinkspider.global.enums.NotificationType;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.entity.Notification;
import io.pinkspider.leveluptogethermvp.notificationservice.infrastructure.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-539: 중복 방지(dedup) 타입 알림의 insert 를 호출자와 **분리된 트랜잭션**에서 수행한다.
 *
 * <p>{@code NotificationService} 는 dedup 타입을 pre-check(exists) 후 saveAndFlush 하고
 * DataIntegrityViolationException 을 무시하는데, 그 catch 가 같은 트랜잭션 안이면 제약 위반 순간 세션이 오염돼 알림 저장 트랜잭션 전체가
 * 실패한다. insert 를 REQUIRES_NEW 로 분리해 호출자 컨텍스트를 보호한다. 구조는 gamification 의 {@code
 * UserStatsCreator}(LUT-538) 와 동일.
 *
 * <p>참고: notification 테이블에는 (user_id, notification_type, reference_id) 인덱스만 있고 유니크 제약은 없다 — 현재
 * dedup 은 pre-check 가 실질이고, 유니크 인덱스를 추가하는 날 이 구조가 레이스를 500 없이 흡수한다.
 *
 * <p>중복 예외는 삼키지 않고 호출자에게 던진다. 알림은 자연키가 없어 생성된 id 를 돌려주며, 호출자는 그 id 로 재조회해 managed 엔티티를 얻는다.
 */
@Service
@RequiredArgsConstructor
public class NotificationCreator {

    private final NotificationRepository notificationRepository;

    /**
     * 알림 행을 별도 트랜잭션에서 생성하고 id 를 돌려준다. 호출자는 반환 엔티티가 아니라 id 로 재조회할 것.
     *
     * @throws org.springframework.dao.DataIntegrityViolationException 유니크 제약 위반(다른 요청이 먼저 생성)
     */
    @Transactional(
            transactionManager = "notificationTransactionManager",
            propagation = Propagation.REQUIRES_NEW)
    public Long create(
            String userId,
            NotificationType type,
            String title,
            String message,
            String referenceType,
            Long referenceId,
            String actionUrl,
            String iconUrl) {
        Notification saved =
                notificationRepository.saveAndFlush(
                        Notification.builder()
                                .userId(userId)
                                .notificationType(type)
                                .title(title)
                                .message(message)
                                .referenceType(referenceType)
                                .referenceId(referenceId)
                                .actionUrl(actionUrl)
                                .iconUrl(iconUrl)
                                .build());
        return saved.getId();
    }
}
