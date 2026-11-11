package io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.application;

import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.dto.WidgetMessageListResponse;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.dto.WidgetMessageListResponse.WidgetMessageGapResponse;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.dto.WidgetMessageRequest;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.dto.WidgetMessageResponse;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.dto.WidgetMessageView;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.entity.WidgetMessage;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.enums.WidgetMessageCondition;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.policy.WidgetHourRange;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.infrastructure.WidgetMessageRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * LUT-775: 홈 화면 위젯 시간대별 문장 — 어드민 CRUD(LUT-776) + 위젯 조회.
 *
 * <p>위젯 조회는 하루 수십 번이라 활성 문장을 인스턴스 로컬로 {@code widget.message.cache-seconds}(기본 60초) 메모한다 — 어드민 변경은 그
 * 노드에서 즉시, 다른 노드에는 그 안에 반영된다. Redis 캐시를 쓰지 않는 이유: 캐시 직렬화가 final 클래스(record)의 타입 정보를 남기지 않고, 엔티티를
 * 캐시하면 컬럼 추가 뒤 옛 값이 남는다(LUT-534). 조회 실패는 마지막 메모가 있으면 그것으로 응답한다.
 */
@Slf4j
@Service
public class WidgetMessageService {

    private final WidgetMessageRepository repository;
    private final long memoMillis;
    private final LongSupplier clock;

    private volatile List<WidgetMessage> activeMemo;
    private volatile long activeMemoAt;

    @Autowired
    public WidgetMessageService(
            WidgetMessageRepository repository,
            @Value("${widget.message.cache-seconds:60}") long cacheSeconds) {
        this(repository, cacheSeconds, System::currentTimeMillis);
    }

    WidgetMessageService(
            WidgetMessageRepository repository, long cacheSeconds, LongSupplier clock) {
        this.repository = repository;
        this.memoMillis = cacheSeconds * 1000L;
        this.clock = clock;
    }

    /** 위젯용 — 활성 문장 전체를 요청 언어로(시작 시각·순서 순). 위젯이 시간대·완료 여부로 직접 고른다 */
    public List<WidgetMessageView> getActiveMessages(String language) {
        return activeMessages().stream().map(m -> WidgetMessageView.of(m, language)).toList();
    }

    private List<WidgetMessage> activeMessages() {
        long now = clock.getAsLong();
        List<WidgetMessage> snapshot = activeMemo;
        if (snapshot != null && now - activeMemoAt < memoMillis) {
            return snapshot;
        }
        try {
            snapshot =
                    List.copyOf(repository.findByActiveTrueOrderByStartHourAscSortOrderAscIdAsc());
        } catch (RuntimeException e) {
            if (snapshot == null) {
                throw e;
            }
            log.warn("[widget-message] 활성 문장 조회 실패 — 마지막 메모로 응답: {}", e.getMessage());
            return snapshot;
        }
        activeMemo = snapshot;
        activeMemoAt = now;
        return snapshot;
    }

    /** 커밋 전에 비우면 그 사이 다른 요청이 옛 값을 다시 메모할 수 있어 커밋 뒤에도 한 번 더 비운다 */
    private void evictAfterCommit() {
        activeMemo = null;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            activeMemo = null;
                        }
                    });
        }
    }

    // ========== 어드민 (Internal API) ==========

    /** 문장 목록(시작 시각 → 순서 → ID) + 빈 시간대. {@code condition} 은 목록만 거르고 빈 시간대는 늘 전체 기준 */
    @Transactional(transactionManager = "metaTransactionManager", readOnly = true)
    public WidgetMessageListResponse list(@Nullable WidgetMessageCondition condition) {
        List<WidgetMessage> all = repository.findAllByOrderByStartHourAscSortOrderAscIdAsc();
        List<WidgetMessageResponse> messages =
                all.stream()
                        .filter(m -> condition == null || m.getCondition() == condition)
                        .map(WidgetMessageResponse::from)
                        .toList();
        return new WidgetMessageListResponse(messages, gaps(all));
    }

    /** 완료 전·완료 후 각각, 활성 문장(그 조건 + ALL)이 하나도 걸리지 않는 시각 */
    static List<WidgetMessageGapResponse> gaps(List<WidgetMessage> messages) {
        List<WidgetMessageGapResponse> gaps = new ArrayList<>();
        for (WidgetMessageCondition state :
                List.of(WidgetMessageCondition.NOT_DONE, WidgetMessageCondition.DONE)) {
            boolean completedToday = state == WidgetMessageCondition.DONE;
            List<int[]> ranges =
                    messages.stream()
                            .filter(WidgetMessage::isActive)
                            .filter(m -> m.getCondition().matches(completedToday))
                            .map(m -> new int[] {m.getStartHour(), m.getEndHour()})
                            .toList();
            WidgetHourRange.gaps(ranges)
                    .forEach(
                            g ->
                                    gaps.add(
                                            new WidgetMessageGapResponse(
                                                    state, g.startHour(), g.endHour())));
        }
        return gaps;
    }

    @Transactional(transactionManager = "metaTransactionManager", readOnly = true)
    public WidgetMessageResponse get(Long id) {
        return WidgetMessageResponse.from(find(id));
    }

    @Transactional(transactionManager = "metaTransactionManager")
    public WidgetMessageResponse create(WidgetMessageRequest request) {
        WidgetMessage saved =
                repository.save(
                        WidgetMessage.builder()
                                .startHour(request.startHour())
                                .endHour(request.endHour())
                                .condition(request.condition())
                                .textKo(request.textKo().trim())
                                .textEn(optionalText(request.textEn()))
                                .textJa(optionalText(request.textJa()))
                                .textAr(optionalText(request.textAr()))
                                .active(request.active() == null || request.active())
                                .sortOrder(request.sortOrder() == null ? 0 : request.sortOrder())
                                .build());
        evictAfterCommit();
        log.info(
                "[widget-message] 생성 id={} {}~{} {}",
                saved.getId(),
                saved.getStartHour(),
                saved.getEndHour(),
                saved.getCondition());
        return WidgetMessageResponse.from(saved);
    }

    @Transactional(transactionManager = "metaTransactionManager")
    public WidgetMessageResponse update(Long id, WidgetMessageRequest request) {
        WidgetMessage message = find(id);
        message.update(
                request.startHour(),
                request.endHour(),
                request.condition(),
                request.textKo().trim(),
                optionalText(request.textEn()),
                optionalText(request.textJa()),
                optionalText(request.textAr()),
                request.active() == null ? message.isActive() : request.active(),
                request.sortOrder() == null ? message.getSortOrder() : request.sortOrder());
        evictAfterCommit();
        log.info("[widget-message] 수정 id={}", id);
        return WidgetMessageResponse.from(message);
    }

    @Transactional(transactionManager = "metaTransactionManager")
    public WidgetMessageResponse toggleActive(Long id) {
        WidgetMessage message = find(id);
        message.toggleActive();
        evictAfterCommit();
        log.info("[widget-message] 활성 변경 id={} active={}", id, message.isActive());
        return WidgetMessageResponse.from(message);
    }

    @Transactional(transactionManager = "metaTransactionManager")
    public void delete(Long id) {
        repository.delete(find(id));
        evictAfterCommit();
        log.info("[widget-message] 삭제 id={}", id);
    }

    private WidgetMessage find(Long id) {
        return repository.findById(id).orElseThrow(() -> new WidgetMessageNotFoundException(id));
    }

    /** 선택 언어 — 비우면 null(위젯이 en → ko 로 대체) */
    private static String optionalText(@Nullable String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
