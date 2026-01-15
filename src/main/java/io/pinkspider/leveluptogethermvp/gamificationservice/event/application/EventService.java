package io.pinkspider.leveluptogethermvp.gamificationservice.event.application;

import io.pinkspider.leveluptogethermvp.gamificationservice.event.api.dto.EventResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.event.domain.entity.Event;
import io.pinkspider.leveluptogethermvp.gamificationservice.event.infrastructure.EventRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
public class EventService {

    private final EventRepository eventRepository;

    /**
     * 현재 진행중인 이벤트 목록 조회
     */
    public List<EventResponse> getCurrentEvents(String locale) {
        return eventRepository.findCurrentEvents(LocalDateTime.now())
            .stream()
            .map(event -> EventResponse.from(event, locale))
            .toList();
    }

    /**
     * 현재 진행중 또는 예정된 이벤트 목록 조회 (Home 표시용)
     */
    public List<EventResponse> getActiveOrUpcomingEvents(String locale) {
        return eventRepository.findActiveOrUpcomingEvents(LocalDateTime.now())
            .stream()
            .map(event -> EventResponse.from(event, locale))
            .toList();
    }

    /**
     * 이벤트 상세 조회
     */
    public EventResponse getEvent(Long id, String locale) {
        Event event = eventRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("이벤트를 찾을 수 없습니다: " + id));
        return EventResponse.from(event, locale);
    }
}
