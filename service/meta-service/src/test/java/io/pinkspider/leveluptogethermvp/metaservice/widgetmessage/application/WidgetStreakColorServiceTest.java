package io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.dto.WidgetStreakColorRequest;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.dto.WidgetStreakColorResponse;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.dto.WidgetStreakColorView;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.entity.WidgetStreakColor;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.infrastructure.WidgetStreakColorRepository;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
@DisplayName("WidgetStreakColorService — 위젯 'N일' 숫자 색 구간 (LUT-816)")
class WidgetStreakColorServiceTest {

    private static final String GREEN = "#1dec06";
    private static final String CYAN = "#00f9ff";

    @Mock WidgetStreakColorRepository repository;

    private final AtomicLong clock = new AtomicLong(1_000_000L);
    private WidgetStreakColorService service;

    @BeforeEach
    void setUp() {
        service = new WidgetStreakColorService(repository, 60, clock::get);
    }

    @AfterEach
    void clearSync() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static WidgetStreakColor range(int min, Integer max, String color) {
        return WidgetStreakColor.builder().minDays(min).maxDays(max).color(color).build();
    }

    private static WidgetStreakColorRequest request(WidgetStreakColorRequest.Range... ranges) {
        return new WidgetStreakColorRequest(List.of(ranges));
    }

    private static WidgetStreakColorRequest.Range item(int min, Integer max, String color) {
        return new WidgetStreakColorRequest.Range(min, max, color);
    }

    @Nested
    @DisplayName("위젯 조회")
    class GetRanges {

        @Test
        @DisplayName("구간 전체를 시작 일수 순으로 돌려주고 60초 동안 메모한다")
        void memo() {
            when(repository.findAllByOrderByMinDaysAsc())
                    .thenReturn(List.of(range(1, 7, GREEN), range(8, null, CYAN)));

            List<WidgetStreakColorView> first = service.getRanges();
            clock.addAndGet(59_999);
            List<WidgetStreakColorView> second = service.getRanges();

            assertThat(first)
                    .containsExactly(
                            new WidgetStreakColorView(1, 7, GREEN),
                            new WidgetStreakColorView(8, null, CYAN));
            assertThat(second).isSameAs(first);
            verify(repository, times(1)).findAllByOrderByMinDaysAsc();
        }

        @Test
        @DisplayName("메모 시간이 지나면 다시 읽는다")
        void memoExpires() {
            when(repository.findAllByOrderByMinDaysAsc())
                    .thenReturn(List.of(range(1, 7, GREEN)))
                    .thenReturn(List.of(range(1, 7, CYAN)));

            service.getRanges();
            clock.addAndGet(60_000);

            assertThat(service.getRanges()).containsExactly(new WidgetStreakColorView(1, 7, CYAN));
        }

        @Test
        @DisplayName("조회가 실패하면 마지막 메모로 응답한다")
        void failureFallsBackToMemo() {
            when(repository.findAllByOrderByMinDaysAsc())
                    .thenReturn(List.of(range(1, 7, GREEN)))
                    .thenThrow(new IllegalStateException("db down"));

            service.getRanges();
            clock.addAndGet(60_000);

            assertThat(service.getRanges()).containsExactly(new WidgetStreakColorView(1, 7, GREEN));
        }

        @Test
        @DisplayName("메모도 없는데 실패하면 빈 목록 — 위젯 요약을 막지 않는다(위젯은 기본 색)")
        void failureWithoutMemo() {
            when(repository.findAllByOrderByMinDaysAsc())
                    .thenThrow(new IllegalStateException("db down"));

            assertThat(service.getRanges()).isEmpty();
        }
    }

    @Nested
    @DisplayName("어드민")
    class Admin {

        @Test
        @DisplayName("목록은 시작 일수 순")
        void list() {
            when(repository.findAllByOrderByMinDaysAsc())
                    .thenReturn(List.of(range(1, 7, GREEN), range(8, 30, CYAN)));

            WidgetStreakColorResponse response = service.list();

            assertThat(response.ranges())
                    .containsExactly(
                            new WidgetStreakColorView(1, 7, GREEN),
                            new WidgetStreakColorView(8, 30, CYAN));
        }

        @Test
        @DisplayName("저장은 통째로 바꾼다 — 먼저 지우고, 정렬·소문자로 맞춰 넣는다")
        void replace() {
            when(repository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

            WidgetStreakColorResponse response =
                    service.replace(request(item(8, null, "#00F9FF"), item(1, 7, "#1DEC06")));

            InOrder order = inOrder(repository);
            order.verify(repository).deleteAllInBatch();
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<WidgetStreakColor>> saved = ArgumentCaptor.forClass(List.class);
            order.verify(repository).saveAll(saved.capture());
            assertThat(saved.getValue())
                    .extracting(
                            WidgetStreakColor::getMinDays,
                            WidgetStreakColor::getMaxDays,
                            WidgetStreakColor::getColor)
                    .containsExactly(tuple(1, 7, GREEN), tuple(8, null, CYAN));
            assertThat(response.ranges())
                    .containsExactly(
                            new WidgetStreakColorView(1, 7, GREEN),
                            new WidgetStreakColorView(8, null, CYAN));
        }

        @Test
        @DisplayName("빈 목록으로 저장하면 구간을 모두 지운다")
        void replaceWithEmpty() {
            when(repository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

            WidgetStreakColorResponse response = service.replace(request());

            verify(repository).deleteAllInBatch();
            assertThat(response.ranges()).isEmpty();
        }

        @Test
        @DisplayName("겹치면 080206 — 아무것도 지우지 않는다")
        void overlap() {
            assertThatThrownBy(
                            () -> service.replace(request(item(1, 10, GREEN), item(8, 30, CYAN))))
                    .isInstanceOfSatisfying(
                            WidgetStreakColorException.class,
                            e -> {
                                assertThat(e.getCode())
                                        .isEqualTo(WidgetStreakColorException.CODE_OVERLAP);
                                assertThat(e.getMessageKey())
                                        .isEqualTo("error.widget_message.streak_color_overlap");
                            });
            verify(repository, never()).deleteAllInBatch();
            verify(repository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("사이가 비면 080207 — 아무것도 지우지 않는다")
        void gap() {
            assertThatThrownBy(() -> service.replace(request(item(1, 7, GREEN), item(9, 30, CYAN))))
                    .isInstanceOfSatisfying(
                            WidgetStreakColorException.class,
                            e -> {
                                assertThat(e.getCode())
                                        .isEqualTo(WidgetStreakColorException.CODE_GAP);
                                assertThat(e.getMessageKey())
                                        .isEqualTo("error.widget_message.streak_color_gap");
                            });
            verify(repository, never()).deleteAllInBatch();
        }

        @Test
        @DisplayName("저장하면 위젯 메모를 비운다 — 커밋 뒤에도 한 번 더")
        void replaceEvictsMemo() {
            when(repository.findAllByOrderByMinDaysAsc())
                    .thenReturn(List.of(range(1, 7, GREEN)))
                    .thenReturn(List.of(range(1, 7, CYAN)))
                    .thenReturn(List.of(range(1, 7, CYAN)));
            when(repository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
            service.getRanges();

            TransactionSynchronizationManager.initSynchronization();
            service.replace(request(item(1, 7, CYAN)));
            // 커밋 전 다른 요청이 다시 메모해도
            assertThat(service.getRanges()).containsExactly(new WidgetStreakColorView(1, 7, CYAN));
            // 커밋 뒤 한 번 더 비워서 다음 조회가 DB 를 본다
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
            service.getRanges();

            verify(repository, times(3)).findAllByOrderByMinDaysAsc();
        }
    }
}
