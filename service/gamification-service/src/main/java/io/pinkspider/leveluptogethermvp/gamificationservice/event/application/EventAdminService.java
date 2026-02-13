package io.pinkspider.leveluptogethermvp.gamificationservice.event.application;

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
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(transactionManager = "gamificationTransactionManager")
public class EventAdminService {

    private final EventRepository eventRepository;
    private final TitleRepository titleRepository;

    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public EventAdminPageResponse searchEvents(String keyword, Pageable pageable) {
        Page<EventAdminResponse> page;
        if (keyword != null && !keyword.isBlank()) {
            page = eventRepository.searchByKeyword(keyword, pageable)
                .map(EventAdminResponse::from);
        } else {
            page = eventRepository.findAllByOrderByStartAtDesc(pageable)
                .map(EventAdminResponse::from);
        }
        return EventAdminPageResponse.from(page);
    }

    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public List<EventAdminResponse> getActiveEvents() {
        return eventRepository.findByIsActiveTrueOrderByStartAtDesc().stream()
            .map(EventAdminResponse::from)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public List<EventAdminResponse> getCurrentEvents() {
        return eventRepository.findCurrentEvents(LocalDateTime.now()).stream()
            .map(EventAdminResponse::from)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public EventAdminResponse getEvent(Long id) {
        Event event = eventRepository.findById(id)
            .orElseThrow(() -> new CustomException("120101", "이벤트를 찾을 수 없습니다: " + id));
        return EventAdminResponse.from(event);
    }

    public EventAdminResponse createEvent(EventAdminRequest request) {
        validateEventDates(request.startAt(), request.endAt());

        String rewardTitleName = resolveRewardTitleName(request.rewardTitleId());

        Event event = Event.builder()
            .name(request.name())
            .nameEn(request.nameEn())
            .nameAr(request.nameAr())
            .description(request.description())
            .descriptionEn(request.descriptionEn())
            .descriptionAr(request.descriptionAr())
            .imageUrl(request.imageUrl())
            .startAt(request.startAt())
            .endAt(request.endAt())
            .rewardTitleId(request.rewardTitleId())
            .rewardTitleName(rewardTitleName)
            .isActive(request.isActive() != null ? request.isActive() : true)
            .build();

        Event saved = eventRepository.save(event);
        log.info("이벤트 생성: {} (ID: {})", request.name(), saved.getId());
        return EventAdminResponse.from(saved);
    }

    public EventAdminResponse updateEvent(Long id, EventAdminRequest request) {
        Event event = eventRepository.findById(id)
            .orElseThrow(() -> new CustomException("120101", "이벤트를 찾을 수 없습니다: " + id));

        validateEventDates(request.startAt(), request.endAt());

        String rewardTitleName = resolveRewardTitleName(request.rewardTitleId());

        event.setName(request.name());
        event.setNameEn(request.nameEn());
        event.setNameAr(request.nameAr());
        event.setDescription(request.description());
        event.setDescriptionEn(request.descriptionEn());
        event.setDescriptionAr(request.descriptionAr());
        event.setImageUrl(request.imageUrl());
        event.setStartAt(request.startAt());
        event.setEndAt(request.endAt());
        event.setRewardTitleId(request.rewardTitleId());
        event.setRewardTitleName(rewardTitleName);
        if (request.isActive() != null) {
            event.setIsActive(request.isActive());
        }

        Event updated = eventRepository.save(event);
        log.info("이벤트 수정: {} (ID: {})", request.name(), id);
        return EventAdminResponse.from(updated);
    }

    public void deleteEvent(Long id) {
        Event event = eventRepository.findById(id)
            .orElseThrow(() -> new CustomException("120101", "이벤트를 찾을 수 없습니다: " + id));
        log.info("이벤트 삭제: {} (ID: {})", event.getName(), id);
        eventRepository.delete(event);
    }

    private void validateEventDates(LocalDateTime startAt, LocalDateTime endAt) {
        if (endAt.isBefore(startAt) || endAt.isEqual(startAt)) {
            throw new CustomException("120102", "종료 일시는 시작 일시 이후여야 합니다.");
        }
    }

    private String resolveRewardTitleName(Long rewardTitleId) {
        if (rewardTitleId == null) {
            return null;
        }
        return titleRepository.findById(rewardTitleId)
            .map(Title::getName)
            .orElse(null);
    }
}
