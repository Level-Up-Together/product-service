package io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.application.image.WidgetImageStorageService;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.dto.WidgetMessageListResponse;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.dto.WidgetMessageListResponse.WidgetMessageGapResponse;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.dto.WidgetMessageRequest;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.dto.WidgetMessageResponse;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.dto.WidgetMessageView;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.entity.WidgetMessage;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.enums.WidgetMessageCondition;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.infrastructure.WidgetMessageRepository;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
@DisplayName("WidgetMessageService — 위젯 문장 (LUT-775)")
class WidgetMessageServiceTest {

    @Mock WidgetMessageRepository repository;
    @Mock WidgetImageStorageService imageStorage;

    private final AtomicLong clock = new AtomicLong(1_000_000L);
    private WidgetMessageService service;

    @BeforeEach
    void setUp() {
        service = new WidgetMessageService(repository, imageStorage, 60, clock::get);
    }

    @AfterEach
    void clearSync() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static WidgetMessage message(
            long id, int start, int end, WidgetMessageCondition condition, boolean active) {
        WidgetMessage m =
                WidgetMessage.builder()
                        .startHour(start)
                        .endHour(end)
                        .condition(condition)
                        .textKo("문장" + id)
                        .textEn("text" + id)
                        .active(active)
                        .sortOrder((int) id)
                        .build();
        setId(m, id);
        return m;
    }

    private static WidgetMessageRequest request(Boolean active, Integer sortOrder) {
        return request(active, sortOrder, null);
    }

    private static WidgetMessageRequest request(
            Boolean active, Integer sortOrder, String backgroundImageUrl) {
        return new WidgetMessageRequest(
                22,
                2,
                WidgetMessageCondition.DONE,
                "  밤 문장 ",
                " ",
                "夜",
                null,
                active,
                sortOrder,
                backgroundImageUrl);
    }

    private static final String IMAGE = "https://cdn.test/widget/a.png";
    private static final String OTHER_IMAGE = "https://cdn.test/widget/b.png";

    private static WidgetMessage messageWithImage(long id, String imageUrl) {
        WidgetMessage m =
                WidgetMessage.builder()
                        .startHour(6)
                        .endHour(12)
                        .condition(WidgetMessageCondition.ALL)
                        .textKo("문장" + id)
                        .active(true)
                        .sortOrder((int) id)
                        .backgroundImageUrl(imageUrl)
                        .build();
        setId(m, id);
        return m;
    }

    @Nested
    @DisplayName("getActiveMessages — 위젯 조회")
    class GetActiveMessages {

        @Test
        @DisplayName("활성 문장을 요청 언어로 내려 준다")
        void localized() {
            when(repository.findByActiveTrueOrderByStartHourAscSortOrderAscIdAsc())
                    .thenReturn(List.of(message(1, 6, 12, WidgetMessageCondition.NOT_DONE, true)));

            List<WidgetMessageView> ko = service.getActiveMessages("ko");
            List<WidgetMessageView> ja = service.getActiveMessages("ja");

            assertThat(ko)
                    .containsExactly(
                            new WidgetMessageView(
                                    1L, 6, 12, WidgetMessageCondition.NOT_DONE, "문장1", null));
            assertThat(ja.get(0).text()).isEqualTo("text1");
        }

        @Test
        @DisplayName("메모 시간 안에서는 DB 를 다시 읽지 않고, 지나면 다시 읽는다")
        void memo() {
            when(repository.findByActiveTrueOrderByStartHourAscSortOrderAscIdAsc())
                    .thenReturn(List.of(message(1, 6, 12, WidgetMessageCondition.ALL, true)));

            service.getActiveMessages("en");
            clock.addAndGet(59_999);
            service.getActiveMessages("en");
            verify(repository, times(1)).findByActiveTrueOrderByStartHourAscSortOrderAscIdAsc();

            clock.addAndGet(1);
            service.getActiveMessages("en");
            verify(repository, times(2)).findByActiveTrueOrderByStartHourAscSortOrderAscIdAsc();
        }

        @Test
        @DisplayName("조회 실패 — 마지막 메모가 있으면 그것으로 응답한다")
        void failureUsesLastMemo() {
            when(repository.findByActiveTrueOrderByStartHourAscSortOrderAscIdAsc())
                    .thenReturn(List.of(message(1, 6, 12, WidgetMessageCondition.ALL, true)))
                    .thenThrow(new IllegalStateException("db down"));

            service.getActiveMessages("en");
            clock.addAndGet(60_000);

            assertThat(service.getActiveMessages("en")).hasSize(1);
        }

        @Test
        @DisplayName("조회 실패 — 메모가 없으면 오류를 그대로 낸다")
        void failureWithoutMemo() {
            when(repository.findByActiveTrueOrderByStartHourAscSortOrderAscIdAsc())
                    .thenThrow(new IllegalStateException("db down"));

            assertThatThrownBy(() -> service.getActiveMessages("en"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("어드민 변경 뒤에는 메모를 비워 바로 다시 읽는다")
        void evictedByWrite() {
            WidgetMessage m = message(1, 6, 12, WidgetMessageCondition.ALL, true);
            when(repository.findByActiveTrueOrderByStartHourAscSortOrderAscIdAsc())
                    .thenReturn(List.of(m));
            when(repository.findById(1L)).thenReturn(Optional.of(m));

            service.getActiveMessages("en");
            service.toggleActive(1L);
            service.getActiveMessages("en");

            verify(repository, times(2)).findByActiveTrueOrderByStartHourAscSortOrderAscIdAsc();
        }

        @Test
        @DisplayName("트랜잭션 안의 변경은 커밋 뒤에도 메모를 한 번 더 비운다")
        void evictedAfterCommit() {
            WidgetMessage m = message(1, 6, 12, WidgetMessageCondition.ALL, true);
            when(repository.findByActiveTrueOrderByStartHourAscSortOrderAscIdAsc())
                    .thenReturn(List.of(m));
            when(repository.findById(1L)).thenReturn(Optional.of(m));
            TransactionSynchronizationManager.initSynchronization();

            service.toggleActive(1L);
            // 커밋 전에 다른 요청이 옛 값을 다시 메모했다고 치자
            service.getActiveMessages("en");
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
            service.getActiveMessages("en");

            verify(repository, times(2)).findByActiveTrueOrderByStartHourAscSortOrderAscIdAsc();
        }
    }

    @Nested
    @DisplayName("list — 어드민 목록")
    class ListMessages {

        @Test
        @DisplayName("조건으로 목록을 거르고, 빈 시간대는 활성 문장 전체 기준으로 낸다")
        void filterAndGaps() {
            when(repository.findAllByOrderByStartHourAscSortOrderAscIdAsc())
                    .thenReturn(
                            List.of(
                                    message(1, 0, 12, WidgetMessageCondition.NOT_DONE, true),
                                    message(2, 12, 24, WidgetMessageCondition.ALL, true),
                                    message(3, 0, 6, WidgetMessageCondition.DONE, true),
                                    message(4, 6, 12, WidgetMessageCondition.DONE, false)));

            WidgetMessageListResponse response = service.list(WidgetMessageCondition.DONE);

            assertThat(response.messages())
                    .extracting(WidgetMessageResponse::id)
                    .containsExactly(3L, 4L);
            // 완료 전: 0~12(NOT_DONE) + 12~24(ALL) = 빈 곳 없음
            // 완료 후: 0~6(DONE) + 12~24(ALL), 6~12 는 비활성 → 6~12 빈 구간
            assertThat(response.gaps())
                    .containsExactly(
                            new WidgetMessageGapResponse(WidgetMessageCondition.DONE, 6, 12));
        }

        @Test
        @DisplayName("조건이 없으면 전체 목록")
        void all() {
            when(repository.findAllByOrderByStartHourAscSortOrderAscIdAsc())
                    .thenReturn(List.of(message(1, 0, 24, WidgetMessageCondition.ALL, true)));

            WidgetMessageListResponse response = service.list(null);

            assertThat(response.messages()).hasSize(1);
            assertThat(response.gaps()).isEmpty();
        }

        @Test
        @DisplayName("문장이 없으면 두 상태 모두 하루 전체가 빈 구간")
        void empty() {
            when(repository.findAllByOrderByStartHourAscSortOrderAscIdAsc()).thenReturn(List.of());

            assertThat(service.list(null).gaps())
                    .containsExactly(
                            new WidgetMessageGapResponse(WidgetMessageCondition.NOT_DONE, 0, 24),
                            new WidgetMessageGapResponse(WidgetMessageCondition.DONE, 0, 24));
        }
    }

    @Nested
    @DisplayName("작성·수정·삭제")
    class Write {

        @Test
        @DisplayName("작성 — ko 는 다듬고, 빈 선택 언어는 null, 활성·순서 기본값은 켬·0")
        void create() {
            when(repository.save(any(WidgetMessage.class))).thenAnswer(inv -> inv.getArgument(0));

            WidgetMessageResponse response = service.create(request(null, null));

            ArgumentCaptor<WidgetMessage> captor = ArgumentCaptor.forClass(WidgetMessage.class);
            verify(repository).save(captor.capture());
            WidgetMessage saved = captor.getValue();
            assertThat(saved.getTextKo()).isEqualTo("밤 문장");
            assertThat(saved.getTextEn()).isNull();
            assertThat(saved.getTextJa()).isEqualTo("夜");
            assertThat(saved.getTextAr()).isNull();
            assertThat(saved.isActive()).isTrue();
            assertThat(saved.getSortOrder()).isZero();
            assertThat(response.startHour()).isEqualTo(22);
            assertThat(response.endHour()).isEqualTo(2);
        }

        @Test
        @DisplayName("작성 — 비활성·순서를 주면 그대로")
        void createInactive() {
            when(repository.save(any(WidgetMessage.class))).thenAnswer(inv -> inv.getArgument(0));

            WidgetMessageResponse response = service.create(request(false, 7));

            assertThat(response.active()).isFalse();
            assertThat(response.sortOrder()).isEqualTo(7);
        }

        @Test
        @DisplayName("수정 — 활성·순서를 비우면 그대로 둔다")
        void updateKeeps() {
            WidgetMessage m = message(5, 6, 12, WidgetMessageCondition.NOT_DONE, false);
            when(repository.findById(5L)).thenReturn(Optional.of(m));

            WidgetMessageResponse response = service.update(5L, request(null, null));

            assertThat(response.active()).isFalse();
            assertThat(response.sortOrder()).isEqualTo(5);
            assertThat(response.condition()).isEqualTo(WidgetMessageCondition.DONE);
            assertThat(response.textKo()).isEqualTo("밤 문장");
            assertThat(response.textEn()).isNull();
        }

        @Test
        @DisplayName("수정 — 활성·순서를 주면 바꾼다")
        void updateChanges() {
            WidgetMessage m = message(5, 6, 12, WidgetMessageCondition.NOT_DONE, false);
            when(repository.findById(5L)).thenReturn(Optional.of(m));

            WidgetMessageResponse response = service.update(5L, request(true, 9));

            assertThat(response.active()).isTrue();
            assertThat(response.sortOrder()).isEqualTo(9);
        }

        @Test
        @DisplayName("조회 — 있으면 응답")
        void get() {
            when(repository.findById(5L))
                    .thenReturn(
                            Optional.of(message(5, 6, 12, WidgetMessageCondition.NOT_DONE, true)));

            assertThat(service.get(5L).id()).isEqualTo(5L);
        }

        @Test
        @DisplayName("활성 토글")
        void toggle() {
            WidgetMessage m = message(5, 6, 12, WidgetMessageCondition.NOT_DONE, true);
            when(repository.findById(5L)).thenReturn(Optional.of(m));

            assertThat(service.toggleActive(5L).active()).isFalse();
        }

        @Test
        @DisplayName("삭제")
        void delete() {
            WidgetMessage m = message(5, 6, 12, WidgetMessageCondition.NOT_DONE, true);
            when(repository.findById(5L)).thenReturn(Optional.of(m));

            service.delete(5L);

            verify(repository).delete(m);
        }

        @Test
        @DisplayName("없는 문장은 WidgetMessageNotFoundException")
        void notFound() {
            when(repository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.get(99L))
                    .isInstanceOf(WidgetMessageNotFoundException.class);
            assertThatThrownBy(() -> service.update(99L, request(null, null)))
                    .isInstanceOf(WidgetMessageNotFoundException.class);
            assertThatThrownBy(() -> service.toggleActive(99L))
                    .isInstanceOf(WidgetMessageNotFoundException.class);
            assertThatThrownBy(() -> service.delete(99L))
                    .isInstanceOf(WidgetMessageNotFoundException.class);
            verify(repository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("배경 이미지 (LUT-797)")
    class BackgroundImage {

        @Test
        @DisplayName("위젯 조회 — 배경 이미지 주소를 함께 내려 준다(없으면 null)")
        void viewCarriesImage() {
            when(repository.findByActiveTrueOrderByStartHourAscSortOrderAscIdAsc())
                    .thenReturn(List.of(messageWithImage(1, IMAGE), messageWithImage(2, null)));

            List<WidgetMessageView> views = service.getActiveMessages("ko");

            assertThat(views)
                    .extracting(WidgetMessageView::backgroundImageUrl)
                    .containsExactly(IMAGE, null);
        }

        @Test
        @DisplayName("작성 — 우리 저장소가 발급한 주소면 다듬어 저장한다")
        void createWithOwnedImage() {
            when(imageStorage.isOwned(IMAGE)).thenReturn(true);
            when(repository.save(any(WidgetMessage.class))).thenAnswer(inv -> inv.getArgument(0));

            WidgetMessageResponse response = service.create(request(null, null, " " + IMAGE + " "));

            assertThat(response.backgroundImageUrl()).isEqualTo(IMAGE);
        }

        @Test
        @DisplayName("작성 — 비우거나 빈 문자열이면 배경 없음")
        void createWithoutImage() {
            when(repository.save(any(WidgetMessage.class))).thenAnswer(inv -> inv.getArgument(0));

            assertThat(service.create(request(null, null, null)).backgroundImageUrl()).isNull();
            assertThat(service.create(request(null, null, "  ")).backgroundImageUrl()).isNull();
            verify(imageStorage, never()).isOwned(any());
        }

        @Test
        @DisplayName("작성·수정 — 발급하지 않은 주소는 080205 로 거절하고 저장하지 않는다")
        void rejectsForeignImage() {
            when(imageStorage.isOwned("https://evil.test/x.png")).thenReturn(false);
            WidgetMessage m = messageWithImage(5, IMAGE);
            when(repository.findById(5L)).thenReturn(Optional.of(m));

            assertThatThrownBy(() -> service.create(request(null, null, "https://evil.test/x.png")))
                    .isInstanceOfSatisfying(
                            WidgetMessageImageException.class,
                            e ->
                                    assertThat(e.getCode())
                                            .isEqualTo(WidgetMessageImageException.CODE_NOT_OWNED));
            assertThatThrownBy(
                            () ->
                                    service.update(
                                            5L, request(null, null, "https://evil.test/x.png")))
                    .isInstanceOf(WidgetMessageImageException.class);

            verify(repository, never()).save(any());
            assertThat(m.getBackgroundImageUrl()).isEqualTo(IMAGE);
            verify(imageStorage, never()).delete(any());
        }

        @Test
        @DisplayName("수정 — 필드를 비우면(null) 그대로 둔다 (구 어드민 호환)")
        void updateKeepsImageWhenNull() {
            WidgetMessage m = messageWithImage(5, IMAGE);
            when(repository.findById(5L)).thenReturn(Optional.of(m));

            WidgetMessageResponse response = service.update(5L, request(null, null, null));

            assertThat(response.backgroundImageUrl()).isEqualTo(IMAGE);
            verify(imageStorage, never()).delete(any());
        }

        @Test
        @DisplayName("수정 — 같은 주소를 다시 보내면 파일을 지우지 않는다")
        void updateSameImage() {
            WidgetMessage m = messageWithImage(5, IMAGE);
            when(repository.findById(5L)).thenReturn(Optional.of(m));
            when(imageStorage.isOwned(IMAGE)).thenReturn(true);

            service.update(5L, request(null, null, IMAGE));

            verify(imageStorage, never()).delete(any());
        }

        @Test
        @DisplayName("수정 — 빈 문자열이면 지우고, 어느 문장도 안 쓰는 파일은 저장소에서 지운다")
        void updateRemovesImage() {
            WidgetMessage m = messageWithImage(5, IMAGE);
            when(repository.findById(5L)).thenReturn(Optional.of(m));
            when(repository.countByBackgroundImageUrl(IMAGE)).thenReturn(0L);

            WidgetMessageResponse response = service.update(5L, request(null, null, ""));

            assertThat(response.backgroundImageUrl()).isNull();
            verify(imageStorage).delete(IMAGE);
        }

        @Test
        @DisplayName("수정 — 다른 이미지로 바꾸면 옛 파일을 지운다. 다른 문장이 쓰고 있으면 둔다")
        void updateReplacesImage() {
            WidgetMessage m = messageWithImage(5, IMAGE);
            when(repository.findById(5L)).thenReturn(Optional.of(m));
            when(imageStorage.isOwned(OTHER_IMAGE)).thenReturn(true);
            when(repository.countByBackgroundImageUrl(IMAGE)).thenReturn(1L);

            WidgetMessageResponse response = service.update(5L, request(null, null, OTHER_IMAGE));

            assertThat(response.backgroundImageUrl()).isEqualTo(OTHER_IMAGE);
            verify(imageStorage, never()).delete(any());
        }

        @Test
        @DisplayName("트랜잭션 안에서는 커밋 뒤에만 파일을 지운다 (롤백되면 지우지 않는다)")
        void deletesAfterCommitOnly() {
            WidgetMessage m = messageWithImage(5, IMAGE);
            when(repository.findById(5L)).thenReturn(Optional.of(m));
            when(repository.countByBackgroundImageUrl(IMAGE)).thenReturn(0L);
            TransactionSynchronizationManager.initSynchronization();

            service.update(5L, request(null, null, ""));
            verify(imageStorage, never()).delete(any());

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
            verify(imageStorage).delete(IMAGE);
        }

        @Test
        @DisplayName("문장 삭제 — 그 문장만 쓰던 배경 파일도 지운다. 배경이 없으면 저장소를 부르지 않는다")
        void deleteMessageRemovesImage() {
            WidgetMessage withImage = messageWithImage(5, IMAGE);
            WidgetMessage plain = messageWithImage(6, null);
            when(repository.findById(5L)).thenReturn(Optional.of(withImage));
            when(repository.findById(6L)).thenReturn(Optional.of(plain));
            when(repository.countByBackgroundImageUrl(IMAGE)).thenReturn(0L);

            service.delete(5L);
            service.delete(6L);

            verify(imageStorage, times(1)).delete(IMAGE);
            verify(repository, times(1)).countByBackgroundImageUrl(any());
        }

        @Test
        @DisplayName("파일 정리가 실패해도 문장 변경은 성공한다")
        void cleanupFailureIsSwallowed() {
            WidgetMessage m = messageWithImage(5, IMAGE);
            when(repository.findById(5L)).thenReturn(Optional.of(m));
            when(repository.countByBackgroundImageUrl(IMAGE)).thenReturn(0L);
            doThrow(new IllegalStateException("s3 down")).when(imageStorage).delete(IMAGE);

            WidgetMessageResponse response = service.update(5L, request(null, null, ""));

            assertThat(response.backgroundImageUrl()).isNull();
        }
    }
}
