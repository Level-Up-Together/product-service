package io.pinkspider.leveluptogethermvp.towerservice.application;

import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.leveluptogethermvp.profanity.domain.enums.ProfanitySeverity;
import io.pinkspider.leveluptogethermvp.towerservice.application.chat.TowerChatGuard;
import io.pinkspider.leveluptogethermvp.towerservice.application.chat.TowerChatHistoryStore;
import io.pinkspider.leveluptogethermvp.towerservice.application.chat.TowerChatLogWriter;
import io.pinkspider.leveluptogethermvp.towerservice.application.chat.TowerChatModerationService;
import io.pinkspider.leveluptogethermvp.towerservice.application.chat.TowerChatModerationService.ModerationResult;
import io.pinkspider.leveluptogethermvp.towerservice.application.chat.TowerChatProperties;
import io.pinkspider.leveluptogethermvp.towerservice.application.chat.TowerCheerWindowStore;
import io.pinkspider.leveluptogethermvp.towerservice.application.chat.TowerVisitorContentPolicy;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerChatException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerChatHistoryResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerChatMessage;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerInstanceEvent;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerReactionPresetsResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerReactionResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerChatLog;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerOccupant;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerOccupantRepository;
import io.pinkspider.leveluptogethermvp.towerservice.presence.TowerPresence;
import io.pinkspider.leveluptogethermvp.towerservice.presence.TowerPresenceStore;
import io.pinkspider.leveluptogethermvp.towerservice.realtime.TowerRealtimePublisher;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * LUT-633: 탑 채팅·리액션. 전송은 REST(거부 사유 코드를 바로 받는다), 전파는 같은 인스턴스 토픽의 {@code CHAT}/{@code REACTION} 이벤트.
 *
 * <p>판정 순서(채팅): 점유자 확인(150301) → 미션 중(150304) → 길이(150307) → 방문객 내용 제한(150308) → 제한 중(150306) → 전송
 * 간격(150302) → 같은 내용(150303) → 금칙어(HIGH 150305, LOW/MEDIUM 가림, MEDIUM 반복은 다음부터 제한). 원문은 운영 로그에만. 차단
 * 유저의 메시지는 이력 API 가 요청자 기준으로 빼고, 실시간은 클라이언트가 {@code user_id} 로 숨긴다(토픽은 방 공용).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TowerChatService {
    private final TowerOccupantRepository occupantRepository;
    private final TowerPresenceStore presenceStore;
    private final TowerChatModerationService moderationService;
    private final TowerChatGuard guard;
    private final TowerChatHistoryStore historyStore;
    private final TowerCheerWindowStore cheerWindowStore;
    private final TowerChatLogWriter logWriter;
    private final TowerRealtimePublisher realtimePublisher;
    private final TowerSeatService towerSeatService;
    private final TowerChatProperties properties;
    @Lazy private final UserQueryFacade userQueryFacade;

    public TowerChatMessage send(
            Long instanceId,
            @Nullable String userId,
            @Nullable String visitorKey,
            String rawContent) {
        TowerOccupant me = resolveOccupant(instanceId, userId, visitorKey);
        if (me.isMissionActive()) {
            throw TowerChatException.missionInProgress();
        }
        String content = rawContent == null ? "" : rawContent.trim();
        boolean visitor = me.isVisitor();
        int maxLength = visitor ? properties.getVisitorMaxLength() : properties.getMaxLength();
        if (content.isEmpty() || content.length() > maxLength) {
            throw TowerChatException.contentInvalid();
        }
        if (visitor) {
            Optional<String> violation = TowerVisitorContentPolicy.violation(content);
            if (violation.isPresent()) {
                writeLog(
                        me,
                        visitorKey,
                        content,
                        null,
                        TowerChatLog.RESULT_REJECTED,
                        TowerChatException.VISITOR_CONTENT_BLOCKED,
                        violation.get());
                throw TowerChatException.visitorContentBlocked();
            }
        }
        if (guard.isMuted(me.getId())) {
            throw TowerChatException.muted();
        }
        if (!guard.tryAcquireSend(me.getId(), visitor)) {
            throw TowerChatException.rateLimited();
        }
        if (guard.isDuplicate(me.getId(), content)) {
            throw TowerChatException.duplicate();
        }
        ModerationResult moderation = moderationService.moderate(content);
        if (moderation.rejected()) {
            writeLog(
                    me,
                    visitorKey,
                    content,
                    null,
                    TowerChatLog.RESULT_REJECTED,
                    TowerChatException.PROFANITY_REJECTED,
                    String.join(",", moderation.matchedWords()));
            throw TowerChatException.profanityRejected();
        }
        if (moderation.maxSeverity() == ProfanitySeverity.MEDIUM) {
            guard.recordMediumHit(me.getId());
        }
        guard.rememberContent(me.getId(), content);
        LocalDateTime now = LocalDateTime.now();
        Optional<TowerPresence> presence = presenceStore.find(instanceId, me.getId());
        TowerChatMessage message =
                new TowerChatMessage(
                        UUID.randomUUID().toString(),
                        instanceId,
                        me.getId(),
                        me.getUserId(),
                        presence.map(TowerPresence::visitorNo).orElse(me.getVisitorNo()),
                        presence.map(TowerPresence::nickname).orElse(null),
                        moderation.content(),
                        moderation.masked(),
                        now);
        writeLog(
                me,
                visitorKey,
                content,
                moderation.content(),
                moderation.masked() ? TowerChatLog.RESULT_MASKED : TowerChatLog.RESULT_SENT,
                null,
                String.join(",", moderation.matchedWords()));
        historyStore.push(message);
        realtimePublisher.publishToInstance(instanceId, TowerInstanceEvent.chat(message, now));
        return message;
    }

    public TowerReactionResponse react(
            Long instanceId,
            @Nullable String userId,
            @Nullable String visitorKey,
            String emoji,
            @Nullable String targetUserId) {
        TowerOccupant me = resolveOccupant(instanceId, userId, visitorKey);
        if (emoji == null || !properties.getReactionEmojis().contains(emoji)) {
            throw TowerChatException.invalidReaction();
        }
        if (guard.isMuted(me.getId())) {
            throw TowerChatException.muted();
        }
        if (!guard.tryAcquireReaction(me.getId(), me.isVisitor())) {
            throw TowerChatException.rateLimited();
        }
        boolean cheer = false;
        Integer cheerCount = null;
        String target = targetUserId == null || targetUserId.isBlank() ? null : targetUserId;
        if (target != null
                && !target.equals(me.getUserId())
                && cheerWindowStore.isOpen(instanceId, target)
                && cheerWindowStore.markCheered(instanceId, target, me.getId())) {
            int count = towerSeatService.addCheer(target);
            if (count >= 0) {
                cheer = true;
                cheerCount = count;
            }
        }
        realtimePublisher.publishToInstance(
                instanceId,
                TowerInstanceEvent.reaction(
                        me.getId(),
                        me.getUserId(),
                        emoji,
                        cheer ? target : null,
                        cheer,
                        LocalDateTime.now()));
        return new TowerReactionResponse(emoji, cheer, cheerCount);
    }

    /** 최근 N건(오래된 것부터). 로그인 요청자는 차단한 유저의 메시지를 뺀다 */
    public TowerChatHistoryResponse history(
            Long instanceId, @Nullable String userId, @Nullable String visitorKey) {
        resolveOccupant(instanceId, userId, visitorKey);
        List<TowerChatMessage> messages = historyStore.recent(instanceId);
        if (userId != null && !messages.isEmpty()) {
            Set<String> blocked = Set.copyOf(safeBlockedIds(userId));
            if (!blocked.isEmpty()) {
                messages =
                        messages.stream()
                                .filter(m -> m.userId() == null || !blocked.contains(m.userId()))
                                .toList();
            }
        }
        return new TowerChatHistoryResponse(instanceId, messages);
    }

    public TowerReactionPresetsResponse presets() {
        return new TowerReactionPresetsResponse(properties.getReactionEmojis());
    }

    private List<String> safeBlockedIds(String userId) {
        try {
            List<String> ids = userQueryFacade.getBlockedUserIds(userId);
            return ids == null ? List.of() : ids;
        } catch (RuntimeException e) {
            log.warn("[tower-chat] 차단 목록 조회 실패: {}", e.getMessage());
            return List.of();
        }
    }

    private TowerOccupant resolveOccupant(
            Long instanceId, @Nullable String userId, @Nullable String visitorKey) {
        Optional<TowerOccupant> me =
                userId != null
                        ? occupantRepository.findByUserId(userId)
                        : (visitorKey == null || visitorKey.isBlank()
                                ? Optional.empty()
                                : occupantRepository.findByVisitorKey(visitorKey));
        if (me.isEmpty() || !me.get().getInstanceId().equals(instanceId)) {
            throw TowerChatException.notInInstance();
        }
        return me.get();
    }

    private void writeLog(
            TowerOccupant me,
            @Nullable String visitorKey,
            String original,
            @Nullable String processed,
            String result,
            @Nullable String rejectCode,
            @Nullable String matched) {
        logWriter.write(
                TowerChatLog.builder()
                        .instanceId(me.getInstanceId())
                        .floorNumber(me.getFloorNumber())
                        .occupantId(me.getId())
                        .userId(me.getUserId())
                        .visitorKey(me.isVisitor() ? visitorKey : null)
                        .originalContent(original)
                        .processedContent(processed)
                        .result(result)
                        .rejectCode(rejectCode)
                        .matchedWords(matched == null || matched.isEmpty() ? null : matched)
                        .build());
    }
}
