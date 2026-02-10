package io.pinkspider.leveluptogethermvp.chatservice.application;

import io.pinkspider.global.event.GuildChatMessageEvent;
import io.pinkspider.leveluptogethermvp.chatservice.domain.dto.ChatMessageRequest;
import io.pinkspider.leveluptogethermvp.chatservice.domain.dto.ChatMessageResponse;
import io.pinkspider.leveluptogethermvp.chatservice.domain.dto.ChatParticipantResponse;
import io.pinkspider.leveluptogethermvp.chatservice.domain.dto.ChatRoomInfoResponse;
import io.pinkspider.leveluptogethermvp.chatservice.domain.entity.GuildChatMessage;
import io.pinkspider.leveluptogethermvp.chatservice.domain.entity.GuildChatParticipant;
import io.pinkspider.leveluptogethermvp.chatservice.domain.entity.GuildChatReadStatus;
import io.pinkspider.leveluptogethermvp.chatservice.domain.enums.ChatMessageType;
import io.pinkspider.leveluptogethermvp.chatservice.infrastructure.GuildChatMessageRepository;
import io.pinkspider.leveluptogethermvp.chatservice.infrastructure.GuildChatParticipantRepository;
import io.pinkspider.leveluptogethermvp.chatservice.infrastructure.GuildChatReadStatusRepository;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import io.pinkspider.leveluptogethermvp.userservice.profile.application.UserProfileCacheService;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(transactionManager = "chatTransactionManager", readOnly = true)
public class GuildChatService {

    private final GuildChatMessageRepository chatMessageRepository;
    private final GuildChatReadStatusRepository readStatusRepository;
    private final GuildChatParticipantRepository participantRepository;
    private final GuildRepository guildRepository;
    private final GuildMemberRepository memberRepository;
    private final UserProfileCacheService userProfileCacheService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(transactionManager = "chatTransactionManager")
    public ChatMessageResponse sendMessage(Long guildId, String userId, String nickname,
                                            ChatMessageRequest request) {
        Guild guild = findGuildById(guildId);
        validateMembership(guildId, userId);

        String effectiveNickname = nickname;
        if (effectiveNickname == null || effectiveNickname.isBlank()) {
            effectiveNickname = userProfileCacheService.getUserProfile(userId).nickname();
        }

        GuildChatMessage message;
        if (request.getImageUrl() != null && !request.getImageUrl().isEmpty()) {
            message = GuildChatMessage.builder()
                .guildId(guildId)
                .senderId(userId)
                .senderNickname(effectiveNickname)
                .messageType(ChatMessageType.IMAGE)
                .content(request.getContent() != null ? request.getContent() : "")
                .imageUrl(request.getImageUrl())
                .build();
        } else {
            message = GuildChatMessage.createTextMessage(guildId, userId, effectiveNickname, request.getContent());
        }

        GuildChatMessage saved = chatMessageRepository.save(message);
        log.debug("채팅 메시지 전송: guildId={}, userId={}", guildId, userId);

        List<String> memberIds = memberRepository.findActiveMembers(guildId).stream()
            .map(member -> member.getUserId())
            .filter(memberId -> !memberId.equals(userId))
            .toList();

        if (!memberIds.isEmpty()) {
            eventPublisher.publishEvent(new GuildChatMessageEvent(
                userId,
                effectiveNickname,
                guildId,
                guild.getName(),
                saved.getId(),
                saved.getContent(),
                memberIds
            ));
        }

        return ChatMessageResponse.from(saved);
    }

    @Transactional(transactionManager = "chatTransactionManager")
    public ChatMessageResponse sendSystemMessage(Long guildId, ChatMessageType type, String content) {
        validateGuildExists(guildId);
        GuildChatMessage message = GuildChatMessage.createSystemMessage(guildId, type, content);
        GuildChatMessage saved = chatMessageRepository.save(message);
        return ChatMessageResponse.from(saved);
    }

    @Transactional(transactionManager = "chatTransactionManager")
    public ChatMessageResponse sendSystemMessage(Long guildId, ChatMessageType type, String content,
                                                  String referenceType, Long referenceId) {
        validateGuildExists(guildId);
        GuildChatMessage message = GuildChatMessage.createSystemMessage(
            guildId, type, content, referenceType, referenceId);
        GuildChatMessage saved = chatMessageRepository.save(message);
        return ChatMessageResponse.from(saved);
    }

    public Page<ChatMessageResponse> getMessages(Long guildId, String userId, Pageable pageable) {
        validateMembership(guildId, userId);
        return chatMessageRepository.findByGuildIdOrderByCreatedAtDesc(guildId, pageable)
            .map(ChatMessageResponse::from);
    }

    public List<ChatMessageResponse> getNewMessages(Long guildId, String userId, LocalDateTime since) {
        validateMembership(guildId, userId);
        return chatMessageRepository.findNewMessages(guildId, since).stream()
            .map(ChatMessageResponse::from)
            .toList();
    }

    public List<ChatMessageResponse> getMessagesAfterId(Long guildId, String userId, Long lastMessageId) {
        validateMembership(guildId, userId);
        return chatMessageRepository.findMessagesAfterId(guildId, lastMessageId).stream()
            .map(ChatMessageResponse::from)
            .toList();
    }

    public Page<ChatMessageResponse> getMessagesBeforeId(Long guildId, String userId,
                                                          Long beforeId, Pageable pageable) {
        validateMembership(guildId, userId);
        return chatMessageRepository.findMessagesBeforeId(guildId, beforeId, pageable)
            .map(ChatMessageResponse::from);
    }

    public Page<ChatMessageResponse> searchMessages(Long guildId, String userId,
                                                     String keyword, Pageable pageable) {
        validateMembership(guildId, userId);
        return chatMessageRepository.searchMessages(guildId, keyword, pageable)
            .map(ChatMessageResponse::from);
    }

    @Transactional(transactionManager = "chatTransactionManager")
    public void deleteMessage(Long guildId, Long messageId, String userId) {
        GuildChatMessage message = chatMessageRepository.findById(messageId)
            .orElseThrow(() -> new IllegalArgumentException("메시지를 찾을 수 없습니다."));

        if (!message.getGuildId().equals(guildId)) {
            throw new IllegalArgumentException("해당 길드의 메시지가 아닙니다.");
        }

        // 본인 메시지이거나 길드 마스터만 삭제 가능
        Guild guild = findGuildById(guildId);
        if (!message.getSenderId().equals(userId) && !guild.isMaster(userId)) {
            throw new IllegalStateException("본인 메시지 또는 길드 마스터만 삭제할 수 있습니다.");
        }

        message.delete();
        log.info("채팅 메시지 삭제: messageId={}, deletedBy={}", messageId, userId);
    }

    @Transactional(transactionManager = "chatTransactionManager")
    public void notifyMemberJoin(Long guildId, String memberNickname) {
        sendSystemMessage(guildId, ChatMessageType.SYSTEM_JOIN,
            memberNickname + "님이 길드에 가입했습니다.");
    }

    @Transactional(transactionManager = "chatTransactionManager")
    public void notifyMemberLeave(Long guildId, String memberNickname) {
        sendSystemMessage(guildId, ChatMessageType.SYSTEM_LEAVE,
            memberNickname + "님이 길드를 떠났습니다.");
    }

    @Transactional(transactionManager = "chatTransactionManager")
    public void notifyMemberKick(Long guildId, String memberNickname) {
        sendSystemMessage(guildId, ChatMessageType.SYSTEM_KICK,
            memberNickname + "님이 추방되었습니다.");
    }

    private Guild findGuildById(Long guildId) {
        return guildRepository.findByIdAndIsActiveTrue(guildId)
            .orElseThrow(() -> new IllegalArgumentException("길드를 찾을 수 없습니다: " + guildId));
    }

    private void validateGuildExists(Long guildId) {
        if (!guildRepository.existsByIdAndIsActiveTrue(guildId)) {
            throw new IllegalArgumentException("길드를 찾을 수 없습니다: " + guildId);
        }
    }

    private void validateMembership(Long guildId, String userId) {
        if (!memberRepository.isActiveMember(guildId, userId)) {
            throw new IllegalStateException("길드 멤버만 채팅에 참여할 수 있습니다.");
        }
    }

    // ============ 읽음 확인 관련 메서드 ============

    public ChatRoomInfoResponse getChatRoomInfo(Long guildId, String userId) {
        validateMembership(guildId, userId);

        Guild guild = findGuildById(guildId);
        int memberCount = (int) memberRepository.countActiveMembers(guildId);
        int participantCount = (int) participantRepository.countActiveParticipants(guildId);

        GuildChatReadStatus readStatus = readStatusRepository.findByGuildIdAndUserId(guildId, userId)
            .orElse(null);

        Long lastReadMessageId = readStatus != null ? readStatus.getLastReadMessageId() : 0L;

        int unreadCount = readStatus != null
            ? readStatusRepository.countUnreadMessagesForUser(guildId, readStatus.getLastReadMessageId())
            : (int) chatMessageRepository.countByGuildId(guildId);

        return ChatRoomInfoResponse.of(guild.getId(), guild.getName(), guild.getImageUrl(),
            memberCount, participantCount, unreadCount, lastReadMessageId);
    }

    @Transactional(transactionManager = "chatTransactionManager")
    public void markAsRead(Long guildId, String userId, Long messageId) {
        validateMembership(guildId, userId);

        GuildChatMessage message = chatMessageRepository.findById(messageId)
            .orElseThrow(() -> new IllegalArgumentException("메시지를 찾을 수 없습니다: " + messageId));

        if (!message.getGuildId().equals(guildId)) {
            throw new IllegalArgumentException("해당 길드의 메시지가 아닙니다.");
        }

        GuildChatReadStatus readStatus = readStatusRepository.findByGuildIdAndUserId(guildId, userId)
            .orElseGet(() -> {
                GuildChatReadStatus newStatus = GuildChatReadStatus.create(guildId, userId);
                return readStatusRepository.save(newStatus);
            });

        readStatus.updateLastRead(message);
        log.debug("메시지 읽음 처리: guildId={}, userId={}, messageId={}", guildId, userId, messageId);
    }

    @Transactional(transactionManager = "chatTransactionManager")
    public void initializeReadStatus(Long guildId, String userId) {
        validateGuildExists(guildId);

        readStatusRepository.findByGuildIdAndUserId(guildId, userId)
            .orElseGet(() -> {
                GuildChatReadStatus newStatus = GuildChatReadStatus.create(guildId, userId);
                return readStatusRepository.save(newStatus);
            });
        log.debug("읽음 상태 초기화: guildId={}, userId={}", guildId, userId);
    }

    @Transactional(transactionManager = "chatTransactionManager")
    public void deleteReadStatus(Long guildId, String userId) {
        readStatusRepository.deleteByGuildIdAndUserId(guildId, userId);
        log.debug("읽음 상태 삭제: guildId={}, userId={}", guildId, userId);
    }

    public Page<ChatMessageResponse> getMessagesWithUnreadCount(Long guildId, String userId, Pageable pageable) {
        validateMembership(guildId, userId);

        Page<GuildChatMessage> messages = chatMessageRepository.findByGuildIdOrderByCreatedAtDesc(guildId, pageable);
        int totalParticipants = (int) participantRepository.countActiveParticipants(guildId);

        List<Long> messageIds = messages.getContent().stream()
            .map(GuildChatMessage::getId)
            .toList();

        Map<Long, Long> readerCountMap = new HashMap<>();
        if (!messageIds.isEmpty()) {
            List<Object[]> results = readStatusRepository.countReadersForMessages(guildId, messageIds);
            for (Object[] result : results) {
                Long msgId = (Long) result[0];
                Long readCount = (Long) result[1];
                readerCountMap.put(msgId, readCount);
            }
        }

        List<ChatMessageResponse> responses = messages.getContent().stream()
            .map(msg -> {
                long readCount = readerCountMap.getOrDefault(msg.getId(), 0L);
                int unreadCount = Math.max(0, totalParticipants - (int) readCount);
                return ChatMessageResponse.from(msg, unreadCount);
            })
            .toList();

        return new PageImpl<>(responses, pageable, messages.getTotalElements());
    }

    public int getReadCount(Long guildId, Long messageId) {
        return (int) readStatusRepository.countReadersForMessage(guildId, messageId);
    }

    public int getUnreadCount(Long guildId, Long messageId) {
        int totalParticipants = (int) participantRepository.countActiveParticipants(guildId);
        int readCount = getReadCount(guildId, messageId);
        return Math.max(0, totalParticipants - readCount);
    }

    // ============ 채팅방 참여 관련 메서드 ============

    @Transactional(transactionManager = "chatTransactionManager")
    public ChatParticipantResponse joinChat(Long guildId, String userId, String nickname) {
        validateMembership(guildId, userId);
        validateGuildExists(guildId);

        String effectiveNickname = nickname;
        if (effectiveNickname == null || effectiveNickname.isBlank()) {
            effectiveNickname = userProfileCacheService.getUserProfile(userId).nickname();
        }
        final String finalNickname = effectiveNickname;

        GuildChatParticipant participant = participantRepository.findByGuildIdAndUserId(guildId, userId)
            .map(existing -> {
                if (!existing.isParticipating()) {
                    existing.rejoin(finalNickname);
                    sendSystemMessage(guildId, ChatMessageType.SYSTEM_JOIN,
                        finalNickname + "님이 채팅방에 입장했습니다.");
                }
                return existing;
            })
            .orElseGet(() -> {
                GuildChatParticipant newParticipant = GuildChatParticipant.create(guildId, userId, finalNickname);
                sendSystemMessage(guildId, ChatMessageType.SYSTEM_JOIN,
                    finalNickname + "님이 채팅방에 입장했습니다.");
                return participantRepository.save(newParticipant);
            });

        initializeReadStatus(guildId, userId);

        log.info("채팅방 입장: guildId={}, userId={}, nickname={}", guildId, userId, finalNickname);
        return ChatParticipantResponse.from(participant);
    }

    @Transactional(transactionManager = "chatTransactionManager")
    public void leaveChat(Long guildId, String userId, String nickname) {
        validateMembership(guildId, userId);

        String effectiveNickname = nickname;
        if (effectiveNickname == null || effectiveNickname.isBlank()) {
            effectiveNickname = userProfileCacheService.getUserProfile(userId).nickname();
        }
        final String finalNickname = effectiveNickname;

        participantRepository.findByGuildIdAndUserId(guildId, userId)
            .ifPresent(participant -> {
                if (participant.isParticipating()) {
                    participant.leave();
                    sendSystemMessage(guildId, ChatMessageType.SYSTEM_LEAVE,
                        finalNickname + "님이 채팅방에서 퇴장했습니다.");
                    log.info("채팅방 퇴장: guildId={}, userId={}, nickname={}", guildId, userId, finalNickname);
                }
            });
    }

    public boolean isParticipating(Long guildId, String userId) {
        validateMembership(guildId, userId);
        return participantRepository.isParticipating(guildId, userId);
    }

    public List<ChatParticipantResponse> getActiveParticipants(Long guildId, String userId) {
        validateMembership(guildId, userId);
        return participantRepository.findActiveParticipants(guildId).stream()
            .map(ChatParticipantResponse::from)
            .toList();
    }

    public long getParticipantCount(Long guildId, String userId) {
        validateMembership(guildId, userId);
        return participantRepository.countActiveParticipants(guildId);
    }
}
