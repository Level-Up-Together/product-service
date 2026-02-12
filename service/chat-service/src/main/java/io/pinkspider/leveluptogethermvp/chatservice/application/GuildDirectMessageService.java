package io.pinkspider.leveluptogethermvp.chatservice.application;

import io.pinkspider.leveluptogethermvp.chatservice.domain.dto.DirectConversationResponse;
import io.pinkspider.leveluptogethermvp.chatservice.domain.dto.DirectMessageRequest;
import io.pinkspider.leveluptogethermvp.chatservice.domain.dto.DirectMessageResponse;
import io.pinkspider.leveluptogethermvp.chatservice.domain.entity.GuildDirectConversation;
import io.pinkspider.leveluptogethermvp.chatservice.domain.entity.GuildDirectMessage;
import io.pinkspider.leveluptogethermvp.chatservice.infrastructure.GuildDirectConversationRepository;
import io.pinkspider.leveluptogethermvp.chatservice.infrastructure.GuildDirectMessageRepository;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildQueryFacadeService;
import io.pinkspider.leveluptogethermvp.notificationservice.application.FcmPushService;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.PushMessageRequest;
import io.pinkspider.leveluptogethermvp.userservice.profile.application.UserProfileCacheService;
import io.pinkspider.leveluptogethermvp.userservice.profile.domain.dto.UserProfileCache;
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
@Transactional(transactionManager = "chatTransactionManager", readOnly = true)
public class GuildDirectMessageService {

    private final GuildDirectConversationRepository conversationRepository;
    private final GuildDirectMessageRepository messageRepository;
    private final GuildQueryFacadeService guildQueryFacadeService;
    private final UserProfileCacheService userProfileCacheService;
    private final FcmPushService fcmPushService;

    @Transactional(transactionManager = "chatTransactionManager")
    public DirectMessageResponse sendMessage(
            Long guildId,
            String senderId,
            String recipientId,
            DirectMessageRequest request) {

        validateGuildExists(guildId);
        validateBothAreMember(guildId, senderId, recipientId);

        String senderNickname = userProfileCacheService.getUserNickname(senderId);

        GuildDirectConversation conversation = conversationRepository
            .findConversation(guildId, senderId, recipientId)
            .orElseGet(() -> {
                GuildDirectConversation newConversation = GuildDirectConversation.create(guildId, senderId, recipientId);
                return conversationRepository.save(newConversation);
            });

        GuildDirectMessage message;
        if (request.getImageUrl() != null && !request.getImageUrl().isEmpty()) {
            message = GuildDirectMessage.createImageMessage(
                conversation, senderId, senderNickname, request.getContent(), request.getImageUrl());
        } else {
            message = GuildDirectMessage.createTextMessage(
                conversation, senderId, senderNickname, request.getContent());
        }

        GuildDirectMessage savedMessage = messageRepository.save(message);
        conversation.updateLastMessage(request.getContent());

        log.debug("DM 전송: guildId={}, senderId={}, recipientId={}", guildId, senderId, recipientId);

        sendDmPushNotification(recipientId, senderNickname, request.getContent(), guildId, conversation.getId());

        return DirectMessageResponse.from(savedMessage);
    }

    public List<DirectConversationResponse> getConversations(Long guildId, String userId) {
        validateMembership(guildId, userId);

        List<GuildDirectConversation> conversations = conversationRepository
            .findAllByGuildIdAndUserId(guildId, userId);

        List<String> otherUserIds = conversations.stream()
            .map(c -> c.getOtherUserId(userId))
            .distinct()
            .toList();

        Map<String, UserProfileCache> profileMap = userProfileCacheService.getUserProfiles(otherUserIds);

        return conversations.stream()
            .map(conv -> {
                String otherUserId = conv.getOtherUserId(userId);
                UserProfileCache otherProfile = profileMap.get(otherUserId);
                String otherNickname = otherProfile != null ? otherProfile.nickname() : "알 수 없음";
                String otherProfileImage = otherProfile != null ? otherProfile.picture() : null;
                int unreadCount = messageRepository.countUnreadMessages(conv.getId(), userId);
                return DirectConversationResponse.from(conv, userId, otherNickname, otherProfileImage, unreadCount);
            })
            .toList();
    }

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

        if (!conversation.getGuildId().equals(guildId)) {
            throw new IllegalArgumentException("해당 길드의 대화가 아닙니다.");
        }

        return messageRepository.findByConversationId(conversationId, pageable)
            .map(DirectMessageResponse::from);
    }

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

    @Transactional(transactionManager = "chatTransactionManager")
    public void markAsRead(Long guildId, String userId, Long conversationId) {
        validateMembership(guildId, userId);
        validateConversationAccess(conversationId, userId, guildId);

        int updatedCount = messageRepository.markAllAsRead(conversationId, userId);
        log.debug("DM 읽음 처리: conversationId={}, userId={}, count={}", conversationId, userId, updatedCount);
    }

    public int getTotalUnreadCount(Long guildId, String userId) {
        validateMembership(guildId, userId);
        return messageRepository.countTotalUnreadMessages(guildId, userId);
    }

    @Transactional(transactionManager = "chatTransactionManager")
    public DirectConversationResponse getOrCreateConversation(Long guildId, String userId, String otherUserId) {
        validateGuildExists(guildId);
        validateBothAreMember(guildId, userId, otherUserId);

        GuildDirectConversation conversation = conversationRepository
            .findConversation(guildId, userId, otherUserId)
            .orElseGet(() -> {
                GuildDirectConversation newConversation = GuildDirectConversation.create(guildId, userId, otherUserId);
                return conversationRepository.save(newConversation);
            });

        UserProfileCache otherProfile = userProfileCacheService.getUserProfile(otherUserId);
        String otherNickname = otherProfile != null ? otherProfile.nickname() : "알 수 없음";
        String otherProfileImage = otherProfile != null ? otherProfile.picture() : null;
        int unreadCount = messageRepository.countUnreadMessages(conversation.getId(), userId);

        return DirectConversationResponse.from(conversation, userId, otherNickname, otherProfileImage, unreadCount);
    }

    @Transactional(transactionManager = "chatTransactionManager")
    public void deleteMessage(Long guildId, String userId, Long messageId) {
        validateMembership(guildId, userId);

        GuildDirectMessage message = messageRepository.findById(messageId)
            .orElseThrow(() -> new IllegalArgumentException("메시지를 찾을 수 없습니다."));

        if (!message.getSenderId().equals(userId)) {
            throw new IllegalStateException("본인 메시지만 삭제할 수 있습니다.");
        }

        if (!message.getConversation().getGuildId().equals(guildId)) {
            throw new IllegalArgumentException("해당 길드의 메시지가 아닙니다.");
        }

        message.delete();
        log.info("DM 삭제: messageId={}, deletedBy={}", messageId, userId);
    }

    // ============ 헬퍼 메서드 ============

    private void validateGuildExists(Long guildId) {
        if (!guildQueryFacadeService.guildExists(guildId)) {
            throw new IllegalArgumentException("길드를 찾을 수 없습니다: " + guildId);
        }
    }

    private void validateMembership(Long guildId, String userId) {
        if (!guildQueryFacadeService.isActiveMember(guildId, userId)) {
            throw new IllegalStateException("길드 멤버만 DM을 사용할 수 있습니다.");
        }
    }

    private void validateBothAreMember(Long guildId, String userId1, String userId2) {
        if (userId1.equals(userId2)) {
            throw new IllegalArgumentException("자기 자신에게 DM을 보낼 수 없습니다.");
        }
        if (!guildQueryFacadeService.isActiveMember(guildId, userId1)) {
            throw new IllegalStateException("발신자가 길드 멤버가 아닙니다.");
        }
        if (!guildQueryFacadeService.isActiveMember(guildId, userId2)) {
            throw new IllegalStateException("수신자가 길드 멤버가 아닙니다.");
        }
    }

    private void validateConversationAccess(Long conversationId, String userId, Long guildId) {
        GuildDirectConversation conversation = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new IllegalArgumentException("대화를 찾을 수 없습니다."));

        if (!conversation.isParticipant(userId)) {
            throw new IllegalStateException("해당 대화에 접근할 수 없습니다.");
        }

        if (!conversation.getGuildId().equals(guildId)) {
            throw new IllegalArgumentException("해당 길드의 대화가 아닙니다.");
        }
    }

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
