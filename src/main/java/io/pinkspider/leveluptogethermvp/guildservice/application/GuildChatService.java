package io.pinkspider.leveluptogethermvp.guildservice.application;

import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.ChatMessageRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.ChatMessageResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildChatMessage;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.ChatMessageType;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildChatMessageRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuildChatService {

    private final GuildChatMessageRepository chatMessageRepository;
    private final GuildRepository guildRepository;
    private final GuildMemberRepository memberRepository;

    // 메시지 전송
    @Transactional
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
    @Transactional
    public ChatMessageResponse sendSystemMessage(Long guildId, ChatMessageType type, String content) {
        Guild guild = findGuildById(guildId);
        GuildChatMessage message = GuildChatMessage.createSystemMessage(guild, type, content);
        GuildChatMessage saved = chatMessageRepository.save(message);
        return ChatMessageResponse.from(saved);
    }

    // 시스템 메시지 전송 (참조 포함)
    @Transactional
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
    @Transactional
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
    @Transactional
    public void notifyMemberJoin(Long guildId, String memberNickname) {
        sendSystemMessage(guildId, ChatMessageType.MEMBER_JOIN,
            memberNickname + "님이 길드에 가입했습니다.");
    }

    // 멤버 탈퇴 시스템 메시지
    @Transactional
    public void notifyMemberLeave(Long guildId, String memberNickname) {
        sendSystemMessage(guildId, ChatMessageType.MEMBER_LEAVE,
            memberNickname + "님이 길드를 떠났습니다.");
    }

    // 업적 달성 시스템 메시지
    @Transactional
    public void notifyAchievement(Long guildId, String memberNickname, String achievementName,
                                   Long achievementId) {
        sendSystemMessage(guildId, ChatMessageType.ACHIEVEMENT,
            memberNickname + "님이 '" + achievementName + "' 업적을 달성했습니다!",
            "ACHIEVEMENT", achievementId);
    }

    // 레벨업 시스템 메시지
    @Transactional
    public void notifyLevelUp(Long guildId, String memberNickname, int newLevel) {
        sendSystemMessage(guildId, ChatMessageType.LEVEL_UP,
            memberNickname + "님이 레벨 " + newLevel + "에 도달했습니다!");
    }

    // 미션 완료 시스템 메시지
    @Transactional
    public void notifyMissionComplete(Long guildId, String memberNickname, String missionTitle,
                                       Long missionId) {
        sendSystemMessage(guildId, ChatMessageType.MISSION_COMPLETE,
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
}
