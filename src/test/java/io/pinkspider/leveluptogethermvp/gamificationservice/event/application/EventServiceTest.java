package io.pinkspider.leveluptogethermvp.gamificationservice.event.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.gamificationservice.event.api.dto.EventResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.event.domain.entity.Event;
import io.pinkspider.leveluptogethermvp.gamificationservice.event.infrastructure.EventRepository;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private EventService eventService;

    private static final String LOCALE_KO = "ko";
    private static final String LOCALE_EN = "en";

    private Event createMockEvent(Long id, String name, LocalDateTime startAt, LocalDateTime endAt) {
        Event event = Event.builder()
            .name(name)
            .nameEn(name + " (EN)")
            .nameAr(name + " (AR)")
            .description("테스트 이벤트 설명")
            .descriptionEn("Test event description")
            .descriptionAr("وصف حدث الاختبار")
            .imageUrl("/uploads/events/test-image.png")
            .startAt(startAt)
            .endAt(endAt)
            .rewardTitleId(1L)
            .rewardTitleName("테스트 칭호")
            .isActive(true)
            .build();
        setEntityId(event, id);
        return event;
    }

    private void setEntityId(Event event, Long id) {
        try {
            Field idField = Event.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(event, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("getCurrentEvents 테스트")
    class GetCurrentEventsTest {

        @Test
        @DisplayName("현재 진행중인 이벤트 목록을 조회한다")
        void getCurrentEvents_success() {
            // given
            LocalDateTime now = LocalDateTime.now();
            Event event1 = createMockEvent(1L, "이벤트1", now.minusDays(5), now.plusDays(5));
            Event event2 = createMockEvent(2L, "이벤트2", now.minusDays(1), now.plusDays(10));

            when(eventRepository.findCurrentEvents(any(LocalDateTime.class)))
                .thenReturn(List.of(event1, event2));

            // when
            List<EventResponse> result = eventService.getCurrentEvents(LOCALE_KO);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getId()).isEqualTo(1L);
            assertThat(result.get(0).getName()).isEqualTo("이벤트1");
            assertThat(result.get(1).getId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("진행중인 이벤트가 없으면 빈 목록을 반환한다")
        void getCurrentEvents_empty() {
            // given
            when(eventRepository.findCurrentEvents(any(LocalDateTime.class)))
                .thenReturn(List.of());

            // when
            List<EventResponse> result = eventService.getCurrentEvents(LOCALE_KO);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("영어 locale로 조회하면 영어 이름을 반환한다")
        void getCurrentEvents_withEnglishLocale() {
            // given
            LocalDateTime now = LocalDateTime.now();
            Event event = createMockEvent(1L, "이벤트1", now.minusDays(5), now.plusDays(5));

            when(eventRepository.findCurrentEvents(any(LocalDateTime.class)))
                .thenReturn(List.of(event));

            // when
            List<EventResponse> result = eventService.getCurrentEvents(LOCALE_EN);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("이벤트1 (EN)");
        }
    }

    @Nested
    @DisplayName("getActiveOrUpcomingEvents 테스트")
    class GetActiveOrUpcomingEventsTest {

        @Test
        @DisplayName("활성 또는 예정된 이벤트 목록을 조회한다")
        void getActiveOrUpcomingEvents_success() {
            // given
            LocalDateTime now = LocalDateTime.now();
            Event currentEvent = createMockEvent(1L, "진행중 이벤트", now.minusDays(5), now.plusDays(5));
            Event upcomingEvent = createMockEvent(2L, "예정 이벤트", now.plusDays(1), now.plusDays(10));

            when(eventRepository.findActiveOrUpcomingEvents(any(LocalDateTime.class)))
                .thenReturn(List.of(currentEvent, upcomingEvent));

            // when
            List<EventResponse> result = eventService.getActiveOrUpcomingEvents(LOCALE_KO);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getName()).isEqualTo("진행중 이벤트");
            assertThat(result.get(1).getName()).isEqualTo("예정 이벤트");
        }

        @Test
        @DisplayName("활성 또는 예정된 이벤트가 없으면 빈 목록을 반환한다")
        void getActiveOrUpcomingEvents_empty() {
            // given
            when(eventRepository.findActiveOrUpcomingEvents(any(LocalDateTime.class)))
                .thenReturn(List.of());

            // when
            List<EventResponse> result = eventService.getActiveOrUpcomingEvents(LOCALE_KO);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getEvent 테스트")
    class GetEventTest {

        @Test
        @DisplayName("이벤트 상세를 조회한다")
        void getEvent_success() {
            // given
            LocalDateTime now = LocalDateTime.now();
            Event event = createMockEvent(1L, "테스트 이벤트", now.minusDays(5), now.plusDays(5));

            when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

            // when
            EventResponse result = eventService.getEvent(1L, LOCALE_KO);

            // then
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getName()).isEqualTo("테스트 이벤트");
            assertThat(result.getDescription()).isEqualTo("테스트 이벤트 설명");
            assertThat(result.getImageUrl()).isEqualTo("/uploads/events/test-image.png");
            assertThat(result.getRewardTitleId()).isEqualTo(1L);
            assertThat(result.getRewardTitleName()).isEqualTo("테스트 칭호");
            assertThat(result.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("존재하지 않는 이벤트 조회시 예외가 발생한다")
        void getEvent_notFound() {
            // given
            when(eventRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> eventService.getEvent(999L, LOCALE_KO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이벤트를 찾을 수 없습니다");
        }

        @Test
        @DisplayName("영어 locale로 조회하면 영어 이름과 설명을 반환한다")
        void getEvent_withEnglishLocale() {
            // given
            LocalDateTime now = LocalDateTime.now();
            Event event = createMockEvent(1L, "테스트 이벤트", now.minusDays(5), now.plusDays(5));

            when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

            // when
            EventResponse result = eventService.getEvent(1L, LOCALE_EN);

            // then
            assertThat(result.getName()).isEqualTo("테스트 이벤트 (EN)");
            assertThat(result.getDescription()).isEqualTo("Test event description");
        }

        @Test
        @DisplayName("이벤트 상태가 올바르게 반환된다 - 진행중")
        void getEvent_statusInProgress() {
            // given
            LocalDateTime now = LocalDateTime.now();
            Event event = createMockEvent(1L, "진행중 이벤트", now.minusDays(5), now.plusDays(5));

            when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

            // when
            EventResponse result = eventService.getEvent(1L, LOCALE_KO);

            // then
            assertThat(result.getStatus()).isEqualTo("IN_PROGRESS");
            assertThat(result.getStatusName()).isEqualTo("진행중");
        }

        @Test
        @DisplayName("이벤트 상태가 올바르게 반환된다 - 예정")
        void getEvent_statusScheduled() {
            // given
            LocalDateTime now = LocalDateTime.now();
            Event event = createMockEvent(1L, "예정 이벤트", now.plusDays(1), now.plusDays(10));

            when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

            // when
            EventResponse result = eventService.getEvent(1L, LOCALE_KO);

            // then
            assertThat(result.getStatus()).isEqualTo("SCHEDULED");
            assertThat(result.getStatusName()).isEqualTo("예정");
        }

        @Test
        @DisplayName("이벤트 상태가 올바르게 반환된다 - 종료")
        void getEvent_statusEnded() {
            // given
            LocalDateTime now = LocalDateTime.now();
            Event event = createMockEvent(1L, "종료된 이벤트", now.minusDays(10), now.minusDays(1));

            when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

            // when
            EventResponse result = eventService.getEvent(1L, LOCALE_KO);

            // then
            assertThat(result.getStatus()).isEqualTo("ENDED");
            assertThat(result.getStatusName()).isEqualTo("종료");
        }
    }
}
