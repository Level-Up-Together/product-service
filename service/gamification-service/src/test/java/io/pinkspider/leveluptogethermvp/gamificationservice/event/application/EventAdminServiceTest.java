package io.pinkspider.leveluptogethermvp.gamificationservice.event.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Title;
import io.pinkspider.leveluptogethermvp.gamificationservice.event.domain.dto.EventAdminPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.event.domain.dto.EventAdminRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.event.domain.dto.EventAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.event.domain.entity.Event;
import io.pinkspider.leveluptogethermvp.gamificationservice.event.infrastructure.EventRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.TitleRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class EventAdminServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private TitleRepository titleRepository;

    @InjectMocks
    private EventAdminService eventAdminService;

    private Event createMockEvent(Long id, String name, LocalDateTime startAt, LocalDateTime endAt) {
        Event event = Event.builder()
            .name(name)
            .nameEn(name + " (EN)")
            .nameAr(name + " (AR)")
            .description("이벤트 설명")
            .descriptionEn("Event description")
            .descriptionAr("وصف الحدث")
            .imageUrl("https://cdn.example.com/events/test.png")
            .startAt(startAt)
            .endAt(endAt)
            .rewardTitleId(null)
            .rewardTitleName(null)
            .isActive(true)
            .build();
        setId(event, id);
        return event;
    }

    private EventAdminRequest createEventRequest(
            String name, LocalDateTime startAt, LocalDateTime endAt,
            Long rewardTitleId, Boolean isActive) {
        return new EventAdminRequest(
            name, name + " EN", name + " AR",
            "이벤트 설명", "Event description", "وصف الحدث",
            "https://cdn.example.com/events/test.png",
            startAt, endAt, rewardTitleId, isActive
        );
    }

    @Nested
    @DisplayName("searchEvents 테스트")
    class SearchEventsTest {

        @Test
        @DisplayName("키워드가 있으면 키워드로 이벤트를 검색한다")
        void searchEvents_withKeyword() {
            // given
            LocalDateTime now = LocalDateTime.now();
            Event event = createMockEvent(1L, "테스트 이벤트", now.minusDays(1), now.plusDays(1));
            Pageable pageable = PageRequest.of(0, 10);
            Page<Event> page = new PageImpl<>(List.of(event), pageable, 1);

            when(eventRepository.searchByKeyword(eq("테스트"), eq(pageable))).thenReturn(page);

            // when
            EventAdminPageResponse result = eventAdminService.searchEvents("테스트", pageable);

            // then
            assertThat(result).isNotNull();
            verify(eventRepository).searchByKeyword("테스트", pageable);
        }

        @Test
        @DisplayName("키워드가 없으면 전체 이벤트를 시작일시 내림차순으로 조회한다")
        void searchEvents_withoutKeyword() {
            // given
            LocalDateTime now = LocalDateTime.now();
            Event event = createMockEvent(1L, "이벤트", now.minusDays(1), now.plusDays(1));
            Pageable pageable = PageRequest.of(0, 10);
            Page<Event> page = new PageImpl<>(List.of(event), pageable, 1);

            when(eventRepository.findAllByOrderByStartAtDesc(pageable)).thenReturn(page);

            // when
            EventAdminPageResponse result = eventAdminService.searchEvents(null, pageable);

            // then
            assertThat(result).isNotNull();
            verify(eventRepository).findAllByOrderByStartAtDesc(pageable);
        }

        @Test
        @DisplayName("키워드가 공백이면 전체 이벤트를 조회한다")
        void searchEvents_blankKeyword() {
            // given
            LocalDateTime now = LocalDateTime.now();
            Pageable pageable = PageRequest.of(0, 10);
            Page<Event> page = new PageImpl<>(List.of(), pageable, 0);

            when(eventRepository.findAllByOrderByStartAtDesc(pageable)).thenReturn(page);

            // when
            EventAdminPageResponse result = eventAdminService.searchEvents("   ", pageable);

            // then
            assertThat(result).isNotNull();
            verify(eventRepository).findAllByOrderByStartAtDesc(pageable);
        }
    }

    @Nested
    @DisplayName("getActiveEvents 테스트")
    class GetActiveEventsTest {

        @Test
        @DisplayName("활성화된 이벤트 목록을 조회한다")
        void getActiveEvents_success() {
            // given
            LocalDateTime now = LocalDateTime.now();
            Event event1 = createMockEvent(1L, "이벤트1", now.minusDays(1), now.plusDays(1));
            Event event2 = createMockEvent(2L, "이벤트2", now.minusDays(5), now.plusDays(5));

            when(eventRepository.findByIsActiveTrueOrderByStartAtDesc())
                .thenReturn(List.of(event1, event2));

            // when
            List<EventAdminResponse> result = eventAdminService.getActiveEvents();

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getId()).isEqualTo(1L);
            assertThat(result.get(0).getName()).isEqualTo("이벤트1");
            assertThat(result.get(0).getIsActive()).isTrue();
            verify(eventRepository).findByIsActiveTrueOrderByStartAtDesc();
        }

        @Test
        @DisplayName("활성 이벤트가 없으면 빈 목록을 반환한다")
        void getActiveEvents_empty() {
            // given
            when(eventRepository.findByIsActiveTrueOrderByStartAtDesc()).thenReturn(List.of());

            // when
            List<EventAdminResponse> result = eventAdminService.getActiveEvents();

            // then
            assertThat(result).isEmpty();
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
            Event event = createMockEvent(1L, "진행중 이벤트", now.minusDays(1), now.plusDays(1));

            when(eventRepository.findCurrentEvents(any(LocalDateTime.class)))
                .thenReturn(List.of(event));

            // when
            List<EventAdminResponse> result = eventAdminService.getCurrentEvents();

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("진행중 이벤트");
            verify(eventRepository).findCurrentEvents(any(LocalDateTime.class));
        }

        @Test
        @DisplayName("진행중인 이벤트가 없으면 빈 목록을 반환한다")
        void getCurrentEvents_empty() {
            // given
            when(eventRepository.findCurrentEvents(any(LocalDateTime.class))).thenReturn(List.of());

            // when
            List<EventAdminResponse> result = eventAdminService.getCurrentEvents();

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getEvent 테스트")
    class GetEventTest {

        @Test
        @DisplayName("ID로 이벤트를 조회한다")
        void getEvent_success() {
            // given
            LocalDateTime now = LocalDateTime.now();
            Event event = createMockEvent(1L, "테스트 이벤트", now.minusDays(1), now.plusDays(1));

            when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

            // when
            EventAdminResponse result = eventAdminService.getEvent(1L);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getName()).isEqualTo("테스트 이벤트");
            assertThat(result.getStatus()).isEqualTo("IN_PROGRESS");
        }

        @Test
        @DisplayName("존재하지 않는 이벤트 조회 시 예외를 발생시킨다")
        void getEvent_notFound_throwsException() {
            // given
            when(eventRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> eventAdminService.getEvent(999L))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("createEvent 테스트")
    class CreateEventTest {

        @Test
        @DisplayName("이벤트를 생성한다")
        void createEvent_success() {
            // given
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startAt = now.plusDays(1);
            LocalDateTime endAt = now.plusDays(10);
            EventAdminRequest request = createEventRequest("새 이벤트", startAt, endAt, null, true);
            Event savedEvent = createMockEvent(1L, "새 이벤트", startAt, endAt);

            when(eventRepository.save(any(Event.class))).thenReturn(savedEvent);

            // when
            EventAdminResponse result = eventAdminService.createEvent(request);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("새 이벤트");
            verify(eventRepository).save(any(Event.class));
        }

        @Test
        @DisplayName("보상 칭호 ID가 있으면 칭호명을 조회해서 설정한다")
        void createEvent_withRewardTitle() {
            // given
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startAt = now.plusDays(1);
            LocalDateTime endAt = now.plusDays(10);
            EventAdminRequest request = createEventRequest("새 이벤트", startAt, endAt, 5L, true);

            Title title = Title.builder().name("황금 칭호").build();
            setId(title, 5L);

            Event savedEvent = Event.builder()
                .name("새 이벤트")
                .startAt(startAt)
                .endAt(endAt)
                .rewardTitleId(5L)
                .rewardTitleName("황금 칭호")
                .isActive(true)
                .build();
            setId(savedEvent, 1L);

            when(titleRepository.findById(5L)).thenReturn(Optional.of(title));
            when(eventRepository.save(any(Event.class))).thenReturn(savedEvent);

            // when
            EventAdminResponse result = eventAdminService.createEvent(request);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getRewardTitleId()).isEqualTo(5L);
            assertThat(result.getRewardTitleName()).isEqualTo("황금 칭호");
            verify(titleRepository).findById(5L);
        }

        @Test
        @DisplayName("종료 일시가 시작 일시 이전이면 예외를 발생시킨다")
        void createEvent_endBeforeStart_throwsException() {
            // given
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startAt = now.plusDays(5);
            LocalDateTime endAt = now.plusDays(1);  // 시작보다 이전
            EventAdminRequest request = createEventRequest("잘못된 이벤트", startAt, endAt, null, true);

            // when & then
            assertThatThrownBy(() -> eventAdminService.createEvent(request))
                .isInstanceOf(CustomException.class);
        }

        @Test
        @DisplayName("종료 일시가 시작 일시와 같으면 예외를 발생시킨다")
        void createEvent_endEqualsStart_throwsException() {
            // given
            LocalDateTime now = LocalDateTime.now().plusDays(1);
            EventAdminRequest request = createEventRequest("잘못된 이벤트", now, now, null, true);

            // when & then
            assertThatThrownBy(() -> eventAdminService.createEvent(request))
                .isInstanceOf(CustomException.class);
        }

        @Test
        @DisplayName("isActive가 null이면 기본값 true로 설정된다")
        void createEvent_nullIsActive_defaultTrue() {
            // given
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startAt = now.plusDays(1);
            LocalDateTime endAt = now.plusDays(10);
            EventAdminRequest request = createEventRequest("새 이벤트", startAt, endAt, null, null);
            Event savedEvent = createMockEvent(1L, "새 이벤트", startAt, endAt);

            when(eventRepository.save(any(Event.class))).thenReturn(savedEvent);

            // when
            EventAdminResponse result = eventAdminService.createEvent(request);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("존재하지 않는 칭호 ID를 설정해도 칭호명은 null로 저장된다")
        void createEvent_titleNotFound_rewardTitleNameNull() {
            // given
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startAt = now.plusDays(1);
            LocalDateTime endAt = now.plusDays(10);
            EventAdminRequest request = createEventRequest("새 이벤트", startAt, endAt, 999L, true);

            Event savedEvent = createMockEvent(1L, "새 이벤트", startAt, endAt);

            when(titleRepository.findById(999L)).thenReturn(Optional.empty());
            when(eventRepository.save(any(Event.class))).thenReturn(savedEvent);

            // when
            EventAdminResponse result = eventAdminService.createEvent(request);

            // then
            assertThat(result).isNotNull();
            verify(titleRepository).findById(999L);
        }
    }

    @Nested
    @DisplayName("updateEvent 테스트")
    class UpdateEventTest {

        @Test
        @DisplayName("이벤트를 수정한다")
        void updateEvent_success() {
            // given
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startAt = now.plusDays(1);
            LocalDateTime endAt = now.plusDays(10);
            Event existingEvent = createMockEvent(1L, "기존 이벤트", startAt, endAt);
            EventAdminRequest request = createEventRequest("수정된 이벤트", startAt, endAt, null, true);

            when(eventRepository.findById(1L)).thenReturn(Optional.of(existingEvent));
            when(eventRepository.save(any(Event.class))).thenReturn(existingEvent);

            // when
            EventAdminResponse result = eventAdminService.updateEvent(1L, request);

            // then
            assertThat(result).isNotNull();
            verify(eventRepository).findById(1L);
            verify(eventRepository).save(existingEvent);
        }

        @Test
        @DisplayName("수정 시 종료 일시가 시작 일시 이전이면 예외를 발생시킨다")
        void updateEvent_invalidDates_throwsException() {
            // given
            LocalDateTime now = LocalDateTime.now();
            Event existingEvent = createMockEvent(
                1L, "기존 이벤트", now.plusDays(1), now.plusDays(10));
            EventAdminRequest request = createEventRequest(
                "수정된 이벤트", now.plusDays(10), now.plusDays(1), null, true);

            when(eventRepository.findById(1L)).thenReturn(Optional.of(existingEvent));

            // when & then
            assertThatThrownBy(() -> eventAdminService.updateEvent(1L, request))
                .isInstanceOf(CustomException.class);
        }

        @Test
        @DisplayName("존재하지 않는 이벤트 수정 시 예외를 발생시킨다")
        void updateEvent_notFound_throwsException() {
            // given
            LocalDateTime now = LocalDateTime.now();
            EventAdminRequest request = createEventRequest(
                "이벤트", now.plusDays(1), now.plusDays(10), null, true);

            when(eventRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> eventAdminService.updateEvent(999L, request))
                .isInstanceOf(CustomException.class);
        }

        @Test
        @DisplayName("isActive가 null이면 기존 값을 유지한다")
        void updateEvent_nullIsActive_keepExistingValue() {
            // given
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startAt = now.plusDays(1);
            LocalDateTime endAt = now.plusDays(10);
            Event existingEvent = createMockEvent(1L, "기존 이벤트", startAt, endAt);
            existingEvent.setIsActive(false);  // 비활성화 상태
            EventAdminRequest request = createEventRequest(
                "수정된 이벤트", startAt, endAt, null, null);  // isActive null

            when(eventRepository.findById(1L)).thenReturn(Optional.of(existingEvent));
            when(eventRepository.save(any(Event.class))).thenReturn(existingEvent);

            // when
            eventAdminService.updateEvent(1L, request);

            // then - isActive 변경 없이 기존 false 유지
            assertThat(existingEvent.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("보상 칭호 ID가 있으면 칭호명을 업데이트한다")
        void updateEvent_withRewardTitle_updatesTitleName() {
            // given
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startAt = now.plusDays(1);
            LocalDateTime endAt = now.plusDays(10);
            Event existingEvent = createMockEvent(1L, "기존 이벤트", startAt, endAt);
            EventAdminRequest request = createEventRequest(
                "수정된 이벤트", startAt, endAt, 7L, true);

            Title title = Title.builder().name("전설의 칭호").build();
            setId(title, 7L);

            when(eventRepository.findById(1L)).thenReturn(Optional.of(existingEvent));
            when(titleRepository.findById(7L)).thenReturn(Optional.of(title));
            when(eventRepository.save(any(Event.class))).thenReturn(existingEvent);

            // when
            eventAdminService.updateEvent(1L, request);

            // then
            assertThat(existingEvent.getRewardTitleName()).isEqualTo("전설의 칭호");
            verify(titleRepository).findById(7L);
        }
    }

    @Nested
    @DisplayName("deleteEvent 테스트")
    class DeleteEventTest {

        @Test
        @DisplayName("이벤트를 삭제한다")
        void deleteEvent_success() {
            // given
            LocalDateTime now = LocalDateTime.now();
            Event event = createMockEvent(1L, "삭제할 이벤트", now.plusDays(1), now.plusDays(10));

            when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

            // when
            eventAdminService.deleteEvent(1L);

            // then
            verify(eventRepository).findById(1L);
            verify(eventRepository).delete(event);
        }

        @Test
        @DisplayName("존재하지 않는 이벤트 삭제 시 예외를 발생시킨다")
        void deleteEvent_notFound_throwsException() {
            // given
            when(eventRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> eventAdminService.deleteEvent(999L))
                .isInstanceOf(CustomException.class);
        }
    }
}
