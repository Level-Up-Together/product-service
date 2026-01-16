package io.pinkspider.leveluptogethermvp.guildservice.application;

import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.ChatMessageRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.ChatMessageResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.ChatRoomInfoResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildChatMessage;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildChatReadStatus;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.ChatMessageType;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildChatMessageRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildChatReadStatusRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(transactionManager = "guildTransactionManager", readOnly = true)
public class GuildChatService {

    private final GuildChatMessageRepository chatMessageRepository;
    private final GuildChatReadStatusRepository readStatusRepository;
    private final GuildRepository guildRepository;
    private final GuildMemberRepository memberRepository;

    // 메시지 전송
    @Transactional(transactionManager = "guildTransactionManager")
    public ChatMessageResponse sendMessage(Long guildId, String userId, String nickname,
                                            ChatMessageRequest request) {
        Guild guild = findGuildById(guildId);
        validateMembership(guildId, userId);

        GuildChatMessage message;
        if (request.getImageUrl() != null && !request.getImageUrl().isEmpty()) {
            message = GuildChatMessage.builder()
                .guild(guild)
                .senderId(userId)
                .senderNickname(nickname)
                .messageType(ChatMessageType.IMAGE)
                .content(request.getContent() != null ? request.getContent() : "")
                .imageUrl(request.getImageUrl())
                .build();
        } else {
            message = GuildChatMessage.createTextMessage(guild, userId, nickname, request.getContent());
        }

        GuildChatMessage saved = chatMessageRepository.save(message);
        log.debug("채팅 메시지 전송: guildId={}, userId={}", guildId, userId);

        return ChatMessageResponse.from(saved);
    }

    // 시스템 메시지 전송
    @Transactional(transactionManager = "guildTransactionManager")
    public ChatMessageResponse sendSystemMessage(Long guildId, ChatMessageType type, String content) {
        Guild guild = findGuildById(guildId);
        GuildChatMessage message = GuildChatMessage.createSystemMessage(guild, type, content);
        GuildChatMessage saved = chatMessageRepository.save(message);
        return ChatMessageResponse.from(saved);
    }

    // 시스템 메시지 전송 (참조 포함)
    @Transactional(transactionManager = "guildTransactionManager")
    public ChatMessageResponse sendSystemMessage(Long guildId, ChatMessageType type, String content,
                                                  String referenceType, Long referenceId) {
        Guild guild = findGuildById(guildId);
        GuildChatMessage message = GuildChatMessage.createSystemMessage(
            guild, type, content, referenceType, referenceId);
        GuildChatMessage saved = chatMessageRepository.save(message);
        return ChatMessageResponse.from(saved);
    }

    // 최신 메시지 조회
    public Page<ChatMessageResponse> getMessages(Long guildId, String userId, Pageable pageable) {
        validateMembership(guildId, userId);
        return chatMessageRepository.findByGuildIdOrderByCreatedAtDesc(guildId, pageable)
            .map(ChatMessageResponse::from);
    }

    // 새 메시지 조회 (폴링)
    public List<ChatMessageResponse> getNewMessages(Long guildId, String userId, LocalDateTime since) {
        validateMembership(guildId, userId);
        return chatMessageRepository.findNewMessages(guildId, since).stream()
            .map(ChatMessageResponse::from)
            .toList();
    }

    // 특정 ID 이후 메시지 조회
    public List<ChatMessageResponse> getMessagesAfterId(Long guildId, String userId, Long lastMessageId) {
        validateMembership(guildId, userId);
        return chatMessageRepository.findMessagesAfterId(guildId, lastMessageId).stream()
            .map(ChatMessageResponse::from)
            .toList();
    }

    // 이전 메시지 조회 (무한 스크롤)
    public Page<ChatMessageResponse> getMessagesBeforeId(Long guildId, String userId,
                                                          Long beforeId, Pageable pageable) {
        validateMembership(guildId, userId);
        return chatMessageRepository.findMessagesBeforeId(guildId, beforeId, pageable)
            .map(ChatMessageResponse::from);
    }

    // 메시지 검색
    public Page<ChatMessageResponse> searchMessages(Long guildId, String userId,
                                                     String keyword, Pageable pageable) {
        validateMembership(guildId, userId);
        return chatMessageRepository.searchMessages(guildId, keyword, pageable)
            .map(ChatMessageResponse::from);
    }

    // 메시지 삭제
    @Transactional(transactionManager = "guildTransactionManager")
    public void deleteMessage(Long guildId, Long messageId, String userId) {
        GuildChatMessage message = chatMessageRepository.findById(messageId)
            .orElseThrow(() -> new IllegalArgumentException("메시지를 찾을 수 없습니다."));

        if (!message.getGuild().getId().equals(guildId)) {
            throw new IllegalArgumentException("해당 길드의 메시지가 아닙니다.");
        }

        // 본인 메시지이거나 길드 마스터만 삭제 가능
        Guild guild = message.getGuild();
        if (!message.getSenderId().equals(userId) && !guild.isMaster(userId)) {
            throw new IllegalStateException("본인 메시지 또는 길드 마스터만 삭제할 수 있습니다.");
        }

        message.delete();
        log.info("채팅 메시지 삭제: messageId={}, deletedBy={}", messageId, userId);
    }

    // 멤버 가입 시스템 메시지
    @Transactional(transactionManager = "guildTransactionManager")
    public void notifyMemberJoin(Long guildId, String memberNickname) {
        sendSystemMessage(guildId, ChatMessageType.SYSTEM_JOIN,
            memberNickname + "님이 길드에 가입했습니다.");
    }

    // 멤버 탈퇴 시스템 메시지
    @Transactional(transactionManager = "guildTransactionManager")
    public void notifyMemberLeave(Long guildId, String memberNickname) {
        sendSystemMessage(guildId, ChatMessageType.SYSTEM_LEAVE,
            memberNickname + "님이 길드를 떠났습니다.");
    }

    // 업적 달성 시스템 메시지
    @Transactional(transactionManager = "guildTransactionManager")
    public void notifyAchievement(Long guildId, String memberNickname, String achievementName,
                                   Long achievementId) {
        sendSystemMessage(guildId, ChatMessageType.SYSTEM_ACHIEVEMENT,
            memberNickname + "님이 '" + achievementName + "' 업적을 달성했습니다!",
            "ACHIEVEMENT", achievementId);
    }

    // 레벨업 시스템 메시지
    @Transactional(transactionManager = "guildTransactionManager")
    public void notifyLevelUp(Long guildId, String memberNickname, int newLevel) {
        sendSystemMessage(guildId, ChatMessageType.SYSTEM_LEVEL_UP,
            memberNickname + "님이 레벨 " + newLevel + "에 도달했습니다!");
    }

    // 미션 완료 시스템 메시지
    @Transactional(transactionManager = "guildTransactionManager")
    public void notifyMissionComplete(Long guildId, String memberNickname, String missionTitle,
                                       Long missionId) {
        sendSystemMessage(guildId, ChatMessageType.SYSTEM_MISSION,
            memberNickname + "님이 '" + missionTitle + "' 미션을 완료했습니다!",
            "MISSION", missionId);
    }

    private Guild findGuildById(Long guildId) {
        return guildRepository.findByIdAndIsActiveTrue(guildId)
            .orElseThrow(() -> new IllegalArgumentException("길드를 찾을 수 없습니다: " + guildId));
    }

    private void validateMembership(Long guildId, String userId) {
        if (!memberRepository.isActiveMember(guildId, userId)) {
            throw new IllegalStateException("길드 멤버만 채팅에 참여할 수 있습니다.");
        }
    }

    // ============ 읽음 확인 관련 메서드 ============

    // 채팅방 정보 조회 (참여자 수, 안읽은 메시지 수)
    public ChatRoomInfoResponse getChatRoomInfo(Long guildId, String userId) {
        validateMembership(guildId, userId);

        Guild guild = findGuildById(guildId);
        int memberCount = (int) memberRepository.countActiveMembers(guildId);

        // 사용자의 읽음 상태 조회
        GuildChatReadStatus readStatus = readStatusRepository.findByGuildIdAndUserId(guildId, userId)
            .orElse(null);

        Long lastReadMessageId = readStatus != null ? readStatus.getLastReadMessageId() : 0L;

        // 안읽은 메시지 수 계산
        int unreadCount = readStatus != null
            ? readStatusRepository.countUnreadMessagesForUser(guildId, readStatus.getLastReadMessageId())
            : (int) chatMessageRepository.countByGuildId(guildId);

        return ChatRoomInfoResponse.of(guild, memberCount, unreadCount, lastReadMessageId);
    }

    // 메시지 읽음 처리
    @Transactional(transactionManager = "guildTransactionManager")
    public void markAsRead(Long guildId, String userId, Long messageId) {
        validateMembership(guildId, userId);

        Guild guild = findGuildById(guildId);
        GuildChatMessage message = chatMessageRepository.findById(messageId)
            .orElseThrow(() -> new IllegalArgumentException("메시지를 찾을 수 없습니다: " + messageId));

        if (!message.getGuild().getId().equals(guildId)) {
            throw new IllegalArgumentException("해당 길드의 메시지가 아닙니다.");
        }

        GuildChatReadStatus readStatus = readStatusRepository.findByGuildIdAndUserId(guildId, userId)
            .orElseGet(() -> {
                GuildChatReadStatus newStatus = GuildChatReadStatus.create(guild, userId);
                return readStatusRepository.save(newStatus);
            });

        readStatus.updateLastRead(message);
        log.debug("메시지 읽음 처리: guildId={}, userId={}, messageId={}", guildId, userId, messageId);
    }

    // 길드 입장 시 읽음 상태 초기화 (새 멤버)
    @Transactional(transactionManager = "guildTransactionManager")
    public void initializeReadStatus(Long guildId, String userId) {
        Guild guild = findGuildById(guildId);

        readStatusRepository.findByGuildIdAndUserId(guildId, userId)
            .orElseGet(() -> {
                GuildChatReadStatus newStatus = GuildChatReadStatus.create(guild, userId);
                return readStatusRepository.save(newStatus);
            });
        log.debug("읽음 상태 초기화: guildId={}, userId={}", guildId, userId);
    }

    // 길드 탈퇴 시 읽음 상태 삭제
    @Transactional(transactionManager = "guildTransactionManager")
    public void deleteReadStatus(Long guildId, String userId) {
        readStatusRepository.deleteByGuildIdAndUserId(guildId, userId);
        log.debug("읽음 상태 삭제: guildId={}, userId={}", guildId, userId);
    }

    // 최신 메시지 조회 (안읽은 수 포함)
    public Page<ChatMessageResponse> getMessagesWithUnreadCount(Long guildId, String userId, Pageable pageable) {
        validateMembership(guildId, userId);

        Page<GuildChatMessage> messages = chatMessageRepository.findByGuildIdOrderByCreatedAtDesc(guildId, pageable);
        int totalMembers = (int) memberRepository.countActiveMembers(guildId);

        // 메시지 ID 목록 추출
        List<Long> messageIds = messages.getContent().stream()
            .map(GuildChatMessage::getId)
            .toList();

        // 각 메시지별 읽은 사람 수 일괄 조회
        Map<Long, Long> readerCountMap = new HashMap<>();
        if (!messageIds.isEmpty()) {
            List<Object[]> results = readStatusRepository.countReadersForMessages(guildId, messageIds);
            for (Object[] result : results) {
                Long msgId = (Long) result[0];
                Long readCount = (Long) result[1];
                readerCountMap.put(msgId, readCount);
            }
        }

        // 응답 변환 (안읽은 수 = 전체 멤버 - 읽은 사람 수)
        List<ChatMessageResponse> responses = messages.getContent().stream()
            .map(msg -> {
                long readCount = readerCountMap.getOrDefault(msg.getId(), 0L);
                int unreadCount = Math.max(0, totalMembers - (int) readCount);
                return ChatMessageResponse.from(msg, unreadCount);
            })
            .toList();

        return new PageImpl<>(responses, pageable, messages.getTotalElements());
    }

    // 특정 메시지의 읽은 사람 수 조회
    public int getReadCount(Long guildId, Long messageId) {
        return (int) readStatusRepository.countReadersForMessage(guildId, messageId);
    }

    // 특정 메시지의 안읽은 사람 수 조회
    public int getUnreadCount(Long guildId, Long messageId) {
        int totalMembers = (int) memberRepository.countActiveMembers(guildId);
        int readCount = getReadCount(guildId, messageId);
        return Math.max(0, totalMembers - readCount);
    }
}
