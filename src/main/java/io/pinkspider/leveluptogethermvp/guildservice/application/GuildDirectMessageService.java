package io.pinkspider.leveluptogethermvp.guildservice.application;

import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.DirectConversationResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.DirectMessageRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.DirectMessageResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildDirectConversation;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildDirectMessage;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildDirectConversationRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildDirectMessageRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import io.pinkspider.leveluptogethermvp.notificationservice.application.FcmPushService;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.PushMessageRequest;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(transactionManager = "guildTransactionManager", readOnly = true)
public class GuildDirectMessageService {

    private final GuildDirectConversationRepository conversationRepository;
    private final GuildDirectMessageRepository messageRepository;
    private final GuildRepository guildRepository;
    private final GuildMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final FcmPushService fcmPushService;

    /**
     * DM 전송
     */
    @Transactional(transactionManager = "guildTransactionManager")
    public DirectMessageResponse sendMessage(
            Long guildId,
            String senderId,
            String recipientId,
            DirectMessageRequest request) {

        // 길드 조회
        Guild guild = findActiveGuildById(guildId);

        // 발신자, 수신자 모두 길드 멤버인지 확인
        validateBothAreMember(guildId, senderId, recipientId);

        // 발신자 닉네임 조회
        String senderNickname = userRepository.findById(senderId)
            .map(Users::getNickname)
            .orElse("익명");

        // 대화 조회 또는 생성
        GuildDirectConversation conversation = conversationRepository
            .findConversation(guildId, senderId, recipientId)
            .orElseGet(() -> {
                GuildDirectConversation newConversation = GuildDirectConversation.create(guild, senderId, recipientId);
                return conversationRepository.save(newConversation);
            });

        // 메시지 생성
        GuildDirectMessage message;
        if (request.getImageUrl() != null && !request.getImageUrl().isEmpty()) {
            message = GuildDirectMessage.createImageMessage(
                conversation, senderId, senderNickname, request.getContent(), request.getImageUrl());
        } else {
            message = GuildDirectMessage.createTextMessage(
                conversation, senderId, senderNickname, request.getContent());
        }

        GuildDirectMessage savedMessage = messageRepository.save(message);

        // 대화의 마지막 메시지 정보 업데이트
        conversation.updateLastMessage(request.getContent());

        log.debug("DM 전송: guildId={}, senderId={}, recipientId={}", guildId, senderId, recipientId);

        // 수신자에게 푸시 알림 전송
        sendDmPushNotification(recipientId, senderNickname, request.getContent(), guildId, conversation.getId());

        return DirectMessageResponse.from(savedMessage);
    }

    /**
     * 대화 목록 조회
     */
    public List<DirectConversationResponse> getConversations(Long guildId, String userId) {
        validateMembership(guildId, userId);

        List<GuildDirectConversation> conversations = conversationRepository
            .findAllByGuildIdAndUserId(guildId, userId);

        // 상대방 userId 수집
        List<String> otherUserIds = conversations.stream()
            .map(c -> c.getOtherUserId(userId))
            .distinct()
            .toList();

        // 상대방 정보 일괄 조회
        Map<String, Users> userMap = userRepository.findAllByIdIn(otherUserIds).stream()
            .collect(Collectors.toMap(Users::getId, Function.identity()));

        return conversations.stream()
            .map(conv -> {
                String otherUserId = conv.getOtherUserId(userId);
                Users otherUser = userMap.get(otherUserId);
                String otherNickname = otherUser != null ? otherUser.getDisplayName() : "알 수 없음";
                String otherProfileImage = otherUser != null ? otherUser.getPicture() : null;
                int unreadCount = messageRepository.countUnreadMessages(conv.getId(), userId);
                return DirectConversationResponse.from(conv, userId, otherNickname, otherProfileImage, unreadCount);
            })
            .toList();
    }

    /**
     * 대화 메시지 조회
     */
    public Page<DirectMessageResponse> getMessages(
            Long guildId,
            String userId,
            String otherUserId,
            Pageable pageable) {

        validateMembership(guildId, userId);

        GuildDirectConversation conversation = conversationRepository
            .findConversation(guildId, userId, otherUserId)
            .orElseThrow(() -> new IllegalArgumentException("대화를 찾을 수 없습니다."));

        return messageRepository.findByConversationId(conversation.getId(), pageable)
            .map(DirectMessageResponse::from);
    }

    /**
     * 대화 ID로 메시지 조회
     */
    public Page<DirectMessageResponse> getMessagesByConversationId(
            Long guildId,
            String userId,
            Long conversationId,
            Pageable pageable) {

        validateMembership(guildId, userId);

        GuildDirectConversation conversation = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new IllegalArgumentException("대화를 찾을 수 없습니다."));

        if (!conversation.isParticipant(userId)) {
            throw new IllegalStateException("해당 대화에 접근할 수 없습니다.");
        }

        if (!conversation.getGuild().getId().equals(guildId)) {
            throw new IllegalArgumentException("해당 길드의 대화가 아닙니다.");
        }

        return messageRepository.findByConversationId(conversationId, pageable)
            .map(DirectMessageResponse::from);
    }

    /**
     * 이전 메시지 조회 (무한 스크롤)
     */
    public Page<DirectMessageResponse> getMessagesBeforeId(
            Long guildId,
            String userId,
            Long conversationId,
            Long beforeId,
            Pageable pageable) {

        validateMembership(guildId, userId);
        validateConversationAccess(conversationId, userId, guildId);

        return messageRepository.findMessagesBeforeId(conversationId, beforeId, pageable)
            .map(DirectMessageResponse::from);
    }

    /**
     * 메시지 읽음 처리
     */
    @Transactional(transactionManager = "guildTransactionManager")
    public void markAsRead(Long guildId, String userId, Long conversationId) {
        validateMembership(guildId, userId);
        validateConversationAccess(conversationId, userId, guildId);

        int updatedCount = messageRepository.markAllAsRead(conversationId, userId);
        log.debug("DM 읽음 처리: conversationId={}, userId={}, count={}", conversationId, userId, updatedCount);
    }

    /**
     * 전체 안읽은 DM 수 조회
     */
    public int getTotalUnreadCount(Long guildId, String userId) {
        validateMembership(guildId, userId);
        return messageRepository.countTotalUnreadMessages(guildId, userId);
    }

    /**
     * 대화 또는 상대방과의 대화 생성/조회
     */
    @Transactional(transactionManager = "guildTransactionManager")
    public DirectConversationResponse getOrCreateConversation(Long guildId, String userId, String otherUserId) {
        Guild guild = findActiveGuildById(guildId);
        validateBothAreMember(guildId, userId, otherUserId);

        GuildDirectConversation conversation = conversationRepository
            .findConversation(guildId, userId, otherUserId)
            .orElseGet(() -> {
                GuildDirectConversation newConversation = GuildDirectConversation.create(guild, userId, otherUserId);
                return conversationRepository.save(newConversation);
            });

        Users otherUser = userRepository.findById(otherUserId).orElse(null);
        String otherNickname = otherUser != null ? otherUser.getDisplayName() : "알 수 없음";
        String otherProfileImage = otherUser != null ? otherUser.getPicture() : null;
        int unreadCount = messageRepository.countUnreadMessages(conversation.getId(), userId);

        return DirectConversationResponse.from(conversation, userId, otherNickname, otherProfileImage, unreadCount);
    }

    /**
     * 메시지 삭제
     */
    @Transactional(transactionManager = "guildTransactionManager")
    public void deleteMessage(Long guildId, String userId, Long messageId) {
        validateMembership(guildId, userId);

        GuildDirectMessage message = messageRepository.findById(messageId)
            .orElseThrow(() -> new IllegalArgumentException("메시지를 찾을 수 없습니다."));

        // 본인 메시지만 삭제 가능
        if (!message.getSenderId().equals(userId)) {
            throw new IllegalStateException("본인 메시지만 삭제할 수 있습니다.");
        }

        if (!message.getConversation().getGuild().getId().equals(guildId)) {
            throw new IllegalArgumentException("해당 길드의 메시지가 아닙니다.");
        }

        message.delete();
        log.info("DM 삭제: messageId={}, deletedBy={}", messageId, userId);
    }

    // ============ 헬퍼 메서드 ============

    private Guild findActiveGuildById(Long guildId) {
        return guildRepository.findByIdAndIsActiveTrue(guildId)
            .orElseThrow(() -> new IllegalArgumentException("길드를 찾을 수 없습니다: " + guildId));
    }

    private void validateMembership(Long guildId, String userId) {
        if (!memberRepository.isActiveMember(guildId, userId)) {
            throw new IllegalStateException("길드 멤버만 DM을 사용할 수 있습니다.");
        }
    }

    private void validateBothAreMember(Long guildId, String userId1, String userId2) {
        if (userId1.equals(userId2)) {
            throw new IllegalArgumentException("자기 자신에게 DM을 보낼 수 없습니다.");
        }
        if (!memberRepository.isActiveMember(guildId, userId1)) {
            throw new IllegalStateException("발신자가 길드 멤버가 아닙니다.");
        }
        if (!memberRepository.isActiveMember(guildId, userId2)) {
            throw new IllegalStateException("수신자가 길드 멤버가 아닙니다.");
        }
    }

    private void validateConversationAccess(Long conversationId, String userId, Long guildId) {
        GuildDirectConversation conversation = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new IllegalArgumentException("대화를 찾을 수 없습니다."));

        if (!conversation.isParticipant(userId)) {
            throw new IllegalStateException("해당 대화에 접근할 수 없습니다.");
        }

        if (!conversation.getGuild().getId().equals(guildId)) {
            throw new IllegalArgumentException("해당 길드의 대화가 아닙니다.");
        }
    }

    /**
     * DM 푸시 알림 전송
     */
    private void sendDmPushNotification(String recipientId, String senderNickname, String content, Long guildId, Long conversationId) {
        try {
            String body = content.length() > 100 ? content.substring(0, 100) + "..." : content;
            PushMessageRequest pushRequest = new PushMessageRequest(
                senderNickname,
                body,
                null,
                "OPEN_DM",
                java.util.Map.of(
                    "type", "DM",
                    "guild_id", String.valueOf(guildId),
                    "conversation_id", String.valueOf(conversationId)
                )
            );
            fcmPushService.sendToUser(recipientId, pushRequest);
        } catch (Exception e) {
            log.warn("DM 푸시 알림 전송 실패: recipientId={}, error={}", recipientId, e.getMessage());
        }
    }
}
