package io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.application;

import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.dto.WidgetStreakColorRequest;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.dto.WidgetStreakColorResponse;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.dto.WidgetStreakColorView;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.entity.WidgetStreakColor;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.policy.WidgetStreakColorRule;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.policy.WidgetStreakColorRule.Range;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.policy.WidgetStreakColorRule.Violation;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.infrastructure.WidgetStreakColorRepository;
import java.util.List;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * LUT-816: 홈 화면 위젯 'N일' 숫자 색 구간 — 어드민 조회·저장 + 위젯 조회.
 *
 * <p>구간 한 벌을 모든 위젯 문장에 쓴다. 저장은 보낸 목록으로 통째로 바꾼다(겹침 400 {@code 080206}, 빈 곳 400 {@code 080207}).
 *
 * <p>위젯 조회는 문장과 같은 이유로 인스턴스 로컬에 {@code widget.message.cache-seconds}(기본 60초) 메모한다. 조회가 실패하면 마지막
 * 메모로, 메모도 없으면 빈 목록으로 응답한다 — 색 설정 때문에 위젯 요약(연속 일수·문장)을 못 받게 두지 않는다. 빈 목록이면 위젯은 기본 색으로 그린다.
 */
@Slf4j
@Service
public class WidgetStreakColorService {

    private final WidgetStreakColorRepository repository;
    private final long memoMillis;
    private final LongSupplier clock;

    private volatile List<WidgetStreakColorView> memo;
    private volatile long memoAt;

    @Autowired
    public WidgetStreakColorService(
            WidgetStreakColorRepository repository,
            @Value("${widget.message.cache-seconds:60}") long cacheSeconds) {
        this(repository, cacheSeconds, System::currentTimeMillis);
    }

    WidgetStreakColorService(
            WidgetStreakColorRepository repository, long cacheSeconds, LongSupplier clock) {
        this.repository = repository;
        this.memoMillis = cacheSeconds * 1000L;
        this.clock = clock;
    }

    /** 위젯용 — 구간 전체(시작 일수 순). 위젯이 연속 실천 일수로 직접 고른다 */
    public List<WidgetStreakColorView> getRanges() {
        long now = clock.getAsLong();
        List<WidgetStreakColorView> snapshot = memo;
        if (snapshot != null && now - memoAt < memoMillis) {
            return snapshot;
        }
        try {
            snapshot = views(repository.findAllByOrderByMinDaysAsc());
        } catch (RuntimeException e) {
            log.warn("[widget-streak-color] 구간 조회 실패 — 마지막 메모(없으면 기본 색)로 응답: {}", e.getMessage());
            return snapshot == null ? List.of() : snapshot;
        }
        memo = snapshot;
        memoAt = now;
        return snapshot;
    }

    /** 커밋 전에 비우면 그 사이 다른 요청이 옛 값을 다시 메모할 수 있어 커밋 뒤에도 한 번 더 비운다 */
    private void evictAfterCommit() {
        memo = null;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            memo = null;
                        }
                    });
        }
    }

    // ========== 어드민 (Internal API) ==========

    @Transactional(transactionManager = "metaTransactionManager", readOnly = true)
    public WidgetStreakColorResponse list() {
        return new WidgetStreakColorResponse(views(repository.findAllByOrderByMinDaysAsc()));
    }

    /** 보낸 목록으로 통째로 바꾼다 — 빈 목록이면 모두 지운다 */
    @Transactional(transactionManager = "metaTransactionManager")
    public WidgetStreakColorResponse replace(WidgetStreakColorRequest request) {
        List<Range> ranges =
                WidgetStreakColorRule.normalize(
                        request.ranges().stream()
                                .map(r -> new Range(r.minDays(), r.maxDays(), r.color()))
                                .toList());
        Violation violation = WidgetStreakColorRule.firstViolation(ranges);
        if (violation != null) {
            throw WidgetStreakColorException.of(violation);
        }
        // 바로 지운다 — 영속성 컨텍스트에 맡기면 insert 가 delete 보다 먼저 나가 min_days 유니크에 걸린다
        repository.deleteAllInBatch();
        List<WidgetStreakColor> saved =
                repository.saveAll(
                        ranges.stream()
                                .map(
                                        r ->
                                                WidgetStreakColor.builder()
                                                        .minDays(r.min())
                                                        .maxDays(r.max())
                                                        .color(r.color())
                                                        .build())
                                .toList());
        evictAfterCommit();
        log.info("[widget-streak-color] 구간 저장 count={}", saved.size());
        return new WidgetStreakColorResponse(views(saved));
    }

    private static List<WidgetStreakColorView> views(List<WidgetStreakColor> ranges) {
        return ranges.stream().map(WidgetStreakColorView::from).toList();
    }
}
